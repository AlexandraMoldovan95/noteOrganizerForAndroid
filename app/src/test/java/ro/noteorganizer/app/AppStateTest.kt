package ro.noteorganizer.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * The open note and everything else that can reach it. On a phone the
 * editor covers the screen; on a wide one the list stays beside it, and
 * the list can pin, move, copy or share the very note the editor holds.
 *
 * The editor keeps its own copy of every field and writes them all back
 * when it saves, so the order is the whole rule: what it holds is saved
 * BEFORE anything else touches the note, and it reads the note back
 * AFTER anything else wrote it. Here the editor is stood in for by its
 * flush, which records what the file held at the moment it was asked --
 * the one thing a tap through the emulator cannot time.
 */
class AppStateTest {

    private fun state(): AppState {
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "A", "body": "first", "category": "General", "date": "2026-10-05"},
                {"id": 2, "uid": "b", "title": "B", "body": "second", "category": "General"}]""")
        File(dir, "attachments").mkdirs()
        return AppState(dir, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun `a Trash window chosen ends one lost with an unreadable file`() {
        val state = state()
        state.putSetting(Settings.PHONE_TRASH_WINDOW_UNKNOWN, true)
        assertNull(state.load.settings?.let { Settings.purgeWindow(it) })
        assertTrue(state.chooseTrashWindow(60))
        val written = JSONObject(File(state.dir, "settings.json").readText())
        assertEquals(60, written.opt(Settings.RETENTION_DAYS))
        assertFalse("the mark is taken off", written.has(Settings.PHONE_TRASH_WINDOW_UNKNOWN))
        assertEquals(60, state.load.settings?.let { Settings.purgeWindow(it) })
    }

    @Test
    fun `a cleared day's Undo never paints over what was chosen since`() {
        // Cleared red, painted green within the Undo's seconds, Undo tapped:
        // the green stays, as the desktop's _undo_day_clear refuses.
        val state = state()
        state.paintDay("2026-10-24", "#ff0000")
        state.paintDay("2026-10-24", "")
        state.paintDay("2026-10-24", "#00ff00")
        assertFalse(state.undoDayClear("2026-10-24", colour = "#ff0000"))
        assertEquals("#00ff00", state.dayColours["2026-10-24"])
        // Still empty, it is put back.
        state.paintDay("2026-10-25", "#ff0000")
        state.paintDay("2026-10-25", "")
        assertTrue(state.undoDayClear("2026-10-25", colour = "#ff0000"))
        assertEquals("#ff0000", state.dayColours["2026-10-25"])
        // The markers the same.
        state.markDay("2026-10-26", listOf("⭐"))
        state.markDay("2026-10-26", emptyList())
        state.markDay("2026-10-26", listOf("🎂"))
        assertFalse(state.undoDayClear("2026-10-26", markers = listOf("⭐")))
        assertEquals(listOf("🎂"), state.dayMarkers["2026-10-26"])
        state.markDay("2026-10-26", emptyList())
        assertTrue(state.undoDayClear("2026-10-26", markers = listOf("⭐")))
        assertEquals(listOf("⭐"), state.dayMarkers["2026-10-26"])
    }

    @Test
    fun `the calendar's month stays within the years a date is written in, as the desktop's shift_calendar`() {
        // Past December 9999 a day's key no longer read back as a date.
        val state = state()
        state.calendarMonth = java.time.YearMonth.of(9999, 12)
        state.calendarMonth = state.calendarMonth.plusMonths(1)
        assertEquals(java.time.YearMonth.of(9999, 12), state.calendarMonth)
        state.calendarMonth = java.time.YearMonth.of(1, 1).minusMonths(1)
        assertEquals(java.time.YearMonth.of(1, 1), state.calendarMonth)
    }

    /** The editor's save, standing in: the body it holds, written now. */
    private fun AppState.editorHolds(id: Int, body: String, seen: MutableList<String>) {
        flush = {
            seen.add("flush")
            Notebook.updateNote(load, id, note(id)!!.title, body)
            refresh()
        }
    }

    private fun AppState.pinnedOnDisk(id: Int) = rawNote(id)?.optBoolean("pinned") == true

    @Test
    fun anotherCardTappedSavesTheOpenNoteFirst() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush while ${s.openId} open") }
        val before = s.opening
        s.openNote(2)
        assertEquals(listOf("flush while 1 open"), seen)
        assertEquals(2, s.openId)
        assertEquals(before + 1, s.opening)
    }

    @Test
    fun theOpenNotesOwnCardChangesNothing() {
        val s = state()
        s.openNote(1)
        val before = s.opening
        s.openNote(1)
        assertEquals(before, s.opening)
    }

    @Test
    fun aSecondNewNoteIsAFreshEditor() {
        val s = state()
        s.newNote()
        val first = s.opening
        s.newNoteSaved(3)
        assertEquals(3, s.openId)
        s.newNote()
        assertEquals(0, s.openId)
        assertEquals(first + 1, s.opening)
        // A save that reaches it only once it is no longer new changes nothing.
        s.openNote(2)
        s.newNoteSaved(9)
        assertEquals(2, s.openId)
    }

    @Test
    fun aPinFromTheListLandsOnTheWordsJustTypedAndIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = {
            seen.add("flush, pinned on disk: ${s.pinnedOnDisk(1)}")
            Notebook.updateNote(s.load, 1, "A", "typed last")
            s.refresh()
        }
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(1), true)
        assertEquals(listOf("flush, pinned on disk: false"), seen)
        assertTrue(s.pinnedOnDisk(1))
        assertEquals("typed last", s.rawNote(1)?.optString("body"))
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun aChangeToAnotherNoteLeavesTheEditorAlone() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush") }
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(2), true)
        s.moveToCategory(listOf(2), "General")
        assertTrue(seen.isEmpty())
        assertEquals(rewritten, s.openNoteRewritten)
    }

    @Test
    fun aCategoryFromTheListIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        val rewritten = s.openNoteRewritten
        s.moveToCategory(listOf(1), "General")
        assertEquals(listOf("flush"), seen)
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun aCopyAndACodeCarryTheLastWordsTypedWithoutRereadingTheNote() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        val rewritten = s.openNoteRewritten
        val code = s.shareCode(listOf(1))!!
        val shared = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("typed last", shared.optString("body"))
        s.editorHolds(1, "typed later", seen)
        s.duplicate(1)
        assertTrue(s.load.notes.any { it.id != 1 && it.body == "typed later" })
        // Read, not written: the editor keeps its cursor.
        assertEquals(rewritten, s.openNoteRewritten)
        assertEquals(listOf("flush", "flush"), seen)
    }

    @Test
    fun theTrashGetsTheOpenNoteWithItsLastWords() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        s.trash(listOf(1))
        assertEquals(listOf("flush"), seen)
        assertNull(s.openId)
        assertEquals("typed last", s.load.trash.last().body)
    }

    @Test
    fun aRepeatSetOnTheOpenNoteIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush") }
        val rewritten = s.openNoteRewritten
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        assertEquals(listOf("flush"), seen)
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun aDayChangedFromTheCalendarBesideTheNoteIsSavedFirstAndReadBack() {
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush") }
        val rewritten = s.openNoteRewritten
        s.resetDay(1, "2026-10-12")
        s.deleteDay(1, "2026-10-19")
        assertEquals(listOf("flush", "flush"), seen)
        assertEquals(rewritten + 2, s.openNoteRewritten)
    }

    @Test
    fun anImportReadsTheOpenNoteBackAndOnlyAnOpenOne() {
        val s = state()
        val rewritten = s.openNoteRewritten
        s.afterImport()
        assertEquals(rewritten, s.openNoteRewritten)
        s.openNote(1)
        var flushed = false
        s.flush = { flushed = true }
        s.beforeImport()
        assertTrue(flushed)
        s.afterImport()
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun customOrderStartsFromTheOrderShownOnlyWhenNothingIsPlacedYet() {
        val s = state()
        File(s.dir, "settings.json").writeText("{}")
        s.reload()
        s.chooseSort(SortOrder.CUSTOM, from = SortOrder.TITLE_ZA)
        val placed = { s.rawNotes().associate { it.getInt("id") to it.optInt("order", -1) } }
        assertEquals(mapOf(2 to 0, 1 to 1), placed())
        assertEquals("Custom order", s.settings.optString(Settings.NOTE_SORT))
        // Arranged by hand since: choosing Custom order again from another
        // sort must not start it over from that sort.
        s.moveNote(1, anchor = 2, before = true, grouped = false)
        s.chooseSort(SortOrder.TITLE_AZ, from = SortOrder.CUSTOM)
        s.chooseSort(SortOrder.CUSTOM, from = SortOrder.TITLE_ZA)
        assertEquals(mapOf(1 to 0, 2 to 1), placed())
    }

    @Test
    fun theRailSavesTheNoteItLeaves() {
        val s = state()
        s.openNote(1)
        var flushed = false
        s.flush = { flushed = true }
        s.saveAndCloseNote()
        assertTrue(flushed)
        assertNull(s.openId)
        assertFalse(s.openDay != null)
    }
}
