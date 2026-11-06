package ro.noteorganizer.app

/**
 * A note's body as the editor shows it: prose the person types into, and
 * tables drawn as grids between it. A phone's text field cannot hold a
 * grid, as the desktop's text box does, so the body is cut into SEGMENTS
 * by line: each run of prose a field, each table a grid. Typing in one
 * rewrites only its lines, the body put back together around it -- one
 * body in the file, as the desktop stores it, styles moved by [remapSpans].
 * A body ending in a table gets an empty prose segment after it, to keep
 * writing in; it adds a line only once something is typed.
 */
object Editing {

    /**
     * One piece of the body, by the body's own line numbers, [lastLine]
     * inclusive (past the end: `lastLine == firstLine - 1`); [cpStart] in code
     * points. Not [drawn]: a table past [MAX_DRAWN_CELLS] or the layout's
     * budget. [long]: shown read-only, a run past [MAX_UNBROKEN] or the budget
     * ([MAX_LAYOUT_COST]); only a [field] is typed in. [heavy]: long for
     * another reason ([MAX_DRAWN_LINES], [MAX_SEGMENTS], [MAX_FIELD_WEIGHT]).
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
     * The longest run with no break in it a text field is given to lay out:
     * broken letter by letter at the edge, its cost grows with its square --
     * 150,000 letters held the screen 12.6 s, against 0.17 s with spaces, and
     * a share code can bring one. A run lies between places the layout can
     * break ([forEachRun]), not between Kotlin's whitespace, which counts the
     * no-break spaces the layout never breaks at.
     */
    const val MAX_UNBROKEN = 10_000

    /**
     * The most letter-by-letter work one body, title or comments field may
     * give the layout, as the sum of each run's square: twice one run of
     * [MAX_UNBROKEN], about 0.1 s. The work adds up across lines -- a hundred
     * lines of 9,999 letters each passed alone, some 5.6 s together -- while
     * ordinary words add little: 300,000 characters of prose, a hundredth.
     */
    const val MAX_LAYOUT_COST = 2L * MAX_UNBROKEN * MAX_UNBROKEN

    /**
     * Every run of [text] the layout cannot break, in UTF-16 units: ended at
     * a line break, after the spaces between two words, and between two
     * ideographs, and going on through no-break spaces, joiners, controls and
     * a space after a bracket or quote (UAX #14). Longer than the layout's,
     * never shorter: wrong, it shows read-only what a field could have held,
     * and never hands a field a line it cannot break.
     */
    internal inline fun forEachRun(text: CharSequence, each: (Int) -> Unit) {
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
     * Whether a line can certainly be broken between [before] and [c],
     * [lastSeen] the last character ahead of any spaces: after a space
     * (LB18) only between a letter, a digit or ,.;:!? and a letter or digit,
     * as brackets, quotes and dashes keep their spaces (LB14-17) and some
     * characters may not start a line (LB7-13); between two ideographs always (LB31).
     */
    internal fun breaksBefore(before: Char, c: Char, lastSeen: Char): Boolean =
        if (before == ' ') c.isLetterOrDigit() && (lastSeen.isLetterOrDigit() || lastSeen in ENDS_A_WORD)
        else isIdeograph(before) && isIdeograph(c)

    private const val ENDS_A_WORD = ",.;:!?"

    private fun isIdeograph(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c in '\uF900'..'\uFAFF'

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
     * What drawing one field costs beside laying it out: Compose draws it
     * whole, each line by a call copying the ENTIRE text again (a field of
     * 362,000 letters took nine seconds), so the work is length times lines.
     * The most a field may take: a line of 150,000 letters with spaces, 0.17 s;
     * 200,000 empty lines from a 666-character code came to fifty times this.
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
     * The most lines a body's fields draw together ([drawnLines]), and the
     * most pieces, all drawn at once: past either, the rest is one read-only
     * piece, every byte kept and found by Find. A ceiling for hostile notes
     * only (ISSUES_LOG, "A ceiling for hostile notes that ordinary ones"):
     * measured, 500 pieces open in 2.6 s, 40,000 lines in 1.1 s, where at 100
     * and 20,000 fifty weekly tables and a long journal ended read-only.
     */
    const val MAX_DRAWN_LINES = 40_000
    const val MAX_SEGMENTS = 500

    /**
     * The most cells a whole body draws as grids, its tables together: each
     * cell is laid out when the note opens, and 90,000 from a 2.9 KB code kept
     * it shut. Past it a table shows a few lines, read-only, nothing changed.
     * A cell costs one to two milliseconds on the emulator; 1,000 -- fifty
     * rows of twenty, past any table written by hand -- keeps the slowest grid
     * near two seconds (the walk's big_table times one at the line).
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
     * emoji, ordinary there, was read-only on the phone.
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
     * The desktop's `_limit_entry`, for every box kept to a length: an edit
     * within [limit], or leaving the words shorter, stands; past it, the words
     * there stay whole and only what came in is cut, never through an emoji,
     * and said ("Pasted text shortened", or [shortened]) only when words were
     * left out. What came in is read up to the [cursor] ([changeAt]), as the
     * desktop inserts at its mark -- not `take` over the whole value.
     */
    fun fitRow(was: String, now: String, limit: Int = CHECKLIST_ROW_CHARS,
               what: String = "A checklist item", shortened: String? = null, cursor: Int? = null): Fit {
        val change = changeAt(was, now, cursor)
        val before = was.substring(0, change.head)
        val after = was.substring(was.length - change.tail)
        // Each line break one letter in what came in, as the desktop's _cap_paste
        // puts a paste: Windows' clipboard keeps CR LF, and a paste that fitted
        // was weighed at two a line, cut and said shortened.
        val came = lineBreaks(change.came)
        val whole = if (came == change.came) now else before + came + after
        val length = rowChars(whole)
        if (length <= limit || length < rowChars(was)) {
            return Fit(whole, if (whole == now) null else change.head + came.length, null)
        }
        val room = limit - rowChars(before) - rowChars(after)
        val count = String.format(java.util.Locale.ENGLISH, "%,d", limit)
        if (room <= 0) return Fit(was, null, "$what can be up to $count characters")
        val cut = came.offsetByCodePoints(0, room)
        val leftOut = PyText.strip(came.substring(cut)).isNotEmpty()
        return Fit(before + came.substring(0, cut) + after, change.head + cut,
                   if (!leftOut) null
                   else shortened ?: "Pasted text shortened: ${what.take(1).lowercase()}${what.drop(1)} can be up to $count characters")
    }

    /**
     * What an edit from [was] to [now] put in: the words before it ([head],
     * in UTF-16 units) and after it ([tail]) read the same in both, each end
     * on the edge of a letter, never inside an emoji; [came] is what lies
     * between them in [now]. An edit is one change at one place, as typing,
     * a paste and a cut each are.
     */
    data class Change(val head: Int, val tail: Int, val came: String)

    fun changeOf(was: String, now: String): Change {
        val most = minOf(was.length, now.length)
        var head = 0
        while (head < most && was[head] == now[head]) head++
        if (head > 0 && Character.isHighSurrogate(now[head - 1])) head--
        var tail = 0
        while (tail < most - head && was[was.length - 1 - tail] == now[now.length - 1 - tail]) tail++
        if (tail > 0 && Character.isLowSurrogate(now[now.length - tail])) tail--
        return Change(head, tail, now.substring(head, now.length - tail))
    }

    /**
     * [changeOf], what came in ending at [cursor] -- where a field leaves its
     * cursor after typing or a paste -- when the words after the cursor are
     * the old words' end; [changeOf] alone otherwise. What is taken away and
     * nothing put in -- Backspace, a cut -- is taken at the cursor too: read
     * off the texts, a line break taken from a run of them was the last of
     * the run.
     */
    fun changeAt(was: String, now: String, cursor: Int?): Change {
        val plain = changeOf(was, now)
        if (cursor == null || cursor !in 0..now.length) return plain
        val tail = now.length - cursor
        if (tail > was.length || !now.regionMatches(cursor, was, was.length - tail, tail)) return plain
        val head = minOf(plain.head, was.length - tail, cursor)
        return Change(head, tail, now.substring(head, cursor))
    }

    /**
     * The edit [typed], a prose field's words, made to its [segment] of the
     * body, in code points: what came in read up to the [cursor] the field
     * leaves ([changeAt]), as the desktop's Tk marks see a key go in at the
     * insert mark -- off the two texts, Enter at the calculator's place read
     * as the break before it. Null when nothing changed, or for the field
     * under a closing table, a line the body does not hold yet.
     */
    fun proseEdit(segment: Segment, typed: String, cursor: Int?): Paragraphs.Edit? {
        val was = segment.text
        if (segment.isVirtual || typed == was) return null
        val change = changeAt(was, typed, cursor)
        return Paragraphs.Edit(segment.cpStart + was.codePointCount(0, change.head),
            was.codePointCount(change.head, was.length - change.tail), change.came.codePointCount(0, change.came.length))
    }

    /** CR LF, and a CR alone, as one line break: the desktop's `_cap_paste`. */
    fun lineBreaks(text: String): String =
        if ('\r' !in text) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * [now] typed over [was] in a box with no cap -- the note's text, its
     * comments, a cell, a template's -- a CR in what came in given as the one
     * line break the desktop's paste puts in ([lineBreaks]): the words and the
     * cursor after what came in, or null when no CR came in. A paste in CR LF
     * kept every CR in the file, where the desktop stores none; a CR the
     * words already held stays.
     */
    fun breaksCame(was: String, now: String, cursor: Int? = null): Pair<String, Int>? {
        if ('\r' !in now) return null
        val change = changeAt(was, now, cursor)
        if ('\r' !in change.came) return null
        val came = lineBreaks(change.came)
        return now.substring(0, change.head) + came + now.substring(now.length - change.tail) to change.head + came.length
    }

    /**
     * Whether the edit from [was] to [now], in a title, was Enter: what came
     * in is one line break and nothing else. Asked of the whole value, every
     * letter typed into a title already holding a break -- a share code, a
     * desktop template or an older phone can leave one -- was taken for
     * Enter, dropped and the cursor sent to the note's words.
     */
    fun isEnter(was: String, now: String): Boolean = changeOf(was, now).came == "\n"

    /** [text] on one line: each line break a space, the length kept. */
    fun oneLine(text: String): String =
        if ('\n' !in text && '\r' !in text) text else text.replace('\r', ' ').replace('\n', ' ')

    /**
     * A note's title after [now] replaced [was]: one line, as the desktop's.
     * What came in goes in as `_on_title_paste` puts a paste, on one line
     * ([titleLine]), ending at the [cursor] the field gives (off the texts
     * alone, a paste with a break took the title's own space: "Buy
     * freshmilk"); a break the title held is a space once written in. The
     * text, and the cursor when what came in changed; null leaves the field's.
     */
    fun titleTyped(was: String, now: String, cursor: Int? = null): Pair<String, Int?> {
        val change = changeAt(was, now, cursor)
        val came = titleLine(change.came)
        val broken = came != change.came
        val text = oneLine(now.substring(0, change.head)) + came + oneLine(now.substring(now.length - change.tail))
        return text to (if (broken) change.head + came.length else null)
    }

    /**
     * The desktop's `_TITLE_PASTE_BREAK` join, since its 51f0c44: a line
     * break or a tab -- \t \n \v \f \r, U+001C to U+001E, U+0085, U+2028,
     * U+2029 -- with the spaces around it, is one space between words and
     * nothing at either end; other spaces go in as copied. A run of Python's
     * spaces holding a break is read once, letter by letter, where a regex
     * would try each space of a long run again.
     */
    fun titleLine(text: String): String {
        if (text.none { it.code < 0x20 || it == '\u0085' || it == '\u2028' || it == '\u2029' }) return text
        val out = StringBuilder()
        var i = 0
        var pendingBreak = false
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!PyText.isSpace(cp)) {
                if (pendingBreak && out.isNotEmpty()) out.append(' ')
                pendingBreak = false
                out.appendCodePoint(cp)
                i += Character.charCount(cp)
                continue
            }
            // A run of spaces: a separator when it holds a break, kept as it was otherwise.
            val start = i
            var breaks = false
            while (i < text.length && PyText.isSpace(text.codePointAt(i))) {
                if (isTitleBreak(text.codePointAt(i))) breaks = true
                i += Character.charCount(text.codePointAt(i))
            }
            if (breaks) pendingBreak = true
            else out.append(text, start, i)
        }
        return out.toString()
    }

    private fun isTitleBreak(cp: Int): Boolean =
        cp in 0x09..0x0D || cp in 0x1C..0x1E || cp == 0x85 || cp == 0x2028 || cp == 0x2029

    /** A note's title, and a template's name and title: the desktop's TITLE_MAX_LEN, in letters. */
    const val TITLE_CHARS = 200

    /** "A new item…", as a [BoxCap.fit]'s box: a row's is its place in the list, never below 0. */
    const val NEW_ROW_BOX = -1

    /** [text]'s first [letters] letters, as Python's `text[:letters]` takes them: never half an emoji. */
    fun firstLetters(text: String, letters: Int): String {
        if (text.length <= letters) return text
        var at = 0
        var taken = 0
        while (at < text.length && taken < letters) {
            at += Character.charCount(text.codePointAt(at))
            taken++
        }
        return text.substring(0, at)
    }

    /**
     * [fitRow] for boxes kept to [limit] -- one editor's rows and its box for
     * a new row, or one title -- said once while edits go on meeting the cap,
     * as the phone would queue a bar for each letter the desktop refuses; said
     * again at a cut that does not go on from what the last left in the same
     * [fit]'s `box` (another row, the box emptied by Add).
     */
    class BoxCap(private val say: (String) -> Unit, private val limit: Int = CHECKLIST_ROW_CHARS,
                 private val what: String = "A checklist item", private val shortened: String? = null) {
        // The box the words were last said for, and what the cut left in it.
        private var saidIn: Any? = null
        private var left: String? = null

        /** [fitRow] of [was] to [now], what came in read up to the [cursor] the field leaves, and said as above. */
        fun fit(was: String, now: String, box: Any = Unit, cursor: Int? = null): Fit =
            fitRow(was, now, limit, what, shortened, cursor).also { fit ->
            if (fit.said == null) {
                saidIn = null
                left = null
            } else {
                if (left == null || box != saidIn || was != left) say(fit.said)
                saidIn = box
                left = fit.text
            }
        }
    }

    /**
     * The most letters a checklist's rows draw as fields together, from the
     * top; past it a row is shown short, read-only, kept whole. Weighed alone,
     * 120 rows of 150,000 letters from a 38 KB code were 18 million letters
     * laid out at each opening; a thousand rows of a hundred are drawn whole.
     */
    const val CHECKLIST_CHARS = 100_000

    /**
     * How many of a checklist's first rows are drawn, and how many more each
     * "Show" draws: every row is a piece of the screen drawn at once, and
     * 1,000 short rows held the screen 5.3 s as the note opened. The last
     * [CHECKLIST_TAIL_ROWS] are drawn too, where a new item lands.
     */
    const val CHECKLIST_ROWS = 200
    const val CHECKLIST_TAIL_ROWS = 5

    /**
     * The rows of a checklist of [size] that are drawn, in order: its first
     * [head] and its last [tail] -- all of them when that leaves out no more
     * than a few -- and the rows up to [reach], a row a search stepped to,
     * when it lies between: a reach among the last drew every row, at each
     * letter typed in the bar.
     */
    fun checklistShown(size: Int, head: Int, reach: Int = -1, tail: Int = CHECKLIST_TAIL_ROWS): List<Int> {
        val last = size - tail
        val first = if (reach in head until last) reach + 1 else head
        if (last - first <= CHECKLIST_TAIL_ROWS) return (0 until size).toList()
        return (0 until first).toList() + (last until size).toList()
    }

    /**
     * The [checklistShown] window -- its head and its tail -- after the row
     * at [from] moved to [to], in a list of [size]: every row drawn before is
     * drawn after, rows the move pushed across an edge included, and the row
     * moved too, the window one row wider on the side it came from. Stepped
     * among rows not drawn, it vanished, and a screen reader's next Move
     * moved the row shown in its place.
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
     * Where a row dragged from [from] lands: on the drawn row whose middle is
     * nearest the dragged row's, now at [at] -- [middles] holding each drawn
     * row's. Counted in rows of one height, the "N more rows" line counted as
     * one row and the rows it stands for as none.
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
     * A row shown to be read, in parts of at most [chars] UTF-16 units: each
     * line, each long line cut after its last space near a part's end, or on
     * the edge of a letter as a person sees one ([letterEdge]) -- never through
     * an emoji, a flag or an accent, as each part is drawn and read aloud
     * alone. Start and end in the row each; a line break belongs to none. The
     * space is looked for in the slack alone, or a spaceless row read its square.
     */
    fun rowParts(text: CharSequence, chars: Int = ROW_PART_CHARS): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            var start = lineStart
            do {
                var end = minOf(lineEnd, start + chars)
                if (end < lineEnd) {
                    val from = maxOf(start + 1, end - ROW_PART_SLACK)
                    val space = lastSpace(text, end, from)
                    if (space >= 0) end = space + 1
                    else {
                        // A letter longer than the slack -- a pile of accents
                        // -- is cut where the part ends, never inside an emoji.
                        val edge = letterEdge(text, end, from, start)
                        if (edge >= 0) end = edge else if (Character.isLowSurrogate(text[end])) end--
                    }
                }
                out.add(start to end)
                start = end
            } while (start < lineEnd)
            lineStart = lineEnd + 1
        }
        return out
    }

    /** The last space of [text] before [end] and no earlier than [from], or -1. */
    private fun lastSpace(text: CharSequence, end: Int, from: Int): Int {
        for (at in end - 1 downTo from) if (text[at] == ' ') return at
        return -1
    }

    /**
     * The last place at or before [end], and no earlier than [from], where
     * one letter as a person sees it ends and the next begins -- a grapheme
     * cluster's edge, by the rules of Unicode's TR29 that a note meets -- or
     * -1. [start] is where the part begins: a flag is two regional letters,
     * and a part drawn on its own pairs them from its own start. Reads a few
     * letters back from [end]; a run of flags, at most back to [start].
     */
    private fun letterEdge(text: CharSequence, end: Int, from: Int, start: Int): Int {
        var at = end
        while (at >= from) {
            if (at > start && at < text.length && regional(codePointAt(text, at)) && regional(codePointBefore(text, at))) {
                // A flag's second regional letter (GB12, GB13): an odd count
                // of them before it, counted once, from the part's start.
                var count = 0
                var back = at
                while (back > start) {
                    val prior = codePointBefore(text, back)
                    if (!regional(prior)) break
                    count++
                    back -= Character.charCount(prior)
                }
                val edge = if (count % 2 == 1) at - 2 else at
                return if (edge >= from) edge else -1
            }
            if (!joinsBack(text, at, start)) return at
            at -= if (at >= 2 && Character.isLowSurrogate(text[at - 1]) && Character.isHighSurrogate(text[at - 2])) 2 else 1
        }
        return -1
    }

    /** Whether the code point at [at] is one letter with the one before it. */
    private fun joinsBack(text: CharSequence, at: Int, start: Int): Boolean {
        if (at <= start || at >= text.length) return false
        // The second half of an emoji.
        if (Character.isLowSurrogate(text[at]) && Character.isHighSurrogate(text[at - 1])) return true
        val cp = codePointAt(text, at)
        val before = codePointBefore(text, at)
        // A family, a profession, a flag of a nation's part: joined by ZWJ
        // (GB11), and the joiners themselves (GB9).
        if (before == ZWJ || cp == ZWJ || cp == ZWNJ) return true
        when (Character.getType(cp)) {
            // Accents and marks, spacing or not (GB9, GB9a).
            Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt() -> return true
        }
        // Variation selectors, skin tones and tag letters (Extend, GB9).
        if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F) return true
        // Hangul spelt in jamo (GB6 to GB8): a vowel or a final joins what is before it, a first sound what follows.
        if (cp in 0x1160..0x11FF || cp in 0xD7B0..0xD7FF) return true
        if ((before in 0x1100..0x115F || before in 0xA960..0xA97F) &&
            (cp in 0x1100..0x115F || cp in 0xA960..0xA97F || cp in 0xAC00..0xD7A3)) return true
        return false
    }

    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C

    private fun regional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    private fun codePointAt(text: CharSequence, at: Int): Int {
        val c = text[at]
        if (Character.isHighSurrogate(c) && at + 1 < text.length && Character.isLowSurrogate(text[at + 1])) {
            return Character.toCodePoint(c, text[at + 1])
        }
        return c.code
    }

    private fun codePointBefore(text: CharSequence, at: Int): Int {
        val c = text[at - 1]
        if (Character.isLowSurrogate(c) && at >= 2 && Character.isHighSurrogate(text[at - 2])) {
            return Character.toCodePoint(text[at - 2], c)
        }
        return c.code
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
        // What the lines given to fields so far cost to lay out ([MAX_LAYOUT_COST]):
        // a line taking it past the limit is read-only too. A body no longer than
        // one run's limit cannot reach it, and is not weighed.
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
            // Empty lines between two read-only ones are read-only too: costing
            // nothing, they were let in, and "x", "", "x", "" ... past the limit
            // came out as ten thousand pieces, every other one a field.
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
            // A grid's cells are text laid out like any other: a row of two cells,
            // one 150,000 letters long, passed the count of cells and froze the note.
            // Weighed as lines are, it is shown as one -- read-only, its start below.
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
     * A field's change taken once: Samsung's keyboard, Enter on a word still
     * being composed, has the field report the result twice from the same
     * value, and the second broke a second line (ISSUES_LOG, "One change told
     * twice by Samsung's keyboard"). The same change from the same value
     * within [WINDOW_MS] is one report: no person repeats a keystroke that fast.
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

    private const val WORDS_A_MINUTE = 200

    /**
     * The desktop's `reading_time`: at [WORDS_A_MINUTE], the slow end of
     * adult reading -- a reader who finishes early has lost nothing. Python
     * rounds a half to the even neighbour, and so does this.
     */
    fun readingTime(words: Int): String {
        if (words <= 0) return ""
        if (words < WORDS_A_MINUTE) return "under a minute to read"
        val minutes = Math.rint(words.toDouble() / WORDS_A_MINUTE).toInt()
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
     * last. Letters of the same kind typed within [pause] join one step, so an
     * Undo takes back a burst of words; a format, a list key, a table edit is
     * its own. At most [limit] steps and [budget] in all, by [weigh] -- 200
     * whole states of a two-million-letter note were 760 MB -- the oldest
     * going first, never the newest. A change after an Undo forgets the Redo.
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
         * the state kept is the one before the first. True when [before] is
         * kept as a step of its own, the newest; false when the change
         * joined the step before.
         */
        fun record(before: T, kind: String, now: Long): Boolean {
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
            return !joins
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
        val lineStart = Paragraphs.lineStart(out, last + 1)
        val end = lineStart + label.length
        return Captioned(out.joinToString("\n"), last + 1, end - "description".length, end)
    }

    /**
     * `_insert_table`: a blank table at the cursor ([caret], in code points),
     * on lines of its own -- a break before it when the cursor's line holds
     * something or a table sits right above, so two tables never fuse; a
     * break after it, to carry on writing, and a second when a table follows
     * below. A cursor past the end (under a closing table) is a new line there.
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
