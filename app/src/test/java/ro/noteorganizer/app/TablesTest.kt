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
    fun `a column is drawn at least eight characters wide, as the desktop's MIN_COLUMN floor, up to the cap`() {
        // An empty column of a new table, a short word, a checkbox: all at
        // the floor, where an empty cell had been a sliver no finger found.
        val t = table("|  |  | Mon |", "| --- | --- | --- |", "| [x] |  | " + "w".repeat(40) + " |")
        assertEquals(8, t.drawnChars(0))
        assertEquals(8, t.drawnChars(1))
        assertEquals(26, t.drawnChars(2))
        // A width set by hand is drawn exactly, narrower than the floor too.
        val set = table("| a | b |", "| ---- | ------------ |")
        assertEquals(4, set.drawnChars(0))
        assertEquals(12, set.drawnChars(1))
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
    fun `a column is removed from three, and emptied at two, as the desktop does`() {
        val three = TableEdit(listOf("| a | b | c |", "| --- | --- | --- |", "| d | e | f |")).removeColumn(0).lines()
        assertEquals(listOf("b", "c"), splitRow(three[0]))
        assertTrue(isSeparatorRow(three[1]))
        assertEquals(listOf("e", "f"), splitRow(three[2]))
        // One column reads as prose, not as a table: at two, the column is
        // emptied and the table stays one.
        val two = TableEdit(listOf("| a | b |", "| --- | --- |", "| c | d |")).removeColumn(0).lines()
        assertEquals(listOf("", "b"), splitRow(two[0]))
        assertTrue("the separator keeps its dashes", isSeparatorRow(two[1]))
        assertEquals(listOf("", "d"), splitRow(two[2]))
        val single = TableEdit(listOf("| a |")).removeColumn(0).lines()
        assertEquals(1, splitRow(single[0]).size)
    }

    @Test
    fun `the header row deleted, the next row is the header and the dashes stay under it`() {
        val out = TableEdit(listOf("| A | B |", "| --- | --- |", "| 1 | 2 |", "| 3 | 4 |")).removeRow(0).lines()
        assertEquals(listOf("1", "2"), splitRow(out[0]))
        assertTrue("the separator is under the first row", isSeparatorRow(out[1]))
        assertEquals(listOf("3", "4"), splitRow(out[2]))
        // The separator is no row to delete, and the last data row stays.
        val sep = listOf("| A | B |", "| --- | --- |", "| 1 | 2 |")
        assertEquals(sep, TableEdit(sep).removeRow(1).lines())
        val last = listOf("| A | B |", "| --- | --- |")
        assertEquals(last, TableEdit(last).removeRow(0).lines())
    }

    @Test
    fun `a merge marker left joining nothing is emptied, as the desktop's _dissolve_orphan_markers`() {
        val out = TableEdit(listOf("| A | B | C |", "| --- | --- | --- |", "| 1 | < | x |")).removeColumn(0).lines()
        assertEquals(listOf("", "x"), splitRow(out[2]))
        // A row deleted the same way: the header gone, the "^" under it is first, joining nothing.
        val up = TableEdit(listOf("| A | B |", "| --- | --- |", "| ^ | 2 |", "| 3 | 4 |")).removeRow(0).lines()
        assertEquals(listOf("", "2"), splitRow(up[0]))
        // A marker that still joins something is kept.
        val kept = TableEdit(listOf("| A | B |", "| --- | --- |", "| 1 | 2 |", "| ^ | 3 |", "| 5 | 6 |")).removeRow(4).lines()
        assertEquals(listOf("^", "3"), splitRow(kept[3]))
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

    // ---- the cell menu ----

    private val grid = listOf("| A | B | C |", "| --- | --- | --- |", "| a | b | c |", "| d | e | f |")

    @Test
    fun `merging writes the desktop's marker into the cell`() {
        val left = TableEdit(grid).setMarker(2, 1, "<").lines()
        assertEquals(listOf("a", "<", "c"), splitRow(left[2]))
        assertEquals("the merge is read back as one wide cell", 2, parseTable(left).rows[1][0]!!.colSpan)
        val up = TableEdit(grid).setMarker(3, 0, "^").lines()
        assertEquals("^", splitRow(up[3])[0])
        assertEquals("the separator is never a cell", grid[1], TableEdit(grid).setMarker(1, 0, "<").lines()[1])
    }

    /** A merge's writes applied as the editor applies them, one cell at a time. */
    private fun applied(lines: List<String>, plan: MergePlan): List<String> =
        plan.writes.fold(lines) { now, w -> TableEdit(now).setCell(parseTable(now).fileRow(w.at.row), w.at.column, w.text).lines() }

    @Test
    fun `a merge keeps the words of both cells, joined in reading order, as the desktop's _grid_merge_selection`() {
        val plan = mergePlan(grid, CellAt(1, 1), up = false)!!
        assertEquals(listOf(CellWrite(CellAt(1, 0), "a b"), CellWrite(CellAt(1, 1), "<")), plan.writes)
        assertEquals(listOf(CellAt(1, 0), CellAt(1, 1)), plan.pieces)
        val merged = parseTable(applied(grid, plan))
        assertEquals(listOf("a b"), merged.rows[1][0]!!.lines)
        assertEquals(2, merged.rows[1][0]!!.colSpan)
        // Above, too: the upper cell's words first.
        val up = mergePlan(grid, CellAt(2, 1), up = true)!!
        assertEquals(listOf(CellWrite(CellAt(1, 1), "b e"), CellWrite(CellAt(2, 1), "^")), up.writes)
    }

    @Test
    fun `a cell merged with an empty one keeps its words, whichever of the two is kept`() {
        // The owner's case on her phone: words merged with the empty cell on
        // their left came out as an empty cell.
        val lines = listOf("| A | B |", "| --- | --- |", "|  | gym |")
        val plan = mergePlan(lines, CellAt(1, 1), up = false)!!
        assertEquals(CellWrite(CellAt(1, 0), "gym"), plan.writes.first())
        assertEquals(listOf("gym", "<"), splitRow(applied(lines, plan)[2]))
        assertNull("no neighbour on the left of the first column", mergePlan(lines, CellAt(1, 0), up = false))
        assertNull("none above the first row", mergePlan(lines, CellAt(0, 1), up = true))
    }

    @Test
    fun `a merge grows to take a merge it touches whole, and the box is written the desktop's way`() {
        // "a" already spans two columns; "d" merged up into it takes the whole box.
        val wide = TableEdit(grid).setMarker(2, 1, "<").lines()
        val plan = mergePlan(wide, CellAt(2, 0), up = true)!!
        assertEquals(CellWrite(CellAt(1, 0), "a d e"), plan.writes.first())
        assertEquals(setOf(CellWrite(CellAt(1, 1), "<"), CellWrite(CellAt(2, 0), "^"), CellWrite(CellAt(2, 1), "<")),
                     plan.writes.drop(1).toSet())
        val merged = parseTable(applied(wide, plan))
        assertEquals(2, merged.rows[1][0]!!.colSpan)
        assertEquals("the row under it, swallowed into it whole",
                     mapOf(CellAt(2, 0) to CellAt(1, 0), CellAt(2, 1) to CellAt(1, 0)),
                     merged.swallowedBy.filterKeys { it.row == 2 })
        assertEquals(listOf("c"), merged.rows[1][2]!!.lines)
    }

    @Test
    fun `a row made taller pads its tallest cell with empty lines, as the desktop's _grid_set_row_lines`() {
        val lines = listOf("| A | B |", "| --- | --- |", "| Mon<br>early | gym |")
        assertEquals(listOf(CellWrite(CellAt(1, 0), "Mon<br>early<br>")), rowLinesPlan(lines, 1, 3))
        val tall = applied(lines, MergePlan(rowLinesPlan(lines, 1, 3), emptyList()))
        assertEquals(listOf("Mon", "early", ""), parseTable(tall).rows[1][0]!!.lines)
        // Already that tall: nothing to write.
        assertEquals(emptyList<CellWrite>(), rowLinesPlan(lines, 1, 2))
    }

    @Test
    fun `a row made shorter trims empty lines only, never the words a cell holds`() {
        val lines = listOf("| A | B |", "| --- | --- |", "| Mon<br><br> | gym<br> |")
        assertEquals(setOf(CellWrite(CellAt(1, 0), "Mon"), CellWrite(CellAt(1, 1), "gym")), rowLinesPlan(lines, 1, 1).toSet())
        val words = listOf("| A | B |", "| --- | --- |", "| Mon<br>early | gym |")
        assertEquals("two lines of words stay two lines", emptyList<CellWrite>(), rowLinesPlan(words, 1, 1))
    }

    @Test
    fun `a cell merged down over several rows is left alone by a row's height`() {
        val lines = listOf("| A | B |", "| --- | --- |", "| Mon | gym |", "| ^ | run |")
        assertEquals(listOf(CellWrite(CellAt(1, 1), "gym<br>")), rowLinesPlan(lines, 1, 2))
    }

    @Test
    fun `the separator is read in the first two lines only, as the desktop's separator_index`() {
        // Lower down, a row of dashes is a row of the table, drawn and numbered.
        val t = table("| A | B |", "| x | y |", "| --- | --- |", "| c | d |")
        assertEquals(-1, t.separatorAt)
        assertEquals(4, t.rows.size)
        assertEquals(listOf("---"), t.rows[2][0]!!.lines)
        // A merge on the last row writes the last row, numbered as it was read.
        val lines = listOf("| A | B |", "| x | y |", "| --- | --- |", "| c | d |")
        val plan = mergePlan(lines, CellAt(3, 1), up = false)!!
        assertEquals(listOf("| --- | --- |", "c d", "<"),
                     applied(lines, plan).let { listOf(it[2], splitRow(it[3])[0], splitRow(it[3])[1]) })
    }

    @Test
    fun `a row added under the header goes under the separator, as the desktop's _grid_insert_row`() {
        val out = TableEdit(grid).insertRowAfter(0).lines()
        assertTrue("the separator is still the second line", isSeparatorRow(out[1]))
        assertEquals(listOf("", "", ""), splitRow(out[2]))
        assertEquals(listOf("a", "b", "c"), splitRow(out[3]))
    }

    @Test
    fun `a ragged table's separator grows by dashes, so it stays a separator`() {
        // One row typed past the rest: any edit squares the table up.
        val lines = listOf("| A | B |", "| --- | --- |", "| a | b | c |")
        val out = TableEdit(lines).insertRowAfter(2).lines()
        assertTrue(out[1], isSeparatorRow(out[1]))
        assertEquals(3, splitRow(out[1]).size)
    }

    @Test
    fun `a right-aligned or centred cell typed full keeps its alignment`() {
        val lines = listOf("| Item | Cost |", "| --- | --- |", "| fuel |    1 |")
        assertEquals(CellAlign.RIGHT, cellAlignments(lines[2])[1])
        val full = TableEdit(lines).setCell(2, 1, "12345").lines()
        assertEquals(CellAlign.RIGHT, cellAlignments(full[2])[1])
        val centred = TableEdit(lines).setAlignment(2, 1, CellAlign.CENTER).lines()
        val typed = TableEdit(centred).setCell(2, 1, "1234567").lines()
        assertEquals(CellAlign.CENTER, cellAlignments(typed[2])[1])
    }

    @Test
    fun `wider is never narrower than what the column holds, though the phone draws it capped`() {
        val long = "w".repeat(40)
        val t = table("| A | B |", "| --- | --- |", "| $long | b |")
        assertEquals(26, t.drawnChars(0))
        assertEquals("the whole column, not 30 cutting its own words", 40, t.widerChars(0))
        assertEquals("a short column: four more than drawn", 12, t.widerChars(1))
        assertEquals("a set width: four more than set", 16, table("| A |", "| ------------ |", "| a |").widerChars(0))
    }

    @Test
    fun `a merge's marker or a box is mechanics, any other words are words`() {
        for (token in listOf("<", "^", "[ ]", "[x]", " [X] ")) assertTrue(token, isCellToken(token))
        for (words in listOf("<5 €", "^up", "[ ] milk", "", "x")) assertFalse(words, isCellToken(words))
    }

    @Test
    fun `the first row is the header with or without a separator, as the desktop draws it`() {
        assertEquals(1, table("| Day | Task |", "| Mon | gym |").headerRows)
        assertEquals(1, table("| Day | Task |", "| --- | --- |", "| Mon | gym |").headerRows)
    }

    @Test
    fun `widths are counted in characters, an emoji one, as the desktop counts them`() {
        val out = formatTable(listOf(listOf("😀", "b"), listOf("abcdefghij", "c")),
                              listOf(listOf(CellAlign.LEFT, CellAlign.LEFT), listOf(CellAlign.LEFT, CellAlign.LEFT)))
        assertEquals("both rows the same characters wide",
                     out[0].codePointCount(0, out[0].length), out[1].codePointCount(0, out[1].length))
    }

    @Test
    fun `a table whose padding would pass the ceiling is left ragged, its cells and alignment kept`() {
        // A cell of 5,000 characters over 50 rows: lined up, 250,000 spaces.
        val rows = listOf(listOf("x".repeat(5_000), "right")) + List(49) { listOf("a", "b") }
        val aligns = List(50) { listOf(CellAlign.LEFT, CellAlign.RIGHT) }
        val out = formatTable(rows, aligns)
        assertTrue("${out.sumOf { it.length }} characters", out.sumOf { it.length } < 6_000)
        assertEquals("| a |  b |", out[1])
        assertEquals(CellAlign.RIGHT, cellAlignments(out[1])[1])
        assertEquals(rows.map { r -> r }, out.map { splitRow(it) })
        // Under the ceiling, every row as wide as the widest, as before.
        val small = formatTable(List(3) { listOf("abc", "d") }, List(3) { listOf(CellAlign.LEFT, CellAlign.LEFT) })
        assertEquals(1, small.map { it.length }.toSet().size)
    }

    @Test
    fun `a merge down from under the header joins the header's cell, not the separator's dashes`() {
        // Read over the whole grid, the "^" took the dashes for the cell above it.
        val t = table("| Day | Task |", "| --- | --- |", "| ^ | gym |")
        assertEquals(CellAt(0, 0), t.swallowedBy[CellAt(1, 0)])
        assertNull(t.rows[1][0])
    }

    @Test
    fun `splitting empties every cell the merge swallowed, the anchor keeps its text`() {
        val merged = TableEdit(grid).setMarker(2, 1, "<").lines().let { TableEdit(it).setMarker(2, 2, "<").lines() }
        assertTrue(TableEdit(merged).isMerged(2, 0))
        assertFalse(TableEdit(merged).isMerged(3, 0))
        val split = TableEdit(merged).split(2, 2).lines()
        assertEquals("split from a swallowed cell, too", listOf("a", "", ""), splitRow(split[2]))
        assertFalse(TableEdit(split).isMerged(2, 0))
        // Another merge in the same table is left alone.
        val two = TableEdit(merged).setMarker(3, 1, "<").lines()
        val one = TableEdit(two).split(2, 0).lines()
        assertEquals(listOf("d", "<", "f"), splitRow(one[3]))
        assertTrue(TableEdit(one).isMerged(3, 0))
    }

    @Test
    fun `a cell's alignment is its padding, and the drawn cell reads it back`() {
        val centred = TableEdit(grid).setAlignment(2, 1, CellAlign.CENTER).lines()
        assertEquals(CellAlign.CENTER, cellAlignments(centred[2])[1])
        assertEquals(CellAlign.CENTER, parseTable(centred).rows[1][1]!!.align)
        assertEquals("the others stay left", CellAlign.LEFT, parseTable(centred).rows[1][0]!!.align)
        val marker = TableEdit(grid).setMarker(2, 1, "<").lines()
        assertEquals("a marker's padding says nothing", cellAlignments(marker[2]),
            cellAlignments(TableEdit(marker).setAlignment(2, 1, CellAlign.RIGHT).lines()[2]))
    }

    @Test
    fun `a row above the header becomes the header, the separator staying under the first row`() {
        val lines = TableEdit(grid).insertRowBefore(0).lines()
        assertEquals(5, lines.size)
        assertTrue("still the separator", isSeparatorRow(lines[1]))
        assertEquals(listOf("", "", ""), splitRow(lines[0]))
        assertEquals("the old header is the first data row", listOf("A", "B", "C"), splitRow(lines[2]))
        val below = TableEdit(grid).insertRowBefore(2).lines()
        assertEquals("above a data row, simply above it", listOf("", "", ""), splitRow(below[2]))
        assertTrue(isSeparatorRow(below[1]))
    }

    @Test
    fun `nothing changed is no change to the table's lines`() {
        val untidy = listOf("| a |b|", "|c| d |")
        assertEquals(untidy, TableEdit(untidy).setMarker(5, 0, "<").lines())
        assertEquals(listOf("| a | b |"), TableEdit(listOf("| a | b |")).removeRow(0).lines())
    }

    @Test
    fun `a table deleted takes its lines and the break after them`() {
        assertEquals("before\nafter", Editing.deleteLines("before\n| a | b |\n| c | d |\nafter", 1, 2))
        assertEquals("closing the note, the line before keeps its break", "before\n",
            Editing.deleteLines("before\n| a | b |", 1, 1))
        assertEquals("", Editing.deleteLines("| a | b |", 0, 0))
    }

    @Test
    fun `a cell's highlight lies pipe to pipe, and colours the cell as the export does`() {
        val body = "intro\n" + grid.joinToString("\n")
        val (from, to) = Exporting.cellRange(body, 3, 1)!!
        assertEquals(" b ", body.substring(from, to))
        val spans = setStyle(emptyList(), from, to, "highlight", "#fff3b0")
        assertEquals(mapOf((2 to 1) to "#fff3b0"), Exporting.cellHighlights(body, spans, 1, 4))
        val page = Exporting.noteToHtml(body, org.json.JSONArray().put(org.json.JSONObject()
            .put("start", from).put("end", to).put("style", "highlight").put("value", "#fff3b0")))
        // The desktop's own page for this note, byte for byte.
        assertTrue("the export fills the same cell",
            "<td style=\"background-color:#fff3b0;\"><span style=\"background-color:#fff3b0;\">b</span></td>" in page)
        val words = "| milk | bread |"
        val (m0, _) = Exporting.cellRange(words, 0, 0)!!
        assertEquals("a highlight on part of a cell does not fill it", emptyMap<Pair<Int, Int>, String>(),
            Exporting.cellHighlights(words, setStyle(emptyList(), m0 + 1, m0 + 3, "highlight", "#fff3b0"), 0, 0))
        assertNull("no such cell", Exporting.cellRange(body, 3, 5))
        // An empty cell's highlight rides its padding, and still fills it.
        val empty = "| a |   |"
        val (pad0, pad1) = Exporting.cellRange(empty, 0, 1)!!
        assertEquals(mapOf((0 to 1) to "#cdeac0"),
            Exporting.cellHighlights(empty, setStyle(emptyList(), pad0, pad1, "highlight", "#cdeac0"), 0, 0))
    }

    // ---- column widths ----

    @Test
    fun `a separator says each column's width, alignment and hidden mark, as the desktop reads it`() {
        // The desktop's parse_separator over the same lines.
        assertEquals(listOf(TableColumn(null, false, null), TableColumn(CellAlign.CENTER, false, 5),
                            TableColumn(null, true, null), TableColumn(CellAlign.RIGHT, false, 11)),
                     parseSeparator("| --- | :---: | ~ | ----------: |"))
        assertEquals(listOf(TableColumn(CellAlign.LEFT, false, null), TableColumn(null, false, null)),
                     parseSeparator("| :- | -- |"))
        // ...and its make_separator for these columns.
        assertEquals(listOf("---", ":------:", "~", "-----:", ":----"), separatorCells(listOf(
            TableColumn(null, false, null), TableColumn(CellAlign.CENTER, false, 8), TableColumn(null, true),
            TableColumn(CellAlign.RIGHT, false, 6), TableColumn(CellAlign.LEFT, false, 5))))
    }

    @Test
    fun `a column's width changes the separator line alone, and a table without one gets one under its header`() {
        val table = listOf("| Item     | Cost     |", "| -------- | -------: |", "| fuel     |      120 |")
        assertEquals(listOf(table[0], "| ------------ | -------: |", table[2]), setColumnWidth(table, 0, 12))
        assertEquals("three or fewer reads back as no width, so four at least",
                     "| ---- | -------: |", setColumnWidth(table, 0, 2)[1])
        val hidden = hideColumn(table, 1)
        assertEquals(listOf(table[0], "| -------- | ~ |", table[2]), hidden)
        assertEquals("a hidden column comes back with no width of its own",
                     "| -------- | --- |", unhideAllColumns(hidden)[1])
        assertEquals("a width set shows the column again", "| -------- | ------ |", setColumnWidth(hidden, 1, 6)[1])
        val bare = listOf("| Item | Cost |", "| fuel | 120 |")
        assertEquals(listOf(bare[0], "| -------- | -------- |", bare[1]), autofitColumn(bare, 0))
        assertEquals(listOf(bare[0], "| ---------- | -------- |", bare[1]), setColumnWidth(bare, 0, 10))
        val long = listOf("| Item | Cost |", "| --- | --- |", "| a much longer item | 1 |")
        assertEquals("autofit measures the data, never the separator",
                     "| ------------------ | --- |", autofitColumn(long, 0)[1])
        val widened = listOf("| A | B |", "| -------------- | --- |", "| x | y |")
        assertEquals("a column widened by hand fits back to its words",
                     "| -------- | --- |", autofitColumn(widened, 0)[1])
    }

    @Test
    fun `autofit all fits every shown column in one pass and leaves a hidden one hidden`() {
        val table = listOf("| Item | Cost | Note |", "| -------------- | ~ | --- |", "| a much longer item | 1 | x |")
        assertEquals(listOf(table[0], "| ------------------ | ~ | -------- |", table[2]), autofitAllColumns(table))
        val bare = listOf("| Item | Cost |", "| fuel | 120 |")
        assertEquals("a table without a separator gets one, each column fitted",
                     listOf(bare[0], "| -------- | -------- |", bare[1]), autofitAllColumns(bare))
        assertEquals("fitting all is fitting each, column by column",
                     autofitColumn(autofitColumn(table, 0), 2), autofitAllColumns(table))
    }
}
