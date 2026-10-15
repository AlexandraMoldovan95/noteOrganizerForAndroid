package ro.noteorganizer.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * The open note and everything else that can reach it: a code shared in
 * from another app arrives over it, and every action of the list and the
 * calendar can be pointed at the very note the editor holds.
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

}
