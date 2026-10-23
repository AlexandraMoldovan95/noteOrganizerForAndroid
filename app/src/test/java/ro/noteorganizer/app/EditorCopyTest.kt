package ro.noteorganizer.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * What a copy from the editor carries: the selection's formatting as it is
 * when the copy is made. The selection is noted when it is made, and a
 * style put on from the format bar afterwards changes the note without any
 * field saying so -- on a real phone's rehearsal, words selected, made bold
 * and 14, copied, came back pasted plain.
 */
class EditorCopyTest {

    private val words = "Tom & Jerry"

    private fun editor(): Pair<AppState, EditorModel> {
        val dir = NioFiles.createTempDirectory("editorcopy").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "A", "body": "$words", "category": "General"}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        return state to EditorModel(state, state.note(1))
    }

    private fun EditorModel.selectAll() =
        pick(Editing.segments(body).first(), TextFieldValue(body, TextRange(0, body.length)))

    /** The format bar's B, as it applies it: the spans changed, no field told. */
    private fun EditorModel.boldFromTheBar() {
        spans = setStyle(spans, 0, words.length, "bold")
        changed()
    }

    @Test
    fun `a style put on after the words were selected goes out with their copy`() {
        val (state, model) = editor()
        model.selectAll()
        model.boldFromTheBar()
        assertNotNull(model.copyOut(words))
        assertEquals(listOf(FormatSpan(0, words.length, "bold")), state.ownCopy!!.spans)
    }

    @Test
    fun `a cut made after a style keeps it -- the words are gone by the time it copies`() {
        val (state, model) = editor()
        model.selectAll()
        model.boldFromTheBar()
        // The field's change arrives first, the words already taken out.
        model.freshenPick()
        model.body = ""
        model.changed()
        assertNotNull(model.copyOut(words))
        assertEquals(listOf(FormatSpan(0, words.length, "bold")), state.ownCopy!!.spans)
    }

    @Test
    fun `a copy of words that are no longer the selection's goes out plain, as before`() {
        val (_, model) = editor()
        model.selectAll()
        assertNull(model.copyOut("Jerry"))
        assertTrue(model.copyOut(words) != null)
    }

    // ---- a table pasted in: markdown's dashes are not widths set here ----

    private val markdown = "| What | How much |\n| --- | ---: |\n| rent | 1500 |"

    /** The widths each of the note's separators spells. */
    private fun widths(body: String) =
        body.split("\n").filter { isSeparatorRow(it) }.map { line -> parseSeparator(line).map { it.width } }

    private fun EditorModel.pasteAtEnd(plain: String, html: String? = null): Boolean {
        val segment = Editing.segments(body).last { !it.table }
        return paste(segment, TextFieldValue(segment.text, TextRange(segment.text.length)), html, plain)
    }

    @Test
    fun `a markdown table pasted in the middle of words comes without its separator's widths`() {
        val (_, model) = editor()
        // "---:" is four characters: read as a width set by hand, the column
        // was drawn four wide and the header cut to "How…".
        assertTrue("the editor makes this paste, not the field", model.pasteAtEnd("\nCosts:\n$markdown\nthat is all"))
        assertTrue(model.body, "| --- | --: |" in model.body)
        assertEquals(listOf(listOf<Int?>(null, null)), widths(model.body))
        assertTrue(model.body.endsWith("that is all"))
    }

    @Test
    fun `rows pasted on their own lines, and the plain paste, leave them behind too`() {
        val (_, model) = editor()
        assertTrue(model.pasteAtEnd("\n$markdown"))
        assertEquals(listOf(listOf<Int?>(null, null)), widths(model.body))
        val (_, plain) = editor()
        val segment = Editing.segments(plain.body).first()
        plain.pastePlain(segment, TextFieldValue(segment.text, TextRange(segment.text.length)), "\n$markdown")
        assertEquals(listOf(listOf<Int?>(null, null)), widths(plain.body))
    }

    @Test
    fun `words with no such table are still the field's own paste`() {
        val (_, model) = editor()
        assertTrue(!model.pasteAtEnd(" and Spike"))
        assertTrue(!model.pasteAtEnd("\nTom\nJerry"))
    }

    @Test
    fun `a table copied from a note comes back with the widths set in it`() {
        // A field's selection never reaches into a drawn table on the phone,
        // but a copy the app keeps is whatever the copy held: the rule is
        // that its dashes are widths set here.
        val table = "| What | How much |\n| ------------ | --------: |\n| rent | 1500 |"
        val copy = Pasting.copyOut(table, emptyList(), emptyMap(), 0, table.length)!!
        val (plainState, plain) = editor()
        plainState.ownCopy = copy
        assertTrue(plain.pasteAtEnd(copy.text))
        assertEquals("by the plain route", listOf(listOf<Int?>(12, 9)), widths(plain.body))
        val (richState, rich) = editor()
        richState.ownCopy = copy
        assertTrue(rich.pasteAtEnd(copy.text, copy.html))
        assertEquals("by its own HTML", listOf(listOf<Int?>(12, 9)), widths(rich.body))
    }

    @Test
    fun `a day of a series takes the words only, a table among them without its widths`() {
        val dir = NioFiles.createTempDirectory("editorday").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "s", "title": "Gym", "body": "Legs day",
            "category": "General", "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]}}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        val day = EditorModel(state, state.note(1), day = "2026-10-12")
        assertTrue(day.pasteAtEnd("\n$markdown"))
        assertEquals(listOf(listOf<Int?>(null, null)), widths(day.body))
        assertTrue("and words alone are still the field's", !day.pasteAtEnd(" and arms"))
    }
}
