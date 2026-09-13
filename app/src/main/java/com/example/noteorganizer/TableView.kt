package com.example.noteorganizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val GRID_LINE = Color(0xFF1E1E2F)
private val CELL_BG = Color(0xFF34345A)
private val HEADER_BG = Color(0xFF3C3C68)
private val CELL_TEXT = Color(0xFFF2F2F7)

/** While editing, every cell reads as a target rather than as text. */
private val EDIT_BG = Color(0xFF44446E)

/** One character of the monospaced face at the size used below. */
private val CHAR_WIDTH = 8.2.dp

/**
 * Past this many characters a column stops growing and its text wraps.
 * Without a cap, one cell holding a paragraph makes every row that tall
 * and pushes the rest of the table off any reachable scroll position.
 */
private const val MAX_COLUMN_CHARS = 26

/** Where a tapped cell sits, in the table's own row/column numbering. */
data class CellAt(val row: Int, val column: Int)

@Composable
fun TableView(
    table: TableBlock,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    onCellTap: ((CellAt) -> Unit)? = null,
) {
    if (table.rows.isEmpty() || table.columns.isEmpty()) return

    val widths = table.charWidths.map {
        CHAR_WIDTH * minOf(it, MAX_COLUMN_CHARS).coerceAtLeast(1)
    }

    Column(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .background(GRID_LINE)
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
                        CellBox(null, widths.getOrElse(c) { CHAR_WIDTH }, null, isHeader)
                        c += 1
                        continue
                    }
                    val here = CellAt(r, c)
                    // A merged cell is as wide as the columns it swallows,
                    // plus the grid lines that no longer separate them.
                    val last = minOf(c + cell.colSpan, row.size)
                    var span = 0.dp
                    var counted = 0
                    for (i in c until last) {
                        if (table.columns.getOrNull(i)?.hidden == true) continue
                        span += widths.getOrElse(i) { CHAR_WIDTH }
                        counted += 1
                    }
                    if (counted > 1) span += 1.dp * (counted - 1)
                    CellBox(cell, span, column?.align, isHeader, editing) {
                        onCellTap?.invoke(here)
                    }
                    c = last
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
    onTap: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .width(width + 12.dp)
            .fillMaxHeight()
            // The 1dp gap is not a border: the grid's own background shows
            // through it, which is one drawn rectangle instead of four
            // lines per cell.
            .padding(end = 1.dp, bottom = 1.dp)
            .background(
                when {
                    editing -> EDIT_BG
                    isHeader -> HEADER_BG
                    else -> CELL_BG
                }
            )
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
            color = CELL_TEXT,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (align) {
                CellAlign.CENTER -> TextAlign.Center
                CellAlign.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            },
        )
    }
}
