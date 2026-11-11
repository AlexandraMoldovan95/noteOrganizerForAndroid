package ro.noteorganizer.app

/**
 * Find in note: the desktop's find bar, as rules. It looks through the open
 * note's body -- prose and tables' cells -- then its checklist and comments,
 * and changes none of it; matches come in reading order, and the bar counts
 * them "3/12" and steps round them. As on the desktop, case and diacritics
 * count only when asked ("Aa", [searchFold]); matches may overlap ("aa" twice
 * in "aaa"); a cell is one match, read with a line break as a space and no
 * merge marker or checkbox as a word; at most [CAP] matches, then a "+".
 * A table is stepped through row by row, as it is read -- as the desktop's
 * is since its 15d6dae, which went down each column (a finding of this port).
 */
object Finding {

    /** The desktop's ceiling: past it, a search is not finding anything useful. */
    const val CAP = 2000

    sealed interface Match {
        /** Which piece of the body, as [Editing.segments] cuts it. */
        val segment: Int
    }

    /** Words in a run of prose: [start] to [end] in that segment's own text, as its field counts. */
    data class InText(override val segment: Int, val start: Int, val end: Int) : Match

    /** A table cell holding the words, in the drawn grid's numbering (the separator is not a row). */
    data class InCell(override val segment: Int, val row: Int, val column: Int) : Match

    /** Words in the checklist's row [item], [start] to [end] in its words -- under the body, no piece of it. */
    data class InChecklist(val item: Int, val start: Int, val end: Int) : Match {
        override val segment: Int get() = OUTSIDE
    }

    /** Words in the comments, [start] to [end] in their text -- under the body, no piece of it. */
    data class InComments(val start: Int, val end: Int) : Match {
        override val segment: Int get() = OUTSIDE
    }

    /** The [Match.segment] of a match outside the body. */
    const val OUTSIDE = -1

    /**
     * Every match of [query] in [body], top to bottom -- then in the
     * [checklist]'s rows and the [comments] under it, as the note reads
     * on the screen. Those two are searched as prose is. The desktop's bar
     * looks through the body alone; on the phone the owner asked for them
     * too, the checklist and comments being folded away and found the same
     * way (2026-10-26).
     */
    fun scan(body: String, query: String, matchCase: Boolean,
             checklist: List<String> = emptyList(), comments: String = ""): List<Match> {
        if (query.isEmpty()) return emptyList()
        val found = ArrayList<Match>()
        // The spaces at its ends are not part of it, unless it is nothing else:
        // a word picked from the keyboard's suggestions comes with one, as in
        // the list. A line break there is, to find the words that end a line.
        val words = query.trim(' ').ifEmpty { query }
        val needle = if (matchCase) words else searchFold(words)

        // Letter by letter, each search one character after the last match began.
        fun inText(text: String, add: (Int, Int) -> Match) {
            val hay = if (matchCase) text else searchFold(text)
            var at = hay.indexOf(needle)
            while (at >= 0 && found.size < CAP) {
                found.add(add(at, at + needle.length))
                at = hay.indexOf(needle, at + Character.charCount(hay.codePointAt(at)))
            }
        }

        // Read-only text: the first match on each line, its line one stop.
        fun inLines(text: String, add: (Int, Int) -> Match) {
            val hay = if (matchCase) text else searchFold(text)
            var at = hay.indexOf(needle)
            while (at >= 0 && found.size < CAP) {
                found.add(add(at, at + needle.length))
                val next = hay.indexOf('\n', at + needle.length - 1)
                if (next < 0) break
                at = hay.indexOf(needle, next + 1)
            }
        }

        for ((index, segment) in Editing.segments(body).withIndex()) {
            if (found.size >= CAP) break
            if (segment.field) {
                inText(segment.text) { start, end -> InText(index, start, end) }
                continue
            }
            // A read-only piece (lines too long to lay out, the rest past what is
            // drawn, a table too large to draw) is searched too, as the list's search
            // does: unparsed, one stop a line as a cell is, so it cannot use up the count.
            if (segment.long || !segment.drawn) {
                inLines(segment.text) { start, end -> InText(index, start, end) }
                continue
            }
            val table = parseTable(segment.text.split("\n"))
            for ((r, row) in table.rows.withIndex()) {
                for ((c, cell) in row.withIndex()) {
                    if (found.size >= CAP) break
                    // Swallowed by a merge, or in a column the grid hides:
                    // nothing there is drawn, so nothing there is found.
                    if (cell == null || table.columns.getOrNull(c)?.hidden == true) continue
                    if (cell.checked != null) continue
                    val text = cell.lines.joinToString(" ")
                    if (text == MERGE_LEFT || text == MERGE_UP) continue
                    val hay = if (matchCase) text else searchFold(text)
                    if (needle in hay) found.add(InCell(index, r, c))
                }
            }
        }
        // A row or comments shown read-only are searched too, as the body's
        // pieces are, a line one stop: the row is brought into sight, the
        // comments show the line of the match.
        for ((item, words) in checklist.withIndex()) {
            if (found.size >= CAP) break
            val add = { start: Int, end: Int -> InChecklist(item, start, end) }
            if (Editing.rowTooLong(words)) inLines(words, add)
            else inText(words, add)
        }
        if (found.size < CAP) {
            val add = { start: Int, end: Int -> InComments(start, end) }
            if (Editing.tooLongToLayOut(comments)) inLines(comments, add) else inText(comments, add)
        }
        return found
    }

    /**
     * Where each of [lines] starts, as one text joined by line breaks, in
     * UTF-16 units -- the field's offsets; Paragraphs' lineStarts counts code
     * points, the stored note's.
     */
    fun lineStartsUtf16(lines: List<String>): IntArray {
        val starts = IntArray(lines.size)
        var at = 0
        for (i in lines.indices) {
            starts[i] = at
            at += lines[i].length + 1
        }
        return starts
    }

    /**
     * The line holding offset [at], by its [starts] -- in whichever unit they
     * count: [lineStartsUtf16]'s, or Paragraphs.lineStarts' code points.
     */
    fun lineAt(starts: IntArray, at: Int): Int {
        var i = java.util.Arrays.binarySearch(starts, at)
        if (i < 0) i = -i - 2
        return i.coerceIn(0, maxOf(0, starts.size - 1))
    }

    /** What a read-only line shows of itself: [from] to [to] in the line, cut there with "…". */
    data class Cut(val from: Int, val to: Int)

    /**
     * Where [line] is cut to [chars] to be shown read-only: at its start, or
     * -- the match stepped to, [at] in the line, lying past what that shows --
     * around it, a little before. Never through the middle of an emoji.
     */
    fun cutAround(line: String, chars: Int, at: Int?): Cut {
        var from = if (at != null && at > chars - BEFORE) (at - BEFORE).coerceIn(0, line.length) else 0
        if (from in 1 until line.length && Character.isLowSurrogate(line[from])) from--
        var to = minOf(line.length, from + chars)
        if (to in 1 until line.length && Character.isLowSurrogate(line[to])) to--
        return Cut(from, to)
    }

    /** How much of a read-only line is shown before the match it is cut around. */
    private const val BEFORE = 60

    /** The bar's count: nothing before a word is typed, "0/0" when it is found nowhere. */
    fun counter(current: Int, total: Int, query: String): String = when {
        query.isEmpty() -> ""
        total == 0 -> "0/0"
        else -> "${current + 1}/$total" + if (total >= CAP) "+" else ""
    }

    /** Which match is current after the note changed under it: the nearest one left. */
    fun clamp(index: Int, total: Int): Int = if (total == 0) -1 else index.coerceIn(0, total - 1)

    /** One step forward or back, round from the last to the first. */
    fun step(index: Int, delta: Int, total: Int): Int =
        if (total == 0) -1 else Math.floorMod(clamp(index, total) + delta, total)

    /** What the bar opens with: the selected words, when they are on one line. */
    fun prefill(selected: String?): String? =
        selected?.takeIf { it.isNotEmpty() && "\n" !in it }
}
