package ro.noteorganizer.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lists, indents and alignment: the desktop's editor, line for line. */
class ParagraphsTest {

    private val P = Paragraphs
    private fun props(vararg pairs: Pair<Int, Int>) = pairs.associate { (line, indent) -> line to Paragraphs.Props(indent) }

    // ---- the desktop's own renumber, over the fixture ----

    @Test
    fun `every body in the fixture is renumbered as the desktop renumbers it`() {
        val stream = javaClass.classLoader?.getResourceAsStream("renumber_cases.json")
        assertNotNull("renumber_cases.json is missing - rebuild it with tools/make_renumber_fixture.py", stream)
        val cases = JSONObject(stream!!.readBytes().toString(Charsets.UTF_8)).getJSONArray("cases")
        val wrong = ArrayList<String>()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val indents = case.getJSONArray("indents")
            val p = (0 until indents.length()).filter { indents.getInt(it) > 0 }
                .associateWith { Paragraphs.Props(indents.getInt(it)) }
            val got = P.renumber(case.getString("body"), p).body
            if (got != case.getString("renumbered")) wrong.add("#$i\n  desktop ${case.getString("renumbered")}\n  phone   $got")
        }
        assertEquals(wrong.take(3).joinToString("\n"), 0, wrong.size)
        assertTrue(cases.length() > 250)
    }

    // ---- markers ----

    @Test
    fun `a list entry whose marker is gone keeps its alignment, not its depth`() {
        // The desktop's _apply_paragraphs: the body rewritten outside the
        // app, its paragraphs not. Kept, the phone indented a plain line and
        // saved an indent the desktop then honoured.
        val stored = org.json.JSONArray("""[
            {"line": 0, "kind": "bullet", "indent": 2, "align": "center"},
            {"line": 1, "kind": "bullet", "indent": 1},
            {"line": 2, "indent": 1}]""")
        val props = P.propsOf(stored, "plain line\n◦ still a bullet\nindented prose")
        assertEquals(mapOf(0 to Paragraphs.Props(0, "center"), 1 to Paragraphs.Props(1), 2 to Paragraphs.Props(1)), props)
        // By content: the test's JSON library keeps keys in its own order.
        val written = P.extract("plain line\n◦ still a bullet\nindented prose", props)
        val entries = (0 until written.length()).map { i ->
            val o = written.getJSONObject(i)
            o.keys().asSequence().sorted().joinToString(",") { "$it=${o.get(it)}" }
        }
        assertEquals(listOf("align=center,line=0", "indent=1,kind=bullet,line=1", "indent=1,line=2"), entries)
    }

    @Test
    fun `a marker is a glyph and a space, or a number, a dot and a space`() {
        assertEquals(Paragraphs.Marker("bullet", "◦ ", "deeper"), P.parseListMarker("◦ deeper"))
        assertEquals(Paragraphs.Marker("number", "12. ", "x"), P.parseListMarker("12. x"))
        assertEquals("digits from any script", "number", P.parseListMarker("٣. x").kind)
        assertNull(P.parseListMarker("•no space").kind)
        assertNull(P.parseListMarker("- dash").kind)
        assertEquals("▪ ", P.markerFor("bullet", 1, 2))
        assertEquals("• ", P.markerFor("bullet", 1, 3))
        assertEquals("numbers never change with depth", "4. ", P.markerFor("number", 4, 2))
    }

    @Test
    fun `what is stored - the kind read off the marker, the indent and alignment from the line`() {
        val stored = P.extract("• a\nplain\n1. b\n• gone", mapOf(1 to Paragraphs.Props(0, "center"), 2 to Paragraphs.Props(2)))
        assertEquals(listOf(
            Exporting.Paragraph(0, "bullet", 0, null), Exporting.Paragraph(1, null, 0, "center"),
            Exporting.Paragraph(2, "number", 2, null), Exporting.Paragraph(3, "bullet", 0, null)),
            Exporting.normalizeParagraphs(stored, 4))
        assertEquals("a plain left line stores nothing", 4, stored.length())
    }

    // ---- turning lines into a list ----

    @Test
    fun `numbering lines skips the blank ones between them`() {
        val done = P.toggleList("milk\n\nbread\neggs", emptyMap(), 0, 3, "number")!!
        assertEquals("1. milk\n\n2. bread\n3. eggs", done.body)
    }

    @Test
    fun `pressed again, the list is gone, and depth stays`() {
        val on = P.toggleList("a\nb", props(1 to 2), 0, 1, "bullet")!!
        assertEquals("• a\n▪ b", on.body)
        val off = P.toggleList(on.body, on.props, 0, 1, "bullet")!!
        assertEquals("a\nb", off.body)
        assertEquals(props(1 to 2), off.props)
    }

    @Test
    fun `a bullet becomes a number, not a bullet with a number`() {
        assertEquals("1. a\n2. b", P.toggleList("• a\n• b", emptyMap(), 0, 1, "number")!!.body)
    }

    @Test
    fun `one empty line starts a list, a table row never joins one`() {
        assertEquals("• ", P.toggleList("", emptyMap(), 0, 0, "bullet")!!.body)
        assertNull(P.toggleList("| a | b |", emptyMap(), 0, 0, "bullet"))
        assertEquals("• x\n| a | b |", P.toggleList("x\n| a | b |", emptyMap(), 0, 1, "bullet")!!.body)
    }

    @Test
    fun `a style moves with its words when a marker goes in before them`() {
        val done = P.toggleList("intro\nbold here", emptyMap(), 1, 1, "bullet")!!
        assertEquals(listOf(FormatSpan(8, 12, "bold")), done.move(listOf(FormatSpan(6, 10, "bold"))))
        assertEquals("• bold here", done.body.split("\n")[1])
    }

    // ---- depth and alignment ----

    @Test
    fun `indenting a bullet changes its glyph, and each line is clamped on its own`() {
        val done = P.changeIndent("• a\n• b", props(1 to 8), 0, 1, 1)
        assertEquals("◦ a\n▪ b", done.body)
        assertEquals(props(0 to 1, 1 to 8), done.props)
        val back = P.changeIndent(done.body, done.props, 0, 1, -1)
        assertEquals(props(1 to 7), back.props)
        assertEquals("• a\n◦ b", back.body)
    }

    @Test
    fun `a deeper numbered list counts again from one`() {
        val done = P.changeIndent("1. a\n2. b\n3. c", emptyMap(), 1, 1, 1)
        assertEquals("1. a\n1. b\n2. c", done.body)
    }

    @Test
    fun `aligning lines already aligned that way puts them back left`() {
        val centred = P.setAlign("a\nb", emptyMap(), 0, 1, "center")
        assertEquals(mapOf(0 to Paragraphs.Props(0, "center"), 1 to Paragraphs.Props(0, "center")), centred)
        assertEquals(emptyMap<Int, Paragraphs.Props>(), P.setAlign("a\nb", centred, 0, 1, "center"))
        assertEquals("mixed: all take it", 2, P.setAlign("a\nb", mapOf(0 to Paragraphs.Props(0, "center")), 0, 1, "center")
            .count { it.value.align == "center" })
    }

    // ---- what a keystroke means in a list ----

    @Test
    fun `Enter after an item continues the list, numbered on`() {
        val done = P.afterTyping("1. milk", "1. milk\n", emptyMap(), 8)!!
        assertEquals("1. milk\n2. ", done.body)
        assertEquals("the cursor after the new marker", 11, done.caret)
    }

    @Test
    fun `Enter is placed by the cursor, not by a guess`() {
        // "• a" then "b": typed at the end of the item, the texts alone
        // would read as a break at the start of "b".
        val done = P.afterTyping("• a\nb", "• a\n\nb", emptyMap(), 4)!!
        assertEquals("• a\n• \nb", done.body)
    }

    @Test
    fun `Enter in the middle of an item splits it into two items`() {
        val done = P.afterTyping("• milk bread", "• milk\n bread", emptyMap(), 7)!!
        assertEquals("• milk\n•  bread", done.body)
        assertEquals(9, done.caret)
    }

    @Test
    fun `Enter on an empty item leaves the list, a level at a time`() {
        val nested = P.afterTyping("• a\n◦ ", "• a\n◦ \n", props(1 to 1), 7)!!
        assertEquals("steps out first", "• a\n• ", nested.body)
        assertEquals(emptyMap<Int, Paragraphs.Props>(), nested.props)
        val margin = P.afterTyping("• a\n• ", "• a\n• \n", emptyMap(), 7)!!
        assertEquals("then the marker goes", "• a\n", margin.body)
        assertEquals(4, margin.caret)
    }

    @Test
    fun `Backspace against a marker takes the whole marker, or climbs a level`() {
        val margin = P.afterTyping("x\n• milk", "x\n•milk", emptyMap(), 3)!!
        assertEquals("x\nmilk", margin.body)
        assertEquals(2, margin.caret)
        val nested = P.afterTyping("◦ milk", "◦milk", props(0 to 1), 1)!!
        assertEquals("• milk", nested.body)
        assertEquals(props(), nested.props)
        assertNull("a letter of the text is just a letter", P.afterTyping("• milk", "• mil", emptyMap(), 5))
    }

    @Test
    fun `ordinary typing is left as typed`() {
        assertNull(P.afterTyping("plain", "plain\n", emptyMap(), 6))
        assertNull(P.afterTyping("• a", "• ab", emptyMap(), 4))
    }

    // ---- lines and styles through edits ----

    @Test
    fun `a line's properties follow it when lines are added above`() {
        assertEquals(mapOf(2 to Paragraphs.Props(1)), P.follow("a\nb", "new\na\nb", mapOf(1 to Paragraphs.Props(1))))
    }

    @Test
    fun `a centred or indented line broken by Enter keeps it on both halves, as the desktop's tags stay`() {
        val centred = mapOf(1 to Paragraphs.Props(align = "center"), 2 to Paragraphs.Props(1))
        // Broken in the middle: both halves; the line under moves down with its own.
        assertEquals(mapOf(1 to Paragraphs.Props(align = "center"), 2 to Paragraphs.Props(align = "center"),
                           3 to Paragraphs.Props(1)),
                     P.follow("top\nTitle here\nnext", "top\nTitle\n here\nnext", centred))
        // Broken at its start: the line moves down whole, a blank one above.
        assertEquals(mapOf(2 to Paragraphs.Props(align = "center"), 3 to Paragraphs.Props(1)),
                     P.follow("top\nTitle\nnext", "top\n\nTitle\nnext", centred))
        // Joined by Backspace: the upper line's kept, the lower's gone.
        assertEquals(mapOf(1 to Paragraphs.Props(align = "center")),
                     P.follow("top\nTitle\nnext", "top\nTitlenext", centred))
    }

    @Test
    fun `Enter after a centred item continues it centred`() {
        val done = P.afterTyping("1. milk", "1. milk\n", mapOf(0 to Paragraphs.Props(align = "center")), 8)!!
        assertEquals("center", done.props[1]?.align)
    }

    @Test
    fun `text put in at a style's end stays outside it, a removed marker takes nothing with it`() {
        val spans = listOf(FormatSpan(0, 2, "bold"), FormatSpan(2, 5, "italic"))
        assertEquals(listOf(FormatSpan(0, 2, "bold"), FormatSpan(4, 7, "italic")),
            Paragraphs.moveSpans(spans, listOf(Paragraphs.Edit(2, 0, 2))))
        assertEquals("a span only on removed text is gone", listOf(FormatSpan(0, 3, "italic")),
            Paragraphs.moveSpans(spans, listOf(Paragraphs.Edit(0, 2, 0))))
    }
}
