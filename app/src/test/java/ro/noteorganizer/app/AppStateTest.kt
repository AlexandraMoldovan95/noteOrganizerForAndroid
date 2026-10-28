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
    fun `the day turning with the app open moves today, drops the earlier day's bar and asks for the new one's`() {
        // Read once, today stayed on the day the app opened: past midnight the
        // calendar's ring, "Calendar: soonest" and the bar kept to yesterday.
        val state = state()
        state.dayIs("2026-10-04")
        assertEquals("2026-10-04", state.today)
        assertNull("no note on the 4th", state.reminderDue)
        state.dayIs("2026-10-05")
        assertEquals("2026-10-05", state.today)
        assertEquals("the new day's bar", 1, state.reminderDue)
        assertEquals("2026-10-05", state.settings.opt(Reminders.LAST_SHOWN))
        state.dayIs("2026-10-06")
        assertEquals("2026-10-06", state.today)
        assertNull("the 5th's bar goes with the 5th", state.reminderDue)
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
    fun `a notebook that followed the phone keeps the theme it wears, once`() {
        val state = state()
        state.putSetting(Settings.PHONE_DARK_THEME, "dark_ocean")
        state.settleTheme(phoneIsDark = true)
        val written = JSONObject(File(state.dir, "settings.json").readText())
        assertEquals("the theme worn is the one kept", "dark_ocean", written.opt(Settings.THEME))
        assertEquals(false, written.opt(Settings.PHONE_FOLLOWS_PHONE))
        // The phone turned light afterwards: the app no longer turns with it,
        // and settling again does nothing.
        state.settleTheme(phoneIsDark = false)
        assertEquals("dark_ocean", state.themeName(phoneIsDark = false))
    }

    @Test
    fun `Light and Dark go back to the last theme worn in each mode`() {
        val state = state()
        state.settleTheme(phoneIsDark = false)
        assertEquals("light", state.themeName(phoneIsDark = false))
        assertTrue(state.chooseMode(dark = true))
        assertEquals("the original dark one, never chosen before", "dark", state.themeName(false))
        assertTrue(state.chooseTheme("dark_forest"))
        assertTrue(state.chooseMode(dark = false))
        assertEquals("light", state.themeName(false))
        assertTrue(state.chooseTheme("light_warm"))
        assertTrue(state.chooseMode(dark = true))
        assertEquals("the dark one worn last", "dark_forest", state.themeName(false))
        assertTrue(state.chooseMode(dark = false))
        assertEquals("the light one worn last", "light_warm", state.themeName(true))
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
            null
        }
    }

    private fun AppState.pinnedOnDisk(id: Int) = rawNote(id)?.optBoolean("pinned") == true

    @Test
    fun anotherCardTappedSavesTheOpenNoteFirst() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush while ${s.openId} open"); null }
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
            null
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
        s.flush = { seen.add("flush"); null }
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
        s.flush = { seen.add("flush"); null }
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
        s.flush = { seen.add("flush"); null }
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
        s.flush = { flushed = true; null }
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
        s.flush = { flushed = true; null }
        s.saveAndCloseNote()
        assertTrue(flushed)
        assertNull(s.openId)
        assertFalse(s.openDay != null)
    }

    /**
     * Every write of the notes failing, as on a full disk: where the write
     * puts its .tmp is a folder it cannot remove. Returns the way to free it.
     */
    private fun AppState.writesFail(): () -> Unit {
        val blocker = File(dir, "notes_data.json.tmp")
        File(blocker, "hold").apply { parentFile!!.mkdirs(); writeText("x") }
        return { File(blocker, "hold").delete(); blocker.delete() }
    }

    /** The editor open on [id] (0: a new note), registered as NoteEditor registers it, holding [body] unsaved. */
    private fun AppState.editing(id: Int, body: String): EditorModel {
        val editor = EditorModel(this, note(id))
        flush = { editor.write() }
        editor.body = body
        editor.changed()
        return editor
    }

    /** What the bar at the foot says -- the one line a screen reader reads out. */
    private fun AppState.said() = snackbar.currentSnackbarData?.visuals

    @Test
    fun `a note whose save fails stays open on Back, another card, + and the rail, and says why`() {
        val s = state()
        s.openNote(1)
        s.editing(1, "first, and the words typed since")
        val free = s.writesFail()
        val opening = s.opening
        s.saveAndCloseNote()
        s.openNote(2)
        s.newNote()
        assertEquals(1, s.openId)
        assertEquals(opening, s.opening)
        val said = s.said()!!
        assertTrue(said.message, said.message.startsWith("Not saved, so the note stays open: "))
        assertEquals("Leave anyway", said.actionLabel)
        // Saved once it can be, and left only then.
        free()
        s.saveAndCloseNote()
        assertNull(s.openId)
        assertEquals("first, and the words typed since", s.rawNote(1)?.optString("body"))
    }

    @Test
    fun `a new note that cannot be saved is closed only when the person says to leave anyway`() {
        val s = state()
        s.newNote()
        s.editing(0, "Call the dentist Tuesday 10:30")
        s.writesFail()
        s.saveAndCloseNote()
        assertEquals("the editor, the words' only copy, stays", 0, s.openId)
        s.snackbar.currentSnackbarData!!.performAction()
        assertNull(s.openId)
        assertTrue(s.load.notes.none { it.body.startsWith("Call") })
    }

    @Test
    fun `a pin, the Trash or an import waits for an open note that cannot be saved`() {
        val s = state()
        s.openNote(1)
        s.editing(1, "typed, not saved")
        s.writesFail()
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(1), true)
        s.trash(listOf(1))
        assertEquals("the editor is not read back over its words", rewritten, s.openNoteRewritten)
        assertEquals(1, s.openId)
        assertTrue(s.said()!!.message.startsWith("Not saved, so the open note was left as it is: "))
        assertFalse(s.beforeImport())
    }

    @Test
    fun `a failed autosave is said once, and a save a tap asked for every time`() {
        // Drawn in red only, it reached nobody using a screen reader.
        val s = state()
        val editor = s.editing(1, "typed")
        s.writesFail()
        assertFalse(editor.save(auto = true))
        assertTrue(s.said()!!.message.startsWith("Not saved: "))
        s.snackbar.currentSnackbarData!!.dismiss()
        editor.body = "typed more"
        editor.changed()
        assertFalse(editor.save(auto = true))
        assertNull("the autosave does not say it again", s.said())
        assertFalse(editor.save())
        assertTrue(s.said()!!.message.startsWith("Not saved: "))
    }

    /** A note whose checklist reads [rows], open in an editor registered as NoteEditor registers it. */
    private fun checklist(vararg rows: String): Pair<AppState, EditorModel> {
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        val items = rows.joinToString(", ") { """{"text": "$it", "done": false}""" }
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "Bread", "body": "Knead", "category": "General", "checklist": [$items]}]""")
        File(dir, "attachments").mkdirs()
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        s.openNote(1)
        val editor = EditorModel(s, s.note(1))
        s.flush = { editor.write() }
        return s to editor
    }

    private fun AppState.rowsOnDisk(): List<String> {
        val note = org.json.JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0)
        val rows = note.getJSONArray("checklist")
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("text") }
    }

    @Test
    fun `a row being renamed and the words in A new item are written by the flush, no field left`() {
        // Home, or Recents: the activity's onStop runs the flush, and nothing
        // moves the focus. The words waited in the fields, and a process
        // killed in the background took them.
        val (s, editor) = checklist("flour", "yeast")
        editor.renaming = EditorModel.Renaming(1, "yeast", "yeast 7g")
        editor.adding = "salt"
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("flour", "yeast 7g", "salt"), s.rowsOnDisk())
        assertNull(editor.renaming)
        assertEquals("", editor.adding)
    }

    @Test
    fun `a rename waiting for a row the list moved under it is dropped, never put on another row`() {
        val (s, editor) = checklist("flour", "yeast", "salt")
        editor.renaming = EditorModel.Renaming(1, "yeast", "yeast 7g")
        // From elsewhere: the rows move up one under the waiting words.
        Notebook.removeChecklistItem(s.load, 1, 0)
        s.refresh()
        assertNull(editor.write())
        assertEquals(listOf("yeast", "salt"), s.rowsOnDisk())
    }

    @Test
    fun `letters typed into a row wait together, and go whole when another row is typed in`() {
        // The walk typed " 500g" after "flour" and pressed Home: the file
        // read "flour " -- the second letter, judged by a drawing that had
        // not caught up, wrote the first alone and lost the rest.
        val (s, editor) = checklist("flour", "yeast")
        for (now in listOf("flour ", "flour 5", "flour 50", "flour 500g")) assertNull(editor.typeInRow(0, now))
        assertEquals("nothing is written while one row is typed in", listOf("flour", "yeast"), s.rowsOnDisk())
        assertNull(editor.typeInRow(1, "yeast 7g"))
        assertEquals(listOf("flour 500g", "yeast"), s.rowsOnDisk())
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("flour 500g", "yeast 7g"), s.rowsOnDisk())
    }

    @Test
    fun `a row taken off by its cross is said, and Undo puts it back where it was, whole`() {
        // Gone at once, unsaid, with no way back: the editor's Undo holds no checklist.
        val (s, _) = checklist("flour", "yeast", "salt")
        s.removeChecklistRow(1, 1)
        assertEquals(listOf("flour", "salt"), s.rowsOnDisk())
        val offer = s.snackbar.currentSnackbarData!!
        assertEquals("Removed “yeast” from the checklist", offer.visuals.message)
        offer.performAction()
        assertEquals(listOf("flour", "yeast", "salt"), s.rowsOnDisk())
    }

    @Test
    fun `a row's Undo writes the words being typed into another row before it puts the row back`() {
        // Put back under them, the row moved the one being typed in, and its
        // words -- waiting to be written -- were dropped.
        val (s, editor) = checklist("eggs", "bread", "milk", "butter")
        s.removeChecklistRow(1, 1)
        editor.renaming = EditorModel.Renaming(2, "butter", "butter 250g")
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals(listOf("eggs", "bread", "milk", "butter 250g"), s.rowsOnDisk())
    }

    @Test
    fun `an Undo bar lasts from when it shows, for as long as the phone asks`() {
        // Its own eight seconds counted from the asking: queued behind another
        // message it flashed for a second, or stayed for good. Android's long
        // length is counted by the bar at the foot from when it shows, and
        // stretched by the phone's "Time to take action".
        val (s, _) = checklist("eggs", "bread")
        s.removeChecklistRow(1, 0)
        val offer = s.snackbar.currentSnackbarData!!
        assertEquals("Undo", offer.visuals.actionLabel)
        assertEquals(androidx.compose.material3.SnackbarDuration.Long, offer.visuals.duration)
    }

    @Test
    fun `the autosave leaves the checklist's half-typed words where they are`() {
        val (s, editor) = checklist("flour")
        editor.adding = "bre"
        editor.body = "Knead ten minutes"
        editor.changed()
        assertTrue(editor.save(auto = true))
        assertEquals(listOf("flour"), s.rowsOnDisk())
        assertEquals("bre", editor.adding)
    }
}
