package ro.noteorganizer.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun editor(body: String, formatting: String = "[]", title: String = "A"): EditorModel {
        val dir = NioFiles.createTempDirectory("editortyping").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "$title", "body": "$body", "category": "General", "formatting": $formatting}]""")
        File(dir, "attachments").mkdirs()
        state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        return EditorModel(state, state.note(1))
    }

    /** [text] typed so the field holds [now], its caret after the last letter typed, at [caret]. */
    private fun EditorModel.type(now: String, caret: Int) {
        typeInto(Editing.segments(body).first(), TextFieldValue(now, TextRange(caret)), 0)
    }

    /** The editor's paste of [plain] (and [html]) over [from, to): what the menu's Paste does. */
    private fun EditorModel.pasteAt(from: Int, plain: String, html: String? = null, to: Int = from): Boolean {
        val segment = Editing.segments(body).first()
        return paste(segment, TextFieldValue(segment.text, TextRange(from, to)), html, plain)
    }

    /** The menu's Cut of [from, to): the copy it makes, then the words taken out. */
    private fun EditorModel.cutAt(from: Int, to: Int): String? {
        val segment = Editing.segments(body).first()
        pick(segment, TextFieldValue(segment.text, TextRange(from, to)))
        val html = copyOut(body.substring(from, to))
        type(body.removeRange(from, to), from)
        return html
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
        // Enter at the end of a note drawing all the lines a body may: the new
        // line went into the read-only rest, the field kept the keyboard, and
        // the next words were glued onto the line above.
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
    fun `a restore that brings back the day deleted, from that day, leaves it for the whole note`() {
        // The editor stayed on the day, which the restored series had
        // deleted, and every save there said "Saved for this day only" to
        // a day no screen showed and no search found.
        val dir = NioFiles.createTempDirectory("editorrestoregone").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "t", "title": "Team meeting", "body": "Agenda",
            "category": "General", "date": "2026-11-03", "recurrence": {"freq": "weekly", "weekdays": [1]},
            "_prev": {"title": "Team meeting", "body": "Agenda", "exceptions": ["2026-11-17"]}}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-11-17")
        val model = EditorModel(state, state.note(1), day = state.openDay)
        assertTrue(Notebook.restorePrevious(state.load, 1) is SaveResult.Ok)
        model.reloadFrom(state.note(1)!!)
        assertEquals(null, state.openDay)
        assertEquals(1, state.openId)
    }

    @Test
    fun `a day showing the series' words shows its lines' looks, and none once it has its own text`() {
        // Centred and indented on the whole series and on the desktop's day,
        // flat on every day of it here.
        val dir = NioFiles.createTempDirectory("editordaylines").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "p", "title": "Plan", "body": "Agenda\nsecond",
            "category": "General", "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 0, "align": "center"}, {"line": 1, "indent": 1}],
            "overrides": {"2026-11-16": {"body": "own words\nhere"}}}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        val series = EditorModel(state, state.note(1))
        state.openNote(1, "2026-11-09")
        val day = EditorModel(state, state.note(1), day = state.openDay)
        assertEquals(series.props, day.props)
        assertEquals("center", day.props[0]?.align)
        state.openNote(1, "2026-11-16")
        assertEquals(emptyMap<Int, Paragraphs.Props>(), EditorModel(state, state.note(1), day = state.openDay).props)
        // ...but a list's depth, which lives in its entries alone, stays where
        // the day's line keeps its marker: on the desktop a nested numbered
        // list read flat and the next Enter renumbered it.
        File(dir, "notes_data.json").writeText("""[{"id": 2, "uid": "a", "title": "Agenda", "body": "Agenda\n1. Buy\n1. milk",
            "category": "General", "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 1, "kind": "number"}, {"line": 2, "kind": "number", "indent": 1}],
            "overrides": {"2026-11-16": {"body": "Agenda today\n1. Buy\n1. milk"}}}]""")
        val nested = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        nested.openNote(2, "2026-11-16")
        val listDay = EditorModel(nested, nested.note(2), day = nested.openDay)
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1)), listDay.props)
        // ...a paste there keeps it: renumbered with no depth, the nested list
        // read as one run, stored.
        val field = Editing.segments(listDay.body).first()
        listDay.pastePlain(field, TextFieldValue(field.text, TextRange("Agenda today".length)), " (room 4)")
        assertEquals("Agenda today (room 4)\n1. Buy\n1. milk", listDay.body.trimEnd('\n'))
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1)), listDay.props)
        // ...and the day keeps its own list lines once saved, read back as it
        // keeps them, not by line from the series (the owner's choice).
        assertTrue(listDay.save())
        val stored = JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0)
            .getJSONObject("overrides").getJSONObject("2026-11-16").getJSONArray("paragraphs")
        assertEquals(2, stored.length())
        assertEquals(1, stored.getJSONObject(1).optInt("indent"))
        val again = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        again.openNote(2, "2026-11-16")
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1)), EditorModel(again, again.note(2), day = again.openDay).props)
        // ...a day whose lines moved -- a guest added above the list -- reads
        // its own depth, where the series' by line put Buy in the sub-list.
        File(dir, "notes_data.json").writeText("""[{"id": 2, "uid": "a", "title": "Agenda", "body": "Agenda\n1. Buy\n1. milk",
            "category": "General", "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 1, "kind": "number"}, {"line": 2, "kind": "number", "indent": 1}],
            "overrides": {"2026-11-23": {"body": "Agenda\nGuest: Ana\n1. Buy\n1. milk",
                "paragraphs": [{"line": 2, "kind": "number"}, {"line": 3, "kind": "number", "indent": 1}]}}}]""")
        val moved = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        moved.openNote(2, "2026-11-23")
        assertEquals(mapOf(3 to Paragraphs.Props(indent = 1)), EditorModel(moved, moved.note(2), day = moved.openDay).props)
    }

    @Test
    fun `on a day, Enter then Backspace at a nested item's start keep its depth, and the day follows the series again`() {
        // The list rules ran on the whole note only: on a day the item lost
        // its depth, and since a day keeps its lists, that was stored and the
        // day cut off from the series.
        val dir = NioFiles.createTempDirectory("editordayenter").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "t", "title": "Agenda",
            "body": "Agenda\n1. Plan the trip\n1. Book hotel\n2. Pack\n2. Go", "category": "General",
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 1, "kind": "number"}, {"line": 2, "kind": "number", "indent": 1},
                           {"line": 3, "kind": "number", "indent": 1}, {"line": 4, "kind": "number"}]}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-11-09")
        val day = EditorModel(state, state.note(1), day = state.openDay)
        val words = day.body
        val start = "Agenda\n1. Plan the trip\n".length
        day.type(words.substring(0, start) + "\n" + words.substring(start), start + 1)
        assertEquals(words.substring(0, start) + "\n" + words.substring(start), day.body)
        day.type(words, start)
        assertEquals(words, day.body)
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1), 3 to Paragraphs.Props(indent = 1)), day.props)
        assertTrue(day.save())
        assertEquals("follows", day.status)
        // ...a cut across two nested items pasted straight back keeps the depth
        // of the line it went into, as the desktop's does; lines pasted after an
        // item's end keep its depth too, where the desktop's keep its centring alone.
        val cutFrom = words.indexOf("hotel")
        val cutTo = words.indexOf("ck\n2. Go")
        day.type(words.removeRange(cutFrom, cutTo), cutFrom)
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1)), day.props)
        day.type(words, cutTo)
        assertEquals(words, day.body)
        assertEquals(mapOf(2 to Paragraphs.Props(indent = 1), 3 to Paragraphs.Props(indent = 1)), day.props)
        val endOfPack = words.indexOf("\n2. Go")
        val pasted = words.substring(0, endOfPack) + "\n2. Passports\nTickets" + words.substring(endOfPack)
        day.type(pasted, endOfPack + "\n2. Passports\nTickets".length)
        assertEquals(Paragraphs.Props(indent = 1), day.props[4])
        // ...but a line put in at a line's very start moves that line whole, its
        // own depth kept, the new one not given it (the desktop's tags): on the
        // whole note, where a plain line can carry an indent.
        state.openNote(1)
        val whole = EditorModel(state, state.note(1))
        val atStart = whole.body.indexOf("1. Book hotel")
        whole.type(whole.body.substring(0, atStart) + "Note\n" + whole.body.substring(atStart), atStart + "Note\n".length)
        assertEquals(null, whole.props[2])
        assertEquals(Paragraphs.Props(indent = 1), whole.props[3])
    }

    @Test
    fun `a list line cut and pasted back on a day is the line again, never a word corrected with Enter`() {
        // Read as the keyboard's corrected word and its Enter, "• Eggs" and its
        // break pasted before "• Bread" gave "• • Bread", stored, the day cut
        // off from its series.
        val dir = NioFiles.createTempDirectory("editordaypaste").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "t", "title": "Shopping",
            "body": "Shopping\n• Milk\n• Eggs\n• Bread\nCall mum", "category": "General",
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 1, "kind": "bullet"}, {"line": 2, "kind": "bullet"},
                           {"line": 3, "kind": "bullet"}]}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-11-09")
        val day = EditorModel(state, state.note(1), day = state.openDay)
        val words = day.body
        val eggs = words.indexOf("• Eggs")
        val line = "• Eggs\n"
        val cut = words.removeRange(eggs, eggs + line.length)
        // The editor's paste, and the keyboard's clipboard, which the app cannot tell from typing.
        for (byEditor in listOf(true, false)) {
            day.type(cut, eggs)
            assertEquals(cut, day.body)
            if (byEditor) assertTrue(day.pasteAt(eggs, line)) else day.type(words, eggs + line.length)
            assertEquals("pasted by the editor: $byEditor", words, day.body)
        }
        assertTrue(day.save())
        assertEquals("follows", day.status)
        // ...and before a plain line it makes no list of it.
        val mum = words.indexOf("Call mum")
        day.type(words.substring(0, mum) + line + words.substring(mum), mum + line.length)
        assertTrue(day.body, day.body.endsWith("\n• Eggs\nCall mum"))
        day.save()
        // A paste ending in a break at an item's end adds no item: the
        // desktop's paste puts the words in alone.
        state.openNote(1)
        val whole = EditorModel(state, state.note(1))
        assertTrue(whole.pasteAt(whole.body.indexOf("\n• Eggs"), " and eggs\n"))
        assertTrue(whole.body, whole.body.startsWith("Shopping\n• Milk and eggs\n\n• Eggs"))
    }

    @Test
    fun `on a day a numbered item cut and pasted back is renumbered, and a nested one keeps its level by its copy`() {
        // Left to the field, the paste read 1, 2, 2 and a nested item went to
        // the margin, the day stored apart from its series: a day's copy was
        // plain.
        val dir = NioFiles.createTempDirectory("editordaycut").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "t", "title": "Trip",
            "body": "Trip\n1. Plan the trip\n1. Book hotel\n2. Pack\n2. Go", "category": "General",
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "paragraphs": [{"line": 1, "kind": "number"}, {"line": 2, "kind": "number", "indent": 1},
                           {"line": 3, "kind": "number", "indent": 1}, {"line": 4, "kind": "number"}]}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-11-09")
        val day = EditorModel(state, state.note(1), day = state.openDay)
        val words = day.body
        val nested = mapOf(2 to Paragraphs.Props(indent = 1), 3 to Paragraphs.Props(indent = 1))
        val book = words.indexOf("1. Book hotel")
        val bookLine = words.substring(book, words.indexOf("2. Pack"))
        val html = day.cutAt(book, book + bookLine.length)
        assertTrue(day.pasteAt(book, bookLine, html))
        assertEquals(words, day.body)
        assertEquals(nested, day.props)
        val plan = words.indexOf("1. Plan the trip")
        val planLine = words.substring(plan, book)
        val planHtml = day.cutAt(plan, book)
        assertTrue(day.body, day.body.endsWith("\n1. Go"))
        assertTrue(day.pasteAt(plan, planLine, planHtml))
        assertEquals(words, day.body)
        assertEquals(nested, day.props)
        assertTrue(day.save())
        assertEquals("follows", day.status)
    }

    @Test
    fun `a day the series does not draw opens the whole note, asked after the open note is saved`() {
        // Opened on it, every save was refused, and every way out that saves
        // first: only "Leave anyway" was left.
        val dir = NioFiles.createTempDirectory("editoropenundrawn").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "g", "title": "Gym", "body": "plan",
            "category": "General", "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "exceptions": ["2026-11-16"]}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        state.openNote(1, "2026-11-16")
        assertEquals(1, state.openId)
        assertEquals(null, state.openDay)
        state.openNote(1, "2026-11-09")
        assertEquals("2026-11-09", state.openDay)
        // The open note's own save moves the start past the day tapped, as a
        // date changed a moment before the tap on two panes: asked after it.
        state.openNote(1)
        state.flush = {
            Notebook.updateNote(state.load, 1, "Gym", "plan", date = "2026-11-16")
            state.refresh()
            null
        }
        state.openNote(1, "2026-11-09")
        assertEquals(null, state.openDay)
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
        // B, then ↶ on the same bar, then ⊞: Undo emptied the cursor's place, so
        // the table went to the note's end -- onto the table closing it, refused
        // with words about a note too large.
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

    @Test
    fun `the calculator's place moves with Undo and Redo, so each answer still goes on a line of its own`() {
        // An Undo or Redo of the words emptied the last answer's place, and the
        // next was glued on at the cursor: "Total: 2+2=43×3=9", a false number.
        // The desktop's Tk mark moves through Ctrl+Z and Ctrl+Y.
        val model = editor("Total: ")
        model.caret = 7
        model.insertCalculation("2+2=4")
        model.undo()
        assertEquals("Total: ", model.body)
        model.redo()
        model.insertCalculation("3×3=9")
        assertEquals("Total: 2+2=4\n3×3=9", model.body)
        // A letter typed elsewhere and taken back: the answer after the last one, not glued to "Budget".
        val budget = editor("Budget\\n")
        budget.caret = 7
        budget.insertCalculation("12+30=42")
        budget.type("Budgets\n12+30=42", 7)
        budget.undo()
        assertEquals("Budget\n12+30=42", budget.body)
        budget.insertCalculation("5×2=10")
        assertEquals("Budget\n12+30=42\n5×2=10", budget.body)
        // Only a style taken back, the words the same: the place stays where it
        // was, the second answer on its own line (which no
        // test held: the cursor's place was read, the calculator's was not).
        val plan = editor("Plan\\nfuel")
        plan.caret = 4
        plan.insertCalculation("5+3=8")
        assertEquals("Plan5+3=8\nfuel", plan.body)
        plan.spans = toggleStyle(plan.spans, 0, 4, "bold", "")
        plan.changed()
        plan.undo()
        assertEquals(emptyList<FormatSpan>(), plan.spans)
        plan.insertCalculation("2+2=4")
        assertEquals("Plan5+3=8\n2+2=4\nfuel", plan.body)
    }

    /** "Budget", the calculator's answer put on the empty line under it, then "Rent". */
    private fun budget(): EditorModel = editor("Budget\\n\\nRent").also {
        it.caret = 7
        it.insertCalculation("2+2=4")
        assertEquals("Budget\n2+2=4\nRent", it.body)
    }

    @Test
    fun `the calculator's place moves through every place a change made, so the next answer goes under the last`() {
        // Followed as one change, a list's markers, Enter in a numbered list or an
        // Undo of letters in two places sent the next answer into a later line:
        // "3*3=9Rent". The desktop's Tk mark moves through each edit in turn.
        fun EditorModel.bullets() =
            applyLines(Paragraphs.toggleList(body, props, 0, 2, "bullet"), 0, 0, body.length)
        // A: bullets over the three lines, then Insert.
        val a = budget()
        a.bullets()
        assertEquals("• Budget\n• 2+2=4\n• Rent", a.body)
        a.insertCalculation("3*3=9")
        assertEquals("• Budget\n• 2+2=4\n3*3=9\n• Rent", a.body)
        // B: the bullets taken back, then Insert.
        val b = budget()
        b.bullets()
        b.undo()
        assertEquals("Budget\n2+2=4\nRent", b.body)
        b.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n3*3=9\nRent", b.body)
        // E: Undo, then Redo, of the bullets alone, then Insert.
        val e = budget()
        e.bullets()
        e.undo()
        e.redo()
        e.insertCalculation("3*3=9")
        assertEquals("• Budget\n• 2+2=4\n3*3=9\n• Rent", e.body)
        // D: Enter at the end of a numbered list's first item, the answer in the second.
        val d = editor("1. Bread\\n2. Milk total: \\n3. Rent")
        d.caret = 24
        d.insertCalculation("2+2=4")
        assertEquals("1. Bread\n2. Milk total: 2+2=4\n3. Rent", d.body)
        d.type("1. Bread\n\n2. Milk total: 2+2=4\n3. Rent", 9)
        assertEquals("1. Bread\n2. \n3. Milk total: 2+2=4\n4. Rent", d.body)
        d.insertCalculation("3*3=9")
        assertEquals("1. Bread\n2. \n3. Milk total: 2+2=4\n3*3=9\n4. Rent", d.body)
        // A paste that renumbers the list below it: a table row put above the answer.
        val p = editor("1. Bread\\n2. Total \\n3. Rent")
        p.caret = 18
        p.insertCalculation("2+2=4")
        val field = Editing.segments(p.body).first()
        assertTrue(p.paste(field, TextFieldValue(field.text, TextRange(9)), null, "| a | b |"))
        assertEquals("1. Bread\n| a | b |\n1. Total 2+2=4\n2. Rent", p.body)
        p.insertCalculation("3*3=9")
        assertEquals("1. Bread\n| a | b |\n1. Total 2+2=4\n3*3=9\n2. Rent", p.body)
    }

    @Test
    fun `an Undo of letters typed in two places carries the calculator's place back through each`() {
        // One step of Undo only when typed within a second (History joins by the
        // clock), assumed here alone: an Assume ends its whole method, unrun cases and all.
        // C: a letter after "Budget" and one before "Rent", one step of Undo.
        val c = budget()
        val steps = c.undoSteps
        c.type("Budget!\n2+2=4\nRent", 7)
        c.type("Budget!\n2+2=4\nXRent", 15)
        org.junit.Assume.assumeTrue("the two letters typed within a second, one step", c.undoSteps == steps + 1)
        c.undo()
        assertEquals("Budget\n2+2=4\nRent", c.body)
        c.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n3*3=9\nRent", c.body)
        // ...and the other way round, the letter above the answer typed second:
        // each letter of the step carried back, not the first alone.
        val c2 = budget()
        val steps2 = c2.undoSteps
        c2.type("Budget\n2+2=4\nXRent", 14)
        c2.type("Budget!\n2+2=4\nXRent", 7)
        org.junit.Assume.assumeTrue("the two letters typed within a second, one step", c2.undoSteps == steps2 + 1)
        c2.undo()
        assertEquals("Budget\n2+2=4\nRent", c2.body)
        c2.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n3*3=9\nRent", c2.body)
    }

    @Test
    fun `Enter typed at the calculator's place, a line below it, carries the place after it, as the desktop's mark`() {
        // Read off the two texts, Enter right after the answer was the break after
        // the old one, and the next answer went above the words typed under the last.
        // Read at the cursor, as Tk's mark of right gravity sees a key go in.
        val food = budget()
        food.type("Budget\n2+2=4\n\nRent", 13)
        food.type("Budget\n2+2=4\nFood\nRent", 17)
        food.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\nFood\n3*3=9\nRent", food.body)
        // Enter alone, then Insert: under the empty line, as the desktop puts it.
        val empty = budget()
        empty.type("Budget\n2+2=4\n\nRent", 13)
        empty.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n\n3*3=9\nRent", empty.body)
        // One Undo of the Enter takes the place back with it.
        val undone = budget()
        undone.type("Budget\n2+2=4\n\nRent", 13)
        undone.undo()
        undone.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n3*3=9\nRent", undone.body)
        // A line break taken away, of two in a row, is the one the cursor was
        // after: Backspace at the empty line's start. Read off the texts it
        // was the second, the place stayed on "Rent", and the answer was glued.
        val back = budget()
        back.type("Budget\n2+2=4\n\nRent", 13)
        back.type("Budget\n2+2=4\nRent", 12)
        back.insertCalculation("3*3=9")
        assertEquals("Budget\n2+2=4\n3*3=9\nRent", back.body)
    }

    @Test
    fun `Enter at the end of a centred line, a line below it, keeps the centring on both halves`() {
        // The phone's own rule, as the desktop's tags lie over the text: read off
        // the texts, the break was the next line's start, and the new line was
        // left at the margin, where at the note's end it was centred.
        val model = editor("Packing list\\nPassport, charger")
        model.props = mapOf(0 to Paragraphs.Props(align = "center"))
        model.type("Packing list\n\nPassport, charger", 13)
        assertEquals(mapOf(0 to Paragraphs.Props(align = "center"), 1 to Paragraphs.Props(align = "center")), model.props)
    }

    @Test
    fun `a table put in at the calculator's place carries the place after it`() {
        // Made where the cursor was, not read off the texts: put in right
        // after the answer, it was read as put in a line on, and the next
        // answer went above the table.
        val model = editor("Total \\nRent")
        model.caret = 6
        model.insertCalculation("2+2=4")
        assertEquals("Total 2+2=4\nRent", model.body)
        assertTrue(model.insertTable(1, 2) != null)
        model.insertCalculation("3*3=9")
        assertTrue("the answer under the table: ${model.body}", model.body.indexOf("3*3=9") > model.body.lastIndexOf("|"))
    }

    @Test
    fun `a paste cut at a box's cap is cut where the cursor put it in, and said only when words are left out`() {
        // Read again off the texts at the cap, " fresh" pasted after "Buy" kept
        // "fre" glued to "milk" (the desktop: "Buy fr milk"), and a paste whose
        // trailing space was all the cap took was said to be shortened.
        val title = "A".repeat(189) + "Buy milk"
        val model = editor("x", title = title)
        model.typeTitle(TextFieldValue(title, TextRange(192)), TextFieldValue("A".repeat(189) + "Buy fresh milk", TextRange(198)))
        assertEquals("A".repeat(189) + "Buy fr milk", model.title)
        assertEquals("Pasted title shortened to 200 characters", state.snackbar.currentSnackbarData?.visuals?.message)
        val spaced = editor("x", title = "A".repeat(199))
        spaced.typeTitle(TextFieldValue("A".repeat(199), TextRange(199)), TextFieldValue("A".repeat(199) + "x ", TextRange(201)))
        assertEquals("A".repeat(199) + "x", spaced.title)
        assertEquals("nothing said", null, state.snackbar.currentSnackbarData)
        // Every other capped box keeps its words the one way (keptBy): a row,
        // "A new item...", a template's name and title, the person's name.
        for ((limit, what) in listOf(Editing.CHECKLIST_ROW_CHARS to "A checklist item", Editing.TITLE_CHARS to "A template name",
                                     Editing.TITLE_CHARS to "A note title", 80 to "Your name")) {
            val said = ArrayList<String>()
            val cap = Editing.BoxCap({ said.add(it) }, limit, what)
            val pad = "a".repeat(limit - 11)
            val kept = keptBy(cap, TextFieldValue(pad + "Buy milk", TextRange(pad.length + 3)),
                              TextFieldValue(pad + "Buy fresh milk", TextRange(pad.length + 9)))
            assertEquals(what, TextFieldValue(pad + "Buy fr milk", TextRange(pad.length + 6)), kept)
            assertEquals(what, 1, said.size)
            val full = "b".repeat(limit - 1)
            val trailing = keptBy(cap, TextFieldValue(full, TextRange(limit - 1)), TextFieldValue("${full}x ", TextRange(limit + 1)))
            assertEquals(what, "${full}x", trailing.text)
            assertEquals("$what: a trailing space alone, not said", 1, said.size)
        }
    }

    @Test
    fun `a plain paste in CR LF goes into the note's text as the desktop's paste puts it, one break a line`() {
        // The field's own paste, or a keyboard's clipboard chip, kept every
        // CR in the file; the capped boxes
        // already took CR LF as one break.
        val model = editor("Shopping: ")
        val field = Editing.segments(model.body).first()
        val shown = model.typeInto(field, TextFieldValue("Shopping: Milk\r\nEggs\r\nBread", TextRange(27)), 0)
        assertEquals("Shopping: Milk\nEggs\nBread", model.body)
        assertEquals("the field shows the words as kept, the cursor after them",
                     TextFieldValue("Shopping: Milk\nEggs\nBread", TextRange(25)), shown)
        // A CR the words already held is not this edit's, and stays.
        val held = editor("a\\rb")
        held.type("a\rbX", 4)
        assertEquals("a\rbX", held.body)
    }

    @Test
    fun `a title holding a line break takes the letters typed into it, and a tap rewrites nothing`() {
        // A share code, a desktop template or an older phone can leave a break in
        // a title: every letter typed into it was taken for Enter and dropped, and
        // a tap that only moved the cursor rewrote the title and saved the note.
        val model = editor("x", title = "Groceries\\n")
        assertEquals("Groceries\n", model.title)
        val shown = TextFieldValue(model.title, TextRange(model.title.length))
        val edits = model.edits
        val tapped = model.typeTitle(shown, shown.copy(selection = TextRange(3)))
        assertEquals("a tap writes nothing", "Groceries\n", model.title)
        assertEquals(edits, model.edits)
        assertEquals(TextRange(3), tapped.selection)
        assertFalse("a letter after the break is no Enter", Editing.isEnter(shown.text, "Groceries\nl"))
        val typed = model.typeTitle(shown, TextFieldValue("Groceries\nl", TextRange(11)))
        assertEquals("the letter kept, the title one line", "Groceries l", model.title)
        assertEquals(TextRange(11), typed.selection)
        assertEquals("x", model.body)
    }

    @Test
    fun `a title holding a break is shown on one line, so words typed at once are taken as they came`() {
        // Shown with its break, the first letter rewrote the box under the
        // keyboard and letters arriving in that moment were lost: " list"
        // came back " li" (the walk).
        val model = editor("x", title = "Groceries\\n")
        val box = model.titleBox(TextFieldValue())
        assertEquals("Groceries ", box.text)
        assertEquals(TextRange(10), box.selection)
        val moved = box.copy(selection = TextRange(3))
        assertEquals("the cursor where the person put it", moved, model.titleBox(moved))
        val edits = model.edits
        model.typeTitle(box, moved)
        assertEquals("a tap writes nothing", "Groceries\n", model.title)
        assertEquals(edits, model.edits)
        val came = TextFieldValue("Groceries  list", TextRange(15))
        assertEquals("what came in is the box's, nothing rewritten", came, model.typeTitle(box, came))
        assertEquals("Groceries  list", model.title)
        assertEquals(came, model.titleBox(came))
    }

    @Test
    fun `lines pasted into a title go in as the desktop's title paste puts them, said shortened only when cut`() {
        // Weighed as copied, a line copied with its break, or two lines in CR
        // LF, read as shortened at the cap, and the CR stayed in the title
        // (the desktop's 6443d2c).
        fun pasteInto(title: String, clip: String): Pair<String, String?> {
            val model = editor("x", title = title)
            val shown = TextFieldValue(title, TextRange(title.length))
            model.typeTitle(shown, TextFieldValue(title + clip, TextRange(title.length + clip.length)))
            return model.title to state.snackbar.currentSnackbarData?.visuals?.message
        }
        assertEquals("t".repeat(191) + "Groceries" to null, pasteInto("t".repeat(191), "Groceries\n"))
        assertEquals("t".repeat(183) + "Line one Line two" to null, pasteInto("t".repeat(183), "Line one\r\nLine two"))
        assertEquals("Shopping Milk, eggs" to null, pasteInto("", "Shopping\r\nMilk, eggs"))
        assertEquals("t".repeat(195) + "Line " to "Pasted title shortened to 200 characters",
                     pasteInto("t".repeat(195), "Line one\r\nLine two"))
    }
}
