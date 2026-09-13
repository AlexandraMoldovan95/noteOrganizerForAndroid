package com.example.noteorganizer

/**
 * Applying and removing styles over a range of a note's body.
 *
 * The hard part is not the button, it is what a style means where another
 * one already is. Three rules shape everything here:
 *
 * 1. **Styles come in groups, and within a group only one can hold a
 *    position.** Two text colours on one letter is not a thing; bold and
 *    underline on one letter is.
 * 2. **Bold and italic share a group,** because the desktop stores their
 *    combination as the single style `bold_italic` — two Tk tags that
 *    both set a font do not compose. Bolding text that is already italic
 *    must therefore produce `bold_italic`, not two overlapping spans, or
 *    the desktop would draw one and lose the other.
 * 3. **Offsets are code points,** like everything the file stores.
 *
 * The work is done by a boundary sweep: collect every edge in the group,
 * decide each elementary interval's state, then merge the runs back up.
 * That is exact where interval arithmetic by hand is where the bugs live.
 */

/** Styles that cannot overlap each other share a group name. */
fun styleGroup(style: String): String = when (style) {
    "bold", "italic", "bold_italic" -> "font"
    else -> style
}

private fun fontFlags(style: String): Pair<Boolean, Boolean> = when (style) {
    "bold" -> true to false
    "italic" -> false to true
    "bold_italic" -> true to true
    else -> false to false
}

private fun fontName(bold: Boolean, italic: Boolean): String? = when {
    bold && italic -> "bold_italic"
    bold -> "bold"
    italic -> "italic"
    else -> null
}

/**
 * The styles that cover EVERY position of [start, end).
 *
 * "Every", not "any": a toolbar button should look on only when the whole
 * selection carries the style, or pressing it twice would be needed to
 * turn something off that was only partly on.
 */
fun stylesOver(spans: List<FormatSpan>, start: Int, end: Int): Set<String> {
    if (end <= start) return emptySet()
    val out = LinkedHashSet<String>()
    val names = spans.map { it.style }.toSet()
    for (name in names) {
        val covering = spans.filter {
            when (styleGroup(name)) {
                // Bold counts as present under bold_italic too.
                "font" -> styleGroup(it.style) == "font" &&
                    matchesFontFlag(it.style, name)
                else -> it.style == name
            }
        }
        if (coversAll(covering, start, end)) out.add(name)
    }
    // Report the flags, not the stored spelling: a selection that is
    // bold_italic is bold, and is italic.
    if (out.contains("bold_italic")) {
        out.add("bold")
        out.add("italic")
    }
    return out
}

private fun matchesFontFlag(stored: String, asked: String): Boolean {
    val (storedBold, storedItalic) = fontFlags(stored)
    val (askedBold, askedItalic) = fontFlags(asked)
    return (!askedBold || storedBold) && (!askedItalic || storedItalic)
}

private fun coversAll(spans: List<FormatSpan>, start: Int, end: Int): Boolean {
    if (spans.isEmpty()) return false
    var at = start
    for (span in spans.sortedBy { it.start }) {
        if (span.start > at) return false
        if (span.end > at) at = span.end
        if (at >= end) return true
    }
    return at >= end
}

/**
 * Turns a style on over [start, end), or off if it is already on
 * throughout — which is what a toolbar button means by "toggle".
 */
fun toggleStyle(
    spans: List<FormatSpan>,
    start: Int,
    end: Int,
    style: String,
    value: String = "",
): List<FormatSpan> {
    if (end <= start) return spans
    val on = stylesOver(spans, start, end).contains(style)
    // A colour is not a toggle in the same sense: asking for a DIFFERENT
    // colour over text that already has one replaces it rather than
    // clearing it. Only asking for the same one again clears it.
    if (on && isValueStyle(style)) {
        val same = spans.any {
            it.style == style && it.value == value && it.start <= start && it.end >= end
        }
        if (!same) return setStyle(spans, start, end, style, value)
    }
    return if (on) clearStyle(spans, start, end, style)
    else setStyle(spans, start, end, style, value)
}

private fun isValueStyle(style: String) =
    style in setOf("text_color", "highlight", "font_size", "font_family")

/** Applies a style over [start, end), replacing whatever its group held. */
fun setStyle(
    spans: List<FormatSpan>,
    start: Int,
    end: Int,
    style: String,
    value: String = "",
): List<FormatSpan> = rebuild(spans, start, end, style, value, turnOn = true)

/** Removes a style from [start, end), leaving the parts outside it. */
fun clearStyle(
    spans: List<FormatSpan>,
    start: Int,
    end: Int,
    style: String,
): List<FormatSpan> = rebuild(spans, start, end, style, "", turnOn = false)

private fun rebuild(
    all: List<FormatSpan>,
    start: Int,
    end: Int,
    style: String,
    value: String,
    turnOn: Boolean,
): List<FormatSpan> {
    if (end <= start) return all
    val group = styleGroup(style)
    val mine = all.filter { styleGroup(it.style) == group }
    val others = all.filter { styleGroup(it.style) != group }

    // Every edge that could change the state, so each elementary interval
    // between two of them is uniform.
    val edges = sortedSetOf(start, end)
    for (span in mine) {
        edges.add(span.start)
        edges.add(span.end)
    }
    val points = edges.toList()

    val pieces = ArrayList<FormatSpan>()
    for (i in 0 until points.size - 1) {
        val from = points[i]
        val to = points[i + 1]
        if (to <= from) continue
        val existing = mine.firstOrNull { it.start <= from && it.end >= to }
        val inside = from >= start && to <= end
        val result = stateFor(existing, inside, turnOn, style, value, group)
        if (result != null) pieces.add(FormatSpan(from, to, result.first, result.second))
    }
    return (others + merge(pieces)).sortedWith(compareBy({ it.start }, { it.style }))
}

/** What this elementary interval should carry afterwards, or null for nothing. */
private fun stateFor(
    existing: FormatSpan?,
    inside: Boolean,
    turnOn: Boolean,
    style: String,
    value: String,
    group: String,
): Pair<String, String>? {
    if (!inside) {
        return existing?.let { it.style to it.value }
    }
    if (group != "font") {
        return if (turnOn) style to value else null
    }
    // The font group carries two independent flags in one stored name.
    var (bold, italic) = existing?.let { fontFlags(it.style) } ?: (false to false)
    val (wantBold, wantItalic) = fontFlags(style)
    if (wantBold) bold = turnOn
    if (wantItalic) italic = turnOn
    return fontName(bold, italic)?.let { it to "" }
}

/** Runs that touch and agree become one span again. */
private fun merge(pieces: List<FormatSpan>): List<FormatSpan> {
    if (pieces.isEmpty()) return pieces
    val sorted = pieces.sortedBy { it.start }
    val out = ArrayList<FormatSpan>()
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        if (next.start <= current.end &&
            next.style == current.style && next.value == current.value
        ) {
            current = current.copy(end = maxOf(current.end, next.end))
        } else {
            out.add(current)
            current = next
        }
    }
    out.add(current)
    return out
}

/**
 * Turns UTF-16 string indices — which is what a text field reports — into
 * the code-point offsets the file stores. The inverse of [utf16Offsets],
 * and needed for exactly the same reason.
 */
fun codePointOffsets(text: String, wanted: Collection<Int>): Map<Int, Int> {
    val out = HashMap<Int, Int>(wanted.size)
    var codePoints = 0
    var index = 0
    for (target in wanted.toSortedSet()) {
        if (target <= 0) {
            out[target] = 0
            continue
        }
        while (index < target && index < text.length) {
            val ch = text[index]
            val pair = Character.isHighSurrogate(ch) &&
                index + 1 < text.length &&
                Character.isLowSurrogate(text[index + 1])
            index += if (pair) 2 else 1
            codePoints += 1
        }
        out[target] = codePoints
    }
    return out
}
