package ro.noteorganizer.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

/*
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
    val lines = ArrayList<Triple<Int, Int, TextDecoration>>()
    val sizes = ArrayList<Triple<Int, Int, Float>>()
    val text = buildAnnotatedString {
        append(note.body)
        for (span in note.formatting) {
            val start = offsets[span.start] ?: continue
            val end = offsets[span.end] ?: continue
            // Both offsets clamp to the body's end, so a span that ran off
            // the end collapses to nothing rather than throwing inside a
            // draw. Dropping it is the same choice normalize_note makes.
            if (end <= start) continue
            when (span.style) {
                "underline" -> { lines.add(Triple(start, end, TextDecoration.Underline)); continue }
                "strike" -> { lines.add(Triple(start, end, TextDecoration.LineThrough)); continue }
            }
            val style = styleOf(span, surface)
            if (style == null) {
                ignored.add(span.style)
                continue
            }
            if (span.style == "font_size") sizes.add(Triple(start, end, style.fontSize.value)) else addStyle(style, start, end)
        }
        for ((start, end, decoration) in decorations(lines)) addStyle(SpanStyle(textDecoration = decoration), start, end)
        for ((start, end, size) in sizeRuns(sizes)) addStyle(SpanStyle(fontSize = size.em), start, end)
    }
    return RenderedBody(text, ignored)
}

/**
 * Sizes over the same letters, the later one listed winning, as the
 * desktop's runs take them: drawn as em, two sizes over one letter
 * multiplied -- 24 inside 20 drew at 3.3 times the body, and a code's
 * thousands grew past any screen. One sweep over the
 * edges, the sizes open at each held by their place in the list.
 */
internal fun sizeRuns(sizes: List<Triple<Int, Int, Float>>): List<Triple<Int, Int, Float>> {
    if (sizes.size < 2) return sizes
    val points = sizes.flatMap { listOf(it.first, it.second) }.distinct().sorted()
    val starting = sizes.indices.groupBy { sizes[it].first }
    val open = java.util.PriorityQueue<Int>(compareByDescending { it })
    val runs = ArrayList<Triple<Int, Int, Float>>()
    for (k in 0 until points.size - 1) {
        val at = points[k]
        starting[at]?.let { open.addAll(it) }
        while (open.isNotEmpty() && sizes[open.peek()!!].second <= at) open.poll()
        val top = open.peek() ?: continue
        val size = sizes[top].third
        val last = runs.lastOrNull()
        if (last != null && last.second == at && last.third == size) runs[runs.lastIndex] = Triple(last.first, points[k + 1], size)
        else runs.add(Triple(at, points[k + 1], size))
    }
    return runs
}

/**
 * Underline and strike over the same letters, as one decoration: two
 * styles each naming a decoration do not add up -- the later wins -- and
 * the desktop draws both. One sweep over the edges, counting the lines
 * open at each: asked of every span for each pair of edges, the merge took
 * 200 million tests for a note's 10,000 spans at every letter typed.
 */
internal fun decorations(lines: List<Triple<Int, Int, TextDecoration>>): List<Triple<Int, Int, TextDecoration>> {
    if (lines.isEmpty()) return emptyList()
    val kinds = lines.map { it.third }.distinct()
    // At each edge, how many lines of each kind begin there, less those that end.
    val change = java.util.TreeMap<Int, IntArray>()
    for ((start, end, decoration) in lines) {
        val kind = kinds.indexOf(decoration)
        change.getOrPut(start) { IntArray(kinds.size) }[kind] += 1
        change.getOrPut(end) { IntArray(kinds.size) }[kind] -= 1
    }
    val open = IntArray(kinds.size)
    val out = ArrayList<Triple<Int, Int, TextDecoration>>()
    var from = -1
    for ((edge, delta) in change) {
        if (from >= 0) {
            val here = kinds.filterIndexed { kind, _ -> open[kind] > 0 }
            if (here.isNotEmpty()) out.add(Triple(from, edge, if (here.size == 1) here[0] else TextDecoration.combine(here)))
        }
        for (kind in delta.indices) open[kind] += delta[kind]
        from = edge
    }
    return out
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
    // Not the stored colour: a palette meant for a white page buries light
    // text on a dark one, so the paint is blended toward the page, as the
    // desktop does, and the pure colour stays in the data.
    "highlight" -> hexColor(span.value)?.let { SpanStyle(background = blend(it, surface, 0.45f)) }
    // Points against the desktop's 12 pt body, as the desktop and the PDF
    // read them: drawn as sp beside a 16 sp body, 14 made words smaller and
    // 16 changed nothing.
    "font_size" -> span.value.toIntOrNull()
        ?.takeIf { it in 6..96 }
        ?.let { SpanStyle(fontSize = (it / 12f).em) }
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
 * Fake transparency on a surface that has none: *top* at *opacity* over
 * *base*, channel by channel. The desktop's `_blend_hex`, in Compose's
 * floating-point colour channels rather than hex pairs.
 */
fun blend(top: Color, base: Color, opacity: Float): Color = Color(
    red = top.red * opacity + base.red * (1f - opacity),
    green = top.green * opacity + base.green * (1f - opacity),
    blue = top.blue * opacity + base.blue * (1f - opacity),
)

/**
 * Parsed by hand rather than through `android.graphics.Color.parseColor`,
 * which exists only on a device: on the computer it is a stub that throws,
 * so a test touching it fails for a reason unrelated to the colour.
 */
fun hexColor(value: String): Color? {
    // Expanded first: #f00 read as six digits drew a near-black green.
    val full = Notebook.sanitizeColor(value)
    if (full.isEmpty()) return null
    val rgb = full.substring(1).toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}
