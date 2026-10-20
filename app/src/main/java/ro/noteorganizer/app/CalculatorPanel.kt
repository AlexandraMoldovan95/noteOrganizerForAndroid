package ro.noteorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette

/**
 * The calculator's panel while a note is open: whether it shows, the pad,
 * and its two folds. One per open note -- another note starts with an empty
 * display and no history, as the desktop resets its panel on a note switch.
 * Closing the panel keeps all of it, as collapsing the desktop's does.
 */
class CalculatorState {
    var open by mutableStateOf(false)
    var pad by mutableStateOf(Calculator.Pad())
    var more by mutableStateOf(false)
    var history by mutableStateOf(false)

    /** The display has the cursor: the keyboard is up for it, not the note. */
    var displayFocused by mutableStateOf(false)

    /** The last thing put in the note, said under Insert until the next key. */
    var inserted by mutableStateOf<String?>(null)

    fun press(key: String) {
        pad = pad.press(key)
        inserted = null
    }
}

/** The desktop's keypad, row by row: the four operators and "=" in the accent. */
private val KEYS = listOf(
    listOf("(", ")", "C", "⌫"),
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "−"),
    listOf("0", ".", "=", "+"),
)
private val MORE_KEYS = listOf(listOf("√", "^", "%", "{"), listOf("}", "[", "]", ""))
private val ACCENT_KEYS = setOf("÷", "×", "−", "+", "=")

/**
 * Docked under the note, where the format bar sits, so the note stays in
 * view above it and an inserted sum can be seen landing. The display takes
 * typing and pasting as the desktop's does; the keys never move the cursor
 * out of the note, so Insert goes where the cursor was.
 */
@Composable
fun CalculatorPanel(calc: CalculatorState, onInsert: (String) -> Unit, onClose: () -> Unit) {
    val palette = LocalPalette.current
    // Never more than most of the screen: the note it writes into stays in sight.
    val tallest = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val scroll = rememberScrollState()
    // The history opens at the panel's foot, which can be below its fold
    // once the extra keys are out: opened, it is scrolled into sight --
    // after a frame, once the panel has measured what it now holds.
    LaunchedEffect(calc.history) {
        if (calc.history) {
            withFrameNanos { }
            scroll.animateScrollTo(scroll.maxValue)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card)
            .heightIn(max = tallest)
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Display(calc, Modifier.weight(1f))
            val closeSaid = stringResource(R.string.calc_close)
            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeSaid }) {
                Text("✕", fontSize = 18.sp, color = palette.subtext)
            }
        }
        KEYS.forEach { row -> KeyRow(row, calc) }

        Text(
            stringResource(if (calc.more) R.string.calc_less else R.string.calc_more),
            color = palette.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { calc.more = !calc.more }.padding(vertical = 6.dp),
        )
        if (calc.more) MORE_KEYS.forEach { row -> KeyRow(row, calc) }

        val formula = calc.pad.formula
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.sidebar)
                .clickable(enabled = formula != null) {
                    formula?.let {
                        onInsert(it)
                        calc.inserted = it
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.calc_insert), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                 color = if (formula != null) palette.text else palette.subtext)
        }
        calc.inserted?.let {
            Text(stringResource(R.string.calc_inserted, it), color = palette.subtext, fontSize = 12.sp)
        }

        History(calc, onInsert)
    }
}

@Composable
private fun Display(calc: CalculatorState, modifier: Modifier) {
    val palette = LocalPalette.current
    var field by remember { mutableStateOf(TextFieldValue(calc.pad.display)) }
    // A key changed the display: the cursor follows what it wrote, to the end.
    if (field.text != calc.pad.display) {
        field = TextFieldValue(calc.pad.display, TextRange(calc.pad.display.length))
    }
    BasicTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != calc.pad.display) {
                calc.pad = calc.pad.typed(next.text)
                calc.inserted = null
            }
        },
        singleLine = true,
        textStyle = TextStyle(color = palette.text, fontSize = 24.sp, fontFamily = FontFamily.Monospace,
                              textAlign = TextAlign.End),
        cursorBrush = SolidColor(palette.accent),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.sidebar)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .onFocusChanged { calc.displayFocused = it.isFocused },
    )
}

@Composable
private fun KeyRow(keys: List<String>, calc: CalculatorState) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (key in keys) {
            if (key.isEmpty()) {
                Spacer(Modifier.weight(1f))
                continue
            }
            val accent = key in ACCENT_KEYS
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (accent) palette.accent else palette.sidebar)
                    .clickable { calc.press(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(key, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                     color = if (accent) palette.onAccent else palette.text)
            }
        }
    }
}

/**
 * This note's past calculations, newest first, folded until wanted. A line
 * says what tapping does before anything can be tapped -- the desktop added
 * it after a stray click read as the app typing on its own.
 */
@Composable
private fun History(calc: CalculatorState, onInsert: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (calc.history) R.string.calc_history_open else R.string.calc_history),
            color = palette.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).clickable { calc.history = !calc.history }.padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.calc_clear), color = palette.subtext, fontSize = 12.sp,
            modifier = Modifier.clickable { calc.pad = calc.pad.clearHistory() }.padding(8.dp),
        )
    }
    if (!calc.history) return
    if (calc.pad.history.isEmpty()) {
        Text(stringResource(R.string.calc_none_yet), color = palette.subtext, fontSize = 12.sp,
             modifier = Modifier.padding(bottom = 6.dp))
        return
    }
    Text(stringResource(R.string.calc_tap_line), color = palette.subtext, fontSize = 11.sp)
    Column(Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
        for (entry in calc.pad.history) {
            Text(
                entry, color = palette.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onInsert(entry)
                        calc.inserted = entry
                    }
                    .padding(vertical = 6.dp),
            )
        }
    }
}
