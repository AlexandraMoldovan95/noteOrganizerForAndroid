package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * One note out as a file: the desktop's "Export as file", byte for byte.
 *
 * Two formats, as on the desktop. A web page (.html) carries everything
 * the note has -- styles, colours, lists, tables as real tables -- and is
 * the road to a PDF (a browser's print) and to Word, which opens one. Plain
 * text (.txt) is the body as stored. Both add the checklist and the
 * comments after the body, and neither carries attached files: those
 * travel in a full backup.
 *
 * The page is `note_to_html` and `html_document` from the desktop's
 * core/richtext.py, ported line for line, and `CalculatorTest`'s neighbour
 * `ExportingTest` holds a fixture of notes the desktop itself exported, so
 * a file from the phone is the file the desktop would have written.
 *
 * Everything here counts as Python does: a note's formatting and its
 * paragraph lines are positions in CODE POINTS, so each line is worked
 * on as an array of them, and Python's spaces are the ones stripped.
 *
 * The note's styles are read here through the desktop's own gatekeepers
 * (`normalize_formatting`, `normalize_paragraphs`) rather than the
 * phone's lenient reader, so a span the desktop would drop is dropped
 * from the page too.
 */
object Exporting {

    const val HTML = "html"
    const val TXT = "txt"

    /** The suggested file name's extension and the type a phone's apps know it by. */
    fun extension(format: String) = if (format == HTML) ".html" else ".txt"
    fun mimeType(format: String) = if (format == HTML) "text/html" else "text/plain"

    // ---- the desktop's gatekeepers, as an export reads a note ----

    private val ON_OFF = setOf("bold", "italic", "bold_italic", "underline", "strike")
    private val COLOURS = setOf("text_color", "highlight")
    private val FONT_FAMILIES = Fonts.FAMILIES
    private val FONT_SIZES = Fonts.SIZES.map { it.toLong() }
    private const val MAX_SPANS = 10_000
    private val BULLET_GLYPHS = listOf("•", "◦", "▪")
    private val LIST_KINDS = setOf("bullet", "number")
    private val ALIGNMENTS = setOf("left", "center", "right")
    private const val MAX_INDENT = 8L
    private const val LINE_BREAK = "<br>"

    /** A JSON whole number as Python's json reads one: never a true/false, never 1.0. */
    private fun whole(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is java.math.BigInteger -> if (value.bitLength() < 63) value.toLong()
                                   else if (value.signum() > 0) Long.MAX_VALUE else Long.MIN_VALUE
        else -> null
    }

    private fun clampToInt(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun validHex(value: Any?): Boolean =
        value is String && value.length == 7 && value[0] == '#' &&
            value.substring(1).all { it in "0123456789abcdefABCDEF" }

    /** `normalize_formatting`: the spans a note's file holds, as the desktop keeps them. */
    fun normalizeFormatting(value: Any?): List<FormatSpan> {
        val array = value as? JSONArray ?: return emptyList()
        val spans = ArrayList<FormatSpan>()
        for (i in 0 until array.length()) {
            val span = array.opt(i) as? JSONObject ?: continue
            val style = span.opt("style")
            val start = whole(span.opt("start"))
            val end = whole(span.opt("end"))
            if (start == null || end == null || start < 0 || start >= end) continue
            val s = clampToInt(start)
            val e = clampToInt(end)
            val raw = span.opt("value")
            when {
                style is String && style in ON_OFF -> spans.add(FormatSpan(s, e, style))
                style is String && style in COLOURS && validHex(raw) ->
                    spans.add(FormatSpan(s, e, style, (raw as String).lowercase()))
                style == "font_size" -> {
                    val size = whole(raw)
                    if (size != null && size in FONT_SIZES) spans.add(FormatSpan(s, e, "font_size", size.toString()))
                }
                style == "font_family" -> {
                    if (raw is String && raw in FONT_FAMILIES) spans.add(FormatSpan(s, e, "font_family", raw))
                }
            }
            if (spans.size >= MAX_SPANS) break
        }
        return foldBoldWithItalic(spans).take(MAX_SPANS)
    }

    /**
     * `_fold_bold_with_italic`: where bold and italic cover the same text,
     * one bold_italic span there instead -- and the list re-sorted only when
     * something did overlap, exactly as the desktop does, since the order
     * decides which of two colours on one letter wins.
     */
    private fun foldBoldWithItalic(spans: List<FormatSpan>): List<FormatSpan> {
        data class Mark(val at: Int, val style: String, val step: Int)
        val marks = spans.filter { it.style == "bold" || it.style == "italic" }
            .flatMap { listOf(Mark(it.start, it.style, 1), Mark(it.end, it.style, -1)) }
            .sortedWith(compareBy<Mark>({ it.at }, { it.style }, { it.step }))
        var bold = 0
        var italic = 0
        val pieces = ArrayList<FormatSpan>()
        var overlap = false
        var i = 0
        while (i < marks.size) {
            val at = marks[i].at
            while (i < marks.size && marks[i].at == at) {
                if (marks[i].style == "bold") bold += marks[i].step else italic += marks[i].step
                i++
            }
            if (i == marks.size) break
            val style = when {
                bold != 0 && italic != 0 -> "bold_italic"
                bold != 0 -> "bold"
                italic != 0 -> "italic"
                else -> null
            }
            overlap = overlap || style == "bold_italic"
            if (style == null) continue
            val last = pieces.lastOrNull()
            if (last != null && last.end == at && last.style == style) {
                pieces[pieces.size - 1] = last.copy(end = marks[i].at)
            } else {
                pieces.add(FormatSpan(at, marks[i].at, style))
            }
        }
        if (!overlap) return spans
        return (spans.filter { it.style != "bold" && it.style != "italic" } + pieces)
            .sortedWith(compareBy<FormatSpan>({ it.start }, { it.end }, { it.style }))
    }

    /** One line's paragraph properties: a list kind, an indent, an alignment. */
    data class Paragraph(val line: Int, val kind: String?, val indent: Int, val align: String?)

    /** `normalize_paragraphs`: only usable entries, one per line, in line order. */
    fun normalizeParagraphs(value: Any?, lineCount: Int?): List<Paragraph> {
        val array = value as? JSONArray ?: return emptyList()
        val clean = ArrayList<Paragraph>()
        val seen = HashSet<Long>()
        for (i in 0 until array.length()) {
            val item = array.opt(i) as? JSONObject ?: continue
            // Python's bool is an int: a line of true is line 1.
            val line = when (val raw = item.opt("line")) {
                is Boolean -> if (raw) 1L else 0L
                else -> whole(raw)
            } ?: continue
            if (line < 0) continue
            if (lineCount != null && line >= lineCount) continue
            if (line in seen) continue
            val kind = (item.opt("kind") as? String)?.takeIf { it in LIST_KINDS }
            val align = (item.opt("align") as? String)?.takeIf { it in ALIGNMENTS } ?: "left"
            val indent = (whole(item.opt("indent"))?.takeIf { it >= 0 } ?: 0L).coerceAtMost(MAX_INDENT).toInt()
            if (kind == null && align == "left" && indent == 0) continue
            seen.add(line)
            clean.add(Paragraph(clampToInt(line), kind, indent, align.takeIf { it != "left" }))
        }
        return clean.sortedBy { it.line }
    }

    // ---- text as Python counts it ----

    private fun codePoints(text: String): IntArray = text.codePoints().toArray()

    /** `text[from:to]`, clamped as Python's slice is. */
    private fun slice(cps: IntArray, from: Int, to: Int = cps.size): String {
        val a = from.coerceIn(0, cps.size)
        val b = to.coerceIn(a, cps.size)
        return String(cps, a, b - a)
    }

    /** `html.escape`, quotes included. */
    fun escape(text: String): String {
        val out = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#x27;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** What Python's "replace" puts for a character it cannot encode. */
    val REPLACEMENT = Char(0xFFFD)

    /** `mend_surrogates`: a half of an emoji alone becomes U+FFFD rather than a broken file. */
    fun mend(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (Character.isHighSurrogate(ch) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                out.append(ch).append(text[i + 1])
                i += 2
                continue
            }
            out.append(if (Character.isSurrogate(ch)) REPLACEMENT else ch)
            i++
        }
        return out.toString()
    }

    // ---- the desktop's table helpers, on code points ----

    private fun lstripCount(cps: IntArray, from: Int, to: Int, spaceOnly: Boolean = false): Int {
        var n = 0
        while (from + n < to && (if (spaceOnly) cps[from + n] == ' '.code else PyText.isSpace(cps[from + n]))) n++
        return n
    }

    private fun rstripCount(cps: IntArray, from: Int, to: Int, spaceOnly: Boolean = false): Int {
        var n = 0
        while (to - n > from && (if (spaceOnly) cps[to - n - 1] == ' '.code else PyText.isSpace(cps[to - n - 1]))) n++
        return n
    }

    /** `cell_bounds`: where each cell's text sits in its row, padding left out. */
    private fun cellBounds(cps: IntArray): List<Pair<Int, Int>> {
        val lead = lstripCount(cps, 0, cps.size)
        val trail = rstripCount(cps, lead, cps.size)
        val bodyEnd = cps.size - trail
        if (bodyEnd - lead < 1 || cps[lead] != '|'.code || cps[bodyEnd - 1] != '|'.code) return emptyList()
        // The parts between the pipes of the stripped body: split("|")[1:-1].
        val pipes = (lead until bodyEnd).filter { cps[it] == '|'.code }
        val bounds = ArrayList<Pair<Int, Int>>()
        for (k in 0 until pipes.size - 1) {
            val partFrom = pipes[k] + 1
            val partTo = pipes[k + 1]
            val left = lstripCount(cps, partFrom, partTo)
            val textLength = (partTo - partFrom) - left - rstripCount(cps, partFrom + left, partTo)
            val start = if (textLength > 0) partFrom + left else partFrom + minOf(1, partTo - partFrom)
            bounds.add(start to start + textLength)
        }
        return bounds
    }

    /** `pipe_positions`: every pipe in a row, as positions in it. */
    private fun pipePositions(line: String, cps: IntArray): List<Int> =
        if (!isTableRow(line)) emptyList() else cps.indices.filter { cps[it] == '|'.code }

    /** `cell_alignments`: read off the padding, as the desktop stores alignment. */
    private fun cellAlignments(line: String, cps: IntArray): List<String> {
        val pipes = pipePositions(line, cps)
        val aligns = ArrayList<String>()
        for (i in 0 until pipes.size - 1) {
            val from = pipes[i] + 1
            val to = pipes[i + 1]
            val stripped = (to - from) - lstripCount(cps, from, to)
            if (stripped <= 0) {
                aligns.add("left")
                continue
            }
            val lead = lstripCount(cps, from, to, spaceOnly = true)
            val trail = rstripCount(cps, from, to, spaceOnly = true)
            aligns.add(when {
                lead >= 2 && trail >= 2 -> "center"
                lead >= 2 -> "right"
                else -> "left"
            })
        }
        return aligns
    }

    /** `separator_index`: the first separator in a block's first two rows. */
    private fun separatorIndex(block: List<String>): Int? =
        block.take(2).indexOfFirst { isSeparatorRow(it) }.takeIf { it >= 0 }

    // ---- note_to_html ----

    private val TAG_FOR = mapOf("bold" to "b", "italic" to "i", "underline" to "u", "strike" to "s")
    private val CSS_FOR = mapOf("text_color" to "color:%s;", "highlight" to "background-color:%s;",
                                "font_size" to "font-size:%spt;", "font_family" to "font-family:'%s';")
    private val FONT_NAME_KEEPS = Regex("[^\\p{L}\\p{N}_ .\\-]")

    /** Every style on one character: its on/off styles, and its valued ones sorted by name. */
    private data class StylesAt(val plain: Set<String>, val colours: List<Pair<String, String>>)

    private fun stylesAt(spans: List<FormatSpan>, index: Int): StylesAt {
        val plain = HashSet<String>()
        val colours = HashMap<String, String>()
        for (span in spans) {
            if (span.start <= index && index < span.end) {
                when (span.style) {
                    "bold_italic" -> { plain.add("bold"); plain.add("italic") }
                    in TAG_FOR -> plain.add(span.style)
                    in CSS_FOR -> colours[span.style] = span.value
                }
            }
        }
        return StylesAt(plain, colours.entries.map { it.key to it.value }.sortedBy { it.first })
    }

    /** One run of identically styled text, as markup. */
    private fun wrap(chunk: String, styles: StylesAt): String {
        var out = escape(chunk)
        for (style in styles.plain.sorted()) {
            val tag = TAG_FOR.getValue(style)
            out = "<$tag>$out</$tag>"
        }
        val css = styles.colours.joinToString("") { (key, value) ->
            CSS_FOR.getValue(key).format(if (key == "font_family") FONT_NAME_KEEPS.replace(value, "") else value)
        }
        return if (css.isNotEmpty()) "<span style=\"$css\">$out</span>" else out
    }

    /** One line's text with its character styles, worked out only where a span starts or ends. */
    private fun lineHtml(line: IntArray, spans: List<FormatSpan>, offset: Int): String {
        if (line.isEmpty()) return ""
        val end = offset + line.size
        val cuts = sortedSetOf(0, line.size)
        for (span in spans) {
            for (at in intArrayOf(span.start, span.end)) {
                if (offset < at && at < end) cuts.add(at - offset)
            }
        }
        val points = cuts.toList()
        val pieces = StringBuilder()
        var runStart = 0
        var current: StylesAt? = stylesAt(spans, offset)
        for (i in points.drop(1)) {
            val here = if (i < line.size) stylesAt(spans, offset + i) else null
            if (here != current) {
                pieces.append(wrap(slice(line, runStart, i), current!!))
                runStart = i
                current = here
            }
        }
        return pieces.toString()
    }

    /** Each line's spans -- the ones that reach into it -- in one pass. */
    private fun spansByLine(spans: List<FormatSpan>, lines: List<IntArray>): List<List<FormatSpan>> {
        val starts = IntArray(lines.size)
        var at = 0
        for ((n, line) in lines.withIndex()) {
            starts[n] = at
            at += line.size + 1
        }
        val byLine = List(lines.size) { ArrayList<FormatSpan>() }
        for (span in spans) {
            // bisect_right(starts, span.start) - 1, never below 0.
            var lo = 0
            var hi = starts.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (span.start < starts[mid]) hi = mid else lo = mid + 1
            }
            var n = maxOf(0, lo - 1)
            while (n < lines.size && starts[n] < span.end) {
                byLine[n].add(span)
                n++
            }
        }
        return byLine
    }

    /** One table cell's inner markup, its `<br>` markers turned into real breaks. */
    private fun cellHtml(line: IntArray, spans: List<FormatSpan>, offset: Int, start: Int, end: Int): String {
        val value = line.copyOfRange(start.coerceIn(0, line.size), end.coerceIn(start.coerceIn(0, line.size), line.size))
        val marker = codePoints(LINE_BREAK)
        val parts = ArrayList<Pair<Int, Int>>()
        var pos = 0
        while (true) {
            val cut = indexOf(value, marker, pos)
            if (cut < 0) {
                parts.add(pos to value.size)
                break
            }
            parts.add(pos to cut)
            pos = cut + marker.size
        }
        return parts.joinToString("<br>") { (a, b) ->
            lineHtml(value.copyOfRange(a, b), spans, offset + start + a)
        }
    }

    private fun indexOf(hay: IntArray, needle: IntArray, from: Int): Int {
        var i = from
        while (i + needle.size <= hay.size) {
            var j = 0
            while (j < needle.size && hay[i + j] == needle[j]) j++
            if (j == needle.size) return i
            i++
        }
        return -1
    }

    private val NUMBER_MARKER = Regex("^\\p{Nd}+\\. ")

    /** `note_to_html`: a note as a fragment other programs read. */
    fun noteToHtml(text: String, formatting: Any? = null, paragraphs: Any? = null): String {
        val lineTexts = text.split("\n")
        val lines = lineTexts.map { codePoints(it) }
        val lineSpans = spansByLine(normalizeFormatting(formatting), lines)
        val props = normalizeParagraphs(paragraphs, lines.size).associateBy { it.line }

        // Merge markers only make sense against their whole block.
        val mergeAt = HashMap<Int, Pair<Map<Int, Pair<Int, Int>>, Set<Int>>>()
        val separatorLines = HashSet<Int>()
        val hiddenAt = HashMap<Int, Set<Int>>()
        for (block in tableBlocks(text)) {
            val first = block.first
            val blockLines = lineTexts.subList(block.first, block.last + 1)
            val sepAt = separatorIndex(blockLines)
            var hidden = emptySet<Int>()
            if (sepAt != null) {
                separatorLines.add(first + sepAt)
                hidden = parseSeparator(blockLines[sepAt]).withIndex().filter { it.value.hidden }.map { it.index }.toSet()
            }
            val dataLines = (block.first..block.last).filter { it - first != sepAt }
            val matrix = dataLines.map { splitRow(lineTexts[it]) }
            val anchors = mergeAnchors(matrix)
            val spans = LinkedHashMap<Pair<Int, Int>, Pair<Int, Int>>()
            for ((covered, anchor) in anchors) {
                val (rs, cs) = spans[anchor] ?: (1 to 1)
                spans[anchor] = maxOf(rs, covered.first - anchor.first + 1) to maxOf(cs, covered.second - anchor.second + 1)
            }
            for ((r, n) in dataLines.withIndex()) {
                mergeAt[n] = spans.filterKeys { it.first == r }.mapKeys { it.key.second } to
                    anchors.keys.filter { it.first == r }.map { it.second }.toSet()
                hiddenAt[n] = hidden
            }
        }

        val out = ArrayList<String>()
        val openLists = ArrayList<Pair<String, Boolean>>()
        var offset = 0
        var inTable = false
        var headerRow = false
        for ((number, line) in lines.withIndex()) {
            val lineText = lineTexts[number]
            val spans = lineSpans[number]
            val entry = props[number]
            val kind = entry?.kind
            val indent = entry?.indent ?: 0
            var body = line
            var bodyOffset = offset
            if (kind != null) {
                var marker = ""
                for (glyph in BULLET_GLYPHS) {
                    if (lineText.startsWith("$glyph ")) {
                        marker = "$glyph "
                        break
                    }
                }
                if (marker.isEmpty()) marker = NUMBER_MARKER.find(lineText)?.value ?: ""
                val length = marker.codePointCount(0, marker.length)
                body = line.copyOfRange(length, line.size)
                bodyOffset = offset + length
            }

            val target = if (kind != null) indent + 1 else 0
            // Too many levels open, or the level about to be written is the
            // wrong kind: a number after a bullet at one depth is a new list.
            while (openLists.isNotEmpty() && (openLists.size > target ||
                    (kind != null && openLists.size == target && openLists.last().first != kind))) {
                val (closed, nested) = openLists.removeAt(openLists.size - 1)
                out.add(if (closed == "bullet") "</ul>" else "</ol>")
                if (nested) out.add("</li>")
            }
            val row = if (kind != null) null else if (isTableRow(lineText)) cellBounds(line) else null
            if (number in separatorLines) {
                offset += line.size + 1
                continue
            }
            if (inTable && row == null) {
                out.add("</table>")
                inTable = false
            }
            if (kind != null) {
                while (openLists.size < target) {
                    // A sub-list belongs inside the item it hangs off.
                    val nested = out.isNotEmpty() && out.last().endsWith("</li>")
                    if (nested) out[out.size - 1] = out.last().dropLast("</li>".length)
                    openLists.add(kind to nested)
                    out.add(if (kind == "bullet") "<ul>" else "<ol>")
                }
                out.add("<li>${lineHtml(body, spans, bodyOffset)}</li>")
            } else if (row != null) {
                if (!inTable) {
                    out.add(when (entry?.align) {
                        "center" -> "<table align=\"center\" style=\"margin:0.5em auto;\">"
                        "right" -> "<table align=\"right\" style=\"float:none; margin:0.5em 0 0.5em auto;\">"
                        else -> "<table>"
                    })
                    inTable = true
                    headerRow = true
                } else {
                    headerRow = false
                }
                val (rowSpans, rowCovered) = mergeAt[number] ?: (emptyMap<Int, Pair<Int, Int>>() to emptySet())
                val rowHidden = hiddenAt[number] ?: emptySet()
                val rowAligns = cellAlignments(lineText, line)
                val cells = StringBuilder()
                for ((c, bounds) in row.withIndex()) {
                    val (start, end) = bounds
                    if (c in rowCovered || c in rowHidden) continue
                    val (rs, colSpan) = rowSpans[c] ?: (1 to 1)
                    // A merge across a hidden column narrows by what the page will not show.
                    val cs = colSpan - rowHidden.count { it > c && it < c + colSpan }
                    var attr = ""
                    if (cs > 1) attr += " colspan=\"$cs\""
                    if (rs > 1) attr += " rowspan=\"$rs\""
                    val state = checkboxState(slice(line, start, end))
                    val styles = StringBuilder()
                    if (state != null) {
                        styles.append("text-align:center;")
                    } else if (c < rowAligns.size && rowAligns[c] != "left") {
                        styles.append("text-align:${rowAligns[c]};")
                    }
                    // A highlight over the whole cell colours the cell itself;
                    // an empty cell's highlight rides its padding.
                    var cellA = offset + start
                    var cellB = offset + end
                    if (cellB == cellA) {
                        val pipes = pipePositions(lineText, line)
                        if (c + 1 < pipes.size) {
                            cellA = offset + pipes[c] + 1
                            cellB = offset + pipes[c + 1]
                        }
                    }
                    if (cellB > cellA) {
                        spans.firstOrNull { it.style == "highlight" && it.start <= cellA && it.end >= cellB }
                            ?.let { styles.append("background-color:${it.value};") }
                    }
                    if (styles.isNotEmpty()) attr += " style=\"$styles\""
                    val tag = if (headerRow) "th" else "td"
                    val inner = if (state != null) {
                        val glyph = if (state) "☑" else "☐"
                        spans.firstOrNull { it.style == "text_color" && it.start <= offset + start && it.end >= offset + end }
                            ?.let { "<span style=\"color:${it.value};\">$glyph</span>" } ?: glyph
                    } else {
                        cellHtml(line, spans, offset, start, end)
                    }
                    cells.append("<$tag$attr>$inner</$tag>")
                }
                out.add("<tr>$cells</tr>")
            } else {
                val style = entry?.align?.let { " style=\"text-align:$it;\"" } ?: ""
                out.add("<div$style>${lineHtml(body, spans, bodyOffset).ifEmpty { "<br>" }}</div>")
            }
            offset += line.size + 1
        }
        if (inTable) out.add("</table>")
        while (openLists.isNotEmpty()) {
            val (closed, nested) = openLists.removeAt(openLists.size - 1)
            out.add(if (closed == "bullet") "</ul>" else "</ol>")
            if (nested) out.add("</li>")
        }
        return out.joinToString("")
    }

    /** `html_document`: the standalone file around one note's fragment. */
    fun htmlDocument(title: String, bodyHtml: String): String {
        val safeTitle = escape(title.ifEmpty { "Untitled note" })
        return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n" +
            "<title>$safeTitle</title>\n" +
            "<style>\n" +
            "body { font-family: 'Segoe UI', Arial, sans-serif; " +
            "max-width: 800px; margin: 2em auto; padding: 0 1em; " +
            "line-height: 1.5; }\n" +
            "* { -webkit-print-color-adjust: exact; " +
            "print-color-adjust: exact; }\n" +
            "table { border-collapse: collapse; margin: 0.5em 0; " +
            "max-width: 100%; }\n" +
            "th, td { border: 1px solid #999; padding: 4px 10px; " +
            "overflow-wrap: anywhere; }\n" +
            "th { text-align: left; }\n" +
            "</style>\n</head>\n<body>\n" +
            "<div style=\"font-size: 1.7em; font-weight: bold; " +
            "margin: 0 0 0.6em 0;\">$safeTitle</div>\n" +
            "$bodyHtml\n</body>\n</html>\n"
    }

    // ---- the note as a file ----

    /** Python's truth of a stored value. */
    private fun truthy(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotEmpty()
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    /** The title a file is given and shows: "Untitled note" for none. */
    fun titleOf(note: JSONObject): String =
        PyText.strip(note.opt("title") as? String ?: "").ifEmpty { "Untitled note" }

    private val FORBIDDEN_IN_NAME = Regex("[<>:\"/\\\\|?*\\n\\r\\t]")

    /** The file name offered: what Windows forbids replaced, at most 60 characters, "note" for none. */
    fun fileName(title: String, format: String): String {
        val safe = FORBIDDEN_IN_NAME.replace(title, "_").trim(' ', '.', '_')
        val cps = codePoints(safe)
        return slice(cps, 0, 60).ifEmpty { "note" } + extension(format)
    }

    /** `_note_export_content`: the whole file, in one format. */
    fun content(note: JSONObject, format: String, title: String): String {
        val body = note.opt("body") as? String ?: ""
        val comments = PyText.strip(note.opt("comments") as? String ?: "")
        val checklist = (note.opt("checklist") as? JSONArray)?.let { list ->
            (0 until list.length()).mapNotNull { list.opt(it) as? JSONObject }
        } ?: emptyList()
        val text = if (format == HTML) {
            var fragment = noteToHtml(body, note.opt("formatting"), note.opt("paragraphs"))
            if (checklist.isNotEmpty()) {
                // Glyphs, not form controls: a character renders everywhere.
                val items = checklist.joinToString("\n") {
                    "<div>${if (truthy(it.opt("done"))) "☑" else "☐"} ${escape(it.opt("text") as? String ?: "")}</div>"
                }
                fragment += "<hr>\n<div><i>Checklist</i></div>\n$items"
            }
            if (comments.isNotEmpty()) fragment += "<hr>\n<div><i>Comments</i></div>\n" + noteToHtml(comments)
            htmlDocument(title, fragment)
        } else {
            var plain = "$title\n\n$body"
            if (checklist.isNotEmpty()) {
                plain += "\n\nChecklist:\n" + checklist.joinToString("\n") {
                    "[${if (truthy(it.opt("done"))) "x" else " "}] ${it.opt("text") as? String ?: ""}"
                }
            }
            if (comments.isNotEmpty()) plain += "\n\nComments:\n$comments"
            "$plain\n"
        }
        return mend(text)
    }
}
