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
     * where it begins, counted in code points as the file counts. A table
     * of more than [MAX_DRAWN_CELLS] cells is not [drawn] as a grid. A
     * [long] segment is lines holding a run longer than [MAX_UNBROKEN]
     * that the layout cannot break, or runs past what the body may give the
     * layout in all ([MAX_LAYOUT_COST]) -- a table's cells counted too, and
     * such a table is not [drawn] -- shown read-only; only a [field] is
     * typed in. A [heavy] one is [long] for another reason: past what one
     * body draws ([MAX_DRAWN_LINES], [MAX_SEGMENTS]), or one line heavier
     * to draw than a field may be ([MAX_FIELD_WEIGHT]).
     */
    data class Segment(
        val table: Boolean,
        val firstLine: Int,
        val lastLine: Int,
        val text: String,
        val cpStart: Int,
        val drawn: Boolean = true,
        val long: Boolean = false,
        val heavy: Boolean = false,
    ) {
        val isVirtual: Boolean get() = lastLine < firstLine

        /** A run of prose the person types into: neither a table nor a [long] line. */
        val field: Boolean get() = !table && !long
    }

    /**
     * The longest run of letters with no space in it that a text field is
     * given to lay out. Such a run is broken where it meets the edge, letter
     * by letter, and the cost grows with its square: on the emulator 20,000
     * letters opened in 0.2 s and 150,000 held the screen 12.6 s, against
     * 0.17 s for 150,000 with spaces (measured 2026-10-28). A share code of a
     * few hundred characters can bring one, in a body, a title, comments or
     * a checklist row, and the note froze at every opening; the code dialog
     * froze the same way on a code of 120,000.
     *
     * A run is what lies between two places where the layout can break a
     * line ([forEachRun]) -- not between two of what Kotlin calls
     * whitespace, which counts the no-break spaces the layout never breaks
     * at: 150,000 letters joined by them measured as runs of four.
     */
    const val MAX_UNBROKEN = 10_000

    /**
     * The most letter-by-letter work all the text of one body, one title or
     * one comments field may give the layout, as the sum of each run's
     * square: twice one run of [MAX_UNBROKEN], about 0.1 s on the emulator
     * by the figures above. A run is broken letter by letter wherever it
     * lies, so the work adds up across the lines: a hundred lines of 9,999
     * letters passed the line's own test one by one, and come by the same
     * figures to some 5.6 s in one field. Ordinary words add little -- a
     * page of prose a few thousandths of this, 300,000 characters of it
     * about a hundredth.
     */
    const val MAX_LAYOUT_COST = 2L * MAX_UNBROKEN * MAX_UNBROKEN

    /**
     * Every run of [text] the layout cannot break, by its length in UTF-16
     * units. A run ends at a line break, and where a line can certainly be
     * broken: after the spaces between two words, and between two
     * ideographs. Everywhere else it goes on -- through a no-break space
     * (U+00A0, U+2007, U+202F), a word joiner, a control character, a space
     * after an opening bracket or a quotation mark (Unicode's line-breaking
     * rules, UAX #14, keep "( (" together) -- so a run may be longer than
     * what the layout finds, never shorter: wrong, it shows a line
     * read-only that a field could have held, and never hands a field a line
     * it cannot break.
     */
    private inline fun forEachRun(text: CharSequence, each: (Int) -> Unit) {
        var run = 0
        // The last character before the spaces now being read.
        var lastSeen = '\n'
        for (i in 0 until text.length) {
            val c = text[i]
            if (c == '\n') {
                if (run > 0) each(run)
                run = 0
                lastSeen = '\n'
                continue
            }
            if (run > 0 && breaksBefore(text[i - 1], c, lastSeen)) {
                each(run)
                run = 0
            }
            run++
            if (c != ' ') lastSeen = c
        }
        if (run > 0) each(run)
    }

    /**
     * Whether a line can certainly be broken between [before] and [c], with
     * [lastSeen] the last character ahead of any spaces. After a space a
     * break is allowed (UAX #14's LB18) unless the spaces follow an opening
     * bracket, a quotation mark, a closing bracket or a dash (LB14 to LB17),
     * or what comes next may not start a line (LB7 to LB13): so only
     * between a letter, a digit or ,.;:!? and a letter or digit. Two
     * ideographs may always be broken between (LB31).
     */
    private fun breaksBefore(before: Char, c: Char, lastSeen: Char): Boolean =
        if (before == ' ') c.isLetterOrDigit() && (lastSeen.isLetterOrDigit() || lastSeen in ENDS_A_WORD)
        else isIdeograph(before) && isIdeograph(c)

    private const val ENDS_A_WORD = ",.;:!?"

    private fun isIdeograph(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c in '\uF900'..'\uFAFF'

    /** The longest run of [text] the layout cannot break ([forEachRun]), in UTF-16 units. */
    fun longestUnbroken(text: String): Int {
        var longest = 0
        forEachRun(text) { if (it > longest) longest = it }
        return longest
    }

    /**
     * The letter-by-letter work laying out [text] costs ([MAX_LAYOUT_COST]),
     * or null when one of its runs is longer than [MAX_UNBROKEN].
     */
    fun layoutCost(text: CharSequence): Long? {
        var cost = 0L
        var over = false
        forEachRun(text) {
            if (it > MAX_UNBROKEN) over = true
            cost += it.toLong() * it
        }
        return if (over) null else cost
    }

    /**
     * Whether [text] is too much to lay out -- a run past [MAX_UNBROKEN], or
     * runs that add up past [MAX_LAYOUT_COST]: shown read-only, never as a
     * field. Text no longer than one run's limit never is.
     */
    fun tooLongToLayOut(text: String): Boolean = runsTooLong(text) || tooHeavyToDraw(text)

    /** The first half of [tooLongToLayOut]: runs the layout breaks letter by letter, past its limits. */
    fun runsTooLong(text: String): Boolean =
        text.length > MAX_UNBROKEN && (layoutCost(text) ?: Long.MAX_VALUE) > MAX_LAYOUT_COST

    /**
     * The fewest letters a line of the editor holds on a phone: at the
     * body's size a narrow phone's line holds about forty. A field's lines
     * are counted as if each held this many ([drawnLines]): more lines than
     * the screen draws, never fewer.
     */
    const val WRAP = 32

    /** The lines a line of [length] UTF-16 units is drawn as, at [WRAP] letters a line; an empty one is one. */
    private fun drawnLines(length: Int): Int = maxOf(1, (length + WRAP - 1) / WRAP)

    /** The lines [text] is drawn as, at [WRAP] letters a line ([drawnLines]). */
    fun drawnLines(text: CharSequence): Long {
        var lines = 0L
        var run = 0
        for (i in 0 until text.length) {
            if (text[i] == '\n') {
                lines += drawnLines(run)
                run = 0
            } else {
                run++
            }
        }
        return lines + drawnLines(run)
    }

    /**
     * What drawing one field costs, beside laying it out: Compose draws a
     * field in a scrolling column whole, and each of its lines is drawn by a
     * call that copies the field's ENTIRE text again (simpleperf, 2026-10-27:
     * a field of 362,000 letters took nine seconds) -- so the work is its
     * length times its lines. The most one field may take, as that product:
     * a line of 150,000 letters with spaces, which opened in 0.17 s on the
     * emulator (measured 2026-10-28). A note of 200,000 empty lines, from a
     * share code of 666 characters, came to 4e10 -- some fifty times this.
     */
    const val MAX_FIELD_WEIGHT = 150_000L * ((150_000 + WRAP - 1) / WRAP)

    /** Whether [text], as one field, is heavier to draw than a field may be ([MAX_FIELD_WEIGHT]). */
    fun tooHeavyToDraw(text: CharSequence): Boolean =
        // No text shorter than the root can reach it: its lines are never more than its letters.
        text.length > 26_000 && text.length.toLong() * drawnLines(text) > MAX_FIELD_WEIGHT

    /**
     * The most lines one field of a body draws ([drawnLines]): past it, the
     * prose goes on in the next field, cut between two lines. A body is any
     * number of fields, so each is kept small enough that the letter typed in
     * it -- which draws that field again -- copies little: 1,000 lines, some
     * 32,000 letters, a fiftieth of [MAX_FIELD_WEIGHT].
     */
    const val FIELD_LINES = 1_000

    /**
     * The most lines all of a body's fields draw together ([drawnLines]), and
     * the most pieces it is drawn as: the editor draws every piece at once,
     * in one scrolling column. Past either, the rest of the body is one
     * piece shown read-only, its start and how many lines it holds -- the
     * note keeps every byte, Find finds it, and it can be read on. A note of
     * 7,000 lines of prose, 308,000 characters, draws 14,000 lines.
     *
     * The pieces were 100, and ordinary notes reached them: fifty weekly
     * tables under their headings, 2,331 characters, made the fiftieth and
     * all after it read-only, with nowhere to write at the end (the final
     * audit's fifth round). They are a ceiling for hostile notes only -- the
     * 3,000 tables of two cells a share code of a few hundred characters
     * brings -- set from what the emulator measured them to cost: at 500,
     * those tables open in 2.6 s, at 2,000 in 6.2; fifty weekly tables in
     * 0.65. The lines were 20,000, and a journal of 10,050 lines, 442,000
     * characters, had its end read-only: one of 40,000 drawn lines opens
     * in 1.1 s, against 0.8 at 20,000 (measured 2026-10-29).
     */
    const val MAX_DRAWN_LINES = 40_000
    const val MAX_SEGMENTS = 500

    /**
     * The most cells a table may have to be drawn as a grid. Every cell is
     * a piece of the screen of its own, laid out and measured when the note
     * opens, all of them at once: a table of 300 by 300, 90,000 cells, came
     * in a share code of 2.9 KB, and the note it was in would not open.
     * Past this a table is shown as a few of its lines, read-only, and
     * nothing in the note changes. Written into the text around it, as
     * first done, it opened in nine seconds on the emulator: a field of
     * 362,000 letters is laid out whole and drawn whole, each of its
     * thousands of lines copying all of its text again (measured,
     * 2026-10-27). And a grid costs one to two milliseconds a cell on the
     * emulator, measured from the tap to the last frame drawn: 100 cells
     * 0.4 s, 400 cells 1.0 s, 1,000 cells 2.0 s, 2,000 cells 2.7 s. 1,000 --
     * fifty rows of twenty columns, past any table written by hand -- keeps
     * the slowest grid near two seconds on that slow machine; the walk's
     * big_table times one at the line.
     *
     * It is what a whole body draws, all its tables together: asked of each
     * table alone, ten tables of 1,000 cells, from a share code of 350
     * characters, were drawn as 10,000. A table past what is left is shown
     * as one too large is.
     */
    const val MAX_DRAWN_CELLS = 1_000

    /**
     * The longest checklist row written in as a field: the desktop's
     * MAX_CHECKLIST_ITEM_CHARS, the most its box adds and the most of a row
     * it draws -- counted as Python counts, an emoji one ([rowChars]). A
     * longer row is shown read-only, kept whole, and read there; the row's
     * field and the box for a new one take no more ([fitRow]).
     */
    const val CHECKLIST_ROW_CHARS = 2_000

    /**
     * A row's length as the desktop weighs it: in code points. Counted in
     * UTF-16 units, 1,200 emoji typed kept 1,000, and a desktop row of 1,500
     * emoji, an ordinary row there, was read-only on the phone (the final
     * audit's sixth round).
     */
    fun rowChars(text: String): Int = text.codePointCount(0, text.length)

    /**
     * What a row's field, or a box for a new row, keeps of an edit from
     * [was] to [now]: the [text] kept; the cursor's place in it, in UTF-16
     * units, when what came in was cut ([caret]; null leaves the field's
     * own); and what is [said] of it, in the desktop's words.
     */
    data class Fit(val text: String, val caret: Int?, val said: String?)

    /**
     * The desktop's `_limit_entry`, for a row's field and a box for a new
     * row: an edit within [limit] -- or one leaving the words shorter than
     * they were -- stands. Past it, the words already there stay whole and
     * only what came in is cut, to the room left, never through an emoji:
     * "Pasted text shortened: ...". With no room, nothing comes in: "... can
     * be up to". `take` over the whole value kept the first 2,000 of the
     * edit's result, so a letter or a paste anywhere but at the end cut the
     * row's own last words -- out of sight in a field of one line, with no
     * Undo and nothing said (the final audit's sixth round).
     */
    fun fitRow(was: String, now: String, limit: Int = CHECKLIST_ROW_CHARS): Fit {
        val length = rowChars(now)
        if (length <= limit || length < rowChars(was)) return Fit(now, null, null)
        // What the edit put in: the words before it and after it read the
        // same in both, each end on the edge of a letter, never inside an emoji.
        val most = minOf(was.length, now.length)
        var head = 0
        while (head < most && was[head] == now[head]) head++
        if (head > 0 && Character.isHighSurrogate(now[head - 1])) head--
        var tail = 0
        while (tail < most - head && was[was.length - 1 - tail] == now[now.length - 1 - tail]) tail++
        if (tail > 0 && Character.isLowSurrogate(now[now.length - tail])) tail--
        val before = was.substring(0, head)
        val after = was.substring(was.length - tail)
        val came = now.substring(head, now.length - tail)
        val room = limit - rowChars(before) - rowChars(after)
        val count = String.format(java.util.Locale.ENGLISH, "%,d", limit)
        if (room <= 0) return Fit(was, null, "A checklist item can be up to $count characters")
        val cut = came.offsetByCodePoints(0, room)
        return Fit(before + came.substring(0, cut) + after, head + cut,
                   "Pasted text shortened: a checklist item can be up to $count characters")
    }

    /**
     * [fitRow] for one editor's boxes, what it says said once while edits go
     * on meeting the cap -- a keyboard's letters at the cap are many, and the
     * phone queues a bar for each -- and again after an edit that fitted.
     */
    class RowCap(private val say: (String) -> Unit) {
        private var said = false

        fun fit(was: String, now: String): Fit = fitRow(was, now).also { fit ->
            if (fit.said == null) said = false
            else if (!said) {
                said = true
                say(fit.said)
            }
        }
    }

    /**
     * The most letters a checklist's rows draw as fields together, from the
     * top; past it a row is shown short, read-only, kept whole. Each row was
     * weighed alone, and every row is drawn at once in the note's scrolling
     * column: 120 rows of 150,000 letters, from a share code of 38 KB, were
     * 18 million letters laid out at each opening (the final audit's fifth
     * round). A thousand rows of a hundred letters -- a long list written by
     * hand -- are drawn whole.
     */
    const val CHECKLIST_CHARS = 100_000

    /**
     * How many of a checklist's first rows are drawn, and how many more each
     * "Show" draws: every row is a piece of the screen of its own, drawn at
     * once in the note's column, and 1,000 short rows held the screen 5.3 s
     * as the note opened on the emulator, whatever their letters (measured,
     * the final audit's fifth round). The last [CHECKLIST_TAIL_ROWS] are
     * drawn too, where a new item lands.
     */
    const val CHECKLIST_ROWS = 200
    const val CHECKLIST_TAIL_ROWS = 5

    /**
     * The rows of a checklist of [size] that are drawn, in order: its first
     * [head] and its last [tail] -- all of them when that leaves out no more
     * than a few -- and the rows up to [reach], a row a search stepped to,
     * when it lies in the rows between. A row already drawn among the last
     * -- where the newest item lands, and is looked for -- reached past
     * them all and drew every row, 1,000 of them at each letter typed in
     * the bar (the final audit's sixth round).
     */
    fun checklistShown(size: Int, head: Int, reach: Int = -1, tail: Int = CHECKLIST_TAIL_ROWS): List<Int> {
        val last = size - tail
        val first = if (reach in head until last) reach + 1 else head
        if (last - first <= CHECKLIST_TAIL_ROWS) return (0 until size).toList()
        return (0 until first).toList() + (last until size).toList()
    }

    /**
     * The [checklistShown] window -- its head and its tail -- after the row
     * at [from] moved to [to], in a list of [size]: wide enough that every
     * row drawn before is drawn after, the rows the move pushed across an
     * edge of the stretch not drawn included, and the row moved too -- into
     * that stretch, it is drawn there, the window one row wider on the side
     * it came from. Counted over every row, a step from the edge of the rows
     * not drawn put the row among them: it vanished, a row never shown stood
     * in its place, and a screen reader's next Move moved that one (the
     * final audit's sixth round).
     */
    fun keepMovedInSight(size: Int, head: Int, tail: Int, reach: Int, from: Int, to: Int): Pair<Int, Int> {
        val shown = checklistShown(size, head, reach, tail)
        if (shown.size == size) return head to tail
        // Where the head drawn now ends, a search's reach in it, and where the tail begins.
        val ends = shown.indices.first { shown[it] != it }
        val begins = size - tail
        fun after(i: Int) = when {
            i == from -> to
            from < to && i in from + 1..to -> i - 1
            to < from && i in to until from -> i + 1
            else -> i
        }
        var h = head
        var t = tail
        // A move shifts the rows between its ends by one, so the last two of
        // the head and the first two of the tail say where each edge went.
        for (i in listOf(ends - 1, ends - 2)) if (i >= 0 && i != from) h = maxOf(h, after(i) + 1)
        for (i in listOf(begins, begins + 1)) if (i < size && i != from) t = maxOf(t, size - after(i))
        if (to >= h && to < size - t) {
            if (from >= begins) t = size - to else h = to + 1
        }
        return h to t
    }

    /**
     * Where a row dragged from [from] lands: on the drawn row whose middle
     * is nearest the dragged row's middle, now at [at] -- [middles] holding
     * each drawn row's. Counted in rows of one height from where it began,
     * the "N more rows" line between the rows drawn was counted as a row and
     * the rows it stands for as none, and a drop under the last row before
     * it landed among rows not drawn (the final audit's sixth round).
     */
    fun dropTarget(middles: Map<Int, Float>, at: Float, from: Int): Int =
        middles.minByOrNull { (_, middle) -> kotlin.math.abs(middle - at) }?.key ?: from

    /**
     * Which of a checklist's [rows] are written in as fields: each no
     * longer than [CHECKLIST_ROW_CHARS], all of them together no more than
     * [CHECKLIST_CHARS] -- in letters, as the desktop counts them ([rowChars]).
     */
    fun checklistFields(rows: List<String>): BooleanArray {
        var left = CHECKLIST_CHARS
        return BooleanArray(rows.size) { i ->
            val length = rowChars(rows[i])
            (length <= CHECKLIST_ROW_CHARS && length <= left).also { if (it) left -= length }
        }
    }

    /** Whether a checklist row is longer than one is written in ([CHECKLIST_ROW_CHARS]), or too long to lay out. */
    fun rowTooLong(text: String): Boolean = rowChars(text) > CHECKLIST_ROW_CHARS || tooLongToLayOut(text)

    /**
     * A row shown to be read, in parts of at most [chars] UTF-16 units that a
     * phone's width wraps: each line of it, and each long line cut after its
     * last space near the end of a part -- or, with none there, where the
     * part ends, never through an emoji. Each part is its start and end in
     * the row; a line break belongs to none.
     */
    fun rowParts(text: String, chars: Int = ROW_PART_CHARS): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            var start = lineStart
            do {
                var end = minOf(lineEnd, start + chars)
                if (end < lineEnd) {
                    val space = text.lastIndexOf(' ', end - 1)
                    if (space >= end - ROW_PART_SLACK && space > start) end = space + 1
                    else if (Character.isLowSurrogate(text[end])) end--
                }
                out.add(start to end)
                start = end
            } while (start < lineEnd)
            lineStart = lineEnd + 1
        }
        return out
    }

    /** How long a part of a row shown to be read is ([rowParts]), and how far back a space is looked for. */
    const val ROW_PART_CHARS = 300
    private const val ROW_PART_SLACK = 60

    /**
     * How many cells a table's [lines] are drawn as: every row padded to
     * the widest, as [parseTable] pads it. Counted as written, a first row
     * of 500 cells over 250 rows of two came to 1,000 -- under the line --
     * and was drawn as 125,500, from a share code of 174 characters.
     */
    fun gridCells(lines: List<String>): Long {
        var widest = 0
        for (line in lines) widest = maxOf(widest, splitRow(line).size)
        return lines.size.toLong() * widest
    }

    /**
     * The last body cut and its segments: the editor asks for the same body's
     * segments several times for one letter typed, and a long body is now
     * weighed run by run ([layoutCost]) at each asking.
     */
    @Volatile private var lastCut: Pair<String, List<Segment>>? = null

    fun segments(body: String): List<Segment> {
        lastCut?.let { (cut, segments) -> if (cut == body) return segments }
        return cut(body).also { lastCut = body to it }
    }

    private fun cut(body: String): List<Segment> {
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
        fun text(first: Int, last: Int) = lines.subList(first, last + 1).joinToString("\n")
        val out = ArrayList<Segment>()
        // What the lines given to fields so far cost to lay out, in the
        // body's order ([MAX_LAYOUT_COST]): a line that would take it past
        // the limit is shown read-only too. A body no longer than one run's
        // limit cannot reach it, and is not weighed at all.
        var spent = 0L
        val weighed = body.length > MAX_UNBROKEN
        // Asked once of each line, in order: it counts what it lets in.
        fun tooMuch(i: Int): Boolean {
            if (!weighed) return false
            val cost = layoutCost(lines[i]) ?: return true
            if (spent + cost > MAX_LAYOUT_COST) return true
            spent += cost
            return false
        }
        // A table's rows, asked once, as a whole: its cells, each laid out on
        // its own, counted as lines are counted.
        fun cellsTooMuch(rows: List<String>): Boolean {
            if (!weighed) return false
            var cost = 0L
            for (row in rows) for (cell in splitRow(row)) cost += layoutCost(cell) ?: return true
            if (spent + cost > MAX_LAYOUT_COST) return true
            spent += cost
            return false
        }
        // What the body may still draw ([MAX_DRAWN_CELLS], [MAX_DRAWN_LINES]),
        // and the line from which the rest of it is one read-only piece,
        // once that or the number of pieces ([MAX_SEGMENTS]) runs out.
        var cellsLeft = MAX_DRAWN_CELLS.toLong()
        var linesLeft = MAX_DRAWN_LINES.toLong()
        var rest = -1
        // One place is kept for the rest itself.
        fun room(at: Int): Boolean {
            if (out.size < MAX_SEGMENTS - 1) return true
            rest = at
            return false
        }
        // A run of prose lines, each run of lines too much to lay out cut
        // out of it as a segment of its own, shown read-only: the lines
        // around it stay fields, and the note keeps every byte.
        fun prose(first: Int, last: Int) {
            // Asked once of each line, in order ([tooMuch] counts what it
            // lets in); a line heavier to draw than a field may be is not
            // let in, and so not counted.
            val shown = BooleanArray(last - first + 1) { k ->
                val i = first + k
                !(weighed && tooHeavyToDraw(lines[i])) && !tooMuch(i)
            }
            fun readOnly(i: Int) = !shown[i - first]
            // Empty lines between two read-only ones are read-only too: an
            // empty line costs the layout nothing and was let in, so once
            // the limit was reached "x", "", "x", "" ... came out as ten
            // thousand pieces, every other one a field.
            var k = 0
            while (k < shown.size) {
                if (!shown[k] || lines[first + k].isNotEmpty()) {
                    k++
                    continue
                }
                var end = k
                while (end + 1 < shown.size && shown[end + 1] && lines[first + end + 1].isEmpty()) end++
                if (k > 0 && !shown[k - 1] && end + 1 < shown.size && !shown[end + 1]) {
                    for (j in k..end) shown[j] = false
                }
                k = end + 1
            }
            var i = first
            while (i <= last) {
                if (!room(i)) return
                if (readOnly(i)) {
                    var end = i
                    while (end + 1 <= last && readOnly(end + 1)) end++
                    val heavy = (i..end).any { tooHeavyToDraw(lines[it]) }
                    out.add(Segment(false, i, end, text(i, end), starts[i], long = true, heavy = heavy))
                    i = end + 1
                    continue
                }
                // A field: lines while it draws no more than its own share
                // ([FIELD_LINES]) and the body has lines left -- at least one
                // line, which is never cut.
                var end = i - 1
                var drawn = 0L
                while (end + 1 <= last && !readOnly(end + 1)) {
                    val more = drawnLines(lines[end + 1].length).toLong()
                    if (end >= i && drawn + more > FIELD_LINES) break
                    if (more > linesLeft) {
                        rest = end + 1
                        break
                    }
                    drawn += more
                    linesLeft -= more
                    end++
                }
                if (end >= i) out.add(Segment(false, i, end, text(i, end), starts[i]))
                if (rest >= 0) return
                i = end + 1
            }
        }

        var line = 0
        for (block in tableBlocks(body)) {
            if (block.first > line) prose(line, block.first - 1)
            if (rest >= 0 || !room(block.first)) break
            val rows = lines.subList(block.first, block.last + 1)
            val cells = gridCells(rows)
            val gridded = cells <= cellsLeft
            // A grid's cells are text laid out like any other, each in a
            // column a few words wide: a row of two cells, one of them
            // 150,000 letters long, passed the count of cells, was drawn,
            // and froze the note as the line alone would have. Weighed as
            // lines are, it is shown as one -- read-only, its start below.
            val tooMuch = gridded && cellsTooMuch(rows)
            if (gridded && !tooMuch) cellsLeft -= cells
            out.add(Segment(true, block.first, block.last, text(block.first, block.last), starts[block.first],
                drawn = gridded && !tooMuch, long = tooMuch))
            line = block.last + 1
        }
        if (rest < 0 && line < lines.size) prose(line, lines.size - 1)
        if (rest >= 0) {
            // Past what the body draws: the rest, read-only, its start and
            // its count shown. No room to write is offered after it -- a line
            // written there would be part of it, and read-only at once.
            out.add(Segment(false, rest, lines.size - 1, text(rest, lines.size - 1), starts[rest], long = true, heavy = true))
            return out
        }
        if (out.isEmpty() || !out.last().field) {
            // The body ends with a table, or a line too long to edit: room
            // to write after it.
            out.add(Segment(false, lines.size, lines.size - 1, "", starts[lines.size]))
        }
        return out
    }

    /**
     * The body with one prose segment's lines replaced by [newText]. A
     * virtual segment appends a line only when something was typed.
     */
    fun replaceProse(body: String, segment: Segment, newText: String): String {
        require(segment.field) { "a table is changed through TableEdit, and a line too long to lay out not at all" }
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
     * Where a change from [old] to [new] ended in [new], in code points:
     * after its last changed character -- where the cursor stands after
     * typing, for a field that does not say, and where Undo puts it. The
     * ends are matched only outside what the starts matched, so Enter at
     * the end of "a\n" ends after the new break, not before it.
     */
    fun changeEndPoints(old: String, new: String): Int {
        val a = old.codePoints().toArray()
        val b = new.codePoints().toArray()
        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
        return b.size - tail
    }

    /** The same place in UTF-16 units, as a text field and its layout count. */
    fun changeEnd(old: String, new: String): Int = new.offsetByCodePoints(0, changeEndPoints(old, new))

    /**
     * A field's change taken once. Samsung's keyboard, pressing Enter on a
     * word it is still composing, finishes the word and presses the key in
     * one batch, and the field reports the result twice, a millisecond
     * apart, both times from the value before it. The note's text applies
     * a change to the body as it is now, so the second report broke a second
     * line -- every Enter after a word skipped one (the owner's phone,
     * 2026-10-23, read off the field's own log). The same change from the
     * same value within [WINDOW_MS] is the same report; a person cannot
     * repeat a keystroke from the very same state that fast.
     */
    class Once {
        private var from: Any? = null
        private var to: Any? = null
        private var at = Long.MIN_VALUE

        /** Whether a change from [from] to [to], at [now] in milliseconds, is news. */
        fun news(from: Any, to: Any, now: Long): Boolean {
            val again = from == this.from && to == this.to && now - at in 0 until WINDOW_MS
            this.from = from
            this.to = to
            at = now
            return !again
        }

        companion object {
            const val WINDOW_MS = 150L
        }
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

    /** The desktop's picker: up to 6 rows (24 by number) and 2 to 8 columns. */
    const val TABLE_ROWS = 6
    const val TABLE_COLUMNS = 8

    /** `blank_table`: a new empty table, padded as the desktop pads one. */
    fun blankTable(rows: Int, columns: Int): List<String> {
        val r = maxOf(1, rows)
        val c = maxOf(1, columns)
        return formatTable(List(r) { List(c) { "" } }, List(r) { List(c) { CellAlign.LEFT } })
    }

    /**
     * `_grid_delete_table`: lines [first]..[last] gone with the break after
     * them -- or, when they end the note, the break before them stays, as
     * the desktop's text keeps its line.
     */
    fun deleteLines(body: String, first: Int, last: Int): String {
        val lines = body.split("\n")
        val a = first.coerceIn(0, lines.lastIndex)
        val b = last.coerceIn(a, lines.lastIndex)
        val kept = lines.subList(0, a) + lines.subList(b + 1, lines.size)
        return if (b == lines.lastIndex && a > 0) (kept + "").joinToString("\n") else kept.joinToString("\n")
    }

    /** A body with a table put in, and the line the table starts on. */
    data class Inserted(val body: String, val firstLine: Int)

    /**
     * The most the editor's Undo keeps, in characters ([History]): some 32
     * megabytes at two bytes a letter, the most a phone's string takes. 200
     * steps of a page of prose, 50 of a journal of 300,000 characters, 8 of
     * a note of two million; a note longer than this keeps its last step.
     */
    const val HISTORY_CHARS = 16_000_000L

    /**
     * The editor's Undo and Redo: the states a note went through, newest
     * last. Typing is one step per pause -- letters typed within [pause] of
     * each other, of the same kind, join the step before -- so one Undo
     * takes back a word or a burst of words, not a letter; a format, a list
     * key, a table edit is a step of its own. At most [limit] steps are
     * kept. A new change after an Undo forgets what Redo would have brought
     * back, as every editor's does.
     *
     * And at most [budget] in all, by what [weigh] says each state holds:
     * a step is the whole note as it was, and 200 of a note of two million
     * letters -- from a share code of 8 KB -- were 760 MB, far past what a
     * phone gives an app (the final audit's fifth round). The oldest go
     * first, but never the newest, so the last change can always be undone.
     */
    class History<T>(
        private val limit: Int = 200,
        private val pause: Long = 1000,
        private val budget: Long = Long.MAX_VALUE,
        private val weigh: (T) -> Long = { 0L },
    ) {
        private val past = ArrayDeque<T>()
        private val future = ArrayDeque<T>()
        private var lastKind: String? = null
        private var lastAt = Long.MIN_VALUE
        // What [past] and [future] hold together, as [weigh] counts it.
        private var held = 0L

        val canUndo: Boolean get() = past.isNotEmpty()
        val canRedo: Boolean get() = future.isNotEmpty()

        /** How many steps Undo can take now. */
        val steps: Int get() = past.size

        /**
         * [before] is the state just before a change of [kind] made at [now]
         * (milliseconds). A change of "typing" soon after another joins it:
         * the state kept is the one before the first.
         */
        fun record(before: T, kind: String, now: Long) {
            // Typing joins typing in the same field: "typing", "typing:title".
            val joins = kind.startsWith(TYPING) && kind == lastKind && now - lastAt in 0..pause && past.isNotEmpty()
            for (state in future) held -= weigh(state)
            future.clear()
            if (!joins) {
                past.addLast(before)
                held += weigh(before)
                while (past.size > limit) held -= weigh(past.removeFirst())
                trim()
            }
            lastKind = kind
            lastAt = now
        }

        /** The oldest steps forgotten while all of them hold more than the budget -- the newest kept. */
        private fun trim() {
            while (held > budget && past.size > 1) held -= weigh(past.removeFirst())
        }

        /** The state to go back to, given the one on screen now -- kept for Redo. */
        fun undo(current: T): T? {
            val back = past.removeLastOrNull() ?: return null
            held -= weigh(back)
            future.addLast(current)
            held += weigh(current)
            lastKind = null
            return back
        }

        /** The state Undo left, given the one on screen now. */
        fun redo(current: T): T? {
            val forward = future.removeLastOrNull() ?: return null
            held -= weigh(forward)
            past.addLast(current)
            held += weigh(current)
            trim()
            lastKind = null
            return forward
        }

        /** Everything forgotten: the note was read again from the file. */
        fun clear() {
            past.clear()
            future.clear()
            held = 0L
            lastKind = null
        }

        companion object {
            const val TYPING = "typing"
        }
    }

    // ---- a table's caption ----

    // Python's \d is any decimal digit, not only 0-9; re.match holds the start.
    private val CAPTION_LABEL = Regex("^(?:Table|Tab\\.) \\p{Nd}+:")

    /**
     * A caption's label line, "Table 3: ..." -- or "Tab. 3:", as notes from
     * before the labels went English have it. Prose that merely begins with
     * the word is not one: the number and the colon are. The desktop's
     * `is_caption_line`.
     */
    fun isCaptionLine(line: String): Boolean = CAPTION_LABEL.containsMatchIn(PyText.strip(line))

    /** The line a caption keeps its source on: `is_source_line`. */
    fun isSourceLine(line: String): Boolean = PyText.strip(line).startsWith("Source:")

    /**
     * The caption right under a table ending on line [last]: its label line
     * and the source line after it, as line numbers -- only one touching its
     * table belongs to it. Empty when the line under the table is not one.
     */
    fun captionLines(lines: List<String>, last: Int): List<Int> {
        val label = last + 1
        if (label !in lines.indices || !isCaptionLine(lines[label])) return emptyList()
        return if (label + 1 in lines.indices && isSourceLine(lines[label + 1])) listOf(label, label + 1) else listOf(label)
    }

    /** A body with a caption put under a table: the label's line, and the placeholder to select, in code points. */
    data class Captioned(val body: String, val line: Int, val selectStart: Int, val selectEnd: Int)

    /**
     * The desktop's "Add caption below…": a numbered label line and a source
     * line under the table ending on line [last], ordinary text after that.
     * Numbered after every caption the note already has, the older "Tab. N:"
     * ones included, so an older note's count carries on; "description"
     * comes selected, so typing replaces it.
     */
    fun addCaption(body: String, last: Int): Captioned {
        val lines = body.split("\n")
        val n = 1 + lines.count { isCaptionLine(it) }
        val label = "Table $n: description"
        val out = lines.take(last + 1) + label + "Source: " + lines.drop(last + 1)
        val lineStart = out.take(last + 1).sumOf { it.codePointCount(0, it.length) + 1 }
        val end = lineStart + label.length
        return Captioned(out.joinToString("\n"), last + 1, end - "description".length, end)
    }

    /**
     * `_insert_table`: a blank table at the cursor ([caret], in code points),
     * on lines of its own -- a break before it when the cursor's line holds
     * something or a table sits right above, so two tables never fuse into
     * one; a break after it, so there is somewhere to carry on writing, and
     * a second one when a table follows right below. A cursor past the end
     * -- the empty field under a table that closes the note -- is a new line
     * there.
     */
    fun insertTable(body: String, caret: Int, rows: Int, columns: Int): Inserted {
        val r = rows.coerceIn(1, TABLE_ROWS * 4)
        val c = columns.coerceIn(2, TABLE_COLUMNS)
        var text = body
        val length = body.codePointCount(0, body.length)
        var at = caret.coerceAtLeast(0)
        if (at > length) {
            text = body + "\n"
            at = length + 1
        }
        val cut = text.offsetByCodePoints(0, at)
        val lines = text.split("\n")
        val lineNo = text.substring(0, cut).count { it == '\n' }
        val line = lines[lineNo]
        val above = lines.getOrElse(lineNo - 1) { "" }
        val below = lines.getOrElse(lineNo + 1) { "" }
        val before = if (PyText.strip(line).isNotEmpty() || isTableRow(above)) "\n" else ""
        val after = "\n" + if (isTableRow(below)) "\n" else ""
        val block = blankTable(r, c).joinToString("\n")
        return Inserted(text.substring(0, cut) + before + block + after + text.substring(cut),
                        lineNo + if (before.isNotEmpty()) 1 else 0)
    }
}
