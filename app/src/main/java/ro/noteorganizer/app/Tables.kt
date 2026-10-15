package ro.noteorganizer.app

/**
 * Turning a run of pipe-delimited lines into something a screen can draw.
 *
 * Nothing here imports Compose, so the rules can be tested on the computer
 * with no phone — the same separation the desktop keeps between `core/`
 * and its interface.
 *
 * What a real notebook actually uses, measured before any of this was
 * written (59 table blocks across 77 notes): checkbox cells 143 times,
 * merge-left 102 times, `<br>` inside a cell 34 times, separator rows 14
 * times — and merge-UP and hidden columns not once. So merge-left is a
 * main case, not a corner one, and the two unused features are handled
 * only well enough not to lose anything.
 */

/** Column alignment, as the separator's colons spell it. */
enum class CellAlign { LEFT, CENTER, RIGHT }

data class TableColumn(val align: CellAlign?, val hidden: Boolean)

/**
 * One drawn cell. [lines] is the text split on the `<br>` marker, which is
 * the line break a table cell draws. [checked] is non-null only when the
 * whole cell is a checkbox, which is a control rather than a word.
 * [align] is what the cell's own padding says, as `cell_alignments` reads it.
 */
data class TableCell(
    val lines: List<String>,
    val checked: Boolean? = null,
    val colSpan: Int = 1,
    val align: CellAlign = CellAlign.LEFT,
)

/**
 * A parsed table. A `null` in [rows] is a position swallowed by a merge —
 * nothing is drawn there, because its content belongs to the anchor.
 */
data class TableBlock(
    val rows: List<List<TableCell?>>,
    val columns: List<TableColumn>,
    /** The widest content in each column, counted in characters. */
    val charWidths: List<Int>,
    /** How many rows come before the separator; 0 when there is none. */
    val headerRows: Int,
    /**
     * Which FILE line the separator was, or -1. The drawn grid skips it,
     * so a row number taken off the screen is one short of the file's from
     * that point on -- which silently edits the row above the one tapped.
     */
    val separatorAt: Int = -1,
) {
    /** The line in the file that a drawn row came from. */
    fun fileRow(displayedRow: Int): Int =
        if (separatorAt >= 0 && displayedRow >= separatorAt) displayedRow + 1
        else displayedRow
}

private const val SEPARATOR_HIDDEN_MARK = "~"
private const val MERGE_LEFT_MARK = "<"
private const val MERGE_UP_MARK = "^"
private const val BREAK_MARK = "<br>"

/**
 * A note's body, cut into the pieces a screen draws differently.
 *
 * [Prose] carries UTF-16 indices into the body rather than the text
 * itself, so the caller can slice the already-styled text with them and
 * keep every span — re-rendering each piece separately would need the
 * formatting offsets recomputed per piece, and that is exactly the sort
 * of second copy of a rule that drifts.
 */
sealed class BodyPart {
    data class Prose(val from: Int, val to: Int) : BodyPart()
    data class Grid(val table: TableBlock) : BodyPart()
}

fun splitBody(body: String): List<BodyPart> {
    val lines = body.split("\n")
    // Where each line starts, in the same UTF-16 indices a rendered
    // AnnotatedString uses.
    val starts = IntArray(lines.size)
    var at = 0
    for (i in lines.indices) {
        starts[i] = at
        at += lines[i].length + 1
    }

    val parts = ArrayList<BodyPart>()
    var line = 0
    for (block in tableBlocks(body)) {
        if (block.first > line) {
            val from = starts[line]
            // Stop before the newline that introduces the table, so the
            // prose does not end with a blank line the table replaces.
            val to = (starts[block.first] - 1).coerceIn(from, body.length)
            if (to > from) parts.add(BodyPart.Prose(from, to))
        }
        parts.add(BodyPart.Grid(parseTable(lines.subList(block.first, block.last + 1))))
        line = block.last + 1
    }
    if (line < lines.size) {
        val from = starts[line]
        if (from < body.length) parts.add(BodyPart.Prose(from, body.length))
    }
    return parts
}

// ---- writing a table back out --------------------------------------
//
// A table is stored as its drawn text: the padding IS the alignment, and
// `cell_alignments` reads it back from the spaces. So rebuilding a table
// after an edit has to re-pad it the way the desktop would, or a right-
// aligned column quietly becomes a left-aligned one on the next read.

/** Never narrower than this, matching `MIN_COLUMN`. */
private const val MIN_COLUMN = 8

/**
 * Per-cell alignment, read off the padding itself.
 *
 * Two or more spaces on both sides is centred; two or more on the left
 * alone is right; anything else, including an empty cell, is left. There
 * is no marker and no field — the layout is the store.
 */
fun cellAlignments(line: String): List<CellAlign> {
    if (!isTableRow(line)) return emptyList()
    val pipes = line.indices.filter { line[it] == '|' }
    val out = ArrayList<CellAlign>()
    for (i in 0 until pipes.size - 1) {
        val segment = line.substring(pipes[i] + 1, pipes[i + 1])
        if (segment.isBlank()) {
            out.add(CellAlign.LEFT)
            continue
        }
        val lead = segment.length - segment.trimStart(' ').length
        val trail = segment.length - segment.trimEnd(' ').length
        out.add(
            when {
                lead >= 2 && trail >= 2 -> CellAlign.CENTER
                lead >= 2 -> CellAlign.RIGHT
                else -> CellAlign.LEFT
            }
        )
    }
    return out
}

/** Extra width a cell needs before its padding can say what it means. */
private fun paddingNeed(align: CellAlign) = when (align) {
    CellAlign.CENTER -> 2
    CellAlign.RIGHT -> 1
    CellAlign.LEFT -> 0
}

fun layoutWidths(rows: List<List<String>>, aligns: List<List<CellAlign>>): List<Int> {
    val widths = ArrayList<Int>()
    for ((r, row) in rows.withIndex()) {
        for ((i, cell) in row.withIndex()) {
            while (i >= widths.size) widths.add(0)
            var need = cell.length
            aligns.getOrNull(r)?.getOrNull(i)?.let { need += paddingNeed(it) }
            widths[i] = maxOf(widths[i], need)
        }
    }
    return widths.map { maxOf(MIN_COLUMN, it) }
}

/** One row, padded so its columns line up -- and so its padding still reads. */
fun formatRow(cells: List<String>, widths: List<Int>, aligns: List<CellAlign>): String {
    val parts = ArrayList<String>(widths.size)
    for ((i, width) in widths.withIndex()) {
        val cell = cells.getOrElse(i) { "" }
        val align = aligns.getOrElse(i) { CellAlign.LEFT }
        val extra = maxOf(0, width - cell.length)
        val lead: Int
        val trail: Int
        when {
            align == CellAlign.CENTER && cell.isNotEmpty() -> {
                lead = extra / 2
                trail = extra - extra / 2
            }
            align == CellAlign.RIGHT && cell.isNotEmpty() -> {
                lead = extra
                trail = 0
            }
            else -> {
                lead = 0
                trail = extra
            }
        }
        parts.add(" " + " ".repeat(lead) + cell + " ".repeat(trail) + " ")
    }
    return "|" + parts.joinToString("|") + "|"
}

fun formatTable(rows: List<List<String>>, aligns: List<List<CellAlign>>): List<String> {
    val widths = layoutWidths(rows, aligns)
    return rows.mapIndexed { r, row ->
        formatRow(row, widths, aligns.getOrElse(r) { emptyList() })
    }
}

/**
 * A table's lines taken apart, edited, and written back out with the
 * alignment each cell carried in its padding.
 *
 * Every edit goes through here so the rules live in one place: the
 * desktop learned that lesson with its search, where two copies drifted.
 */
class TableEdit(private val original: List<String>) {
    private val lines = original
    private val rows = lines.map { splitRow(it).toMutableList() }.toMutableList()
    private val aligns = lines.map { cellAlignments(it).toMutableList() }.toMutableList()
    private val separatorAt = lines.indexOfFirst { isSeparatorRow(it) }

    /**
     * Set when the only change was one cell's text.
     *
     * A cell edit then rewrites JUST that cell's stretch of its own line
     * and leaves every other byte of the table alone. Re-padding the whole
     * table instead changes every line, and the span remap — which finds
     * an edit by what did NOT change — sees one enormous changed region
     * and drops every style inside it. Measured on a real note: editing
     * one cell lost a colour three rows away.
     *
     * It also keeps each cell's alignment exactly, since the padding IS
     * the alignment and untouched padding cannot be misread.
     */
    private var surgical: Pair<Int, Int>? = null

    /**
     * Whether anything changed at all. Nothing did -- a marker asked for on
     * the separator, the last row asked to go -- and the table's lines come
     * back as they were, not re-padded into a change nobody made.
     */
    private var touched = false

    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    private fun pad() {
        val width = columnCount
        for (r in rows.indices) {
            while (rows[r].size < width) rows[r].add("")
            while (aligns[r].size < width) aligns[r].add(CellAlign.LEFT)
        }
    }

    fun setCell(row: Int, column: Int, text: String): TableEdit {
        pad()
        if (row in rows.indices && column in 0 until columnCount) {
            // A cell may not contain a pipe: it would split into two cells
            // on the next read, silently changing the table's shape.
            rows[row][column] = text.replace("|", "/").trim()
            surgical = row to column
            touched = true
        }
        return this
    }

    fun insertRowAfter(row: Int): TableEdit {
        surgical = null
        pad()
        val at = (row + 1).coerceIn(0, rows.size)
        rows.add(at, MutableList(columnCount) { "" })
        aligns.add(at, MutableList(columnCount) { CellAlign.LEFT })
        touched = true
        return this
    }

    /**
     * A new row above [row]. Above the header, it becomes the header, and the
     * separator keeps its place under the first row: pushed down a line, it
     * stopped being read as one -- only the first two rows are looked at --
     * and its dashes turned into a row of the table.
     */
    fun insertRowBefore(row: Int): TableEdit {
        insertRowAfter(row - 1)
        val at = row.coerceIn(0, rows.size - 1)
        if (separatorAt >= 0 && at <= separatorAt) {
            val separator = rows.removeAt(separatorAt + 1)
            val separatorAligns = aligns.removeAt(separatorAt + 1)
            rows.add(separatorAt, separator)
            aligns.add(separatorAt, separatorAligns)
        }
        return this
    }

    fun removeRow(row: Int): TableEdit {
        surgical = null
        // The last content row is never removed: an empty table is not a
        // table, and the lines would stop reading as one at all.
        if (rows.size <= 1 || row !in rows.indices) return this
        rows.removeAt(row)
        aligns.removeAt(row)
        touched = true
        return this
    }

    fun insertColumnAfter(column: Int): TableEdit {
        surgical = null
        pad()
        val at = (column + 1).coerceIn(0, columnCount)
        for (r in rows.indices) {
            // A new column in the separator row is mechanics, not content.
            rows[r].add(at, if (r == separatorAt) "---" else "")
            aligns[r].add(at, CellAlign.LEFT)
        }
        touched = true
        return this
    }

    /**
     * `_grid_set_marker`: the cell becomes a merge marker -- "<" joins it to
     * the cell on its left, "^" to the one above -- which is how a merge is
     * written down. A marker with nothing to join stays plain text.
     */
    fun setMarker(row: Int, column: Int, marker: String): TableEdit {
        pad()
        if (row in rows.indices && row != separatorAt && column in 0 until columnCount) {
            rows[row][column] = marker
            surgical = row to column
            touched = true
        }
        return this
    }

    /** The data rows -- the separator is mechanics, not a row -- as file rows. */
    private fun dataRows(): List<Int> = rows.indices.filter { it != separatorAt }

    /**
     * `_grid_split`: the merge that covers this cell undone -- every cell it
     * swallowed emptied of its marker, the anchor keeping its text.
     */
    fun split(row: Int, column: Int): TableEdit {
        surgical = null
        pad()
        val data = dataRows()
        val at = data.indexOf(row)
        if (at < 0) return this
        val anchors = mergeAnchors(data.map { rows[it] })
        val anchor = anchors[at to column] ?: (at to column)
        for ((covered, owner) in anchors) {
            if (owner == anchor) {
                rows[data[covered.first]][covered.second] = ""
                touched = true
            }
        }
        return this
    }

    /** Whether this cell is the anchor of a merge -- something to split. */
    fun isMerged(row: Int, column: Int): Boolean {
        val data = dataRows()
        val at = data.indexOf(row)
        if (at < 0) return false
        return mergeAnchors(data.map { rows[it] }).values.any { it == (at to column) }
    }

    /**
     * `_grid_set_alignment` for one cell: its padding says left, centre or
     * right. A merge marker's padding says nothing, and is left alone.
     */
    fun setAlignment(row: Int, column: Int, align: CellAlign): TableEdit {
        surgical = null
        pad()
        if (row in rows.indices && row != separatorAt && column in 0 until columnCount &&
            rows[row][column] != MERGE_LEFT_MARK && rows[row][column] != MERGE_UP_MARK) {
            aligns[row][column] = align
            touched = true
        }
        return this
    }

    fun removeColumn(column: Int): TableEdit {
        surgical = null
        pad()
        if (columnCount <= 1 || column !in 0 until columnCount) return this
        for (r in rows.indices) {
            if (column < rows[r].size) rows[r].removeAt(column)
            if (column < aligns[r].size) aligns[r].removeAt(column)
        }
        touched = true
        return this
    }

    fun lines(): List<String> {
        if (!touched) return original
        surgical?.let { (row, column) ->
            val rewritten = rewriteOneCell(row, column)
            if (rewritten != null) return rewritten
        }
        pad()
        return formatTable(rows, aligns)
    }

    /**
     * The cheap path: replace one cell's stretch of its own line and leave
     * every other character of the table exactly as it was.
     *
     * The cell keeps at least the width it had, so the columns below it do
     * not jump; it grows only if the new text needs more room. Returns
     * null when the line cannot be read as a row, and the caller falls
     * back to rebuilding.
     */
    private fun rewriteOneCell(row: Int, column: Int): List<String>? {
        val line = original.getOrNull(row) ?: return null
        val pipes = line.indices.filter { line[it] == '|' }
        if (column + 1 >= pipes.size) return null

        val start = pipes[column] + 1
        val end = pipes[column + 1]
        val width = maxOf(end - start - 2, 0)
        val text = rows[row][column]
        val align = aligns.getOrNull(row)?.getOrNull(column) ?: CellAlign.LEFT
        val extra = maxOf(0, width - text.length)
        val lead: Int
        val trail: Int
        when {
            align == CellAlign.CENTER && text.isNotEmpty() -> {
                lead = extra / 2
                trail = extra - extra / 2
            }
            align == CellAlign.RIGHT && text.isNotEmpty() -> {
                lead = extra
                trail = 0
            }
            else -> {
                lead = 0
                trail = extra
            }
        }
        val segment = " " + " ".repeat(lead) + text + " ".repeat(trail) + " "
        val out = original.toMutableList()
        out[row] = line.substring(0, start) + segment + line.substring(end)
        return out
    }
}

/**
 * Replaces one table block inside a body and returns the new body.
 *
 * The caller still has to move the formatting spans afterwards: a table's
 * lines change length, and every span after them slides.
 */
fun replaceTable(body: String, block: IntRange, newLines: List<String>): String {
    val lines = body.split("\n").toMutableList()
    if (block.first !in lines.indices) return body
    val last = minOf(block.last, lines.size - 1)
    val rebuilt = ArrayList<String>(lines.size)
    rebuilt.addAll(lines.subList(0, block.first))
    rebuilt.addAll(newLines)
    rebuilt.addAll(lines.subList(last + 1, lines.size))
    return rebuilt.joinToString("\n")
}

/** Every run of consecutive table rows in the body, as line ranges. */
fun tableBlocks(body: String): List<IntRange> {
    val lines = body.split("\n")
    val blocks = ArrayList<IntRange>()
    var start = -1
    for (i in lines.indices) {
        if (isTableRow(lines[i])) {
            if (start < 0) start = i
        } else if (start >= 0) {
            blocks.add(start..(i - 1))
            start = -1
        }
    }
    if (start >= 0) blocks.add(start..(lines.size - 1))
    return blocks
}

/**
 * Resolves merge markers into spans, the way `merge_map` does.
 *
 * A marker with nothing to attach to — `<` in the first column, `^` in
 * the first row — stays ordinary content rather than being guessed at.
 *
 * Returns the anchor each covered position belongs to.
 */
fun mergeAnchors(rows: List<List<String>>): Map<Pair<Int, Int>, Pair<Int, Int>> {
    val anchorOf = LinkedHashMap<Pair<Int, Int>, Pair<Int, Int>>()
    for (r in rows.indices) {
        for (c in rows[r].indices) {
            val value = rows[r][c]
            if (value == MERGE_LEFT_MARK && c > 0) {
                val left = anchorOf[r to (c - 1)] ?: (r to (c - 1))
                val at = rows.getOrNull(left.first)?.getOrNull(left.second)
                if (at != MERGE_LEFT_MARK && at != MERGE_UP_MARK) anchorOf[r to c] = left
            } else if (value == MERGE_UP_MARK && r > 0) {
                val above = anchorOf[(r - 1) to c] ?: ((r - 1) to c)
                val at = rows.getOrNull(above.first)?.getOrNull(above.second)
                if (at != MERGE_LEFT_MARK && at != MERGE_UP_MARK) anchorOf[r to c] = above
            }
        }
    }
    return anchorOf
}

/** The column mechanics a separator row spells out. */
fun parseSeparator(line: String): List<TableColumn> = splitRow(line).map { cell ->
    if (cell == SEPARATOR_HIDDEN_MARK) return@map TableColumn(null, hidden = true)
    val left = cell.startsWith(":")
    val right = cell.endsWith(":")
    val align = when {
        left && right -> CellAlign.CENTER
        right -> CellAlign.RIGHT
        left -> CellAlign.LEFT
        else -> null
    }
    TableColumn(align, hidden = false)
}

/**
 * Parses one block of table lines into a grid.
 *
 * Short rows are padded so every row has the same number of columns: a
 * ragged table is a table someone is still typing, and drawing it ragged
 * would move the columns under the reader's eyes.
 */
fun parseTable(lines: List<String>): TableBlock {
    val raw = lines.map { splitRow(it) }
    val width = raw.maxOfOrNull { it.size } ?: 0
    val grid = raw.map { row -> List(width) { c -> row.getOrElse(c) { "" } } }

    val separatorAt = lines.indexOfFirst { isSeparatorRow(it) }
    val columns = if (separatorAt >= 0) {
        val parsed = parseSeparator(lines[separatorAt])
        List(width) { c -> parsed.getOrElse(c) { TableColumn(null, false) } }
    } else {
        List(width) { TableColumn(null, false) }
    }

    val anchors = mergeAnchors(grid)
    // Anchor -> how many columns it swallows to its right.
    val colSpans = HashMap<Pair<Int, Int>, Int>()
    for ((covered, anchor) in anchors) {
        if (covered.first != anchor.first) continue      // a row merge, not a column one
        val span = covered.second - anchor.second + 1
        colSpans[anchor] = maxOf(colSpans[anchor] ?: 1, span)
    }

    val rows = ArrayList<List<TableCell?>>()
    for (r in grid.indices) {
        if (r == separatorAt) continue                   // mechanics, not content
        val row = ArrayList<TableCell?>(width)
        val padding = cellAlignments(lines[r])
        for (c in 0 until width) {
            if (anchors.containsKey(r to c)) {
                row.add(null)                            // swallowed by an anchor
                continue
            }
            val value = grid[r][c]
            row.add(
                TableCell(
                    lines = value.split(BREAK_MARK),
                    checked = checkboxState(value),
                    colSpan = colSpans[r to c] ?: 1,
                    align = padding.getOrElse(c) { CellAlign.LEFT },
                )
            )
        }
        rows.add(row)
    }

    // Column widths, in characters. A cell spanning several columns is
    // left out: letting it set one column's width would stretch a column
    // to fit text that is not even in it. Such a cell wraps inside the
    // width it gets, which is taller but never clipped.
    val charWidths = MutableList(width) { 1 }
    for (row in rows) {
        for (c in 0 until width) {
            val cell = row.getOrNull(c) ?: continue
            if (cell.colSpan > 1) continue
            val longest = if (cell.checked != null) 1
                          else cell.lines.maxOfOrNull { codePointsOf(it).size } ?: 0
            if (longest > charWidths[c]) charWidths[c] = longest
        }
    }

    return TableBlock(
        rows = rows,
        columns = columns,
        charWidths = charWidths,
        headerRows = if (separatorAt > 0) separatorAt else 0,
        separatorAt = separatorAt,
    )
}
