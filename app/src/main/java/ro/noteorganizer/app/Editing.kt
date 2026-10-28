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
     * with no space to break it, shown read-only; only a [field] is typed in.
     */
    data class Segment(
        val table: Boolean,
        val firstLine: Int,
        val lastLine: Int,
        val text: String,
        val cpStart: Int,
        val drawn: Boolean = true,
        val long: Boolean = false,
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
     */
    const val MAX_UNBROKEN = 10_000

    /** The longest run of [text] with no space, tab or line break in it, in UTF-16 units. */
    fun longestUnbroken(text: String): Int {
        var longest = 0
        var run = 0
        for (c in text) {
            if (c.isWhitespace()) run = 0 else if (++run > longest) longest = run
        }
        return longest
    }

    /** Whether [text] holds a run too long to lay out ([MAX_UNBROKEN]): shown read-only, never as a field. */
    fun tooLongToLayOut(text: String): Boolean = text.length > MAX_UNBROKEN && longestUnbroken(text) > MAX_UNBROKEN

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
     */
    const val MAX_DRAWN_CELLS = 1_000

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
        fun text(first: Int, last: Int) = lines.subList(first, last + 1).joinToString("\n")
        val out = ArrayList<Segment>()
        // A run of prose lines, each run of lines too long to lay out cut
        // out of it as a segment of its own, shown read-only: the lines
        // around it stay fields, and the note keeps every byte.
        fun prose(first: Int, last: Int) {
            var from = first
            var i = first
            while (i <= last) {
                if (!tooLongToLayOut(lines[i])) {
                    i++
                    continue
                }
                var end = i
                while (end + 1 <= last && tooLongToLayOut(lines[end + 1])) end++
                if (i > from) out.add(Segment(false, from, i - 1, text(from, i - 1), starts[from]))
                out.add(Segment(false, i, end, text(i, end), starts[i], long = true))
                from = end + 1
                i = end + 1
            }
            if (from <= last) out.add(Segment(false, from, last, text(from, last), starts[from]))
        }

        var line = 0
        for (block in tableBlocks(body)) {
            if (block.first > line) prose(line, block.first - 1)
            out.add(Segment(true, block.first, block.last, text(block.first, block.last), starts[block.first],
                drawn = gridCells(lines.subList(block.first, block.last + 1)) <= MAX_DRAWN_CELLS))
            line = block.last + 1
        }
        if (line < lines.size) prose(line, lines.size - 1)
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
     * The editor's Undo and Redo: the states a note went through, newest
     * last. Typing is one step per pause -- letters typed within [pause] of
     * each other, of the same kind, join the step before -- so one Undo
     * takes back a word or a burst of words, not a letter; a format, a list
     * key, a table edit is a step of its own. At most [limit] steps are
     * kept. A new change after an Undo forgets what Redo would have brought
     * back, as every editor's does.
     */
    class History<T>(private val limit: Int = 200, private val pause: Long = 1000) {
        private val past = ArrayDeque<T>()
        private val future = ArrayDeque<T>()
        private var lastKind: String? = null
        private var lastAt = Long.MIN_VALUE

        val canUndo: Boolean get() = past.isNotEmpty()
        val canRedo: Boolean get() = future.isNotEmpty()

        /**
         * [before] is the state just before a change of [kind] made at [now]
         * (milliseconds). A change of "typing" soon after another joins it:
         * the state kept is the one before the first.
         */
        fun record(before: T, kind: String, now: Long) {
            // Typing joins typing in the same field: "typing", "typing:title".
            val joins = kind.startsWith(TYPING) && kind == lastKind && now - lastAt in 0..pause && past.isNotEmpty()
            if (!joins) {
                past.addLast(before)
                while (past.size > limit) past.removeFirst()
            }
            future.clear()
            lastKind = kind
            lastAt = now
        }

        /** The state to go back to, given the one on screen now -- kept for Redo. */
        fun undo(current: T): T? {
            val back = past.removeLastOrNull() ?: return null
            future.addLast(current)
            lastKind = null
            return back
        }

        /** The state Undo left, given the one on screen now. */
        fun redo(current: T): T? {
            val forward = future.removeLastOrNull() ?: return null
            past.addLast(current)
            lastKind = null
            return forward
        }

        /** Everything forgotten: the note was read again from the file. */
        fun clear() {
            past.clear()
            future.clear()
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
