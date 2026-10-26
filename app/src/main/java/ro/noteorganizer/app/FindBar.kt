package ro.noteorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.Palette
import ro.noteorganizer.app.ui.theme.hexColor

/**
 * The find bar while a note is open: whether it shows, what its field
 * holds, which match is current. The words themselves live on [AppState],
 * so the next note opens the bar with them, as the desktop's Ctrl+F does.
 */
class FindState {
    var open by mutableStateOf(false)
    var field by mutableStateOf(TextFieldValue(""))
    var index by mutableIntStateOf(0)

    /** Counts the requests to scroll the current match into sight. */
    var reveal by mutableIntStateOf(0)

    fun step(delta: Int, total: Int) {
        if (total == 0) return
        index = Finding.step(index, delta, total)
        reveal += 1
    }
}

/** Every match's ground: the accent at a third of its strength over the card. */
fun findAllColour(palette: Palette): Color =
    hexColor(Themes.blend(palette.hex.getValue("card"), palette.hex.getValue("accent"), 0.35))

/**
 * Prose with its matches painted: every one on the light ground, the
 * current one in the accent with the letters that read on it -- and
 * underlined, so it is told from the others by more than colour, as the
 * desktop boxes it for a colour-blind eye.
 */
fun paintFound(text: AnnotatedString, found: List<Finding.InText>, current: Finding.InText?,
               palette: Palette): AnnotatedString =
    paintRanges(text, found.map { it.start to it.end }, current?.let { it.start to it.end }, palette)

/** [paintFound] for any field: each match a (start, end) in [text]. */
fun paintRanges(text: AnnotatedString, found: List<Pair<Int, Int>>, current: Pair<Int, Int>?,
                palette: Palette): AnnotatedString {
    if (found.isEmpty()) return text
    val all = findAllColour(palette)
    val builder = AnnotatedString.Builder(text)
    for ((start, end) in found) {
        if (end <= text.length) builder.addStyle(SpanStyle(background = all), start, end)
    }
    if (current != null && current.second <= text.length) {
        builder.addStyle(
            SpanStyle(background = palette.accent, color = palette.onAccent, textDecoration = TextDecoration.Underline),
            current.first, current.second,
        )
    }
    return builder.toAnnotatedString()
}

/**
 * Under the editor's top bar, across the screen: the field, the count,
 * back and next, "Aa" for case, and ✕. The keyboard's own search key steps
 * to the next match, as Enter does on the desktop.
 */
@Composable
fun FindBar(
    find: FindState,
    counter: String,
    missed: Boolean,
    matchCase: Boolean,
    focus: FocusRequester,
    onQuery: (TextFieldValue) -> Unit,
    onCase: () -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    // Opened, the bar has the keyboard: ready to type into, as Ctrl+F is.
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = find.field,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onStep(1) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .clip(RoundedCornerShape(8.dp))
                // Found nowhere: the theme's warning tint, as the desktop's box.
                .background(if (missed) hexColor(palette.hex.getValue("find_miss")) else palette.sidebar)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { field ->
                if (find.field.text.isEmpty()) {
                    Text(stringResource(R.string.find_hint), color = palette.subtext, fontSize = 15.sp)
                }
                field()
            },
        )
        Text(counter, color = palette.subtext, fontSize = 12.sp,
             modifier = Modifier.width(56.dp).padding(start = 8.dp))
        BarKey("▴", stringResource(R.string.find_prev)) { onStep(-1) }
        BarKey("▾", stringResource(R.string.find_next)) { onStep(1) }
        val caseSaid = stringResource(R.string.find_case)
        Box(
            Modifier
                .size(40.dp)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(if (matchCase) Modifier.background(palette.accent)
                      else Modifier.border(1.dp, palette.hairline, RoundedCornerShape(6.dp)))
                .toggleable(value = matchCase, role = Role.Checkbox, onValueChange = { onCase() })
                .semantics { contentDescription = caseSaid },
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                 color = if (matchCase) palette.onAccent else palette.subtext)
        }
        BarKey("✕", stringResource(R.string.find_close), onClose)
    }
}

@Composable
private fun BarKey(label: String, said: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.text)
    }
}
