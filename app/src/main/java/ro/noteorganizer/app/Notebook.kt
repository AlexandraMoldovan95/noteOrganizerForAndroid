package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Date

/** The label a note without a title is listed by, as the desktop lists it. */
const val UNTITLED = "(untitled)"

/** A title as the lists show it: stripped as the desktop strips it, or [UNTITLED]. */
fun shownTitle(title: String?): String = title?.let(PyText::strip)?.ifEmpty { null } ?: UNTITLED

/**
 * One note, in the shape the Windows desktop app actually writes to disk.
 *
 * The Python is the specification. Every default here is the one
 * `normalize_note` applies, and every field is read the way the desktop
 * reads it -- not the way the JSON happens to look.
 *
 * Nothing in this file throws. A hand-edited, truncated or hostile file
 * must produce a shorter list, never a crash: that is the same promise
 * `normalize_note` makes at its single choke point, and it is the reason
 * bad data has never left the desktop app unopenable twice running.
 */
data class Note(
    val id: Int = 0,
    val title: String = "",
    val body: String = "",
    val category: String = "General",
    val color: String = "",
    val date: String = "",
    val updated: String = "",
    val created: String = "",
    /** Where the person put it in Custom order; null until they do. */
    val order: Double? = null,
    val pinned: Boolean = false,
    val comments: String = "",
    /** How many files, how many paragraph records: counts, where the file holds the lists. */
    val attachmentCount: Int = 0,
    val formatting: List<FormatSpan> = emptyList(),
    val paragraphCount: Int = 0,
    val checklist: List<ChecklistItem> = emptyList(),
    val recurrenceFreq: String = "",
    val recurrenceInterval: Int = 1,
    /** The rule in words, as the desktop's card says it: "Every 2 weeks on Mon, Thu". */
    val recurrenceLabel: String = "",
    /** How many days the series skips, and how many keep edits of their own. */
    val exceptionCount: Int = 0,
    val overrideCount: Int = 0,
    /** Only a trashed note carries this: "YYYY-MM-DD HH:MM:SS". */
    val deletedAt: String = "",
    /**
     * The note's identity, as opposed to [id], which is only its place in
     * THIS file. Two devices both hand a new note `max + 1`, so both
     * invent 87 and nothing can tell the two apart afterwards.
     *
     * Empty for a note written before the desktop app learned to mint
     * these, and left so when the file is read: this app mints one only
     * for a note it adds -- created here, or brought by an import without
     * one ([Normalize.normalizeNote]), as the desktop mints at its own door.
     */
    val uid: String = "",
    /** Whether a rescue copy ("↩ Restore previous") is waiting. */
    val hasPrevious: Boolean = false,
    /**
     * What that copy brings back besides the text, in the words the
     * desktop's question uses ("checklist", "repeat rule"...). Empty for a
     * drastic shrink's copy, which is the text alone.
     */
    val previousAlso: List<String> = emptyList(),
) {
    /**
     * A note repeats when its rule names a frequency -- NOT when its
     * recurrence object is non-empty.
     *
     * `normalize_note` gives every saved note a fully populated rule, so
     * an emptiness test reports the whole notebook as repeating: measured
     * against a real 77-note file, emptiness said 77 and this said 2.
     * Mirrors `is_recurring` in core/recurrence.py.
     */
    val isRecurring: Boolean get() = recurrenceFreq.isNotEmpty()

    val checklistTotal: Int get() = checklist.size
    val checklistDone: Int get() = checklist.count { it.done }

    /**
     * Everything this note can be found by, folded ([searchFold]), computed once.
     *
     * Once, because the alternative is stripping a 107,000-character
     * body's table plumbing on every keystroke for every note. The
     * desktop solves the same cost by debouncing the search box; doing
     * the work a single time is better than doing it later.
     */
    private val searchText: String by lazy {
        val bodyText = if (body.contains('|')) contentText(body) else body
        searchFold(title + "\n" + bodyText + "\n" + comments + "\n" + checklist.joinToString("\n") { it.text })
    }

    /**
     * The ONE definition of "this note matches the search".
     *
     * The desktop learned this the hard way: the list render and "select
     * all visible" each carried a hand-rolled copy, the copies drifted,
     * and "select all" quietly selected notes the filter had hidden --
     * then bulk actions hit them. One definition, every caller.
     *
     * Title, body, comments and the checklist's rows -- the desktop's rule,
     * since a difference here is a surprise, not a feature. The query's ends
     * are not part of it: a word picked from the keyboard's suggestions comes
     * with a space after it, and missed every word ending a line.
     */
    fun matches(query: String): Boolean {
        val q = searchFold(PyText.strip(query))
        if (q.isEmpty()) return true
        return searchText.contains(q)
    }

    /**
     * The card's words and its table count, from the desktop's one pass
     * ([previewText]) -- worked out ONCE per note, not on every drawing:
     * a card scrolling into sight used to walk its whole body two or three
     * times, and on a note of tens of thousands of characters that was a
     * frame missed each time.
     *
     * Table lines are left out, as the desktop leaves them out: rows read
     * as pipe soup at card size, and the tags line says a table is there.
     */
    private val previewParts: Pair<List<String>, Int> by lazy {
        val (prose, tables) = previewText(body)
        prose.split("\n").map { it.trim() }.filter { it.isNotEmpty() } to tables
    }

    /** Body lines worth previewing: blanks and table lines dropped. */
    private val spokenLines: List<String> get() = previewParts.first

    /** How many tables the body holds -- runs of lines a person reads as a table. */
    val tableCount: Int get() = previewParts.second

    /**
     * The phone's headline. Measured against a real notebook, 48 of 77
     * notes have no title and 15 more have one of three characters or
     * fewer, so leading with the title puts "(untitled)" in the largest
     * type on two cards out of three. This falls through to the first
     * line the note actually says.
     */
    val headline: String by lazy {
        val t = title.trim()
        when {
            // A glimpse of a title too: one of 150,000 letters with no space,
            // which a share code can bring, held the list 3.8 s at every start.
            t.isNotEmpty() -> glimpse(t)
            spokenLines.isNotEmpty() -> glimpse(spokenLines.first())
            // Words only in a table: the note is not empty, it is untitled.
            body.isNotBlank() -> UNTITLED
            else -> "(empty)"
        }
    }

    /** Whether `headline` came from the body rather than from a real title. */
    val headlineIsBorrowed: Boolean get() = title.trim().isEmpty()

    /**
     * The rest of the note, after whatever the headline already used --
     * a glimpse, not the whole text: the card shows two lines of it, and
     * laying out tens of thousands of characters to show two lines is
     * what made the list stutter on a phone.
     */
    val snippet: String by lazy {
        val rest = if (headlineIsBorrowed) spokenLines.drop(1) else spokenLines
        val out = StringBuilder()
        for (line in rest) {
            if (out.length >= GLIMPSE) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(glimpse(line))
        }
        glimpse(out.toString())
    }

    /** The tags line, worked out once: it is drawn on every card in sight. */
    private val tags: String by lazy { buildTagLine() }

    /**
     * The desktop card's third line, rebuilt in the same order:
     * category, then the repeat rule or the date, then the badges.
     */
    fun tagLine(): String = tags

    private fun buildTagLine(): String {
        val parts = StringBuilder(category)
        if (isRecurring) {
            parts.append("   🔁 ").append(recurrenceLabel)
            if (exceptionCount > 0) parts.append("   ✕").append(exceptionCount)
            if (overrideCount > 0) parts.append("   ✎").append(overrideCount)
        } else if (date.isNotEmpty()) {
            parts.append("   📅 ").append(Recurrence.showDate(date))
        }
        if (comments.isNotEmpty()) parts.append("   💬")
        if (attachmentCount > 0) parts.append("   📎×").append(attachmentCount)
        val tables = tableCount
        if (tables > 0) {
            parts.append(if (tables == 1) "   ▦ 1 table" else "   ▦ $tables tables")
        }
        if (checklistTotal > 0) {
            parts.append("   ☑ ").append(checklistDone).append('/').append(checklistTotal)
        }
        return parts.toString()
    }
}

/**
 * Each character of the basic plane as a search compares it: its base
 * letter, the diacritic dropped, then lowered -- always one for one, so a
 * match's place in the text is its place in the folded text. Only marks are
 * dropped: a Korean syllable comes apart into letters, and cut to its first it
 * found every syllable that starts with the same one. The desktop's
 * `_search_fold_table`.
 */
private val SEARCH_FOLD: CharArray by lazy {
    CharArray(0x10000) { code ->
        val ch = code.toChar()
        if (ch.isSurrogate()) ch
        else {
            val parts = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            val marks = (1 until parts.length).all { Character.getType(parts[it]) == Character.NON_SPACING_MARK.toInt() }
            val base = if (marks) parts[0] else ch
            val low = base.toString().lowercase(java.util.Locale.ROOT)
            if (low.length == 1) low[0] else base
        }
    }
}

/**
 * [text] as every search compares it -- the list, Find in note, the
 * History: case and diacritics gone, so "sedinta" finds "Ședință" and ș with
 * a comma finds ş with a cedilla. The desktop's `fold_for_search`; a
 * character beyond the basic plane stays as it is.
 *
 * The phone folded the list's search first, and the reason is the language:
 * Romanian diacritics are routinely dropped when typing quickly, and a
 * notebook written half with them and half without is unsearchable under a
 * strict rule. The owner chose it for every search on both apps (2026-11-03).
 */
fun searchFold(text: String): String {
    val out = CharArray(text.length)
    for (i in text.indices) out[i] = SEARCH_FOLD[text[i].code]
    return String(out)
}

/**
 * How the list is ordered. The desktop's options, same rules -- and the
 * desktop's own words, which are also the values `note_sort` stores, so a
 * choice made on one device reads back on the other.
 */
enum class SortOrder(val label: String) {
    UPDATED_NEW("Recently updated"),
    UPDATED_OLD("Oldest updated"),
    TITLE_AZ("Title A–Z"),
    TITLE_ZA("Title Z–A"),
    CATEGORY("Category"),
    SOONEST("Calendar: soonest"),
    OLDEST_DATE("Calendar: oldest"),
    CUSTOM("Custom order");

    companion object {
        /** A stored label, or the default for one this version does not know. */
        fun fromLabel(label: String?): SortOrder = entries.firstOrNull { it.label == label } ?: UPDATED_NEW
    }
}

/**
 * Orders the list, then floats the pinned notes to the top.
 *
 * Pinning is applied as a SECOND, stable sort rather than as part of the
 * key, exactly as the desktop does it: pinned notes rise but keep their
 * relative order from whatever sort the user chose.
 *
 * Untitled notes sink to the bottom of an alphabetical sort instead of
 * clustering at the top, which is where empty strings would naturally
 * land and is never what anyone means.
 */
fun sortNotes(notes: List<Note>, order: SortOrder, today: String = Recurrence.formatDate(java.time.LocalDate.now()),
              nextDay: (Note) -> String? = { null }): List<Note> {
    val sorted = when (order) {
        SortOrder.UPDATED_NEW -> notes.sortedByDescending { it.updated }
        SortOrder.UPDATED_OLD -> notes.sortedBy { it.updated }
        // Folded as a search folds: by code point, Ă Î Ș Ț came after Z.
        SortOrder.TITLE_AZ -> notes.sortedWith(
            compareBy({ it.title.isBlank() }, { searchFold(it.title) }, { it.title }))
        SortOrder.TITLE_ZA -> notes.sortedWith(
            compareBy<Note> { it.title.isBlank() }
                .thenByDescending { searchFold(it.title) }.thenByDescending { it.title })
        SortOrder.CATEGORY -> notes.sortedWith(
            compareBy({ searchFold(it.category) }, { it.category }, { it.updated }))
        SortOrder.SOONEST -> {
            // Each note's NEXT date from today, a series by its next day:
            // what is coming first, then the past newest first, then the
            // undated. A series that has ended stands with the past by its
            // start date, as on the desktop.
            fun group(n: Note): Pair<Int, String> {
                if (n.isRecurring) {
                    nextDay(n)?.let { return 0 to it }
                    return if (Notebook.isDate(n.date)) 1 to n.date else 2 to ""
                }
                if (!Notebook.isDate(n.date)) return 2 to ""
                return if (n.date >= today) 0 to n.date else 1 to n.date
            }
            // Each note's place worked out once, before sorting: asked inside
            // the comparison, a series' next day was worked out twice per
            // comparison -- 7,584 times for 2,000 notes, each over the whole
            // notebook -- again after every save.
            notes.map { it to group(it) }.sortedWith(Comparator { (_, a), (_, b) ->
                val (ga, da) = a
                val (gb, db) = b
                when {
                    ga != gb -> ga.compareTo(gb)
                    ga == 1 -> db.compareTo(da)
                    else -> da.compareTo(db)
                }
            }).map { it.first }
        }
        // Plain chronology by the note's own date: the archive walk.
        SortOrder.OLDEST_DATE -> notes.sortedWith(
            compareBy({ !Notebook.isDate(it.date) }, { if (Notebook.isDate(it.date)) it.date else "" }))
        // Where the person put each note -- on the desktop, for now; one
        // nobody has placed stands above, newest first, as it does there.
        SortOrder.CUSTOM -> notes
            .sortedWith(compareByDescending<Note> { it.created }.thenByDescending { it.id })
            .sortedWith(compareBy<Note> { it.order != null }.thenBy { it.order ?: 0.0 })
    }
    return sorted.sortedBy { !it.pinned }
}

/**
 * What the list's "Show" can narrow it to, and the one judgement of
 * whether a note passes -- the desktop's `_filter_options` and
 * `_note_passes_filter`, under the desktop's own labels, which are what
 * `note_filter` stores.
 */
object Showing {
    const val ALL = "All"
    const val REPEATING = "🔁 Repeating"
    const val DATED = "📅 Dated"
    const val FILES = "📎 With files"

    fun options(categories: Collection<String>): List<String> = listOf(ALL, REPEATING, DATED, FILES) + categories

    /** The stored choice, or All when it is no longer on offer -- a category since deleted. */
    fun valid(selected: String?, categories: Collection<String>): String =
        if (selected != null && selected in options(categories)) selected else ALL

    fun passes(note: Note, selected: String): Boolean = when (selected) {
        ALL -> true
        REPEATING -> note.isRecurring
        DATED -> !note.isRecurring && note.date.isNotEmpty()
        FILES -> note.attachmentCount > 0
        else -> note.category == selected
    }

    /**
     * A search's words as they are handed to Android, to be given back after
     * it ends the app behind Home: kept while they are short, nothing past
     * [SEARCH_KEPT] -- the words themselves stay in the box while the app
     * runs. Android hands that state over in one transaction, and past about
     * half a million letters the app stopped as Home was pressed (ISSUES_LOG,
     * "A crash in front of the crash foretold"); the list's search, kept
     * twice, got there at half that, from one paste (the final audit's
     * eighth round). The desktop's search box takes any length, so the box
     * keeps no cap.
     */
    fun searchKept(words: String): String? = words.takeIf { it.length <= SEARCH_KEPT }

    const val SEARCH_KEPT = 10_000

    /** One row of a list grouped by category: a header, or a note under it. */
    sealed class Row {
        data class Header(val category: String, val count: Int, val collapsed: Boolean) : Row()
        data class Item(val note: Note) : Row()
    }

    /**
     * The list's rows, in order. Grouped, each category is a header over
     * its notes, in the order Settings keeps the categories, a category no
     * longer in that list keeping a group of its own so no note is dropped.
     */
    fun rows(notes: List<Note>, grouped: Boolean, collapsed: Set<String>, categoryOrder: List<String>): List<Row> {
        if (!grouped) return notes.map { Row.Item(it) }
        val groups = LinkedHashMap<String, MutableList<Note>>()
        for (note in notes) groups.getOrPut(note.category.ifEmpty { Themes.PROTECTED_CATEGORY }) { ArrayList() }.add(note)
        val ordered = categoryOrder.filter { it in groups } + groups.keys.filter { it !in categoryOrder }
        val out = ArrayList<Row>()
        for (category in ordered) {
            val members = groups.getValue(category)
            val shut = category in collapsed
            out.add(Row.Header(category, members.size, shut))
            if (!shut) members.forEach { out.add(Row.Item(it)) }
        }
        return out
    }

    /**
     * The notes the list shows as cards: what Select all takes, and the
     * desktop's `_visible_note_ids`. A folded group's notes are counted in
     * its header but not shown, and are not taken -- taken, Select all and
     * then the Trash button sent notes nobody could see.
     */
    fun visibleIds(rows: List<Row>): Set<Int> = rows.filterIsInstance<Row.Item>().map { it.note.id }.toSet()

    /** A row's key: the list keeps the card it has in sight by it, and a reveal finds that card by it. */
    fun key(row: Row): Any = when (row) {
        is Row.Header -> "h:" + row.category
        is Row.Item -> row.note.id
    }

    /**
     * Which of [ids] the change from [listed] to [rows] moved: their place
     * differs, or they arrived. Under a sort a move does not reorder -- Title
     * A-Z, Custom order, a calendar's -- filing a selection made across a
     * scroll brought the list back to the first card selected, screens above,
     * nothing moved; a pin of notes some already pinned scrolled to one of
     * those (wave 2, rounds 10, 14 and 16).
     */
    fun movedIds(listed: List<Row>, rows: List<Row>, ids: Set<Int>): Set<Int> {
        fun place(of: List<Row>, id: Int) = of.indexOfFirst { (it as? Row.Item)?.note?.id == id }
        return ids.filterTo(HashSet()) { place(listed, it) != place(rows, it) }
    }

    /**
     * Where the list scrolls to bring the moved notes [ids] into sight among
     * [rows] -- the first of them in the order the list has now -- or null
     * when it needs none: none listed, or already in the window of [seen],
     * the keys at least half in sight when the action was taken. The list
     * keeps its first item, [anchor], a sliver too, [lead] rows above [seen].
     * When that item moved itself, the list follows it only so far -- about
     * a hundred rows, Compose's nearest range -- and past that keeps its
     * place by index: no window is trusted then, and the card is brought into
     * sight (rounds 14-15). A list [atTop] stays at its top when the card
     * shows from there; in a group, the header comes with its card.
     */
    fun revealAt(rows: List<Row>, ids: Set<Int>, seen: List<Any>, atTop: Boolean, anchor: Any?, lead: Int): Int? {
        val at = rows.indexOfFirst { (it as? Row.Item)?.note?.id in ids }
        if (at < 0) return null
        if (anchor is Int && anchor in ids) return toward(rows, at, seen, atTop)
        val kept = rows.indexOfFirst { key(it) == anchor }
        // The first item gone, the list keeps its place by index: the window
        // counted, as best it can be, from a card in sight that did not move.
        val k = seen.indexOfFirst { it !in ids }
        val first = when {
            kept >= 0 -> kept + lead
            k < 0 -> -1
            else -> rows.indexOfFirst { key(it) == seen[k] }.let { if (it < 0) -1 else it - k }
        }
        if (first >= 0 && at in first until first + seen.size) return null
        return toward(rows, at, seen, atTop)
    }

    /** Where to scroll for the card at [at]: the top when it shows from there, its header with it. */
    private fun toward(rows: List<Row>, at: Int, seen: List<Any>, atTop: Boolean): Int = when {
        atTop && at < seen.size -> 0
        at > 0 && rows[at - 1] is Row.Header -> at - 1
        else -> at
    }

    /**
     * Whether a dragged card's slot is still in the viewport, [start] to
     * [end], once it trades one [step] with its neighbour, the list held by
     * place -- or, when the card [leads] the screen and steps up, kept where
     * it is. A card laid out of the viewport went, and its drag with it, when
     * a taller neighbour came in at the edge (wave 2, round 17). Offsets and
     * sizes are the layout's, before the trade. A card that leads trades so
     * once, until it stops leading ([ledOnce]): the next card above lay in
     * the list's top padding again, and a slow edge scroll chained ten or
     * twenty such trades in a fifth of a second (round 23).
     */
    fun tradeStaysInSight(step: Int, meOffset: Int, meSize: Int, otherOffset: Int, otherSize: Int,
                          leads: Boolean, start: Int, end: Int, ledOnce: Boolean = false): Boolean =
        if (step > 0) otherOffset + otherSize - meSize < end
        else if (leads) !ledOnce
        else otherOffset + meSize > start

    /**
     * How far to scroll to bring a card at [top], [size] tall, wholly into a
     * viewport from 0 to [end]: 0 when it is, never so far that its top goes.
     * A card stepped past the top or the bottom card on screen went out of
     * sight, or was left a strip (wave 2, round 17).
     */
    fun intoSight(top: Int, size: Int, end: Int): Int = when {
        top < 0 -> top
        top + size > end -> minOf(top + size - end, top)
        else -> 0
    }

    /**
     * The way a drag is heading (+1 down, -1 up, 0 not yet a drag) once the
     * finger is [pulled] from the press, and its furthest point that way,
     * from the [heading] and [far] before: a drag only past the touch [slop],
     * as Android's own, and turned only once back past it from [far]. Taken
     * from each frame's sign, a tap's pixel of shift opened no menu, and a
     * hair's recoil at an edge stopped the scroll or ran it against the
     * finger, moving the note the wrong way (wave 2, round 19).
     */
    fun dragHeading(heading: Int, far: Float, pulled: Float, slop: Float): Pair<Int, Float> = when {
        heading == 0 -> if (kotlin.math.abs(pulled) > slop) Pair(if (pulled > 0f) 1 else -1, pulled) else Pair(0, far)
        heading > 0 && pulled > far || heading < 0 && pulled < far -> Pair(heading, pulled)
        kotlin.math.abs(pulled - far) > slop -> Pair(-heading, pulled)
        else -> Pair(heading, far)
    }

    /**
     * Which way a dragged card at [top], [size] tall, scrolls the list at an
     * edge of the viewport from [start] to [end] (-1 up, +1 down, 0 not):
     * toward the edge it is [heading], and only once the finger is past the
     * touch [slop] beyond the press that way ([pulled]). On the heading
     * alone, the top card nudged down and put back scrolled the list under
     * it, and was saved a place up (wave 2, round 20).
     */
    fun edgeScroll(heading: Int, pulled: Float, slop: Float, top: Float, size: Int,
                   start: Int, end: Int, edge: Float): Int = when {
        heading < 0 && pulled < -slop && top < start + edge -> -1
        heading > 0 && pulled > slop && top + size > end - edge -> 1
        else -> 0
    }

    /**
     * How deep the edge zones of a list from [start] to [end] reach: [most],
     * or an eighth of a short list. On a phone on its side every resting
     * card sat in a zone of [most] (wave 2, round 22).
     */
    fun edgeZone(start: Int, end: Int, most: Float): Float = minOf(most, (end - start) / 8f)

    /**
     * How far one pass of the edge loop scrolls the [way] edgeScroll gave,
     * for a card [depth] into a zone [edge] deep, [heldMs] after the scroll
     * began: a pixel at first, up to [most] as the card goes deeper and is
     * held longer, as Android's own reorder does. At one speed a drag of one
     * place on a phone on its side went two or three (wave 2, round 22).
     */
    fun edgeStep(way: Int, depth: Float, edge: Float, heldMs: Long, most: Float): Float {
        if (way == 0) return 0f
        val out = 1f - (depth / edge).coerceIn(0f, 1f)
        val held = (heldMs / 2000f).coerceIn(0f, 1f)
        val speed = most * (1f - out * out * out * out * out) * (held * held * held * held * held)
        return way * maxOf(1f, speed)
    }

    /**
     * When the edge's scroll began ([since], 0 for not yet), as the card at
     * [side] (-1 the top zone, +1 the foot's, 0 neither; [sideWas] before)
     * scrolls the list [toward]: from its first pass in a zone until it
     * leaves it. Begun again at every turn of the finger, a nudge at full
     * speed fell back to a crawl (wave 2, round 23).
     */
    fun edgeSince(since: Long, sideWas: Int, side: Int, toward: Int, now: Long): Long = when {
        side != sideWas -> if (toward != 0) now else 0L
        since == 0L && toward != 0 -> now
        else -> since
    }

    /**
     * Whether the edge stays armed past the press for a list already
     * scrolling [way] while the drag still heads that way: a settle of a
     * few pixels back inside the slop stopped it (wave 2, round 21).
     */
    fun edgeLatched(way: Int, heading: Int): Boolean = way != 0 && way == heading

    /**
     * How far to scroll once a card [size] tall has moved one [step], so it
     * and the card it passed, [passedSize] tall, show together: the card at
     * [top] when the layout holds it, else beside the passed card at
     * [passedTop] -- null when the layout holds neither. A card stepped down
     * past the foot jumped a screen (round 18); brought into sight alone, it
     * left a shorter card it passed above the screen (round 19). A pair
     * taller than the screen keeps the moved card, as a step up does: the
     * pair's top left it under the + (round 20). The moved card's own [gap]
     * below it is no part of the pair: counted, a pair 8 dp too tall lost
     * the card it passed (round 21).
     */
    fun afterStep(top: Int?, size: Int, passedTop: Int?, passedSize: Int, step: Int, end: Int,
                  gap: Int = 0): Int? = when {
        top != null && step > 0 && passedSize + size - gap <= end ->
            intoSight(top - passedSize, passedSize + size - gap, end)
        top != null && step > 0 -> intoSight(top, size, end)
        top != null -> intoSight(top, size + passedSize, end)
        passedTop == null || size <= 0 -> null
        else -> intoSight(if (step > 0) passedTop + passedSize else passedTop - size, size, end)
    }
}

/** What a save did, or the reason it did not. Never silence. */
sealed class SaveResult {
    data class Ok(
        val id: Int,
        val lostStyles: Int = 0,
        /** Only an import fills this: how many notes arrived. */
        val imported: Int = 0,
        /** The note made of the words this save took beside an Update's copy, 0 for none. */
        val keptAside: Int = 0,
        /** Whether that note holds the comments alone, the save taking no more. */
        val keptComments: Boolean = false,
        /** The notes kept aside that went again, their words back in the note. */
        val spentAside: List<Int> = emptyList(),
    ) : SaveResult()

    data class Failed(val reason: String) : SaveResult()
}

/**
 * The note's own text split into code points, which is how Python counts.
 * Straight into an array: gathered in a list first, each code point was an
 * object of its own, and a 25-million-letter note asked for some 600 MB on
 * its way in from a backup, more than a phone gives an app.
 */
internal fun codePointsOf(text: String): IntArray = text.codePoints().toArray()

/**
 * How many items two sequences of [aSize] and [bSize] share at their start,
 * then at their end, no item counted twice -- where an edit sits, found by
 * what did not change. [same] compares the first's i-th with the second's j-th.
 */
internal inline fun sharedEnds(aSize: Int, bSize: Int, same: (Int, Int) -> Boolean): Pair<Int, Int> {
    var prefix = 0
    while (prefix < aSize && prefix < bSize && same(prefix, prefix)) prefix++
    var suffix = 0
    while (suffix < aSize - prefix && suffix < bSize - prefix && same(aSize - 1 - suffix, bSize - 1 - suffix)) suffix++
    return prefix to suffix
}

/** Spans that survived an edit, and how many did not. */
data class Remapped(val spans: List<FormatSpan>, val lost: Int)

/**
 * Moves a note's formatting to where the text went after a plain-text edit.
 *
 * This is the whole reason editing needed thought. Formatting is stored as
 * POSITIONS — "bold from character 40 to 55". Delete a word near the top
 * and everything after it slides, while the positions do not: the bold
 * would still say 40..55 and would now sit on different words. Not lost —
 * *moved onto the wrong text*, which is worse, because nothing looks
 * broken. 41 of a real notebook's 77 notes carry formatting.
 *
 * The edit is located by what did NOT change: the longest identical
 * prefix and the longest identical suffix. Everything before the prefix
 * keeps its position, everything after the suffix shifts by the change in
 * length, and only the middle is uncertain.
 *
 * Inside that middle the rule is deliberately timid — a span's start moves
 * FORWARD past the edit and its end moves BACK to where the edit began, so
 * formatting never creeps over words the user has just typed. A span that
 * lived entirely inside deleted text collapses and is dropped, and the
 * count of those is returned rather than quietly absorbed.
 *
 * Offsets are counted in code points throughout, because that is the
 * coordinate system the file stores.
 */
fun remapSpans(old: String, new: String, spans: List<FormatSpan>): Remapped {
    if (old == new || spans.isEmpty()) return Remapped(spans, 0)

    val a = codePointsOf(old)
    val b = codePointsOf(new)
    val (prefix, suffix) = sharedEnds(a.size, b.size) { i, j -> a[i] == b[j] }

    val oldSuffixStart = a.size - suffix
    val newSuffixStart = b.size - suffix
    val delta = b.size - a.size

    var lost = 0
    val kept = ArrayList<FormatSpan>(spans.size)
    for (span in spans) {
        // A span that lay ENTIRELY inside the edited stretch styled text
        // that is gone. Stretching it over the replacement would hand the
        // user's new words the styling of the words they deleted -- which
        // a first version of this did, and a test caught: bold on "bb",
        // replaced with "NOU NOU", came back as bold "NOU NOU".
        //
        // A span that reaches PAST the edit on either side is different
        // and is stretched on purpose: editing one word inside a bold
        // sentence must not un-bold the sentence.
        if (span.start >= prefix && span.end <= oldSuffixStart) {
            lost++
            continue
        }
        var start = when {
            span.start <= prefix -> span.start
            span.start >= oldSuffixStart -> span.start + delta
            else -> newSuffixStart          // timid: skip past the edit
        }
        var end = when {
            span.end <= prefix -> span.end
            span.end >= oldSuffixStart -> span.end + delta
            else -> prefix                  // timid: stop where it began
        }
        start = start.coerceIn(0, b.size)
        end = end.coerceIn(0, b.size)
        if (end <= start) {
            lost++
            continue
        }
        kept.add(span.copy(start = start, end = end))
    }
    return Remapped(kept, lost)
}

/**
 * The same problem one level up: a bullet or a number is stored as a LINE
 * index, so inserting a line above one renumbers everything below it. The
 * line list is diffed the same way, and a marker whose line was inside the
 * edit is dropped rather than left pointing at someone else's line.
 */
fun remapParagraphLines(old: String, new: String, lines: List<Int>): Map<Int, Int> {
    if (lines.isEmpty()) return emptyMap()
    if (old == new) return lines.associateWith { it }

    val a = old.split("\n")
    val b = new.split("\n")
    val (prefix, suffix) = sharedEnds(a.size, b.size) { i, j -> a[i] == b[j] }

    val oldSuffixStart = a.size - suffix
    val delta = b.size - a.size
    // As many lines in the edited stretch after as before: its lines were
    // typed INTO, not added or taken away, and each keeps what it was --
    // as a line on the desktop keeps its bullet while text is typed in it.
    // Without this, typing a letter into a list item on the phone dropped
    // its bullet, its indent or its centring.
    val inPlace = delta == 0

    // old line -> new line, so a caller can carry each marker's KIND with
    // it. Returning a bare list nearly shipped a bug: drop one line and
    // every kind after it pairs with the wrong marker.
    val kept = LinkedHashMap<Int, Int>()
    for (line in lines) {
        when {
            line < prefix -> kept[line] = line
            line >= oldSuffixStart -> {
                val moved = line + delta
                if (moved in b.indices) kept[line] = moved
            }
            inPlace -> kept[line] = line
            // A line inside an edit that added or removed lines: whatever it
            // marked may no longer exist. Dropping the marker loses a
            // bullet; keeping it puts a bullet on a stranger's line.
            else -> Unit
        }
    }
    return kept
}

/**
 * Drops unpaired surrogates, which are not characters and which a JSON
 * writer either refuses or emits as something nothing can read back.
 *
 * The desktop's `mend_surrogates` exists because an emoji in a note once
 * made every save after it fail, silently, for sixteen autosaves in a row
 * -- every edit lived only in the running app. The guard is cheap; the
 * failure it prevents is not.
 */
internal fun mendSurrogates(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            Character.isHighSurrogate(ch) -> {
                if (i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                    out.append(ch).append(text[i + 1])
                    i += 2
                } else {
                    i += 1          // a high surrogate with no partner
                }
            }
            Character.isLowSurrogate(ch) -> i += 1   // a partner with no high
            else -> {
                out.append(ch)
                i += 1
            }
        }
    }
    return out.toString()
}

/** One checklist row, as `normalize_note` stores it. */
data class ChecklistItem(val text: String, val done: Boolean)

/**
 * One run of styling over the body.
 *
 * `start` and `end` are offsets into `body` counted the way PYTHON counts
 * them -- in characters, which are code points. Kotlin indexes strings in
 * UTF-16 units, where an emoji is two, so these numbers must be converted
 * before they touch a string index. See [utf16Offsets].
 *
 * `value` carries the extra data for the styles that need it: a `#rrggbb`
 * for `text_color` and `highlight`, a number for `font_size`, a face name
 * for `font_family`. The on/off styles leave it empty.
 */
data class FormatSpan(
    val start: Int,
    val end: Int,
    val style: String,
    val value: String = "",
)

/**
 * Translates Python's character offsets into Kotlin's string indices.
 *
 * Python counts a body in code points, so an emoji is one character.
 * Kotlin counts UTF-16 units, so the same emoji is two. Every span that
 * begins after an emoji therefore lands one position early per emoji
 * before it. This is not theoretical: against a real notebook, 4 notes
 * would have drawn their styling in the wrong place, one of them across
 * 24 spans shifted by 3.
 *
 * The offsets are sorted and the body is walked ONCE for all of them,
 * rather than counted from the start for each. That is the same fix the
 * desktop app needed when reading formatting took 14 seconds of its
 * 15-second startup: the cost is in re-walking, not in the walk.
 *
 * An offset past the end clamps to the end, and a negative one to zero --
 * this data can be hand-edited, so it is coerced rather than trusted.
 */
fun utf16Offsets(text: String, wanted: Collection<Int>): Map<Int, Int> {
    val out = HashMap<Int, Int>(wanted.size)
    var codePoints = 0
    var index = 0
    for (target in wanted.toSortedSet()) {
        if (target <= 0) {
            out[target] = 0
            continue
        }
        while (codePoints < target && index < text.length) {
            val ch = text[index]
            val pair = Character.isHighSurrogate(ch) &&
                index + 1 < text.length &&
                Character.isLowSurrogate(text[index + 1])
            index += if (pair) 2 else 1
            codePoints++
        }
        out[target] = index
    }
    return out
}

/**
 * A line reads as a table row when BOTH ends are a pipe and it holds at
 * least two cells. A sentence that merely contains one -- "a | b" in
 * prose, a maths expression -- is not a row, and neither is a line that
 * only opens with one. Mirrors `is_table_row` in core/tables.py, with
 * Python's spaces: the one answer the editor, the paste and the export ask.
 */
internal fun isTableRow(line: String): Boolean {
    val s = PyText.strip(line)
    if (s.length < 3 || !s.startsWith("|") || !s.endsWith("|")) return false
    return splitRow(s).size >= 2
}

/**
 * How much of a note's text a card is given: more than its two lines can
 * show on the widest pane, far less than a long note holds. The cut is
 * never seen -- the card's own ellipsis comes first.
 */
private const val GLIMPSE = 300

/**
 * [text] cut to [GLIMPSE] characters, never through the middle of an emoji:
 * what a line of a list, a calendar day or a picker is given of a title or
 * a name, which no one wrote that long but a share code or a backup can.
 */
internal fun glimpse(text: String): String {
    if (text.length <= GLIMPSE) return text
    val end = if (Character.isLowSurrogate(text[GLIMPSE])) GLIMPSE - 1 else GLIMPSE
    return text.substring(0, end)
}

/**
 * A checklist row as a list of rows draws it where it is not typed into --
 * the template editor: a [glimpse], with "…" where it was cut. Drawn
 * whole, wrapping, a 150,000-letter row saved from a note froze the
 * template editor as it opened, and a thousand rows of 9,999 letters, each
 * under every other limit, a thousand times over. Kept whole, drawn short,
 * as the desktop draws a checklist item longer than its limit.
 */
internal fun drawnRow(text: String): String = glimpse(text).let { if (it.length < text.length) "$it…" else text }

/**
 * How much of a stored title, checklist row, template name or file name a
 * message, a question or a screen reader quotes: enough to tell which, and
 * a category's longest name whole.
 */
internal const val QUOTED = 80

/**
 * [text] as a message, a question or a screen reader's label quotes it: on
 * one line, cut to [QUOTED] characters -- never through the middle of an
 * emoji -- with "…" where it was cut. What is quoted is laid out like any
 * text: a bar at the foot, a dialog and a label each took the whole of a
 * 150,000-letter row or title, and froze the app as the note had before
 * its own guard -- by the very ✕ a person uses to be rid of the row. The
 * note keeps every byte; only the quote is short.
 */
internal fun quoted(text: String): String {
    val cut = text.length > QUOTED
    // The "…" takes the last place; a pair of halves is kept whole or left out.
    val end = when {
        !cut -> text.length
        Character.isLowSurrogate(text[QUOTED - 1]) -> QUOTED - 2
        else -> QUOTED - 1
    }
    val head = text.substring(0, end).replace('\n', ' ').replace('\r', ' ')
    return if (cut) "$head…" else head
}

/** The longest message the bar at the foot, or a question, is given whole. */
internal const val MAX_SAID = 1_000

/**
 * [message] as the bar at the foot or a question draws it: whole, unless it
 * is longer than [MAX_SAID], then cut there with "…". A last guard behind
 * [quoted]: a reason can still carry what came from outside -- a backup's
 * entry name, a reader's error -- and the bar lays out what it is given.
 */
internal fun forTheScreen(message: String): String {
    if (message.length <= MAX_SAID) return message
    val end = if (Character.isLowSurrogate(message[MAX_SAID - 1])) MAX_SAID - 2 else MAX_SAID - 1
    return message.substring(0, end) + "…"
}

/**
 * A file's name as [quoted] quotes it, its type kept at the end -- "A very
 * long name….pdf" -- in the row that opens it and in the question that
 * removes it: the type says what will open, and the desktop's row gives way
 * before it the same way (7aa5a85). A type too long to be one is cut with
 * the rest.
 */
internal fun quotedFile(name: String): String {
    if (name.length <= QUOTED) return quoted(name)
    val kind = Normalize.fileTypeOf(name)
    val at = if (kind.length in 2..16) name.lastIndexOf(kind, ignoreCase = true) else -1
    if (at <= 0 || name.length - at > 20) return quoted(name)
    var keep = QUOTED - 1 - (name.length - at)
    if (Character.isHighSurrogate(name[keep - 1])) keep -= 1
    return (name.substring(0, keep) + "…" + name.substring(at)).replace('\n', ' ').replace('\r', ' ')
}

/**
 * `_reads_as_plumbing`: whether a PERSON would see this line as part of a
 * table -- deliberately looser than [isTableRow]. That one decides what
 * the editor treats as a row, so it is strict: both ends a pipe. A row
 * whose last cell was typed past (`| a | b |and then`), or one never
 * closed (`| a | b`), is therefore not a row, and a preview that filtered
 * on it showed those lines -- "only vertical lines", reported on the
 * desktop twice and on this phone once. Loose, but not past the way a row
 * is written: every cell opens with a pipe and a space, so `|x| = 5`
 * stays words.
 */
internal fun readsAsPlumbing(line: String): Boolean {
    val s = PyText.strip(line)
    if (isTableRow(s)) return true
    return s.startsWith("|") && s.count { it == '|' } >= 2 && s.length > 1 && PyText.isSpace(s.codePointAt(1))
}

/**
 * `preview_text`: a note's words without its tables, and how many tables
 * it had -- both from one pass, because "this note has 2 tables" and the
 * text beside it must be answering about the same lines. A run of table
 * lines is one table; the blank lines a table leaves are collapsed, so
 * the words do not float in a gap.
 */
fun previewText(body: String): Pair<String, Int> {
    val kept = ArrayList<String>()
    var tables = 0
    var inside = false
    for (line in body.split("\n")) {
        if (readsAsPlumbing(line)) {
            if (!inside) {
                tables++
                inside = true
            }
            continue
        }
        inside = false
        kept.add(line)
    }
    val prose = ArrayList<String>()
    for (line in kept) {
        val blank = PyText.strip(line).isEmpty()
        if (blank && (prose.isEmpty() || PyText.strip(prose.last()).isEmpty())) continue
        prose.add(line)
    }
    while (prose.isNotEmpty() && PyText.strip(prose.last()).isEmpty()) prose.removeAt(prose.lastIndex)
    return prose.joinToString("\n") to tables
}

// A table's marks, written once for the editor, the paste, the export and find.

/** A cell holding exactly one of these is a merge marker, not content. */
internal const val MERGE_LEFT = "<"
internal const val MERGE_UP = "^"

/** The separator's cell for a hidden column. */
internal const val SEPARATOR_HIDDEN = "~"

/** Inside a cell, this stands for the line break the table draws. */
internal const val LINE_BREAK = "<br>"

/**
 * None when the cell is not a checkbox; false unchecked, true checked.
 * A capital X is tolerated, which hand-written markdown uses.
 */
fun checkboxState(value: String): Boolean? {
    val v = PyText.strip(value)
    if (v == "[ ]") return false
    if (v.lowercase() == "[x]") return true
    return null
}

/**
 * A markdown-style separator row: every cell is dashes, with optional
 * alignment colons, or the hidden-column marker.
 */
fun isSeparatorRow(line: String): Boolean {
    if (!isTableRow(line)) return false
    val cells = splitRow(line)
    if (cells.isEmpty()) return false
    for (cell in cells) {
        if (cell == SEPARATOR_HIDDEN) continue
        var body = cell
        if (body.startsWith(":")) body = body.substring(1)
        if (body.endsWith(":")) body = body.dropLast(1)
        if (body.isEmpty() || body.trim('-').isNotEmpty()) return false
    }
    return true
}

/**
 * The text with a table's mechanics stripped out: separator rows vanish,
 * pipes and merge markers vanish, checkbox cells vanish (a checkbox is a
 * control, not a word), and the break marker becomes the gap it draws.
 * What remains is what the user actually wrote, which is the right thing
 * to search through. Prose lines pass straight through.
 *
 * Note that this is NOT the rule the card preview uses -- that drops a
 * table's lines entirely, because pipes read as soup at card size.
 * Searching wants the words inside the cells; the card does not. Two
 * purposes, two rules, both of them the desktop's.
 */
fun contentText(text: String): String {
    val out = ArrayList<String>()
    for (raw in text.split("\n")) {
        if (!isTableRow(raw)) {
            out.add(raw)
            continue
        }
        if (isSeparatorRow(raw)) continue
        val cells = splitRow(raw)
            .filter { it != MERGE_LEFT && it != MERGE_UP && checkboxState(it) == null }
            .map { it.replace(LINE_BREAK, " ") }
            .filter { it.isNotEmpty() }
        out.add(cells.joinToString(" "))
    }
    return out.joinToString("\n")
}

/** `"| Milk |   2 |"` gives `["Milk", "2"]`, as `split_row` does. */
internal fun splitRow(line: String): List<String> {
    val s = PyText.strip(line)
    if (!s.startsWith("|")) return emptyList()
    val parts = s.split("|")
    if (parts.size < 2) return emptyList()
    // First and last are what sits outside the outer pipes: nothing.
    return parts.subList(1, parts.size - 1).map { PyText.strip(it) }
}

/**
 * Reads the desktop app's files. Two of them: the notes, and the category
 * colours that the list's left stripe is drawn in.
 */
object Notebook {

    // The reasons a write refuses, worded once. Each is shown to a person,
    // so each says what happened and what it means for their notes.
    private const val NOWHERE = "There is nowhere to keep the notes."
    private const val UNREADABLE =
        "Your notes file could not be read, so nothing will be written over it."
    private const val GONE = "This note is no longer in the notes file."
    private const val BAD_DATE = "A date is written like 2026-10-17."
    private const val ROW_GONE = "That row is no longer there."

    /**
     * What a read produced, including why it produced nothing.
     *
     * `raw` and `file` exist so a write never has to rebuild the notebook
     * from [Note]. That model reads only what the screen draws — it drops
     * the contents of `attachments`, `exceptions` and `overrides`, and
     * every field a future desktop version adds. Serialising it back would
     * silently delete all of that from every note in the file. A write
     * appends to the array that was READ and leaves the rest untouched.
     */
    data class Load(
        val notes: List<Note>,
        val categories: Map<String, String>,
        val source: String,
        val problem: String? = null,
        val file: File? = null,
        val raw: JSONArray? = null,
        val trash: List<Note> = emptyList(),
        val trashFile: File? = null,
        val trashRaw: JSONArray? = null,
        val retentionDays: Int = Settings.TRASH_RETENTION_DAYS,
        /** The settings this notebook was read with; null only in tests. */
        val settings: Settings.Load? = null,
        /** How many notes this read made of days a series' copy set aside ([settleAside]). */
        val setAside: Int = 0,
        /**
         * The notes kept aside of each note's words this session, by the
         * note's uid ([keepTextAside]): the desktop's `_kept_asides`, which
         * its next start no longer knows either.
         */
        val keptAside: MutableMap<String, MutableList<KeptAside>> = HashMap(),
    )

    /** A note [keepTextAside] made: its [uid], the [words] that spend it, and how it [look]ed made. */
    class KeptAside(val uid: String, val words: Map<String, String>, val look: String)

    /**
     * How many days this entry has left before the purge at the app's
     * start removes it ([Organizing.purgeExpired]).
     *
     * An unreadable stamp counts as "keep it", never as "it is due" —
     * the purge keeps it too. Null means no answer.
     */
    fun daysLeftInTrash(deletedAt: String, retentionDays: Int): Int? {
        val when_ = Organizing.deletedAt(deletedAt) ?: return null
        val elapsed = (Date().time - when_.time) / (1000L * 60 * 60 * 24)
        return maxOf(0, retentionDays - elapsed.toInt())
    }

    /**
     * Reads the notebook kept in [dir]: the notes, the Trash and the
     * settings that go with them.
     *
     * A file that is missing is an empty notebook, ready for its first
     * note. A file that exists but cannot be read is the desktop's case
     * exactly, and gets the desktop's answer: its bytes are copied aside
     * (`notes_data_corrupt_<stamp>.json`, never deleted), the app starts
     * with an empty list, and [Load.problem] says what happened and where
     * the copy is. Only when even the copy fails is the file left as the
     * one thing nothing may write over -- `raw` stays null, and every
     * write refuses.
     */
    fun load(dir: File): Load {
        val settings = Settings.load(dir)
        val problems = ArrayList<String>()
        settings.problem?.let { problems.add(it) }

        val file = File(dir, "notes_data.json")
        val raw = readOrRescue(file, "Your notes file", problems)
        // A turned-off series' days kept only in its copy, as this phone up
        // to 0.12.0 kept them: back in the note, once, as the desktop's load
        // brings them back; a moved day home, as its read sends it
        // ([settleAsRead]). The next write keeps them.
        raw?.let { notes -> for (i in 0 until notes.length()) (notes.opt(i) as? JSONObject)?.let { settleAsRead(it) } }
        // ...and what a day holding words could not take, notes of their own
        // now, as the desktop's start makes them; the start writes them at
        // once (Startup), or each start made them again under new identities.
        val setAside = raw?.let { settleAside(it) } ?: 0
        val trashFile = File(dir, "trash.json")
        val trashRaw = readOrRescue(trashFile, "The Trash", problems)

        return Load(
            notes = raw?.let { notesOf(it) } ?: emptyList(),
            categories = Settings.categories(settings.raw, raw),
            source = file.path,
            problem = problems.joinToString("\n").ifEmpty { null },
            file = file,
            raw = raw,
            trash = trashRaw?.let { notesOf(it) } ?: emptyList(),
            trashFile = trashFile,
            trashRaw = trashRaw,
            retentionDays = Settings.retentionDays(settings.raw),
            settings = settings,
            setAside = setAside,
        )
    }

    /**
     * The same notebook, read again from the arrays in memory rather than
     * from the disk. After a write succeeded those arrays ARE what the files
     * hold, so parsing them is all a refresh needs; reading half a megabyte
     * back off the flash after every autosave would cost a weak phone for
     * nothing.
     */
    fun refreshed(load: Load): Load = load.copy(
        notes = load.raw?.let { notesOf(it) } ?: emptyList(),
        trash = load.trashRaw?.let { notesOf(it) } ?: emptyList(),
        categories = load.settings?.let { Settings.categories(it.raw, load.raw) } ?: load.categories,
        retentionDays = load.settings?.let { Settings.retentionDays(it.raw) } ?: load.retentionDays,
    )

    /**
     * The first of [dirs] that holds a notebook, else the first of them --
     * where a new notebook would go.
     */
    fun load(dirs: List<File>): Load {
        val dir = dirs.firstOrNull { File(it, "notes_data.json").isFile } ?: dirs.firstOrNull()
            ?: return Load(emptyList(), Themes.DEFAULT_CATEGORY_COLORS, "?",
                           "There is nowhere to keep a notebook.")
        return load(dir)
    }

    /**
     * The array in [file]: empty when the file does not exist yet, null
     * only when it exists, cannot be read AND could not be copied aside.
     */
    private fun readOrRescue(file: File, what: String, problems: MutableList<String>): JSONArray? {
        if (!file.isFile) return JSONArray()
        val parsed = try {
            Files.parseOwn(file) { JSONArray(it) }
        } catch (e: Exception) {
            null
        }
        if (parsed != null) return parsed
        val kept = Files.preserveCorrupt(file)
        if (kept == null) {
            problems.add("$what could not be read, and could not be copied aside either, " +
                "so nothing will be written over it.")
            return null
        }
        problems.add("$what could not be read, so the app started without it. Nothing " +
            "was deleted: the file was kept as ${kept.name}, and a backup saved at " +
            "Settings → Backup carries it, in its folder \"${Backup.UNREADABLE}\".")
        return JSONArray()
    }

    private fun notesOf(array: JSONArray): List<Note> {
        val out = ArrayList<Note>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(readNote(obj))
        }
        return out
    }

    /**
     * Appends one new note to the notebook on disk.
     *
     * Three guards, each of them a lesson the desktop paid for:
     *
     * 1. **The write appends to the array that was READ.** Rebuilding the
     *    file from [Note] would drop every field this app does not model —
     *    attachment lists, per-day overrides, anything a later desktop
     *    version adds — from all the OTHER notes, invisibly.
     * 2. **A file that exists but did not parse is never written over.**
     *    Overwriting it turns "cannot read your notebook right now" into
     *    "your notebook is gone".
     * 3. **A failure is returned, never swallowed.** The desktop's own
     *    comment: a save that looked fine while nothing reached the disk
     *    is exactly how notes "vanished" on other machines.
     *
     * The note itself is built with the complete default shape, so what
     * lands on disk is what `normalize_note` would have produced.
     */
    fun createNote(
        load: Load,
        title: String,
        body: String,
        category: String = "General",
        color: String = "",
        date: String = "",
        pinned: Boolean = false,
        formatting: List<FormatSpan> = emptyList(),
        paragraphs: JSONArray? = null,
        comments: String = "",
        // Items already of the note's shape -- {"text", "done"} -- as a
        // template's checklist is once normalized.
        checklist: JSONArray? = null,
    ): SaveResult {
        if (date.isNotBlank() && !isDate(date)) {
            return SaveResult.Failed(BAD_DATE)
        }
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        if (file.exists() && load.raw == null) return SaveResult.Failed(UNREADABLE)
        val array = load.raw ?: JSONArray()

        // Read the ids from the RAW array: a note the model skipped still
        // owns its id, and reusing it would merge two notes.
        val newId = Organizing.ids(array).next()

        val now = Files.now()
        val note = JSONObject()
        note.put("id", newId)
        // Born here, so it is minted here. This is the only place this
        // app creates an identity: notes that arrived from elsewhere keep
        // whatever they came with, including nothing.
        note.put("uid", java.util.UUID.randomUUID().toString())
        note.put("title", mendSurrogates(title))
        note.put("body", mendSurrogates(body))
        note.put("formatting", spansToJson(formatting))
        note.put("comments", mendSurrogates(comments))
        note.put("category", category.trim().ifEmpty { "General" })
        note.put("date", date.trim())
        note.put("color", sanitizeColor(color))
        note.put("attachments", JSONArray())
        note.put("updated", now)
        note.put("created", now)
        note.put("recurrence", defaultRecurrence())
        note.put("exceptions", JSONArray())
        note.put("overrides", JSONObject())
        note.put("moved", JSONObject())
        note.put("pinned", pinned)
        note.put("checklist", checklist ?: JSONArray())
        note.put("paragraphs", paragraphs ?: JSONArray())

        array.put(note)
        val problem = writeAtomic(file, array)
        if (problem != null) {
            // Undo the append so the list on screen keeps matching the disk.
            array.remove(array.length() - 1)
            return SaveResult.Failed(problem)
        }
        return SaveResult.Ok(newId)
    }

    /**
     * Rewrites one existing note's title and body, in place, in the array
     * that was read — every other field of that note, and every other note
     * in the file, is left exactly as it was found.
     *
     * Two things happen that a naive "set the text and save" would miss:
     * the formatting and the bullet markers are moved to follow the edit
     * (see [remapSpans]), and the previous version is kept inside the note
     * when the edit takes a lot of text away.
     *
     * That last guard is the desktop's, thresholds included
     * ([shouldKeepPrevious]): emptied completely, a big part of the text
     * gone, or most of a short note -- counted by what is gone, not by how
     * much shorter the text got. A 17-character shopping list cleared by
     * accident once slipped under thresholds that only looked at size.
     */
    fun updateNote(
        load: Load,
        id: Int,
        title: String,
        body: String,
        // null means "leave this field exactly as it was" -- so a caller
        // that only edits the text cannot wipe a note's colour by omission.
        category: String? = null,
        color: String? = null,
        date: String? = null,
        pinned: Boolean? = null,
        // When an editor was ALSO changing styles, it hands back the spans
        // it has been keeping in step with its own text as the user typed.
        // Remapping those again would move them twice.
        formatting: List<FormatSpan>? = null,
        comments: String? = null,
        // The same for the lines' lists, indents and alignment: an editor
        // that changes them hands back what it kept in step with its text.
        paragraphs: JSONArray? = null,
        // A write of the repeat follows, which settles the schedule: the
        // Repeat sheet saves the start first, then the rule. Asked here with
        // the new start and the old rule, a copy's days were spent and the
        // copy dropped on a state the desktop's one save never has (the
        // final audit's ninth round); the repeat's write spends once, on the
        // final start and rule ([Days.setRepeat]).
        scheduleFollows: Boolean = false,
    ): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)

        val index = Organizing.indexOfId(array, id)
        if (index < 0) return SaveResult.Failed(GONE)
        // Edited as a copy, put in place only once written. Changed in
        // place, a failed write left the new words in memory: the next save
        // found nothing to write and said "Auto-saved", and a restart lost
        // them (ISSUES_LOG, "A save that failed, then said it had saved").
        val note = JSONObject((array.get(index) as JSONObject).toString())

        val oldTitle = (note.opt("title") as? String) ?: ""
        val oldBody = (note.opt("body") as? String) ?: ""
        val newTitle = mendSurrogates(title)
        val newBody = mendSurrogates(body)

        // A date is refused rather than silently dropped: the user typed
        // it, so a wrong one is worth saying out loud.
        if (date != null && date.isNotBlank() && !isDate(date)) {
            return SaveResult.Failed(BAD_DATE)
        }
        // The coupling that is easy to miss: `normalize_note` THROWS AWAY a
        // recurrence rule whose note has no valid date. Clearing the date
        // of a repeating note here would look harmless and would delete
        // the repeat on the desktop's next read.
        val repeats = ((note.opt("recurrence") as? JSONObject)?.opt("freq") as? String)
            ?.isNotEmpty() == true
        if (repeats && date != null && date.isBlank()) {
            return SaveResult.Failed(
                "This note repeats; clearing its date would also delete the repeat.")
        }

        val spans = readSpans(note.opt("formatting") as? JSONArray)
        val pinChanged = pinned != null && pinned != (note.opt("pinned") == true)
        val otherChanged = (formatting != null && formatting != spans) ||
            (category != null && category != note.opt("category")) ||
            (color != null && sanitizeColor(color) != (note.opt("color") ?: "")) ||
            (date != null && date.trim() != (note.opt("date") ?: "")) ||
            (comments != null && mendSurrogates(comments) != (note.opt("comments") ?: "")) ||
            (paragraphs != null && Exporting.normalizeParagraphs(paragraphs, null) !=
                Exporting.normalizeParagraphs(note.opt("paragraphs"), null))
        val wordsChanged = oldTitle != newTitle || oldBody != newBody
        if (!wordsChanged && !otherChanged && !pinChanged) {
            return SaveResult.Ok(id)          // nothing to write
        }

        val moved = if (formatting != null) {
            Remapped(formatting, 0)
        } else {
            remapSpans(oldBody, newBody, spans)
        }

        // Keep the whole state of the old version, so a rescue restores a
        // note rather than its text with someone else's styling -- when its
        // words go, or its comments do.
        val oldComments = (note.opt("comments") as? String) ?: ""
        val newComments = comments?.let { mendSurrogates(it) } ?: oldComments
        var kept: Pair<JSONObject, Map<String, String>>? = null
        if (shouldKeepPrevious(oldTitle, oldBody, newTitle, newBody, oldComments, newComments)) {
            // Due for the comments alone, over a copy the note does not hold
            // again: that copy keeps its words and takes only the comments
            // this save replaces, as the desktop's save does. Replaced whole,
            // a big delete of the body before it was in no file once the
            // comments were rewritten (the final audit's seventh round).
            val waiting = note.opt("_prev") as? JSONObject
            val commentsAlone = holdsUnspentCopy(note) && !shouldKeepPrevious(oldTitle, oldBody, newTitle, newBody)
            // An import's Update copy is the note as it was here before the
            // Update, in no other file: it stays, and the words this save
            // takes become a note of their own (the desktop's wave 2).
            if (waiting != null && updateCopy(waiting) && !spent(note, waiting)) {
                kept = keepTextAside(note, commentsAlone)
            } else {
                val previous = if (commentsAlone) JSONObject(waiting!!.toString()) else JSONObject().apply {
                    put("title", oldTitle)
                    put("body", oldBody)
                    put("formatting", note.opt("formatting") ?: JSONArray())
                    put("paragraphs", note.opt("paragraphs") ?: JSONArray())
                }
                // The copy's own comments stay when theirs is the larger loss:
                // two letters cleared after a long comment was deleted wrote over
                // it (the final audit's eighth round, the desktop's 947e9d3).
                previous.put("comments", if (commentsAlone) commentsKeptByCopy(waiting!!.opt("comments") as? String ?: "", oldComments)
                                         else note.opt("comments") ?: "")
                // Words alone, as the desktop's: a turned-off series' days live
                // in the note ([daysHoldOwnText]), never only in a copy the next
                // one replaces -- another series' copy, replaced here, sets its
                // days with words aside first ([copyDaysSetAside]).
                if (!commentsAlone) copyDaysSetAside(note)
                note.put("_prev", previous)
            }
        }
        // No longer repeating, as the desktop's save has it: days that hold
        // no text of their own -- a skipped day, a colour -- are clutter
        // nothing shows, and go; a day's own text stays, inert until a repeat
        // brings it back.
        if ((wordsChanged || otherChanged) && !Recurrence.isRecurring(note) && !daysHoldOwnText(note)) clearDays(note)

        note.put("title", newTitle)
        note.put("body", newBody)
        note.put("formatting", spansToJson(moved.spans))
        note.put("paragraphs", paragraphs ?: movedParagraphs(note, oldBody, newBody))
        if (category != null) {
            note.put("category", category.trim().ifEmpty { "General" })
        }
        if (color != null) note.put("color", sanitizeColor(color))
        if (date != null) note.put("date", date.trim())
        if (pinned != null) note.put("pinned", pinned)
        if (comments != null) note.put("comments", mendSurrogates(comments))
        // A pin alone is not an edit: the desktop's `toggle_pin` leaves the
        // time alone, and so does a pin from the note's details here.
        if (wordsChanged || otherChanged) {
            note.put("updated", Files.now())
        }
        // `created` is deliberately untouched: an edit is not a birth.
        // Repeating as the series its copy holds -- its date put back to the
        // series' own -- the note holds the days, and the copy's are spent,
        // as in the desktop's every save of the whole note (a pin alone is no
        // such save there).
        if (!scheduleFollows) {
            if (wordsChanged || otherChanged) {
                Normalize.dropHiddenMoves(note)
                spendSeriesDays(note)
            }
            dropSpentCopy(note)
        }

        val candidate = copyOf(array)
        candidate.put(index, note)
        // The words kept aside, then the days a series' copy set aside: notes
        // of their own in the same write, in the desktop's order.
        val aside = kept?.first?.put("id", Organizing.ids(candidate).next())
        if (aside != null) candidate.put(aside)
        val made = asideNotes(note, Organizing.ids(candidate), candidate)
        for (fresh in made) candidate.put(fresh)
        val uid = note.opt("uid") as? String ?: ""
        val back = takeBackAside(load, note, candidate)
        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        Organizing.replace(array, candidate)
        // Remembered once written: a failed write kept nothing aside.
        load.keptAside[uid]?.removeAll(back.entries.toSet())
        if (aside != null && kept != null) {
            load.keptAside.getOrPut(uid) { ArrayList() }.add(KeptAside(aside.opt("uid") as? String ?: "", kept.second, asideLook(aside)))
        }
        if (load.keptAside[uid]?.isEmpty() == true) load.keptAside.remove(uid)
        return SaveResult.Ok(id, moved.lost, keptAside = aside?.optInt("id") ?: 0,
                             keptComments = kept?.second?.keys == setOf("comments"), spentAside = back.removed)
    }

    /**
     * The words [note] holds before a save that takes them, beside an
     * import's Update copy, as a note of their own: "<title> (earlier
     * text)", its look along, a one-off's date too -- a series' start is no
     * day of the words -- and only the comments when the save takes no more
     * ([commentsOnly]). With it, the words that spend it ([takeBackAside]);
     * null when there is nothing to keep. The desktop's `_keep_text_aside`.
     */
    private fun keepTextAside(note: JSONObject, commentsOnly: Boolean): Pair<JSONObject, Map<String, String>>? {
        fun text(key: String) = note.opt(key) as? String ?: ""
        val title = text("title")
        val body = if (commentsOnly) "" else text("body")
        val comments = text("comments")
        if (pyStrip(title).isEmpty() && pyStrip(body).isEmpty() && pyStrip(comments).isEmpty()) return null
        val stamp = Files.now()
        val made = Normalize.normalizeNote(JSONObject()
            .put("title", pyStrip("$title (earlier text)"))
            .put("body", body)
            .put("formatting", if (commentsOnly) JSONArray() else deepCopy(note.opt("formatting") ?: JSONArray()))
            .put("paragraphs", if (commentsOnly) JSONArray() else deepCopy(note.opt("paragraphs") ?: JSONArray()))
            .put("comments", comments)
            .put("category", text("category").ifEmpty { "General" })
            .put("date", if (Recurrence.isRecurring(note)) "" else text("date"))
            .put("color", text("color"))
            .put("created", stamp)
            .put("updated", stamp))
        val words = if (commentsOnly) mapOf("comments" to comments)
                    else mapOf("title" to title, "body" to body, "comments" to comments)
        return made to words
    }

    /** What [takeBackAside] found: the entries whose words are back, and the notes that went with them. */
    private class TakenBack(val entries: List<KeptAside>, val removed: List<Int>)

    /**
     * The notes [keepTextAside] made of [note]'s words, once [note] holds
     * those words again -- an Undo, a cut pasted back -- each by [wordsLost],
     * and taken off [candidate] if untouched since, as a rescue copy is
     * spent: one stayed in the list for good after every Undo. A note pinned
     * or filed since is the person's, and stays. The desktop's `_take_back_aside`.
     */
    private fun takeBackAside(load: Load, note: JSONObject, candidate: JSONArray): TakenBack {
        val held = load.keptAside[note.opt("uid") as? String ?: ""] ?: return TakenBack(emptyList(), emptyList())
        val back = held.filter { entry ->
            entry.words.all { (key, value) -> wordsLost(value, note.opt(key) as? String ?: "") == 0 }
        }
        val removed = ArrayList<Int>()
        for (entry in back) {
            val at = (0 until candidate.length()).firstOrNull {
                entry.uid.isNotEmpty() && (candidate.opt(it) as? JSONObject)?.opt("uid") == entry.uid
            } ?: continue
            if (asideLook(candidate.getJSONObject(at)) == entry.look) {
                removed.add(candidate.getJSONObject(at).optInt("id"))
                candidate.remove(at)
            }
        }
        return TakenBack(back, removed)
    }

    /** What an '(earlier text)' note is made of, to tell it untouched: its content and its stamp. */
    private fun asideLook(note: JSONObject): String =
        Normalize.contentSignature(note) + "\u0002" + (note.opt("updated") ?: "")

    /** A note kept aside, put back from the Trash ([uids]), is the person's now: no Undo spends it again. */
    internal fun forgetAsides(load: Load, uids: Set<String>) {
        for (held in load.keptAside.values) held.removeAll { it.uid in uids }
        load.keptAside.values.removeAll { it.isEmpty() }
    }

    /**
     * Ticks or unticks one checklist row.
     *
     * The row's own JSON object is edited in place rather than rebuilt, so
     * any field beyond `text` and `done` survives. Only `done` and the
     * note's `updated` stamp change; no rescue copy is kept, because
     * nothing was removed — a tick is not a deletion.
     */
    fun setChecklistDone(load: Load, id: Int, index: Int, done: Boolean): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)
        val note = Organizing.noteById(array, id)
            ?: return SaveResult.Failed(GONE)

        val checklist = note.opt("checklist") as? JSONArray
            ?: return SaveResult.Failed("This note has no checklist.")
        val item = checklist.opt(index) as? JSONObject
            ?: return SaveResult.Failed(ROW_GONE)
        if (item.opt("done") == done) return SaveResult.Ok(id)

        item.put("done", done)
        note.put("updated", Files.now())

        val problem = writeAtomic(file, array)
        if (problem != null) {
            item.put("done", !done)          // put the screen back in step
            return SaveResult.Failed(problem)
        }
        return SaveResult.Ok(id)
    }

    private fun copyOf(array: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until array.length()) out.put(array.get(i))
        return out
    }

    /**
     * The desktop's ceiling. Not a performance budget — an anti-bomb
     * limit: a pasted share code is saved before it is ever drawn, so an
     * unbounded list could make the app fail to open on every launch
     * afterwards. That happened, twice, with real fixes behind it.
     */
    const val MAX_CHECKLIST_ITEMS = 1000

    /** What is said when a list is full: a note's, and a template's in its editor. */
    const val CHECKLIST_FULL = "A checklist holds at most $MAX_CHECKLIST_ITEMS items."

    /** Adds a row to the end of a note's checklist. */
    fun addChecklistItem(load: Load, id: Int, text: String): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (checklist.length() >= MAX_CHECKLIST_ITEMS) {
                return@editChecklist CHECKLIST_FULL
            }
            val item = JSONObject()
            item.put("text", mendSurrogates(text))
            item.put("done", false)
            checklist.put(item)
            null
        }
    }

    fun renameChecklistItem(load: Load, id: Int, index: Int, text: String): SaveResult {
        return editChecklist(load, id) { checklist ->
            val item = checklist.opt(index) as? JSONObject
                ?: return@editChecklist ROW_GONE
            // In place, so a field this app does not model survives a rename.
            item.put("text", mendSurrogates(text))
            null
        }
    }

    /**
     * Moves one row to another place in the list -- the drag handle, the
     * desktop's ▲ ▼. The row object itself moves, so a field this app does
     * not model goes with it, and its tick stays its own.
     */
    fun moveChecklistItem(load: Load, id: Int, from: Int, to: Int): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (from !in 0 until checklist.length() || to !in 0 until checklist.length()) {
                return@editChecklist ROW_GONE
            }
            if (from == to) return@editChecklist null
            val rows = (0 until checklist.length()).map { checklist.get(it) }.toMutableList()
            val row = rows.removeAt(from)
            rows.add(to, row)
            while (checklist.length() > 0) checklist.remove(checklist.length() - 1)
            rows.forEach { checklist.put(it) }
            null
        }
    }

    fun removeChecklistItem(load: Load, id: Int, index: Int): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (index < 0 || index >= checklist.length()) {
                return@editChecklist ROW_GONE
            }
            checklist.remove(index)
            null
        }
    }

    /**
     * A row taken off, put back at [index] -- or at the end, when the list
     * has grown shorter since: the Undo the desktop's toast offers after a
     * row's ✕, "the one thing that could be taken off a note with no way
     * back at all". [row] is the row object as it was, every field kept.
     */
    fun restoreChecklistItem(load: Load, id: Int, index: Int, row: JSONObject): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (checklist.length() >= MAX_CHECKLIST_ITEMS) {
                return@editChecklist CHECKLIST_FULL
            }
            val rows = (0 until checklist.length()).map { checklist.get(it) }.toMutableList()
            rows.add(index.coerceIn(0, rows.size), JSONObject(row.toString()))
            while (checklist.length() > 0) checklist.remove(checklist.length() - 1)
            rows.forEach { checklist.put(it) }
            null
        }
    }

    /**
     * The shared middle of every checklist change: find the note, work on
     * a COPY of the list, write it, and only then let the change reach the
     * array the screen is reading. A failed write leaves the screen and
     * the disk saying the same thing.
     */
    private fun editChecklist(
        load: Load,
        id: Int,
        change: (JSONArray) -> String?,
    ): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)
        val note = Organizing.noteById(array, id)
            ?: return SaveResult.Failed(GONE)

        val current = note.opt("checklist") as? JSONArray ?: JSONArray()
        // A DEEP copy. A shallow one shares each row object with the
        // original, so renaming a row in the "candidate" would rename it
        // in the real note too -- and the rollback below would restore
        // the same, already-changed object.
        val candidate = try {
            JSONArray(current.toString())
        } catch (e: Exception) {
            return SaveResult.Failed("This note's checklist could not be read.")
        }
        val refusal = change(candidate)
        if (refusal != null) return SaveResult.Failed(refusal)

        val before = note.opt("checklist")
        val stamp = note.opt("updated")
        note.put("checklist", candidate)
        note.put("updated", Files.now())

        val problem = writeAtomic(file, array)
        if (problem != null) {
            if (before != null) note.put("checklist", before) else note.remove("checklist")
            if (stamp != null) note.put("updated", stamp)
            return SaveResult.Failed(problem)
        }
        return SaveResult.Ok(id)
    }

    /**
     * What a pasted code turns out to hold, before anything is written.
     *
     * Split out from the import so the screen can ASK before acting: a
     * code carrying notes this device already has is a question, not an
     * instruction, and the answer the user gives cannot be guessed.
     */
    sealed class Inspection {
        data class Failed(val reason: String) : Inspection()
        data class Ready(
            val notes: List<JSONObject>,
            /** How many of them are notes this device already has. */
            val known: Int,
            /** The questions this import raises; see [Importing]. */
            val plan: Importing.Plan,
        ) : Inspection()
    }

    /**
     * What a pasted code holds, sorted by [Importing.plan] -- matched by
     * identity, not resemblance: matching on the text would say "new note"
     * exactly when a note has been edited, which is the only time anyone
     * sends one back.
     */
    fun inspectShareCode(load: Load, code: String): Inspection =
        inspectShareCode(load.raw ?: JSONArray(), load.categories.keys, code)

    /**
     * The same, against [notebook] and [categories] taken from the notebook
     * beforehand: what the code dialog reads on a thread of its own.
     */
    fun inspectShareCode(notebook: JSONArray, categories: Set<String>, code: String): Inspection {
        val decoded = Sharing.decode(code)
        if (decoded is Sharing.Decoded.Failed) return Inspection.Failed(decoded.reason)
        val notes = (decoded as Sharing.Decoded.Notes).notes
        val plan = Importing.plan(notebook, notes, Importing.Source.CODE, categories)
        return Inspection.Ready(notes, plan.differing.size + plan.unchanged.size, plan)
    }

    /**
     * The fields an incoming copy replaces: the desktop's
     * UPDATABLE_FROM_SHARED. Never the id, the attachments (a code carries
     * none) or the per-day maps, a timeline the sender's copy may not share.
     */
    val UPDATABLE_FROM_SHARED = listOf(
        "title", "body", "formatting", "paragraphs",
        "comments", "category", "date", "color", "recurrence", "checklist",
    )

    /**
     * A backup is the person's OWN notebook, so its copy also owns its
     * timeline and its pin: the desktop's UPDATABLE_FROM_BACKUP.
     */
    val UPDATABLE_FROM_BACKUP = UPDATABLE_FROM_SHARED + listOf(
        "exceptions", "overrides", "moved", "pinned",
    )

    /** The text half of a rescue copy, which every `_prev` has. */
    val PREV_TEXT_FIELDS = listOf("title", "body", "formatting", "paragraphs", "comments")

    /** A series' own days, which hang on its repeat: the desktop's DAY_FIELDS. */
    val DAY_FIELDS = listOf("exceptions", "overrides", "moved")

    /**
     * The days of [before] that writing [recurrence] over it puts out of
     * sight -- none unless it repeats now and will not after -- copied:
     * the desktop's `days_to_rescue`. Whatever ends a repeat COPIES these
     * into its rescue copy, beside the repeat they hang on, and the note
     * keeps its own: a share code that turned a series' repeat off left
     * no copy of its days, and a Save in the Repeat sheet then cleared
     * them; and moved out of the note, as the desktop first did, the next
     * copy made over the one slot erased them.
     */
    internal fun daysToRescue(before: JSONObject, recurrence: Any?): Map<String, Any> {
        if (!Recurrence.isRecurring(before) || Recurrence.isRecurring(JSONObject().put("recurrence", recurrence))) {
            return emptyMap()
        }
        return DAY_FIELDS.associateWith { shaped(before, it) }.filterValues { daysIn(it) > 0 }
            .mapValues { deepCopy(it.value) }
    }

    private fun daysIn(value: Any?): Int = (value as? JSONObject)?.length() ?: (value as? JSONArray)?.length() ?: 0

    /**
     * Whether the days a turned-off [note] kept belong to it turned back on
     * with [date] and [recurrence]: only when both are as the series had
     * them, which its rescue copy holds -- the desktop's `kept_days_fit`.
     * True when it kept none, or nothing says what the series was: kept,
     * they lose nothing. Turned back on from another date, an old deleted
     * day hid the new series' own first day (the final audit's fifth
     * round); the owner's answer: a deleted day holds nothing and goes, the
     * rescue copy keeping them all, and days with their own words stay.
     */
    internal fun keptDaysFit(note: JSONObject, date: Any?, recurrence: Any?): Boolean {
        if (Recurrence.isRecurring(note) || !Recurrence.isRecurring(JSONObject().put("recurrence", recurrence))) return true
        val kept = DAY_FIELDS.filter { daysIn(note.opt(it)) > 0 }
        val previous = note.opt("_prev") as? JSONObject
        if (kept.isEmpty() || previous == null || !Recurrence.isRecurring(previous) ||
            kept.any { !sameJson(previous.opt(it), note.opt(it)) }) return true
        return sameSeries(previous.opt("date"), previous.opt("recurrence"), date, recurrence)
    }

    /**
     * Whether two starts and rules are one series: a weekly rule's start
     * weekday lit, or left to the start, is the same rule -- as a monthly
     * rule's day. Both are stored, and a series turned back on as it was read
     * as another (the desktop's `_same_series`, the seventeenth round).
     */
    internal fun sameSeries(day: Any?, rule: Any?, otherDay: Any?, otherRule: Any?): Boolean {
        if (day != otherDay) return false
        val whenDay = Recurrence.parseDate(day)
        val rules = listOf(Recurrence.normalize(rule), Recurrence.normalize(otherRule))
        if (whenDay != null) for (rec in rules) {
            if (rec.optString("freq") == "weekly" && rec.getJSONArray("weekdays").length() == 0) {
                rec.put("weekdays", JSONArray().put(whenDay.dayOfWeek.value - 1))
            }
            if (rec.optString("freq") == "monthly" && rec.getJSONArray("month_days").length() == 0) {
                rec.put("month_days", JSONArray().put(whenDay.dayOfMonth))
            }
        }
        // A field the frequency never reads changes no day: kept hidden (the
        // Repeat sheet stores all three), it made a series turned back on as
        // it was read as another (the audit's round 19 and the lows wave; the
        // desktop's _same_series the same).
        for (rec in rules) {
            val freq = rec.optString("freq")
            if (freq != "weekly") rec.put("weekdays", JSONArray())
            if (freq != "monthly") rec.put("month_days", JSONArray())
            val monthDays = rec.getJSONArray("month_days")
            // "Skip that month" is read only where a month can lack the day.
            val short = (freq == "monthly" && (0 until monthDays.length()).any { monthDays.optInt(it) > 28 }) ||
                (freq == "yearly" && whenDay != null && whenDay.monthValue == 2 && whenDay.dayOfMonth == 29)
            if (!short) rec.put("month_day_policy", "clamp")
        }
        return sameJson(rules[0], rules[1])
    }

    /**
     * Whether a day of [note] holds a title, text or comments of its own --
     * asked of a note that may no longer repeat, so of what each day holds
     * ([Recurrence.dayAsKept]), not of what it shows: the desktop's
     * `days_hold_own_text`, the one answer its turn-off, its saves and the
     * read of an older notebook ([daysBackFromCopy]) all ask. Each value read
     * as Python's `value or ""` reads it: a day's title written as a number
     * by hand is a title of its own there, and read as text it was none.
     */
    internal fun daysHoldOwnText(note: JSONObject): Boolean {
        val overrides = note.opt("overrides") as? JSONObject ?: return false
        return overrides.keys().asSequence().any { dayHasWords(note, it) }
    }

    /** A day's words, which go with a repeat turned off; the rest of a day's edit, its look, says nothing typed: the desktop's `DAY_WORDS`. */
    private val DAY_WORDS = listOf("title", "body", "comments")

    /** A day's look, its colour and category: what a day's edit holds besides its words -- the desktop's `_DAY_LOOK`. */
    private val DAY_LOOK = Recurrence.OVERRIDABLE_FIELDS.filter { it !in DAY_WORDS }

    /**
     * Whether [note]'s edit of [day] holds a title, text or comments of its
     * own -- what the day holds ([Recurrence.dayAsKept]), not what it shows:
     * the desktop's `day_has_words`, each word compared as [sameWord].
     */
    internal fun dayHasWords(note: JSONObject, day: String): Boolean {
        val shown = Recurrence.dayAsKept(note, day)
        return DAY_WORDS.any { !sameWord(it, shown.opt(it), note.opt(it)) }
    }

    /**
     * Whether two of a day's words say the same -- the desktop's
     * `_same_word`, each read as Python's `str(value or "")`: a title without
     * the spaces around it, as [Recurrence.dayMatchesSeries] reads one; a text
     * with the line under a closing table on both sides or neither
     * ([Normalize.withCaretLine]) -- a copy this phone made keeps its series'
     * text as typed and its days with the line, and a day only coloured read
     * as words (the final audit's fifteenth round).
     */
    private fun sameWord(word: String, a: Any?, b: Any?): Boolean {
        val x = pyText(a)
        val y = pyText(b)
        return when (word) {
            "title" -> pyStrip(x) == pyStrip(y)
            "body" -> Normalize.sameBody(x, y)
            else -> x == y
        }
    }

    /** Python's `str(value or "")`, for the values a note's words hold. */
    private fun pyText(value: Any?): String = when {
        !Exporting.truthy(value) -> ""
        value == true -> "True"
        else -> value.toString()
    }

    /** Python's `strip()`: U+0085 and the non-breaking spaces count as space there, not for Kotlin's isWhitespace. */
    internal fun pyStrip(text: String): String = text.trim { it.isWhitespace() || it.code in PY_SPACES }

    private val PY_SPACES = setOf(0x85, 0xA0, 0x2007, 0x202F)

    /** A note's days taken out: what a repeat's end leaves when none of them holds text of its own. */
    internal fun clearDays(note: JSONObject) {
        note.put("exceptions", JSONArray())
        note.put("overrides", JSONObject())
        note.put("moved", JSONObject())
    }

    /**
     * A turned-off series' days, brought back into the note from the copy
     * that alone held them -- the desktop's `_days_back_from_copy`, asked as
     * a note is read. The owner's choice: those days live in one place, the
     * note, as the desktop keeps them. This phone, up to 0.12.0, moved them
     * out of the note into the series' rescue copy at a turn-off, and the
     * desktop's next copy of words erased that copy: a day's own text was in
     * no file (the final audit's sixth round). Only from the series' own
     * copy ([seriesOwnCopy]) into a note that keeps no days of its own, when
     * a day there holds text of its own; the copy keeps them too. Into a
     * note that no longer repeats -- and into one that repeats again as that
     * same series, its start and rule the copy's: 0.12.0 turned back on as it
     * was gave the note none of its days back, and the next copy of words
     * erased them (the seventh round). Never from an update's copy: it holds
     * the days that update replaced, on purpose, and taken in from a backup's
     * update of a series they made the same backup read as changed, its
     * Update spending the only earlier version (the desktop's seventh round).
     * The copy keeps them while the note does not repeat; once it repeats as
     * that series, they are spent from the copy ([spendSeriesDays]).
     */
    internal fun daysBackFromCopy(note: JSONObject) {
        val previous = note.opt("_prev") as? JSONObject ?: return
        if (daysBackWanted(note, previous)) for (field in DAY_FIELDS) note.put(field, deepCopy(shaped(previous, field)))
        spendSeriesDays(note)
    }

    /**
     * A series' own copy holds the series' days only while the note does not
     * repeat as that series. Once it does again, the copy's days come back to
     * the note on every date the note's own days leave free -- kept days come
     * back onto the same date and rule -- and only then are the copy's spent,
     * taken out of it. Kept there, a day the person then reset came back from
     * the copy at every start, in every duplicate and every Trash restore,
     * and the same backup read as changed (the final audit's eighth round);
     * spent alone, days only the copy held were in no file: 0.12.0's series
     * turned back on as it was, then any other day touched, lost its first
     * day's own text at the first start, and a series turned on from another
     * start, then put back, its deleted day (the ninth). The note's own day
     * keeps its date, and a copy's move never goes onto a date the note
     * holds. Since the tenth round a copy's day is dropped only where the
     * note deleted that day or wrote words of its own for it ([takeDaysBack]).
     * The desktop's `spend_series_days` (8b5d6cc, 04044ce): at every read,
     * after the move ([daysBackFromCopy], [daysBackAsRead]), and in the save
     * that turns the series back on ([updateNote], [Days.setRepeat]). A note
     * that does not repeat keeps its copy's days, as before.
     */
    internal fun spendSeriesDays(note: JSONObject) {
        val previous = note.opt("_prev") as? JSONObject ?: return
        if (!seriesDaysSpent(note, previous)) return
        val aside = takeDaysBack(note, previous)
        for (field in DAY_FIELDS) previous.remove(field)
        addAside(note, aside)
    }

    /**
     * The dates [note]'s own days speak for -- a day edited, deleted or
     * moved, and the dates days are moved to: the desktop's `_dates_held`.
     */
    private fun datesHeld(note: JSONObject): Set<String> {
        val held = HashSet<String>()
        (note.opt("overrides") as? JSONObject)?.keys()?.forEach { held.add(it) }
        (note.opt("exceptions") as? JSONArray)?.let { days ->
            for (i in 0 until days.length()) (days.opt(i) as? String)?.let { held.add(it) }
        }
        (note.opt("moved") as? JSONObject)?.let { moved ->
            for (day in moved.keys()) {
                held.add(day)
                (moved.opt(day) as? String)?.let { held.add(it) }
            }
        }
        return held
    }

    /**
     * [previous]'s days put into [note] -- the desktop's `spend_series_days`
     * since its 04044ce: a copy's day stays out of the note only where the
     * note deleted that day or wrote words of its own for it, and its words
     * are set aside there ([copyDaysPlaced]). The copy's deleted days and
     * its moves still ask [datesHeld] (asked before any is put in): a deleted
     * day on every date the note's own leave free, a move as
     * [copyMovesTaken] takes it. Its days' edits no longer do: each goes to
     * the date the note draws its day on ([copyDaysPlaced]). Asked by the
     * date, round nine spent a day's own words wherever the note had only
     * moved that day or given it a colour: 0.12.0's notebook, or the
     * desktop's 0.97.405, lost them at the first start, in no file (the
     * final audit's tenth round). The note's deleted days written sorted,
     * once each. Returns the copy's days set aside ([copyDaysPlaced]).
     */
    private fun takeDaysBack(note: JSONObject, previous: JSONObject): JSONArray {
        val held = datesHeld(note)
        val moved = (note.opt("moved") as? JSONObject)?.let { JSONObject(it.toString()) } ?: JSONObject()
        for ((day, to) in copyMovesTaken(previous.opt("moved") as? JSONObject, held, moved.length())) {
            moved.put(day, deepCopy(to))
        }
        val (overrides, aside) = copyDaysPlaced(note, previous, moved)
        val exceptions = sortedSetOf<String>()
        exceptions.addAll(datesIn(note.opt("exceptions")))
        exceptions.addAll(datesIn(previous.opt("exceptions")).filter { it !in held })
        note.put("overrides", overrides)
        note.put("exceptions", JSONArray(exceptions.toList()))
        note.put("moved", moved)
        return aside
    }

    /** The texts of a list of dates, anything else in it left out. */
    private fun datesIn(list: Any?): List<String> =
        (list as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } } ?: emptyList()

    /**
     * The copy's moves [theirs] the note takes -- the desktop's
     * `_copy_moves_taken`: of a day its own days leave alone, onto a date
     * they leave free ([held]), in the order of the day's date, while it
     * holds fewer than a series' most ([room] its own) -- and none onto a
     * day whose own move stays behind, nor, in turn, onto one of those: that
     * day stays on its date, and two days on one date were drawn as one.
     * One worklist, each day sent home once.
     */
    private fun copyMovesTaken(theirs: JSONObject?, held: Set<String>, room: Int): Map<String, Any> {
        theirs ?: return emptyMap()
        val taken = LinkedHashMap<String, Any>()
        for (day in theirs.keys().asSequence().sorted()) {
            val to = theirs.get(day)
            // Never onto a date the note holds: a load keeps one day a date,
            // and the first in order -- the copy's -- would push the note's out.
            if (day !in held && !(to is String && to in held) && room + taken.size < Recurrence.MAX_OCCURRENCES) taken[day] = to
        }
        val onto = HashMap<Any, String>()
        for ((day, to) in taken) onto[to] = day
        val home = ArrayDeque(theirs.keys().asSequence().filter { it !in taken }.toList())
        while (home.isNotEmpty()) {
            val day = onto.remove(home.removeLast()) ?: continue
            taken.remove(day)
            home.addLast(day)
        }
        return taken
    }

    /**
     * [note]'s days' edits with [previous]'s put in, [moved] the note's
     * moves once the copy's are taken -- the desktop's `_copy_days_placed`:
     * each of the copy's on the date the note draws its day, as a move keys
     * a day's edit, not where the note deleted the day or wrote words of its
     * own for it. An edit there with no words of its own takes the copy's,
     * its colour and category kept. An edit the copy draws on no day -- off
     * its schedule, or under a date its day left or it deleted -- stays on
     * its date. Two of the copy's for one date: the one with words goes in,
     * then the one on its own date, then the one moved there, then the one
     * drawn on no day. Of a day already holding words -- the note's, or
     * another of the copy's -- the copy's words it lacks are set aside
     * ([asideEntry]), to be a note of their own: dropped, a day coloured
     * before the series' text changed cost them (the eleventh round); kept in
     * the copy, its one slot lost them (the twelfth); added to the day, four
     * ways of telling its lines apart each left a case (the thirteenth to
     * fifteenth, the owner's choice). Returns the edits, and those set aside.
     */
    private fun copyDaysPlaced(note: JSONObject, previous: JSONObject, moved: JSONObject): Pair<JSONObject, JSONArray> {
        val mine = note.opt("overrides") as? JSONObject ?: JSONObject()
        val overrides = JSONObject(mine.toString())
        val deleted = datesIn(note.opt("exceptions")).toHashSet()
        val came = HashMap<Any, String>()
        (previous.opt("moved") as? JSONObject)?.let { theirs -> for (day in theirs.keys()) came.putIfAbsent(theirs.get(day), day) }
        val undrawn = Recurrence.offScheduleDays(previous).toHashSet()
        datesIn(previous.opt("exceptions")).filterTo(undrawn) { it !in came }
        class Wanted(val noWords: Boolean, val rank: Int, val at: String, val key: String, val patch: Any)
        val wanted = ArrayList<Wanted>()
        val gone = ArrayList<String>()
        val theirs = previous.opt("overrides") as? JSONObject ?: JSONObject()
        for (key in theirs.keys().asSequence().toList()) {
            val stray = key in undrawn
            val day = if (stray) key else came[key] ?: key
            if (day in deleted) {
                if (dayHasWords(previous, key)) gone.add(key)
                continue
            }
            val at = if (stray) key else moved.opt(day) as? String ?: day
            val rank = if (stray) 2 else if (at == day) 0 else 1
            wanted.add(Wanted(!dayHasWords(previous, key), rank, at, key, theirs.get(key)))
        }
        val taken = HashSet<String>()
        val aside = JSONArray()
        // A day the note deleted stays deleted, but the copy's words for it
        // are set aside, not dropped: 0.13.0 showed such a day blank, its
        // words in the copy alone (the owner's choice, the twenty-first round,
        // as the desktop's _copy_days_placed).
        for (key in gone.sorted()) {
            if (!ownWordsHeld(previous, key, note, mine, key)) aside.put(asideEntry(previous, key, key))
        }
        for (want in wanted.sortedWith(compareBy<Wanted>({ it.noWords }, { it.rank }, { it.at }))) {
            val look = mine.opt(want.at)?.takeUnless { it == JSONObject.NULL }
            if (want.at in taken || (look != null && !want.noWords && dayHasWords(note, want.at))) {
                if (!want.noWords && !ownWordsHeld(previous, want.key, note, overrides, want.at)) {
                    aside.put(asideEntry(previous, want.key, want.at))
                }
                taken.add(want.at)
                continue
            }
            taken.add(want.at)
            if (look == null) {
                overrides.put(want.at, deepCopy(want.patch))
            } else if (!want.noWords) {
                val laid = deepCopy(want.patch) as JSONObject
                (look as? JSONObject)?.let { for (key in DAY_LOOK) if (it.has(key)) laid.put(key, it.opt(key)) }
                overrides.put(want.at, laid)
            }
        }
        return overrides to aside
    }

    /**
     * Whether [note]'s day [at], its edits [days], holds every word
     * [previous]'s day [key] has of its own: each title, text or comments it
     * holds unlike its series' ([sameWord]) -- the series' words are not the
     * day's. The desktop's `_own_words_held`. Read off the edit and the note,
     * not off a copy of the note: copied for each day, a year of days froze
     * the turn-on (the thirteenth round).
     */
    private fun ownWordsHeld(previous: JSONObject, key: String, note: JSONObject, days: JSONObject?, at: String): Boolean {
        val theirs = Recurrence.dayAsKept(previous, key)
        val patch = days?.opt(at) as? JSONObject
        return DAY_WORDS.all { word ->
            val own = theirs.opt(word)
            val mine = if (patch != null && patch.has(word)) patch.opt(word) else note.opt(word)
            sameWord(word, own, previous.opt(word)) || sameWord(word, own, mine)
        }
    }

    /**
     * [previous]'s day [key], drawn on [at], as the copy kept it: what a note
     * of its own is made of ([asideNotes]). The desktop's `_aside_entry`.
     */
    private fun asideEntry(previous: JSONObject, key: String, at: String): JSONObject {
        val kept = Recurrence.dayAsKept(previous, key)
        val entry = JSONObject()
        for (word in Recurrence.OVERRIDABLE_FIELDS) entry.put(word, pyText(kept.opt(word)))
        return entry.put("day", at)
    }

    /** [aside] added to the days [note] set aside, as the desktop's `note["_set_aside"] += aside`. */
    private fun addAside(note: JSONObject, aside: JSONArray) {
        if (aside.length() == 0) return
        val all = note.opt("_set_aside") as? JSONArray ?: JSONArray()
        for (i in 0 until aside.length()) all.put(aside.get(i))
        note.put("_set_aside", all)
    }

    /**
     * Before [note]'s rescue copy is replaced -- a turn-off, a big delete, an
     * import's Update: the days with words of their own of a series' copy the
     * note no longer repeats as, set aside to be notes of their own, unless
     * the note's own day holds them. The copy was the only place they were
     * kept, and the next copy erased them (the final audit's fifteenth round,
     * the desktop's `copy_days_set_aside`). An Update's copy too, as a
     * restore leaves it (the seventeenth round). [asked] is the note as the
     * question is asked of it: the series as it was before this save;
     * [incoming], the note whose days a backup's Update is about to write,
     * holds a day as well.
     */
    internal fun copyDaysSetAside(note: JSONObject, asked: JSONObject = note, incoming: JSONObject? = null) {
        val previous = note.opt("_prev") as? JSONObject ?: return
        if (!Recurrence.isRecurring(previous) || repeatsAs(asked, previous)) return
        val came = HashMap<String, String>()
        (previous.opt("moved") as? JSONObject)?.let { m -> for (day in m.keys()) (m.opt(day) as? String)?.let { came[it] = day } }
        // A holder's day for it: on its date, on the day the copy moved there
        // from, or where the holder has moved that day since -- by date alone,
        // a day the note moved was set aside as words it still held (the
        // sixteenth round, the desktop's copy_days_set_aside).
        fun held(key: String): Boolean {
            val day = came[key] ?: key
            return listOfNotNull(note, incoming).any { holder ->
                val mine = holder.opt("overrides") as? JSONObject
                val there = (holder.opt("moved") as? JSONObject)?.opt(day) as? String ?: day
                mine != null && setOf(key, day, there).any { at -> mine.has(at) && ownWordsHeld(previous, key, holder, mine, at) }
            }
        }
        val aside = JSONArray()
        (previous.opt("overrides") as? JSONObject)?.keys()?.asSequence()?.sorted()?.forEach { key ->
            if (dayHasWords(previous, key) && !held(key)) aside.put(asideEntry(previous, key, key))
        }
        addAside(note, aside)
        for (field in DAY_FIELDS) previous.remove(field)
    }

    /**
     * A note's days set aside as a load reads them -- the desktop's
     * `_shaped_aside`: each with a real date, written as the calendar keys
     * one, and a word, every field text. Null when none is left.
     */
    internal fun shapedAside(entries: Any?): JSONArray? {
        val list = entries as? JSONArray ?: return null
        val kept = JSONArray()
        for (i in 0 until list.length()) {
            val entry = list.opt(i) as? JSONObject ?: continue
            val day = Recurrence.parseDate(entry.opt("day")) ?: continue
            val shaped = JSONObject()
            for (key in Recurrence.OVERRIDABLE_FIELDS) shaped.put(key, entry.opt(key) as? String ?: "")
            shaped.put("day", Recurrence.formatDate(day))
            if (DAY_WORDS.any { pyStrip(shaped.getString(it)).isNotEmpty() }) kept.put(shaped)
            // Capped as moved days are: each becomes a note at the next
            // start, and a file can say anything (the desktop's cap).
            if (kept.length() >= Recurrence.MAX_OCCURRENCES) break
        }
        return kept.takeIf { it.length() > 0 }
    }

    /**
     * The notes made of [note]'s days set aside, each on its date with its
     * words and look, the series' title marked "(earlier text)", numbered
     * from [ids] -- and taken off the note. Shown on its day, those words
     * were read four ways in four rounds, each leaving a case; a note of its
     * own is seen and kept as it is (the fifteenth round, the owner's choice:
     * the desktop's `notes_set_aside`, made as its `_set_aside_days` makes them).
     */
    internal fun asideNotes(note: JSONObject, ids: Organizing.Ids, among: JSONArray): List<JSONObject> {
        val entries = shapedAside(note.remove("_set_aside")) ?: return emptyList()
        fun text(value: Any?): String = value as? String ?: ""
        // Its comments among its words: other comments are another note.
        fun key(made: JSONObject) = listOf(text(made.opt("title")), text(made.opt("date")), text(made.opt("body")),
                                          text(made.opt("comments")))
        // A note of [among] already holding the same words on the same day is
        // that note: a backup's Update made it again at every import (the lows
        // wave's verification; the desktop's settle_set_aside the same).
        val held = (0 until among.length()).mapNotNull { among.opt(it) as? JSONObject }.mapTo(HashSet()) { key(it) }
        val stamp = Files.now()
        val made = ArrayList<JSONObject>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val title = text(entry.opt("title")).ifEmpty { text(note.opt("title")) }
            val fresh = Normalize.normalizeNote(JSONObject()
                .put("title", pyStrip("$title (earlier text)"))
                .put("body", text(entry.opt("body")))
                .put("comments", text(entry.opt("comments")))
                .put("category", text(entry.opt("category")).ifEmpty { text(note.opt("category")) })
                .put("color", text(entry.opt("color")))
                .put("date", text(entry.opt("day")))
                .put("created", stamp)
                .put("updated", stamp))
            if (!held.add(key(fresh))) continue
            made.add(fresh.put("id", ids.next()))
        }
        return made
    }

    /**
     * Every note of [array] whose days were set aside given them as notes of
     * their own, added at its end; how many. [array] is what is about to be
     * written -- a copy, made the list on screen once written -- or the list
     * a load just read, which its next write keeps.
     */
    internal fun settleAside(array: JSONArray): Int {
        val ids = Organizing.ids(array)
        var made = 0
        for (i in 0 until array.length()) {
            val note = array.opt(i) as? JSONObject ?: continue
            if (!note.has("_set_aside")) continue
            for (fresh in asideNotes(note, ids, array)) {
                array.put(fresh)
                made++
            }
        }
        return made
    }

    /**
     * Whether [previous] is [note]'s series' own copy and [note] repeats as
     * it -- the desktop's `copy_holds_this_series`: its days are the note's
     * now. The turn-off no longer asks it: any copy but an Update's gives
     * the words the note has lost ([turnOffCopy], the tenth round).
     */
    private fun seriesDaysSpent(note: JSONObject, previous: JSONObject): Boolean =
        seriesOwnCopy(previous) && repeatsAs(note, previous)

    /**
     * Whether the copy [note] keeps is an import's Update copy: a turn-off
     * over one keeps it, and Restore previous then brings back the note as
     * it was before it was updated, not the series -- the desktop's
     * `_repeat_off_line` (the final audit's ninth round).
     */
    fun keepsUpdateCopy(note: JSONObject): Boolean = (note.opt("_prev") as? JSONObject)?.let { updateCopy(it) } == true

    /** Whether [note] repeats as the series [previous] holds, its date and its rule: the desktop's `_repeats_as`. */
    private fun repeatsAs(note: JSONObject, previous: JSONObject): Boolean =
        Recurrence.isRecurring(note) &&
            sameSeries(previous.opt("date"), previous.opt("recurrence"), note.opt("date"), note.opt("recurrence"))

    /**
     * Whether [previous] is the copy an import's Update made: the note as it
     * was here before the backup's version replaced it. The desktop's
     * `update_copy`.
     */
    internal fun updateCopy(previous: JSONObject): Boolean = UPDATE_COPY_ONLY.any { previous.has(it) }

    /**
     * Which comments a waiting copy keeps when a save that takes only the
     * comments clears [comments] over a copy holding [copyComments]: the
     * ones whose loss would be the larger, counted by what is gone ([lost]).
     * Two letters cleared after a long comment was deleted lose to it, as the
     * body's words do; a long comment cleared over a short one takes its
     * place. Taken always, the two letters wrote over the long comment, in no
     * file after (the final audit's eighth round, the desktop's
     * `comments_kept_by_copy`).
     */
    internal fun commentsKeptByCopy(copyComments: String, comments: String): String =
        if (lost(comments, copyComments) < lost(copyComments, comments)) copyComments else comments

    /**
     * What only an update's copy holds ([applyUpdate], the desktop's
     * `apply_shared_update`): a series' own copy -- a turn-off's, on either
     * app, 0.12.0's included -- holds the text, the date, the repeat and the
     * days, and none of these. The desktop's `_UPDATE_COPY_ONLY`.
     */
    private val UPDATE_COPY_ONLY = listOf("category", "color", "checklist", "pinned")

    /** Whether [previous] is the copy a repeat's turn-off made of its series: the desktop's `_series_own_copy`. */
    private fun seriesOwnCopy(previous: JSONObject): Boolean =
        Recurrence.isRecurring(previous) && UPDATE_COPY_ONLY.none { previous.has(it) }

    /** [daysBackFromCopy]'s question, of a [note] as a load shapes it and its copy, [previous]. */
    private fun daysBackWanted(note: JSONObject, previous: JSONObject): Boolean {
        if (!seriesOwnCopy(previous) || DAY_FIELDS.any { daysIn(note.opt(it)) > 0 }) return false
        // Repeating now as another series: its days are not these.
        if (Recurrence.isRecurring(note) && !repeatsAs(note, previous)) return false
        return daysHoldOwnText(previous)
    }

    /**
     * What the desktop's read does to a note from a file, done to the raw
     * note the calendar and every day's action read -- at the load and at a
     * restore from the Trash: a series' days back from its copy
     * ([daysBackAsRead]), and a move onto a date its rule now gives dropped
     * ([Normalize.dropHiddenMoves]); a delete there took the hidden day (the
     * sixteenth round). The next write keeps it.
     */
    internal fun settleAsRead(raw: JSONObject) {
        daysBackAsRead(raw)
        // One move a date, as the read's view keeps them: two moves onto one
        // date, left by 0.13.0 or an Undo, gave History two rows of one key,
        // and the list threw (the nineteenth round).
        (raw.opt("moved") as? JSONObject)?.let { moved ->
            val clean = Normalize.cleanMoves(moved, raw.opt("exceptions") as? JSONArray ?: JSONArray(), raw)
            if (clean.length() != moved.length()) raw.put("moved", clean)
        }
        Normalize.dropHiddenMoves(raw)
    }

    /**
     * [daysBackFromCopy] for a note read from the file as it lies, [raw]:
     * asked of the note as `normalize_note` shapes it -- its deleted days
     * real dates, its copy shaped, a day's edit its text alone -- as the
     * desktop asks it at the end of its load, and the days it takes put into
     * the note as written, shaped. Asked of the file as it lay, a deleted
     * day written another way stopped the move the desktop makes, and a copy
     * with no date, or a day's edit under no date, made one the desktop does
     * not (the final audit's seventh round). Then the copy's days are spent
     * from the copy as written, when the note repeats as its series
     * ([spendSeriesDays]): its days first taken back into the note on every
     * date the note's own leave free, the note's days then written as the
     * view holds them. Only a copy that can be a series' own copy holding a
     * day is shaped to ask -- one with a repeat, none of an update's own
     * fields, and a day's field: shaping takes a repeat away, never gives
     * one, and keeps the fields a copy has. Asked of any copy with a day's
     * field, every note an import's Update had made a copy of -- each holds
     * all three -- was shaped twice at each start, for nothing (the final
     * audit's ninth round).
     */
    internal fun daysBackAsRead(raw: JSONObject) {
        val held = raw.opt("_prev") as? JSONObject ?: return
        if (DAY_FIELDS.none { held.has(it) } || !Recurrence.isRecurring(held) || UPDATE_COPY_ONLY.any { held.has(it) }) return
        val view = Normalize.normalizeNote(raw, daysBack = false)
        val previous = view.opt("_prev") as? JSONObject ?: return
        if (daysBackWanted(view, previous)) for (field in DAY_FIELDS) {
            val days = deepCopy(shaped(previous, field))
            raw.put(field, days)
            view.put(field, deepCopy(days))
        }
        if (seriesDaysSpent(view, previous)) {
            addAside(view, takeDaysBack(view, previous))
            for (field in DAY_FIELDS) {
                raw.put(field, deepCopy(view.get(field)))
                held.remove(field)
            }
            // The copy's days a day holding words could not take, set aside
            // as the desktop's load keeps them: notes of their own at once
            // ([settleAside]).
            view.opt("_set_aside")?.let { raw.put("_set_aside", it) }
        }
    }

    /**
     * What else a rescue copy can hold, and the word the restore question
     * uses for it -- the desktop's `_PREV_EXTRA_LABELS`, in its order.
     */
    val PREV_EXTRA_LABELS = listOf(
        "checklist" to "checklist", "date" to "date",
        "recurrence" to "repeat rule", "color" to "colour",
        "category" to "category", "overrides" to "per-day edits",
        "exceptions" to "skipped days", "moved" to "moved days",
        "pinned" to "pin",
    )

    /**
     * A field's value as a load would shape it: what `normalize_note`
     * fills in for one that is missing or of the wrong type, so a copy that
     * omits an empty checklist does not differ from a note holding one.
     */
    private fun shaped(note: JSONObject, field: String): Any {
        val value = note.opt(field)
        return when (field) {
            "formatting", "paragraphs", "checklist", "exceptions" ->
                value as? JSONArray ?: JSONArray()
            // A rule with keys missing is the default rule with those keys
            // filled, as RECURRENCE_DEFAULTS fills them on a load.
            "recurrence" -> {
                val rule = defaultRecurrence()
                (value as? JSONObject)?.let { given ->
                    for (key in given.keys()) rule.put(key, given.opt(key))
                }
                rule
            }
            "overrides", "moved" -> value as? JSONObject ?: JSONObject()
            "pinned" -> value == true
            "category" -> (value as? String)?.ifEmpty { null } ?: "General"
            else -> value as? String ?: ""
        }
    }

    /**
     * Whether updating [note] from [incoming] would change any of [fields].
     * A no-change update would replace the rescue copy with a duplicate and
     * lose the one earlier version: the desktop's `shared_update_changes`.
     */
    internal fun sharedUpdateChanges(
        note: JSONObject, incoming: JSONObject, fields: List<String>,
    ): Boolean {
        // Both as a load shapes them, as the desktop compares them since its
        // own unchanged backup read as "edited elsewhere": a day's empty
        // colour, styles in another order, a closing table's line -- each a
        // difference no person made, and an Update on one spent the note's
        // only rescue copy on a copy of itself.
        // This side without its rescue copy, as the desktop compares it: the
        // note held here is not read again, and read again, an older
        // notebook's read took days from its copy ([daysBackFromCopy]) -- an
        // unchanged note read as changed, its Update spending the only
        // earlier version (the seventh round). The arriving note read as a
        // load reads it, its copy's days taken back, then compared without
        // its copy: read bare, an older backup of a turned-off series read
        // as edited elsewhere against the same notebook (the desktop's
        // 8b5d6cc, the final audit's ninth round, reversing the eighth's bare
        // reading; a backup whose copy holds a day reset here since now reads,
        // truly, as changed). No field compared is the copy.
        val bare = JSONObject().also { out ->
            for (key in note.keys()) if (key != "_prev") out.put(key, note.opt(key))
        }
        val mine = Normalize.normalizeNote(bare)
        val theirs = Normalize.normalizeNote(incoming)
        // The repeat by the days it gives ([sameSeries]): its start weekday lit
        // in one and left to the start in the other read as changed, and the
        // Update spent the note's only earlier version (the audit's round 19).
        // Another start is a change all the same, through "date".
        return fields.any {
            if (it == "recurrence") !sameSeries(mine.opt("date"), mine.opt(it), mine.opt("date"), theirs.opt(it))
            else !sameJson(shaped(mine, it), shaped(theirs, it))
        }
    }

    /**
     * Overwrite a note's [fields] from an incoming copy, keeping everything
     * they held in `_prev` -- the rescue copy "↩ Restore previous" puts
     * back, the checklist's ticks and the repeat rule included, not only
     * the words. An update that changes nothing leaves the note alone.
     *
     * @return whether the note changed.
     */
    internal fun applyUpdate(
        note: JSONObject,
        incoming: JSONObject,
        now: String,
        fields: List<String> = UPDATABLE_FROM_SHARED,
    ): Boolean {
        if (!sharedUpdateChanges(note, incoming, fields)) return false
        val previous = JSONObject()
        for (field in PREV_TEXT_FIELDS) previous.put(field, shaped(note, field))
        for (field in fields) {
            if (!previous.has(field)) previous.put(field, deepCopy(shaped(note, field)))
        }
        // Turned back on another way than the series was -- another start or
        // rule ([keptDaysFit]): the old series' deleted days, which hold
        // nothing, go with this copy instead of hiding the new series' own.
        val shared = "overrides" !in fields && "recurrence" in fields
        val restarted = shared && !keptDaysFit(note, shaped(incoming, "date"), shaped(incoming, "recurrence"))
        // A share code's repeat turned off: this phone's days are copied
        // beside the repeat they hang on, and stay in the note too, out of
        // sight until a repeat brings them back. A backup brings its own.
        if (shared) {
            for ((field, days) in daysToRescue(note, shaped(incoming, "recurrence"))) previous.put(field, days)
        }
        if (restarted && daysIn(note.opt("exceptions")) > 0) previous.put("exceptions", deepCopy(shaped(note, "exceptions")))
        // The copy this one replaces may hold another series' days alone:
        // set aside once [keptDaysFit] has read them (the fifteenth round) --
        // held by the days a backup brings, too (the seventeenth round).
        copyDaysSetAside(note, incoming = if ("overrides" in fields) incoming else null)
        note.put("_prev", previous)
        for (field in fields) note.put(field, deepCopy(shaped(incoming, field)))
        // A backup's note sets aside what its own copy kept, as it is read:
        // an Add kept those, an Update dropped them (the audit's twenty-second
        // round; the desktop's apply_shared_update the same).
        val arriving = incoming.opt("_set_aside") as? JSONArray
        if ("overrides" in fields && arriving != null && arriving.length() > 0) {
            val mine = note.opt("_set_aside") as? JSONArray ?: JSONArray()
            for (i in 0 until arriving.length()) mine.put(deepCopy(arriving.get(i)))
            note.put("_set_aside", mine)
        }
        if (restarted) note.put("exceptions", JSONArray())
        Normalize.dropHiddenMoves(note)
        note.put("updated", now)
        return true
    }

    /** A JSON value copied all the way down, so two notes never share a list. */
    private fun deepCopy(value: Any): Any = when (value) {
        is JSONObject -> JSONObject(value.toString())
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }

    /**
     * Structural equality: the same keys with the same values, in any
     * order. Comparing the text instead calls two identical rules
     * different when their keys were written in another order.
     */
    internal fun sameJson(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject ->
            a.length() == b.length() && a.keys().asSequence().all { b.has(it) && sameJson(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray ->
            a.length() == b.length() && (0 until a.length()).all { sameJson(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    /**
     * The rescue copy is spent once the note holds what it kept: Undo after
     * a big deletion brought the words back, yet the banner stayed, and
     * "↩ Restore previous" pressed later put the old version over everything
     * written since.
     */
    internal fun dropSpentCopy(note: JSONObject) {
        val previous = note.opt("_prev") as? JSONObject ?: return
        if (spent(note, previous)) note.remove("_prev")
    }

    /** Whether [note] holds again everything [previous] kept: the desktop's `rescue_copy_spent`. */
    private fun spent(note: JSONObject, previous: JSONObject): Boolean =
        PREV_TEXT_FIELDS.all { sameJson(shaped(note, it), shaped(previous, it)) } &&
            PREV_EXTRA_LABELS.all { (field, _) -> !previous.has(field) || copyFieldHeld(note, previous, field) }

    /**
     * Whether [note] holds [previous]'s [field] again: a repeat rule by the
     * days it gives ([sameSeries]), any other as it is. Compared as stored, a
     * rule with its weekday lit was never the same, and the copy never spent
     * (the desktop's `_copy_field_held`, the eighteenth round).
     */
    private fun copyFieldHeld(note: JSONObject, previous: JSONObject, field: String): Boolean =
        if (field == "recurrence") {
            sameSeries(if (previous.has("date")) previous.opt("date") else note.opt("date"), previous.opt("recurrence"),
                note.opt("date"), note.opt("recurrence"))
        } else sameJson(shaped(note, field), shaped(previous, field))

    /**
     * Whether [note] holds a rescue copy with something the note lacks --
     * the desktop's `_holds_unspent_rescue`. A note has one copy: an
     * import's Update makes another over it, so its question says so.
     */
    internal fun holdsUnspentCopy(note: JSONObject): Boolean =
        (note.opt("_prev") as? JSONObject)?.let { !spent(note, it) } == true

    /**
     * Whether a turn-off of [note] keeps its copy whole -- an import's
     * Update copy not spent ([turnOffCopy]): its days stay in it, nothing
     * set aside (the seventeenth round).
     */
    internal fun turnOffKeepsCopy(note: JSONObject): Boolean {
        val existing = note.opt("_prev") as? JSONObject ?: return false
        return !spent(note, existing) && updateCopy(existing)
    }

    /**
     * The copy a repeat's turn-off makes, [copy] -- the note whole, its
     * repeat and days as they are now -- given the words of any copy but an
     * Update's [note] holds whose words the note has lost ([wordsLostTo]:
     * the big delete's test, from the copy to the note). A big delete saved
     * first, the repeat turned off after: the turn-off's copy, taken from
     * the note as it stood, replaced the delete's, and the deleted words
     * were in no copy (the desktop's `_turn_off_rescue_copy`). The note
     * keeps its newest words itself: a turn-off changes none of them. A copy
     * of words is words alone, as the desktop's: a series' days live in the
     * note, and no copy carries them on.
     *
     * And an import's Update copy the note does not hold again is kept whole
     * -- the note as it was here before the backup's version replaced it, in
     * no other file -- given the series' date, repeat and days only where it
     * lacks them: replaced by the turn-off's, that version was gone unsaid
     * (the final audit's eighth round, the desktop's `_turn_off_rescue_copy`).
     * Asked first, as the desktop asks it since its 8b5d6cc, where it comes
     * before the branch of a held save -- one this phone has no road to:
     * its Repeat sheet saves the words before the repeat, never holding one.
     *
     * And an earlier turn-off's copy gives its words as a copy of words
     * does: a big delete, the repeat off, back on as it was, off again, and
     * the second turn-off's copy replaced the first's -- the deleted words,
     * or a long comment the comments' own save had kept there, in no file
     * (the ninth round). Whatever series it held: back on from another
     * start, every two weeks or to an end, the same loss (the tenth, the
     * desktop's 99f041e). And only words the note has lost: given whenever
     * the two differed, a line added between the two turn-offs went back to
     * the older words at Restore previous (the tenth, the desktop's 04044ce).
     * The copy to keep: [copy], given words, or the update's.
     */
    internal fun turnOffCopy(copy: JSONObject, note: JSONObject): JSONObject {
        val existing = note.opt("_prev") as? JSONObject ?: return copy
        if (spent(note, existing)) return copy
        if (updateCopy(existing)) {
            val kept = JSONObject(existing.toString())
            for (key in listOf("date", "recurrence") + DAY_FIELDS) {
                if (!kept.has(key)) kept.put(key, deepCopy(copy.opt(key) ?: JSONObject.NULL))
            }
            return kept
        }
        if (wordsLostTo(existing, note)) {
            for (field in PREV_TEXT_FIELDS) if (existing.has(field)) copy.put(field, deepCopy(existing.get(field)))
        }
        return copy
    }

    /**
     * Whether [note] has lost words to the copy [existing] holds -- the big
     * delete's own question ([shouldKeepPrevious], from the copy to the
     * note: its title, body and comments), as the desktop's
     * `_turn_off_rescue_copy` asks it since its 04044ce. Asked only whether
     * the two differ, a line added between two turn-offs went back to the
     * older words at Restore previous, and was in no file after one more
     * (the final audit's tenth round).
     */
    private fun wordsLostTo(existing: JSONObject, note: JSONObject): Boolean {
        fun text(holder: JSONObject, key: String): String = holder.opt(key) as? String ?: ""
        return shouldKeepPrevious(text(existing, "title"), text(existing, "body"), text(note, "title"), text(note, "body"),
            text(existing, "comments"), text(note, "comments"))
    }

    /**
     * What a restore keeps of the version it replaces, to be the copy in its
     * turn: its text, and every other field [previous] brings back -- so the
     * same banner brings it back. Null when it holds nothing the restored
     * version lacks (an emptied note, words the restored text contains): a
     * banner offering that back would only mislead. The desktop's
     * `copy_replaced_by_restore`.
     */
    internal fun replacedByRestore(live: JSONObject, previous: JSONObject): JSONObject? {
        val wasTitle = (live.opt("title") as? String).orEmpty()
        // A restore that ends the repeat: a copy of the series' days goes
        // beside the repeat in the copy it leaves -- the note keeps its own
        // -- so the same banner brings the series back whole
        // (`days_to_rescue`, the days the copy restored does not hold itself).
        val taken = if (previous.has("recurrence")) {
            daysToRescue(live, previous.opt("recurrence")).filterKeys { !previous.has(it) }
        } else emptyMap()
        // Everything the restore writes over is weighed: the comments too,
        // and the styles of the same words. Comments typed since the copy
        // was made went to no file, the question saying nothing of them --
        // and a body as the copy has it, its bold changed since, the same
        // (the final audit's seventh round, the desktop's fix with it).
        val body = (live.opt("body") as? String).orEmpty()
        val sameWords = body == (previous.opt("body") as? String).orEmpty()
        val lines = body.split("\n").size
        val formatting = Exporting.normalizeFormatting(live.opt("formatting"))
        val paragraphs = Exporting.normalizeParagraphs(live.opt("paragraphs"), lines)
        // Words held in the copy's but not the same: their styles cannot be
        // laid beside the copy's, so any style is weighed as lost -- a part of
        // the copy's words made bold or set as a list since went to no file
        // (the desktop's ae6efe2, `styles_kept`; the final audit's ninth round).
        val stylesKept = if (sameWords) {
            formatting == Exporting.normalizeFormatting(previous.opt("formatting")) &&
                paragraphs == Exporting.normalizeParagraphs(previous.opt("paragraphs"), lines)
        } else formatting.isEmpty() && paragraphs.isEmpty()
        val nothingLost = PyText.contains((previous.opt("body") as? String).orEmpty(), PyText.strip(body)) &&
            (wasTitle.isBlank() || wasTitle == (previous.opt("title") as? String).orEmpty()) &&
            PyText.contains((previous.opt("comments") as? String).orEmpty(), PyText.strip((live.opt("comments") as? String).orEmpty())) &&
            stylesKept &&
            PREV_EXTRA_LABELS.all { (field, _) -> !previous.has(field) || copyFieldHeld(live, previous, field) } &&
            taken.isEmpty()
        if (nothingLost) return null
        val replaced = JSONObject()
        for (field in PREV_TEXT_FIELDS) replaced.put(field, deepCopy(shaped(live, field)))
        for ((field, _) in PREV_EXTRA_LABELS) {
            // A repeat hangs on its start date: a copy holding the series'
            // repeat without its date is shaped as no repeat at all
            // (shapePrevious, as normalize_note), and the restore after this
            // one left the note without its series -- unseen while a day's
            // kept words showed whether the note repeated or not.
            if (previous.has(field) || (field == "date" && previous.has("recurrence"))) {
                replaced.put(field, deepCopy(shaped(live, field)))
            }
        }
        for ((field, days) in taken) replaced.put(field, days)
        return replaced
    }

    /**
     * Whether restoring note [id]'s rescue copy would keep what it holds
     * now, for the restore question to promise it -- asked when the
     * question is, of that one note. Worked out for every note at every
     * load, a backup's rescue copy of millions of letters held the app
     * still at each start and after each save (ISSUES_LOG, "A question
     * asked of every note, to answer one").
     */
    fun previousKeeps(load: Load, id: Int): Boolean {
        val array = load.raw ?: return false
        val index = Organizing.indexOfId(array, id)
        if (index < 0) return false
        val live = array.get(index) as JSONObject
        val previous = live.opt("_prev") as? JSONObject ?: return false
        return replacedByRestore(live, previous) != null
    }

    /**
     * Brings back the rescue copy: the text a drastic shortening replaced,
     * or everything an update replaced. Written straight onto the note, as
     * the desktop writes it, and a category deleted in the meantime comes
     * back as General. What it replaces becomes the copy ([replacedByRestore]),
     * so the same banner brings that back: spent, the copy took everything
     * written since the deletion with no way back.
     */
    fun restorePrevious(load: Load, id: Int): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val index = Organizing.indexOfId(array, id)
        if (index < 0) return SaveResult.Failed(GONE)
        val live = array.get(index) as JSONObject
        // Shaped again, held to the note's ceilings: a file written before
        // the import shaped it can hold a copy as it came.
        val previous = (live.opt("_prev") as? JSONObject)?.let { Normalize.shapePrevious(it) }
            ?: return SaveResult.Failed("This note has no earlier version to bring back.")
        // What it replaces, the series' days included when it ends the repeat.
        val replaced = replacedByRestore(live, previous)
        val note = JSONObject(live.toString())
        for (field in PREV_TEXT_FIELDS) note.put(field, shaped(previous, field))
        for ((field, _) in PREV_EXTRA_LABELS) {
            if (previous.has(field)) note.put(field, deepCopy(shaped(previous, field)))
        }
        val categories = load.categories
        if ((note.opt("category") as? String) !in categories) note.put("category", "General")
        (note.opt("overrides") as? JSONObject)?.let { overrides ->
            for (day in overrides.keys()) {
                val patch = overrides.opt(day) as? JSONObject ?: continue
                if (patch.has("category") && (patch.opt("category") as? String) !in categories) {
                    patch.put("category", "General")
                }
            }
        }
        // An older rule brought back can give a date a day was moved onto
        // since: that move goes now, as the desktop's restore drops it (the
        // sixteenth round).
        Normalize.dropHiddenMoves(note)
        if (replaced == null) note.remove("_prev") else note.put("_prev", replaced)
        note.put("updated", Files.now())
        val candidate = copyOf(array)
        candidate.put(index, note)
        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        array.put(index, note)
        return SaveResult.Ok(id)
    }


    /**
     * The desktop's rule for when an edit deserves a rescue copy
     * (`rescue_copy_due`): emptied; more than 40 characters gone, 30% of
     * the text or 200; or most of a short note, 70% and 15 characters at
     * least. Counted by what is gone ([textLost]), not by how much shorter
     * the text got: a short note replaced by a new line, and a paragraph
     * replaced by one as long, kept no copy (2026-10-24, the desktop's test
     * case 692).
     *
     * The comments are weighed as a text of their own, by the same bars:
     * left out, wiping them -- selected and typed over, or deleted -- kept no
     * copy, though the copy holds them; counted in with the body, a long
     * comment would make a whole body deleted read as a small share (the
     * final audit's sixth round, the desktop's fix with it).
     */
    internal fun shouldKeepPrevious(
        oldTitle: String, oldBody: String, newTitle: String, newBody: String,
        oldComments: String = "", newComments: String = "",
    ): Boolean = textDue(oldTitle, oldBody, newTitle, newBody) || textDue("", oldComments, "", newComments)

    /** [shouldKeepPrevious]'s bars, for one text: a title and a body, or the comments alone -- `_text_rescue_due`. */
    private fun textDue(oldTitle: String, oldBody: String, newTitle: String, newBody: String): Boolean {
        // In letters, as Python's len counts them, as every count below is.
        val oldLen = oldTitle.codePointCount(0, oldTitle.length) + oldBody.codePointCount(0, oldBody.length)
        if (oldLen == 0) return false
        if (newTitle.length + newBody.length == 0) return true   // emptied
        // Both bars below ask for 15 characters gone at least, and [lost] is
        // never more than the span changed: a save changing less -- the
        // autosave after each letter -- is answered from the span alone. It
        // split the whole old and new text into words three times, on the
        // screen's thread, for one letter typed (the final audit's fifth round).
        if (textLost(oldTitle, newTitle) + textLost(oldBody, newBody) < 15) return false
        val lost = lost(oldTitle, newTitle) + lost(oldBody, newBody)
        // The shares in the unit the loss is counted in, each word with one
        // space ([measured]): set against a length with a table's padding
        // in it, five rows of a shopping list's eight deleted at once read
        // as under 30% and kept no copy (the desktop's fifth round).
        val size = (measured(oldTitle) + measured(oldBody)).takeIf { it > 0 } ?: oldLen
        if (lost > 40 && (lost >= size * 0.3 || lost >= 200)) return true
        return lost >= 15 && lost >= size * 0.7
    }

    /** [text]'s length as [wordsLost] counts it: each word with one space -- the desktop's `_measured`. */
    internal fun measured(text: String): Int = words(text).sumOf { it.codePointCount(0, it.length) + 1 }

    /**
     * [text]'s words, each line's list marker left out -- the desktop's
     * `_words`: a marker is no word. Counted as words, numbers turned into
     * bullets, or bullets taken one level in, read as words lost, and the
     * autosave's copy replaced the one holding a paragraph deleted before
     * (the final audit's fifth round, found on the phone first).
     */
    private fun words(text: String): List<String> = PyText.split(Pasting.stripListMarkers(text))

    /**
     * What a save of [new] over [old] takes away: the smaller of two counts
     * that each never come out under it -- the desktop's `_lost`. [textLost]
     * read a column added to a table, which pads every row again, as the
     * whole table lost, and the autosave's copy replaced the one the note
     * held: a deleted paragraph in no copy any more (the final audit's
     * fourth round). [wordsLost] reads one letter changed in a paragraph
     * with no spaces as the paragraph lost; the smaller is right for both.
     */
    internal fun lost(old: String, new: String): Int = minOf(textLost(old, new), wordsLost(old, new))

    /**
     * How many characters of the words in [old] are not in [new], each word
     * counted as often as it appears, with the space it went with -- the
     * desktop's `words_lost`: its words Python's `str.split()`, their
     * lengths in code points. A word moved, or a cell padded wider, is not
     * lost; a word with one letter changed is lost whole; a list's markers
     * are no words ([words]).
     */
    internal fun wordsLost(old: String, new: String): Int {
        val gone = HashMap<String, Int>()
        for (word in words(old)) gone[word] = (gone[word] ?: 0) + 1
        for (word in words(new)) gone[word]?.let { gone[word] = it - 1 }
        return gone.entries.sumOf { (word, count) -> if (count > 0) (word.codePointCount(0, word.length) + 1) * count else 0 }
    }

    /**
     * How many characters of [old] [new] does not keep at either end: its
     * length less the longest start the two share and, of what follows, the
     * longest end -- the desktop's `text_lost`, in code points as Python
     * counts. Words kept in the middle count as lost, so the measure errs
     * towards keeping a copy.
     */
    internal fun textLost(old: String, new: String): Int {
        val limit = minOf(old.length, new.length)
        var start = 0
        while (start < limit && old[start] == new[start]) start++
        // Ends on the edges of letters, as Python's are: an emoji the two
        // share half of differs whole. Counted in UTF-16 units, twelve emoji
        // deleted were twenty-four characters lost: past the bars on the
        // phone alone, whose copy then replaced the one holding a deleted
        // paragraph (the final audit's seventh round).
        if (start > 0 && Character.isHighSurrogate(old[start - 1]) &&
            (start < old.length && Character.isLowSurrogate(old[start]) ||
                start < new.length && Character.isLowSurrogate(new[start]))) start--
        var end = 0
        while (end < limit - start && old[old.length - 1 - end] == new[new.length - 1 - end]) end++
        if (end > 0 && Character.isLowSurrogate(old[old.length - end]) &&
            (old.length - end > 0 && Character.isHighSurrogate(old[old.length - end - 1]) ||
                new.length - end > 0 && Character.isHighSurrogate(new[new.length - end - 1]))) end--
        return old.codePointCount(start, old.length - end)
    }

    /**
     * [formatting] and [paragraphs] carried through one edit of the text
     * from [old] to [new], as a note's save carries them: the styles by
     * [remapSpans], the lines' properties by their lines. The template
     * editor asks it at every change, as the note editor moves its styles
     * at every change: it kept the template's styles at their offsets
     * while the words moved under them, and every note made from it began
     * with its bold on the wrong words.
     */
    fun throughEdit(formatting: JSONArray, paragraphs: JSONArray, old: String, new: String): Pair<JSONArray, JSONArray> =
        spansToJson(remapSpans(old, new, readSpans(formatting)).spans) to
            movedParagraphs(JSONObject().put("paragraphs", paragraphs), old, new)

    internal fun spansToJson(spans: List<FormatSpan>): JSONArray {
        val array = JSONArray()
        for (span in spans) {
            val o = JSONObject()
            o.put("start", span.start)
            o.put("end", span.end)
            o.put("style", span.style)
            if (span.value.isNotEmpty()) {
                // Back as the number the desktop keeps a size as: "20" in
                // quotes is a size its normalize_formatting refuses.
                val size = if (span.style == "font_size") span.value.toIntOrNull() else null
                o.put("value", size ?: span.value)
            }
            array.put(o)
        }
        return array
    }

    private fun movedParagraphs(note: JSONObject, old: String, new: String): JSONArray {
        val source = note.opt("paragraphs") as? JSONArray ?: return JSONArray()
        val oldLines = old.split("\n").size
        val byLine = LinkedHashMap<Int, JSONObject>()
        for (i in 0 until source.length()) {
            val o = source.opt(i) as? JSONObject ?: continue
            // The entry the desktop's normalize_paragraphs would keep for a
            // line, and only that one. A line is a whole number -- or
            // true/false, which Python counts as 1 and 0; not 1.0, which it
            // refuses. Within the text as it was: a line past its end is
            // dropped there, and an edit adding lines must not revive it.
            // The FIRST usable entry for a line wins, as there; a later one
            // for the same line once replaced it here.
            val line = when (val raw = o.opt("line")) {
                is Int -> raw
                is Long -> if (raw in 0..Int.MAX_VALUE.toLong()) raw.toInt() else continue
                is Boolean -> if (raw) 1 else 0
                else -> continue
            }
            if (line < 0 || line >= oldLines || line in byLine || !worthKeeping(o)) continue
            byLine[line] = o
        }
        val survivors = remapParagraphLines(old, new, byLine.keys.toList())
        val kept = JSONArray()
        for ((oldLine, newLine) in survivors) {
            val marker = byLine[oldLine] ?: continue
            // The whole entry moves, only its line changes. A paragraph can
            // be centred, or indented, without being a list at all; this
            // once rebuilt each entry from its line and its kind alone, and
            // wrote "bullet" where there was no kind -- an edit on the phone
            // turned a centred line into a bullet and dropped its indent.
            val moved = JSONObject(marker.toString())
            moved.put("line", newLine)
            kept.put(moved)
        }
        return kept
    }

    /**
     * Whether the desktop records an entry at all: a list kind, a centred
     * or right alignment, or an indent. A plain left line has none, and an
     * entry for one does not claim its line there.
     */
    private fun worthKeeping(o: JSONObject): Boolean {
        val kind = o.opt("kind")
        val align = o.opt("align")
        val indent = o.opt("indent")
        return kind == "bullet" || kind == "number" || align == "center" || align == "right" ||
            ((indent is Int || indent is Long) && (indent as Number).toLong() > 0)
    }

    /** RECURRENCE_DEFAULTS, field for field. "" freq means a one-off note. */
    private fun defaultRecurrence(): JSONObject {
        val rule = JSONObject()
        rule.put("freq", "")
        rule.put("interval", 1)
        rule.put("weekdays", JSONArray())
        rule.put("month_days", JSONArray())
        rule.put("month_day_policy", "clamp")
        rule.put("end", "")
        rule.put("count", 0)
        return rule
    }

    /**
     * Indent 2, non-ASCII left alone: the same bytes the desktop writes,
     * so a file can move between the two unchanged. The writing itself is
     * [Files.writeAtomic], the one copy every data file uses.
     */
    private fun writeAtomic(file: File, array: JSONArray): String? =
        Files.writeAtomic(file, array.toString(2))

    fun parseNotes(text: String): List<Note> {
        val array = try {
            JSONArray(text)
        } catch (e: Exception) {
            return emptyList()
        }
        return notesOf(array)
    }

    /**
     * `#rgb` and `#rrggbb`, which is exactly what `sanitize_color` accepts.
     *
     * Colours arrive from every untrusted door the app has — a share code,
     * an imported backup, a hand-edited file — so anything else is refused
     * rather than passed on to a renderer.
     */
    fun isHexColor(value: String): Boolean {
        val v = value.trim()
        if (!v.startsWith("#")) return false
        if (v.length != 4 && v.length != 7) return false
        return v.drop(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * The colour if it is one, otherwise [fallback] -- and #rgb comes back as
     * #rrggbb, as the desktop's `sanitize_color` expands it at the door:
     * read as six digits, `#f00` drew a near-black green.
     */
    fun sanitizeColor(value: String, fallback: String = ""): String {
        if (!isHexColor(value)) return fallback
        val v = value.trim()
        return if (v.length == 4) "#" + v.drop(1).map { "$it$it" }.joinToString("") else v
    }

    /**
     * True for a stored date naming a day that exists -- the desktop's
     * `parse_date`, asked of [Recurrence], so "a date" means one thing here.
     *
     * Not lenient: left lenient, "2026-02-30" would quietly become the 2nd
     * of March, and a note would show a date the user never chose.
     */
    fun isDate(value: String): Boolean = Recurrence.parseDate(value) != null

    private fun readNote(o: JSONObject): Note {
        val rule = o.opt("recurrence") as? JSONObject
        return Note(
            id = o.intOr("id", 0),
            title = o.str("title"),
            body = o.str("body"),
            category = o.str("category", "General").ifEmpty { "General" },
            color = o.str("color"),
            date = o.str("date"),
            updated = o.str("updated"),
            created = o.str("created"),
            order = (o.opt("order") as? Number)?.toDouble()?.takeIf { it.isFinite() && o.opt("order") !is Boolean },
            pinned = o.opt("pinned") == true,
            comments = o.str("comments"),
            attachmentCount = o.arrayLength("attachments"),
            formatting = readSpans(o.opt("formatting") as? JSONArray),
            paragraphCount = o.arrayLength("paragraphs"),
            checklist = readChecklist(o.opt("checklist") as? JSONArray),
            recurrenceFreq = rule?.str("freq") ?: "",
            recurrenceInterval = rule?.intOr("interval", 1) ?: 1,
            recurrenceLabel = Recurrence.describe(o),
            exceptionCount = o.arrayLength("exceptions"),
            overrideCount = (o.opt("overrides") as? JSONObject)?.length() ?: 0,
            deletedAt = o.str("deleted_at"),
            uid = o.str("uid"),
            hasPrevious = o.opt("_prev") is JSONObject,
            previousAlso = (o.opt("_prev") as? JSONObject)?.let { prev ->
                PREV_EXTRA_LABELS.filter { (key, _) -> prev.has(key) }.map { it.second }
            } ?: emptyList(),
        )
    }

    /**
     * A span survives only if it names a style and describes a real range.
     * A backwards or zero-width span is dropped here rather than at the
     * renderer, where it would be an exception in the middle of a draw.
     */
    private fun readSpans(array: JSONArray?): List<FormatSpan> {
        if (array == null) return emptyList()
        val out = ArrayList<FormatSpan>(array.length())
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val style = o.str("style")
            if (style.isEmpty()) continue
            val start = o.intOr("start", -1)
            val end = o.intOr("end", -1)
            if (start < 0 || end <= start) continue
            // A colour or a face is text in the desktop's file, but a size
            // is a NUMBER: read as text alone, every size the desktop set
            // came in empty -- not drawn, and written back without a value,
            // which the desktop then dropped on its next start. And ONLY a
            // number: a size in quotes is one the desktop refuses, and read
            // here it went back out as a number the desktop then honoured.
            val v = o.opt("value")
            val value = if (style == "font_size") {
                if (v is Int || v is Long) v.toString() else ""
            } else {
                v as? String ?: ""
            }
            out.add(FormatSpan(start, end, style, value))
        }
        // Bold and italic on the same letters as two spans -- a desktop file
        // from before its `normalize_formatting` folded them -- are read as
        // one bold_italic, as the desktop reads them on every load: the
        // editor's styling sees one style a letter, and with two, pressing B
        // to take the bold off took the italic with it.
        return Exporting.foldBoldWithItalic(out)
    }

    private fun readChecklist(array: JSONArray?): List<ChecklistItem> {
        if (array == null) return emptyList()
        val out = ArrayList<ChecklistItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            out.add(ChecklistItem(o.str("text"), o.opt("done") == true))
        }
        return out
    }

    /**
     * A wrong TYPE is coerced to the default rather than stringified.
     * org.json's own optString turns a JSON null into the four characters
     * "null", which would print as a title; the desktop's `_note_str`
     * refuses anything that is not a string, and so does this.
     */
    private fun JSONObject.str(key: String, fallback: String = ""): String {
        val v = opt(key)
        return if (v is String) v else fallback
    }

    private fun JSONObject.intOr(key: String, fallback: Int): Int {
        val v = opt(key)
        return if (v is Number) v.toInt() else fallback
    }

    private fun JSONObject.arrayLength(key: String): Int =
        (opt(key) as? JSONArray)?.length() ?: 0
}
