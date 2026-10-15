package ro.noteorganizer.app

/**
 * A note's body as the editor shows it: prose the person types into, and
 * tables drawn as grids between it.
 *
 * The desktop draws a table inside its text box. A phone's text field
 * cannot hold a grid, so the editor cuts the body into SEGMENTS by line:
 * each run of prose becomes its own field, each table a grid. Typing in
 * one field rewrites only that field's lines, and the body is put back
 * together around it -- so the file still holds one body, exactly as the
 * desktop stores it, and the formatting is moved by [remapSpans], the rule
 * every other edit uses.
 *
 * A body that ends with a table gets an empty prose segment after it, so
 * there is somewhere to keep writing; it adds a line only once something
 * is typed into it.
 */
object Editing {

    /**
     * One piece of the body. Lines are the body's own line numbers,
     * [lastLine] inclusive; a segment past the end of the body has
     * `lastLine == firstLine - 1` and holds nothing yet. [cpStart] is
     * where it begins, counted in code points as the file counts.
     */
    data class Segment(
        val table: Boolean,
        val firstLine: Int,
        val lastLine: Int,
        val text: String,
        val cpStart: Int,
    ) {
        val isVirtual: Boolean get() = lastLine < firstLine
    }

    fun segments(body: String): List<Segment> {
        val lines = body.split("\n")
        // Where each line starts, in code points; one past the end for the
        // virtual segment.
        val starts = IntArray(lines.size + 1)
        var at = 0
        for (i in lines.indices) {
            starts[i] = at
            at += lines[i].codePointCount(0, lines[i].length) + 1
        }
        starts[lines.size] = at
        fun prose(first: Int, last: Int) = Segment(
            false, first, last, lines.subList(first, last + 1).joinToString("\n"), starts[first])

        val out = ArrayList<Segment>()
        var line = 0
        for (block in tableBlocks(body)) {
            if (block.first > line) out.add(prose(line, block.first - 1))
            out.add(Segment(true, block.first, block.last,
                lines.subList(block.first, block.last + 1).joinToString("\n"), starts[block.first]))
            line = block.last + 1
        }
        if (line < lines.size) {
            out.add(prose(line, lines.size - 1))
        } else {
            // The body ends with a table: room to write after it.
            out.add(Segment(false, lines.size, lines.size - 1, "", starts[lines.size]))
        }
        return out
    }

    /**
     * The body with one prose segment's lines replaced by [newText]. A
     * virtual segment appends a line only when something was typed.
     */
    fun replaceProse(body: String, segment: Segment, newText: String): String {
        require(!segment.table) { "a table is changed through TableEdit" }
        val lines = body.split("\n")
        if (segment.isVirtual) {
            return if (newText.isEmpty()) body else body + "\n" + newText
        }
        val first = segment.firstLine.coerceIn(0, lines.size)
        val last = segment.lastLine.coerceIn(first - 1, lines.size - 1)
        val rebuilt = ArrayList<String>(lines.size)
        rebuilt.addAll(lines.subList(0, first))
        rebuilt.addAll(newText.split("\n"))
        rebuilt.addAll(lines.subList(last + 1, lines.size))
        return rebuilt.joinToString("\n")
    }

    /**
     * The spans that fall inside a segment, moved so 0 is the segment's
     * first character -- what its field draws. A span reaching past either
     * end is cut to the segment.
     */
    fun localSpans(spans: List<FormatSpan>, segment: Segment): List<FormatSpan> {
        val length = segment.text.codePointCount(0, segment.text.length)
        val from = segment.cpStart
        val to = from + length
        return spans.mapNotNull { span ->
            val start = maxOf(span.start, from) - from
            val end = minOf(span.end, to) - from
            if (end > start) span.copy(start = start, end = end) else null
        }
    }

    /**
     * Words a person wrote: a table's pipes and dashes are not words, so
     * the count reads the body the way search does.
     */
    fun wordCount(body: String): Int =
        contentText(body).split(Regex("\\s+")).count { it.isNotEmpty() }

    /**
     * The desktop's `reading_time`: at 200 words a minute, the slow end of
     * adult reading -- a reader who finishes early has lost nothing. Python
     * rounds a half to the even neighbour, and so does this.
     */
    fun readingTime(words: Int, wpm: Int = 200): String {
        if (words <= 0) return ""
        if (words < wpm) return "under a minute to read"
        val minutes = Math.rint(words.toDouble() / wpm).toInt()
        return "about $minutes minute${if (minutes == 1) "" else "s"} to read"
    }

    /**
     * A selection inside a segment's field -- UTF-16 indices, as a text
     * field reports them -- as code points in the whole body, which is what
     * the spans are counted in.
     */
    fun bodyRange(segment: Segment, fromUtf16: Int, toUtf16: Int): Pair<Int, Int> {
        val map = codePointOffsets(segment.text, listOf(fromUtf16, toUtf16))
        return (segment.cpStart + (map[fromUtf16] ?: 0)) to (segment.cpStart + (map[toUtf16] ?: 0))
    }
}
