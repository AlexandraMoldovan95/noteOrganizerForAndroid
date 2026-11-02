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
 * A table changed from the editor, the styles on its words kept: what the
 * table's bar does to the note, read back from the editor's own state.
 */
class EditorTableTest {

    private fun editor(body: String): EditorModel {
        val dir = NioFiles.createTempDirectory("editortable").toFile()
        val json = org.json.JSONArray().put(org.json.JSONObject()
            .put("id", 1).put("uid", "t").put("title", "T").put("body", body).put("category", "General"))
        File(dir, "notes_data.json").writeText(json.toString())
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        return EditorModel(state, state.note(1))
    }

    private fun EditorModel.styled(span: FormatSpan): String =
        String(codePointsOf(body), span.start, span.end - span.start)

    @Test
    fun `a merge carries the words' styles into the merged cell, and one Undo takes it all back`() {
        val body = "Plan\n| Day | Task |\n| --- | --- |\n| Mon | gym |"
        val model = editor(body)
        val gym = body.indexOf("gym")
        model.spans = listOf(FormatSpan(gym, gym + 3, "bold"))
        model.changed()
        val table = Editing.segments(model.body).first { it.table }
        val kept = model.mergeCells(table, CellAt(1, 1), up = false)
        assertEquals(CellAt(1, 0), kept)
        assertTrue(model.body, model.body.split("\n")[3].replace(" ", "") == "|Mongym|<|")
        assertEquals("the bold is still on its word, inside the merged cell",
                     listOf("gym"), model.spans.map { model.styled(it) })
        model.undo()
        assertEquals(body, model.body)
        assertEquals(listOf(FormatSpan(gym, gym + 3, "bold")), model.spans)
    }

    @Test
    fun `a cell left on a box's or a merge's mark keeps it, as the desktop writes a cell when it is left`() {
        // The field holds "[x]" back while typed -- written, the cell turns
        // into a box under the finger -- and only the next letter wrote it:
        // a cell left there kept "[x" (the final audit's wave 3).
        val body = "Plan\n| Day | Task |\n| --- | --- |\n| Mon | gym |"
        val model = editor(body)
        fun type(at: CellAt, words: String) {
            val segment = Editing.segments(model.body).first { it.table }
            model.typeInCell(segment, parseTable(segment.text.split("\n")), at, words, words.length)
        }
        fun row(): String = model.body.split("\n")[3].replace(" ", "")
        for (words in listOf("", "[", "[x", "[x]")) type(CellAt(1, 1), words)
        assertEquals("held while typed", "|Mon|[x|", row())
        assertEquals(null, model.write())
        assertEquals("written by the save that leaving the note makes", "|Mon|[x]|", row())
        for (words in listOf("", "^")) type(CellAt(1, 0), words)
        model.writeHeldCell()                      // what leaving the cell does
        assertEquals("written when the cell is left", "|^|[x]|", row())
        // Undo after a held mark takes it back with the typing, and nothing
        // writes it again afterwards.
        for (words in listOf("", "<")) type(CellAt(1, 0), words)
        model.undo()
        assertEquals(null, model.cellHeld)
        model.writeHeldCell()
        assertTrue(row(), "<" !in row())
    }

    /** A cell's words written as its field writes them, the cursor at [caret] in the words. */
    private fun EditorModel.typeInCell(fileRow: Int, column: Int, text: String, caret: Int) {
        cellWordsCaret = caret
        editTable(Editing.segments(body).first { it.table }) { it.setCell(fileRow, column, text) }
        cellWordsCaret = null
    }

    @Test
    fun `letters typed in a padded or aligned cell keep its words' styles, as the desktop's cell editor styles them`() {
        // Read off the texts, a cell narrower than its column, or aligned
        // right, cut or dropped the bold on its words (the fourteenth round).
        val body = "Budget\n| Item  |  Cost |\n|-------|------:|\n| Rent  |   500 |\n| Total |  1250 |"
        val model = editor(body)
        val total = body.indexOf("1250")
        model.spans = listOf(FormatSpan(total, total + 4, "bold"))
        model.changed()
        for ((text, caret) in listOf("125" to 3, "12" to 2, "1" to 1, "13" to 2, "130" to 3, "1300" to 4)) {
            model.typeInCell(3, 1, text, caret)
        }
        assertEquals("1300 is bold, right-aligned", listOf("1300"), model.spans.map { model.styled(it) })
        val left = editor("Budget\n| Item  | Cost |\n|-------|------|\n| Rent  | 500  |\n| Total | 1250 |")
        val rent = left.body.indexOf("Rent")
        left.spans = listOf(FormatSpan(rent, rent + 4, "bold"))
        left.changed()
        left.typeInCell(2, 0, "Reent", 2)
        assertEquals("a letter after the bold R is bold", listOf("Reent"), left.spans.map { left.styled(it) })
        for ((text, caret) in listOf("MReent" to 1, "MyReent" to 2, "My Reent" to 3)) left.typeInCell(2, 0, text, caret)
        assertEquals("letters at the words' start take the style of the one ahead",
                     listOf("My Reent"), left.spans.map { left.styled(it) })
    }

    @Test
    fun `a table or an answer put in where a styled run starts leaves it as it was, as the desktop's insert`() {
        // Read off the texts, a run starting where the table went in grew
        // over the whole table, and one starting on the line after an
        // answer took the answer (the final audit's twelfth round).
        val model = editor("Trip\nImportant: book flights")
        model.spans = listOf(FormatSpan(5, 14, "highlight", "#ffff00"))
        model.changed()
        model.caret = 5
        assertTrue(model.insertTable(2, 2) != null)
        assertEquals("only Important is highlighted", listOf("Important"), model.spans.map { model.styled(it) })
        for ((name, op) in listOf<Pair<String, (EditorModel) -> Unit>>(
            "a caption" to { m -> m.addCaption(m.table()) },
            "a line above" to { m -> m.addLineAbove(m.table()) },
            "the table taken out" to { m -> m.deleteTable(m.table()) })) {
            op(model)
            assertEquals("$name keeps it on its word", listOf("Important"), model.spans.map { model.styled(it) })
        }
        val calc = editor("Budget: \nRent 500")
        calc.spans = listOf(FormatSpan(9, 13, "highlight", "#ffff00"))
        calc.changed()
        calc.caret = 8
        calc.insertCalculation("2+2=4")
        calc.insertCalculation("3*3=9")
        assertTrue(calc.body, calc.body.contains("3*3=9\nRent"))
        assertEquals("only Rent is highlighted", listOf("Rent"), calc.spans.map { calc.styled(it) })
    }

    @Test
    fun `a caption under a highlighted table takes no highlight, its Source line's break included`() {
        // The desktop clears to the line after "Source: "; the phone left its
        // line break highlighted, a bar to the margin there (the thirteenth round).
        val model = editor("Budget\n| Item | Cost |\n| Rent | 500 |\nNext line")
        model.spans = listOf(FormatSpan(7, codePointsOf(model.body).size, "highlight", "#ffff00"))
        model.changed()
        model.addCaption(model.table())
        val lines = model.body.split("\n")
        val source = lines.indexOfFirst { it.startsWith("Source: ") }
        val breakAt = lines.take(source + 1).sumOf { it.codePointCount(0, it.length) + 1 } - 1
        assertTrue("no highlight on the Source line or its break: ${model.spans}",
                   model.spans.none { it.start <= breakAt && it.end > breakAt - "Source: ".length })
        assertTrue("Next is still highlighted", model.spans.any { model.styled(it).contains("Next") })
    }

    @Test
    fun `a column made wider on a table with no separator keeps a cell's colour on its cell, through a row added`() {
        // The phone's own tables have no separator; Wider puts one under the
        // header. Read off the texts it was found two letters into the next
        // row, that row's first cell's colour spread over it, and the next row
        // added dropped the colour (the final audit's thirteenth round).
        val model = editor("Budget\n| Item | Cost |\n| Rent | 500 |")
        model.highlightCell(model.table(), 1, 0, "#ffff00")
        val before = model.styled(model.spans.single())
        model.editColumns(model.table()) { setColumnWidth(it, 0, 10) }
        assertEquals("the colour where it was", before, model.styled(model.spans.single()))
        model.editTable(model.table()) { it.insertRowAfter(0) }
        assertTrue("the colour still on Rent's cell: ${model.spans}",
                   model.spans.any { it.style == "highlight" && model.styled(it).contains("Rent") })
    }

    @Test
    fun `the calculator's answer goes into the cell being written in, at its cursor`() {
        // The desktop's _calc_insert_formula puts it in the open cell; the
        // phone put it on a line under the table.
        val body = "Costs\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |"
        val model = editor(body)
        val table = Editing.segments(model.body).first { it.table }
        model.activeCell = ActiveCell(table.firstLine, CellAt(1, 1))
        model.cellCaret = 0
        model.insertCalculation("100+20=")
        val row = model.body.split("\n")[3].split("|").map { it.trim() }
        assertEquals("100+20=120", row[2])
        assertEquals("nothing under the table", 4, model.body.split("\n").size)
        assertEquals("the cursor after what went in", 7, model.cellCaretNext)
        // With no cell, the answer goes on a line of its own, as before.
        model.activeCell = null
        model.insertCalculation("1+1=2")
        assertTrue(model.body, model.body.contains("1+1=2"))
    }

    /** A table whose middle cell is filled yellow pipe to pipe and whose "gym" is bold. */
    private fun styledTable(): EditorModel {
        val body = "| Day | Task | Time |\n| --- | --- | --- |\n| Mon | gym | 7am |\n| Tue | run | 8am |"
        val model = editor(body)
        val (from, to) = Exporting.cellRange(body, 2, 1)!!
        val gym = body.indexOf("gym")
        model.spans = listOf(FormatSpan(from, to, "highlight", "#ffff00"), FormatSpan(gym, gym + 3, "bold"))
        model.changed()
        return model
    }

    private fun EditorModel.table() = Editing.segments(body).first { it.table }

    /** The highlight's cell words, and the bold's words, read back from the body. */
    private fun EditorModel.marks(): Pair<String, String> {
        val fill = spans.single { it.style == "highlight" }
        val bold = spans.single { it.style == "bold" }
        return styled(fill).trim() to styled(bold)
    }

    @Test
    fun `a column added keeps a cell's fill and its words' bold, as the desktop's _grid_lay_cell_styles`() {
        // Re-padded and remapped as one change, both were dropped.
        val model = styledTable()
        model.editTable(model.table()) { it.insertColumnAfter(0) }
        assertEquals(splitRow(model.body.split("\n")[2]), listOf("Mon", "", "gym", "7am"))
        assertEquals("gym" to "gym", model.marks())
        val fill = model.spans.single { it.style == "highlight" }
        assertEquals("pipe to pipe still", Exporting.cellRange(model.body, 2, 2), fill.start to fill.end)
    }

    @Test
    fun `a row deleted, and a cell aligned, keep the other cells' styles`() {
        val model = styledTable()
        model.editTable(model.table()) { it.removeRow(3) }
        assertEquals("gym" to "gym", model.marks())
        model.editTable(model.table()) { it.setAlignment(2, 1, CellAlign.RIGHT) }
        assertEquals("the aligned cell's own words keep their bold", "gym" to "gym", model.marks())
    }

    @Test
    fun `Undo and Redo of words typed in a cell leave the cursor in the cell`() {
        // Placed by the rule for prose, the place of the change was in no
        // field: the line under the grid took the cursor, the cell let go,
        // and the next letter after Undo was written under the table.
        val body = "Costs\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |\nafter"
        val model = editor(body)
        val cell = ActiveCell(model.table().firstLine, CellAt(1, 0))
        model.activeCell = cell
        model.editTable(model.table(), Editing.History.TYPING + ":cell") { it.setCell(2, 0, "fuel and oil") }
        val epoch = model.cellEpoch
        model.undo()
        assertEquals(body, model.body)
        assertEquals("still the cell written in", cell, model.activeCell)
        assertEquals("no line of prose asked for the cursor", null, model.focus)
        assertTrue("the cell's field reads its words again", model.cellEpoch > epoch)
        model.redo()
        assertEquals("fuel and oil", splitRow(model.body.split("\n")[3])[0])
        assertEquals(cell, model.activeCell)
        assertEquals(null, model.focus)
        // No cell being written in: the cursor goes by the rule for prose, as before.
        val plain = editor(body)
        plain.editTable(plain.table()) { it.setCell(2, 0, "fuel and oil") }
        plain.undo()
        assertEquals("the line under the grid", Editing.segments(plain.body).indexOfFirst { it.field && it.firstLine == 4 },
                     plain.focus)
        // A cell written in, and Undo of words typed outside its table: the
        // cursor goes where they were, as Tk puts it, and the cell lets go.
        val prose = editor(body)
        val after = Editing.segments(prose.body).indexOfFirst { it.field && it.firstLine == 4 }
        prose.typeInto(Editing.segments(prose.body)[after], TextFieldValue("after all", TextRange(9)), after)
        prose.activeCell = cell
        prose.focus = null
        prose.undo()
        assertEquals(body, prose.body)
        assertEquals("the words' own line", after, prose.focus)
    }

    @Test
    fun `after Undo of a cell's words, the calculator writes where the person was writing once the cell is let go`() {
        // Undo in a cell keeps the cell (d6b5850), and emptied the places
        // remembered in the text: once the cell was let go, the calculator's
        // answer went to the note's end (the final audit's eighth round).
        val body = "Costs\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |\nafter"
        val model = editor(body)
        model.caret = 5
        model.activeCell = ActiveCell(model.table().firstLine, CellAt(1, 0))
        model.editTable(model.table(), Editing.History.TYPING + ":cell") { it.setCell(2, 0, "fuel and oil") }
        model.undo()
        assertEquals(body, model.body)
        assertEquals("the place written at, carried through the Undo", 5, model.caret)
        model.activeCell = null
        model.insertCalculation("1+1=2")
        assertEquals("Costs1+1=2\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |\nafter", model.body)
    }

    @Test
    fun `words typed in a styled cell keep the style where it was`() {
        val model = styledTable()
        model.editTable(model.table(), Editing.History.TYPING + ":cell") { it.setCell(2, 1, "gyms") }
        assertEquals(listOf("Mon", "gyms", "7am"), splitRow(model.body.split("\n")[2]))
        assertEquals("gyms" to "gym", model.marks())
    }

    @Test
    fun `a caption centred from the format bar takes its table along, as the desktop's _set_paragraph_align`() {
        val body = "| Day | Task |\n| --- | --- |\n| Mon | gym |\nTable 1: Week\nSource: me\nafter"
        val model = editor(body)
        model.alignLines(3, 3, "center")
        assertEquals((0..4).associateWith { "center" }, model.props.mapValues { it.value.align })
        // A line that is no caption moves alone.
        model.alignLines(5, 5, "right")
        assertEquals("right", model.props[5]?.align)
        assertEquals("center", model.props[0]?.align)
    }

    @Test
    fun `a merge leaves the styles of the cells around it where they were`() {
        // Written one cell at a time, each remap sees one small change: a
        // style between the two cells of a merge down is not in it.
        val body = "| A | B |\n| --- | --- |\n| Mon | far |\n| gym | x |"
        val model = editor(body)
        val far = body.indexOf("far")
        model.spans = listOf(FormatSpan(far, far + 3, "italic"))
        model.changed()
        val table = Editing.segments(model.body).first { it.table }
        model.mergeCells(table, CellAt(2, 0), up = true)
        assertEquals(listOf("far"), model.spans.map { model.styled(it) })
        assertTrue(model.body, model.body.split("\n")[2].contains("Mon gym"))
    }
}
