package ro.noteorganizer.app

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

/**
 * The faces and sizes a note may carry: the desktop's FONT_FAMILIES and
 * FONT_SIZES, the only values its normalize_formatting keeps. "Default"
 * in a picker means no span at all -- the note's own size and face.
 */
object Fonts {
    const val DEFAULT = "Default"
    val SIZES = listOf(9, 10, 11, 12, 14, 16, 20, 24)
    val FAMILIES = listOf("Segoe UI", "Arial", "Calibri", "Georgia", "Times New Roman",
                          "Verdana", "Consolas", "Courier New")

    /**
     * The phone has none of these Windows faces; each is drawn as the kind
     * it is -- serifed, fixed-width, or plain -- which is what the person
     * chose it for. Null for a name that is not one of them.
     */
    fun kindOf(face: String): String? = when (face) {
        "Georgia", "Times New Roman" -> "serif"
        "Consolas", "Courier New" -> "monospace"
        in FAMILIES -> "sans"
        else -> null
    }
}

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

/**
 * The one value [style] holds over every position of [start, end) -- a
 * size of "14", a face -- or null where it holds none, or a mix: the
 * desktop's `_uniform_font_in_selection`, which its size and face boxes
 * show. A mix reads as no value rather than as the first run's, which
 * would claim the whole selection is that size.
 */
fun uniformValue(spans: List<FormatSpan>, start: Int, end: Int, style: String): String? {
    if (end <= start) return null
    val values = spans.filter { it.style == style && it.start < end && it.end > start }.map { it.value }.toSet()
    val only = values.singleOrNull() ?: return null
    return only.takeIf { coversAll(spans.filter { it.style == style && it.value == only }, start, end) }
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

/** The four styles a key turns on and off, which wait for what is typed next when nothing is selected. */
val PENDING_STYLES = setOf("bold", "italic", "underline", "strike")

/**
 * The styles after typing: [deleted] code points at [at] replaced by
 * [typed], as the desktop's text box leaves them, its
 * `_spread_typed_run` included.
 *
 * - Letters typed at the start of a styled run stay outside it, and
 *   letters typed inside a run are inside it: a run takes what lands
 *   between two of its own letters.
 * - Letters glued onto a styled non-space character [before] wear its
 *   styles -- weight, lines, colour, highlight, size and face -- up to a
 *   space typed with them: "ly" typed after a highlighted "quick" is
 *   highlighted, "Very " typed before a bold "Important" is not.
 * - [pending] styles, turned on with nothing selected, go on everything
 *   typed: the desktop's pending style, on until its key is pressed again.
 *
 * The phone once did the opposite at both edges, by the rule an edit
 * found from the text alone keeps ([remapSpans]).
 */
fun typedSpans(
    spans: List<FormatSpan>,
    at: Int,
    deleted: Int,
    typed: IntArray,
    before: Int?,
    pending: Set<String> = emptySet(),
): List<FormatSpan> {
    val k = typed.size
    val out = ArrayList<FormatSpan>(spans.size)
    for (span in spans) {
        // The stretch replaced taken out first, as the text box deletes a
        // selection before it types.
        var s = if (span.start <= at) span.start else maxOf(at, span.start - deleted)
        var e = if (span.end <= at) span.end else maxOf(at, span.end - deleted)
        if (e <= s) continue
        if (s >= at) {
            s += k
            e += k
        } else if (e > at) {
            e += k
        }
        out.add(span.copy(start = s, end = e))
    }
    // A space typed first stops the styles there and then: up to it is nothing.
    if (k > 0 && before != null && !PyText.isSpace(before)) {
        val space = typed.indexOfFirst { PyText.isSpace(it) }
        val stop = at + if (space >= 0) space else k
        for (i in out.indices) {
            val span = out[i]
            if (span.end == at) out[i] = span.copy(end = stop)
        }
    }
    var result: List<FormatSpan> = out
    if (k > 0) for (style in pending.filter { it in PENDING_STYLES }) result = setStyle(result, at, at + k, style)
    return result.sortedWith(compareBy({ it.start }, { it.style }))
}

/**
 * [old] become [new] by typing that left the caret at [caret], in code
 * points: the edit found from the caret -- what follows it is the old
 * text's end -- and the styles moved by [typedSpans]. A change that is not
 * letters typed before the caret, or that only took letters away, is
 * moved by [remapSpans].
 */
fun spansAfterTyping(old: String, new: String, caret: Int, spans: List<FormatSpan>,
                     pending: Set<String> = emptySet()): List<FormatSpan> {
    val a = codePointsOf(old)
    val b = codePointsOf(new)
    val tail = b.size - caret
    if (caret < 0 || tail < 0 || tail > a.size) return remapSpans(old, new, spans).spans
    for (i in 0 until tail) if (a[a.size - tail + i] != b[caret + i]) return remapSpans(old, new, spans).spans
    val oldTail = a.size - tail
    var at = 0
    while (at < oldTail && at < caret && a[at] == b[at]) at++
    if (caret == at) return remapSpans(old, new, spans).spans
    return typedSpans(spans, at, oldTail - at, b.copyOfRange(at, caret), if (at > 0) a[at - 1] else null, pending)
}

/**
 * Turns a style on over [start, end), or off if it is already on
 * throughout — which is what a toolbar button means by "toggle". A colour,
 * a size or a face is set, never toggled: the same swatch again leaves it,
 * as the desktop's does, and only ✕ takes it away.
 */
fun toggleStyle(
    spans: List<FormatSpan>,
    start: Int,
    end: Int,
    style: String,
    value: String = "",
): List<FormatSpan> {
    if (end <= start) return spans
    // The same colour tapped again to be sure took it away; the desktop's
    // swatch always sets, and its Clear -- the phone's ✕ -- clears.
    if (isValueStyle(style)) return setStyle(spans, start, end, style, value)
    val on = stylesOver(spans, start, end).contains(style)
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
