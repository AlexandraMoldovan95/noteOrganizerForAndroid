package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pasting from another app with its formatting: HTML in; a note's text,
 * styles and lines out.
 *
 * This is the desktop's `html_to_note` (core/richtext.py) and the
 * `_Reader` behind it, ported line for line. The reader runs on Python's
 * `html.parser`, and what it hands the reader -- which text arrives in
 * which piece, what a broken tag turns into, how `&amp;` and its 2,231
 * relatives are read -- decides the note as much as the reader does. So
 * that parser is here too, the parts of it this reader can reach, as
 * Python 3.13.14 has them; and so is `html.unescape`, reading the same
 * table of named references, written out of Python by
 * `tools/make_html_entities.py`.
 *
 * Everything counts as Python does: lengths and offsets are code points,
 * spaces are Python's spaces (`PyText`), a digit is any script's digit
 * where Python's `int()` and `float()` accept one. `PastingTest` holds the
 * desktop's own answers for a few hundred fragments, and this file has to
 * give every one of them exactly.
 *
 * What the mapping keeps and drops is the desktop's decision, not this
 * file's: bold, italic, underline, strikethrough, a colour that is really
 * a colour, a pale highlight, lists, alignment and tables come in; sizes,
 * typefaces and everything else a page can say are left behind.
 */
object Pasting {

    /** The desktop's caps: a pasted page must not make a note nobody can edit. */
    const val MAX_PASTE_CHARS = 100_000
    const val MAX_PASTE_SPANS = 2_000

    /**
     * What a paste brings: the text, its styles and its lines' properties,
     * through the desktop's own gatekeepers -- and whether the caps left
     * anything out, so the paste can say so instead of cutting quietly.
     */
    data class Pasted(
        val text: String,
        val formatting: List<FormatSpan>,
        val paragraphs: List<Exporting.Paragraph>,
        val textCut: Boolean = false,
        val stylesCut: Boolean = false,
    )

    private val NOTHING = Pasted("", emptyList(), emptyList())

    // ---- the editor's side: what a copy carries, and where a paste lands ----

    /**
     * A copy made from a note: everything it carried -- sizes and faces
     * too, which no HTML brings back in -- kept with the HTML it put on the
     * clipboard, so a paste of that same HTML can bring all of it back.
     */
    data class OwnCopy(val text: String, val spans: List<FormatSpan>, val paragraphs: List<Exporting.Paragraph>, val html: String)

    /**
     * `_copy_formatted`: [from, to) of a body (code points) as text, the
     * styles over it, its lines' properties and the HTML other programs
     * read -- tables with their borders on, for Word.
     */
    fun copyOut(body: String, spans: List<FormatSpan>, props: Map<Int, Paragraphs.Props>, from: Int, to: Int): OwnCopy? {
        val c = cps(body)
        if (from < 0 || to > c.size || to <= from) return null
        val text = String(c, from, to - from)
        val clipped = spans.mapNotNull { s ->
            val lo = maxOf(s.start, from)
            val hi = minOf(s.end, to)
            if (hi > lo) s.copy(start = lo - from, end = hi - from) else null
        }
        val firstLine = (0 until from).count { c[it] == '\n'.code }
        // A copy ending in a line break holds nothing of the line after it,
        // nor one begun at a line's end of that line: counted in, its looks
        // went onto the line a paste landed in (the lows wave's verification,
        // rounds 5 and 6).
        val lineCount = text.count { it == '\n' } + if (text.endsWith("\n")) 0 else 1
        val skipped = if (text.startsWith("\n") && from > 0 && c[from - 1] != '\n'.code) 1 else 0
        val stored = Paragraphs.extract(body, props)
        val lines = JSONArray()
        // The lines the copy holds, as the desktop picks them. Both readers
        // below would drop the others anyway, so a mutant here is equivalent.
        for (i in 0 until stored.length()) {
            val p = stored.getJSONObject(i)
            val n = p.getInt("line") - firstLine
            if (n in skipped until lineCount) lines.put(JSONObject(p.toString()).put("line", n))
        }
        val html = Exporting.noteToHtml(text, Templates.spansJson(clipped), lines, inlineTables = true)
        return OwnCopy(text, clipped, Exporting.normalizeParagraphs(lines, lineCount), html)
    }

    /**
     * What a paste of [html] brings, as `_paste_formatted` decides it: the
     * app's own copy whole when the HTML is the one it wrote (and nothing
     * was cut), a plain paragraph's indent left at home; and a source that
     * sent no formatting at all -- a PDF -- with its column's breaks
     * rejoined. Null when there is nothing to paste.
     */
    fun fromHtml(html: String, mine: OwnCopy?): Pasted? {
        val read = htmlToNote(html)
        val own = mine != null && !read.textCut && !read.stylesCut && html == mine.html
        var text = read.text
        var spans = read.formatting
        var lines = read.paragraphs
        if (own) {
            text = mine!!.text
            spans = mine.spans
            // One word copied from an indented line must not indent the
            // paragraph it is pasted into.
            lines = mine.paragraphs.mapNotNull { p ->
                val kept = if (p.kind == null) p.copy(indent = 0) else p
                kept.takeIf { it.kind != null || it.indent != 0 || it.align != null }
            }
        }
        if (text.isEmpty()) return null
        if (spans.isEmpty() && lines.isEmpty() && !own) text = reflowWrapped(text)
        return Pasted(text, spans, lines, read.textCut, read.stylesCut)
    }

    /** `_paste_plain`'s text: a PDF's column rejoined, then any marker that would make a list taken off. */
    fun plainText(text: String): String = stripListMarkers(reflowWrapped(text))

    /**
     * `_paste_pipe_text_on_own_lines`: plain text whose first or last line
     * is a table row, as it goes in -- or null for text that is not, which
     * pastes as typed. Rows only mean something at a line's start.
     */
    fun pipeRows(raw: String?): String? {
        if (raw.isNullOrEmpty() || '\n' !in PyText.strip(raw)) {
            if (!isRow((raw ?: "").trim('\n'))) return null
        }
        val text = raw!!.replace("\r\n", "\n").replace("\r", "\n").trim('\n')
        val lines = text.split("\n")
        if (!(isRow(lines.first()) || isRow(lines.last()))) return null
        return text
    }

    /** `is_table_row`, with Python's spaces. */
    fun isRow(line: String): Boolean = isTableRow(cps(line))

    /**
     * Words pasted into the comments, which are plain text and draw no
     * table: the table's rows taken out and the prose kept, the blank runs
     * a table leaves collapsed -- the desktop's `_on_comments_paste`. Null
     * when [pasted] holds no row, and goes in as it is.
     */
    fun intoComments(pasted: String): String? {
        val lines = pasted.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        if (lines.none { isRow(it) }) return null
        return Regex("\n{3,}").replace(lines.filterNot { isRow(it) }.joinToString("\n"), "\n\n").trim('\n')
    }

    /**
     * The comments [old] become [new], with what went in at once cleared of
     * table rows by [intoComments]. Null when nothing was taken out. Only
     * rows wholly inside what went in count -- a paste -- so a row typed
     * letter by letter is the person's own and stays, as on the desktop.
     */
    fun commentsAfter(old: String, new: String): String? {
        var front = 0
        while (front < old.length && front < new.length && old[front] == new[front]) front++
        var back = 0
        while (back < old.length - front && back < new.length - front &&
            old[old.length - 1 - back] == new[new.length - 1 - back]) back++
        val went = new.substring(front, new.length - back)
        val kept = intoComments(went) ?: return null
        return new.substring(0, front) + kept + new.substring(new.length - back)
    }

    /**
     * A body after a paste: its text, styles and lines, where the cursor
     * goes, and the edits that made it, each in the text the one before
     * left ([Calculator.follow]): the paste, then each marker renumbered.
     */
    data class Inserted(val body: String, val spans: List<FormatSpan>, val props: Map<Int, Paragraphs.Props>, val caret: Int,
                        val edits: List<Paragraphs.Edit> = emptyList())

    /**
     * [text] put in place of [from, to) of [body] (code points), as the
     * desktop's paste writes it: the selection out; a line break before or
     * after where table rows at its edges would otherwise join the line
     * or the table beside them (`_rows_gap`); the text in, a styled run the
     * cursor stood INSIDE carried over it as a Tk tag is, one it stood at
     * the edge of not; [pastedSpans] laid over -- a highlight never over a
     * line's end, as the desktop lays one -- and [pastedLines] given their
     * lines (an alignment a source put on a table row is not one this
     * person chose, and is left behind); then the lists renumbered.
     */
    fun insert(body: String, spans: List<FormatSpan>, props: Map<Int, Paragraphs.Props>, from: Int, to: Int,
               text: String, pastedSpans: List<FormatSpan> = emptyList(),
               pastedLines: List<Exporting.Paragraph> = emptyList()): Inserted {
        val c = cps(body)
        var a = from.coerceIn(0, c.size)
        var b = to.coerceIn(a, c.size)
        // Over a selection that reaches a list, the paste takes the place
        // typing over it would (Paragraphs.deletionRange): the next item keeps
        // its number, and no marker is left among the words -- "zz" over items
        // 2 and 3 gave "zz4. four".
        if (b > a && text.isNotEmpty()) {
            val starts = ArrayList<Int>().apply { add(0) }
            for (i in c.indices) if (c[i] == '\n'.code) starts.add(i + 1)
            fun pos(x: Int): Paragraphs.Pos {
                val n = starts.indexOfLast { it <= x }
                return Paragraphs.Pos(n, x - starts[n])
            }
            Paragraphs.deletionRange(body.split("\n"), pos(a), pos(b), typing = true, pasted = text)?.let { (s, e) ->
                a = starts[s.line] + s.column
                b = starts[e.line] + e.column
            }
        }
        // With nothing selected, at an item's marker end -- where Enter in a
        // list and a tap on a marker leave the cursor -- a paste bringing its
        // own marker takes that one's place, or, with words after it and a
        // line break of its own, goes in before the item. Kept, a copied item
        // pasted into a new one read "3. 1. Kiwi" (the lows wave's
        // verification, round 8).
        val head = text.substringBefore('\n')
        if (a == b && Paragraphs.parseListMarker(head).kind != null && !isRow(head)) {
            var ls = a
            while (ls > 0 && c[ls - 1] != '\n'.code) ls--
            var le = a
            while (le < c.size && c[le] != '\n'.code) le++
            val item = Paragraphs.parseListMarker(String(c, ls, le - ls))
            if (item.kind != null && a - ls == item.marker.codePointCount(0, item.marker.length)) {
                if (PyText.strip(item.rest).isNotEmpty() && text.endsWith("\n")) b = ls
                a = ls
            }
        }
        val firstLine = (0 until a).count { c[it] == '\n'.code }
        val lastLine = firstLine + (a until b).count { c[it] == '\n'.code }
        val kept = c.copyOfRange(0, a) + c.copyOfRange(b, c.size)
        var moved = Paragraphs.moveSpans(spans, listOf(Paragraphs.Edit(a, b - a, 0)))

        // What the caret's line holds either side of it, and the lines around.
        var lineStart = a
        while (lineStart > 0 && kept[lineStart - 1] != '\n'.code) lineStart--
        var lineEnd = a
        while (lineEnd < kept.size && kept[lineEnd] != '\n'.code) lineEnd++
        val keptLines = String(kept, 0, kept.size).split("\n")
        val before = String(kept, lineStart, a - lineStart)
        val after = String(kept, a, lineEnd - a)
        val above = if (firstLine > 0) keptLines[firstLine - 1] else ""
        val below = keptLines.getOrElse(firstLine + 1) { "" }
        val pasted = text.split("\n")
        // Words pasted at a table row's end or start take a line of their
        // own: the place to write after a note's last table pastes at that
        // row's end, and a paragraph pasted there was glued onto it -- the
        // row read as words, the table one row short (the owner, 2026-10-26).
        val onRow = isRow(before + after)
        val trail = if ((isRow(pasted.last()) && (PyText.strip(after).isNotEmpty() || isRow(below))) ||
                        (onRow && before.isEmpty() && !isRow(pasted.last()))) "\n" else ""
        val lead = if ((isRow(pasted.first()) && (PyText.strip(before).isNotEmpty() || isRow(above))) ||
                       (onRow && after.isEmpty() && !isRow(pasted.first()))) "\n" else ""

        val whole = cps(lead + text + trail)
        val newBody = String(kept, 0, a) + String(whole, 0, whole.size) + String(kept, a, kept.size - a)
        moved = Paragraphs.moveSpans(moved, listOf(Paragraphs.Edit(a, 0, whole.size)))
        val at = a + lead.length
        val bodyCps = cps(newBody)
        for (s in pastedSpans) {
            var start = s.start + at
            val end = s.end + at
            if (s.style == "highlight") {
                // Line by line, never over a line's end: a highlight that
                // covers one paints a bar to the window's edge on the desktop.
                for (k in start until end) {
                    if (bodyCps[k] == '\n'.code) {
                        if (k > start) moved = setStyle(moved, start, k, s.style, s.value)
                        start = k + 1
                    }
                }
                if (end > start) moved = setStyle(moved, start, end, s.style, s.value)
            } else {
                moved = setStyle(moved, start, end, s.style, s.value)
            }
        }

        // The lines: those after the paste move down by the lines it brought,
        // those the selection took away are gone, and the caret's keeps its own.
        // Words a paste with a line break pushes down keep their line's looks,
        // as the desktop's text carries its tags: left on the line the paste
        // began, a line pasted before a nested item flattened it.
        val added = whole.count { it == '\n'.code }
        val pushed = added > 0 && after.isNotEmpty()
        val lines = HashMap<Int, Paragraphs.Props>()
        for ((n, p) in props) {
            when {
                n < firstLine -> lines[n] = p
                n == firstLine -> if (before.isNotEmpty() || !pushed) lines[n] = p
                n <= lastLine -> Unit
                else -> lines[n - (lastLine - firstLine) + added] = p
            }
        }
        if (pushed) props[lastLine]?.let { lines[firstLine + added] = it }
        val texts = newBody.split("\n")
        val base = firstLine + if (lead.isNotEmpty()) 1 else 0
        for (p in pastedLines) {
            val n = p.line + base
            if (n >= texts.size) continue
            var align = p.align ?: "left"
            if (align != "left" && isRow(texts[n])) align = "left"
            if (align != "left") lines[n] = (lines[n] ?: Paragraphs.Props()).copy(align = align)
            if (p.kind != null && Paragraphs.parseListMarker(texts[n]).kind != p.kind) continue
            if (p.kind == null && p.indent == 0) continue
            lines[n] = (lines[n] ?: Paragraphs.Props()).copy(indent = p.indent)
        }
        // A bullet pasted as words, with no depth of its own, takes the one
        // its glyph says -- "◦ " one in, "▪ " two -- as the desktop's
        // _paragraph_indent_of reads a line with no margin: pasted plain,
        // "◦ b" sat at the margin here. Only lines the paste began: a line
        // already in the note keeps the depth it was given.
        val first = base + if (lead.isEmpty() && before.isNotEmpty()) 1 else 0
        for (n in first..minOf(firstLine + added, texts.size - 1)) {
            if ((lines[n]?.indent ?: 0) != 0) continue
            val marker = Paragraphs.parseListMarker(texts[n])
            if (marker.kind != "bullet") continue
            val level = Paragraphs.BULLET_GLYPHS.indexOf(marker.marker.trim())
            if (level > 0) lines[n] = (lines[n] ?: Paragraphs.Props()).copy(indent = level)
        }
        // A pasted list starts at one; renumbering makes it continue the one it joined.
        val renumbered = Paragraphs.renumber(newBody, lines.filterValues { !it.plain })
        return Inserted(renumbered.body, Paragraphs.moveSpans(moved, renumbered.edits), renumbered.props,
                        Paragraphs.moveStart(a + whole.size, renumbered.edits),
                        listOf(Paragraphs.Edit(a, b - a, whole.size)) + renumbered.edits.sortedByDescending { it.at })
    }

    /** `html_to_note`. */
    fun htmlToNote(fragment: String?): Pasted {
        val source = fragment ?: ""
        // Looked for in the source rather than during parsing: a block tag
        // late in the fragment still means the earlier newlines were markup.
        val reader = Reader(keepNewlines = !hasBlockTag(source))
        try {
            Parser(source, reader).run()
        } catch (e: Exception) {
            // A pasted page is arbitrary input, and a failed paste must never
            // take the editor with it. Python fails here too, on the same
            // inputs: a number of more than 4,300 digits is a ValueError.
            return NOTHING
        }
        for (open in reader.open.toList()) reader.close(open.style)

        val unframed = unframeLoneCells(reader.text.joinToString(""), reader.loneCells)
        val joined = unframed.text
        val text = joined.trim('\n')
        val dropped = joined.length - joined.trimStart('\n').length
        val length = text.codePointCount(0, text.length)
        for (i in 0 until reader.paragraphs.length()) {
            val p = reader.paragraphs.getJSONObject(i)
            p.put("line", unframed.lineMoved(p.getInt("line")))
        }
        var spans = reader.spans
            .map { it.copy(start = maxOf(0, unframed.moved(it.start) - dropped),
                           end = maxOf(0, unframed.moved(it.end) - dropped)) }
            .filter { it.end > it.start && it.start < length }
            .map { it.copy(end = minOf(it.end, length)) }
        var out = text
        // A pasted table arrives with cells of whatever width they had;
        // padding them here keeps the offsets and the text in step, and
        // align() reports what it moved so the spans move with it.
        if (findBlocks(text.split("\n").map(::cps)).isNotEmpty()) {
            // A page's own markdown brings its separator's dashes as whoever
            // typed them; the app's own copy is put back whole by [fromHtml].
            val (forgot, forgotten) = forgetWidths(text)
            spans = shiftSpans(spans, forgotten)
            val (aligned, shifts) = align(forgot)
            out = aligned
            spans = shiftSpans(spans, shifts)
        }
        // The ceiling holds for what lands, padding and all: the cells' own
        // spaces a ragged table keeps can still take a paste past it, so
        // whole lines are kept up to it, and the paste says it was cut.
        var cut = reader.textCut
        if (out.codePointCount(0, out.length) > MAX_PASTE_CHARS) {
            val limit = out.offsetByCodePoints(0, MAX_PASTE_CHARS)
            out = out.substring(0, out.lastIndexOf('\n', limit).let { if (it < 0) limit else it })
            val kept = out.codePointCount(0, out.length)
            spans = spans.filter { it.start < kept }.map { it.copy(end = minOf(it.end, kept)) }
            cut = true
        }
        val json = JSONArray()
        for (s in spans) {
            val o = JSONObject().put("start", s.start).put("end", s.end).put("style", s.style)
            if (s.value != null) o.put("value", s.value)
            json.put(o)
        }
        return Pasted(
            out,
            Exporting.normalizeFormatting(json),
            Exporting.normalizeParagraphs(reader.paragraphs, out.split("\n").size),
            cut,
            reader.stylesCut,
        )
    }

    /** What [unframeLoneCells] made: the text, and where an offset or a line of the old one now falls. */
    private class Unframed(val text: String, private val cuts: List<IntArray>, private val breaks: IntArray) {
        private val starts = IntArray(cuts.size) { cuts[it][0] }
        private val before = IntArray(cuts.size + 1).also { b ->
            for (i in cuts.indices) b[i + 1] = b[i] + cuts[i][1] - cuts[i][0]
        }

        fun moved(pos: Int): Int {
            val i = lowerBound(starts, pos)
            return if (i == 0) pos else pos - before[i - 1] - (minOf(pos, cuts[i - 1][1]) - starts[i - 1])
        }

        fun lineMoved(n: Int): Int = n + lowerBound(breaks, n)

        /** `bisect_left`: how many of the sorted [values] are below [key]. */
        private fun lowerBound(values: IntArray, key: Int): Int {
            var lo = 0
            var hi = values.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] < key) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    /**
     * `_unframe_lone_cells`: each row of one cell -- [rows], the code-point
     * (start, end) of its "| ... |" -- written as the cell's words alone, its
     * line breaks real lines. A row of one cell is no table here: left
     * framed, one cell or one column copied from a page pasted as "| rent |".
     * Offsets are looked up by halving: a long styled column has a cut for
     * every row, and walking them all for every span froze the paste.
     */
    private fun unframeLoneCells(text: String, rows: List<IntArray>): Unframed {
        val cp = cps(text)
        val cuts = ArrayList<IntArray>()
        val breaks = ArrayList<Int>()
        val swaps = ArrayList<Int>()
        val br = cps(LINE_BREAK)
        var line = 0
        var seen = 0
        for (row in rows) {
            val start = row[0]
            val end = row[1]
            if (end - start < 2 || end > cp.size || cp[start] != PIPE || cp[end - 1] != PIPE) continue
            for (i in seen until start) if (cp[i] == '\n'.code) line += 1
            seen = start
            var first = start + 1
            while (first < end - 1 && cp[first] == ' '.code) first += 1
            var last = end - 1
            while (last > first && cp[last - 1] == ' '.code) last -= 1
            if (first >= last) {
                cuts.add(intArrayOf(start, end))
                continue
            }
            cuts.add(intArrayOf(start, first))
            var at = first
            while (at + br.size <= last) {
                if ((br.indices).all { cp[at + it] == br[it] }) {
                    swaps.add(at)
                    cuts.add(intArrayOf(at + 1, at + br.size))
                    breaks.add(line)
                    at += br.size
                } else {
                    at += 1
                }
            }
            cuts.add(intArrayOf(last, end))
        }
        for (at in swaps) cp[at] = '\n'.code
        val out = StringBuilder(text.length)
        var at = 0
        for (cut in cuts) {
            for (i in at until cut[0]) out.appendCodePoint(cp[i])
            at = cut[1]
        }
        for (i in at until cp.size) out.appendCodePoint(cp[i])
        return Unframed(out.toString(), cuts, breaks.toIntArray())
    }

    // ---- the tags ----

    private val VOID_TAGS = setOf("br", "hr", "img", "input", "area", "base", "col", "embed",
        "source", "track", "wbr", "meta", "link")
    private val BLOCK_TAGS = setOf("p", "div", "br", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "pre", "section", "article")
    private val CELL_TAGS = setOf("td", "th")
    private val STYLE_TAGS = mapOf(
        "b" to "bold", "strong" to "bold",
        "i" to "italic", "em" to "italic",
        "u" to "underline", "ins" to "underline",
        "s" to "strike", "strike" to "strike", "del" to "strike",
    )

    private const val CELL = "|"
    private const val LINE_BREAK = "<br>"
    private const val MERGE_LEFT = "<"
    private const val MERGE_UP = "^"
    private const val CHECKBOX_EMPTY = "[ ]"
    private const val CHECKBOX_DONE = "[x]"

    /**
     * `_BLOCK_TAG_RE.search`: "<", spaces, an optional "/", spaces, one of
     * the block tags' names and a word's end, ignoring case. Spelled out
     * rather than handed to a regex engine, because the
     * phone's engine is not the computer's and neither is Python's: the
     * three letters Python also matches -- İ and ı for i, the Kelvin sign
     * for k, the long ſ for s -- are named here, and `\b` is Python's, a
     * letter or digit of any script or "_" (both measured over all of
     * Unicode against Python 3.13).
     */
    private val BLOCK_NAMES = (BLOCK_TAGS + setOf("li", "ul", "ol", "table")).sorted()

    private fun hasBlockTag(s: String): Boolean {
        var lt = s.indexOf('<')
        while (lt >= 0) {
            var q = skipSpaces(s, lt + 1)
            if (q < s.length && s[q] == '/') q = skipSpaces(s, q + 1)
            for (name in BLOCK_NAMES) {
                if (tagNameAt(s, q, name)) return true
            }
            lt = s.indexOf('<', lt + 1)
        }
        return false
    }

    private fun skipSpaces(s: String, from: Int): Int {
        var i = from
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (!PyText.isSpace(cp)) break
            i += Character.charCount(cp)
        }
        return i
    }

    private fun tagNameAt(s: String, at: Int, name: String): Boolean {
        if (at + name.length > s.length) return false
        for (k in name.indices) {
            val c = s[at + k]
            val want = name[k]
            val same = c == want || c == want.uppercaseChar() ||
                // By number: the Kelvin sign and a K look the same on a page.
                (want == 'i' && (c.code == 0x130 || c.code == 0x131)) ||
                (want == 'k' && c.code == 0x212A) ||
                (want == 's' && c.code == 0x17F)
            if (!same) return false
        }
        val end = at + name.length
        return end == s.length || !isWordChar(s.codePointAt(end))
    }

    private fun isWordChar(cp: Int): Boolean = cp == '_'.code || when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    // ---- Python's html.parser, as far as this reader reaches into it ----

    private val CDATA_CONTENT = setOf("script", "style", "xmp", "iframe", "noembed", "noframes")
    private val RCDATA_CONTENT = setOf("textarea", "title")

    private fun isTagSpace(c: Char) = c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == ' '
    private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
    private fun asciiLower(c: Char) = if (c in 'A'..'Z') c + 32 else c

    /**
     * `HTMLParser.feed` and `close`, with `convert_charrefs` on, as the
     * reader is made. One pass that treats the end of the text as the end:
     * `feed` stops at anything unfinished and `close` takes it up again
     * from there with the same rules, so the pieces the reader is handed
     * are the same either way.
     */
    private class Parser(private val raw: String, private val reader: Reader) {
        private val n = raw.length
        private var cdataElem: String? = null
        private var escapable = true
        private var plaintext = false

        fun run() {
            var i = 0
            while (i < n) {
                val j: Int
                if (cdataElem == null) {
                    val lt = raw.indexOf('<', i)
                    j = if (lt < 0) n else lt
                } else {
                    j = if (plaintext) n else cdataEnd(i)
                    if (j < 0) break
                }
                if (i < j) reader.data(if (escapable) unescape(raw.substring(i, j)) else raw.substring(i, j))
                i = j
                if (i == n) break
                var k = when {
                    i + 1 < n && isAsciiLetter(raw[i + 1]) -> startTag(i)
                    raw.startsWith("</", i) -> endTag(i)
                    raw.startsWith("<!--", i) -> comment(i)
                    raw.startsWith("<?", i) -> raw.indexOf('>', i + 2).let { if (it < 0) -1 else it + 1 }
                    raw.startsWith("<!", i) -> declaration(i)
                    else -> {
                        reader.data("<")
                        i + 1
                    }
                }
                if (k < 0) {
                    // Unfinished at the end of the text: everything from here
                    // is the tag, comment or declaration that never closed.
                    // Only a bare "</" at the very end is said as text.
                    if (!(i + 1 < n && isAsciiLetter(raw[i + 1])) && raw.startsWith("</", i) && i + 2 == n) {
                        reader.data("</")
                    }
                    k = n
                }
                i = k
            }
            if (i < n) reader.data(if (escapable) unescape(raw.substring(i)) else raw.substring(i))
        }

        /** Where `</name` starts, followed by a space, "/" or ">", ASCII case ignored; or -1. */
        private fun cdataEnd(from: Int): Int {
            val name = cdataElem!!
            var lt = raw.indexOf("</", from)
            while (lt >= 0) {
                val after = lt + 2 + name.length
                if (after < n && (isTagSpace(raw[after]) || raw[after] == '/' || raw[after] == '>') &&
                    name.indices.all { asciiLower(raw[lt + 2 + it]) == name[it] }) return lt
                lt = raw.indexOf("</", lt + 1)
            }
            return -1
        }

        private fun setCdata(tag: String, canEscape: Boolean) {
            cdataElem = tag
            escapable = canEscape
            plaintext = tag == "plaintext"
        }

        private fun clearCdata() {
            cdataElem = null
            escapable = true
            plaintext = false
        }

        /** `parse_starttag`. */
        private fun startTag(i: Int): Int {
            val endpos = locateTagEnd(i + 1)
            if (raw[endpos - 1] != '>') return -1
            val (name, afterName) = tagFind(i + 1)
            val tag = name.lowercase()
            val attrs = ArrayList<Pair<String, String?>>()
            var k = afterName
            while (k < endpos) {
                val m = attrFind(k) ?: break
                var value: String? = m.value
                if (m.value == null) {
                    value = null
                } else if (m.value.length >= 2 &&
                    ((m.value.first() == '\'' && m.value.last() == '\'') || (m.value.first() == '"' && m.value.last() == '"'))) {
                    value = m.value.substring(1, m.value.length - 1)
                }
                if (!value.isNullOrEmpty()) value = unescapeAttr(value)
                attrs.add(m.name.lowercase() to value)
                k = m.end
            }
            val end = if (k < endpos) PyText.strip(raw.substring(k, endpos)) else ""
            if (end != ">" && end != "/>") {
                reader.data(raw.substring(i, endpos))
                return endpos
            }
            if (end.endsWith("/>")) {
                reader.startTag(tag, attrs)
                reader.endTag(tag)
            } else {
                reader.startTag(tag, attrs)
                if (tag in CDATA_CONTENT || tag == "plaintext") setCdata(tag, canEscape = false)
                else if (tag in RCDATA_CONTENT) setCdata(tag, canEscape = true)
            }
            return endpos
        }

        /** `parse_endtag`. */
        private fun endTag(i: Int): Int {
            if (raw.indexOf('>', i + 2) < 0) return -1
            if (!(i + 2 < n && isAsciiLetter(raw[i + 2]))) {
                if (i + 2 < n && raw[i + 2] == '>') return i + 3
                return raw.indexOf('>', i + 2) + 1
            }
            val j = locateTagEnd(i + 2)
            if (raw[j - 1] != '>') return -1
            reader.endTag(tagFind(i + 2).first.lowercase())
            clearCdata()
            return j
        }

        /** `parse_comment`: to the first `-->` or `--!>`, or an abrupt `-->` straight away. */
        private fun comment(i: Int): Int {
            var p = raw.indexOf("--", i + 4)
            while (p >= 0) {
                if (p + 2 < n && raw[p + 2] == '>') return p + 3
                if (p + 3 < n && raw[p + 2] == '!' && raw[p + 3] == '>') return p + 4
                p = raw.indexOf("--", p + 1)
            }
            if (i + 4 < n && raw[i + 4] == '>') return i + 5
            if (i + 5 < n && raw[i + 4] == '-' && raw[i + 5] == '>') return i + 6
            return -1
        }

        /** `parse_html_declaration`: a CDATA section, a doctype, or a bogus comment. */
        private fun declaration(i: Int): Int {
            if (raw.startsWith("<![CDATA[", i)) {
                val j = raw.indexOf("]]>", i + 9)
                return if (j < 0) -1 else j + 3
            }
            val doctype = i + 9 <= n && "<!doctype".indices.all { asciiLower(raw[i + it]) == "<!doctype"[it] }
            val gt = raw.indexOf('>', if (doctype) i + 9 else i + 2)
            return if (gt < 0) -1 else gt + 1
        }

        private fun nameChar(c: Char) = !isTagSpace(c) && c != '/' && c != '>'

        /** `tagfind_tolerant`: the name, and where the spaces and stray slashes after it end. */
        private fun tagFind(from: Int): Pair<String, Int> {
            var k = from + 1
            while (k < n && nameChar(raw[k])) k++
            val name = raw.substring(from, k)
            while (k < n && (isTagSpace(raw[k]) || (raw[k] == '/' && !(k + 1 < n && raw[k + 1] == '>')))) k++
            return name to k
        }

        private class Attr(val name: String, val value: String?, val end: Int)

        /** An attribute's name at [k], or -1: it must follow a quote, a space or a slash. */
        private fun attrNameEnd(k: Int): Int {
            if (k <= 0 || k >= n) return -1
            val before = raw[k - 1]
            if (!(before == '\'' || before == '"' || isTagSpace(before) || before == '/')) return -1
            if (!nameChar(raw[k])) return -1
            var m = k + 1
            while (m < n && nameChar(raw[m]) && raw[m] != '=') m++
            return m
        }

        /**
         * The value after a name ending at [m]: `[ws]*=[ws]*` then a quoted
         * value or a bare one. Returns the value's text (quotes included)
         * and where it ends, or null when there is none. An unclosed quote
         * after spaces is the regex backing off one space and reading an
         * empty bare value there -- which is what Python's does.
         */
        private fun valueAt(m: Int): Pair<String, Int>? {
            var p = m
            while (p < n && isTagSpace(raw[p])) p++
            if (p >= n || raw[p] != '=') return null
            val afterEq = p + 1
            var t = afterEq
            while (t < n && isTagSpace(raw[t])) t++
            if (t < n && (raw[t] == '\'' || raw[t] == '"')) {
                val close = raw.indexOf(raw[t], t + 1)
                if (close >= 0) return raw.substring(t, close + 1) to close + 1
                return if (t > afterEq) "" to t - 1 else null
            }
            var e = t
            while (e < n && raw[e] != '>' && !isTagSpace(raw[e])) e++
            return raw.substring(t, e) to e
        }

        /** `attrfind_tolerant`. */
        private fun attrFind(k: Int): Attr? {
            val nameEnd = attrNameEnd(k)
            if (nameEnd < 0) return null
            val value = valueAt(nameEnd)
            var e = value?.second ?: nameEnd
            while (e < n && (isTagSpace(raw[e]) || (raw[e] == '/' && !(e + 1 < n && raw[e + 1] == '>')))) e++
            return Attr(raw.substring(k, nameEnd), value?.first, e)
        }

        /** `locatetagend`: where a tag's markup ends, past its ">" if it has one. */
        private fun locateTagEnd(from: Int): Int {
            var k = from + 1
            while (k < n && nameChar(raw[k])) k++
            while (k < n && (isTagSpace(raw[k]) || raw[k] == '/')) k++
            while (true) {
                val nameEnd = attrNameEnd(k)
                if (nameEnd < 0) break
                k = valueAt(nameEnd)?.second ?: nameEnd
                while (k < n && (isTagSpace(raw[k]) || raw[k] == '/')) k++
            }
            if (k < n && raw[k] == '>') k++
            return k
        }
    }

    // ---- html.unescape ----

    private class Entities(val named: Map<String, String>, val charrefs: Map<Int, String>, val invalid: Set<Int>)

    private val entities: Entities by lazy {
        // From the root, by the file's own path: a name relative to this
        // class is looked up in the class's package, and the release build
        // renames the class into another (R8 made it "ca1", no package).
        // The file was then not found, the parser's guard turned that into
        // "nothing read", and a paste from a web page with an &, a < or an
        // &nbsp; in it -- most of them -- came in as plain words, its
        // formatting silently gone.
        val stream = Pasting::class.java.getResourceAsStream("/ro/noteorganizer/app/html_entities.json")
            ?: error("html_entities.json is missing from the app")
        val json = JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        val html5 = json.getJSONObject("html5")
        val named = HashMap<String, String>(html5.length() * 2)
        for (key in html5.keys()) named[key] = html5.getString(key)
        val refs = json.getJSONObject("invalid_charrefs")
        val charrefs = HashMap<Int, String>()
        for (key in refs.keys()) charrefs[key.toInt()] = refs.getString(key)
        val bad = json.getJSONArray("invalid_codepoints")
        val invalid = HashSet<Int>()
        for (i in 0 until bad.length()) invalid.add(bad.getInt(i))
        Entities(named, charrefs, invalid)
    }

    /** Python's `int()` refuses a number written with more than this many digits. */
    private const val MAX_INT_DIGITS = 4300

    /** Raised where Python raises: the paste then brings nothing, as on the desktop. */
    private class Unreadable : Exception()

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** A number reference's character: `_replace_charref`'s numeric half. */
    private fun charFor(num: Int): String {
        entities.charrefs[num]?.let { return it }
        if (num in 0xD800..0xDFFF || num > 0x10FFFF) return 0xFFFD.toChar().toString()
        if (num in entities.invalid) return ""
        return String(Character.toChars(num))
    }

    /** Digits [from, to) as a number, anything past U+10FFFF read as just past it. */
    private fun number(s: String, from: Int, to: Int, radix: Int): Int {
        if (radix == 10 && to - from > MAX_INT_DIGITS) throw Unreadable()
        var v = 0
        for (k in from until to) {
            v = v * radix + Character.digit(s[k], radix)
            if (v > 0x10FFFF) v = 0x110000
        }
        return v
    }

    /** A numeric reference at [amp] ("&#..."): its character and its end, or null. */
    private fun numericAt(s: String, amp: Int): Pair<String, Int>? {
        val p = amp + 2
        if (p < s.length && s[p] in '0'..'9') {
            var e = p
            while (e < s.length && s[e] in '0'..'9') e++
            val ch = charFor(number(s, p, e, 10))
            return ch to (if (e < s.length && s[e] == ';') e + 1 else e)
        }
        if (p + 1 < s.length && (s[p] == 'x' || s[p] == 'X') && isHex(s[p + 1])) {
            var e = p + 1
            while (e < s.length && isHex(s[e])) e++
            val ch = charFor(number(s, p + 1, e, 16))
            return ch to (if (e < s.length && s[e] == ';') e + 1 else e)
        }
        return null
    }

    /** `html.unescape`. */
    fun unescape(s: String): String {
        if ('&' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (true) {
            val amp = s.indexOf('&', i)
            if (amp < 0) break
            out.append(s, i, amp)
            val hit = if (amp + 1 < s.length && s[amp + 1] == '#') numericAt(s, amp) else namedAt(s, amp)
            if (hit == null) {
                out.append('&')
                i = amp + 1
            } else {
                out.append(hit.first)
                i = hit.second
            }
        }
        out.append(s, i, s.length)
        return out.toString()
    }

    private fun namedChar(cp: Int) = cp != '\t'.code && cp != '\n'.code && cp != 0x0C && cp != ' '.code &&
        cp != '<'.code && cp != '&'.code && cp != '#'.code && cp != ';'.code

    /**
     * A named reference at [amp]: up to 32 characters and an optional ";",
     * the whole of it if it is a name, else its longest known beginning
     * with the rest left as it was, else "&" and the rest unchanged.
     */
    private fun namedAt(s: String, amp: Int): Pair<String, Int>? {
        val starts = ArrayList<Int>()
        var e = amp + 1
        while (e < s.length && starts.size < 32) {
            val cp = s.codePointAt(e)
            if (!namedChar(cp)) break
            starts.add(e)
            e += Character.charCount(cp)
        }
        if (starts.isEmpty()) return null
        if (e < s.length && s[e] == ';') {
            starts.add(e)
            e += 1
        }
        val name = s.substring(amp + 1, e)
        entities.named[name]?.let { return it to e }
        for (x in starts.size - 1 downTo 2) {
            val cut = starts[x]
            entities.named[s.substring(amp + 1, cut)]?.let { return it + s.substring(cut, e) to e }
        }
        return "&$name" to e
    }

    /**
     * `_unescape_attrvalue`: in an attribute, a number reference is always
     * read; a name only when it is exactly one and not followed by "=".
     */
    private fun unescapeAttr(s: String): String {
        if ('&' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (true) {
            val amp = s.indexOf('&', i)
            if (amp < 0) break
            out.append(s, i, amp)
            var e = amp + 1
            var replaced: String? = null
            if (e < s.length && s[e] == '#') {
                val p = e + 1
                if (p < s.length && s[p] in '0'..'9') {
                    e = p
                    while (e < s.length && s[e] in '0'..'9') e++
                    replaced = charFor(number(s, p, e, 10))
                } else if (p + 1 < s.length && (s[p] == 'x' || s[p] == 'X') && isHex(s[p + 1])) {
                    e = p + 1
                    while (e < s.length && isHex(s[e])) e++
                    replaced = charFor(number(s, p + 1, e, 16))
                } else {
                    out.append('&')
                    i = amp + 1
                    continue
                }
                if (e < s.length && s[e] == ';') e++
                else if (e < s.length && s[e] == '=') {
                    replaced += "="
                    e++
                }
            } else if (e < s.length && isAsciiLetter(s[e])) {
                e++
                while (e < s.length && (isAsciiLetter(s[e]) || s[e] in '0'..'9')) e++
                if (e < s.length && (s[e] == ';' || s[e] == '=')) e++
                val ref = s.substring(amp, e)
                replaced = if (!ref.endsWith("=")) entities.named[ref.substring(1)] ?: ref else ref
            } else {
                out.append('&')
                i = amp + 1
                continue
            }
            out.append(replaced)
            i = e
        }
        out.append(s, i, s.length)
        return out.toString()
    }

    // ---- reading CSS and numbers as the desktop's Python does ----

    /** `_css`: a style attribute as names and values, lowercased; "!important" dropped. */
    private fun css(attr: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (chunk in (attr ?: "").split(";")) {
            val colon = chunk.indexOf(':')
            if (colon < 0) continue
            var value = PyText.strip(chunk.substring(colon + 1)).lowercase()
            if (value.endsWith("!important")) value = PyText.strip(value.dropLast("!important".length))
            out[PyText.strip(chunk.substring(0, colon)).lowercase()] = value
        }
        return out
    }

    /** `str.split()`: runs of Python's spaces, no empty pieces. */
    private fun pySplit(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            i = skipSpaces(s, i)
            if (i >= s.length) break
            var e = i
            while (e < s.length) {
                val cp = s.codePointAt(e)
                if (PyText.isSpace(cp)) break
                e += Character.charCount(cp)
            }
            out.add(s.substring(i, e))
            i = e
        }
        return out
    }

    /**
     * The ASCII Python's `int()` and `float()` read a string as: any
     * script's decimal digit becomes its ASCII digit (`float("٦٠٠")` is
     * 600.0); anything else beyond ASCII makes it unreadable (null).
     */
    private fun asciiDigits(s: String): String? {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            when {
                cp < 127 -> out.append(cp.toChar())
                Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER.toInt() ->
                    out.append('0' + Character.digit(cp, 10))
                else -> return null
            }
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** Python's rule for "_" in a number: only ever between two digits. */
    private fun withoutUnderscores(s: String): String? {
        if ('_' !in s) return s
        for (k in s.indices) {
            if (s[k] == '_' && !(k > 0 && s[k - 1] in '0'..'9' && k + 1 < s.length && s[k + 1] in '0'..'9')) return null
        }
        return s.replace("_", "")
    }

    private val FLOAT = Regex("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val FLOAT_WORD = Regex("([+-]?)(inf|infinity|nan)", RegexOption.IGNORE_CASE)

    /** `float(s)` for a string with no spaces at its ends, or null where Python raises. */
    private fun pyFloat(s: String): Double? {
        val ascii = asciiDigits(s) ?: return null
        if (ascii.any { it == ' ' || it in '\t'..'\r' }) return null
        val plain = withoutUnderscores(ascii) ?: return null
        FLOAT_WORD.matchEntire(plain)?.let { m ->
            val v = if (m.groupValues[2].equals("nan", true)) Double.NaN else Double.POSITIVE_INFINITY
            return if (m.groupValues[1] == "-") -v else v
        }
        if (!FLOAT.matches(plain)) return null
        return plain.toDouble()
    }

    /** `_span_attr`: `max(1, min(30, int(str(value).strip())))`, 1 where `int()` raises. */
    private fun spanAttr(attrs: Map<String, String?>, name: String): Int {
        val raw = if (name in attrs) attrs[name] ?: "None" else "1"
        val ascii = asciiDigits(PyText.strip(raw)) ?: return 1
        var body = ascii
        var negative = false
        if (body.startsWith("+") || body.startsWith("-")) {
            negative = body[0] == '-'
            body = body.substring(1)
        }
        if (body.isEmpty() || body[0] == '_') return 1
        val digits = withoutUnderscores(body) ?: return 1
        if (digits.isEmpty() || !digits.all { it in '0'..'9' } || digits.length > MAX_INT_DIGITS) return 1
        if (negative) return 1
        var v = 0
        for (c in digits) {
            v = v * 10 + (c - '0')
            if (v > 30) return 30
        }
        return maxOf(1, v)
    }

    /** `_weight_is_bold`: the words, or a number from 600. */
    private fun weightIsBold(value: String): Boolean {
        if (value == "bold" || value == "bolder") return true
        val number = pyFloat(value) ?: return false
        return number >= 600
    }

    /** `_bold_and_italic`: true or false where the style says, null where it says nothing. */
    private fun boldAndItalic(style: Map<String, String>): Pair<Boolean?, Boolean?> {
        var bold: Boolean? = null
        var italic: Boolean? = null
        val font = style["font"]
        if (!font.isNullOrEmpty()) {
            bold = false
            italic = false
            for (token in pySplit(font.split("/")[0])) {
                if (token == "italic" || token.startsWith("oblique")) italic = true
                else if (weightIsBold(token)) bold = true
            }
        }
        style["font-weight"]?.let { bold = weightIsBold(it) }
        style["font-style"]?.let { italic = it.startsWith("italic") || it.startsWith("oblique") }
        return bold to italic
    }

    private val HEX = Regex("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})")

    /** `_as_hex`: a CSS colour as `#rrggbb`, or null. */
    private fun asHex(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val v = PyText.strip(value).lowercase()
        if (HEX.matches(v)) {
            return if (v.length == 4) "#" + v.substring(1).map { "$it$it" }.joinToString("") else v
        }
        val parts = rgbParts(v) ?: return null
        if (parts.all { it in 0..255 }) return "#%02x%02x%02x".format(parts[0], parts[1], parts[2])
        return null
    }

    /**
     * `_RGB.match`: `rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)`, with
     * Python's `\s` and `\d` (any script's digits), each number read by
     * `int()` -- which raises past 4,300 digits, and nothing here catches
     * that on the desktop either.
     */
    private fun rgbParts(v: String): List<Int>? {
        var i = when {
            v.startsWith("rgba(") -> 5
            v.startsWith("rgb(") -> 4
            else -> return null
        }
        val parts = ArrayList<Int>(3)
        var longest = 0
        for (n in 0 until 3) {
            if (n > 0) {
                i = skipSpaces(v, i)
                if (i >= v.length || v[i] != ',') return null
                i++
            }
            i = skipSpaces(v, i)
            var digits = 0
            var value = 0
            while (i < v.length) {
                val cp = v.codePointAt(i)
                if (Character.getType(cp) != Character.DECIMAL_DIGIT_NUMBER.toInt()) break
                digits++
                value = minOf(value * 10 + Character.digit(cp, 10), 256)
                i += Character.charCount(cp)
            }
            if (digits == 0) return null
            longest = maxOf(longest, digits)
            parts.add(value)
        }
        // Only once all three have matched: the numbers are read after the match.
        if (longest > MAX_INT_DIGITS) throw Unreadable()
        return parts
    }

    private const val MIN_SATURATION = 40
    private const val MIN_HIGHLIGHT_LIGHTNESS = 200

    /** `_is_meaningful`: colour that is really colour, and a highlight pale enough to read through. */
    private fun isMeaningful(hex: String, kind: String): Boolean {
        val r = hex.substring(1, 3).toInt(16)
        val g = hex.substring(3, 5).toInt(16)
        val b = hex.substring(5, 7).toInt(16)
        if (maxOf(r, g, b) - minOf(r, g, b) < MIN_SATURATION) return false
        if (kind == "highlight") return 0.299 * r + 0.587 * g + 0.114 * b >= MIN_HIGHLIGHT_LIGHTNESS
        return true
    }

    // ---- the reader ----

    private fun cpLength(s: String) = s.codePointCount(0, s.length)
    private fun cpFrom(s: String, from: Int): String =
        if (from >= cpLength(s)) "" else s.substring(s.offsetByCodePoints(0, maxOf(0, from)))
    private fun cpTo(s: String, to: Int): String =
        if (to >= cpLength(s)) s else s.substring(0, s.offsetByCodePoints(0, maxOf(0, to)))

    private data class Span(val start: Int, val end: Int, val style: String, val value: String?)
    private class Open(val style: String, val value: String?, val start: Int)
    private class Owed(var left: Int, val marker: String)

    private val WS_RUN = Regex("[ \\t\\r\\n]+")
    private val WS_RUN_KEEP = Regex("[ \\t\\r]+")
    private val NEWLINE_PADDED = Regex(" *\\n *")

    /** `_Reader`: markup in, one note's worth of text and spans out. */
    private class Reader(val keepNewlines: Boolean) {
        val text = ArrayList<String>()
        var length = 0
        val spans = ArrayList<Span>()
        val paragraphs = JSONArray()
        val open = ArrayList<Open>()
        private val implied = HashMap<String, ArrayList<Boolean>>()
        private val lists = ArrayList<Pair<String, IntArray>>()
        private var line = 0
        private var lineHasText = false
        private var cellDepth = 0
        var textCut = false
        var stylesCut = false
        private var tblActive = 0
        private var tblPending: HashMap<Int, Owed>? = null
        private var tblCol: Int? = null
        private var tblCellAlign = ""
        private var tblCell: Triple<Int, Int, Int>? = null
        private val styleOpens = HashMap<String, ArrayList<List<String>>>()
        private var rowCells = 0          // cells written on the row being read
        private var rowFrom = 0           // where that row's first cell begins
        val loneCells = ArrayList<IntArray>()   // (start, end) of each row of one cell

        private fun atLineStart() = length == 0 || text.lastOrNull()?.endsWith("\n") == true

        fun add(piece: String) {
            val room = MAX_PASTE_CHARS - length
            if (cpLength(piece) > room && PyText.strip(cpFrom(piece, maxOf(room, 0))).isNotEmpty()) textCut = true
            if (room <= 0) return
            val chunk = cpTo(piece, room)
            if (chunk.isEmpty()) return
            text.add(chunk)
            length += cpLength(chunk)
            if (PyText.strip(chunk).isNotEmpty()) lineHasText = true
        }

        /** `_trim_line_end`: spaces left at a line's end by indented markup are not the note's. */
        private fun trimLineEnd() {
            while (text.isNotEmpty() && text.last().isNotEmpty() && text.last().all { it == ' ' }) {
                length -= cpLength(text.removeAt(text.lastIndex))
            }
            if (text.isNotEmpty() && text.last().endsWith(" ")) {
                val trimmed = text.last().trimEnd(' ')
                length -= text.last().length - trimmed.length
                text[text.lastIndex] = trimmed
            }
        }

        /** One cell's opening, counted for its row ([loneCells]). */
        private fun cell(piece: String) {
            if (rowCells == 0) rowFrom = length
            rowCells += 1
            add(piece)
        }

        private fun newline() {
            trimLineEnd()
            if (length != 0 && !text.last().endsWith("\n")) {
                add("\n")
                line += 1
                lineHasText = false
            }
        }

        fun close(style: String) {
            for (i in open.indices.reversed()) {
                if (open[i].style == style) {
                    val o = open.removeAt(i)
                    if (length > o.start && spans.size >= MAX_PASTE_SPANS) stylesCut = true
                    else if (length > o.start) spans.add(Span(o.start, length, o.style, o.value))
                    return
                }
            }
        }

        /** `_owed_merge_cell`: one marker cell owed to a rowspan over this column. */
        private fun owedMergeCell(): Boolean {
            val pending = tblPending
            val col = tblCol ?: 0
            if (pending.isNullOrEmpty() || col !in pending) return false
            val owed = pending.getValue(col)
            cell("$CELL ${owed.marker} ")
            if (owed.left - 1 > 0) owed.left -= 1 else pending.remove(col)
            tblCol = col + 1
            return true
        }

        /** [attrList] in order, a valueless attribute as null -- as `HTMLParser` lists them. */
        fun startTag(tag: String, attrList: List<Pair<String, String?>>) {
            val attrs = LinkedHashMap<String, String?>()
            for ((k, v) in attrList) attrs[k] = v
            val style = css(attrs["style"])
            val (bold, italic) = boldAndItalic(style)
            if (tag == "table") {
                tblActive += 1
                tblPending = HashMap()
                tblCol = 0
            } else if (tag == "tr" && tblActive != 0) {
                tblCol = 0
            }
            STYLE_TAGS[tag]?.let { meant ->
                // A tag's own CSS can take back what the tag means: Google
                // Docs wraps a whole copy in <b style="font-weight:normal">.
                val kept = !((meant == "bold" && bold == false) || (meant == "italic" && italic == false))
                if (kept) open.add(Open(meant, null, length))
                implied.getOrPut(tag) { ArrayList() }.add(kept)
            }
            if (tag == "ul" || tag == "ol") {
                if (lists.size < Paragraphs.MAX_INDENT) lists.add((if (tag == "ul") "bullet" else "number") to intArrayOf(0))
            } else if (tag == "li") {
                newline()
                if (lists.isNotEmpty()) {
                    val (kind, count) = lists.last()
                    count[0] += 1
                    val indent = lists.size - 1
                    add(Paragraphs.markerFor(kind, count[0], indent))
                    val entry = JSONObject().put("line", line).put("kind", kind)
                    if (indent != 0) entry.put("indent", indent)
                    paragraphs.put(entry)
                }
            } else if (tag in CELL_TAGS) {
                cellDepth += 1
                tblCellAlign = PyText.strip(css(attrs["style"])["text-align"] ?: "").lowercase()
                while (owedMergeCell()) { /* every column a rowspan still covers */ }
                tblCell = Triple(tblCol ?: 0, spanAttr(attrs, "colspan"), spanAttr(attrs, "rowspan"))
                // An extra lead space is how the padding says "not left".
                cell(CELL + if (tblCellAlign == "center" || tblCellAlign == "right") "  " else " ")
            } else if (tag in BLOCK_TAGS) {
                // A cell is one line of the note: a <br> in it is the cell's
                // own line break, any other block only a gap between words.
                if (cellDepth != 0) add(if (tag == "br") LINE_BREAK else " ") else newline()
            }

            // A void tag never gets an end tag, so a style it opened could
            // only close at the end of the document.
            if (tag in VOID_TAGS) return
            val opened = ArrayList<String>()
            for ((prop, name) in listOf("color" to "text_color", "background" to "highlight",
                    "background-color" to "highlight")) {
                val hex = asHex(style[prop])
                if (hex != null && isMeaningful(hex, name)) {
                    open.add(Open(name, hex, length))
                    opened.add(name)
                }
            }
            if (bold == true) {
                open.add(Open("bold", null, length))
                opened.add("bold")
            }
            if (italic == true) {
                open.add(Open("italic", null, length))
                opened.add("italic")
            }
            if (opened.isNotEmpty()) styleOpens.getOrPut(tag) { ArrayList() }.add(opened)
            val align = style["text-align"]
            if (align == "center" || align == "right") paragraphs.put(JSONObject().put("line", line).put("align", align))
        }

        fun endTag(tag: String) {
            // The spans this tag's start opened close first, before any
            // newline below is swept into them; keyed by name, so soup with
            // a stray element closes at the next same-named end tag.
            styleOpens[tag]?.let { list ->
                if (list.isNotEmpty()) for (name in list.removeAt(list.lastIndex).reversed()) close(name)
            }
            STYLE_TAGS[tag]?.let { meant ->
                val taken = implied[tag]
                if (taken.isNullOrEmpty() || taken.removeAt(taken.lastIndex)) close(meant)
            }
            if (tag == "ul" || tag == "ol") {
                if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                newline()
            } else if (tag in CELL_TAGS) {
                cellDepth = maxOf(0, cellDepth - 1)
                add(if (tblCellAlign == "center") "  " else " ")
                val (col, spanC, spanR) = tblCell ?: Triple(0, 1, 1)
                // A colspan is the anchor and then "<" cells; a rowspan owes
                // "^" under the anchor and "<" under the rest of its width.
                repeat(spanC - 1) { cell("$CELL $MERGE_LEFT ") }
                if (spanR > 1) {
                    val pending = tblPending ?: HashMap<Int, Owed>().also { tblPending = it }
                    for (cover in col until col + spanC) pending[cover] = Owed(spanR - 1, if (cover == col) MERGE_UP else MERGE_LEFT)
                }
                tblCol = col + spanC
            } else if (tag == "table") {
                tblActive = maxOf(0, tblActive - 1)
                tblPending = HashMap()
            } else if (tag == "tr") {
                if (tblActive != 0) {
                    // Rowspan columns the row never reached still owe markers,
                    // with empty filler for any gap on the way to them.
                    val pending = tblPending ?: HashMap()
                    for (cover in pending.keys.sorted()) {
                        if (cover < (tblCol ?: 0)) continue
                        while ((tblCol ?: 0) < cover) {
                            cell("$CELL  ")
                            tblCol = (tblCol ?: 0) + 1
                        }
                        owedMergeCell()
                    }
                }
                add(CELL)
                if (rowCells == 1) loneCells.add(intArrayOf(rowFrom, length))
                rowCells = 0
                newline()
            } else if (tag in BLOCK_TAGS) {
                if (cellDepth == 0) newline()
            }
        }

        fun data(data: String) {
            // Markup is indented for humans; a note should not inherit that.
            var cleaned = if (keepNewlines) {
                NEWLINE_PADDED.replace(WS_RUN_KEEP.replace(data, " "), "\n")
            } else {
                WS_RUN.replace(data, " ")
            }
            // A page's non-breaking spaces are plain ones in a note: a browser
            // writes one at each edge of a bold word or a link, and a search
            // for the words either side found nothing (2026-10-25).
            cleaned = cleaned.replace(Char(0xA0), ' ')
            if (cellDepth != 0) {
                // The export draws a checkbox cell as its glyph; coming back,
                // the glyph is the checkbox again -- only inside a cell.
                cleaned = cleaned.replace("☑", CHECKBOX_DONE).replace("☐", CHECKBOX_EMPTY)
            }
            if (atLineStart()) cleaned = cleaned.trimStart(' ')
            if (PyText.strip(cleaned).isNotEmpty() || lineHasText) add(cleaned)
        }
    }

    // ---- tables, as `tables.align` lines them up ----

    private const val PIPE = '|'.code
    private const val MIN_COLUMN = 8

    private fun cps(s: String): IntArray = s.codePoints().toArray()

    /** Where Python's `strip()` would cut [c]: the first kept index and one past the last. */
    private fun stripped(c: IntArray, from: Int = 0, to: Int = c.size): Pair<Int, Int> {
        var a = from
        var b = to
        while (a < b && PyText.isSpace(c[a])) a++
        while (b > a && PyText.isSpace(c[b - 1])) b--
        return a to b
    }

    private fun isTableRow(c: IntArray): Boolean {
        val (a, b) = stripped(c)
        if (b - a < 3 || c[a] != PIPE || c[b - 1] != PIPE) return false
        return splitRow(c).size >= 2
    }

    /** `split_row`: each cell as (first, end) of its stripped text. */
    private fun splitRow(c: IntArray): List<Pair<Int, Int>> {
        val (a, b) = stripped(c)
        if (b == a || c[a] != PIPE) return emptyList()
        val pipes = (a until b).filter { c[it] == PIPE }
        return (0 until pipes.size - 1).map { stripped(c, pipes[it] + 1, pipes[it + 1]) }
    }

    /** `cell_bounds`: where each cell's text sits in the row, padding excluded. */
    private fun cellBounds(c: IntArray): List<Pair<Int, Int>> {
        val (a, b) = stripped(c)
        if (b == a || c[a] != PIPE || c[b - 1] != PIPE) return emptyList()
        val pipes = (a until b).filter { c[it] == PIPE }
        val bounds = ArrayList<Pair<Int, Int>>()
        for (k in 0 until pipes.size - 1) {
            val from = pipes[k] + 1
            val to = pipes[k + 1]
            val (ta, tb) = stripped(c, from, to)
            val start = if (tb > ta) ta else from + minOf(1, to - from)
            bounds.add(start to start + (tb - ta))
        }
        return bounds
    }

    private enum class Align { LEFT, CENTER, RIGHT }

    /** `cell_alignments`: the padding read as alignment. */
    private fun cellAlignments(c: IntArray): List<Align> {
        if (!isTableRow(c)) return emptyList()
        val pipes = c.indices.filter { c[it] == PIPE }
        return (0 until pipes.size - 1).map { k ->
            val from = pipes[k] + 1
            val to = pipes[k + 1]
            val (ta, tb) = stripped(c, from, to)
            if (tb == ta) return@map Align.LEFT
            var lead = 0
            while (from + lead < to && c[from + lead] == ' '.code) lead++
            var trail = 0
            while (to - 1 - trail >= from && c[to - 1 - trail] == ' '.code) trail++
            when {
                lead >= 2 && trail >= 2 -> Align.CENTER
                lead >= 2 -> Align.RIGHT
                else -> Align.LEFT
            }
        }
    }

    private fun need(align: Align) = when (align) {
        Align.CENTER -> 2
        Align.RIGHT -> 1
        Align.LEFT -> 0
    }

    /** `layout_widths`. */
    private fun layoutWidths(rows: List<List<Int>>, aligns: List<List<Align>>): MutableList<Int> {
        val widths = ArrayList<Int>()
        for ((r, row) in rows.withIndex()) {
            for ((i, cell) in row.withIndex()) {
                if (i >= widths.size) widths.add(0)
                var want = cell
                if (r < aligns.size && i < aligns[r].size) want += need(aligns[r][i])
                widths[i] = maxOf(widths[i], want)
            }
        }
        return widths.mapTo(ArrayList()) { maxOf(MIN_COLUMN, it) }
    }

    private fun isSeparatorRow(c: IntArray): Boolean {
        if (!isTableRow(c)) return false
        val cells = splitRow(c)
        if (cells.isEmpty()) return false
        for ((a, b) in cells) {
            if (b - a == 1 && c[a] == '~'.code) continue
            var from = a
            var to = b
            if (to > from && c[from] == ':'.code) from++
            if (to > from && c[to - 1] == ':'.code) to--
            if (to == from || (from until to).any { c[it] != '-'.code }) return false
        }
        return true
    }

    /** `find_blocks`: every run of table rows, as first and last line. */
    private fun findBlocks(lines: List<IntArray>): List<Pair<Int, Int>> {
        val blocks = ArrayList<Pair<Int, Int>>()
        var start = -1
        for ((i, line) in lines.withIndex()) {
            if (isTableRow(line)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                blocks.add(start to i - 1)
                start = -1
            }
        }
        if (start >= 0) blocks.add(start to lines.size - 1)
        return blocks
    }

    /** `pad_ops`: the space edits that would line this row up, right to left. */
    private fun padOps(c: IntArray, widths: List<Int>, aligns: List<Align>?): List<Pair<Int, Int>> {
        val bounds = cellBounds(c)
        if (bounds.isEmpty()) return emptyList()
        var rawEnd = c.size
        while (rawEnd > 0 && PyText.isSpace(c[rawEnd - 1])) rawEnd--
        val ops = ArrayList<Pair<Int, Int>>()
        for (i in bounds.indices.reversed()) {
            val (start, end) = bounds[i]
            val width = if (i < widths.size) widths[i] else end - start
            val align = aligns?.getOrNull(i) ?: Align.LEFT
            val content = end - start
            val extra = maxOf(0, width - content)
            val (wantLead, wantTrail) = when {
                align == Align.CENTER && content != 0 -> 1 + extra / 2 to 1 + extra - extra / 2
                align == Align.RIGHT && content != 0 -> 1 + extra to 1
                else -> 1 to 1 + extra
            }
            var closing = end
            while (closing < rawEnd && c[closing] != PIPE) closing++
            val haveTrail = closing - end
            if (haveTrail != wantTrail) ops.add(end to wantTrail - haveTrail)
            var opening = start - 1
            while (opening >= 0 && c[opening] != PIPE) opening--
            val haveLead = start - opening - 1
            if (haveLead != wantLead) ops.add(opening + 1 to wantLead - haveLead)
        }
        return ops
    }

    /**
     * `tables.align`: every table re-padded, and where characters went in
     * or came out -- every offset counted in the text as it came, which is
     * what `shift_spans` judges them against. The desktop counts a second
     * table's offsets in the text the first one's padding has already
     * changed, and a style in that table lands beside its words: a desktop
     * finding, not copied (ISSUES_LOG).
     */
    private fun align(text: String): Pair<String, List<Pair<Int, Int>>> {
        val lines = text.split("\n").map(::cps).toMutableList()
        val asCame = lines.map { it.size }
        val shifts = ArrayList<Pair<Int, Int>>()
        var counted = 0
        var prefix = 0
        for ((first, last) in findBlocks(lines)) {
            val block = lines.subList(first, last + 1).toList()
            val sepAt = (0 until minOf(2, block.size)).firstOrNull { isSeparatorRow(block[it]) }
            val rows = block.map { row -> splitRow(row).map { (a, b) -> b - a } }
            val aligns = block.map(::cellAlignments)
            val widths = if (sepAt != null) {
                val w = layoutWidths(rows.filterIndexed { i, _ -> i != sepAt }, aligns.filterIndexed { i, _ -> i != sepAt })
                val sep = block[sepAt]
                for ((i, cell) in splitRow(sep).withIndex()) {
                    val size = cell.second - cell.first
                    val hidden = size == 1 && sep[cell.first] == '~'.code
                    if (i < w.size && !hidden && size > 3) w[i] = maxOf(w[i], size)
                }
                w
            } else {
                layoutWidths(rows, aligns)
            }
            // Weighed before it is built: past MAX_TABLE_PADDING the table is
            // left ragged, each cell padded only as its alignment needs. Padded
            // after the paste ceiling, one 60,000-character cell over 2,000 rows
            // became 120 million characters, and the app ran out of memory.
            val perRow = rowWidths(rows, widths) { r, i -> need(aligns[r].getOrNull(i) ?: Align.LEFT) }
            // The lines before this block, as they came, summed once and kept
            // running: summed afresh per block, many small tables walked the
            // text once each.
            while (counted < first) prefix += asCame[counted++] + 1
            var pos = prefix
            for (offset in 0..last - first) {
                val old = lines[first + offset]
                // The edits come right to left, each at its place in the line
                // as it was: the line is put together once from its pieces.
                // Rebuilt at every edit, a row of thousands of cells was
                // copied thousands of times.
                val pieces = ArrayList<IntArray>()
                var end = old.size
                for ((at, delta) in padOps(old, perRow[offset], aligns[offset])) {
                    if (delta > 0) {
                        pieces.add(old.copyOfRange(at, end))
                        pieces.add(IntArray(delta) { ' '.code })
                    } else {
                        pieces.add(old.copyOfRange(at - delta, end))
                    }
                    end = at
                    shifts.add(pos + at to delta)
                }
                pieces.add(old.copyOfRange(0, end))
                val new = IntArray(pieces.sumOf { it.size })
                var k = 0
                for (piece in pieces.asReversed()) {
                    piece.copyInto(new, k)
                    k += piece.size
                }
                pos += old.size + 1
                lines[first + offset] = new
            }
        }
        return lines.joinToString("\n") { String(it, 0, it.size) } to shifts
    }

    /**
     * `tables.forget_widths`: every table's separator spelled at markdown's
     * minimum, for text from outside the app. Its dashes are as many as its
     * writer typed -- "---:" is how markdown spells a right-aligned column --
     * and read as this app's own record they were a width set by hand: the
     * column drawn four characters wide, its header cut to "How…". The
     * colons and a hidden mark stay. Offsets in code points, counted in the
     * text as it came, for [shiftSpans].
     */
    fun forgetWidths(text: String): Pair<String, List<Pair<Int, Int>>> {
        val lines = text.split("\n").map(::cps).toMutableList()
        val starts = IntArray(lines.size)
        var pos = 0
        for (i in lines.indices) {
            starts[i] = pos
            pos += lines[i].size + 1
        }
        val shifts = ArrayList<Pair<Int, Int>>()
        for ((first, last) in findBlocks(lines)) {
            val block = lines.subList(first, last + 1).toList()
            val at = (0 until minOf(2, block.size)).firstOrNull { isSeparatorRow(block[it]) } ?: continue
            val n = first + at
            val line = lines[n]
            var new = line
            var cut = 0
            for ((bounds, column) in cellBounds(line).zip(parseSeparator(String(line, 0, line.size)))) {
                if (column.width == null) continue
                val (a, b) = bounds
                val short = cps(separatorCells(listOf(column.copy(width = null)))[0])
                new = new.copyOfRange(0, a - cut) + short + new.copyOfRange(b - cut, new.size)
                shifts.add(starts[n] + a + short.size to short.size - (b - a))
                cut += (b - a) - short.size
            }
            lines[n] = new
        }
        return lines.joinToString("\n") { String(it, 0, it.size) } to shifts
    }

    /**
     * `_foreign_plain`: [raw], pasted as text, with its tables' separators
     * at markdown's minimum ([forgetWidths]) -- unless it is the app's own
     * copy from a note ([mine]), whose dashes are widths set here. Line
     * endings come back as the note's.
     */
    fun foreignPlain(raw: String, mine: OwnCopy?): String {
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        if (mine != null && text == mine.text) return text
        return forgetWidths(text).first
    }

    /**
     * What a plain field holds after [old] became [new], when the change put
     * in more than a line at once -- a paste -- with the separators in what
     * went in at markdown's minimum ([foreignPlain]). The template editor's
     * text, which has no paste of its own to ask. A change of one line or
     * less is typing, and comes back as it is: a separator typed dash by
     * dash is the person's own.
     */
    fun forgetPastedWidths(old: String, new: String, mine: OwnCopy?): String {
        var head = 0
        while (head < old.length && head < new.length && old[head] == new[head]) head++
        var tail = 0
        while (tail < old.length - head && tail < new.length - head &&
               old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++
        val came = new.substring(head, new.length - tail)
        if ('\n' !in came) return new
        val kept = foreignPlain(came, mine)
        return if (kept == came) new else new.substring(0, head) + kept + new.substring(new.length - tail)
    }

    // ---- a PDF's column, and markers a plain paste leaves behind ----

    private const val MIN_WRAPPED_LINES = 3
    private const val MIN_COLUMN_WIDTH = 40
    private const val MOSTLY_FORCED = 0.6
    private const val COLUMN_SLACK = 2
    private const val CLEARLY_SHORT = 0.7
    private val SENTENCE_END = setOf('.'.code, '!'.code, '?'.code, ':'.code, ';'.code, '"'.code, '\''.code,
        ')'.code, ']'.code, 0x2019, 0x201D)
    private val BLANK_LINE = Regex("\\n[ \\t]*\\n")

    private fun leading(c: IntArray): Int {
        var k = 0
        while (k < c.size && PyText.isSpace(c[k])) k++
        return k
    }

    private fun rstripped(c: IntArray): IntArray {
        var b = c.size
        while (b > 0 && PyText.isSpace(c[b - 1])) b--
        return c.copyOf(b)
    }

    /** `_ends_a_paragraph`: a line that could have taken another word, and really ended. */
    private fun endsAParagraph(line: IntArray, width: Int): Boolean {
        val s = rstripped(line)
        if (s.isEmpty()) return true
        if (s.size < width * CLEARLY_SHORT) return true
        return s.last() in SENTENCE_END
    }

    /** `_forced_break`: the next line's first word could not have fitted on this one. */
    private fun forcedBreak(line: IntArray, next: IntArray, width: Int, slack: Int = 0): Boolean {
        val (a, b) = stripped(next)
        if (b == a) return false
        var e = a
        while (e < b && next[e] != ' '.code) e++
        val word = e - a
        if (word == 0) return false
        return line.size + 1 + word > width - slack
    }

    /** `_looks_wrapped`: these lines were broken by a column, not on purpose. */
    private fun looksWrapped(lines: List<IntArray>): Boolean {
        if (lines.size < MIN_WRAPPED_LINES) return false
        // A list's breaks are the list, and a table's rows are its rows.
        if (lines.any { Paragraphs.parseListMarker(String(it, 0, it.size).trimStart(' ', '\t')).kind != null }) return false
        if (lines.any { isTableRow(it) }) return false
        val width = lines.maxOf { it.size }
        if (width < MIN_COLUMN_WIDTH) return false
        val breaks = lines.size - 1
        val forced = (0 until breaks).count { forcedBreak(lines[it], lines[it + 1], width) }
        return forced >= maxOf(2, (breaks * MOSTLY_FORCED).toInt())
    }

    /** `_join_wrapped`: one paragraph out of the lines a column broke it into. */
    private fun joinWrapped(lines: List<IntArray>): String {
        val joined = StringBuilder()
        // The joined text's last two characters, kept as it grows -- the
        // join's space too, though a piece one character long always ends
        // its paragraph, so nothing reads the pair right after it.
        var last = -1
        var beforeLast = -1
        fun took(from: IntArray, a: Int, b: Int) {
            for (k in a until b) {
                beforeLast = last
                last = from[k]
            }
        }
        for (line in lines) {
            val (a, b) = stripped(line)
            if (joined.isEmpty()) {
                joined.append(String(line, a, b - a))
            } else if (beforeLast >= 0 && last == '-'.code && Character.isLetter(beforeLast) &&
                       b > a && Character.isLowerCase(line[a])) {
                // A hyphen at the break joins without a space, and is kept:
                // "type-setting" still reads, "moreor-less" would not.
                joined.append(String(line, a, b - a))
            } else {
                joined.append(' ').append(String(line, a, b - a))
                took(intArrayOf(' '.code), 0, 1)
            }
            took(line, a, b)
        }
        return joined.toString()
    }

    /**
     * `reflow_wrapped`: lines a fixed-width column broke, joined again; real
     * breaks left alone. A run that does not look wrapped comes back as it
     * went in.
     */
    fun reflowWrapped(text: String?): String {
        val blocks = ArrayList<String>()
        for (block in BLANK_LINE.split(text ?: "")) {
            val lines = block.split("\n").map(::cps).filter { line -> stripped(line).let { it.second > it.first } }
                .map(::rstripped)
            if (lines.isEmpty()) continue
            if (!looksWrapped(lines)) {
                blocks.add(lines.joinToString("\n") { String(it, 0, it.size) })
                continue
            }
            val width = lines.maxOf { it.size }
            // A first line indented past the block's edge starts a paragraph.
            val edge = lines.minOf { leading(it) }
            val paragraphs = ArrayList<List<IntArray>>()
            var current = ArrayList<IntArray>()
            for ((i, line) in lines.withIndex()) {
                current.add(line)
                if (i == lines.lastIndex) {
                    paragraphs.add(current)
                    current = ArrayList()
                    continue
                }
                val following = lines[i + 1]
                val indented = leading(following) > edge
                val couldFit = !forcedBreak(line, following, width, COLUMN_SLACK)
                if (indented || (couldFit && endsAParagraph(line, width))) {
                    paragraphs.add(current)
                    current = ArrayList()
                }
            }
            if (current.isNotEmpty()) paragraphs.add(current)
            blocks.add(paragraphs.joinToString("\n") { joinWrapped(it) })
        }
        return blocks.joinToString("\n\n")
    }

    /**
     * `strip_list_markers`: what this app would read back as a list marker,
     * taken off -- for a plain paste, which was asked to leave the
     * formatting behind. "- " and "* " are the writer's own and stay.
     */
    fun stripListMarkers(text: String?): String =
        (text ?: "").split("\n").joinToString("\n") { line ->
            val marker = Paragraphs.parseListMarker(line.trimStart(' ', '\t'))
            if (marker.kind != null) marker.rest else line
        }

    /** `shift_spans`: every offset judged against the span as it was. */
    private fun shiftSpans(spans: List<Span>, shifts: List<Pair<Int, Int>>): List<Span> {
        if (shifts.isEmpty()) return spans
        return spans.map { s ->
            s.copy(start = s.start + shifts.filter { it.first < s.start }.sumOf { it.second },
                   end = s.end + shifts.filter { it.first < s.end }.sumOf { it.second })
        }
    }
}
