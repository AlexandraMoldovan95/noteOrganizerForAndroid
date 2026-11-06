package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One note out as a file. qa1's export checks first, then the fixture: a
 * few hundred notes the desktop itself exported, every byte compared.
 */
class ExportingTest {

    // ---- qa1's "export one note as a file" ----

    private val trial = JSONObject("""{
        "id": 8800, "title": "Export <trial>",
        "body": "first line\n• bullet\n| A | B |\n| --- | --- |\n| [x] | val |\ntail",
        "formatting": [{"start": 0, "end": 5, "style": "bold"}],
        "paragraphs": [{"line": 1, "kind": "bullet"}],
        "comments": "a comment", "category": "General",
        "checklist": [{"text": "done already", "done": true}, {"text": "to do <more>", "done": false}]
    }""")

    @Test
    fun `a web page - a full document, the table as a table, styles and escapes kept`() {
        val html = Exporting.content(trial, Exporting.HTML, Exporting.titleOf(trial))
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue("<table>" in html)
        assertTrue("&lt;trial&gt;" in html)
        assertTrue("<b>first</b>" in html)
        assertTrue("<ul><li>bullet</li></ul>" in html)
    }

    @Test
    fun `plain text - the body as stored, comments included`() {
        val txt = Exporting.content(trial, Exporting.TXT, Exporting.titleOf(trial))
        assertTrue("first line" in txt)
        assertTrue("a comment" in txt)
        assertTrue(txt.startsWith("Export <trial>\n\nfirst line"))
        assertTrue(txt.endsWith("\n"))
    }

    @Test
    fun `the checklist goes out in both formats, its states kept`() {
        val html = Exporting.content(trial, Exporting.HTML, Exporting.titleOf(trial))
        val txt = Exporting.content(trial, Exporting.TXT, Exporting.titleOf(trial))
        assertTrue("☑ done already" in html)
        assertTrue("☐ to do" in html)
        assertTrue("&lt;more&gt;" in html)
        assertTrue("[x] done already" in txt)
        assertTrue("[ ] to do <more>" in txt)
    }

    @Test
    fun `the file name offered is scrubbed of what Windows forbids`() {
        assertEquals("the scrub's last underscore is trimmed too", "Export _trial.html",
                     Exporting.fileName("Export <trial>", Exporting.HTML))
        assertEquals("note.txt", Exporting.fileName("...", Exporting.TXT))
        assertEquals("Trip_ day 1_2.html", Exporting.fileName("Trip: day 1/2", Exporting.HTML))
        assertEquals("sixty characters at most", 60 + 5, Exporting.fileName("x".repeat(80), Exporting.HTML).length)
    }

    @Test
    fun `an untitled note is called so`() {
        assertEquals("Untitled note", Exporting.titleOf(JSONObject("""{"title": "   "}""")))
        assertEquals("Shopping", Exporting.titleOf(JSONObject("""{"title": "  Shopping\u0085"}""")))
    }

    @Test
    fun `half an emoji alone becomes a replacement mark, a whole one stays`() {
        assertEquals("x" + Exporting.REPLACEMENT + "y", Exporting.mend("x\uD83Dy"))
        assertEquals("😀", Exporting.mend("😀"))
    }

    // ---- three cases the fixture's random notes never happened to hold ----

    @Test
    fun `a list numbered in another script's digits loses its number in the item`() {
        val html = Exporting.noteToHtml("٣. arabic", paragraphs = JSONArray("""[{"line": 0, "kind": "number"}]"""))
        assertEquals("<ol><li>arabic</li></ol>", html)
    }

    @Test
    fun `when bold and italic fold, two colours on one letter are settled in the sorted order`() {
        // Red over 0-5 written before blue over 0-3: as written, blue would
        // be the later span on 0-3; sorted, as the desktop sorts after a
        // fold, red is -- and the later span is the one a letter wears.
        val html = Exporting.noteToHtml("hello", JSONArray("""[
            {"start": 0, "end": 5, "style": "text_color", "value": "#ff0000"},
            {"start": 0, "end": 3, "style": "text_color", "value": "#0000ff"},
            {"start": 0, "end": 5, "style": "bold"}, {"start": 0, "end": 5, "style": "italic"}]"""))
        assertEquals("<div><span style=\"color:#ff0000;\"><i><b>hello</b></i></span></div>", html)
    }

    @Test
    fun `reading stops at the ceiling, before any folding`() {
        val spans = JSONArray()
        repeat(9_999) { spans.put(JSONObject().put("start", it).put("end", it + 1).put("style", "underline")) }
        spans.put(JSONObject().put("start", 0).put("end", 10).put("style", "italic"))
        spans.put(JSONObject().put("start", 0).put("end", 10).put("style", "bold"))
        val read = Exporting.normalizeFormatting(spans)
        assertEquals(10_000, read.size)
        assertTrue("the bold past the ceiling was never read", read.none { it.style == "bold" || it.style == "bold_italic" })
    }

    // ---- the fixture: what the desktop's own renderer wrote ----

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("export_cases.json")
        assertNotNull("export_cases.json is missing - rebuild it with tools/make_export_fixture.py", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `every note in the fixture is exported as the desktop exported it`() {
        val cases = fixture().getJSONArray("cases")
        assertTrue("the fixture is too small to mean anything", cases.length() > 300)
        val wrong = ArrayList<String>()
        var compared = 0
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val note = case.getJSONObject("note")
            val title = Exporting.titleOf(note)
            if (title != case.getString("title")) wrong.add("#$i title: desktop ${case.getString("title")}, phone $title")
            val name = Exporting.fileName(title, Exporting.HTML)
            if (name != case.getString("name")) wrong.add("#$i name: desktop ${case.getString("name")}, phone $name")
            if (case.has("raises")) {
                // The desktop's renderer raised on this one (see the test
                // below); the phone must simply not.
                Exporting.content(note, Exporting.HTML, title)
                continue
            }
            for (format in listOf(Exporting.HTML, Exporting.TXT)) {
                val want = case.getString(format)
                val got = Exporting.content(note, format, title)
                compared++
                if (got != want) wrong.add("#$i $format:\n  desktop ${firstDifference(want, got)}")
            }
        }
        assertEquals(wrong.take(6).joinToString("\n"), 0, wrong.size)
        assertTrue(compared > 700)
    }

    @Test
    fun `a table too large to draw is written into a page in time that grows with its size, not its square`() {
        // A row of 50,000 empty cells (a code of 186 characters) looked for the
        // row's pipes at each cell; 60,000 rows of "^" asked every merge at each
        // row. Here 20,000 and 30,000: minutes the old way, a fraction of a second now.
        val empty = JSONObject().put("title", "Empty cells").put("body", "|".repeat(20_001))
        val merged = JSONObject().put("title", "Merged").put("body", "| a | b |\n" + List(30_000) { "| ^ | ^ |" }.joinToString("\n"))
        val hidden = JSONObject().put("title", "Hidden").put("body",
            "| " + List(20_000) { "x" }.joinToString(" | ") + " |\n|" + List(20_000) { if (it % 2 == 0) "---" else "~" }.joinToString("|") + "|\n" +
            "| " + List(20_000) { if (it % 2 == 0) "y" else "<" }.joinToString(" | ") + " |")
        for (note in listOf(empty, merged, hidden)) {
            val started = System.nanoTime()
            val page = Exporting.content(note, Exporting.HTML, note.getString("title"))
            val seconds = (System.nanoTime() - started) / 1e9
            assertTrue("${note.getString("title")}: %.1f s".format(seconds), seconds < 5)
            assertTrue(page.contains("<table>"))
        }
        assertEquals(20_000, Regex("<th>").findAll(Exporting.content(empty, Exporting.HTML, "E")).count())
        assertTrue(Exporting.content(merged, Exporting.HTML, "M").contains("rowspan=\"30001\""))
        // A merge narrows by the hidden columns inside it, and only those:
        // one hidden just past its end leaves it as wide as it was.
        fun page(body: String) = Exporting.content(JSONObject().put("title", "H").put("body", body), Exporting.HTML, "H")
        assertTrue(page("| a | < | c |\n|---|---|~|\n| d | e | f |").contains("<th colspan=\"2\">a</th>"))
        assertTrue(page("| a | < | < |\n|---|~|---|\n| d | e | f |").contains("<th colspan=\"2\">a</th>"))
        assertTrue(page("| a | < | c |\n|~|---|---|\n| d | e | f |").let { !it.contains("colspan") })
    }

    @Test
    fun `where the desktop's renderer raised, it is a merge under a shorter row`() {
        val cases = fixture().getJSONArray("cases")
        val raised = (0 until cases.length()).map { cases.getJSONObject(it) }.filter { it.has("raises") }
        // Recorded for the desktop: merge_map reads the cell above a "^"
        // without asking whether the row above has that many cells.
        assertTrue(raised.all { it.getString("raises") == "IndexError" && "^" in it.getJSONObject("note").getString("body") })
    }

    private fun firstDifference(want: String, got: String): String {
        var i = 0
        while (i < want.length && i < got.length && want[i] == got[i]) i++
        val from = maxOf(0, i - 40)
        return "…${want.substring(from, minOf(want.length, i + 40))}…\n  phone   …${got.substring(from, minOf(got.length, i + 40))}…"
    }

    // ---- the gatekeepers, by hand ----

    @Test
    fun `a bold and an italic over the same words fold into one bold_italic`() {
        val spans = Exporting.normalizeFormatting(JSONArray("""[
            {"start": 0, "end": 10, "style": "bold"}, {"start": 5, "end": 15, "style": "italic"}]"""))
        assertEquals(listOf(FormatSpan(0, 5, "bold"), FormatSpan(5, 10, "bold_italic"), FormatSpan(10, 15, "italic")), spans)
    }

    @Test
    fun `only the desktop's sizes, faces and colours survive`() {
        val spans = Exporting.normalizeFormatting(JSONArray("""[
            {"start": 0, "end": 2, "style": "font_size", "value": 13},
            {"start": 0, "end": 2, "style": "font_size", "value": 12},
            {"start": 0, "end": 2, "style": "font_size", "value": true},
            {"start": 0, "end": 2, "style": "font_family", "value": "Comic Sans"},
            {"start": 0, "end": 2, "style": "text_color", "value": "#FFAA00"},
            {"start": 0, "end": 2, "style": "highlight", "value": "#gggggg"},
            {"start": 1.0, "end": 2, "style": "bold"}]"""))
        assertEquals(listOf(FormatSpan(0, 2, "font_size", "12"), FormatSpan(0, 2, "text_color", "#ffaa00")), spans)
    }

    @Test
    fun `paragraph entries - one per line, in range, defaults dropped`() {
        val paras = Exporting.normalizeParagraphs(JSONArray("""[
            {"line": 2, "kind": "bullet"}, {"line": 2, "kind": "number"}, {"line": 9, "kind": "bullet"},
            {"line": 0, "align": "left"}, {"line": true, "align": "center"}, {"line": 3, "indent": 20}]"""), 5)
        assertEquals(listOf(
            Exporting.Paragraph(1, null, 0, "center"),
            Exporting.Paragraph(2, "bullet", 0, null),
            Exporting.Paragraph(3, null, 8, null)), paras)
    }

    @Test
    fun `nothing in a note's styles can break out of the page`() {
        val note = JSONObject().put("title", "t").put("body", "<script>").put("formatting",
            JSONArray().put(JSONObject().put("start", 0).put("end", 3).put("style", "font_family").put("value", "Arial")))
        val html = Exporting.content(note, Exporting.HTML, "t")
        assertFalse("<script>" in html.substringAfter("<body>"))
    }
}
