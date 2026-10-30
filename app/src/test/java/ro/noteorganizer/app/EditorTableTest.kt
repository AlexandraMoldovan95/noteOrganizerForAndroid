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
