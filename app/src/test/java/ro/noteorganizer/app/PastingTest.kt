package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Pasting with formatting. A few rules by hand first, each one named;
 * then the fixture: several hundred fragments the desktop's own
 * `html_to_note` read, and the phone has to read every one the same.
 */
class PastingTest {

    private fun spans(p: Pasting.Pasted) = p.formatting.map { listOf(it.start, it.end, it.style, it.value) }

    // ---- by hand ----

    @Test
    fun `bold, italic and a real colour come in - a page's grey and its dark background do not`() {
        val p = Pasting.htmlToNote("""<b>Bold</b> <i>it</i> <span style="color:#d32f2f">red</span>""" +
            """ <span style="color:#777777">grey</span> <span style="background:#222244">dark</span>""")
        assertEquals("Bold it red grey dark", p.text)
        assertEquals(listOf(listOf(0, 4, "bold", ""), listOf(5, 7, "italic", ""), listOf(8, 11, "text_color", "#d32f2f")), spans(p))
    }

    @Test
    fun `a Google Docs copy is not bold all over because of its wrapper`() {
        val p = Pasting.htmlToNote("""<b style="font-weight:normal"><p><span style="font-weight:700">Title</span> and text</p></b>""")
        assertEquals("Title and text", p.text)
        assertEquals(listOf(listOf(0, 5, "bold", "")), spans(p))
    }

    @Test
    fun `lists arrive as the editor would have typed them, markers in the text`() {
        val p = Pasting.htmlToNote("<ul><li>one<ul><li>deep</li></ul></li></ul><ol><li>first</li><li>second</li></ol>")
        assertEquals("• one\n◦ deep\n1. first\n2. second", p.text)
        assertEquals(listOf(Exporting.Paragraph(0, "bullet", 0, null), Exporting.Paragraph(1, "bullet", 1, null),
                            Exporting.Paragraph(2, "number", 0, null), Exporting.Paragraph(3, "number", 0, null)), p.paragraphs)
    }

    @Test
    fun `a table becomes rows lined up, a merged cell its marker`() {
        val p = Pasting.htmlToNote("""<table><tr><td colspan="2">wide</td></tr><tr><td>a</td><td><b>b</b></td></tr></table>""")
        assertEquals("| wide     | <        |\n| a        | b        |", p.text)
        // The bold moved with the padding: it is still on the b.
        val b = p.text.indexOf("b        |", p.text.indexOf('\n'))
        assertEquals(listOf(listOf(b, b + 1, "bold", "")), spans(p))
    }

    @Test
    fun `offsets count an emoji as one character, as the desktop does`() {
        val p = Pasting.htmlToNote("😀<b>after</b>")
        assertEquals(listOf(listOf(1, 6, "bold", "")), spans(p))
    }

    @Test
    fun `references read as Python reads them, a broken one left as it was`() {
        assertEquals("& < © ¬it; € �", Pasting.unescape("&amp; &lt; &copy &notit; &#128; &#xD800;"))
        assertEquals("&#; &#x and &", Pasting.unescape("&#; &#x and &"))
    }

    @Test
    fun `a number with more digits than Python reads is a paste of nothing, as on the desktop`() {
        assertEquals("", Pasting.htmlToNote("before &#" + "1".repeat(4301) + "; after").text)
        // Leading zeros are digits too: 4,300 of them in all is the most.
        assertEquals("A", Pasting.htmlToNote("&#" + "0".repeat(4298) + "65;").text)
        assertEquals("", Pasting.htmlToNote("&#" + "0".repeat(4299) + "65;").text)
    }

    @Test
    fun `newlines are kept only when the fragment has no block at all - a PDF's lines`() {
        assertEquals("line one\nline two", Pasting.htmlToNote("line one\nline two").text)
        assertEquals("line one line two\nblock", Pasting.htmlToNote("line one\nline two<p>block</p>").text)
    }

    @Test
    fun `the caps say what they cut`() {
        val long = Pasting.htmlToNote("x".repeat(Pasting.MAX_PASTE_CHARS) + " and more")
        assertEquals(Pasting.MAX_PASTE_CHARS, long.text.length)
        assertTrue(long.textCut)
        val styled = Pasting.htmlToNote("<b>x</b> ".repeat(Pasting.MAX_PASTE_SPANS + 1))
        assertTrue(styled.stylesCut)
        assertEquals(Pasting.MAX_PASTE_SPANS, styled.formatting.size)
    }

    // ---- where a paste lands ----

    @Test
    fun `pasted inside a bold run the words join it - at its edge they do not, as a Tk tag behaves`() {
        val bold = listOf(FormatSpan(0, 6, "bold"))
        val inside = Pasting.insert("abcdef", bold, emptyMap(), 3, 3, "XY")
        assertEquals("abcXYdef", inside.body)
        assertEquals(listOf(FormatSpan(0, 8, "bold")), inside.spans)
        assertEquals(5, inside.caret)
        val atEnd = Pasting.insert("abcdef", bold, emptyMap(), 6, 6, "XY")
        assertEquals(listOf(FormatSpan(0, 6, "bold")), atEnd.spans)
        val atStart = Pasting.insert("abcdef", bold, emptyMap(), 0, 0, "XY")
        assertEquals(listOf(FormatSpan(2, 8, "bold")), atStart.spans)
    }

    @Test
    fun `a selection is replaced, its styles going with it, and a selection ending at a run's end does not stretch it`() {
        val spans = listOf(FormatSpan(0, 3, "bold"), FormatSpan(4, 6, "italic"))
        val done = Pasting.insert("abc def", spans, emptyMap(), 1, 3, "Z")
        assertEquals("aZ def", done.body)
        assertEquals(listOf(FormatSpan(0, 1, "bold"), FormatSpan(3, 5, "italic")), done.spans)
    }

    @Test
    fun `the pasted styles are laid over, a highlight never over a line's end`() {
        val pasted = Pasting.htmlToNote("""<span style="background:#fff3b0">one<br>two</span>""")
        assertEquals("one\ntwo", pasted.text)
        val done = Pasting.insert("x", emptyList(), emptyMap(), 1, 1, pasted.text, pasted.formatting, pasted.paragraphs)
        assertEquals("xone\ntwo", done.body)
        assertEquals(listOf(FormatSpan(1, 4, "highlight", "#fff3b0"), FormatSpan(5, 8, "highlight", "#fff3b0")), done.spans)
    }

    @Test
    fun `table rows get lines of their own, and a blank line from a table they would touch`() {
        val rows = "| a | b |\n| c | d |"
        val mid = Pasting.insert("before after", emptyList(), emptyMap(), 7, 7, rows)
        assertEquals("before \n| a | b |\n| c | d |\nafter", mid.body)
        val table = "| x | y |\n"
        val under = Pasting.insert(table, emptyList(), emptyMap(), table.length, table.length, rows)
        assertEquals("| x | y |\n\n| a | b |\n| c | d |", under.body)
    }

    @Test
    fun `a pasted numbered list carries on the list it joins`() {
        val pasted = Pasting.htmlToNote("<ol><li>three</li><li>four</li></ol>")
        val body = "1. one\n2. two\n"
        val done = Pasting.insert(body, emptyList(), emptyMap(), body.length, body.length, pasted.text,
                                  pasted.formatting, pasted.paragraphs)
        assertEquals("1. one\n2. two\n3. three\n4. four", done.body)
    }

    @Test
    fun `lines after the paste keep their properties, moved down - the caret's line keeps its own`() {
        val props = mapOf(0 to Paragraphs.Props(align = "center"), 1 to Paragraphs.Props(indent = 2))
        val done = Pasting.insert("top\nnext", emptyList(), props, 1, 1, "a\nb\nc")
        assertEquals("ta\nb\ncop\nnext", done.body)
        assertEquals(mapOf(0 to Paragraphs.Props(align = "center"), 3 to Paragraphs.Props(indent = 2)), done.props)
    }

    @Test
    fun `a source's centring on a table row is not kept, on a paragraph it is`() {
        val pasted = Pasting.htmlToNote("""<p style="text-align:center">title</p><table><tr style="text-align:center"><td>a</td><td>b</td></tr></table>""")
        val done = Pasting.insert("", emptyList(), emptyMap(), 0, 0, pasted.text, pasted.formatting, pasted.paragraphs)
        assertTrue(pasted.paragraphs.any { it.line == 1 && it.align == "center" })
        assertEquals(mapOf(0 to Paragraphs.Props(align = "center")), done.props)
    }

    @Test
    fun `the app's own copy comes back whole, its size included, which HTML cannot carry`() {
        val body = "big words here"
        val spans = listOf(FormatSpan(0, 3, "font_size", "24"), FormatSpan(4, 9, "bold"))
        val copy = Pasting.copyOut(body, spans, emptyMap(), 0, 9)!!
        assertEquals("big words", copy.text)
        val back = Pasting.fromHtml(copy.html, copy)!!
        assertEquals(listOf(FormatSpan(0, 3, "font_size", "24"), FormatSpan(4, 9, "bold")), back.formatting)
        // From another app the same HTML is read as HTML: the size is left behind.
        val foreign = Pasting.fromHtml(copy.html, null)!!
        assertEquals(listOf(FormatSpan(4, 9, "bold")), foreign.formatting)
    }

    @Test
    fun `a plain paragraph's indent stays home - one word copied from an indented line indents nothing`() {
        val props = mapOf(0 to Paragraphs.Props(indent = 2))
        val copy = Pasting.copyOut("indented line", emptyList(), props, 0, 8)!!
        assertEquals(listOf(Exporting.Paragraph(0, null, 2, null)), copy.paragraphs)
        assertEquals(emptyList<Exporting.Paragraph>(), Pasting.fromHtml(copy.html, copy)!!.paragraphs)
    }

    @Test
    fun `a page with no formatting at all - a PDF - has its column rejoined`() {
        // Each line broken because the next word would not have fitted.
        val column = "The quick brown fox jumps over the lazy dog and then\n" +
            "continues running through the long field until evening\n" +
            "arrives with the light going down behind distant hills.\n"
        assertEquals("The quick brown fox jumps over the lazy dog and then continues running through the long " +
                     "field until evening arrives with the light going down behind distant hills.",
                     Pasting.fromHtml(column, null)!!.text)
        // The same lines with a style on them keep their breaks: the style's offsets count them.
        assertEquals(3, Pasting.fromHtml("<b>The</b>" + column.drop(3), null)!!.text.split("\n").size)
    }

    @Test
    fun `a list item's depth is given only to a line that is still that kind of item`() {
        // Pasted onto the end of a line, the item's marker is no longer at a line's start.
        val merged = Pasting.insert("abc", emptyList(), emptyMap(), 3, 3, "◦ deep",
                                    pastedLines = listOf(Exporting.Paragraph(0, "bullet", 1, null)))
        assertEquals("abc◦ deep", merged.body)
        assertEquals(emptyMap<Int, Paragraphs.Props>(), merged.props)
        val own = Pasting.insert("abc\n", emptyList(), emptyMap(), 4, 4, "◦ deep",
                                 pastedLines = listOf(Exporting.Paragraph(0, "bullet", 1, null)))
        assertEquals(mapOf(1 to Paragraphs.Props(indent = 1)), own.props)
    }

    @Test
    fun `a copied table carries its own borders, for Word, which reads no stylesheet`() {
        val copy = Pasting.copyOut("| a | b |\n| c | d |", emptyList(), emptyMap(), 0, 19)!!
        assertTrue(copy.html, copy.html.startsWith("<table style=\"border-collapse:collapse;\">"))
        assertTrue(copy.html, "border:1px solid #999;padding:4px 10px;" in copy.html)
    }

    @Test
    fun `the app's own copy cut short by the caps is read as the HTML it is, and said to be cut`() {
        val body = "x".repeat(Pasting.MAX_PASTE_CHARS + 10)
        val copy = Pasting.copyOut(body, emptyList(), emptyMap(), 0, body.length)!!
        val back = Pasting.fromHtml(copy.html, copy)!!
        assertEquals(Pasting.MAX_PASTE_CHARS, back.text.length)
        assertTrue(back.textCut)
    }

    @Test
    fun `a copy carries the properties of its own lines only`() {
        val props = mapOf(0 to Paragraphs.Props(align = "right"), 1 to Paragraphs.Props(align = "center"))
        val copy = Pasting.copyOut("first\nsecond", emptyList(), props, 0, 3)!!
        assertEquals(listOf(Exporting.Paragraph(0, null, 0, "right")), copy.paragraphs)
        val second = Pasting.copyOut("first\nsecond", emptyList(), props, 6, 9)!!
        assertEquals(listOf(Exporting.Paragraph(0, null, 0, "center")), second.paragraphs)
    }

    @Test
    fun `plain text is table rows on lines of their own only when an edge line is a row`() {
        assertEquals("text\n| a | b |", Pasting.pipeRows("text\n| a | b |"))
        assertEquals("| a | b |", Pasting.pipeRows("| a | b |\n"))
        assertEquals("| a | b |\ntext", Pasting.pipeRows("\r\n| a | b |\r\ntext"))
        assertEquals(null, Pasting.pipeRows("text\n| a | b |\nmore"))
        assertEquals(null, Pasting.pipeRows("a | b"))
        assertEquals(null, Pasting.pipeRows(""))
    }

    @Test
    fun `paste as plain text leaves the markers behind`() {
        assertEquals("milk\neggs\n- dash", Pasting.plainText("• milk\n1. eggs\n- dash"))
    }

    // ---- the fixture: what the desktop's own reader made ----

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("paste_cases.json")
        assertNotNull("paste_cases.json is missing - rebuild it with tools/make_paste_fixture.py", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    private fun input(case: JSONObject): String {
        if (case.has("html")) return case.getString("html")
        val parts = case.getJSONArray("html_parts")
        return (0 until parts.length()).joinToString("") {
            val part = parts.getJSONArray(it)
            part.getString(0).repeat(part.getInt(1))
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun rows(array: JSONArray): List<List<Any?>> = (0 until array.length()).map { i ->
        val row = array.getJSONArray(i)
        (0 until row.length()).map { if (row.isNull(it)) null else row.get(it) }
    }

    @Test
    fun `every fragment in the fixture is read as the desktop read it`() {
        val cases = fixture().getJSONArray("cases")
        assertTrue("the fixture is too small to mean anything", cases.length() > 500)
        val wrong = ArrayList<String>()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val got = Pasting.htmlToNote(input(case))
            val label = "#$i (${case.getString("kind")})"
            if (case.has("text")) {
                if (got.text != case.getString("text")) wrong.add("$label text:\n  desktop ${JSONObject.quote(case.getString("text")).take(300)}\n  phone   ${JSONObject.quote(got.text).take(300)}")
            } else {
                val length = got.text.codePointCount(0, got.text.length)
                if (length != case.getInt("text_length") || sha256(got.text) != case.getString("text_sha256")) {
                    wrong.add("$label long text: desktop ${case.getInt("text_length")} characters, phone $length")
                }
            }
            val wantSpans = rows(case.getJSONArray("formatting")).map { r ->
                listOf((r[0] as Number).toInt(), (r[1] as Number).toInt(), r[2], r.getOrNull(3) ?: "")
            }
            if (spans(got) != wantSpans) wrong.add("$label formatting:\n  desktop ${wantSpans.take(8)}\n  phone   ${spans(got).take(8)}")
            val wantLines = rows(case.getJSONArray("paragraphs")).map { r ->
                Exporting.Paragraph((r[0] as Number).toInt(), r[1] as String?, (r[2] as Number).toInt(), r[3] as String?)
            }
            if (got.paragraphs != wantLines) wrong.add("$label paragraphs:\n  desktop $wantLines\n  phone   ${got.paragraphs}")
            if (got.textCut != case.getBoolean("text_cut") || got.stylesCut != case.getBoolean("styles_cut")) {
                wrong.add("$label caps: desktop ${case.getBoolean("text_cut")}/${case.getBoolean("styles_cut")}, phone ${got.textCut}/${got.stylesCut}")
            }
        }
        assertEquals("${wrong.size} differences:\n" + wrong.take(8).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun `the phone parts from the desktop only where a second table's styles land beside their words`() {
        val cases = fixture().getJSONArray("cases")
        val parted = (0 until cases.length()).map { cases.getJSONObject(it) }.filter { it.has("desktop_formatting") }
        assertTrue("the fault is in the fixture, so the difference is checked", parted.size >= 2)
        for (case in parted) {
            val lines = case.getString("text").split("\n")
            val blocks = lines.indices.count { i -> Pasting.isRow(lines[i]) && (i == 0 || !Pasting.isRow(lines[i - 1])) }
            assertTrue("a difference without two tables: ${case.getString("html").take(120)}", blocks >= 2)
        }
    }

    @Test
    fun `a note copied out is the HTML the desktop's copy puts on the clipboard`() {
        val cases = fixture().getJSONArray("cases")
        val copies = (0 until cases.length()).map { cases.getJSONObject(it) }.filter { it.has("note") }
        assertTrue("too few copies to mean anything", copies.size > 50)
        assertTrue("both kinds of table border", copies.any { it.getBoolean("inline") } && copies.any { !it.getBoolean("inline") })
        val wrong = ArrayList<String>()
        for ((i, case) in copies.withIndex()) {
            val note = case.getJSONObject("note")
            val got = Exporting.noteToHtml(note.optString("body"), note.opt("formatting"), note.opt("paragraphs"),
                                           inlineTables = case.getBoolean("inline"))
            if (got != case.getString("html")) wrong.add("copy #$i:\n  desktop ${case.getString("html").take(200)}\n  phone   ${got.take(200)}")
        }
        assertEquals("${wrong.size} differences:\n" + wrong.take(4).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun `plain text is rejoined and unmarked as the desktop's plain paste does`() {
        val plain = fixture().getJSONArray("plain")
        assertTrue(plain.length() > 100)
        val wrong = ArrayList<String>()
        for (i in 0 until plain.length()) {
            val case = plain.getJSONObject(i)
            val text = case.getString("plain")
            val reflowed = Pasting.reflowWrapped(text)
            if (reflowed != case.getString("reflowed")) wrong.add("#$i reflow:\n  desktop ${JSONObject.quote(case.getString("reflowed"))}\n  phone   ${JSONObject.quote(reflowed)}")
            val unmarked = Pasting.stripListMarkers(text)
            if (unmarked != case.getString("unmarked")) wrong.add("#$i markers:\n  desktop ${JSONObject.quote(case.getString("unmarked"))}\n  phone   ${JSONObject.quote(unmarked)}")
        }
        assertEquals("${wrong.size} differences:\n" + wrong.take(4).joinToString("\n"), 0, wrong.size)
    }
}
