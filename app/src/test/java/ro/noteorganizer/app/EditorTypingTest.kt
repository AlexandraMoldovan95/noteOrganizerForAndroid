package ro.noteorganizer.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * What a letter typed in the editor wears, through the editor itself: the
 * caret it is typed at, the styles beside it, and a B pressed with nothing
 * selected -- the desktop's rules, which the phone had the wrong way round.
 */
class EditorTypingTest {

    private lateinit var state: AppState

    private fun editor(body: String, formatting: String = "[]"): EditorModel {
        val dir = NioFiles.createTempDirectory("editortyping").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "A", "body": "$body", "category": "General", "formatting": $formatting}]""")
        File(dir, "attachments").mkdirs()
        state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        return EditorModel(state, state.note(1))
    }

    /** [text] typed so the field holds [now], its caret after the last letter typed, at [caret]. */
    private fun EditorModel.type(now: String, caret: Int) {
        typeInto(Editing.segments(body).first(), TextFieldValue(now, TextRange(caret)), 0)
    }

    @Test
    fun `B pressed with nothing selected styles what is typed next, until pressed again`() {
        val model = editor("Hello")
        model.pending = setOf("bold")
        model.type("Hello there", 11)
        assertEquals(listOf(FormatSpan(5, 11, "bold")), model.spans)
        // B pressed again: what is typed after a space is plain. A letter
        // glued on the bold word would still wear it, as on the desktop.
        model.pending = emptySet()
        model.type("Hello there now", 15)
        assertEquals(listOf(FormatSpan(5, 11, "bold")), model.spans)
    }

    @Test
    fun `items deleted or typed over in the editor are renumbered, no marker among the words`() {
        // What the keyboard leaves: items 2 and 3 selected and deleted, then
        // a letter typed over them -- the editor gives back the list mended.
        val model = editor("1. one\\n2. two\\n3. three\\n4. four")
        model.type("1. one\n4. four", 7)
        assertEquals("1. one\n2. four", model.body)
        val again = editor("1. one\\n2. two\\n3. three\\n4. four")
        again.type("1. one\nx4. four", 8)
        assertEquals("1. one\n2. x\n3. four", again.body)
    }

    @Test
    fun `a pending style is gone when the note is read again`() {
        val model = editor("Hello")
        model.pending = setOf("italic")
        model.reloadFrom(state.note(1)!!)
        assertTrue(model.pending.isEmpty())
    }

    @Test
    fun `a day given only its own colour keeps the series' styles, one given its own words does not`() {
        // The desktop's load_note keeps the formatting while the day's body
        // is the series'; the phone dropped it for any day with an edit.
        val dir = NioFiles.createTempDirectory("editorday").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "s", "title": "Gym", "body": "Legs day",
            "category": "General", "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]},
            "formatting": [{"start": 0, "end": 4, "style": "bold"}],
            "overrides": {"2026-10-12": {"body": "Legs day", "color": "#ff6b6b"},
                          "2026-10-19": {"body": "Arms day"}}}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(listOf(FormatSpan(0, 4, "bold")), EditorModel(state, state.note(1), day = "2026-10-12").spans)
        assertEquals(emptyList<FormatSpan>(), EditorModel(state, state.note(1), day = "2026-10-19").spans)
    }

    @Test
    fun `a line given to the next field as it is typed takes the cursor with it`() {
        // A field draws at most its share of lines (Editing.FIELD_LINES);
        // a line typed into a full one moves its last line to the next. The
        // cursor stayed in the first, at its end, and the next letter went there.
        val model = editor(List(Editing.FIELD_LINES) { "x" }.joinToString("\\n"))
        assertEquals(1, Editing.segments(model.body).size)
        // Enter at the end of line 500: the cursor stays in the first field.
        val field = Editing.segments(model.body).first()
        val at = 500 * 2 - 1
        val typed = field.text.substring(0, at) + "\n" + field.text.substring(at)
        model.typeInto(field, TextFieldValue(typed, TextRange(at + 1)), 0)
        model.caret = at + 1
        model.keepCaret(0, TextFieldValue(typed, TextRange(at + 1)))
        val after = Editing.segments(model.body)
        assertEquals(2, after.size)
        assertEquals(0, model.focus)
        assertEquals(after[0].text, model.focused!!.text)
        assertEquals(TextRange(at + 1), model.focused!!.selection)
        // A letter typed on a new last line: it went to the second field, and the cursor with it.
        val more = after[0].text + "\nY"
        model.typeInto(after[0], TextFieldValue(more, TextRange(more.length)), 0)
        model.caret = more.length
        model.keepCaret(0, TextFieldValue(more, TextRange(more.length)))
        val last = Editing.segments(model.body)
        assertEquals(1, model.focus)
        assertEquals(last[1].text, model.focused!!.text)
        assertEquals(TextRange(last[1].text.indexOf("Y") + 1), model.focused!!.selection)
    }

    @Test
    fun `glued on a styled word, and before one, as the desktop types`() {
        val model = editor("quick fox", """[{"start": 0, "end": 5, "style": "highlight", "value": "#fff3b0"}]""")
        model.type("quickly fox", 7)
        assertEquals(listOf(FormatSpan(0, 7, "highlight", "#fff3b0")), model.spans)
        model.type("Aquickly fox", 1)
        assertEquals("a letter typed before it stays plain", listOf(FormatSpan(1, 8, "highlight", "#fff3b0")), model.spans)
    }
}
