package ro.noteorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
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
 * Past this many characters a column stops growing and its text wraps.
 * Without a cap, one cell holding a paragraph makes every row that tall
 * and pushes the rest of the table off any reachable scroll position.
 */
private const val MAX_COLUMN_CHARS = 26

/**
 * What a cell adds to its text's width: its padding (12), the grid line
 * inside it (1), and one more so a measured fraction of a pixel never
 * tips the last letter onto a second line.
 */
private val CELL_EXTRA = 14.dp

/** Where a tapped cell sits, in the table's own row/column numbering. */
data class CellAt(val row: Int, val column: Int)

/**
 * [found] are the cells the find bar matched, [current] the one it is on --
 * outlined, as the desktop draws a box on its grid -- and [reveal] counts
 * the requests to scroll that one into sight, sideways as well as down.
 * [place] is where the table sits on the page -- left, centred or right,
 * the alignment of its lines -- and [highlights] the cells a highlight
 * covers whole, in the colour to fill them with.
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
) {
    if (table.rows.isEmpty() || table.columns.isEmpty()) return

    // A width set by hand -- the separator's dash count, more than three --
    // is the column's width exactly, as the desktop draws it: wider than
    // what it holds, or narrower, its words cut short with "…". A plain
    // "---" sets none, and the column takes what it holds, up to the cap.
    val unit = charWidth()
    val widths = table.charWidths.mapIndexed { i, chars ->
        unit * (table.columns.getOrNull(i)?.width ?: minOf(chars, MAX_COLUMN_CHARS)).coerceAtLeast(1)
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
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                var c = 0
                while (c < row.size) {
                    val column = table.columns.getOrNull(c)
                    if (column?.hidden == true) {
                        c += 1
                        continue
                    }
                    val cell = row[c]
                    if (cell == null) {
                        // Swallowed from above rather than from the left:
                        // the content lives at the anchor, so this position
                        // is drawn empty to keep the columns lined up.
                        CellBox(null, widths.getOrElse(c) { unit }, null, isHeader)
                        c += 1
                        continue
                    }
                    val here = CellAt(r, c)
                    // A merged cell is as wide as the cells it swallows,
                    // each with its padding and its grid line -- the whole
                    // CELL_EXTRA a cell adds to its text. Counting only the
                    // line drew a merged cell short of the columns under it,
                    // the grid showing through at its right.
                    val last = minOf(c + cell.colSpan, row.size)
                    var span = 0.dp
                    var counted = 0
                    for (i in c until last) {
                        if (table.columns.getOrNull(i)?.hidden == true) continue
                        span += widths.getOrElse(i) { unit }
                        counted += 1
                    }
                    if (counted > 1) span += CELL_EXTRA * (counted - 1)
                    // The cell's own padding speaks first, as the desktop
                    // reads it; a column's separator colons after that.
                    val align = cell.align.takeIf { it != CellAlign.LEFT } ?: column?.align
                    CellBox(cell, span, align, isHeader, editing,
                            found = here in found, current = here == current, reveal = reveal,
                            fill = highlights[here], clip = column?.width != null) {
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
    onTap: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val requester = remember { BringIntoViewRequester() }
    if (current) {
        LaunchedEffect(reveal) { requester.bringIntoView() }
    }
    Box(
        modifier = Modifier
            .width(width + CELL_EXTRA)
            .fillMaxHeight()
            // The 1dp gap is not a border: the grid's own background shows
            // through it, which is one drawn rectangle instead of four
            // lines per cell.
            .padding(end = 1.dp, bottom = 1.dp)
            .bringIntoViewRequester(requester)
            .background(when {
                found -> findAllColour(palette)
                fill != null -> fill
                isHeader -> palette.calendarHasNotes
                else -> palette.card
            })
            .then(if (current) Modifier.border(2.dp, palette.accent) else Modifier)
            .then(
                if (editing && onTap != null) Modifier.clickable { onTap() } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = when (align) {
            CellAlign.CENTER -> Alignment.Center
            CellAlign.RIGHT -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        },
    ) {
        if (cell == null) return@Box
        Text(
            // A checkbox is a control, so it is drawn as one rather than as
            // the three characters that spell it in the file.
            text = when (cell.checked) {
                true -> "☑"
                false -> "☐"
                null -> cell.lines.joinToString("\n")
            },
            color = palette.text,
            fontSize = CELL_TEXT,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (align) {
                CellAlign.CENTER -> TextAlign.Center
                CellAlign.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            },
            // A column narrowed by hand cuts its words short rather than
            // growing taller, as the desktop's does.
            softWrap = !clip,
            overflow = if (clip) androidx.compose.ui.text.style.TextOverflow.Ellipsis
                       else androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    }
}
