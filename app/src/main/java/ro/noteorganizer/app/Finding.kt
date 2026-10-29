package ro.noteorganizer.app

import java.util.Locale

/**
 * Find in note: the desktop's find bar, as rules.
 *
 * It looks through the open note's body -- its prose and its tables' cells
 * -- then its checklist and comments, and never changes any of it. Matches come in reading order, each one a place
 * the editor can paint and scroll to; the bar counts them "3/12" and steps
 * through them, round from the last to the first.
 *
 * The desktop's own rules, kept:
 *
 *  - Case does not count unless asked ("Aa"). Prose compares letter by
 *    letter, as Tk's own search does; a cell compares its lower-cased text,
 *    as the desktop's cell scan does.
 *  - Matches may overlap: "aa" is found twice in "aaa", because each search
 *    starts one character after the last match began.
 *  - A cell is one match however often its words occur in it, and is
 *    searched as it reads: a line break inside it is a space; a merge
 *    marker or a checkbox is not a word.
 *  - At most [CAP] matches, and the count then says so with a "+".
 *
 * One rule differs, on purpose: the desktop steps through a table's
 * matches column by column -- a side effect of how it sorts them -- and the
 * phone goes row by row, the order a table is read in.
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

    /** Letter by letter, so every position in the text stays where it was. */
    private fun foldEach(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            out.appendCodePoint(Character.toLowerCase(cp))
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private const val MERGE_LEFT = "<"
    private const val MERGE_UP = "^"

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
        val needle = if (matchCase) query else foldEach(query)
        val cellNeedle = if (matchCase) query else query.lowercase(Locale.ROOT)

        // Letter by letter, each search one character after the last match began.
        fun inText(text: String, add: (Int, Int) -> Match) {
            val hay = if (matchCase) text else foldEach(text)
            var at = hay.indexOf(needle)
            while (at >= 0 && found.size < CAP) {
                found.add(add(at, at + needle.length))
                at = hay.indexOf(needle, at + Character.charCount(hay.codePointAt(at)))
            }
        }

        // Read-only text: the first match on each line, its line one stop.
        fun inLines(text: String, add: (Int, Int) -> Match) {
            val hay = if (matchCase) text else foldEach(text)
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
            // A piece shown read-only -- lines too long to lay out, the rest
            // past what the note draws, a table too large to draw -- is
            // searched as written, each line one stop however often its words
            // occur in it, as a cell is: the piece shows the line of the stop
            // stepped to, cut around it (LongText). Skipped, the bar said
            // "0/0" in the colour of a word found nowhere, while the list's
            // search found the note by it (the final audit's fifth round); and
            // a stop at every match, a table of 90,000 cells used up the count
            // before the words after it. A table not drawn is never parsed at
            // every letter typed.
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
                    val hay = if (matchCase) text else text.lowercase(Locale.ROOT)
                    if (cellNeedle in hay) found.add(InCell(index, r, c))
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

    /** Where each of [lines] starts, as one text joined by line breaks. */
    fun lineStarts(lines: List<String>): IntArray {
        val starts = IntArray(lines.size)
        var at = 0
        for (i in lines.indices) {
            starts[i] = at
            at += lines[i].length + 1
        }
        return starts
    }

    /** The line holding offset [at], by its [starts] ([lineStarts]). */
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
