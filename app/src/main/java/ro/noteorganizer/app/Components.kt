package ro.noteorganizer.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.hexColor

/**
 * What a screen reader says for a button drawn as a symbol -- "+", "⋮",
 * "🗑" -- which it otherwise read by the symbol's name, or not at all. The
 * symbol stays the button's text, so a sighted reader and the walk see it
 * as before.
 */
@Composable
fun Modifier.said(id: Int): Modifier {
    val words = stringResource(id)
    return semantics { contentDescription = words }
}

/**
 * A sheet from the bottom, as every one in the app is drawn: a window of its
 * own, whose bars Android drew for the phone's light or dark, not the app's
 * (a white bar of buttons on a light phone with the app dark), and which
 * opened half-way when taller than half the screen, Share's last card under
 * the buttons. A sheet opens whole, as tall as it needs, its bars the app's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, messages: SnackbarHostState? = null,
             content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    val depth = LocalMessagesDepth.current + 1
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.card,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = !palette.isDark,
            isAppearanceLightNavigationBars = !palette.isDark,
        ),
    ) {
        // The app's [messages] in the sheet that stays open over what said them
        // -- drawn in the window under it, they were never seen -- with their
        // room kept (the sheet scrolls), and in the topmost sheet only ([MessagesShown]).
        CompositionLocalProvider(LocalMessagesDepth provides depth) {
            if (messages == null) content()
            else {
                Column(Modifier.weight(1f, fill = false)) { content() }
                if (MessagesShown.Over(messages)) SnackbarHost(messages, Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
        }
    }
}

/** How deep the sheets around what is composed here stand: a host's place in [MessagesShown]. */
private val LocalMessagesDepth = compositionLocalOf { 0 }

/**
 * The hosts drawing the app's messages over the window -- a sheet's, the
 * template editor's -- counted for each state they draw. While one does,
 * the window's own host draws none, and of them only the deepest draws,
 * the newest of equals: a message is one live region, read out once, not
 * two TalkBack could read twice. A host that goes leaves a message still
 * standing to the next, which shows it again.
 */
object MessagesShown {
    private val over = mutableStateMapOf<Any, Map<Any, Int>>()

    /** Whether a host over the window draws [messages] now. */
    fun elsewhere(messages: Any): Boolean = over[messages]?.isNotEmpty() == true

    /** [host], [depth] sheets deep, stands over the window for [messages]. */
    internal fun shown(messages: Any, host: Any, depth: Int) {
        over[messages] = (over[messages] ?: emptyMap()) - host + (host to depth)
    }

    internal fun gone(messages: Any, host: Any) {
        val left = (over[messages] ?: emptyMap()) - host
        if (left.isNotEmpty()) over[messages] = left else over.remove(messages)
    }

    /** Whether [host] is the one of [messages]' hosts that draws them: the deepest, the newest of equals. */
    internal fun drawn(messages: Any, host: Any): Boolean {
        val hosts = over[messages] ?: return false
        val deepest = hosts.values.maxOrNull() ?: return false
        return hosts.entries.last { it.value == deepest }.key === host
    }

    /** A host over the window for [messages] while this is composed; true while it is the one that draws them. */
    @Composable
    fun Over(messages: SnackbarHostState): Boolean {
        val host = remember { Any() }
        val depth = LocalMessagesDepth.current
        DisposableEffect(messages, depth) {
            shown(messages, host, depth)
            onDispose { gone(messages, host) }
        }
        return drawn(messages, host)
    }
}

/**
 * Words that appear to say what just happened, read out by a screen reader
 * as they come, as the app's messages at the foot are: where a dialog or a
 * page says them under its own box instead, a plain line of text was read
 * by nobody.
 */
fun Modifier.saidAloud(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/**
 * A text field in a column that scrolls, kept in sight where it is typed
 * into: Compose brings the cursor into view only as the field takes the
 * focus, and Enter on the last line in sight made the next one under the
 * keys. After each change, once laid out, where it ended is brought into
 * view. On the field: [requester] in its modifier, after any padding;
 * [laidOut] as its onTextLayout; [typed] from its onValueChange.
 */
@Stable
class Following {
    val requester = BringIntoViewRequester()
    var layout by mutableStateOf<TextLayoutResult?>(null)
        private set
    // Where the last change ended, and the length of the text it made: kept
    // until a layout of that text arrives.
    var pending by mutableStateOf<Pair<Int, Int>?>(null)

    fun laidOut(result: TextLayoutResult) {
        layout = result
    }

    /** A change from [old] to [new]; [end] where the cursor stands, when the field knows it. */
    fun typed(old: String, new: String, end: Int? = null) {
        if (old != new) pending = (end ?: Editing.changeEnd(old, new)) to new.length
    }
}

/**
 * [cursor], given while the field has the focus, is also kept in sight as
 * the keyboard rises, asked at each height it passes: Compose keeps a long
 * field in view by another part of it (a tap on line 2, lines 20 to 22 in
 * sight). The note still jumps away and back as the keyboard rises:
 * Compose's own move comes first, and a request can only answer it.
 */
@Composable
fun rememberFollowing(cursor: Int? = null): Following {
    val following = remember { Following() }
    val drawn = following.layout
    LaunchedEffect(drawn) {
        val (at, length) = following.pending ?: return@LaunchedEffect
        // Laid out for this text: its length, or one more where an empty last
        // line is drawn with a stand-in of its own (the editor's paintLines).
        val laidOut = drawn ?: return@LaunchedEffect
        val laid = laidOut.layoutInput.text
        if (at > length || !(laid.length == length ||
                laid.length == length + 1 && laid.text.endsWith(STAND_IN))) return@LaunchedEffect
        following.pending = null
        following.requester.bringIntoView(laidOut.getCursorRect(at))
    }
    val keyboard = if (cursor != null) WindowInsets.ime.getBottom(LocalDensity.current) else 0
    LaunchedEffect(keyboard) {
        val laid = following.layout ?: return@LaunchedEffect
        if (cursor != null && keyboard > 0 && cursor <= laid.layoutInput.text.length) {
            following.requester.bringIntoView(laid.getCursorRect(cursor))
        }
    }
    return following
}

/**
 * A note's section heading that folds the section away and back, ▾ open and
 * ▸ folded, as [SectionLabel] draws a heading: room made for the sections
 * below it, the way the owner asked for checklists and comments (2026-10-25).
 */
@Composable
fun FoldLabel(text: String, folded: Boolean, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    val said = stringResource(if (folded) R.string.section_folded else R.string.section_open)
    Row(
        Modifier.padding(top = 20.dp, bottom = 8.dp).clickable(onClick = onToggle)
            .semantics { stateDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), color = palette.subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium,
             letterSpacing = 0.8.sp)
        // The arrow is drawn, not said: the state says it.
        Text(if (folded) "  ▸" else "  ▾", color = palette.subtext, fontSize = 12.sp,
             modifier = Modifier.clearAndSetSemantics { })
    }
}

/** A small heading over a group of rows, as Settings' sections have. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = LocalPalette.current.subtext,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/**
 * A row of colour swatches: the desktop's palettes, nothing to type. The
 * picked one wears a ring in the accent; [allowNone] adds a first swatch
 * meaning "no colour of its own", drawn so it reads as a choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Swatches(
    colours: List<String>,
    chosen: String,
    allowNone: Boolean,
    onPick: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val none = stringResource(R.string.colour_none)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val all = (if (allowNone) listOf("") else emptyList()) + colours
        for ((position, colour) in all.withIndex()) {
            val picked = colour.equals(chosen, ignoreCase = true)
            // Said by its place and its value, and picked or not, as a radio
            // button is: the hex alone was all a screen reader had, and the
            // ring all an eye had.
            val said = if (colour.isEmpty()) none else stringResource(R.string.swatch_said, position + 1, all.size, colour)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (picked) palette.accent else Color.Transparent)
                    .padding(if (picked) 3.dp else 0.dp)
                    .clip(CircleShape)
                    .background(if (colour.isEmpty()) palette.inset else hexColor(colour))
                    .border(1.dp, palette.hairline, CircleShape)
                    .selectable(selected = picked, role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(colour) }
                    .semantics { contentDescription = said },
                contentAlignment = Alignment.Center,
            ) {
                if (colour.isEmpty()) Text("✕", color = palette.subtext, fontSize = 14.sp)
            }
        }
    }
}

/** A coloured dot, for a category in a list. */
@Composable
fun Dot(colour: String, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(hexColor(colour)))
}

/**
 * The desktop's note card: the colour stripe down its left edge, the
 * headline, the first words, and the tags line in a readable shade of the
 * note's colour; a headline borrowed from the text is set lighter than a
 * title. [open] outlines the card of the note open beside the list, on a
 * screen wide enough for both; a selected card is filled as well.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    colour: String,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    open: Boolean = false,
    // Custom order's drag handle, at the card's right edge, when the list
    // is arranged by hand.
    handle: (@Composable () -> Unit)? = null,
    // What a screen reader or Switch Access may do to the card besides
    // open and select it: Move up, Move down.
    actions: List<androidx.compose.ui.semantics.CustomAccessibilityAction> = emptyList(),
) {
    val palette = LocalPalette.current
    val stripe = hexColor(colour)
    // Filled when selected: its quieter words take the shade that reads on the fill.
    val quiet = palette.subtextOn(selected)
    val tags = if (selected) palette.onChosen(colour) else palette.legibleOnCard(colour)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.calendarHasNotes else palette.card)
            .then(if (selected || open) Modifier.border(2.dp, palette.accent, RoundedCornerShape(12.dp)) else Modifier)
            // The colour stripe painted on the card, not a child stretched to its
            // height, which measured every card twice (IntrinsicSize.Min), every
            // line of its text laid out twice as it scrolled into sight.
            .drawBehind { drawRect(stripe, size = Size(5.dp.toPx(), size.height)) }
            .then(if (actions.isEmpty()) Modifier else Modifier.semantics {
                customActions = actions
            })
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 5.dp),
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Text(if (selected) "☑ " else "☐ ", color = palette.accentTextOn(selected), fontSize = 16.sp)
                }
                Text(
                    text = badges(note) + note.headline,
                    color = if (note.headlineIsBorrowed) quiet else palette.text,
                    fontSize = 15.sp,
                    fontWeight = if (note.headlineIsBorrowed) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.snippet.isNotEmpty()) {
                Text(
                    text = note.snippet,
                    color = quiet,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = note.tagLine(),
                color = tags,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (handle != null) Box(Modifier.align(Alignment.CenterVertically)) { handle() }
    }
}

fun badges(note: Note): String {
    val out = StringBuilder()
    if (note.pinned) out.append("📌 ")
    if (note.isRecurring) out.append("🔁 ")
    return out.toString()
}

/** The desktop's rule: the note's own colour, else its category's, else the accent. */
fun noteColour(note: Note, categories: Map<String, String>, accentHex: String): String =
    note.color.ifEmpty { categories[note.category] ?: accentHex }

/**
 * A dialog's text, scrolled: Material's AlertDialog gives it the room its
 * buttons leave and no scroll, and at the largest letters or on a phone on
 * its side its end was cut under them. [key] starts a new question at its top.
 */
@Composable
fun DialogBody(key: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.key(key) {
        Column(Modifier.verticalScroll(rememberScrollState()), content = content)
    }
}

/**
 * A top bar's title on one line, shrunk to its room, and "…" where even 6 sp
 * does not fit: a plain Text broke inside its word at the largest letters
 * and the 64 dp bar cut the rest ("select" / "ed", "Novembe" / "r 2026").
 */
@Composable
fun BarTitle(text: String, modifier: Modifier = Modifier,
             color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    val style = androidx.compose.material3.LocalTextStyle.current
    val ink = if (color != androidx.compose.ui.graphics.Color.Unspecified) color
              else if (style.color != androidx.compose.ui.graphics.Color.Unspecified) style.color
              else androidx.compose.material3.LocalContentColor.current
    val largest = if (style.fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) style.fontSize else 22.sp
    androidx.compose.foundation.text.BasicText(
        text, modifier,
        style = style.merge(androidx.compose.ui.text.TextStyle(color = ink)),
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        maxLines = 1, softWrap = false,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = largest),
    )
}

/**
 * A question with a safe answer. The safe one is the highlighted button,
 * as on the desktop -- Enter there, the thumb's first landing here -- and
 * the one that cannot be taken back is quieter, in the warning colour.
 */
@Composable
fun Ask(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // Whatever a question quotes is quoted short already; this is the
        // last guard, for a reason that carries text from outside. Scrolled,
        // or its end was cut at the largest letters or on its side.
        text = { Text(forTheScreen(text), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) palette.dangerText else palette.accentText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismiss, fontWeight = FontWeight.Medium, color = palette.text)
            }
        },
        containerColor = palette.card,
        titleContentColor = palette.text,
        textContentColor = palette.subtext,
    )
}
