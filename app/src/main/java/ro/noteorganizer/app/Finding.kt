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

        for ((index, segment) in Editing.segments(body).withIndex()) {
            if (found.size >= CAP) break
            if (!segment.table) {
                inText(segment.text) { start, end -> InText(index, start, end) }
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
        for ((item, words) in checklist.withIndex()) {
            if (found.size >= CAP) break
            inText(words) { start, end -> InChecklist(item, start, end) }
        }
        if (found.size < CAP) inText(comments) { start, end -> InComments(start, end) }
        return found
    }

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
