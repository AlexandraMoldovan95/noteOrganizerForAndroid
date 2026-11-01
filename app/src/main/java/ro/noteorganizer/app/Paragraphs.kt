package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists, indents and alignment: what a note's lines are, beyond their words.
 *
 * The desktop's model, kept whole. A list item is told by the MARKER in its
 * text -- "• " (◦ and ▪ deeper down) or "1. " -- not by anything stored
 * beside it: the marker is what is saved, read back and copied. A line's
 * indent and alignment are its paragraph properties. What the file stores
 * in `paragraphs` is worked out from both at the moment of saving, as the
 * desktop's `_extract_paragraphs` does: the kind from the marker, the
 * indent and alignment from the properties.
 *
 * Every operation here is the desktop's, line for line, from its editor:
 * turning lines into a list and back, indenting, aligning, Enter in a list
 * (it continues; on an empty item it steps out), Backspace against a
 * marker (it climbs out a level, then takes the marker away), and
 * renumbering every numbered run afterwards. Each returns the new body
 * and the EDITS it made -- marker text put in or taken out at the start of
 * lines -- so styles move with exactly the text they were on.
 */
object Paragraphs {

    val BULLET_GLYPHS = listOf("•", "◦", "▪")
    const val MAX_INDENT = 8
    private val NUMBER = Regex("^\\p{Nd}+\\. ")

    /** A line's list kind, its marker, and the text after it. */
    data class Marker(val kind: String?, val marker: String, val rest: String)

    /** `parse_list_marker`. */
    fun parseListMarker(line: String): Marker {
        for (glyph in BULLET_GLYPHS) {
            if (line.startsWith("$glyph ")) return Marker("bullet", "$glyph ", line.substring(glyph.length + 1))
        }
        NUMBER.find(line)?.let { return Marker("number", it.value, line.substring(it.value.length)) }
        return Marker(null, "", line)
    }

    /** `marker_for`: bullets change glyph with depth; numbers never do. */
    fun markerFor(kind: String, number: Int = 1, level: Int = 0): String =
        if (kind == "bullet") BULLET_GLYPHS[level % BULLET_GLYPHS.size] + " " else "$number. "

    /** A line's indent and alignment; a plain left line has none. */
    data class Props(val indent: Int = 0, val align: String = "left") {
        val plain: Boolean get() = indent == 0 && align == "left"
    }

    /**
     * The properties a note's stored paragraphs give the lines of [body].
     * A list entry whose line no longer starts with its kind of marker --
     * the body rewritten outside the app, its paragraphs not -- keeps its
     * alignment but not its depth, as the desktop's `_apply_paragraphs`
     * reads it: kept, the phone indented a plain line and saved the indent,
     * which the desktop then honoured.
     */
    fun propsOf(paragraphs: Any?, body: String): Map<Int, Props> {
        val lines = body.split("\n")
        return Exporting.normalizeParagraphs(paragraphs, lines.size)
            .associate { entry ->
                val stale = entry.kind != null && parseListMarker(lines.getOrElse(entry.line) { "" }).kind != entry.kind
                entry.line to Props(if (stale) 0 else entry.indent, entry.align ?: "left")
            }
            .filterValues { !it.plain }
    }

    /**
     * The series' [paragraphs] a day of it shows, [body] the day's text: all
     * of them while the day shows the series' words ([sameBody]); on text of
     * its own, only the list entries whose line still carries their marker,
     * without alignment -- the desktop's `day_paragraphs`. A list's depth
     * lives nowhere else, and the rest, put on by line number, landed on the
     * wrong lines (the final audit's lows wave).
     */
    fun dayProps(paragraphs: Any?, body: String, sameBody: Boolean, own: Any? = null): Map<Int, Props> =
        propsOf(dayParagraphs(paragraphs, body, sameBody, own), body)

    /**
     * The lines' looks a day of a series shows, as stored entries -- the
     * desktop's `day_paragraphs`: the series' [paragraphs] while the day
     * shows the series' words, its lists' depth its [own] when it keeps
     * them; on text of its own, its own list lines, or -- a day saved
     * before it kept them -- the series' list entries, by line. What the day
     * view shows ([dayProps]) and Make it a note keeps.
     */
    fun dayParagraphs(paragraphs: Any?, body: String, sameBody: Boolean, own: Any? = null): JSONArray {
        if (!sameBody) return listLines(own ?: paragraphs, body)
        val looks = sortedMapOf<Int, JSONObject>()
        for (entry in Exporting.normalizeParagraphs(paragraphs, body.split("\n").size)) {
            looks[entry.line] = JSONObject().put("line", entry.line).also { item ->
                entry.kind?.let { item.put("kind", it) }
                if (entry.indent != 0) item.put("indent", entry.indent)
                entry.align?.takeIf { it != "left" }?.let { item.put("align", it) }
            }
        }
        if (own != null) {
            val lists = listLines(own, body)
            for (i in 0 until lists.length()) {
                val entry = lists.getJSONObject(i)
                val line = entry.getInt("line")
                val item = looks[line] ?: JSONObject().put("line", line)
                item.put("kind", entry.getString("kind"))
                if (entry.has("indent")) item.put("indent", entry.getInt("indent")) else item.remove("indent")
                looks[line] = item
            }
        }
        return JSONArray().also { out -> looks.values.forEach { out.put(it) } }
    }

    /**
     * The list lines of [body] -- the desktop's `list_lines`: each line
     * starting with a list marker, its kind and the depth [paragraphs] give
     * it, else a bullet's glyph's. The one look a day of a series keeps of its
     * own: its text is plain, but a nested list's depth lives nowhere else
     * (the owner's choice, the lows wave). Without [body], the list entries
     * as they are.
     */
    fun listLines(paragraphs: Any?, body: String?): JSONArray {
        val out = JSONArray()
        fun put(line: Int, kind: String, indent: Int) =
            out.put(JSONObject().put("line", line).put("kind", kind).also { if (indent != 0) it.put("indent", indent) })
        if (body == null) {
            for (entry in Exporting.normalizeParagraphs(paragraphs, null)) entry.kind?.let { put(entry.line, it, entry.indent) }
            return out
        }
        val lines = body.split("\n")
        val given = Exporting.normalizeParagraphs(paragraphs, lines.size).filter { it.kind != null }
            .associate { it.line to (it.kind to it.indent) }
        for ((n, line) in lines.withIndex()) {
            val marker = parseListMarker(line)
            val kind = marker.kind ?: continue
            val depth = given[n]?.takeIf { it.first == kind }?.second
                ?: if (kind == "bullet") BULLET_GLYPHS.indexOf(marker.marker.trim()).coerceAtLeast(0) else 0
            put(n, kind, depth)
        }
        return out
    }

    /** [props] as a day's own text keeps them once typed in: its lists' depth alone ([dayProps]). */
    fun listDepths(props: Map<Int, Props>, body: String): Map<Int, Props> {
        val lines = body.split("\n")
        return props.filterKeys { parseListMarker(lines.getOrElse(it) { "" }).kind != null }
            .mapValues { Props(it.value.indent) }
            .filterValues { !it.plain }
    }

    /** `_extract_paragraphs`: what the file stores, the kind read off each line's marker. */
    fun extract(body: String, props: Map<Int, Props>): JSONArray {
        val out = JSONArray()
        for ((n, line) in body.split("\n").withIndex()) {
            val kind = parseListMarker(line).kind
            val p = props[n] ?: Props()
            if (kind == null && p.plain) continue
            val entry = JSONObject().put("line", n)
            if (kind != null) entry.put("kind", kind)
            if (p.indent != 0) entry.put("indent", p.indent)
            if (p.align != "left") entry.put("align", p.align)
            out.put(entry)
        }
        return out
    }

    /**
     * One change to the text, in the positions of the text before it: at
     * [at], [removed] code points taken out and [inserted] put in.
     */
    data class Edit(val at: Int, val removed: Int, val inserted: Int)

    /** A body after an operation, its lines' properties, and what was edited. */
    data class Result(val body: String, val props: Map<Int, Props>, val edits: List<Edit>)

    /**
     * Styles moved through [edits]: text put in at the start of a styled run
     * goes before it, text put in at its end stays outside it, and a span
     * that lay wholly in removed text is gone.
     */
    fun moveSpans(spans: List<FormatSpan>, edits: List<Edit>): List<FormatSpan> {
        if (edits.isEmpty()) return spans
        return spans.mapNotNull { s ->
            val a = moveStart(s.start, edits)
            val b = moveEnd(s.end, edits)
            if (b > a) s.copy(start = a, end = b) else null
        }
    }

    /** Where a start -- or a cursor -- goes: text put in right at it goes before it. */
    fun moveStart(x: Int, edits: List<Edit>): Int = x + edits.sumOf { e ->
        when {
            x >= e.at + e.removed && (e.removed > 0 || x >= e.at) -> e.inserted - e.removed
            x > e.at -> e.at - x
            else -> 0
        }
    }

    /**
     * Where a mark goes through one edit, made in the text the edit before
     * it left, as a Tk mark of right gravity moves -- the desktop's cursor,
     * and its calculator's place: what the edit takes out goes first, a mark
     * inside it falling to its start, then what it puts in, a mark right
     * at that place carried after it.
     */
    fun moveMark(x: Int, e: Edit): Int = when {
        x < e.at -> x
        x <= e.at + e.removed -> e.at + e.inserted
        else -> x - e.removed + e.inserted
    }

    /** Where an end goes: text put in right at it stays after it. */
    fun moveEnd(x: Int, edits: List<Edit>): Int = x + edits.sumOf { e ->
        when {
            x > e.at && x >= e.at + e.removed -> e.inserted - e.removed
            x > e.at -> e.at - x
            else -> 0
        }
    }

    private fun cpLength(text: String) = text.codePointCount(0, text.length)

    /** Where each line starts, in code points. */
    private fun lineStarts(lines: List<String>): IntArray {
        val starts = IntArray(lines.size)
        var at = 0
        for ((n, line) in lines.withIndex()) {
            starts[n] = at
            at += cpLength(line) + 1
        }
        return starts
    }

    /**
     * `renumber`: every numbered run reads 1, 2, 3 from its own start; one
     * counter per depth, a deeper list starting again under each item; a
     * bullet interrupts numbering at its depth and below; one blank line
     * does not end a list, two do.
     */
    fun renumber(body: String, props: Map<Int, Props>): Result {
        val lines = body.split("\n").toMutableList()
        val starts = lineStarts(lines)
        val edits = ArrayList<Edit>()
        val counters = HashMap<Int, Int>()
        var blanks = 0
        for (i in lines.indices) {
            val parsed = parseListMarker(lines[i])
            val level = props[i]?.indent ?: 0
            when {
                parsed.kind == "number" -> {
                    blanks = 0
                    counters[level] = (counters[level] ?: 0) + 1
                    counters.keys.filter { it > level }.forEach { counters.remove(it) }
                    val fresh = markerFor("number", counters.getValue(level), level)
                    if (fresh != parsed.marker) {
                        edits.add(Edit(starts[i], cpLength(parsed.marker), cpLength(fresh)))
                        lines[i] = fresh + parsed.rest
                    }
                }
                parsed.kind == "bullet" -> {
                    blanks = 0
                    counters.keys.filter { it >= level }.forEach { counters.remove(it) }
                }
                PyText.strip(lines[i]).isNotEmpty() -> {
                    blanks = 0
                    counters.clear()
                }
                else -> {
                    blanks += 1
                    if (blanks > 1) counters.clear()
                }
            }
        }
        return Result(lines.joinToString("\n"), props, edits)
    }

    /**
     * An operation's outcome: the body, the lines' properties, and the
     * edits in the order they were made -- each step's positions those of
     * the text the step before it left. [move] carries styles through all
     * of them, from the text the operation started with.
     */
    data class Done(val body: String, val props: Map<Int, Props>, val steps: List<List<Edit>>,
                    /** Where the cursor goes, after an edit made from the keyboard. */
                    val caret: Int? = null) {
        fun move(spans: List<FormatSpan>): List<FormatSpan> = steps.fold(spans) { s, edits -> moveSpans(s, edits) }

        /** A cursor or a selection's edge, carried through every step. */
        fun movePoint(at: Int): Int = steps.fold(at) { x, edits -> moveStart(x, edits) }

        /**
         * Its edits one after another, each in the text the one before left
         * -- what a remembered place is carried through ([moveMark]): a
         * step's own edits, made at once in its text, from the last back.
         */
        fun inTurn(): List<Edit> = steps.flatMap { step -> step.sortedByDescending { it.at } }
    }

    /**
     * The lines' properties after a typed edit added or took away lines --
     * the [edit] itself, in code points, when the field's cursor told where
     * it was made ([Editing.proseEdit]): read off the two texts, Enter at the
     * end of a centred line with a line below was the break after the old
     * one, and the new line was left at the margin (the final audit's tenth
     * round).
     */
    fun follow(old: String, new: String, props: Map<Int, Props>, edit: Edit? = null): Map<Int, Props> {
        if (props.isEmpty() || old == new) return props
        (edit?.let { breakOf(old, new, it) } ?: breakAt(old, new))?.let { (line, column, made) ->
            return if (made) split(props, line, column) else join(props, line)
        }
        val moved = remapParagraphLines(old, new, props.keys.toList())
        val out = moved.entries.associate { (from, to) -> to to props.getValue(from) }.toMutableMap()
        // Text put into a line, or taken from it, across line breaks: each line
        // the edit leaves carries the looks of the line it began in, as the
        // desktop's text gives inserted text the tags around it. Dropped, a cut
        // pasted back into a nested item, or lines pasted into one, went to the
        // margin (the lows wave's verification, round 4).
        edited(old, new, edit)?.let { (line, until) -> props[line]?.let { kept -> for (n in line until until) out[n] = kept } }
        return out
    }

    /**
     * The lines an edit that added or took away lines leaves of the line it
     * began in, when it began inside that line rather than at its start: that
     * line, and each line the text it put in makes. Read off the [edit]'s
     * place when the field told it; else off the two texts, from the first
     * line they differ on. Null for an edit begun at a line's start, which
     * moves the line whole.
     */
    private fun edited(old: String, new: String, edit: Edit?): Pair<Int, Int>? {
        if (old.count { it == '\n' } == new.count { it == '\n' }) return null
        if (edit != null) {
            val oldLength = old.codePointCount(0, old.length)
            if (edit.at !in 0..oldLength) return null
            val at = old.offsetByCodePoints(0, edit.at)
            val column = at - (old.lastIndexOf('\n', at - 1) + 1)
            if (column == 0) return null
            val line = old.substring(0, at).count { it == '\n' }
            val start = new.offsetByCodePoints(0, edit.at.coerceAtMost(new.codePointCount(0, new.length)))
            val end = runCatching { new.offsetByCodePoints(start, edit.inserted) }.getOrNull() ?: return null
            return line to line + new.substring(start, end).count { it == '\n' } + 1
        }
        val a = old.split("\n")
        val b = new.split("\n")
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++
        // Lines the edit leaves of its own -- not ones carried whole from below,
        // which keep their own looks (the remap's) -- begun inside its line.
        val end = b.size - suffix
        if (end <= prefix || prefix >= a.size || a[prefix].commonPrefixWith(b[prefix]).isEmpty()) return null
        return prefix to end
    }

    /** [breakAt]'s answer for an [edit] whose place is known: a line break put in, or taken out, alone. */
    private fun breakOf(old: String, new: String, edit: Edit): Triple<Int, Int, Boolean>? {
        val made = edit.removed == 0 && edit.inserted == 1
        if (!made && !(edit.removed == 1 && edit.inserted == 0)) return null
        val holder = if (made) new else old
        if (edit.at !in 0 until holder.codePointCount(0, holder.length)) return null
        val at = holder.offsetByCodePoints(0, edit.at)
        if (holder[at] != '\n') return null
        val line = holder.substring(0, at).count { it == '\n' }
        val column = at - (holder.lastIndexOf('\n', at - 1) + 1)
        return Triple(line, column, made)
    }

    /**
     * One line break typed or taken away, and nothing else: the line it
     * broke (or the upper of the two it joined), the column it broke at,
     * and whether it was made. A break is the one edit whose lines are
     * known exactly -- the remap, finding lines added, dropped the
     * properties of the lines around the edit for fear of putting them on
     * a stranger's line.
     */
    private fun breakAt(old: String, new: String): Triple<Int, Int, Boolean>? {
        val made = new.length == old.length + 1
        if (!made && old.length != new.length + 1) return null
        val (long, short) = if (made) new to old else old to new
        var i = 0
        while (i < short.length && long[i] == short[i]) i++
        if (long[i] != '\n' || !long.regionMatches(i + 1, short, i, short.length - i)) return null
        val line = short.substring(0, i).count { it == '\n' }
        val column = i - (short.lastIndexOf('\n', i - 1) + 1)
        return Triple(line, column, made)
    }

    /**
     * A line broken in two keeps its centring and indent on both halves, as
     * the desktop's tags, laid over the text, stay on both -- Enter in a
     * centred paragraph left both halves at the margin. Broken at its very
     * start, the line moved down whole, a blank line put above it.
     */
    private fun split(props: Map<Int, Props>, line: Int, column: Int): Map<Int, Props> {
        val out = HashMap<Int, Props>()
        for ((n, p) in props) {
            when {
                n < line -> out[n] = p
                n > line -> out[n + 1] = p
                column == 0 -> out[n + 1] = p
                else -> { out[n] = p; out[n + 1] = p }
            }
        }
        return out
    }

    /** Two lines joined into one: the upper's properties kept, as the desktop keeps the tags at the line's start. */
    private fun join(props: Map<Int, Props>, line: Int): Map<Int, Props> {
        val out = HashMap<Int, Props>()
        for ((n, p) in props) {
            when {
                n <= line -> out[n] = p
                n == line + 1 -> Unit
                else -> out[n - 1] = p
            }
        }
        return out
    }

    /** A step, then the renumbering every list change ends with. */
    private fun finish(step: Result, before: List<List<Edit>> = emptyList()): Done {
        val renumbered = renumber(step.body, step.props)
        return Done(renumbered.body, renumbered.props, before + listOf(step.edits, renumbered.edits))
    }

    private fun withProps(props: Map<Int, Props>, line: Int, p: Props): Map<Int, Props> =
        if (p.plain) props - line else props + (line to p)

    /**
     * `_set_paragraph_indent`: one line at a depth, its bullet's glyph
     * rewritten for that depth -- a demoted item must not keep its
     * parent's bullet, or the two levels look the same.
     */
    private fun setIndent(lines: MutableList<String>, starts: IntArray, props: Map<Int, Props>, line: Int,
                          indent: Int, edits: MutableList<Edit>): Map<Int, Props> {
        val parsed = parseListMarker(lines[line])
        if (parsed.kind == "bullet") {
            val fresh = markerFor("bullet", 1, indent)
            if (fresh != parsed.marker) {
                edits.add(Edit(starts[line], cpLength(parsed.marker), cpLength(fresh)))
                lines[line] = fresh + parsed.rest
            }
        }
        return withProps(props, line, (props[line] ?: Props()).copy(indent = indent))
    }

    /**
     * `_toggle_list`: the lines from [first] to [last] become a list of
     * [kind], or stop being one when every item already is one. Blank lines
     * get no marker -- a numbered list with gaps read 1, 3, 5 -- unless a
     * single empty line is chosen, which starts a list there. Table rows
     * are never list items. Depth is kept across the change.
     */
    fun toggleList(body: String, props: Map<Int, Props>, first: Int, last: Int, kind: String): Done? {
        val lines = body.split("\n").toMutableList()
        val starts = lineStarts(lines)
        val range = first.coerceIn(0, lines.lastIndex)..last.coerceIn(0, lines.lastIndex)
        fun blank(n: Int) = isTableRow(lines[n]) || PyText.strip(parseListMarker(lines[n]).rest).isEmpty()
        var items = range.filter { !blank(it) }
        if (items.isEmpty()) {
            if (range.first != range.last || isTableRow(lines[range.first])) return null
            items = listOf(range.first)
        }
        val turningOff = items.all { parseListMarker(lines[it]).kind == kind }
        val edits = ArrayList<Edit>()
        for (n in range) {
            if (!turningOff && n !in items) continue
            if (isTableRow(lines[n])) continue
            val parsed = parseListMarker(lines[n])
            val indent = props[n]?.indent ?: 0
            if (turningOff) {
                if (parsed.marker.isNotEmpty()) {
                    edits.add(Edit(starts[n], cpLength(parsed.marker), 0))
                    lines[n] = parsed.rest
                }
                continue
            }
            val fresh = markerFor(kind, 1, indent)
            edits.add(Edit(starts[n], cpLength(parsed.marker), cpLength(fresh)))
            lines[n] = fresh + parsed.rest
        }
        // Depth stays where it was, on and off: the properties are untouched.
        return finish(Result(lines.joinToString("\n"), props, edits))
    }

    /**
     * `_change_paragraph_indent`: the lines one step deeper or back out,
     * each clamped on its own, list items or not.
     */
    fun changeIndent(body: String, props: Map<Int, Props>, first: Int, last: Int, delta: Int): Done {
        val lines = body.split("\n").toMutableList()
        val starts = lineStarts(lines)
        val edits = ArrayList<Edit>()
        var next = props
        for (n in first.coerceIn(0, lines.lastIndex)..last.coerceIn(0, lines.lastIndex)) {
            if (isTableRow(lines[n])) continue
            val current = props[n]?.indent ?: 0
            next = setIndent(lines, starts, next, n, (current + delta).coerceIn(0, MAX_INDENT), edits)
        }
        return finish(Result(lines.joinToString("\n"), next, edits))
    }

    /**
     * `_set_paragraph_align`: the lines aligned -- or, when every one of
     * them already is, back to the left, as a pressed button undoes itself.
     */
    fun setAlign(body: String, props: Map<Int, Props>, first: Int, last: Int, align: String): Map<Int, Props> {
        val lines = body.split("\n")
        val range = first.coerceIn(0, lines.lastIndex)..last.coerceIn(0, lines.lastIndex)
        val target = if (range.all { (props[it]?.align ?: "left") == align }) "left" else align
        var next = props
        for (n in range) {
            if (isTableRow(lines[n])) continue
            next = withProps(next, n, (next[n] ?: Props()).copy(align = target))
        }
        return next
    }

    /**
     * What a typed edit means in a list, if anything -- the desktop's Enter
     * and Backspace keys, read off the text the keyboard produced:
     *
     *  - A line break typed after an item's marker continues the list: the
     *    new line gets a marker of the same kind, at the same depth. On an
     *    item with nothing written in it, the break is taken back and the
     *    item steps out a level -- or, at the margin, stops being one.
     *  - The marker's last character deleted takes the item out a level, or
     *    at the margin the whole marker -- not one character of it.
     *
     * [old] and [new] are the body before and after the keyboard's edit;
     * [props] are the properties as they were. The result's steps start
     * from [old] -- the keyboard's own edit is the first of them, or is
     * taken back -- so styles move from the text before the keystroke.
     * Null when the edit is not one of these, and the text stays as typed.
     * [pasted]: the edit is a paste, never a word the keyboard corrected.
     */
    fun afterTyping(old: String, new: String, props: Map<Int, Props>, caret: Int, pasted: Boolean = false): Done? {
        if (!pasted) corrected(old, new, props, caret)?.let { return it }
        val (done, from) = typed(old, new, props, caret) ?: return null
        return done.copy(caret = done.movePoint(from))
    }

    /**
     * Enter sent together with a word the keyboard corrected: Gboard's
     * autocorrect makes "monday" "Monday" and breaks the line in one edit,
     * which is not one break typed, so the list did not go on -- the new line
     * came with no marker, and the item's depth was lost. The correction is
     * made first, as plain typing, and then the Enter.
     */
    private fun corrected(old: String, new: String, props: Map<Int, Props>, caret: Int): Done? {
        val a = old.codePoints().toArray()
        val b = new.codePoints().toArray()
        if (caret < 1 || caret > b.size || b[caret - 1] != '\n'.code || oneMore(a, b, caret - 1)) return null
        // Lines put in before a line -- a paste the keyboard's clipboard made --
        // are no word corrected: read as one, "• Eggs" pasted back before
        // "• Bread" gave "• • Bread" (the lows wave's verification, round 5).
        if (linesPutBefore(a, b, caret)) return null
        val m = b.copyOfRange(0, caret - 1) + b.copyOfRange(caret, b.size)
        val mid = String(m, 0, m.size)
        if (mid == old || mid.count { it == '\n' } != old.count { it == '\n' }) return null
        // A correction that is itself a list edit is not a word corrected.
        if (typed(old, mid, props, caret - 1) != null) return null
        val enter = afterTyping(mid, new, props, caret) ?: return null
        var head = 0
        while (head < a.size && head < m.size && a[head] == m[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < m.size - head && a[a.size - 1 - tail] == m[m.size - 1 - tail]) tail++
        val word = Edit(head, a.size - head - tail, m.size - head - tail)
        return enter.copy(steps = listOf(listOf(word)) + enter.steps)
    }

    /** Whether [new] is [old] with text put in at a line's start, ending at [caret]. */
    private fun linesPutBefore(old: IntArray, new: IntArray, caret: Int): Boolean {
        val start = caret - (new.size - old.size)
        if (start !in 0 until caret || (start > 0 && old[start - 1] != '\n'.code)) return false
        for (i in 0 until start) if (old[i] != new[i]) return false
        for (i in start until old.size) if (old[i] != new[i + caret - start]) return false
        return true
    }

    /** Whether [longer] is [shorter] with one code point put in at [at]. */
    private fun oneMore(shorter: IntArray, longer: IntArray, at: Int): Boolean {
        if (longer.size != shorter.size + 1 || at !in longer.indices) return false
        for (i in 0 until at) if (shorter[i] != longer[i]) return false
        for (i in at until shorter.size) if (shorter[i] != longer[i + 1]) return false
        return true
    }

    /**
     * The operation a keystroke stands for, and where its cursor was in the
     * text before it. Where the keystroke landed is read off the cursor
     * ([caret], after it, in [new]) -- not off a comparison of the texts,
     * which cannot tell a break typed at the end of "• a" from one typed at
     * the start of the next line when both read the same.
     */
    private fun typed(old: String, new: String, props: Map<Int, Props>, caret: Int): Pair<Done, Int>? {
        val a = old.codePoints().toArray()
        val b = new.codePoints().toArray()
        val oldLines = old.split("\n")
        val starts = lineStarts(oldLines)
        fun lineAt(offset: Int): Int {
            var n = 0
            while (n + 1 < starts.size && starts[n + 1] <= offset) n++
            return n
        }

        // Enter: one line break, just before the cursor.
        if (caret >= 1 && oneMore(a, b, caret - 1) && b[caret - 1] == '\n'.code) {
            val prefix = caret - 1
            val n = lineAt(prefix)
            val parsed = parseListMarker(oldLines[n])
            val kind = parsed.kind ?: return null
            if (prefix - starts[n] < cpLength(parsed.marker)) return null
            val indent = props[n]?.indent ?: 0
            if (PyText.strip(parsed.rest).isEmpty()) {
                // An empty item: the break is taken back, and the item steps
                // out a level -- or, at the margin, the line is emptied.
                val lines = oldLines.toMutableList()
                val edits = ArrayList<Edit>()
                if (indent > 0) {
                    val next = setIndent(lines, starts, props, n, indent - 1, edits)
                    return finish(Result(lines.joinToString("\n"), next, edits)) to prefix
                }
                edits.add(Edit(starts[n], cpLength(oldLines[n]), 0))
                lines[n] = ""
                return finish(Result(lines.joinToString("\n"), props - n, edits)) to prefix
            }
            // The line broke in two: what follows moves down a line, and
            // the new line carries the item's depth with a fresh marker --
            // and its centring, as the desktop's new item carries the tags
            // of the one it came from.
            val shifted = props.mapKeys { (line, _) -> if (line > n) line + 1 else line }
            val withNew = withProps(shifted, n + 1, Props(indent = indent, align = props[n]?.align ?: "left"))
            val marker = markerFor(kind, 1, indent)
            val text = String(b, 0, prefix + 1) + marker + String(b, prefix + 1, b.size - prefix - 1)
            return finish(Result(text, withNew, listOf(Edit(prefix + 1, 0, cpLength(marker)))),
                          before = listOf(listOf(Edit(prefix, 0, 1)))) to prefix
        }

        // Backspace against a marker: its last character, alone, just
        // after the cursor in the old text.
        if (oneMore(b, a, caret)) {
            val n = lineAt(caret)
            val parsed = parseListMarker(oldLines[n])
            if (parsed.kind != null && caret == starts[n] + cpLength(parsed.marker) - 1) {
                // The deletion is taken back: the marker goes, or the item climbs.
                val lines = oldLines.toMutableList()
                val edits = ArrayList<Edit>()
                val indent = props[n]?.indent ?: 0
                if (indent > 0) {
                    val next = setIndent(lines, starts, props, n, indent - 1, edits)
                    return finish(Result(lines.joinToString("\n"), next, edits)) to caret + 1
                }
                edits.add(Edit(starts[n], cpLength(parsed.marker), 0))
                lines[n] = parsed.rest
                return finish(Result(lines.joinToString("\n"), props, edits)) to caret + 1
            }
        }
        return listEdit(a, b, oldLines, starts, props, caret)
    }

    /** A place in a body: a line, and a column in code points. */
    data class Pos(val line: Int, val column: Int)

    /**
     * `list_deletion_range`: where a deletion should fall when it reaches a
     * list, or null when it reaches none. A marker is characters, and the
     * keyboard's own deletes saw only characters: two lines joined kept the
     * second one's marker among the words ("2. two3. three"), typing over
     * whole items swallowed the next item's ("x4. four"), and nothing
     * renumbered. Word's answer, where a marker is no character at all: two
     * lines joined keep the first one's marker and lose the second's --
     * unless nothing of the first line is kept, when the second takes its
     * place; typing over a selection that ends at an item's start keeps
     * that item; a deletion from just after a marker keeps it, one into a
     * marker takes all of it, and one from a line's very start takes the
     * marker it chose. [start] and [end] are what the key would delete;
     * [typing]: the selection is being typed over.
     */
    fun deletionRange(lines: List<String>, start: Pos, end: Pos, typing: Boolean = false): Pair<Pos, Pos>? {
        val (l1, c1) = start
        val (l2, c2) = end
        val first = parseListMarker(lines[l1])
        val m1 = cpLength(first.marker)
        if (l1 == l2) {
            // Within one line only its own marker can be reached.
            if (first.kind == null || c1 >= m1) return null
            return Pos(l1, 0) to Pos(l2, maxOf(c2, m1))
        }
        val last = parseListMarker(lines[l2])
        val m2 = cpLength(last.marker)
        if (first.kind == null && last.kind == null &&
            (l1 + 1 until l2).none { parseListMarker(lines[it]).kind != null }) return null
        val keepsWords = c1 > (if (first.kind != null) m1 else 0)
        if (last.kind != null && c2 <= m2) {
            // The end is at the start of an item, before its words.
            if (keepsWords) return start to Pos(l2, m2)
            if (typing) return Pos(l1, if (first.kind != null) m1 else 0) to Pos(l2 - 1, cpLength(lines[l2 - 1]))
            return Pos(l1, 0) to Pos(l2, 0)
        }
        // From just after (or inside) a marker into words: the item keeps it.
        if (first.kind != null && c1 in 1..m1) return Pos(l1, m1) to end
        return start to end
    }

    /**
     * Anything else the keyboard took away where a list is -- a selection
     * deleted or typed over, a line's break taken from either side, a paste
     * over items -- done as [deletionRange] says, from the text before it,
     * then renumbered: what was put in is read off the cursor, the text
     * after it being the old text's after the edit. Enter over a selection
     * stands as typed, as before.
     */
    private fun listEdit(a: IntArray, b: IntArray, oldLines: List<String>, starts: IntArray,
                         props: Map<Int, Props>, caret: Int): Pair<Done, Int>? {
        val end = caret + a.size - b.size          // in the old text
        if (caret !in 0..b.size || end !in 0..a.size) return null
        for (i in 0 until b.size - caret) if (b[caret + i] != a[end + i]) return null
        var p = 0
        while (p < minOf(caret, end) && a[p] == b[p]) p++
        if (end <= p) return null                   // nothing taken away
        val typed = String(b, p, caret - p)
        if (typed == "\n") return null
        fun pos(offset: Int): Pos {
            var n = 0
            while (n + 1 < starts.size && starts[n + 1] <= offset) n++
            return Pos(n, offset - starts[n])
        }
        val (from, to) = deletionRange(oldLines, pos(p), pos(end), typing = typed.isNotEmpty()) ?: return null
        val at = starts[from.line] + from.column
        val upto = starts[to.line] + to.column
        val text = String(a, 0, at) + typed + String(a, upto, a.size - upto)
        // The lines' properties: those above stay; the line the edit lands
        // on keeps the first line's -- or the last line's, when that line
        // took the first one's place whole -- and those below move up.
        val landing = if (from.column == 0 && to.column == 0 && typed.isEmpty()) props[to.line] else props[from.line]
        val added = typed.count { it == '\n' }
        val moved = HashMap<Int, Props>()
        for ((n, prop) in props) {
            if (n < from.line) moved[n] = prop
            else if (n > to.line) moved[n - (to.line - from.line) + added] = prop
        }
        if (landing != null && !landing.plain) moved[from.line] = landing
        return finish(Result(text, moved, listOf(Edit(at, upto - at, cpLength(typed))))) to upto
    }
}
