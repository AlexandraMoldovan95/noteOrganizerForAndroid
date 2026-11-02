package ro.noteorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette

/*
 * A table, drawn as a grid.
 *
 * **Monospaced on purpose.** Column widths come out of the parser counted
 * in CHARACTERS, exactly as the desktop counts them, and a fixed-advance
 * font turns that count into a width with no measuring pass and no
 * guessing. It also happens to be the right look for a table of figures.
 *
 * A wide table scrolls sideways rather than squeezing: a real notebook has
 * tables twenty columns across, and twenty columns crushed into a phone's
 * width is not a table, it is a smear.
 */

/** The size the cells' text is drawn at -- in sp, so it grows with the phone's text size. */
private val CELL_TEXT = 13.sp

/**
 * One character of the cells' face, MEASURED at the size it is drawn.
 * It was a number in dp, fixed: right while the phone's text size was
 * 100%, and short of the letters at 115%, which grow in sp while dp do
 * not -- every cell then wrapped its last letter onto a second line
 * ("g / g"), found on a real phone. Measured, the width and the letters
 * cannot disagree. Bold too, for the header row, and the wider of the two.
 */
@Composable
private fun charWidth(): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        val sample = "0".repeat(20)
        val widest = listOf(FontWeight.Normal, FontWeight.Bold).maxOf { weight ->
            measurer.measure(sample, TextStyle(fontSize = CELL_TEXT, fontFamily = FontFamily.Monospace,
                                               fontWeight = weight), softWrap = false).size.width
        }
        with(density) { (widest / 20f).toDp() }
    }
}

/**
 * What a cell adds to its text's width: its padding (12), the grid line
 * inside it (1), and one more so a measured fraction of a pixel never
 * tips the last letter onto a second line.
 */
private val CELL_EXTRA = 14.dp

/** A cell being written in: its table, by the body line the table starts on, and the cell. */
data class ActiveCell(val firstLine: Int, val at: CellAt)

/**
 * [found] are the cells the find bar matched, [current] the one it is on --
 * outlined, as the desktop draws a box on its grid -- and [reveal] counts
 * the requests to scroll that one into sight, sideways as well as down.
 * [place] is where the table sits on the page -- left, centred or right,
 * the alignment of its lines -- and [highlights] the cells a highlight
 * covers whole, in the colour to fill them with. [active] is the cell
 * being written in: outlined, and drawn by [editor] -- the words typed
 * right in the cell, as Samsung Notes writes them -- unless it is a
 * checkbox, which a tap ticks.
 */
@Composable
fun TableView(
    table: TableBlock,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    onCellTap: ((CellAt) -> Unit)? = null,
    found: Set<CellAt> = emptySet(),
    current: CellAt? = null,
    reveal: Int = 0,
    place: String = "left",
    highlights: Map<CellAt, Color> = emptyMap(),
    active: CellAt? = null,
    editor: (@Composable (TextStyle) -> Unit)? = null,
    onResizeColumn: ((column: Int, chars: Int) -> Unit)? = null,
    onResizeRow: ((row: Int, lines: Int) -> Unit)? = null,
) {
    if (table.rows.isEmpty() || table.columns.isEmpty()) return

    // A drag on the chosen cell's edge, as it goes: the column and the
    // pixels dragged; the row, its height when the drag began, and the
    // pixels. Drawn at once, written when the finger lifts.
    val density = LocalDensity.current
    var widthDrag by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var heightDrag by remember { mutableStateOf<Triple<Int, Int, Float>?>(null) }
    val rowHeights = remember { mutableStateMapOf<Int, Int>() }
    val linePx = cellLinePx()

    // A width set by hand -- the separator's dash count, more than three --
    // is the column's width exactly, as the desktop draws it: wider than
    // what it holds, or narrower, its words cut short with "…". A plain
    // "---" sets none, and the column takes what it holds, between the
    // desktop's floor and the cap ([TableBlock.drawnChars]).
    val unit = charWidth()
    val widths = table.charWidths.indices.map { i ->
        val drawn = unit * table.drawnChars(i).coerceAtLeast(1)
        val dragged = widthDrag?.takeIf { it.first == i }?.second ?: 0f
        if (dragged == 0f) drawn else maxOf(unit, drawn + with(density) { dragged.toDp() })
    }

    // The theme's own colours: the grid lines are the hairline showing
    // through, the header row the accent's tint.
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = when (place) {
            "center" -> Alignment.TopCenter
            "right" -> Alignment.TopEnd
            else -> Alignment.TopStart
        },
    ) {
    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .background(LocalPalette.current.hairline)
    ) {
        table.rows.forEachIndexed { r, row ->
            val isHeader = r < table.headerRows
            // Taller as the edge is dragged down; shorter only once written,
            // since a row never draws shorter than what its cells hold.
            val taller = heightDrag?.takeIf { it.first == r }?.let { (_, base, px) -> base + px }
            // A row whose cell is merged down draws over the rows under it,
            // where its words are centred ([drop]); an earlier one over a later.
            val mergesDown = row.indices.any { cc -> row[cc] != null && table.swallowedBy[CellAt(r + 1, cc)] == CellAt(r, cc) }
            Row(modifier = Modifier
                .zIndex(if (mergesDown) (table.rows.size - r).toFloat() else 0f)
                .then(if (taller != null) Modifier.heightIn(min = with(density) { taller.toDp() }) else Modifier)
                .height(IntrinsicSize.Min)
                .onSizeChanged { if (heightDrag?.first != r) rowHeights[r] = it.height }) {
                var c = 0
                while (c < row.size) {
                    val column = table.columns.getOrNull(c)
                    if (column?.hidden == true) {
                        c += 1
                        continue
                    }
                    val cell = row[c]
                    // A merged cell is as wide as the cells it swallows,
                    // each with its padding and its grid line -- the whole
                    // CELL_EXTRA a cell adds to its text. Counting only the
                    // line drew a merged cell short of the columns under it,
                    // the grid showing through at its right.
                    fun spanOf(until: Int): androidx.compose.ui.unit.Dp {
                        var span = 0.dp
                        var counted = 0
                        for (i in c until until) {
                            if (table.columns.getOrNull(i)?.hidden == true) continue
                            span += widths.getOrElse(i) { unit }
                            counted += 1
                        }
                        return if (counted > 1) span + CELL_EXTRA * (counted - 1) else span
                    }
                    if (cell == null) {
                        // Swallowed from above: the words live in the cell
                        // it was merged into. Drawn in that cell's colour,
                        // over the grid's line between them and as wide as
                        // every cell of this row the same merge swallowed,
                        // it reads as one cell with the one above -- drawn
                        // empty and apart, a merge down looked undone. A tap
                        // on it chooses the merged cell.
                        val anchor = table.swallowedBy[CellAt(r, c)]
                        var last = c + 1
                        while (last < row.size && row[last] == null && table.swallowedBy[CellAt(r, last)] == anchor) last++
                        CellBox(null, spanOf(last), null, anchor != null && anchor.row < table.headerRows, editing,
                                fill = anchor?.let { highlights[it] }, joinsAbove = anchor != null) {
                            anchor?.let { onCellTap?.invoke(it) }
                        }
                        c = last
                        continue
                    }
                    val here = CellAt(r, c)
                    val last = minOf(c + cell.colSpan, row.size)
                    val span = spanOf(last)
                    // The cell's own padding says where its words sit, as the
                    // desktop draws them; the separator's colons only travel
                    // with it. Read as well, a ":---:" column drew centred on
                    // the phone, left on the desktop and in the export.
                    val align = cell.align
                    val writing = here == active && cell.checked == null
                    // The chosen cell's edges: its last column's width, its row's height.
                    val edge = last - 1
                    val resizing = here == active && onResizeColumn != null && onResizeRow != null
                    val widthGrip = if (!resizing) null else EdgeDrag(
                        start = { widthDrag = edge to 0f },
                        moved = { px -> widthDrag = edge to ((widthDrag?.second ?: 0f) + px) },
                        done = {
                            val px = widthDrag?.second ?: 0f
                            widthDrag = null
                            val unitPx = with(density) { unit.toPx() }
                            val chars = table.drawnChars(edge) + (px / unitPx).roundToInt()
                            if (chars != table.drawnChars(edge)) onResizeColumn?.invoke(edge, chars.coerceAtLeast(1))
                        },
                    )
                    val heightGrip = if (!resizing) null else EdgeDrag(
                        start = { heightDrag = Triple(r, rowHeights[r] ?: 0, 0f) },
                        moved = { px -> heightDrag = heightDrag?.let { it.copy(third = it.third + px) } },
                        done = {
                            val px = heightDrag?.third ?: 0f
                            heightDrag = null
                            val lines = row.filterNotNull().maxOfOrNull { it.lines.size } ?: 1
                            val want = (lines + (px / linePx).roundToInt()).coerceAtLeast(1)
                            if (want != lines) onResizeRow?.invoke(r, want)
                        },
                    )
                    // Merged down, its words sit in the middle of all the rows it
                    // covers, as the desktop draws one cell over them; they sat in
                    // the first, the rest of the cell left empty (the owner,
                    // 2026-10-26). Written in, the field stays at the top.
                    val below = (r + 1 until table.rows.size).takeWhile { table.swallowedBy[CellAt(it, c)] == here }
                    val drop = with(density) { (below.sumOf { rowHeights[it] ?: 0 } / 2f).toDp() }
                    CellBox(cell, span, align, isHeader, editing,
                            found = here in found, current = here == current || here == active, reveal = reveal,
                            fill = highlights[here], clip = column?.width != null,
                            editor = if (writing) editor else null,
                            drop = if (writing) 0.dp else drop,
                            widthGrip = widthGrip, heightGrip = heightGrip) {
                        onCellTap?.invoke(here)
                    }
                    c = last
                }
            }
        }
    }
    }
}

@Composable
private fun CellBox(
    cell: TableCell?,
    width: androidx.compose.ui.unit.Dp,
    align: CellAlign?,
    isHeader: Boolean,
    editing: Boolean = false,
    found: Boolean = false,
    current: Boolean = false,
    reveal: Int = 0,
    fill: Color? = null,
    clip: Boolean = false,
    editor: (@Composable (TextStyle) -> Unit)? = null,
    joinsAbove: Boolean = false,
    drop: androidx.compose.ui.unit.Dp = 0.dp,
    widthGrip: EdgeDrag? = null,
    heightGrip: EdgeDrag? = null,
    onTap: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val ground = when {
        found -> findAllColour(palette)
        fill != null -> fill
        isHeader -> palette.calendarHasNotes
        else -> palette.card
    }
    val requester = remember { BringIntoViewRequester() }
    if (current) {
        LaunchedEffect(reveal, editor != null) { requester.bringIntoView() }
    }
    // Over the theme's text style, as a Text's own parameters are: built
    // from nothing, it lost the theme's line height, and a bold header row
    // and a plain one each took their own face's -- rows of two heights.
    val style = LocalTextStyle.current.merge(
        color = palette.text,
        fontSize = CELL_TEXT,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = when (align) {
            CellAlign.CENTER -> TextAlign.Center
            CellAlign.RIGHT -> TextAlign.End
            else -> TextAlign.Start
        },
    )
    Box(
        modifier = Modifier
            .width(width + CELL_EXTRA)
            .fillMaxHeight()
            // The 1dp gap is not a border: the grid's own background shows
            // through it, which is one drawn rectangle instead of four
            // lines per cell.
            .padding(end = 1.dp, bottom = 1.dp)
            .bringIntoViewRequester(requester)
            .then(
                // Part of the cell above it: the line between them painted over.
                if (joinsAbove) Modifier.drawBehind {
                    drawRect(ground, topLeft = Offset(0f, -1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                } else Modifier
            )
            .background(ground)
            .then(if (current) Modifier.border(2.dp, palette.accent) else Modifier)
            .then(
                // The cell being written in takes its taps itself: a tap there
                // moves the cursor, it does not choose the cell again.
                if (editing && onTap != null && editor == null) Modifier.clickable { onTap() } else Modifier
            ),
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp),
            contentAlignment = when (align) {
                CellAlign.CENTER -> Alignment.Center
                CellAlign.RIGHT -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            },
        ) {
            if (cell == null) return@Box
            if (editor != null) {
                editor(style)
                return@Box
            }
            Text(
                // A checkbox is a control, so it is drawn as one rather than as
                // the three characters that spell it in the file.
                text = when (cell.checked) {
                    true -> "☑"
                    false -> "☐"
                    null -> cell.lines.joinToString("\n")
                },
                style = style,
                modifier = Modifier.offset(y = drop),
                // A column narrowed by hand cuts its words short rather than
                // growing taller, as the desktop's does.
                softWrap = !clip,
                overflow = if (clip) androidx.compose.ui.text.style.TextOverflow.Ellipsis
                           else androidx.compose.ui.text.style.TextOverflow.Clip,
            )
        }
        // The chosen cell's edges, to drag: its column wider or narrower
        // from the right, its row taller or shorter from below -- the lines
        // a desktop drags, made a finger's size.
        widthGrip?.let { Grip(it, across = false, label = stringResource(R.string.tb_grip_width),
                              modifier = Modifier.align(Alignment.CenterEnd)) }
        heightGrip?.let { Grip(it, across = true, label = stringResource(R.string.tb_grip_height),
                               modifier = Modifier.align(Alignment.BottomCenter)) }
    }
}

/** A drag on one of the chosen cell's edges: [moved] in pixels as the finger goes, [done] when it lifts. */
private class EdgeDrag(val start: () -> Unit, val moved: (Float) -> Unit, val done: () -> Unit)

/**
 * A handle on an edge of the chosen cell: a short bar in the accent, on a
 * touch area a little larger than it. [across] is the bottom edge's, dragged
 * up and down; otherwise the right edge's, dragged sideways. The drag is the
 * handle's own, so the table does not scroll under it, nor the note.
 */
@Composable
private fun Grip(drag: EdgeDrag, across: Boolean, label: String, modifier: Modifier) {
    val palette = LocalPalette.current
    val now by rememberUpdatedState(drag)
    Box(
        modifier
            .size(if (across) 32.dp else 18.dp, if (across) 16.dp else 30.dp)
            .semantics { contentDescription = label }
            .pointerInput(across) {
                if (across) {
                    detectVerticalDragGestures(onDragStart = { now.start() }, onDragEnd = { now.done() },
                                               onDragCancel = { now.done() }) { change, amount ->
                        change.consume()
                        now.moved(amount)
                    }
                } else {
                    detectHorizontalDragGestures(onDragStart = { now.start() }, onDragEnd = { now.done() },
                                                 onDragCancel = { now.done() }) { change, amount ->
                        change.consume()
                        now.moved(amount)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (across) 20.dp else 4.dp, if (across) 4.dp else 18.dp)
            .clip(RoundedCornerShape(2.dp)).background(palette.accent))
    }
}

/** One line of the cells' text, measured: how far a row's edge goes for a line more or less. */
@Composable
private fun cellLinePx(): Float {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(fontSize = CELL_TEXT, fontFamily = FontFamily.Monospace)
    return remember(measurer, style) {
        val two = measurer.measure("0\n0", style).size.height
        val one = measurer.measure("0", style).size.height
        (two - one).toFloat().coerceAtLeast(1f)
    }
}
