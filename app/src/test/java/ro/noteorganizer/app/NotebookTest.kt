package ro.noteorganizer.app

import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These run on the computer, with no phone and no emulator: `gradlew test`.
 *
 * They pin the rules that were hand-ported from the desktop app's Python,
 * because a re-implementation that nothing checks is a guess. Each case
 * below comes from the behaviour the Python defines, not from what this
 * Kotlin happens to do.
 */
class NotebookTest {

    // ---- is_table_row, from core/tables.py ----

    @Test
    fun `a row needs a pipe at both ends`() {
        assertTrue(isTableRow("| Milk | 2 |"))
        assertTrue(isTableRow("   | Milk | 2 |   "))
    }

    @Test
    fun `prose that merely contains a pipe is not a row`() {
        // The Python's own example: a sentence, and a maths expression.
        assertFalse(isTableRow("a | b"))
        assertFalse(isTableRow("| a"))
        assertFalse(isTableRow("a |"))
    }

    @Test
    fun `too short to be a row`() {
        assertFalse(isTableRow(""))
        assertFalse(isTableRow("|"))
        assertFalse(isTableRow("||"))
    }

    @Test
    fun `a row splits into its cells without padding`() {
        assertEquals(listOf("Milk", "2"), splitRow("| Milk |   2 |"))
    }

    // ---- is_recurring, from core/recurrence.py ----

    @Test
    fun `a filled rule with no frequency is not a repeating note`() {
        // This is the whole point: normalize_note gives EVERY note a full
        // rule, so testing the object for emptiness marks a real 77-note
        // notebook as 77 repeating notes. The frequency is the test.
        val json = """
            [{"id": 1, "title": "x", "recurrence":
              {"freq": "", "interval": 1, "weekdays": [], "end": "", "count": 0}}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertFalse(note.isRecurring)
    }

    @Test
    fun `a rule naming a frequency is a repeating note`() {
        val json = """[{"id": 1, "recurrence": {"freq": "weekly", "interval": 2}}]"""
        val note = Notebook.parseNotes(json).single()
        assertTrue(note.isRecurring)
        assertEquals(2, note.recurrenceInterval)
    }

    // ---- the coercions normalize_note promises ----

    @Test
    fun `a wrong type is coerced to the default, never stringified`() {
        // org.json's own optString would hand back the four characters
        // "null" here, and that would be drawn as a title.
        val json = """[{"id": "not a number", "title": null, "category": 7}]"""
        val note = Notebook.parseNotes(json).single()
        assertEquals(0, note.id)
        assertEquals("", note.title)
        assertEquals("General", note.category)
    }

    @Test
    fun `a broken file yields no notes rather than a crash`() {
        assertEquals(0, Notebook.parseNotes("").size)
        assertEquals(0, Notebook.parseNotes("{ not json").size)
        assertEquals(0, Notebook.parseNotes("""{"notes": []}""").size)
    }

    @Test
    fun `entries that are not objects are skipped, the rest survive`() {
        val json = """[{"id": 1}, 5, null, "x", {"id": 2}]"""
        assertEquals(listOf(1, 2), Notebook.parseNotes(json).map { it.id })
    }

    // ---- what the card shows ----

    @Test
    fun `an untitled note borrows its headline from the body`() {
        val json = """[{"id": 1, "title": "  ", "body": "\n\nFirst line\nSecond"}]"""
        val note = Notebook.parseNotes(json).single()
        assertTrue(note.headlineIsBorrowed)
        assertEquals("First line", note.headline)
        assertEquals("Second", note.snippet)
    }

    @Test
    fun `a titled note keeps its whole body as the snippet`() {
        val json = """[{"id": 1, "title": "Title", "body": "First\nSecond"}]"""
        val note = Notebook.parseNotes(json).single()
        assertFalse(note.headlineIsBorrowed)
        assertEquals("Title", note.headline)
        assertEquals("First Second", note.snippet)
    }

    @Test
    fun `table rows stay out of the preview and are counted instead`() {
        val json = """
            [{"id": 1, "body": "My list\n| a | b |\n| c | d |\nAfter the table"}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertEquals("My list", note.headline)
        assertEquals("After the table", note.snippet)
        assertEquals(1, note.tableCount)
    }

    @Test
    fun `a date is shown the desktop's way, and stored its own way`() {
        // show_date in core/recurrence.py. The card used to print the
        // stored form while the editor printed this one: the same date
        // two ways on two screens, found by looking at a screenshot.
        assertEquals("17.10.2026", Recurrence.showDate("2026-10-17"))
        assertEquals("05.01.2026", Recurrence.showDate("2026-1-5"))
        assertEquals("", Recurrence.showDate(""))
        // Not a date: shown exactly as it was typed, never guessed at.
        assertEquals("32 January", Recurrence.showDate("32 January"))
        assertEquals("2026-02-30", Recurrence.showDate("2026-02-30"))
    }

    @Test
    fun `the card's tags line carries the date as the desktop draws it`() {
        val json = """[{"id": 1, "category": "Work", "date": "2026-10-18",
                         "checklist": [{"text": "a", "done": true}, {"text": "b"}]}]"""
        assertEquals("Work   📅 18.10.2026   ☑ 1/2",
            Notebook.parseNotes(json).single().tagLine())
    }

    @Test
    fun `the card's tags line says the repeat rule in the desktop's words`() {
        // describe_recurrence, whole: it used to be "weekly ×2".
        val json = """[{"id": 1, "category": "Work", "date": "2026-10-05",
                         "recurrence": {"freq": "weekly", "interval": 2, "weekdays": [0, 3], "count": 6},
                         "exceptions": ["2026-10-19"], "overrides": {"2026-11-02": {"title": "x"}}}]"""
        assertEquals("Work   🔁 Every 2 weeks on Mon, Thu, 6 times   ✕1   ✎1",
            Notebook.parseNotes(json).single().tagLine())
    }

    @Test
    fun `two separated tables count as two`() {
        val json = """[{"id": 1, "body": "| a | b |\ntext\n| c | d |"}]"""
        assertEquals(2, Notebook.parseNotes(json).single().tableCount)
    }

    @Test
    fun `a row never closed or typed past is still a table line - not the card's words`() {
        // "Only vertical lines": the strict row rule let these through.
        val note = Notebook.parseNotes("""[{"id": 1, "body": "Intro\n| a | b\n| c | d |and more\nAfter"}]""").single()
        assertEquals("Intro", note.headline)
        assertEquals("After", note.snippet)
        assertEquals(1, note.tableCount)
    }

    @Test
    fun `the preview rule answers as the desktop's preview_text does`() {
        // Each answer from the desktop's own function (core/tables.py).
        val cases = listOf(
            "Intro\n| a | b\n| c | d |and more\nAfter" to ("Intro\nAfter" to 1),
            "|x| = 5\n|a|b|c\n| y" to ("|x| = 5\n|a|b|c\n| y" to 0),
            "Top\n\n| a | b |\n| --- | --- |\n\n\nBottom\n\n" to ("Top\n\nBottom" to 1),
            "| a | b |\n\n| c | d |" to ("" to 2),
            "\n\nwords\n \n\t\nmore" to ("words\n \nmore" to 0),
            "| only | table |" to ("" to 1),
        )
        for ((body, want) in cases) assertEquals(body, want, previewText(body))
    }

    @Test
    fun `an untitled note of nothing but a table is untitled, not empty`() {
        val note = Notebook.parseNotes("""[{"id": 1, "body": "| a | b |\n| c | d |"}]""").single()
        assertEquals("(untitled)", note.headline)
        assertEquals("", note.snippet)
        assertEquals("(empty)", Notebook.parseNotes("""[{"id": 2, "body": " \n "}]""").single().headline)
    }

    @Test
    fun `a card is given a glimpse of a long note, never the whole of it`() {
        // Laying out tens of thousands of characters to show two lines is
        // what made the list stutter on a real phone.
        val long = "word ".repeat(20_000)
        val note = Notebook.parseNotes(JSONArray().put(JSONObject().put("id", 1).put("title", "T").put("body", long)).toString()).single()
        assertTrue(note.snippet.length <= 300)
        assertTrue(note.snippet.startsWith("word word"))
        val untitled = Notebook.parseNotes(JSONArray().put(JSONObject().put("id", 1).put("body", long)).toString()).single()
        assertTrue(untitled.headline.length <= 300)
        // Never cut through an emoji.
        val emoji = Notebook.parseNotes(JSONArray().put(JSONObject().put("id", 1).put("title", "T")
            .put("body", "a".repeat(299) + "😀 tail")).toString()).single()
        assertFalse(Character.isHighSurrogate(emoji.snippet.last()))
    }

    @Test
    fun `a message quotes a title, a row or a file's name short, on one line, and says where it cut`() {
        assertEquals("Shopping", quoted("Shopping"))
        assertEquals("a b", quoted("a\nb"))
        val long = quoted("a".repeat(150_000))
        assertEquals(QUOTED, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("a category's longest name whole", "c".repeat(80), quoted("c".repeat(80)))
        // Never through the middle of an emoji.
        val emoji = quoted("a".repeat(QUOTED - 2) + "😀" + "tail")
        assertFalse(Character.isHighSurrogate(emoji[emoji.length - 2]))
        assertTrue(emoji.endsWith("…"))
        // A file keeps its type: it says what will open.
        val file = quotedFile("x".repeat(150_000) + ".pdf")
        assertTrue(file, file.endsWith("….pdf"))
        assertTrue(file.length <= QUOTED)
        assertEquals("Holiday photo.jpg", quotedFile("Holiday photo.jpg"))
        assertEquals("a type too long to be one is cut with the rest", QUOTED, quotedFile("a." + "b".repeat(150_000)).length)
        // A backup's entry name, quoted in the reason it is refused.
        val bomb = Backup.entriesProblem(listOf(Backup.Entry("attachments/" + "n".repeat(60_000), 60_000_000, 1_000)))!!
        assertTrue(bomb.length.toString(), bomb.length < 300)
        // And the bar's own last guard.
        assertEquals("short", forTheScreen("short"))
        assertEquals(MAX_SAID, forTheScreen("y".repeat(200_000)).length)
    }

    // ---- code points vs UTF-16, the offset trap ----

    @Test
    fun `an emoji shifts every offset after it`() {
        // Python sees "a", the emoji, "b" -- three characters. Kotlin sees
        // four units, because the emoji is a surrogate pair. A span the
        // desktop stored as starting at 2 must be drawn at 3.
        val text = "a👣b"
        assertEquals(4, text.length)
        val map = utf16Offsets(text, listOf(0, 1, 2, 3))
        assertEquals(0, map[0])
        assertEquals(1, map[1])
        assertEquals(3, map[2])
        assertEquals(4, map[3])
    }

    @Test
    fun `plain text needs no shifting at all`() {
        val map = utf16Offsets("abcdef", listOf(0, 3, 6))
        assertEquals(mapOf(0 to 0, 3 to 3, 6 to 6), map)
    }

    @Test
    fun `offsets outside the text are clamped, not thrown`() {
        val map = utf16Offsets("abc", listOf(-5, 99))
        assertEquals(0, map[-5])
        assertEquals(3, map[99])
    }

    @Test
    fun `offsets are resolved in one walk regardless of order asked`() {
        val text = "👣x👣y"
        val forwards = utf16Offsets(text, listOf(0, 1, 2, 3, 4))
        val backwards = utf16Offsets(text, listOf(4, 3, 2, 1, 0))
        assertEquals(forwards, backwards)
        // emoji, x, emoji, y -> 0, 2, 3, 5, 6
        assertEquals(listOf(0, 2, 3, 5, 6), (0..4).map { forwards[it] })
    }

    // ---- formatting spans ----

    @Test
    fun `a span with no style or no range is dropped at the door`() {
        val json = """
            [{"id": 1, "body": "abcdef", "formatting": [
              {"start": 0, "end": 3, "style": "bold"},
              {"start": 3, "end": 3, "style": "italic"},
              {"start": 4, "end": 2, "style": "italic"},
              {"start": 0, "end": 2, "style": ""},
              {"start": -1, "end": 2, "style": "bold"},
              "not an object"
            ]}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertEquals(1, note.formatting.size)
        assertEquals("bold", note.formatting.single().style)
    }

    @Test
    fun `a colour span keeps its value`() {
        val json = """
            [{"id": 1, "body": "abc", "formatting":
              [{"start": 0, "end": 3, "style": "text_color", "value": "#ff0000"}]}]
        """.trimIndent()
        val span = Notebook.parseNotes(json).single().formatting.single()
        assertEquals("#ff0000", span.value)
    }

    @Test
    fun `checklist items keep their text and their state`() {
        val json = """
            [{"id": 1, "checklist": [{"text": "one", "done": true},
                                     {"text": "two", "done": false}]}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertEquals(2, note.checklistTotal)
        assertEquals(1, note.checklistDone)
        assertEquals("one", note.checklist.first().text)
    }

    // ---- the rendering, end to end ----

    @Test
    fun `a span after an emoji is drawn where the text actually is`() {
        // "x" then an emoji then "BOLD". The desktop counted characters, so
        // it stored 2..6. Kotlin's indices for the same four letters are
        // 3..7, and drawing at 2..6 would embolden half the emoji and miss
        // the last letter.
        val json = """
            [{"id": 1, "body": "x👣BOLD", "formatting":
              [{"start": 2, "end": 6, "style": "bold"}]}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        val rendered = renderBody(note)
        val range = rendered.text.spanStyles.single()
        assertEquals(3, range.start)
        assertEquals(7, range.end)
        assertEquals("BOLD", note.body.substring(range.start, range.end))
    }

    @Test
    fun `a style this screen cannot draw is admitted, not silently dropped`() {
        // A face the desktop offers is drawn as its kind now (Consolas,
        // fixed-width); one it does not offer is still admitted to.
        val json = """
            [{"id": 1, "body": "abc", "formatting": [
              {"start": 0, "end": 3, "style": "font_family", "value": "Comic Sans"},
              {"start": 0, "end": 3, "style": "font_family", "value": "Consolas"},
              {"start": 0, "end": 3, "style": "bold"}]}]
        """.trimIndent()
        val rendered = renderBody(Notebook.parseNotes(json).single())
        assertEquals(setOf("font_family"), rendered.ignoredStyles)
        assertEquals("bold, and Consolas as fixed-width", 2, rendered.text.spanStyles.size)
    }

    @Test
    fun `letters both underlined and struck through are drawn with both lines`() {
        // Two styles naming a decoration do not add up -- the later won, and
        // the strike under an underline was not drawn while S said it was.
        val json = """
            [{"id": 1, "body": "abcdef", "formatting": [
              {"start": 0, "end": 4, "style": "strike"},
              {"start": 2, "end": 6, "style": "underline"}]}]
        """.trimIndent()
        val drawn = renderBody(Notebook.parseNotes(json).single()).text.spanStyles
            .filter { it.item.textDecoration != null }
            .map { Triple(it.start, it.end, it.item.textDecoration) }
        assertEquals(listOf(
            Triple(0, 2, androidx.compose.ui.text.style.TextDecoration.LineThrough),
            Triple(2, 4, androidx.compose.ui.text.style.TextDecoration.combine(listOf(
                androidx.compose.ui.text.style.TextDecoration.LineThrough,
                androidx.compose.ui.text.style.TextDecoration.Underline))),
            Triple(4, 6, androidx.compose.ui.text.style.TextDecoration.Underline),
        ), drawn)
    }

    @Test
    fun `underline and strike over many spans are merged in one sweep`() {
        // Every other letter struck through under one underline: merged by
        // asking every span at each pair of edges, 60,000 spans took some
        // seven billion tests, at every drawing of the field. One sweep takes
        // milliseconds; two seconds is a margin no machine speed crosses.
        val under = androidx.compose.ui.text.style.TextDecoration.Underline
        val strike = androidx.compose.ui.text.style.TextDecoration.LineThrough
        val n = 60_000
        val lines = listOf(Triple(0, 2 * n, under)) + List(n) { Triple(2 * it, 2 * it + 1, strike) }
        val started = System.nanoTime()
        val merged = decorations(lines)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("$ms ms", ms < 2_000)
        assertEquals(2 * n, merged.size)
        assertEquals(Triple(0, 1, androidx.compose.ui.text.style.TextDecoration.combine(listOf(under, strike))), merged[0])
        assertEquals(Triple(1, 2, under), merged[1])
        assertEquals(Triple(2 * n - 1, 2 * n, under), merged.last())
    }

    @Test
    fun `a span running past the end of the body collapses instead of throwing`() {
        val json = """
            [{"id": 1, "body": "abc", "formatting":
              [{"start": 10, "end": 20, "style": "bold"}]}]
        """.trimIndent()
        val rendered = renderBody(Notebook.parseNotes(json).single())
        assertEquals(0, rendered.text.spanStyles.size)
    }

    @Test
    fun `a bad colour value is refused rather than drawn as black`() {
        val json = """
            [{"id": 1, "body": "abc", "formatting":
              [{"start": 0, "end": 3, "style": "text_color", "value": "reddish"}]}]
        """.trimIndent()
        val rendered = renderBody(Notebook.parseNotes(json).single())
        assertEquals(0, rendered.text.spanStyles.size)
        assertEquals(setOf("text_color"), rendered.ignoredStyles)
    }

    @Test
    fun `a highlight is blended toward the page, not painted solid`() {
        // A palette meant for a white page is a pale wash that buries light
        // text on a dark one. The stored colour must stay pure; only the
        // paint moves. White at 45% over black is 115 of 255.
        val json = """
            [{"id": 1, "body": "abc", "formatting":
              [{"start": 0, "end": 3, "style": "highlight", "value": "#ffffff"}]}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertEquals("#ffffff", note.formatting.single().value)

        val painted = renderBody(note, Color.Black).text.spanStyles.single().item.background
        assertEquals(0.45f, painted.red, 0.01f)
        assertTrue("the highlight must not stay solid white", painted.red < 0.9f)
    }

    @Test
    fun `blending toward a light page keeps the same colour usable there`() {
        val white = Color.White
        val onDark = blend(Color.White, Color.Black, 0.45f)
        val onLight = blend(Color.White, white, 0.45f)
        // The same stored colour lands dark on a dark page and light on a
        // light one -- that is the whole point of blending per theme.
        assertTrue(onDark.red < onLight.red)
    }

    // ---- search, from _query_matches_note ----

    @Test
    fun `search looks in the title, the body and the comments`() {
        val json = """
            [{"id": 1, "title": "Shopping", "body": "milk", "comments": "urgent"}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertTrue(note.matches("shop"))
        assertTrue(note.matches("milk"))
        assertTrue(note.matches("urgent"))
        assertFalse(note.matches("bread"))
    }

    @Test
    fun `search ignores letter case in both directions`() {
        val note = Notebook.parseNotes("""[{"id": 1, "title": "MILK"}]""").single()
        assertTrue(note.matches("milk"))
        assertTrue(Notebook.parseNotes("""[{"id": 1, "title": "milk"}]""")
            .single().matches("MILK"))
    }

    @Test
    fun `an empty search matches everything`() {
        assertTrue(Notebook.parseNotes("""[{"id": 1}]""").single().matches(""))
    }

    @Test
    fun `search reads inside a table's cells, not its plumbing`() {
        val json = """
            [{"id": 1, "body": "| Milk | 2 |\n| --- | --- |\n| Bread | [x] |"}]
        """.trimIndent()
        val note = Notebook.parseNotes(json).single()
        assertTrue("the words in the cells are found", note.matches("milk"))
        assertTrue(note.matches("bread"))
        // The separator row is mechanics. Searching "---" used to match
        // every note that had a table at all, which is not a search.
        assertFalse("the separator is not content", note.matches("---"))
        // A checkbox is a control, not a word.
        assertFalse("the tick box is not content", note.matches("[x]"))
    }

    @Test
    fun `the category is not searched, matching the desktop`() {
        val json = """[{"id": 1, "title": "x", "category": "Work"}]"""
        assertFalse(Notebook.parseNotes(json).single().matches("work"))
    }

    @Test
    fun `content_text leaves prose alone and strips only tables`() {
        assertEquals("only text", contentText("only text"))
        assertEquals("a b", contentText("| a | b |"))
        assertEquals("", contentText("| --- | --- |"))
        // A break marker inside a cell is the gap it draws.
        assertEquals("one two", contentText("| one<br>two | ^ |"))
    }

    @Test
    fun `a separator row is recognised with alignment colons and hidden columns`() {
        assertTrue(isSeparatorRow("| --- | :--- | ---: | :---: |"))
        assertTrue(isSeparatorRow("| ~ | --- |"))
        assertFalse(isSeparatorRow("| a | --- |"))
        assertFalse(isSeparatorRow("| | |"))
    }

    @Test
    fun `a checkbox cell is read in both spellings`() {
        assertEquals(false, checkboxState("[ ]"))
        assertEquals(true, checkboxState("[x]"))
        assertEquals(true, checkboxState("[X]"))
        assertEquals(null, checkboxState("text"))
    }

    // ---- writing, the first thing here that can lose data ----

    private fun scratch(contents: String?): Notebook.Load {
        // A real directory, far from anything the user owns. Unit tests run
        // on the computer's JVM, so java.nio is available here even though
        // the app's minimum Android version predates it.
        val dir = Files.createTempDirectory("notebook").toFile()
        dir.deleteOnExit()
        if (contents != null) File(dir, "notes_data.json").writeText(contents)
        return Notebook.load(listOf(dir))
    }

    private fun onDisk(load: Notebook.Load): JSONArray =
        JSONArray(load.file!!.readText())

    @Test
    fun `a failed save leaves the note as it was, and the same words are written once it can`() {
        // A full disk, as the write sees one: the .tmp it writes is a folder
        // that cannot be removed. Changed in place, the note kept the new
        // words in memory, so the retry found nothing to write and said Ok.
        val load = scratch("""[{"id": 1, "title": "Shopping", "body": "milk"}]""")
        val blocker = File(load.file!!.parentFile, "notes_data.json.tmp")
        File(blocker, "hold").apply { parentFile!!.mkdirs(); writeText("x") }
        assertTrue(Notebook.updateNote(load, 1, "Shopping", "milk, eggs") is SaveResult.Failed)
        assertEquals("milk", load.raw!!.getJSONObject(0).getString("body"))
        assertEquals("milk", onDisk(load).getJSONObject(0).getString("body"))
        File(blocker, "hold").delete()
        blocker.delete()
        assertTrue(Notebook.updateNote(load, 1, "Shopping", "milk, eggs") is SaveResult.Ok)
        assertEquals("milk, eggs", onDisk(load).getJSONObject(0).getString("body"))
    }

    @Test
    fun `a font size the desktop set is read, and written back as the desktop's number`() {
        // The desktop stores a size as a number; read as text alone it came
        // in empty, and the next save on the phone wrote it back with no
        // value at all -- which the desktop's normalize_formatting drops.
        val load = scratch("""
            [{"id": 1, "body": "big text here",
              "formatting": [{"start": 0, "end": 3, "style": "font_size", "value": 20},
                             {"start": 4, "end": 8, "style": "text_color", "value": "#ff0000"}]}]
        """.trimIndent())
        assertEquals(FormatSpan(0, 3, "font_size", "20"), load.notes[0].formatting[0])
        Notebook.updateNote(load, 1, "", "big text here!")
        val written = onDisk(load).getJSONObject(0).getJSONArray("formatting")
        assertEquals("a number, as the desktop keeps it", 20, written.getJSONObject(0).get("value"))
        assertEquals("#ff0000", written.getJSONObject(1).get("value"))
        // The editor's own spans travel the same road.
        Notebook.updateNote(load, 1, "", "big text here!!", formatting = Notebook.refreshed(load).notes[0].formatting)
        assertEquals(20, onDisk(load).getJSONObject(0).getJSONArray("formatting").getJSONObject(0).get("value"))
    }

    /**
     * The class the font size belonged to: a value the phone reads or
     * writes in a shape the desktop's gatekeepers refuse. Every note in the
     * export fixture -- styles valid and not, sizes as numbers, text and
     * true/false -- goes through a save on the phone, and what the desktop
     * would keep of its styles and its paragraph lines must come back
     * exactly. (Spans past the end of the text are left out: they style
     * nothing, and an edit at the end rightly moves them.)
     */
    @Test
    fun `whatever styling the desktop would keep survives a save on the phone`() {
        val stream = javaClass.classLoader?.getResourceAsStream("export_cases.json")
        val cases = JSONObject(stream!!.readBytes().toString(Charsets.UTF_8)).getJSONArray("cases")
        var compared = 0
        for (i in 0 until cases.length()) {
            val note = JSONObject(cases.getJSONObject(i).getJSONObject("note").toString()).put("id", 1)
            val body = note.getString("body")
            val length = body.codePointCount(0, body.length)
            fun kept(n: JSONObject) = Exporting.normalizeFormatting(n.opt("formatting")).filter { it.end <= length }
            fun lines(n: JSONObject, text: String) = Exporting.normalizeParagraphs(n.opt("paragraphs"), text.split("\n").size)
            val load = scratch(JSONArray().put(note).toString())
            Notebook.updateNote(load, 1, note.optString("title"), "$body!")
            val written = onDisk(load).getJSONObject(0)
            assertEquals("note #$i: styles", kept(note), kept(written))
            assertEquals("note #$i: paragraph lines", lines(note, body), lines(written, "$body!"))
            compared++
        }
        assertTrue(compared > 300)
    }

    @Test
    fun `a size and a face chosen on the phone are written as the desktop keeps them`() {
        val load = scratch("[]")
        val spans = setStyle(setStyle(emptyList(), 0, 3, "font_size", "20"), 4, 8, "font_family", "Georgia")
        Notebook.createNote(load, "", "big text here", formatting = spans)
        val written = onDisk(load).getJSONObject(0).getJSONArray("formatting")
        val bySpan = (0 until written.length()).map { written.getJSONObject(it) }.associateBy { it.getString("style") }
        assertEquals(20, bySpan.getValue("font_size").get("value"))
        assertEquals("Georgia", bySpan.getValue("font_family").get("value"))
        assertEquals("every choice the picker offers is one the desktop keeps",
            Fonts.SIZES.map { FormatSpan(0, 1, "font_size", it.toString()) } +
                Fonts.FAMILIES.map { FormatSpan(0, 1, "font_family", it) },
            Exporting.normalizeFormatting(JSONArray(
                Fonts.SIZES.map { JSONObject().put("start", 0).put("end", 1).put("style", "font_size").put("value", it) } +
                    Fonts.FAMILIES.map { JSONObject().put("start", 0).put("end", 1).put("style", "font_family").put("value", it) })))
        assertEquals("serif", Fonts.kindOf("Georgia"))
        assertEquals("monospace", Fonts.kindOf("Courier New"))
        assertEquals("sans", Fonts.kindOf("Calibri"))
        assertEquals(null, Fonts.kindOf("Comic Sans"))
    }

    @Test
    fun `a captured note lands with the whole default shape`() {
        val load = scratch("[]")
        val result = Notebook.createNote(load, "Title", "Body")
        assertTrue("the save must succeed", result is SaveResult.Ok)

        val saved = onDisk(load).getJSONObject(0)
        assertEquals(1, saved.getInt("id"))
        assertEquals("Title", saved.getString("title"))
        assertEquals("Body", saved.getString("body"))
        // Every field of the contract, so nothing downstream meets a gap.
        for (field in listOf("formatting", "comments", "category", "date",
                             "color", "attachments", "updated", "created",
                             "recurrence", "exceptions", "overrides",
                             "pinned", "checklist", "paragraphs")) {
            assertTrue("missing field: $field", saved.has(field))
        }
        assertEquals("General", saved.getString("category"))
        assertEquals("", saved.getJSONObject("recurrence").getString("freq"))
        // "YYYY-MM-DD HH:MM:SS" -- the desktop's shape, seconds included.
        assertEquals(19, saved.getString("created").length)
        assertEquals(saved.getString("created"), saved.getString("updated"))
    }

    @Test
    fun `saving keeps fields this app does not even read`() {
        // THE test for this feature. The Kotlin model drops attachment
        // lists and per-day overrides; rebuilding the file from it would
        // delete them from every other note, invisibly and permanently.
        val existing = """
            [{"id": 4, "title": "old",
              "attachments": [{"filename": "photo.png", "stored_as": "a1.png"}],
              "overrides": {"2026-01-01": {"title": "only on that day"}},
              "a_future_field": "that this app does not know"}]
        """.trimIndent()
        val load = scratch(existing)
        assertTrue(Notebook.createNote(load, "new", "") is SaveResult.Ok)

        val old = onDisk(load).getJSONObject(0)
        assertEquals("photo.png",
            old.getJSONArray("attachments").getJSONObject(0).getString("filename"))
        assertEquals("only on that day",
            old.getJSONObject("overrides").getJSONObject("2026-01-01").getString("title"))
        assertEquals("that this app does not know",
            old.getString("a_future_field"))
    }

    @Test
    fun `a new id never collides, even with a note the model skipped`() {
        // 5 is not an object, so parseNotes drops it -- but the note with
        // id 9 is real and the next id must clear it.
        val load = scratch("""[{"id": 9}, 5, {"id": 2}]""")
        val result = Notebook.createNote(load, "x", "") as SaveResult.Ok
        assertEquals(10, result.id)
    }

    @Test
    fun `a notebook that cannot be read is kept aside before anything is written`() {
        // The desktop's answer, ported: the unreadable bytes are copied
        // beside the original, the app starts with an empty list, and it
        // says where the copy is. Writing over the file is then safe --
        // and refusing every write forever would make a phone that cannot
        // take a single note.
        val load = scratch("{ not a notes file")
        val kept = load.file!!.parentFile!!.listFiles()!!
            .single { it.name.startsWith("notes_data_corrupt_") }
        assertEquals("{ not a notes file", kept.readText())
        assertTrue("the problem names the copy", load.problem!!.contains(kept.name))
        assertEquals(0, load.notes.size)
        assertTrue(Notebook.createNote(load, "x", "") is SaveResult.Ok)
        assertEquals("{ not a notes file", kept.readText())
    }

    @Test
    fun `a notebook that can neither be read nor copied aside is never written over`() {
        val load = scratch("[]")
        val stuck = Notebook.Load(
            notes = emptyList(), categories = load.categories, source = "x",
            file = load.file, raw = null,
        )
        load.file!!.writeText("{ unreadable")
        assertTrue(Notebook.createNote(stuck, "x", "") is SaveResult.Failed)
        assertEquals("{ unreadable", load.file!!.readText())
    }

    @Test
    fun `a write that fails says why and leaves the list unchanged`() {
        val load = scratch("[]")
        // A path whose parent directory does not exist: the stream throws.
        val nowhere = Notebook.Load(
            notes = load.notes, categories = load.categories, source = "x",
            file = File(load.file!!.parentFile, "no/such/notes_data.json"),
            raw = load.raw,
        )
        val before = nowhere.raw!!.length()
        val result = Notebook.createNote(nowhere, "x", "")
        assertTrue(result is SaveResult.Failed)
        assertTrue("the reason must not be empty",
            (result as SaveResult.Failed).reason.isNotEmpty())
        assertEquals("the added note must be taken back out", before, nowhere.raw!!.length())
    }

    @Test
    fun `a first note creates the file on a phone that has none`() {
        val load = scratch(null)
        assertTrue(Notebook.createNote(load, "first", "body") is SaveResult.Ok)
        assertEquals(1, onDisk(load).length())
    }

    @Test
    fun `an unpaired surrogate never reaches the file`() {
        val load = scratch("[]")
        // A high surrogate on its own -- not a character, and a JSON writer
        // either refuses it or emits something nothing reads back.
        Notebook.createNote(load, "a\uD83D b", "\uDC63")
        val saved = onDisk(load).getJSONObject(0)
        assertEquals("a b", saved.getString("title"))
        assertEquals("", saved.getString("body"))
    }

    @Test
    fun `a whole emoji is kept, only broken halves are dropped`() {
        val load = scratch("[]")
        Notebook.createNote(load, "👣 steps", "")
        assertEquals("👣 steps", onDisk(load).getJSONObject(0).getString("title"))
    }

    @Test
    fun `what was written reads back as the same note`() {
        val load = scratch("[]")
        Notebook.createNote(load, "Gone shopping", "milk\nbread")
        // Not the object we built -- the bytes on disk, read by the same
        // reader the app uses at startup.
        val reread = Notebook.parseNotes(load.file!!.readText()).single()
        assertEquals("Gone shopping", reread.title)
        assertEquals("milk\nbread", reread.body)
        assertTrue(reread.matches("bread"))
        assertFalse(reread.isRecurring)
    }

    // ---- editing: formatting must follow the text ----

    private fun spans(vararg triples: Triple<Int, Int, String>) =
        triples.map { FormatSpan(it.first, it.second, it.third) }

    @Test
    fun `text typed after a span leaves it exactly where it was`() {
        val moved = remapSpans("abcdef", "abcdefGHI", spans(Triple(0, 3, "bold")))
        assertEquals(0, moved.lost)
        assertEquals(0, moved.spans.single().start)
        assertEquals(3, moved.spans.single().end)
    }

    @Test
    fun `text inserted before a span carries it along`() {
        // "abcdef" -> "XXabcdef": bold on "def" was 3..6, now 5..8.
        val moved = remapSpans("abcdef", "XXabcdef", spans(Triple(3, 6, "bold")))
        assertEquals(5, moved.spans.single().start)
        assertEquals(8, moved.spans.single().end)
        assertEquals("def", "XXabcdef".substring(5, 8))
    }

    @Test
    fun `deleting before a span pulls it back onto the same words`() {
        val old = "My weekly list of groceries"
        val new = "My list of groceries"
        // bold on "groceries"
        val at = old.indexOf("groceries")
        val moved = remapSpans(old, new, spans(Triple(at, at + "groceries".length, "bold")))
        val span = moved.spans.single()
        assertEquals("groceries", new.substring(span.start, span.end))
    }

    @Test
    fun `a span whose text was deleted is dropped and counted, not left adrift`() {
        val old = "start SECRET end"
        val new = "start  end"
        val at = old.indexOf("SECRET")
        val moved = remapSpans(old, new, spans(Triple(at, at + 6, "highlight")))
        assertEquals(0, moved.spans.size)
        assertEquals(1, moved.lost)
    }

    @Test
    fun `replacing exactly the styled word does not style the replacement`() {
        // Bold covered "bb" and only "bb". The user replaces it. The new
        // words are the user's, not the deleted word's, so they get no
        // styling -- and nothing may end up covering them.
        val moved = remapSpans("aa bb cc", "aa NEW NEW cc", spans(Triple(3, 5, "bold")))
        assertEquals(1, moved.lost)
        for (span in moved.spans) {
            val covered = "aa NEW NEW cc".substring(span.start, span.end)
            assertFalse("the styling reached the new text: $covered",
                covered.contains("NEW"))
        }
    }

    @Test
    fun `editing one word inside a bold sentence keeps the sentence bold`() {
        // The other side of the same rule: a span that reaches past the
        // edit is stretched, or fixing a typo would strip the styling off
        // everything around it.
        val old = "aa bb cc"
        val new = "aa NEW cc"
        val moved = remapSpans(old, new, spans(Triple(0, 8, "bold")))
        assertEquals(0, moved.lost)
        val span = moved.spans.single()
        assertEquals(new, new.substring(span.start, span.end))
    }

    @Test
    fun `an emoji in the edited text keeps the offsets in code points`() {
        // Python counts the emoji as ONE. The prefix diff must too, or the
        // span lands a position off for every emoji before it.
        val old = "👣 abc"
        val new = "👣 abcdef"
        val moved = remapSpans(old, new, spans(Triple(2, 5, "bold")))
        val span = moved.spans.single()
        assertEquals(2, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun `an unchanged body leaves every span untouched`() {
        val original = spans(Triple(0, 3, "bold"), Triple(4, 8, "italic"))
        val moved = remapSpans("abcdefgh", "abcdefgh", original)
        assertEquals(original, moved.spans)
        assertEquals(0, moved.lost)
    }

    // ---- editing: bullet markers are line numbers ----

    @Test
    fun `a line inserted above a bullet renumbers it`() {
        val moved = remapParagraphLines("a\nb\nc", "NEW\na\nb\nc", listOf(1, 2))
        assertEquals(mapOf(1 to 2, 2 to 3), moved)
    }

    @Test
    fun `a marker on a line that was edited away is dropped`() {
        val moved = remapParagraphLines("a\nb\nc", "a\nc", listOf(1))
        assertEquals(emptyMap<Int, Int>(), moved)
    }

    @Test
    fun `typing into a list item keeps its bullet`() {
        // The edit was inside the line, so the line is still the bullet's:
        // this once dropped it, on every letter typed into a list item.
        assertEquals(mapOf(0 to 0, 1 to 1), remapParagraphLines("• milk\n• bread", "• milk!\n• bread", listOf(0, 1)))
        assertEquals("two lines rewritten in one edit stay two lines",
            mapOf(1 to 1, 2 to 2), remapParagraphLines("a\nb\nc\nd", "a\nB\nC\nd", listOf(1, 2)))
        val load = scratch("""
            [{"id": 1, "body": "• milk\nplain",
              "paragraphs": [{"line": 0, "kind": "bullet", "indent": 1}]}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "", "• milk and eggs\nplain")
        val kept = onDisk(load).getJSONObject(0).getJSONArray("paragraphs")
        assertEquals(1, kept.length())
        assertEquals("bullet", kept.getJSONObject(0).getString("kind"))
        assertEquals(1, kept.getJSONObject(0).getInt("indent"))
    }

    @Test
    fun `each marker keeps its OWN kind when an earlier one is dropped`() {
        // The bug this test exists for: returning a bare list of surviving
        // lines makes the caller pair kinds positionally, so dropping the
        // first marker gives the second marker the first one's kind.
        val load = scratch("""
            [{"id": 1, "body": "a\nGONE\nc",
              "paragraphs": [{"line": 1, "kind": "bullet"},
                             {"line": 2, "kind": "number"}]}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "", "a\nc")
        val kept = onDisk(load).getJSONObject(0).getJSONArray("paragraphs")
        assertEquals(1, kept.length())
        assertEquals("number", kept.getJSONObject(0).getString("kind"))
        assertEquals(1, kept.getJSONObject(0).getInt("line"))
    }

    @Test
    fun `a centred or indented line keeps what it is, and does not become a list`() {
        // A paragraph entry is not only a list marker: the desktop stores
        // a line's alignment and indent in it too, with no kind at all.
        val load = scratch("""
            [{"id": 1, "body": "Title line\nbody",
              "paragraphs": [{"line": 0, "align": "center"},
                             {"line": 1, "kind": "number", "indent": 2, "align": "right"}]}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "", "new first line\nTitle line\nbody")
        val kept = onDisk(load).getJSONObject(0).getJSONArray("paragraphs")
        val centred = kept.getJSONObject(0)
        assertEquals(1, centred.getInt("line"))
        assertEquals("center", centred.getString("align"))
        assertFalse("a centred line is not a bullet", centred.has("kind"))
        val numbered = kept.getJSONObject(1)
        assertEquals(2, numbered.getInt("line"))
        assertEquals(2, numbered.getInt("indent"))
        assertEquals("right", numbered.getString("align"))
    }

    // ---- editing: the file ----

    @Test
    fun `an edit rewrites only that note and only its own fields`() {
        val load = scratch("""
            [{"id": 1, "title": "old", "body": "old body", "category": "Work",
              "created": "2020-01-01 00:00:00", "color": "#ff0000",
              "attachments": [{"filename": "x.png"}]},
             {"id": 2, "title": "the neighbour"}]
        """.trimIndent())
        val result = Notebook.updateNote(load, 1, "new", "new body")
        assertTrue(result is SaveResult.Ok)

        val edited = onDisk(load).getJSONObject(0)
        assertEquals("new", edited.getString("title"))
        assertEquals("new body", edited.getString("body"))
        // Everything the edit did not touch is still there.
        assertEquals("Work", edited.getString("category"))
        assertEquals("#ff0000", edited.getString("color"))
        assertEquals("x.png",
            edited.getJSONArray("attachments").getJSONObject(0).getString("filename"))
        // A save is not a birth: `created` never moves.
        assertEquals("2020-01-01 00:00:00", edited.getString("created"))
        assertEquals(19, edited.getString("updated").length)
        // And the neighbour is untouched.
        assertEquals("the neighbour", onDisk(load).getJSONObject(1).getString("title"))
    }

    @Test
    fun `the note is found by id, not by where it sits in the list`() {
        val load = scratch("""[{"id": 7, "title": "a"}, {"id": 3, "title": "b"}]""")
        Notebook.updateNote(load, 3, "changed", "")
        assertEquals("a", onDisk(load).getJSONObject(0).getString("title"))
        assertEquals("changed", onDisk(load).getJSONObject(1).getString("title"))
    }

    @Test
    fun `emptying a note keeps the old version inside it`() {
        val load = scratch("""
            [{"id": 1, "title": "List", "body": "milk, bread",
              "formatting": [{"start": 0, "end": 4, "style": "bold"}]}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "", "")
        val note = onDisk(load).getJSONObject(0)
        val previous = note.getJSONObject("_prev")
        assertEquals("List", previous.getString("title"))
        assertEquals("milk, bread", previous.getString("body"))
        // The styling comes back with the text, not stripped off it.
        assertEquals(1, previous.getJSONArray("formatting").length())
    }

    @Test
    fun `a small tidy-up does not litter the note with rescue copies`() {
        val load = scratch("""[{"id": 1, "title": "List", "body": "milk and bread"}]""")
        Notebook.updateNote(load, 1, "List", "milk and bread.")
        assertFalse(onDisk(load).getJSONObject(0).has("_prev"))
    }

    @Test
    fun `the desktop's shrink thresholds, case by case`() {
        val long = "x".repeat(500)
        // Emptied completely, however short it was.
        assertTrue(Notebook.shouldKeepPrevious("", "short", "", ""))
        // 41 characters gone out of 50 is both a big fraction and over 40.
        assertTrue(Notebook.shouldKeepPrevious("", "y".repeat(50), "", "y".repeat(9)))
        // 200+ characters gone, even though 500 -> 290 keeps 58%.
        assertTrue(Notebook.shouldKeepPrevious("", long, "", "x".repeat(290)))
        // A typo fix is not a disaster.
        assertFalse(Notebook.shouldKeepPrevious("", "milk", "", "milk."))
        // Exactly 40 removed is not "more than 40".
        assertFalse(Notebook.shouldKeepPrevious("", "z".repeat(100), "", "z".repeat(60)))
    }

    @Test
    fun `a copy is kept by what is gone, not by how much shorter the text got`() {
        // The desktop's test case 692: a short note replaced by a new line
        // before the autosave shrank by 20, and no copy was kept.
        val short = "Buy milk\nCall mom\nPay the rent"
        assertTrue(Notebook.shouldKeepPrevious("", short, "", "A new line"))
        // A paragraph replaced by one as long did not shrink at all.
        val para = "x".repeat(200) + "\n" + "an old paragraph of three hundred characters ".repeat(6) + "\n" + "y".repeat(100)
        assertTrue(Notebook.shouldKeepPrevious("t", para, "t",
            para.replace("an old paragraph", "a new paragraph!").replace("of three hundred characters", "freshly typed in place of it.")))
        // A word changed, words added, a tiny note retyped, a title renamed: none.
        assertFalse(Notebook.shouldKeepPrevious("", short, "", short.replace("mom", "dad")))
        assertFalse(Notebook.shouldKeepPrevious("", short, "", short.replace("Call", "Please call")))
        assertFalse(Notebook.shouldKeepPrevious("", "Milk", "", "Eggs"))
        assertFalse(Notebook.shouldKeepPrevious("Shopping", short, "Groceries", short))
        assertFalse(Notebook.shouldKeepPrevious("", "", "Title", "text"))
        // What neither end keeps.
        assertEquals(2, Notebook.textLost("abcdef", "abXYef"))
        assertEquals(0, Notebook.textLost("", "new"))
        assertEquals(3, Notebook.textLost("abc", ""))
    }

    @Test
    fun `what an edit takes away is counted in letters, an emoji one, as the desktop counts`() {
        // In UTF-16 units twelve emoji deleted were twenty-four characters:
        // the phone's copy then replaced one the desktop keeps, its deleted
        // paragraph in no copy. The desktop's answers, from its own core
        // (scratch probe_rescue_units.py, at a4bbf05).
        val party = "🎉"
        val smile = "😀"
        assertEquals(6, Notebook.textLost(smile.repeat(10), smile.repeat(4)))
        assertEquals("two emoji that share no half", 1, Notebook.textLost("a${smile}b", "a${party}b"))
        // 😀 and 🙂 share their first half, D83D: the start steps back to the
        // emoji's edge (the desktop's answer at 947e9d3, probe_parity.py).
        assertEquals("an emoji the two share half of differs whole", 1, Notebook.textLost("a${smile}b", "a🙂b"))
        assertEquals(1, Notebook.textLost("a${smile}b", "ab"))
        assertFalse(Notebook.shouldKeepPrevious("Party", "", "Party", "", party.repeat(20), party.repeat(12)))
        assertTrue("emptied", Notebook.shouldKeepPrevious("Party", "", "Party", "", party.repeat(20), ""))
        assertFalse(Notebook.shouldKeepPrevious("t", smile.repeat(100), "t", smile.repeat(40) + party.repeat(21) + smile.repeat(39)))
        assertFalse(Notebook.shouldKeepPrevious("t", "𠀀".repeat(120), "t", "𠀀".repeat(90)))
        // Lone halves, as a file can hold them, are a letter each, as Python reads them.
        assertEquals(1, Notebook.textLost("x\uDE00", "y\uDE00"))
        assertEquals("a lone half kept is no emoji kept", 2, Notebook.textLost("\uD83Dx", "😀"))
        // ...and at the end, its mirror: a lone second half the new text pairs.
        // Only this shape engages the end's step, which no case reached (the
        // final audit's eighth round); Python counts 2.
        assertEquals("a lone half kept at the end is no emoji kept", 2, Notebook.textLost("x\uDE00", "😀"))
    }

    @Test
    fun `a column added to a table loses no words, and leaves the copy of a deleted paragraph in place`() {
        // A column added pads every row again: measured as the span between
        // the first change and the last, it read as the whole table lost,
        // and the autosave's copy replaced the deleted paragraph's -- the
        // desktop's `_lost`, the smaller of that span and the words gone.
        // Padded as the table rules pad it: a column in and out again.
        val table = TableEdit(listOf("| Name | Qty | Price |", "| --- | --- | --- |") +
            (1..8).map { "| item $it | $it | ${it * 3}.50 |" }).insertColumnAfter(0).removeColumn(1).lines()
        val paragraph = "A paragraph of three hundred letters, written once and deleted. ".repeat(5)
        val load = scratch("""[{"id": 1, "title": "Shop", "body": "Intro\n$paragraph\n${table.joinToString("\\n")}"}]""")
        assertTrue(Notebook.updateNote(load, 1, "Shop", "Intro\n${table.joinToString("\n")}") is SaveResult.Ok)
        assertTrue(onDisk(load).getJSONObject(0).getJSONObject("_prev").getString("body").contains(paragraph))
        val wider = TableEdit(table).insertColumnAfter(0).lines()
        assertTrue("the rows were padded again", Notebook.textLost(table.joinToString("\n"), wider.joinToString("\n")) > 200)
        assertEquals(0, Notebook.wordsLost(table.joinToString("\n"), wider.joinToString("\n")))
        assertTrue(Notebook.updateNote(load, 1, "Shop", "Intro\n${wider.joinToString("\n")}") is SaveResult.Ok)
        assertTrue("the paragraph's copy stays", onDisk(load).getJSONObject(0).getJSONObject("_prev").getString("body").contains(paragraph))
        // A word changed is lost whole; one letter in a paragraph with no
        // spaces, by the other count, is one letter.
        assertEquals("three letters and a space", 4, Notebook.wordsLost("one two three", "one too three"))
        assertEquals(1, Notebook.lost("中文字".repeat(100), "中文字".repeat(50) + "中文子" + "中文字".repeat(49)))
    }

    @Test
    fun `five rows of a table's eight deleted at once keep a copy, its share measured as the loss is`() {
        // The words gone were set against a length that counts the padding:
        // in a table of short cells it is most of each row, and half a table
        // read as a quarter -- no copy (the desktop's fifth round, its own
        // case: a shopping list's five rows of eight).
        val rows = listOf(listOf("Item", "Qty", "Ok")) + listOf("tea 1 y", "jam 2 n", "oat 1 y", "egg 6 y",
            "fig 3 n", "rye 1 y", "soy 2 n", "nut 4 y").map { it.split(" ") }
        val lines = listOf("Groceries") + formatTable(rows, List(rows.size) { List(3) { CellAlign.LEFT } })
        val old = lines.joinToString("\n")
        val cut = (lines.take(2) + lines.drop(7)).joinToString("\n")
        val lost = Notebook.lost(old, cut)
        assertTrue("$lost of ${old.length}: the old measure would keep none", lost > 40 && lost < 0.3 * old.length)
        assertTrue(Notebook.shouldKeepPrevious("t", old, "t", cut))
        assertEquals(16, Notebook.measured("| tea | 1 | y |"))
    }

    @Test
    fun `a list's markers changed lose no words, and leave the copy of a deleted paragraph in place`() {
        // Each "1." counted as a word: numbers turned to bullets, or bullets
        // one level in, read as the list lost, and the autosave's copy
        // replaced the one holding a paragraph deleted a moment before. The
        // desktop's case, its _words.
        val items = listOf("milk", "eggs", "bread", "butter", "flour", "sugar", "salt",
                           "apples", "pears", "rice", "beans", "tea", "coffee", "onion")
        val numbered = items.mapIndexed { i, w -> "${i + 1}. $w" }.joinToString("\n")
        val bullets = items.joinToString("\n") { "• $it" }
        val inner = items.joinToString("\n") { "◦ $it" }
        assertTrue("the markers as words would pass the bars", Notebook.textLost(numbered, bullets) > 0.3 * numbered.length)
        assertEquals(0, Notebook.wordsLost(numbered, bullets))
        assertFalse(Notebook.shouldKeepPrevious("t", numbered, "t", bullets))
        assertFalse(Notebook.shouldKeepPrevious("t", bullets, "t", inner))
        assertTrue("a real loss still keeps one", Notebook.shouldKeepPrevious("t", numbered, "t", numbered.lines().take(3).joinToString("\n")))
        // Through the save: the paragraph's copy stays when the list changes after.
        val paragraph = "Remember to call the plumber about the leak under the sink before Friday, and ask him about the old pipe."
        val load = scratch("""[{"id": 1, "title": "Shopping", "body": "$paragraph\n${numbered.replace("\n", "\\n")}"}]""")
        assertTrue(Notebook.updateNote(load, 1, "Shopping", numbered) is SaveResult.Ok)
        assertTrue(Notebook.updateNote(load, 1, "Shopping", bullets) is SaveResult.Ok)
        assertTrue("the paragraph's copy stays", onDisk(load).getJSONObject(0).getJSONObject("_prev").getString("body").startsWith(paragraph))
    }

    @Test
    fun `a letter typed in a long note is weighed by the span alone, the words never split`() {
        // Every autosave asked the words as well: the whole old and new text
        // split into words three times for one letter typed. Timed against
        // the span's own measure on the same text, so no machine's speed
        // moves the margin: the words cost some fifty times the span.
        val old = "a word or two in a long journal ".repeat(150_000)
        val typed = old.substring(0, 2_000_000) + "x" + old.substring(2_000_000)
        fun ms(work: () -> Unit): Long {
            val started = System.nanoTime()
            work()
            return (System.nanoTime() - started) / 1_000_000
        }
        repeat(3) { Notebook.textLost(old, typed); Notebook.shouldKeepPrevious("t", old, "t", typed) }
        val span = (1..3).minOf { ms { Notebook.textLost(old, typed) } }
        val save = (1..3).minOf { ms { assertFalse(Notebook.shouldKeepPrevious("t", old, "t", typed)) } }
        assertTrue("save $save ms against the span's $span ms", save <= 5 * span + 30)
    }

    @Test
    fun `a short note replaced by a new line keeps its words, through updateNote`() {
        val load = scratch("""[{"id": 1, "title": "", "body": "Buy milk\nCall mom\nPay the rent"}]""")
        Notebook.updateNote(load, 1, "", "A new line")
        val prev = onDisk(load).getJSONObject(0).optJSONObject("_prev")
        assertEquals("Buy milk\nCall mom\nPay the rent", prev?.optString("body"))
    }

    @Test
    fun `saving a note that did not change writes nothing`() {
        val load = scratch("""[{"id": 1, "title": "a", "body": "b"}]""")
        val before = load.file!!.readText()
        val result = Notebook.updateNote(load, 1, "a", "b")
        assertTrue(result is SaveResult.Ok)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `editing a note that is gone from the file says so`() {
        val load = scratch("""[{"id": 1}]""")
        assertTrue(Notebook.updateNote(load, 99, "x", "y") is SaveResult.Failed)
    }

    @Test
    fun `an edit reports how many styles it could not carry`() {
        val load = scratch("""
            [{"id": 1, "body": "start SECRET end", "formatting":
              [{"start": 6, "end": 12, "style": "highlight"},
               {"start": 0, "end": 5, "style": "bold"}]}]
        """.trimIndent())
        val result = Notebook.updateNote(load, 1, "", "start  end") as SaveResult.Ok
        assertEquals(1, result.lostStyles)
        assertEquals(1, onDisk(load).getJSONObject(0).getJSONArray("formatting").length())
    }

    @Test
    fun `an edited note reads back with its formatting on the right words`() {
        val load = scratch("""
            [{"id": 1, "body": "aa bb cc", "formatting":
              [{"start": 6, "end": 8, "style": "bold"}]}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "", "PREFIX aa bb cc")
        val reread = Notebook.parseNotes(load.file!!.readText()).single()
        val span = reread.formatting.single()
        assertEquals("cc", reread.body.substring(span.start, span.end))
    }

    // ---- Romanian diacritics ----

    @Test
    fun `a search without diacritics finds the words that have them`() {
        val json = """[{"id": 1, "title": "Mașină", "body": "Ștefan și Țara"}]"""
        val note = Notebook.parseNotes(json).single()
        assertTrue(note.matches("masina"))
        assertTrue(note.matches("stefan"))
        assertTrue(note.matches("si"))
        assertTrue(note.matches("tara"))
    }

    @Test
    fun `and a search WITH diacritics still finds text written without them`() {
        val note = Notebook.parseNotes("""[{"id": 1, "title": "masina"}]""").single()
        assertTrue(note.matches("mașină"))
    }

    @Test
    fun `the cedilla spelling and the comma-below spelling match each other`() {
        // Older Windows keyboards produce ş (cedilla, U+015F); the correct
        // Romanian letter is ș (comma below, U+0219). A notebook typed on
        // two machines holds both, and they are different characters.
        assertEquals(fold("ş"), fold("ș"))
        assertEquals(fold("ţ"), fold("ț"))
    }

    @Test
    fun `folding does not merge letters that are genuinely different`() {
        val note = Notebook.parseNotes("""[{"id": 1, "title": "casa"}]""").single()
        assertFalse(note.matches("cosa"))
        assertFalse(note.matches("caza"))
    }

    // ---- sorting ----

    private fun listOfNotes(vararg json: String): List<Note> =
        Notebook.parseNotes("[" + json.joinToString(",") + "]")

    @Test
    fun `recently updated comes first by default`() {
        val notes = listOfNotes(
            """{"id": 1, "updated": "2026-01-01 00:00:00"}""",
            """{"id": 2, "updated": "2026-03-01 00:00:00"}""",
            """{"id": 3, "updated": "2026-02-01 00:00:00"}""",
        )
        assertEquals(listOf(2, 3, 1),
            sortNotes(notes, SortOrder.UPDATED_NEW).map { it.id })
        assertEquals(listOf(1, 3, 2),
            sortNotes(notes, SortOrder.UPDATED_OLD).map { it.id })
    }

    @Test
    fun `untitled notes sink to the bottom of an alphabetical sort`() {
        // Empty strings sort first naturally, which is never what anyone
        // means by "A to Z".
        val notes = listOfNotes(
            """{"id": 1, "title": ""}""",
            """{"id": 2, "title": "bananas"}""",
            """{"id": 3, "title": "Almonds"}""",
        )
        assertEquals(listOf(3, 2, 1), sortNotes(notes, SortOrder.TITLE_AZ).map { it.id })
        // And they stay at the bottom when the order is reversed -- the
        // untitled ones are not "last alphabetically", they are apart.
        assertEquals(listOf(2, 3, 1), sortNotes(notes, SortOrder.TITLE_ZA).map { it.id })
    }

    @Test
    fun `pinned notes float to the top but keep the chosen order among themselves`() {
        val notes = listOfNotes(
            """{"id": 1, "updated": "2026-01-01 00:00:00"}""",
            """{"id": 2, "updated": "2026-02-01 00:00:00", "pinned": true}""",
            """{"id": 3, "updated": "2026-03-01 00:00:00"}""",
            """{"id": 4, "updated": "2026-04-01 00:00:00", "pinned": true}""",
        )
        // 4 and 2 rise; between them the newest-first order still holds.
        assertEquals(listOf(4, 2, 3, 1),
            sortNotes(notes, SortOrder.UPDATED_NEW).map { it.id })
    }

    @Test
    fun `sorting by category breaks ties with the update time`() {
        val notes = listOfNotes(
            """{"id": 1, "category": "Work", "updated": "2026-01-01 00:00:00"}""",
            """{"id": 2, "category": "General", "updated": "2026-02-01 00:00:00"}""",
            """{"id": 3, "category": "General", "updated": "2026-01-01 00:00:00"}""",
        )
        assertEquals(listOf(3, 2, 1), sortNotes(notes, SortOrder.CATEGORY).map { it.id })
    }

    // ---- ticking a checklist ----

    @Test
    fun `ticking a row changes only that row`() {
        val load = scratch("""
            [{"id": 1, "updated": "2020-01-01 00:00:00", "checklist": [
               {"text": "one", "done": false},
               {"text": "two", "done": false, "a_future_field": "keep-me"}]}]
        """.trimIndent())
        assertTrue(Notebook.setChecklistDone(load, 1, 1, true) is SaveResult.Ok)

        val rows = onDisk(load).getJSONObject(0).getJSONArray("checklist")
        assertFalse(rows.getJSONObject(0).getBoolean("done"))
        assertTrue(rows.getJSONObject(1).getBoolean("done"))
        assertEquals("two", rows.getJSONObject(1).getString("text"))
        // A field this app never reads must survive being ticked past.
        assertEquals("keep-me", rows.getJSONObject(1).getString("a_future_field"))
        // Ticking IS a save.
        assertFalse("2020-01-01 00:00:00" ==
            onDisk(load).getJSONObject(0).getString("updated"))
    }

    @Test
    fun `unticking works the same way round`() {
        val load = scratch("""[{"id": 1, "checklist": [{"text": "x", "done": true}]}]""")
        Notebook.setChecklistDone(load, 1, 0, false)
        assertFalse(onDisk(load).getJSONObject(0)
            .getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
    }

    @Test
    fun `a tick is never a deletion, so it leaves no rescue copy`() {
        val load = scratch("""[{"id": 1, "checklist": [{"text": "x", "done": false}]}]""")
        Notebook.setChecklistDone(load, 1, 0, true)
        assertFalse(onDisk(load).getJSONObject(0).has("_prev"))
    }

    @Test
    fun `ticking a row that is gone says so instead of guessing`() {
        val load = scratch("""[{"id": 1, "checklist": [{"text": "x"}]}]""")
        assertTrue(Notebook.setChecklistDone(load, 1, 9, true) is SaveResult.Failed)
        assertTrue(Notebook.setChecklistDone(load, 99, 0, true) is SaveResult.Failed)
    }

    // ---- the Trash ----

    private fun scratchWithTrash(notes: String, trash: String?): Notebook.Load {
        val dir = Files.createTempDirectory("notebook").toFile()
        dir.deleteOnExit()
        File(dir, "notes_data.json").writeText(notes)
        if (trash != null) File(dir, "trash.json").writeText(trash)
        return Notebook.load(listOf(dir))
    }

    private fun trashOnDisk(load: Notebook.Load): JSONArray =
        JSONArray(load.trashFile!!.readText())

    @Test
    fun `deleting moves the note to the trash instead of destroying it`() {
        val load = scratchWithTrash(
            """[{"id": 1, "title": "a"}, {"id": 2, "title": "to delete"}]""", "[]")
        assertTrue(Notebook.deleteNote(load, 2) is SaveResult.Ok)

        // Gone from the notebook...
        assertEquals(listOf(1), (0 until onDisk(load).length())
            .map { onDisk(load).getJSONObject(it).getInt("id") })
        // ...and whole in the trash, with the day it went there.
        val trashed = trashOnDisk(load).getJSONObject(0)
        assertEquals("to delete", trashed.getString("title"))
        assertEquals(19, trashed.getString("deleted_at").length)
    }

    @Test
    fun `a deleted note keeps everything it had`() {
        val load = scratchWithTrash("""
            [{"id": 1, "title": "x", "attachments": [{"filename": "p.png"}],
              "checklist": [{"text": "t", "done": true}],
              "a_future_field": "keep-me"}]
        """.trimIndent(), "[]")
        Notebook.deleteNote(load, 1)
        val trashed = trashOnDisk(load).getJSONObject(0)
        assertEquals("p.png",
            trashed.getJSONArray("attachments").getJSONObject(0).getString("filename"))
        assertEquals("keep-me", trashed.getString("a_future_field"))
    }

    @Test
    fun `the trash is written BEFORE the notebook, so a crash cannot lose a note`() {
        // The ordering is the safety argument, so it is tested rather than
        // trusted: make the NOTEBOOK unwritable and delete. The trash copy
        // must already exist -- meaning the interrupted state is "in both
        // files", never "in neither".
        val load = scratchWithTrash("""[{"id": 1, "title": "precious"}]""", "[]")
        val doomed = Notebook.Load(
            notes = load.notes, categories = load.categories, source = "x",
            file = File(load.file!!.parentFile, "no/such/notes_data.json"),
            raw = load.raw, trash = load.trash,
            trashFile = load.trashFile, trashRaw = load.trashRaw,
        )
        val result = Notebook.deleteNote(doomed, 1)
        assertTrue(result is SaveResult.Failed)
        assertEquals("precious", trashOnDisk(load).getJSONObject(0).getString("title"))
        assertTrue("the message must say nothing was lost",
            (result as SaveResult.Failed).reason.contains("nothing was lost"))
    }

    @Test
    fun `an unreadable trash is kept aside, and a delete still keeps the note`() {
        val load = scratchWithTrash("""[{"id": 1, "title": "kept"}]""", "{ not a trash")
        val kept = load.file!!.parentFile!!.listFiles()!!
            .single { it.name.startsWith("trash_corrupt_") }
        assertEquals("{ not a trash", kept.readText())
        assertTrue(Notebook.deleteNote(load, 1) is SaveResult.Ok)
        assertEquals("kept", trashOnDisk(load).getJSONObject(0).getString("title"))
    }

    @Test
    fun `a trash that can neither be read nor kept aside stops the delete`() {
        val load = scratchWithTrash("""[{"id": 1}]""", "[]")
        val stuck = Notebook.Load(
            notes = load.notes, categories = load.categories, source = "x",
            file = load.file, raw = load.raw, trashFile = load.trashFile, trashRaw = null,
        )
        load.trashFile!!.writeText("{ unreadable")
        assertTrue(Notebook.deleteNote(stuck, 1) is SaveResult.Failed)
        // The notebook is untouched: the note is still there.
        assertEquals(1, onDisk(load).length())
    }

    @Test
    fun `the first delete creates the trash file`() {
        val load = scratchWithTrash("""[{"id": 1, "title": "x"}]""", null)
        assertTrue(Notebook.deleteNote(load, 1) is SaveResult.Ok)
        assertEquals(1, trashOnDisk(load).length())
    }

    @Test
    fun `restoring brings the note back and takes it out of the trash`() {
        val load = scratchWithTrash("""[{"id": 1}]""",
            """[{"id": 5, "title": "coming back", "deleted_at": "2026-01-01 10:00:00"}]""")
        assertTrue(Notebook.restoreFromTrash(load, 5) is SaveResult.Ok)

        val back = onDisk(load).getJSONObject(1)
        assertEquals("coming back", back.getString("title"))
        assertFalse("a deletion stamp has no business on a live note",
            back.has("deleted_at"))
        assertEquals(0, trashOnDisk(load).length())
    }

    @Test
    fun `a restored note gets a fresh id, because its old one may be taken`() {
        // Note 5 was deleted; a note created afterwards took id 5. Restoring
        // with the old id would put two notes under one id.
        val load = scratchWithTrash("""[{"id": 5, "title": "the new one"}]""",
            """[{"id": 5, "title": "the old one", "deleted_at": "2026-01-01 10:00:00"}]""")
        val result = Notebook.restoreFromTrash(load, 5) as SaveResult.Ok
        assertEquals(6, result.id)
        val ids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getInt("id") }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `the notebook is written BEFORE the trash on the way back`() {
        // Mirrored reasoning: interrupted, the note is in both files.
        val load = scratchWithTrash("""[{"id": 1}]""",
            """[{"id": 5, "title": "coming back", "deleted_at": "2026-01-01 10:00:00"}]""")
        val doomed = Notebook.Load(
            notes = load.notes, categories = load.categories, source = "x",
            file = load.file, raw = load.raw, trash = load.trash,
            trashFile = File(load.trashFile!!.parentFile, "no/such/trash.json"),
            trashRaw = load.trashRaw,
        )
        assertTrue(Notebook.restoreFromTrash(doomed, 5) is SaveResult.Failed)
        // The note is already safely back in the notebook.
        assertEquals("coming back", onDisk(load).getJSONObject(1).getString("title"))
    }

    @Test
    fun `a series 0_12_0 turned off and sent to the Trash takes its days back into the note as it comes back`() {
        // Restored as it lay, its days were in its copy alone for the rest of
        // the session, and the next copy of words erased them; the desktop's
        // restore shapes it, the move with it (the final audit's seventh round).
        val load = scratchWithTrash("""[{"id": 1, "title": "Other"}]""",
            """[{"id": 5, "uid": "g", "title": "Gym", "body": "Warm up.", "date": "2026-11-02", "recurrence": {"freq": ""},
                "exceptions": [], "overrides": {}, "moved": {}, "deleted_at": "2026-10-27 10:00:00",
                "_prev": {"title": "Gym", "body": "Warm up.", "comments": "", "formatting": [], "paragraphs": [],
                          "date": "2026-11-02", "recurrence": {"freq": "weekly", "interval": 1},
                          "exceptions": [], "overrides": {"2026-11-09": {"body": "Leg day"}}, "moved": {}}}]""")
        val back = Notebook.restoreFromTrash(load, 5) as SaveResult.Ok
        assertTrue(Notebook.updateNote(load, back.id, "Gym", "Rowing only, the whole plan written again from nothing at all.") is SaveResult.Ok)
        val note = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it) }.single { it.getInt("id") == back.id }
        assertEquals("Leg day", note.getJSONObject("overrides").getJSONObject("2026-11-09").getString("body"))
    }

    @Test
    fun `deleting a note that is already gone says so`() {
        val load = scratchWithTrash("""[{"id": 1}]""", "[]")
        assertTrue(Notebook.deleteNote(load, 99) is SaveResult.Failed)
        assertTrue(Notebook.restoreFromTrash(load, 99) is SaveResult.Failed)
    }

    // ---- how long a trashed note has ----

    @Test
    fun `days left counts down from the retention window`() {
        val today = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        assertEquals(30, Notebook.daysLeftInTrash(today, 30))
    }

    @Test
    fun `an unreadable timestamp never reads as due for deletion`() {
        // Fail-safe: better to keep a note too long than to suggest it is
        // about to go.
        assertNull(Notebook.daysLeftInTrash("", 30))
        assertNull(Notebook.daysLeftInTrash("yesterday", 30))
    }

    @Test
    fun `an overdue note shows zero days, never a negative number`() {
        assertEquals(0, Notebook.daysLeftInTrash("2020-01-01 00:00:00", 30))
    }

    // ---- category, colour, date, pin ----

    @Test
    fun `a field left unnamed is left exactly as it was`() {
        // The whole reason these are nullable: a caller editing only the
        // text must not wipe the note's colour by not mentioning it.
        val load = scratch("""
            [{"id": 1, "title": "a", "body": "b", "category": "Work",
              "color": "#ff0000", "date": "2026-01-01", "pinned": true}]
        """.trimIndent())
        Notebook.updateNote(load, 1, "changed", "b")
        val note = onDisk(load).getJSONObject(0)
        assertEquals("Work", note.getString("category"))
        assertEquals("#ff0000", note.getString("color"))
        assertEquals("2026-01-01", note.getString("date"))
        assertTrue(note.getBoolean("pinned"))
    }

    @Test
    fun `category, colour, date and pin are written when they are named`() {
        val load = scratch("""[{"id": 1, "title": "a", "body": "b"}]""")
        Notebook.updateNote(load, 1, "a", "b", category = "Work",
            color = "#4cc9f0", date = "2026-12-25", pinned = true)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("Work", note.getString("category"))
        assertEquals("#4cc9f0", note.getString("color"))
        assertEquals("2026-12-25", note.getString("date"))
        assertTrue(note.getBoolean("pinned"))
    }

    @Test
    fun `changing only a colour still counts as a change worth writing`() {
        val load = scratch("""[{"id": 1, "title": "a", "body": "b"}]""")
        val result = Notebook.updateNote(load, 1, "a", "b", color = "#4cc9f0")
        assertTrue(result is SaveResult.Ok)
        assertEquals("#4cc9f0", onDisk(load).getJSONObject(0).getString("color"))
    }

    @Test
    fun `a colour that is not one is dropped rather than stored`() {
        // Starting WITH a colour, so the write actually happens: on a note
        // that had none, "no colour" is not a change and nothing is
        // written at all -- which is also correct, just not a test of this.
        val load = scratch("""[{"id": 1, "color": "#ff0000"}]""")
        Notebook.updateNote(load, 1, "", "", color = "bright red")
        assertEquals("", onDisk(load).getJSONObject(0).getString("color"))
    }

    @Test
    fun `asking for no colour on a note that has none writes nothing`() {
        val load = scratch("""[{"id": 1}]""")
        val before = load.file!!.readText()
        assertTrue(Notebook.updateNote(load, 1, "", "", color = "") is SaveResult.Ok)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `both hex spellings are accepted, as the desktop accepts them`() {
        assertTrue(Notebook.isHexColor("#abc"))
        assertTrue(Notebook.isHexColor("#AABBCC"))
        assertFalse(Notebook.isHexColor("#ab"))
        assertFalse(Notebook.isHexColor("abc"))
        assertFalse(Notebook.isHexColor("#not-a-colour"))
    }

    @Test
    fun `a three-digit colour comes in as six, as the desktop's sanitize_color expands it`() {
        assertEquals("#ff00aa", Notebook.sanitizeColor("#f0a"))
        assertEquals("the letters' case kept, as Python's ch * 2 keeps it", "#ff00AA", Notebook.sanitizeColor(" #f0A "))
        assertEquals("#123456", Notebook.sanitizeColor("#123456"))
        assertEquals("x", Notebook.sanitizeColor("#12", "x"))
        // And drawn as six: read as six digits, #f00 was a near-black green.
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF0000), hexColor("#f00"))
    }

    @Test
    fun `a date must name a day that actually exists`() {
        assertTrue(Notebook.isDate("2026-02-28"))
        assertTrue(Notebook.isDate("2024-02-29"))
        // Left lenient this would quietly become the 2nd of March.
        assertFalse(Notebook.isDate("2026-02-30"))
        assertFalse(Notebook.isDate("2026-13-01"))
        assertFalse(Notebook.isDate("12-09-2026"))
        assertFalse(Notebook.isDate("tomorrow"))
    }

    @Test
    fun `a date the user typed wrong is refused, not silently dropped`() {
        val load = scratch("""[{"id": 1, "date": "2026-01-01"}]""")
        val result = Notebook.updateNote(load, 1, "", "", date = "32 January")
        assertTrue(result is SaveResult.Failed)
        assertEquals("2026-01-01", onDisk(load).getJSONObject(0).getString("date"))
    }

    @Test
    fun `clearing the date of a REPEATING note is refused`() {
        // The coupling that is easy to miss: normalize_note throws away a
        // recurrence rule whose note has no valid date, so this would
        // delete the repeat on the desktop's next read.
        val load = scratch("""
            [{"id": 1, "date": "2026-01-01",
              "recurrence": {"freq": "weekly", "interval": 1}}]
        """.trimIndent())
        val result = Notebook.updateNote(load, 1, "", "", date = "")
        assertTrue(result is SaveResult.Failed)
        assertTrue((result as SaveResult.Failed).reason.contains("repeat"))
        assertEquals("2026-01-01", onDisk(load).getJSONObject(0).getString("date"))
    }

    @Test
    fun `clearing the date of a ONE-OFF note is perfectly fine`() {
        val load = scratch("""[{"id": 1, "date": "2026-01-01"}]""")
        assertTrue(Notebook.updateNote(load, 1, "", "", date = "") is SaveResult.Ok)
        assertEquals("", onDisk(load).getJSONObject(0).getString("date"))
    }

    @Test
    fun `a blank category falls back to General rather than to nothing`() {
        val load = scratch("""[{"id": 1, "category": "Work"}]""")
        Notebook.updateNote(load, 1, "", "", category = "   ")
        assertEquals("General", onDisk(load).getJSONObject(0).getString("category"))
    }

    // ---- checklist rows ----

    @Test
    fun `a row can be added, renamed and removed`() {
        val load = scratch("""[{"id": 1, "checklist": []}]""")
        assertTrue(Notebook.addChecklistItem(load, 1, "milk") is SaveResult.Ok)
        assertTrue(Notebook.addChecklistItem(load, 1, "bread") is SaveResult.Ok)
        assertEquals(2, onDisk(load).getJSONObject(0).getJSONArray("checklist").length())

        Notebook.renameChecklistItem(load, 1, 0, "oat milk")
        assertEquals("oat milk", onDisk(load).getJSONObject(0)
            .getJSONArray("checklist").getJSONObject(0).getString("text"))

        Notebook.removeChecklistItem(load, 1, 0)
        val left = onDisk(load).getJSONObject(0).getJSONArray("checklist")
        assertEquals(1, left.length())
        assertEquals("bread", left.getJSONObject(0).getString("text"))
    }

    @Test
    fun `a new row starts unticked`() {
        val load = scratch("""[{"id": 1}]""")
        Notebook.addChecklistItem(load, 1, "x")
        assertFalse(onDisk(load).getJSONObject(0)
            .getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
    }

    @Test
    fun `renaming a row keeps whether it was ticked, and anything else on it`() {
        val load = scratch("""
            [{"id": 1, "checklist":
              [{"text": "old", "done": true, "a_future_field": "keep-me"}]}]
        """.trimIndent())
        Notebook.renameChecklistItem(load, 1, 0, "new")
        val row = onDisk(load).getJSONObject(0).getJSONArray("checklist").getJSONObject(0)
        assertEquals("new", row.getString("text"))
        assertTrue(row.getBoolean("done"))
        assertEquals("keep-me", row.getString("a_future_field"))
    }

    @Test
    fun `the checklist ceiling is a door, not a suggestion`() {
        val rows = (1..Notebook.MAX_CHECKLIST_ITEMS)
            .joinToString(",") { """{"text": "$it", "done": false}""" }
        val load = scratch("""[{"id": 1, "checklist": [$rows]}]""")
        val result = Notebook.addChecklistItem(load, 1, "one more")
        assertTrue(result is SaveResult.Failed)
        assertEquals(Notebook.MAX_CHECKLIST_ITEMS,
            onDisk(load).getJSONObject(0).getJSONArray("checklist").length())
    }

    @Test
    fun `a row that is gone is reported, not guessed at`() {
        val load = scratch("""[{"id": 1, "checklist": []}]""")
        assertTrue(Notebook.renameChecklistItem(load, 1, 0, "x") is SaveResult.Failed)
        assertTrue(Notebook.removeChecklistItem(load, 1, 5) is SaveResult.Failed)
        assertTrue(Notebook.addChecklistItem(load, 99, "x") is SaveResult.Failed)
    }

    @Test
    fun `a failed checklist write leaves the note exactly as it was`() {
        val load = scratch("""[{"id": 1, "checklist": [{"text": "a", "done": false}]}]""")
        val doomed = Notebook.Load(
            notes = load.notes, categories = load.categories, source = "x",
            file = File(load.file!!.parentFile, "no/such/notes_data.json"),
            raw = load.raw,
        )
        assertTrue(Notebook.addChecklistItem(doomed, 1, "b") is SaveResult.Failed)
        // The in-memory note the screen reads must not have kept the row.
        val note = Notebook.parseNotes(doomed.raw!!.toString()).single()
        assertEquals(1, note.checklistTotal)
        assertEquals("a", note.checklist.single().text)
    }

    // ---- styles survive the trip to the file ----

    @Test
    fun `a NEW note keeps the styling it was written with`() {
        // This is the test that was missing. The toolbar worked, the screen
        // showed bold text, and `createNote` dropped every span because the
        // parameter had only been added to `updateNote`. Nothing looked
        // wrong until the file was read back.
        val load = scratch("[]")
        val spans = listOf(
            FormatSpan(0, 4, "bold"),
            FormatSpan(5, 9, "text_color", "#e63946"),
        )
        assertTrue(Notebook.createNote(load, "t", "one two", formatting = spans)
            is SaveResult.Ok)
        val written = onDisk(load).getJSONObject(0).getJSONArray("formatting")
        assertEquals(2, written.length())
        assertEquals("bold", written.getJSONObject(0).getString("style"))
        assertEquals("#e63946", written.getJSONObject(1).getString("value"))
    }

    @Test
    fun `an editor's own spans are written as they are, not remapped again`() {
        // The editor has already kept them in step with its text as the
        // user typed. Remapping here would move them a second time.
        val load = scratch("""
            [{"id": 1, "body": "abc", "formatting":
              [{"start": 0, "end": 3, "style": "bold"}]}]
        """.trimIndent())
        val chosen = listOf(FormatSpan(4, 7, "italic"))
        Notebook.updateNote(load, 1, "", "XYZ abc", formatting = chosen)
        val written = onDisk(load).getJSONObject(0).getJSONArray("formatting")
        assertEquals(1, written.length())
        assertEquals("italic", written.getJSONObject(0).getString("style"))
        assertEquals(4, written.getJSONObject(0).getInt("start"))
    }

    @Test
    fun `changing only the styling is a change worth writing`() {
        val load = scratch("""[{"id": 1, "body": "abc", "formatting": []}]""")
        val result = Notebook.updateNote(
            load, 1, "", "abc", formatting = listOf(FormatSpan(0, 3, "bold")))
        assertTrue(result is SaveResult.Ok)
        assertEquals(1, onDisk(load).getJSONObject(0).getJSONArray("formatting").length())
    }

    @Test
    fun `a style with no value is stored without an empty value field`() {
        // normalize_note fills what it needs; writing "value": "" on a bold
        // span would be noise the desktop never writes itself.
        val load = scratch("[]")
        Notebook.createNote(load, "t", "abc",
            formatting = listOf(FormatSpan(0, 3, "bold")))
        val span = onDisk(load).getJSONObject(0).getJSONArray("formatting").getJSONObject(0)
        assertFalse(span.has("value"))
    }

    // settings.json, the retention window and the category map: SettingsTest.

    @Test
    fun `comments replaced keep a copy, weighed as a text of their own`() {
        // The rule read the title and the body alone, though the copy holds
        // the comments: 780 letters of them typed over with "ok" kept none.
        // The desktop's test_data_safety holds the same five answers.
        val comments = "A long thought kept under the note, in words. ".repeat(17).take(780)
        assertTrue("typed over", Notebook.shouldKeepPrevious("t", "body", "t", "body", comments, "ok"))
        assertTrue("emptied", Notebook.shouldKeepPrevious("t", "body", "t", "body", comments, ""))
        // Weighed on their own, not added to the body: a body of 64 letters
        // deleted with 900 letters of comments untouched still keeps one.
        val long = "c".repeat(900)
        assertTrue("a body wiped beside long comments", Notebook.shouldKeepPrevious("t", "b".repeat(64), "t", "", long, long))
        assertTrue(Notebook.shouldKeepPrevious("t", "words of a body ".repeat(4), "t", "x", long, long))
        assertFalse("a sentence added", Notebook.shouldKeepPrevious("t", "body", "t", "body", comments, "$comments One more."))
        assertFalse("comments begun", Notebook.shouldKeepPrevious("t", "body", "t", "body", "", "new"))
        // And through a save: the copy holds the comments that went.
        val load = scratch(JSONArray().put(JSONObject().put("id", 1).put("title", "Plan").put("body", "Keep")
            .put("comments", comments)).toString())
        assertTrue(Notebook.updateNote(load, 1, "Plan", "Keep", comments = "ok") is SaveResult.Ok)
        val saved = onDisk(load).getJSONObject(0)
        assertEquals("ok", saved.getString("comments"))
        assertEquals(comments, saved.getJSONObject("_prev").getString("comments"))
    }
}
