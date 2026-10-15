package ro.noteorganizer.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Turns a note's stored spans into text Compose can draw.
 *
 * This file is where Compose is allowed in and `Notebook.kt` is not --
 * the same separation the desktop app keeps between its `core/` layer,
 * which may never import tkinter, and its interface. The rules travel
 * between platforms; the drawing does not.
 */

/** What a style could not be drawn as, so the screen can admit it. */
data class RenderedBody(val text: AnnotatedString, val ignoredStyles: Set<String>)

/**
 * @param surface the colour the text is drawn on, which highlights blend
 *   toward. Defaults to the dark theme's page so a test need not name it.
 */
fun renderBody(note: Note, surface: Color = Color(0xFF1E1E2F)): RenderedBody {
    val ignored = LinkedHashSet<String>()
    val offsets = utf16Offsets(note.body, note.formatting.flatMap { listOf(it.start, it.end) })
    val text = buildAnnotatedString {
        append(note.body)
        for (span in note.formatting) {
            val start = offsets[span.start] ?: continue
            val end = offsets[span.end] ?: continue
            // Both offsets clamp to the body's end, so a span that ran off
            // the end collapses to nothing rather than throwing inside a
            // draw. Dropping it is the same choice normalize_note makes.
            if (end <= start) continue
            val style = styleOf(span, surface)
            if (style == null) {
                ignored.add(span.style)
                continue
            }
            addStyle(style, start, end)
        }
    }
    return RenderedBody(text, ignored)
}

/**
 * `bold_italic` is one style rather than two overlapping spans because two
 * Tk tags that both set a font do not compose. Compose has no such
 * problem -- but the stored data contains `bold_italic` and must be read,
 * so the constraint outlives the toolkit that caused it.
 */
private fun styleOf(span: FormatSpan, surface: Color): SpanStyle? = when (span.style) {
    "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
    "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
    "bold_italic" -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "text_color" -> hexColor(span.value)?.let { SpanStyle(color = it) }
    // Not the stored colour: a palette meant for a white page is a solid
    // pale wash that buries light text on a dark one. The desktop hit this
    // and blends the paint toward the page instead, keeping the pure
    // colour in the data -- so the same highlight stays readable in every
    // theme. Its comment is blunt about it: "the solid palette fill buried
    // the text under it."
    "highlight" -> hexColor(span.value)?.let { SpanStyle(background = blend(it, surface, 0.45f)) }
    "font_size" -> span.value.toIntOrNull()
        ?.takeIf { it in 6..96 }
        ?.let { SpanStyle(fontSize = it.sp) }
    // The Windows faces are not on a phone; each is drawn as the kind it
    // is -- serifed, fixed-width or plain -- which is what it was chosen for.
    "font_family" -> when (Fonts.kindOf(span.value)) {
        "serif" -> SpanStyle(fontFamily = FontFamily.Serif)
        "monospace" -> SpanStyle(fontFamily = FontFamily.Monospace)
        "sans" -> SpanStyle(fontFamily = FontFamily.SansSerif)
        else -> null
    }
    // Anything else is named here rather than silently skipped, so the
    // screen can say what it could not honour.
    else -> null
}

/**
 * Parsed by hand rather than through `android.graphics.Color.parseColor`,
 * which exists only on a device: on the computer it is a stub that throws,
 * so a test touching it fails for a reason unrelated to the colour.
 */
/**
 * Fake transparency on a surface that has none: *top* at *opacity* over
 * *base*, channel by channel. The desktop's `_blend_hex`, in Compose's
 * floating-point colour channels rather than hex pairs.
 */
fun blend(top: Color, base: Color, opacity: Float): Color = Color(
    red = top.red * opacity + base.red * (1f - opacity),
    green = top.green * opacity + base.green * (1f - opacity),
    blue = top.blue * opacity + base.blue * (1f - opacity),
)

fun hexColor(value: String): Color? {
    if (!Notebook.isHexColor(value)) return null
    val rgb = value.substring(1).toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}
