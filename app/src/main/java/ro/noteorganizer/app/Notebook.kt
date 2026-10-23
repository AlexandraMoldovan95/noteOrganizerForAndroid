package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val attachments: Int = 0,
    val formatting: List<FormatSpan> = emptyList(),
    val paragraphs: Int = 0,
    val checklist: List<ChecklistItem> = emptyList(),
    val recurrenceFreq: String = "",
    val recurrenceInterval: Int = 1,
    /** The rule in words, as the desktop's card says it: "Every 2 weeks on Mon, Thu". */
    val recurrenceLabel: String = "",
    val exceptions: Int = 0,
    val overrides: Int = 0,
    /** Only a trashed note carries this: "YYYY-MM-DD HH:MM:SS". */
    val deletedAt: String = "",
    /**
     * The note's identity, as opposed to [id], which is only its place in
     * THIS file. Two devices both hand a new note `max + 1`, so both
     * invent 87 and nothing can tell the two apart afterwards.
     *
     * Empty for a note written before the desktop app learned to mint
     * these -- and left empty ON PURPOSE. The desktop fills them in at
     * its own door, for every note, once; if this app filled them in too,
     * a notebook copied here before the desktop had seen it would end up
     * with two different identities for each note, which is the exact
     * problem the field exists to prevent.
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
    /** Whether restoring that copy would keep what the note holds now ([Notebook.replacedByRestore]). */
    val previousKeeps: Boolean = false,
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
     * Everything this note can be found by, lowercased, computed once.
     *
     * Once, because the alternative is stripping a 107,000-character
     * body's table plumbing on every keystroke for every note. The
     * desktop solves the same cost by debouncing the search box; doing
     * the work a single time is better than doing it later.
     */
    private val searchText: String by lazy {
        val bodyText = if (body.contains('|')) contentText(body) else body
        fold(title + "\n" + bodyText + "\n" + comments)
    }

    /**
     * The ONE definition of "this note matches the search".
     *
     * The desktop learned this the hard way: the list render and "select
     * all visible" each carried a hand-rolled copy, the copies drifted,
     * and "select all" quietly selected notes the filter had hidden --
     * then bulk actions hit them. One definition, every caller.
     *
     * Title, body and comments. Not the category, not the checklist --
     * matching the desktop rather than improving on it, because a
     * difference here is a surprise, not a feature.
     */
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        return searchText.contains(fold(query))
    }

    /** What the desktop's list shows for a note with no title. */
    val displayTitle: String get() = title.trim().ifEmpty { "(untitled)" }

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
            t.isNotEmpty() -> t
            spokenLines.isNotEmpty() -> glimpse(spokenLines.first())
            // Words only in a table: the note is not empty, it is untitled.
            body.isNotBlank() -> "(untitled)"
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
            if (exceptions > 0) parts.append("   ✕").append(exceptions)
            if (overrides > 0) parts.append("   ✎").append(overrides)
        } else if (date.isNotEmpty()) {
            parts.append("   📅 ").append(Recurrence.showDate(date))
        }
        if (comments.isNotEmpty()) parts.append("   💬")
        if (attachments > 0) parts.append("   📎×").append(attachments)
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
 * Folds a string so that `si` finds `și` and `masina` finds `mașină`.
 *
 * **This is the first deliberate difference from the desktop**, which
 * compares the letters as typed. It is a one-way widening — a folded
 * search finds everything the strict one would and more — so it can
 * surprise by finding too much, never by finding too little.
 *
 * The reason is the language. Romanian diacritics are routinely dropped
 * when typing quickly, especially on a phone keyboard, and a notebook
 * written half with them and half without is unsearchable under a strict
 * rule. It is worth porting back to the desktop rather than leaving the
 * two apps disagreeing.
 *
 * Decomposing and dropping the combining marks handles the whole set at
 * once: ă â î ș ț, and the cedilla spellings ş ţ that older Windows
 * keyboards produce, which are a different character from the comma-below
 * ones and would otherwise not match them either.
 */
fun fold(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    return COMBINING_MARKS.replace(decomposed, "").lowercase()
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

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
        SortOrder.TITLE_AZ -> notes.sortedWith(
            compareBy({ it.title.isBlank() }, { it.title.lowercase() }))
        SortOrder.TITLE_ZA -> notes.sortedWith(
            compareBy<Note> { it.title.isBlank() }
                .thenByDescending { it.title.lowercase() })
        SortOrder.CATEGORY -> notes.sortedWith(
            compareBy({ it.category }, { it.updated }))
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
            notes.sortedWith(Comparator { a, b ->
                val (ga, da) = group(a)
                val (gb, db) = group(b)
                when {
                    ga != gb -> ga.compareTo(gb)
                    ga == 1 -> db.compareTo(da)
                    else -> da.compareTo(db)
                }
            })
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
        FILES -> note.attachments > 0
        else -> note.category == selected
    }

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
}

/** What a save did, or the reason it did not. Never silence. */
sealed class SaveResult {
    data class Ok(
        val id: Int,
        val lostStyles: Int = 0,
        /** Only an import fills this: how many notes arrived. */
        val imported: Int = 0,
    ) : SaveResult()

    data class Failed(val reason: String) : SaveResult()
}

/** The note's own text split into code points, which is how Python counts. */
internal fun codePointsOf(text: String): IntArray {
    val out = ArrayList<Int>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        out.add(cp)
        i += Character.charCount(cp)
    }
    return out.toIntArray()
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

    var prefix = 0
    while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++

    var suffix = 0
    while (suffix < a.size - prefix && suffix < b.size - prefix &&
        a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
    ) suffix++

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

    var prefix = 0
    while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++

    var suffix = 0
    while (suffix < a.size - prefix && suffix < b.size - prefix &&
        a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
    ) suffix++

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
 * only opens with one. Mirrors `is_table_row` in core/tables.py.
 */
internal fun isTableRow(line: String): Boolean {
    val s = line.trim()
    if (s.length < 3 || !s.startsWith("|") || !s.endsWith("|")) return false
    return splitRow(s).size >= 2
}

/**
 * How much of a note's text a card is given: more than its two lines can
 * show on the widest pane, far less than a long note holds. The cut is
 * never seen -- the card's own ellipsis comes first.
 */
private const val GLIMPSE = 300

/** [text] cut to [GLIMPSE] characters, never through the middle of an emoji. */
private fun glimpse(text: String): String {
    if (text.length <= GLIMPSE) return text
    val end = if (Character.isLowSurrogate(text[GLIMPSE])) GLIMPSE - 1 else GLIMPSE
    return text.substring(0, end)
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

/** A cell holding exactly one of these is a merge marker, not content. */
private const val MERGE_LEFT = "<"
private const val MERGE_UP = "^"

/** The marker a cell carries instead of text when its column is hidden. */
private const val SEPARATOR_HIDDEN = "~"

/** Inside a cell, this stands for the line break the table draws. */
private const val LINE_BREAK = "<br>"

/**
 * None when the cell is not a checkbox; false unchecked, true checked.
 * A capital X is tolerated, which hand-written markdown uses.
 */
fun checkboxState(value: String): Boolean? {
    val v = value.trim()
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
    val s = line.trim()
    if (!s.startsWith("|")) return emptyList()
    val parts = s.split("|")
    if (parts.size < 2) return emptyList()
    // First and last are what sits outside the outer pipes: nothing.
    return parts.subList(1, parts.size - 1).map { it.trim() }
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
    )

    /**
     * How many days this entry has left before the DESKTOP purges it.
     *
     * An unreadable stamp counts as "keep it", never as "it is due" —
     * the desktop's fail-safe, and the only sane direction for a guess
     * about deleting someone's note. Null means no answer.
     */
    fun daysLeftInTrash(deletedAt: String, retentionDays: Int): Int? {
        val when_ = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(deletedAt)
        } catch (e: Exception) {
            null
        } ?: return null
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
            JSONArray(file.readText(Charsets.UTF_8))
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
            "was deleted: the file was kept as ${kept.name}, beside the notes.")
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

        // Read the highest id from the RAW array: a note the model skipped
        // still owns its id, and reusing it would merge two notes.
        var maxId = 0
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val id = o.opt("id")
            if (id is Number && id.toInt() > maxId) maxId = id.toInt()
        }
        val newId = maxId + 1

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
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
     * when the edit removes a lot of text.
     *
     * That last guard is the desktop's, thresholds included: emptied
     * completely, or more than 40 characters gone AND either under 70% of
     * what was there or 200+ characters removed. Its comment explains the
     * shape — a 17-character shopping list cleared by accident slipped
     * under every threshold that only looked at proportions.
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
    ): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)

        val note = findById(array, id)
            ?: return SaveResult.Failed(GONE)

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
        // note rather than its text with someone else's styling.
        if (shouldKeepPrevious(oldTitle, oldBody, newTitle, newBody)) {
            val previous = JSONObject()
            previous.put("title", oldTitle)
            previous.put("body", oldBody)
            previous.put("formatting", note.opt("formatting") ?: JSONArray())
            previous.put("paragraphs", note.opt("paragraphs") ?: JSONArray())
            previous.put("comments", note.opt("comments") ?: "")
            note.put("_prev", previous)
        }

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
            note.put("updated",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        }
        // `created` is deliberately untouched: an edit is not a birth.
        dropSpentCopy(note)

        val problem = writeAtomic(file, array)
        if (problem != null) return SaveResult.Failed(problem)
        return SaveResult.Ok(id, moved.lost)
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
        val note = findById(array, id)
            ?: return SaveResult.Failed(GONE)

        val checklist = note.opt("checklist") as? JSONArray
            ?: return SaveResult.Failed("This note has no checklist.")
        val item = checklist.opt(index) as? JSONObject
            ?: return SaveResult.Failed(ROW_GONE)
        if (item.opt("done") == done) return SaveResult.Ok(id)

        item.put("done", done)
        note.put("updated",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        val problem = writeAtomic(file, array)
        if (problem != null) {
            item.put("done", !done)          // put the screen back in step
            return SaveResult.Failed(problem)
        }
        return SaveResult.Ok(id)
    }

    /**
     * Moves a note to the Trash. Nothing is destroyed.
     *
     * **The order of the two writes is the whole safety argument.** A
     * delete touches two files, and a phone can be killed between them.
     * The trash copy is written FIRST: interrupted there, the note is
     * still in the notebook and nothing happened. Interrupted after it,
     * the note exists in BOTH files — a duplicate, which is visible and
     * fixable. Written the other way round, the same interruption would
     * leave the note in neither, which is the one outcome that cannot be
     * undone. The desktop writes them in this order too.
     *
     * If the trash write fails, the notebook is not touched at all.
     */
    fun deleteNote(load: Load, id: Int): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)
        val trashFile = load.trashFile
            ?: return SaveResult.Failed("There is nowhere to keep the Trash.")
        if (trashFile.exists() && load.trashRaw == null) {
            return SaveResult.Failed("The Trash could not be read, so nothing was deleted.")
        }

        var index = -1
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() == id) {
                index = i
                break
            }
        }
        if (index < 0) return SaveResult.Failed(GONE)
        val note = array.get(index) as JSONObject

        // A deep copy, so a failure below leaves the original note whole.
        val entry = JSONObject(note.toString())
        entry.put("deleted_at",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        // Each write is prepared as a CANDIDATE array and the shared one is
        // only changed once the bytes are on the disk. Undoing a failed
        // change in place is where the bugs live: JSONArray.remove shifts
        // everything left, so putting the note back at its old index
        // overwrites whatever slid into that slot -- a rollback that
        // deletes a different note.
        val trash = load.trashRaw ?: JSONArray()
        val trashCandidate = copyOf(trash)
        trashCandidate.put(entry)
        val trashProblem = writeAtomic(trashFile, trashCandidate)
        if (trashProblem != null) {
            return SaveResult.Failed("The Trash could not be written ($trashProblem), " +
                "so nothing was deleted.")
        }
        trash.put(entry)

        val candidate = copyWithout(array, index)
        val problem = writeAtomic(file, candidate)
        if (problem != null) {
            // The copy in the trash stays. Say so plainly -- a duplicate the
            // user can see beats a silent half-delete.
            return SaveResult.Failed(
                "$problem — the note is in the Trash and still in the list; nothing was lost.")
        }
        array.remove(index)
        return SaveResult.Ok(id)
    }

    private fun copyOf(array: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until array.length()) out.put(array.get(i))
        return out
    }

    private fun copyWithout(array: JSONArray, index: Int): JSONArray {
        val out = JSONArray()
        for (i in 0 until array.length()) if (i != index) out.put(array.get(i))
        return out
    }

    /**
     * Brings a trashed note back.
     *
     * Same reasoning, mirrored: the notebook is written FIRST, so an
     * interruption leaves the note in both places rather than in neither.
     *
     * The restored note gets a FRESH id. Its old one may have been handed
     * to a note created after the delete, and two notes with one id is a
     * corruption that only shows up much later.
     */
    fun restoreFromTrash(load: Load, id: Int): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)
        val trashFile = load.trashFile
            ?: return SaveResult.Failed("There is nowhere to keep the Trash.")
        val trash = load.trashRaw ?: return SaveResult.Failed("The Trash could not be read.")

        var index = -1
        for (i in 0 until trash.length()) {
            val o = trash.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() == id) {
                index = i
                break
            }
        }
        if (index < 0) return SaveResult.Failed("This note is no longer in the Trash.")
        val entry = trash.get(index) as JSONObject

        val restored = JSONObject(entry.toString())
        restored.remove("deleted_at")
        var maxId = 0
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() > maxId) maxId = itsId.toInt()
        }
        val newId = maxId + 1
        restored.put("id", newId)

        val candidate = copyOf(array)
        candidate.put(restored)
        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        array.put(restored)

        val trashCandidate = copyWithout(trash, index)
        val trashProblem = writeAtomic(trashFile, trashCandidate)
        if (trashProblem != null) {
            return SaveResult.Failed(
                "$trashProblem — the note is restored, and is still in the Trash too.")
        }
        trash.remove(index)
        return SaveResult.Ok(newId)
    }

    /**
     * The desktop's ceiling. Not a performance budget — an anti-bomb
     * limit: a pasted share code is saved before it is ever drawn, so an
     * unbounded list could make the app fail to open on every launch
     * afterwards. That happened, twice, with real fixes behind it.
     */
    const val MAX_CHECKLIST_ITEMS = 1000

    /** Adds a row to the end of a note's checklist. */
    fun addChecklistItem(load: Load, id: Int, text: String): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (checklist.length() >= MAX_CHECKLIST_ITEMS) {
                return@editChecklist "A checklist holds at most $MAX_CHECKLIST_ITEMS items."
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
        val note = findById(array, id)
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
        note.put("updated",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        val problem = writeAtomic(file, array)
        if (problem != null) {
            if (before != null) note.put("checklist", before) else note.remove("checklist")
            if (stamp != null) note.put("updated", stamp)
            return SaveResult.Failed(problem)
        }
        return SaveResult.Ok(id)
    }

    /**
     * By id, never by position: the list on screen is filtered and sorted,
     * so an index taken from it means nothing in the file.
     */
    private fun findById(array: JSONArray, id: Int): JSONObject? {
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() == id) return o
        }
        return null
    }

    /**
     * Replaces one table inside a note's body.
     *
     * Deliberately a thin wrapper over [updateNote] rather than its own
     * write: a table's lines change length, so every formatting span and
     * every bullet marker after it slides, and that is exactly what
     * `updateNote` already knows how to do. A second copy of that logic
     * is how the desktop's search rules drifted apart.
     */
    fun updateTable(
        load: Load,
        id: Int,
        blockIndex: Int,
        newLines: List<String>,
    ): SaveResult {
        val array = load.raw
            ?: return SaveResult.Failed(UNREADABLE)
        val note = findById(array, id)
            ?: return SaveResult.Failed(GONE)
        val body = (note.opt("body") as? String) ?: ""
        val blocks = tableBlocks(body)
        if (blockIndex !in blocks.indices) {
            return SaveResult.Failed("That table is no longer there.")
        }
        val title = (note.opt("title") as? String) ?: ""
        return updateNote(load, id, title, replaceTable(body, blocks[blockIndex], newLines))
    }

    /**
     * A share code for one note, taken from the array as it sits on disk
     * rather than from the [Note] model -- the model drops fields a code
     * is supposed to carry.
     */
    fun shareCodeFor(load: Load, id: Int): String? {
        val array = load.raw ?: return null
        val note = findById(array, id) ?: return null
        return Sharing.encode(listOf(note))
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
    fun inspectShareCode(load: Load, code: String): Inspection {
        val decoded = Sharing.decode(code)
        if (decoded is Sharing.Decoded.Failed) return Inspection.Failed(decoded.reason)
        val notes = (decoded as Sharing.Decoded.Notes).notes
        val plan = Importing.plan(load.raw ?: JSONArray(), notes, Importing.Source.CODE, load.categories.keys)
        return Inspection.Ready(notes, plan.differing.size + plan.unchanged.size, plan)
    }

    /**
     * Writes a code's notes. [update] answers the one question the old
     * panel asked: true replaces the notes this device already has, false
     * adds them beside the originals without their identity. Notes nothing
     * here matches are added either way. The screen now asks every
     * question through [Importing]; this is that route with one answer.
     */
    fun applyShareCode(
        load: Load,
        notes: List<JSONObject>,
        update: Boolean,
    ): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        if (file.exists() && load.raw == null) return SaveResult.Failed(UNREADABLE)
        val plan = Importing.plan(load.raw ?: JSONArray(), notes, Importing.Source.CODE)
        val answers = Importing.Answers(update = update, copyUnchanged = !update)
        return when (val result = Importing.apply(load, plan, answers, attachmentsDir = null)) {
            is Importing.Result.Failed -> SaveResult.Failed(result.reason)
            is Importing.Result.Done ->
                SaveResult.Ok(result.outcome.firstId,
                              imported = result.outcome.added + result.outcome.updated)
        }
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
        val mine = Normalize.normalizeNote(note)
        val theirs = Normalize.normalizeNote(incoming)
        return fields.any { !sameJson(shaped(mine, it), shaped(theirs, it)) }
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
        note.put("_prev", previous)
        for (field in fields) note.put(field, deepCopy(shaped(incoming, field)))
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
        val same = PREV_TEXT_FIELDS.all { sameJson(shaped(note, it), shaped(previous, it)) } &&
            PREV_EXTRA_LABELS.all { (field, _) -> !previous.has(field) || sameJson(shaped(note, field), shaped(previous, field)) }
        if (same) note.remove("_prev")
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
        val nothingLost = (previous.opt("body") as? String).orEmpty().contains((live.opt("body") as? String).orEmpty().trim()) &&
            (wasTitle.isBlank() || wasTitle == (previous.opt("title") as? String).orEmpty()) &&
            PREV_EXTRA_LABELS.all { (field, _) -> !previous.has(field) || sameJson(shaped(live, field), shaped(previous, field)) }
        if (nothingLost) return null
        val replaced = JSONObject()
        for (field in PREV_TEXT_FIELDS) replaced.put(field, deepCopy(shaped(live, field)))
        for ((field, _) in PREV_EXTRA_LABELS) {
            if (previous.has(field)) replaced.put(field, deepCopy(shaped(live, field)))
        }
        return replaced
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
        val index = indexById(array, id)
        if (index < 0) return SaveResult.Failed(GONE)
        val live = array.get(index) as JSONObject
        val previous = live.opt("_prev") as? JSONObject
            ?: return SaveResult.Failed("This note has no earlier version to bring back.")
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
        if (replaced == null) note.remove("_prev") else note.put("_prev", replaced)
        note.put("updated", Files.now())
        val candidate = copyOf(array)
        candidate.put(index, note)
        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        array.put(index, note)
        return SaveResult.Ok(id)
    }

    private fun indexById(array: JSONArray, id: Int): Int {
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() == id) return i
        }
        return -1
    }

    /** The desktop's rule for when an edit deserves a rescue copy. */
    internal fun shouldKeepPrevious(
        oldTitle: String, oldBody: String, newTitle: String, newBody: String,
    ): Boolean {
        val oldLen = oldTitle.length + oldBody.length
        val newLen = newTitle.length + newBody.length
        val removed = oldLen - newLen
        if (oldLen > 0 && newLen == 0) return true          // emptied
        val bigFraction = oldLen > 0 && newLen < oldLen * 0.7
        val bigAbsolute = removed >= 200
        return removed > 40 && (bigFraction || bigAbsolute)
    }

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
            attachments = o.arrayLength("attachments"),
            formatting = readSpans(o.opt("formatting") as? JSONArray),
            paragraphs = o.arrayLength("paragraphs"),
            checklist = readChecklist(o.opt("checklist") as? JSONArray),
            recurrenceFreq = rule?.str("freq") ?: "",
            recurrenceInterval = rule?.intOr("interval", 1) ?: 1,
            recurrenceLabel = Recurrence.describe(o),
            exceptions = o.arrayLength("exceptions"),
            overrides = (o.opt("overrides") as? JSONObject)?.length() ?: 0,
            deletedAt = o.str("deleted_at"),
            uid = o.str("uid"),
            hasPrevious = o.opt("_prev") is JSONObject,
            previousAlso = (o.opt("_prev") as? JSONObject)?.let { prev ->
                PREV_EXTRA_LABELS.filter { (key, _) -> prev.has(key) }.map { it.second }
            } ?: emptyList(),
            previousKeeps = (o.opt("_prev") as? JSONObject)?.let { replacedByRestore(o, it) != null } ?: false,
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
