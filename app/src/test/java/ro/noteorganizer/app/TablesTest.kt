package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table rules, pinned against the behaviour `core/tables.py` defines.
 * These run on the computer; no phone is involved.
 */
class TablesTest {

    private fun table(vararg lines: String) = parseTable(lines.toList())

    // ---- finding the blocks ----

    @Test
    fun `a run of rows is one block, and a gap starts another`() {
        val body = "text\n| a | b |\n| c | d |\ntext\n| e | f |"
        assertEquals(listOf(1..2, 4..4), tableBlocks(body))
    }

    @Test
    fun `a body with no table has no blocks`() {
        assertEquals(emptyList<IntRange>(), tableBlocks("only text\non two lines"))
    }

    @Test
    fun `a table running to the end of the body is still a block`() {
        assertEquals(listOf(1..2), tableBlocks("text\n| a | b |\n| c | d |"))
    }

    // ---- cutting the body into pieces ----

    @Test
    fun `prose before and after a table comes back as its own pieces`() {
        val body = "before\n| a | b |\n| c | d |\nafter"
        val parts = splitBody(body)
        assertEquals(3, parts.size)

        val first = parts[0] as BodyPart.Prose
        assertEquals("before", body.substring(first.from, first.to))
        assertTrue(parts[1] is BodyPart.Grid)
        val last = parts[2] as BodyPart.Prose
        assertEquals("after", body.substring(last.from, last.to))
    }

    @Test
    fun `a body that is only a table has no prose pieces`() {
        val parts = splitBody("| a | b |")
        assertEquals(1, parts.size)
        assertTrue(parts[0] is BodyPart.Grid)
    }

    @Test
    fun `a body with no table is one piece covering all of it`() {
        val body = "only text\non two lines"
        val parts = splitBody(body)
        assertEquals(1, parts.size)
        val prose = parts[0] as BodyPart.Prose
        assertEquals(body, body.substring(prose.from, prose.to))
    }

    @Test
    fun `two tables with prose between them come back in order`() {
        val body = "one\n| a | b |\ntwo\n| c | d |\nthree"
        val kinds = splitBody(body).map { if (it is BodyPart.Grid) "T" else "P" }
        assertEquals(listOf("P", "T", "P", "T", "P"), kinds)
    }

    @Test
    fun `the offsets stay valid on a body holding an emoji`() {
        // The slices index a rendered AnnotatedString, which is UTF-16 --
        // so these offsets must be UTF-16 too, unlike the formatting
        // spans, which are code points. Getting the two mixed up would
        // cut a surrogate pair in half.
        val body = "👣 before\n| a | b |\n👣 after"
        val parts = splitBody(body)
        val first = parts[0] as BodyPart.Prose
        val last = parts[2] as BodyPart.Prose
        assertEquals("👣 before", body.substring(first.from, first.to))
        assertEquals("👣 after", body.substring(last.from, last.to))
    }

    @Test
    fun `an empty body yields nothing to draw`() {
        assertEquals(emptyList<BodyPart>(), splitBody(""))
    }

    // ---- the grid ----

    @Test
    fun `a plain table becomes rows of cells`() {
        val t = table("| Item | Qty |", "| Milk | 2 |")
        assertEquals(2, t.rows.size)
        assertEquals("Item", t.rows[0][0]!!.lines.single())
        assertEquals("2", t.rows[1][1]!!.lines.single())
    }

    @Test
    fun `the separator is mechanics and is not drawn`() {
        val t = table("| Item | Qty |", "| --- | ---: |", "| Milk | 2 |")
        assertEquals("only the two rows of content", 2, t.rows.size)
        assertEquals(1, t.headerRows)
        assertNull(t.columns[0].align)
        assertEquals(CellAlign.RIGHT, t.columns[1].align)
    }

    @Test
    fun `alignment colons are read on both sides`() {
        val columns = parseSeparator("| :--- | :---: | ---: | --- | ~ |")
        assertEquals(CellAlign.LEFT, columns[0].align)
        assertEquals(CellAlign.CENTER, columns[1].align)
        assertEquals(CellAlign.RIGHT, columns[2].align)
        assertNull(columns[3].align)
        assertTrue(columns[4].hidden)
    }

    @Test
    fun `a ragged table is padded so the columns cannot shift`() {
        val t = table("| a | b | c |", "| d |")
        assertEquals(3, t.rows[1].size)
        assertEquals("", t.rows[1][2]!!.lines.single())
    }

    // ---- what a cell can hold ----

    @Test
    fun `a checkbox cell is a control, not the characters that spell it`() {
        val t = table("| [x] | [ ] | text |")
        assertEquals(true, t.rows[0][0]!!.checked)
        assertEquals(false, t.rows[0][1]!!.checked)
        assertNull(t.rows[0][2]!!.checked)
    }

    @Test
    fun `a break marker inside a cell is the line break it draws`() {
        val t = table("| one<br>two | x |")
        assertEquals(listOf("one", "two"), t.rows[0][0]!!.lines)
    }

    // ---- merges, the case a real notebook uses 102 times ----

    @Test
    fun `a cell merged to the left swallows the position beside it`() {
        val t = table("| wide | < | c |")
        assertEquals(2, t.rows[0][0]!!.colSpan)
        assertNull("the swallowed position is not drawn", t.rows[0][1])
        assertEquals("c", t.rows[0][2]!!.lines.single())
    }

    @Test
    fun `several markers in a row extend the same anchor`() {
        val t = table("| wide | < | < | < |")
        assertEquals(4, t.rows[0][0]!!.colSpan)
        assertNull(t.rows[0][1])
        assertNull(t.rows[0][2])
        assertNull(t.rows[0][3])
    }

    @Test
    fun `a marker with nothing to attach to stays ordinary text`() {
        // "<" in the first column has no cell on its left. The desktop
        // leaves it alone rather than guessing, and so does this.
        val t = table("| < | b |")
        assertEquals("<", t.rows[0][0]!!.lines.single())
        assertEquals(1, t.rows[0][0]!!.colSpan)
    }

    @Test
    fun `a merge upwards is understood even though no real note uses one`() {
        val rows = listOf(listOf("up", "b"), listOf("^", "d"))
        assertEquals(mapOf((1 to 0) to (0 to 0)), mergeAnchors(rows))
    }

    // ---- column widths ----

    @Test
    fun `a column is as wide as its widest cell`() {
        val t = table("| a | short |", "| longer | b |")
        assertEquals("longer".length, t.charWidths[0])
        assertEquals("short".length, t.charWidths[1])
    }

    @Test
    fun `a cell that spans columns does not stretch any of them`() {
        // Letting it would widen a column to fit text that is not in it.
        val t = table("| a very long text right here | < |", "| a | b |")
        assertEquals(1, t.charWidths[0])
        assertEquals(1, t.charWidths[1])
    }

    @Test
    fun `a checkbox column stays one character wide`() {
        val t = table("| [x] | b |", "| [ ] | c |")
        assertEquals(1, t.charWidths[0])
    }

    @Test
    fun `a wrapped cell is measured by its longest line, not its whole text`() {
        val t = table("| short<br>much-much-longer | b |")
        assertEquals("much-much-longer".length, t.charWidths[0])
    }

    // ---- editing a table ----

    @Test
    fun `alignment is read off the padding, because that is where it lives`() {
        val line = "|  centre  |    right |left    |"
        assertEquals(
            listOf(CellAlign.CENTER, CellAlign.RIGHT, CellAlign.LEFT),
            cellAlignments(line),
        )
    }

    @Test
    fun `an empty cell is left aligned, not guessed at`() {
        assertEquals(listOf(CellAlign.LEFT, CellAlign.LEFT), cellAlignments("|    |   |"))
    }

    @Test
    fun `a rewritten row keeps every column at least the minimum width`() {
        val out = formatRow(listOf("a", "b"), layoutWidths(listOf(listOf("a", "b")),
            listOf(listOf(CellAlign.LEFT, CellAlign.LEFT))), listOf(CellAlign.LEFT))
        assertEquals("| a        | b        |", out)
    }

    @Test
    fun `a rewritten table reads back with the SAME alignments`() {
        // The round trip that matters: padding in, padding out. Rebuild a
        // table without this and a right-aligned column silently goes left.
        val original = "|  centre  |    right | left   |"
        val edited = TableEdit(listOf(original)).setCell(0, 2, "left").lines()
        assertEquals(cellAlignments(original), cellAlignments(edited[0]))
    }

    @Test
    fun `editing a cell changes that cell and nothing else`() {
        val lines = listOf("| a | b |", "| c | d |")
        val out = TableEdit(lines).setCell(1, 0, "NEW").lines()
        val rows = out.map { splitRow(it) }
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("NEW", "d"), rows[1])
    }

    @Test
    fun `a pipe typed into a cell cannot split the table`() {
        // Left alone it would read as two cells next time, changing the
        // table's shape without anyone asking.
        val out = TableEdit(listOf("| a | b |")).setCell(0, 0, "one | two").lines()
        assertEquals(2, splitRow(out[0]).size)
        assertEquals("one / two", splitRow(out[0])[0])
    }

    @Test
    fun `a new row arrives empty and the right width`() {
        val out = TableEdit(listOf("| a | b | c |")).insertRowAfter(0).lines()
        assertEquals(2, out.size)
        assertEquals(listOf("", "", ""), splitRow(out[1]))
    }

    @Test
    fun `a row can be removed, but never the last one`() {
        val two = TableEdit(listOf("| a |", "| b |")).removeRow(0).lines()
        assertEquals(1, two.size)
        assertEquals(listOf("b"), splitRow(two[0]))
        // The last row stays: a table with no rows stops being a table at
        // all, and its lines would read as prose on the next open.
        val one = TableEdit(listOf("| b |")).removeRow(0).lines()
        assertEquals(1, one.size)
    }

    @Test
    fun `a new column is mechanics in the separator row and empty elsewhere`() {
        val lines = listOf("| a | b |", "| --- | --- |", "| c | d |")
        val out = TableEdit(lines).insertColumnAfter(0).lines()
        assertEquals(listOf("a", "", "b"), splitRow(out[0]))
        assertEquals(listOf("---", "---", "---"), splitRow(out[1]))
        assertTrue("the separator row must stay a separator", isSeparatorRow(out[1]))
        assertEquals(listOf("c", "", "d"), splitRow(out[2]))
    }

    @Test
    fun `a column can be removed, but never the last one`() {
        val out = TableEdit(listOf("| a | b |", "| c | d |")).removeColumn(0).lines()
        assertEquals(listOf("b"), splitRow(out[0]))
        assertEquals(listOf("d"), splitRow(out[1]))
        val single = TableEdit(listOf("| a |")).removeColumn(0).lines()
        assertEquals(1, splitRow(single[0]).size)
    }

    @Test
    fun `a ragged table is squared up before it is edited`() {
        val out = TableEdit(listOf("| a | b | c |", "| d |")).setCell(1, 2, "x").lines()
        assertEquals(listOf("d", "", "x"), splitRow(out[1]))
    }

    @Test
    fun `an edited table still parses as the same shape`() {
        val lines = listOf("| Item | Qty |", "| --- | ---: |", "| Milk | 2 |")
        val out = TableEdit(lines).setCell(2, 1, "3").lines()
        val table = parseTable(out)
        assertEquals(2, table.rows.size)
        assertEquals(1, table.headerRows)
        assertEquals(CellAlign.RIGHT, table.columns[1].align)
        assertEquals("3", table.rows[1][1]!!.lines.single())
    }

    @Test
    fun `a checkbox stays a checkbox through an edit`() {
        val out = TableEdit(listOf("| [x] | a |")).setCell(0, 1, "b").lines()
        assertEquals(true, checkboxState(splitRow(out[0])[0]))
    }

    @Test
    fun `a drawn row number is not a file line number`() {
        // The grid skips the separator, so from that row on the screen is
        // one behind the file. Taken at face value, tapping "Milk" edits
        // the header above it -- which is exactly what happened.
        // Two columns, because one is not a table: a row needs at least
        // two cells, the same `_MIN_CELLS` the desktop uses. The first
        // version of this test used one column and measured nothing.
        val t = table("| Item | Qty |", "| --- | --- |", "| Milk | 2 |")
        assertEquals(1, t.separatorAt)
        assertEquals(0, t.fileRow(0))       // the header row, before it
        assertEquals(2, t.fileRow(1))       // "Milk" -- file line 2, not 1
    }

    @Test
    fun `a table with no separator maps its rows one to one`() {
        val t = table("| a | b |", "| c | d |")
        assertEquals(-1, t.separatorAt)
        assertEquals(1, t.fileRow(1))
    }

    @Test
    fun `editing a cell leaves every OTHER line byte for byte the same`() {
        // Rebuilding the whole table re-pads every line, and the span
        // remap then sees one huge changed region and drops every style
        // inside it. Measured on a real note: one cell edit lost a colour
        // three rows away. So a cell edit must touch only its own line.
        val lines = listOf(
            "| Item      | Qty      |",
            "| Milk      | 2        |",
            "| Bread     | 1        |",
        )
        val out = TableEdit(lines).setCell(1, 1, "3").lines()
        assertEquals(lines[0], out[0])
        assertEquals(lines[2], out[2])
        assertEquals(listOf("Milk", "3"), splitRow(out[1]))
    }

    @Test
    fun `a cell that outgrows its column widens only its own line`() {
        val lines = listOf("| a        | b        |", "| c        | d        |")
        val out = TableEdit(lines).setCell(0, 1, "a much longer piece of text").lines()
        assertEquals("the row below is not touched", lines[1], out[1])
        assertEquals(listOf("a", "a much longer piece of text"), splitRow(out[0]))
    }

    @Test
    fun `a cell edit keeps the alignment its padding carried`() {
        val lines = listOf("|    right |  centre  |")
        val out = TableEdit(lines).setCell(0, 0, "other").lines()
        assertEquals(cellAlignments(lines[0]), cellAlignments(out[0]))
    }

    @Test
    fun `a structural change does rebuild, because it has to`() {
        // Adding a column changes every line by definition; there is no
        // surgical version of that, and pretending otherwise would leave
        // the table ragged.
        val lines = listOf("| a | b |", "| c | d |")
        val out = TableEdit(lines).insertColumnAfter(0).lines()
        assertEquals(3, splitRow(out[0]).size)
        assertEquals(3, splitRow(out[1]).size)
    }

    // ---- putting the table back in the body ----

    @Test
    fun `replacing a table leaves the prose around it alone`() {
        val body = "before\n| a | b |\n| c | d |\nafter"
        val out = replaceTable(body, 1..2, listOf("| X | Y |"))
        assertEquals("before\n| X | Y |\nafter", out)
    }

    @Test
    fun `a table can grow or shrink without disturbing what follows`() {
        val body = "| a |\nafter"
        assertEquals("| a |\n| b |\nafter", replaceTable(body, 0..0, listOf("| a |", "| b |")))
        assertEquals("after", replaceTable("| a |\n| b |\nafter", 0..1, emptyList()))
    }

    @Test
    fun `replacing a block that is not there changes nothing`() {
        val body = "only text"
        assertEquals(body, replaceTable(body, 5..7, listOf("| x |")))
    }

    // ---- an empty or odd table must not throw ----

    @Test
    fun `an empty list of lines parses to an empty table`() {
        val t = parseTable(emptyList())
        assertEquals(0, t.rows.size)
        assertEquals(0, t.columns.size)
    }

    @Test
    fun `a table that is only a separator draws nothing but keeps its columns`() {
        val t = table("| --- | ---: |")
        assertEquals(0, t.rows.size)
        assertEquals(2, t.columns.size)
        assertEquals(CellAlign.RIGHT, t.columns[1].align)
        assertFalse(t.columns[0].hidden)
    }
}
