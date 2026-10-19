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

/**
 * A column's mechanics, as its separator cell spells them: the colons'
 * alignment, the hidden marker, and a [width] -- the cell's dashes and
 * colons counted, when there are more than three. Three or fewer is
 * markdown's way of writing any separator at all, not a size anyone set.
 */
data class TableColumn(val align: CellAlign?, val hidden: Boolean, val width: Int? = null)

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
    /**
     * How many rows are drawn as the header: the first, always, as the
     * desktop's `_build_table_grid` draws it and the export writes it. Read
     * as the rows before a separator, a new table -- which has none -- had
     * no header until a column's width was set, and then suddenly one.
     */
    val headerRows: Int,
    /**
     * Which FILE line the separator was, or -1. The drawn grid skips it,
     * so a row number taken off the screen is one short of the file's from
     * that point on -- which silently edits the row above the one tapped.
     */
    val separatorAt: Int = -1,
    /** Every cell a merge swallowed, and the cell it was merged into: drawn rows and columns. */
    val swallowedBy: Map<CellAt, CellAt> = emptyMap(),
) {
    /** The line in the file that a drawn row came from. */
    fun fileRow(displayedRow: Int): Int =
        if (separatorAt >= 0 && displayedRow >= separatorAt) displayedRow + 1
        else displayedRow

    /**
     * A column's width as drawn, in characters. A width set by hand is the
     * width exactly, as the desktop draws it. Otherwise the column takes
     * what it holds, but never fewer than `MIN_COLUMN` -- the desktop's
     * floor, which the phone once skipped, so a new table's empty cells
     * were slivers a finger could not find -- and never more than
     * [MAX_COLUMN_CHARS], past which its words wrap.
     */
    fun drawnChars(column: Int): Int =
        columns.getOrNull(column)?.width ?: charWidths.getOrElse(column) { 0 }.coerceIn(MIN_COLUMN, MAX_COLUMN_CHARS)

    /**
     * More's "wider": four characters more than drawn, and never fewer than
     * all the column holds. A column past the cap is drawn at the cap and
     * wraps; four more than that wrote a width under its own words, which
     * the desktop, drawing it whole until then, began to cut short.
     */
    fun widerChars(column: Int): Int {
        val now = drawnChars(column)
        return if (columns.getOrNull(column)?.width != null) now + 4
               else maxOf(now + 4, charWidths.getOrElse(column) { 0 })
    }
}

/**
 * Past this many characters a column stops growing and its text wraps.
 * Without a cap, one cell holding a paragraph makes every row that tall
 * and pushes the rest of the table off any reachable scroll position.
 */
private const val MAX_COLUMN_CHARS = 26

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
            // Characters, as the desktop's len counts them: an emoji is one, not two.
            var need = cell.codePointCount(0, cell.length)
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
        // In characters, as Python's len counts them: an emoji is one.
        val extra = maxOf(0, width - cell.codePointCount(0, cell.length))
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
 * Where a table's separator is, or -1: the desktop's `separator_index`,
 * which looks at the first two lines only. A row of dashes lower down is a
 * row of the table, as the desktop reads it; read as the separator
 * wherever it was, the phone hid that row, took its dashes for widths, and
 * numbered the rows one way while a merge wrote them another.
 */
fun separatorIndexOf(lines: List<String>): Int = lines.take(2).indexOfFirst { isSeparatorRow(it) }

/** A cell's whole text that reads as mechanics, not words: a merge's marker, or a box. */
fun isCellToken(text: String): Boolean {
    val t = text.trim()
    return t == MERGE_LEFT_MARK || t == MERGE_UP_MARK || checkboxState(t) != null
}

/** One cell in a body, in code points: pipe to pipe, and its words without their padding. */
data class CellPlace(val start: Int, val end: Int, val wordsStart: Int, val wordsEnd: Int)

/**
 * Every cell of the table on body lines [lines], keyed by its row among
 * those lines -- the separator counted, as [TableEdit] counts rows -- and
 * its column.
 */
fun cellPlaces(body: String, lines: IntRange): Map<Pair<Int, Int>, CellPlace> {
    val all = body.split("\n")
    val out = HashMap<Pair<Int, Int>, CellPlace>()
    var offset = 0
    for ((n, line) in all.withIndex()) {
        val points = codePointsOf(line)
        if (n in lines && isTableRow(line)) {
            val pipes = points.indices.filter { points[it] == '|'.code }
            for (k in 0 until pipes.size - 1) {
                val start = pipes[k] + 1
                val end = pipes[k + 1]
                var from = start
                while (from < end && Character.isWhitespace(points[from])) from++
                var to = end
                while (to > from && Character.isWhitespace(points[to - 1])) to--
                out[(n - lines.first) to k] = CellPlace(offset + start, offset + end, offset + from, offset + to)
            }
        }
        offset += points.size + 1
    }
    return out
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
    private val separatorAt = separatorIndexOf(lines)

    /**
     * Where each cell came from -- its row and column in the lines given --
     * or null for a cell the edit made. Moved with the cells, so a cell's
     * styles can go where its words went ([origins]).
     */
    private val from: MutableList<MutableList<Pair<Int, Int>?>> =
        rows.indices.map { r -> MutableList<Pair<Int, Int>?>(rows[r].size) { c -> r to c } }.toMutableList()

    /** [from] for the lines [lines] returns: each written cell's origin, row by row. */
    fun origins(): List<List<Pair<Int, Int>?>> = from

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

    /**
     * Every row as wide as the widest. The separator grows by "---" cells,
     * as the desktop's `_grid_write_separator` pads it: grown by empty ones,
     * a table with one row typed past the rest wrote a separator with an
     * empty cell, which is no separator, and its dashes became a row.
     */
    private fun pad() {
        val width = columnCount
        for (r in rows.indices) {
            while (rows[r].size < width) rows[r].add(if (r == separatorAt) "---" else "")
            while (aligns[r].size < width) aligns[r].add(CellAlign.LEFT)
            while (from[r].size < width) from[r].add(null)
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

    /**
     * A new row under [row]. Under the header, it goes under the separator,
     * as the desktop's `_grid_insert_row` counts data rows: put straight
     * under the header, it pushed the separator to the third line, where
     * the desktop no longer reads one.
     */
    fun insertRowAfter(row: Int): TableEdit {
        surgical = null
        pad()
        var at = (row + 1).coerceIn(0, rows.size)
        if (separatorAt >= 0 && at == separatorAt) at += 1
        val width = columnCount
        rows.add(at, MutableList(width) { "" })
        aligns.add(at, MutableList(width) { CellAlign.LEFT })
        from.add(at, MutableList(width) { null })
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
            from.add(separatorAt, from.removeAt(separatorAt + 1))
        }
        return this
    }

    /**
     * A row taken away -- the desktop's `_grid_delete_row`, which counts
     * data rows only. The separator is never one; the last data row is
     * never taken: an empty table is not a table, and its lines would stop
     * reading as one. The separator stays under whichever row is first
     * now -- deleting the header row left the dashes on top, and the table
     * stopped being one -- and a merge marker left with nothing to join
     * is emptied, as the desktop's `_dissolve_orphan_markers` empties it.
     */
    fun removeRow(row: Int): TableEdit {
        surgical = null
        if (row !in rows.indices || row == separatorAt) return this
        if (rows.indices.count { it != separatorAt } <= 1) return this
        rows.removeAt(row)
        aligns.removeAt(row)
        from.removeAt(row)
        var separator = when {
            separatorAt < 0 -> -1
            row < separatorAt -> separatorAt - 1
            else -> separatorAt
        }
        if (separator == 0 && rows.size > 1) {
            rows.add(1, rows.removeAt(0))
            aligns.add(1, aligns.removeAt(0))
            from.add(1, from.removeAt(0))
            separator = 1
        }
        dissolveOrphans(separator)
        touched = true
        return this
    }

    /** `_dissolve_orphan_markers`: a "<" or "^" that joins nothing becomes an empty cell. */
    private fun dissolveOrphans(separator: Int) {
        val data = rows.indices.filter { it != separator }
        val covered = mergeAnchors(data.map { rows[it] }).keys
        for ((i, r) in data.withIndex()) {
            for (c in rows[r].indices) {
                val value = rows[r][c]
                if ((value == MERGE_LEFT_MARK || value == MERGE_UP_MARK) && (i to c) !in covered) rows[r][c] = ""
            }
        }
    }

    fun insertColumnAfter(column: Int): TableEdit {
        surgical = null
        pad()
        val at = (column + 1).coerceIn(0, columnCount)
        for (r in rows.indices) {
            // A new column in the separator row is mechanics, not content.
            rows[r].add(at, if (r == separatorAt) "---" else "")
            aligns[r].add(at, CellAlign.LEFT)
            from[r].add(at, null)
        }
        touched = true
        return this
    }

    /**
     * `_grid_set_marker`: the cell becomes a merge marker -- "<" joins it to
     * the cell on its left, "^" to the one above -- which is how a merge is
     * written down. A marker with nothing to join stays plain text. The
     * marker alone, the cell's words gone: the app merges through
     * [mergePlan], which keeps them.
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

    /**
     * A column taken away -- the desktop's `_grid_delete_col`. At two
     * columns it is emptied instead: one column reads as prose rather than
     * a table, so a table of two, a column deleted on the phone, stopped
     * being a table. A merge marker left with nothing to join is emptied.
     */
    fun removeColumn(column: Int): TableEdit {
        surgical = null
        pad()
        if (column !in 0 until columnCount) return this
        if (columnCount <= 2) {
            for (r in rows.indices) {
                if (r != separatorAt && column < rows[r].size) rows[r][column] = ""
            }
        } else {
            for (r in rows.indices) {
                if (column < rows[r].size) rows[r].removeAt(column)
                if (column < aligns[r].size) aligns[r].removeAt(column)
                if (column < from[r].size) from[r].removeAt(column)
            }
        }
        dissolveOrphans(separatorAt)
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
        val text = rows[row][column]
        val align = aligns.getOrNull(row)?.getOrNull(column) ?: CellAlign.LEFT
        // Room for the padding that says the alignment too, as the desktop's
        // `layout_widths` counts it: a right-aligned cell typed full had no
        // space left before its words, and read back as aligned left.
        // Counted in characters, as the rest of the table is laid out.
        val chars = text.codePointCount(0, text.length)
        val width = maxOf(line.codePointCount(start, end) - 2, chars + paddingNeed(align))
        val extra = maxOf(0, width - chars)
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

/** Where a tapped cell sits, in the table's own row/column numbering: data rows, the separator left out. */
data class CellAt(val row: Int, val column: Int)

/** A cell to write, by its place in the drawn grid -- data rows, the separator left out. */
data class CellWrite(val at: CellAt, val text: String)

/**
 * A merge worked out: [writes] in order, the kept cell and its joined words
 * first; [pieces] the cells whose words were joined, in the order they were.
 */
data class MergePlan(val writes: List<CellWrite>, val pieces: List<CellAt>) {
    val anchor: CellAt get() = writes.first().at
}

/**
 * A cell merged with its neighbour -- on its left, or above -- the
 * desktop's `_grid_merge_selection` over the two. The box they make, grown
 * until every merge it touches lies inside it whole, becomes one cell kept
 * at its top left. Nothing it swallows is thrown away: each cell's words
 * move into the kept cell, joined in reading order by a space, as the
 * desktop joins them, and an empty cell adds nothing. The phone's first
 * merge made the chosen cell a marker instead, and its words went -- the
 * words of a cell merged with an empty one among them.
 *
 * Every other cell of the box becomes a marker: "<" along the box's first
 * row and in every column but its first, "^" down its first column. Null
 * when there is no neighbour there.
 */
fun mergePlan(lines: List<String>, at: CellAt, up: Boolean): MergePlan? {
    val separator = separatorIndexOf(lines)
    val rows = lines.filterIndexed { i, _ -> i != separator }.map { splitRow(it) }
    val other = if (up) CellAt(at.row - 1, at.column) else CellAt(at.row, at.column - 1)
    if (other.row < 0 || other.column < 0 || at.row !in rows.indices) return null
    // Each merge as the box it covers: its kept cell and everything it swallows.
    val merges = HashMap<Pair<Int, Int>, IntArray>()
    for ((cell, anchor) in mergeAnchors(rows)) {
        val box = merges.getOrPut(anchor) { intArrayOf(anchor.first, anchor.second, anchor.first, anchor.second) }
        box[0] = minOf(box[0], cell.first)
        box[1] = minOf(box[1], cell.second)
        box[2] = maxOf(box[2], cell.first)
        box[3] = maxOf(box[3], cell.second)
    }
    var r0 = minOf(at.row, other.row)
    var c0 = minOf(at.column, other.column)
    var r1 = maxOf(at.row, other.row)
    var c1 = maxOf(at.column, other.column)
    var grown = true
    while (grown) {
        grown = false
        for (box in merges.values) {
            val touches = box[0] <= r1 && box[2] >= r0 && box[1] <= c1 && box[3] >= c0
            val inside = box[0] >= r0 && box[2] <= r1 && box[1] >= c0 && box[3] <= c1
            if (touches && !inside) {
                r0 = minOf(r0, box[0]); c0 = minOf(c0, box[1])
                r1 = maxOf(r1, box[2]); c1 = maxOf(c1, box[3])
                grown = true
            }
        }
    }
    val pieces = ArrayList<CellAt>()
    val words = ArrayList<String>()
    for (r in r0..r1) {
        for (c in c0..c1) {
            val value = rows.getOrNull(r)?.getOrNull(c) ?: continue
            if (value.isEmpty() || value == MERGE_LEFT_MARK || value == MERGE_UP_MARK) continue
            pieces.add(CellAt(r, c))
            words.add(value)
        }
    }
    val writes = arrayListOf(CellWrite(CellAt(r0, c0), words.joinToString(" ")))
    for (r in r0..r1) {
        for (c in c0..c1) {
            if (r == r0 && c == c0) continue
            writes.add(CellWrite(CellAt(r, c), if (r == r0 || c != c0) MERGE_LEFT_MARK else MERGE_UP_MARK))
        }
    }
    return MergePlan(writes, pieces)
}

/**
 * A row made [want] lines tall, the desktop's `_grid_set_row_lines`: the
 * row's tallest cell padded with empty `<br>` lines, or every cell's
 * trailing empty lines trimmed -- never below the words a cell holds, so a
 * drag never cuts the table's text. A cell merged down over several rows
 * is left alone, as the desktop leaves it. Only the cells that change, by
 * their place in the drawn grid.
 */
fun rowLinesPlan(lines: List<String>, row: Int, want: Int): List<CellWrite> {
    val separator = separatorIndexOf(lines)
    val rows = lines.filterIndexed { i, _ -> i != separator }.map { splitRow(it) }
    val cells = rows.getOrNull(row) ?: return emptyList()
    val anchors = mergeAnchors(rows)
    val tall = anchors.filter { (cell, anchor) -> cell.first != anchor.first }.values.toSet()
    val mine = cells.indices.filter { c -> (row to c) !in anchors && (row to c) !in tall }
    if (mine.isEmpty()) return emptyList()
    fun core(value: String): List<String> {
        val parts = value.split(BREAK_MARK).toMutableList()
        while (parts.size > 1 && parts.last().isBlank()) parts.removeAt(parts.size - 1)
        return parts
    }
    val target = maxOf(1, want, mine.maxOf { core(cells[it]).size })
    val tallest = mine.maxBy { cells[it].split(BREAK_MARK).size }
    return mine.mapNotNull { c ->
        val kept = core(cells[c])
        val size = if (c == tallest) target else kept.size
        val value = (kept + List(size - kept.size) { "" }).joinToString(BREAK_MARK)
        if (value == cells[c]) null else CellWrite(CellAt(row, c), value)
    }
}

/** The column mechanics a separator row spells out: the desktop's `parse_separator`. */
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
    TableColumn(align, hidden = false, width = cell.length.takeIf { it > 3 })
}

/**
 * Separator cells for [columns]: the desktop's `make_separator`. The width
 * is kept exactly -- the dash count IS the width that was set -- so writing
 * a separator again never forgets one.
 */
fun separatorCells(columns: List<TableColumn>): List<String> = columns.map { column ->
    if (column.hidden) return@map SEPARATOR_HIDDEN_MARK
    val width = maxOf(3, column.width ?: 3)
    when (column.align) {
        CellAlign.CENTER -> ":" + "-".repeat(maxOf(1, width - 2)) + ":"
        CellAlign.RIGHT -> "-".repeat(maxOf(1, width - 1)) + ":"
        CellAlign.LEFT -> ":" + "-".repeat(maxOf(1, width - 1))
        null -> "-".repeat(width)
    }
}

/**
 * A column's width, hidden flag or fit changed by rewriting the separator
 * line alone -- or putting one under the first row when the table has
 * none, each column at its laid-out width. No data row is re-padded, so
 * no style in the table moves: the desktop's `_grid_column_mechanics`,
 * which learnt that a column drag through a full re-format took every
 * cell's colour with it.
 */
fun withColumnMechanics(lines: List<String>, change: (MutableList<TableColumn>, List<List<String>>, List<List<CellAlign>>) -> Unit): List<String> {
    if (lines.isEmpty()) return lines
    val at = separatorIndexOf(lines)
    val data = lines.filterIndexed { i, _ -> i != at }
    val rows = data.map { splitRow(it) }
    val aligns = data.map { cellAlignments(it) }
    val columns = if (at >= 0) parseSeparator(lines[at]).toMutableList()
                  else layoutWidths(rows, aligns).map { TableColumn(null, false, it) }.toMutableList()
    change(columns, rows, aligns)
    val count = rows.maxOfOrNull { it.size } ?: 0
    while (columns.size < count) columns.add(TableColumn(null, false))
    val separator = "|" + separatorCells(columns).joinToString("|") { " $it " } + "|"
    return if (at >= 0) lines.toMutableList().also { it[at] = separator }
           else listOf(lines[0], separator) + lines.drop(1)
}

/** A column set to [width] characters, shown if it was hidden; four at least -- three reads back as no width. */
fun setColumnWidth(lines: List<String>, column: Int, width: Int): List<String> = withColumnMechanics(lines) { cols, _, _ ->
    if (column in cols.indices) cols[column] = cols[column].copy(width = maxOf(4, width), hidden = false)
}

/** A column fitted to what it holds: the desktop's "Autofit column width". */
fun autofitColumn(lines: List<String>, column: Int): List<String> = withColumnMechanics(lines) { cols, rows, aligns ->
    val widths = layoutWidths(rows, aligns)
    if (column in cols.indices && column in widths.indices) cols[column] = cols[column].copy(width = widths[column], hidden = false)
}

/**
 * Every column fitted at once, in one pass: the desktop's
 * `_grid_autofit_cols` over the whole table (it fits the columns a
 * selection touches; a finger has no selection to drag). A hidden column
 * stays hidden: a selection never holds one, and "fit them all" is not a
 * request to bring one back.
 */
fun autofitAllColumns(lines: List<String>): List<String> = withColumnMechanics(lines) { cols, rows, aligns ->
    val widths = layoutWidths(rows, aligns)
    for (c in cols.indices) if (c in widths.indices && !cols[c].hidden) cols[c] = cols[c].copy(width = widths[c])
}

/** A column hidden, or shown again. */
fun hideColumn(lines: List<String>, column: Int, hidden: Boolean = true): List<String> = withColumnMechanics(lines) { cols, _, _ ->
    if (column in cols.indices) cols[column] = cols[column].copy(hidden = hidden)
}

/** Every hidden column shown: "Unhide all columns". */
fun unhideAllColumns(lines: List<String>): List<String> = withColumnMechanics(lines) { cols, _, _ ->
    for (i in cols.indices) cols[i] = cols[i].copy(hidden = false)
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

    val separatorAt = separatorIndexOf(lines)
    val columns = if (separatorAt >= 0) {
        val parsed = parseSeparator(lines[separatorAt])
        List(width) { c -> parsed.getOrElse(c) { TableColumn(null, false) } }
    } else {
        List(width) { TableColumn(null, false) }
    }

    // Merges are read over the data rows alone, as the desktop's `merge_map`
    // reads them: over the whole grid, a "^" under the header joined the
    // separator's dashes rather than the header's cell.
    val dataLines = grid.indices.filter { it != separatorAt }
    val anchors = mergeAnchors(dataLines.map { grid[it] })
    // Anchor -> how many columns it swallows to its right.
    val colSpans = HashMap<Pair<Int, Int>, Int>()
    for ((covered, anchor) in anchors) {
        if (covered.first != anchor.first) continue      // a row merge, not a column one
        val span = covered.second - anchor.second + 1
        colSpans[anchor] = maxOf(colSpans[anchor] ?: 1, span)
    }

    val rows = ArrayList<List<TableCell?>>()
    for ((d, r) in dataLines.withIndex()) {
        val row = ArrayList<TableCell?>(width)
        val padding = cellAlignments(lines[r])
        for (c in 0 until width) {
            if (anchors.containsKey(d to c)) {
                row.add(null)                            // swallowed by an anchor
                continue
            }
            val value = grid[r][c]
            row.add(
                TableCell(
                    lines = value.split(BREAK_MARK),
                    checked = checkboxState(value),
                    colSpan = colSpans[d to c] ?: 1,
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
        headerRows = if (rows.isEmpty()) 0 else 1,
        separatorAt = separatorAt,
        swallowedBy = anchors.entries.associate { (cell, anchor) ->
            CellAt(cell.first, cell.second) to CellAt(anchor.first, anchor.second)
        },
    )
}
