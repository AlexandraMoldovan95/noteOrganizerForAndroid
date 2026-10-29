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
    fun `a table row typed by hand takes the cursor to the line under its grid, and the next row follows it`() {
        // At its closing pipe the line became a table: no field held the
        // cursor, the field above kept the keyboard with it at its end, and
        // the next row was written above the first -- the grid upside down.
        val model = editor("Shopping")
        val typed = "Shopping\n| a | b |"
        model.typeInto(Editing.segments(model.body).first(), TextFieldValue(typed, TextRange(typed.length)), 0)
        model.caret = typed.length
        model.keepCaret(0, TextFieldValue(typed, TextRange(typed.length)))
        val segments = Editing.segments(model.body)
        assertEquals(listOf(false, true, false), segments.map { it.table })
        assertEquals(2, model.focus)
        assertEquals(TextFieldValue("", TextRange(0)), model.focused)
        model.typeInto(segments[2], TextFieldValue("| c | d |", TextRange(9)), 2)
        assertEquals("Shopping\n| a | b |\n| c | d |", model.body)
    }

    @Test
    fun `a line typed past what the note draws lets the keyboard go, said once, never onto the line above`() {
        // Enter at the end of a note whose fields draw all the lines a body
        // may: the new line went into the read-only rest, the field kept the
        // keyboard with its cursor at its end, and the next words were glued
        // onto the line above.
        val model = editor(List(Editing.MAX_DRAWN_LINES) { "x" }.joinToString("\\n"))
        val segments = Editing.segments(model.body)
        val last = segments.lastIndex
        assertTrue(segments[last].field)
        val typed = segments[last].text + "\n"
        model.typeInto(segments[last], TextFieldValue(typed, TextRange(typed.length)), last)
        model.caret = model.body.length
        model.keepCaret(last, TextFieldValue(typed, TextRange(typed.length)))
        assertTrue("the new line is in the read-only rest", Editing.segments(model.body).last().long)
        assertEquals(null, model.focus)
        assertEquals(1, model.letGo)
        assertTrue(state.snackbar.currentSnackbarData!!.visuals.message.startsWith("The end of this note is more than"))
        state.snackbar.currentSnackbarData!!.dismiss()
        model.keepCaret(last, TextFieldValue(typed, TextRange(typed.length)))
        assertEquals(2, model.letGo)
        assertEquals("said once", null, state.snackbar.currentSnackbarData)
    }

    @Test
    fun `the calculator writes nothing where no field would show it, and says where to tap`() {
        // The cursor never in the note, its answer went to the end -- past
        // what the note draws, out of sight -- and every tap added another.
        val model = editor((List(Editing.MAX_DRAWN_LINES) { "x" } + "past").joinToString("\\n"))
        assertTrue(Editing.segments(model.body).last().long)
        val before = model.body
        model.insertCalculation("5+3=8")
        assertEquals(before, model.body)
        assertTrue(state.snackbar.currentSnackbarData!!.visuals.message.startsWith("The end of this note is more than"))
        // A note ending with a table: the answer goes on the line under it, as ever.
        val table = editor("Costs\\n| a | 1 |")
        table.insertCalculation("5+3=8")
        assertEquals("Costs\n| a | 1 |\n5+3=8", table.body)
    }

    @Test
    fun `words pasted too long to lay out take the cursor to the line after them, not the end of the line above`() {
        val model = editor("abc\\ndef")
        val field = Editing.segments(model.body).single()
        model.pastePlain(field, TextFieldValue(field.text, TextRange(3)), "x".repeat(Editing.MAX_UNBROKEN + 1))
        val segments = Editing.segments(model.body)
        assertTrue(segments[0].long)
        assertEquals("def", segments[1].text)
        assertEquals(1, model.focus)
        assertEquals(TextFieldValue("def", TextRange(0)), model.focused)
    }

    @Test
    fun `Undo keeps what a phone can hold of a long note's steps, and every step of a short one`() {
        // Each step is the whole body: a note of two million letters, from
        // a share code of 8 KB, kept 200 of them -- some 760 MB.
        val long = editor("x".repeat(2_000_000))
        repeat(40) {
            long.body = long.body + "y"
            long.changed()
        }
        assertTrue("${long.undoSteps} steps", long.undoSteps in 1..(Editing.HISTORY_CHARS / 2_000_000).toInt())
        val before = long.body.length
        long.undo()
        assertEquals("the last change undone", before - 1, long.body.length)
        val short = editor("A page of words.")
        repeat(40) {
            short.body = short.body + " more"
            short.changed()
        }
        assertEquals(40, short.undoSteps)
    }

    @Test
    fun `a restore that ends the repeat, from one of its days, leaves the day for the whole note`() {
        // The editor stayed on the day, and every save went to the day and
        // was refused: "This note no longer repeats."
        val dir = NioFiles.createTempDirectory("editorrestoreday").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "r", "title": "R", "body": "now daily",
            "category": "General", "date": "2026-10-05", "recurrence": {"freq": "daily"},
            "_prev": {"title": "R", "body": "a one-off", "date": "2026-10-05", "recurrence": {"freq": ""}}},
            {"id": 2, "uid": "s", "title": "S", "body": "weekly", "category": "General", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]},
            "_prev": {"title": "S", "body": "older words"}}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-10-12")
        assertEquals("2026-10-12", state.openDay)
        val model = EditorModel(state, state.note(1), day = state.openDay)
        assertTrue(Notebook.restorePrevious(state.load, 1) is SaveResult.Ok)
        model.reloadFrom(state.note(1)!!)
        assertEquals(null, state.openDay)
        assertEquals(1, state.openId)
        // A restore that keeps the repeat keeps the day.
        state.openNote(2, "2026-10-12")
        val still = EditorModel(state, state.note(2), day = state.openDay)
        assertTrue(Notebook.restorePrevious(state.load, 2) is SaveResult.Ok)
        still.reloadFrom(state.note(2)!!)
        assertEquals("2026-10-12", state.openDay)
    }

    @Test
    fun `glued on a styled word, and before one, as the desktop types`() {
        val model = editor("quick fox", """[{"start": 0, "end": 5, "style": "highlight", "value": "#fff3b0"}]""")
        model.type("quickly fox", 7)
        assertEquals(listOf(FormatSpan(0, 7, "highlight", "#fff3b0")), model.spans)
        model.type("Aquickly fox", 1)
        assertEquals("a letter typed before it stays plain", listOf(FormatSpan(1, 8, "highlight", "#fff3b0")), model.spans)
    }

    @Test
    fun `Redo puts the cursor by the rule typing uses, under a grid and out of a full note`() {
        // Undo and Redo asked a copy of the rule's first half: after Redo of
        // a table row typed at the end the cursor stayed on the line above
        // the grid, and the next row went in over the first.
        val model = editor("Costs")
        val typed = "Costs\n| a | b |"
        model.typeInto(Editing.segments(model.body).first(), TextFieldValue(typed, TextRange(typed.length)), 0)
        model.caret = typed.length
        model.keepCaret(0, TextFieldValue(typed, TextRange(typed.length)))
        model.undo()
        assertEquals("Costs", model.body)
        assertEquals(0, model.focus)
        model.redo()
        assertEquals(typed, model.body)
        assertEquals("the field under the grid", 2, model.focus)
        assertEquals(TextFieldValue("", TextRange(0)), model.focused)
        model.typeInto(Editing.segments(model.body)[2], TextFieldValue("| c | d |", TextRange(9)), 2)
        assertEquals("Costs\n| a | b |\n| c | d |", model.body)
        // Enter at the end of a note drawing all it may: the keyboard went;
        // Undo brings it back, and Redo lets it go again, not left on the line above.
        val full = editor(List(Editing.MAX_DRAWN_LINES) { "x" }.joinToString("\\n"))
        val last = Editing.segments(full.body).lastIndex
        val segment = Editing.segments(full.body)[last]
        val enter = segment.text + "\n"
        full.typeInto(segment, TextFieldValue(enter, TextRange(enter.length)), last)
        full.caret = full.body.length
        full.keepCaret(last, TextFieldValue(enter, TextRange(enter.length)))
        assertEquals(1, full.letGo)
        full.undo()
        assertEquals(last, full.focus)
        full.redo()
        assertEquals(null, full.focus)
        assertEquals(2, full.letGo)
    }

    @Test
    fun `Undo of a style leaves the cursor where it was, so a table goes in there, not onto the one at the end`() {
        // B, then ↶ on the same bar, then ⊞: Undo emptied the place the
        // cursor was and only a change of the words put it back, so the
        // table went to the note's end -- onto the table closing it, refused
        // with words about a note too large (the final audit's seventh round).
        val model = editor("Shopping\\nbread and milk\\n| Item | Qty |\\n| Eggs | 6 |")
        val field = Editing.segments(model.body).first()
        assertTrue(field.field)
        model.focus = 0
        model.focused = TextFieldValue(field.text, TextRange(9, 14))
        model.caret = 14
        model.spans = toggleStyle(model.spans, 9, 14, "bold", "")
        model.changed()
        model.undo()
        assertEquals(emptyList<FormatSpan>(), model.spans)
        assertEquals("the place written at, kept", 14, model.caret)
        assertEquals(0, model.focus)
        assertEquals("the selection kept", TextRange(9, 14), model.focused!!.selection)
        assertEquals(2, model.insertTable(2, 2))
        assertTrue(model.body.startsWith("Shopping\nbread\n|"))
        // Redo, the same; and with the cursor out of the text -- in the title
        // -- the place the calculator writes at stays where it was.
        val again = editor("Plan")
        again.focus = 0
        again.focused = TextFieldValue("Plan", TextRange(2))
        again.caret = 2
        again.spans = toggleStyle(again.spans, 0, 4, "italic", "")
        again.changed()
        again.undo()
        again.redo()
        assertEquals(2, again.caret)
        again.focus = null
        again.focused = null
        again.title = "Plans"
        again.changed()
        again.undo()
        assertEquals("A", again.title)
        assertEquals(2, again.caret)
    }

    @Test
    fun `Insert table writes nothing where no field would show it, and says where to tap`() {
        // The cursor never in the note, the table went to its end -- past
        // what the note draws, out of sight -- one more at every tap.
        val model = editor((List(Editing.MAX_DRAWN_LINES) { "x" } + "past").joinToString("\\n"))
        val before = model.body
        assertEquals(null, model.insertTable(2, 2))
        assertEquals(before, model.body)
        assertTrue(state.snackbar.currentSnackbarData!!.visuals.message.startsWith("The end of this note is more than"))
        // An ordinary note: at its end, a grid, as ever.
        val plain = editor("Costs")
        assertEquals(1, plain.insertTable(2, 2))
        assertTrue(Editing.segments(plain.body).any { it.table && it.drawn && it.firstLine == 1 })
    }
}
