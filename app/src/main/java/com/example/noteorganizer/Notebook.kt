package com.example.noteorganizer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    val pinned: Boolean = false,
    val comments: String = "",
    val attachments: Int = 0,
    val formatting: List<FormatSpan> = emptyList(),
    val paragraphs: Int = 0,
    val checklist: List<ChecklistItem> = emptyList(),
    val recurrenceFreq: String = "",
    val recurrenceInterval: Int = 1,
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

    /** The desktop card's preview: 40 characters, newlines flattened. */
    val preview: String
        get() {
            val flat = body.replace('\n', ' ').trim()
            return if (flat.length > 40) flat.take(40) + "..." else flat
        }

    /**
     * Body lines worth previewing: blanks dropped, and table rows dropped
     * with them.
     *
     * Table rows read as pipe soup at card size, which is why the desktop
     * strips them from its own preview and says "a table is here" on the
     * tags line instead. Skipping this was visible on the first
     * screenshot: 24 of a real notebook's 77 notes hold a table, so it is
     * not a rare case that could wait.
     */
    private val spokenLines: List<String>
        get() = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isTableRow(it) }
            .toList()

    /** How many table blocks the body holds -- runs of adjacent rows. */
    val tableCount: Int
        get() {
            var blocks = 0
            var inBlock = false
            for (line in body.lineSequence()) {
                if (isTableRow(line.trim())) {
                    if (!inBlock) blocks++
                    inBlock = true
                } else {
                    inBlock = false
                }
            }
            return blocks
        }

    /**
     * The phone's headline. Measured against a real notebook, 48 of 77
     * notes have no title and 15 more have one of three characters or
     * fewer, so leading with the title puts "(untitled)" in the largest
     * type on two cards out of three. This falls through to the first
     * line the note actually says.
     */
    val headline: String
        get() {
            val t = title.trim()
            if (t.isNotEmpty()) return t
            return spokenLines.firstOrNull() ?: "(goala)"
        }

    /** Whether `headline` came from the body rather than from a real title. */
    val headlineIsBorrowed: Boolean get() = title.trim().isEmpty()

    /** The rest of the note, after whatever the headline already used. */
    val snippet: String
        get() {
            val rest = if (headlineIsBorrowed) spokenLines.drop(1) else spokenLines
            return rest.joinToString(" ")
        }

    /**
     * The desktop card's third line, rebuilt in the same order:
     * category, then the repeat rule or the date, then the badges.
     */
    fun tagLine(): String {
        val parts = StringBuilder(category)
        if (isRecurring) {
            parts.append("   🔁 ").append(describeRecurrence())
            if (exceptions > 0) parts.append("   ✕").append(exceptions)
            if (overrides > 0) parts.append("   ✎").append(overrides)
        } else if (date.isNotEmpty()) {
            parts.append("   📅 ").append(date)
        }
        if (comments.isNotEmpty()) parts.append("   💬")
        if (attachments > 0) parts.append("   📎×").append(attachments)
        val tables = tableCount
        if (tables > 0) {
            parts.append(if (tables == 1) "   ▦ 1 tabel" else "   ▦ $tables tabele")
        }
        if (checklistTotal > 0) {
            parts.append("   ☑ ").append(checklistDone).append('/').append(checklistTotal)
        }
        return parts.toString()
    }

    /**
     * Deliberately shorter than the desktop's `describe_recurrence`, which
     * spells out weekdays, month-day policies and end conditions. This is
     * the smallest app; the full sentence is a decision for later, not an
     * omission that slipped through.
     */
    private fun describeRecurrence(): String =
        if (recurrenceInterval > 1) "$recurrenceFreq ×$recurrenceInterval"
        else recurrenceFreq
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

/** How the list is ordered. The desktop's options, same rules. */
enum class SortOrder(val label: String) {
    UPDATED_NEW("Recent modificate"),
    UPDATED_OLD("Cele mai vechi"),
    TITLE_AZ("Titlu A–Z"),
    TITLE_ZA("Titlu Z–A"),
    CATEGORY("Categorie"),
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
fun sortNotes(notes: List<Note>, order: SortOrder): List<Note> {
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
    }
    return sorted.sortedBy { !it.pinned }
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
            // A line inside the edited region: whatever it marked may no
            // longer exist. Dropping the marker loses a bullet; keeping it
            // puts a bullet on a stranger's line.
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

    /** Starting categories, from core/themes.py's DEFAULT_CATEGORY_COLORS. */
    val DEFAULT_CATEGORY_COLORS = mapOf(
        "General" to "#7c5cff",
        "Work" to "#4cc9f0",
        "Personal" to "#f9c74f",
    )

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
        val retentionDays: Int = TRASH_RETENTION_DAYS,
    )

    /** The desktop's default window, and its clamp on a hand-edited one. */
    const val TRASH_RETENTION_DAYS = 30

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
     * Looks for the notebook in the app's own external folder, which `adb
     * push` can write to without any permission and without root. The
     * internal folder is checked second so a future in-app import lands
     * somewhere the user's file manager cannot reach.
     */
    fun load(dirs: List<File>): Load {
        for (dir in dirs) {
            val file = File(dir, "notes_data.json")
            if (!file.isFile) continue
            val text = try {
                file.readText()
            } catch (e: Exception) {
                return Load(emptyList(), emptyMap(), file.path, "nu s-a putut citi: ${e.message}")
            }
            val raw = try {
                JSONArray(text)
            } catch (e: Exception) {
                null
            }
            val notes = parseNotes(text)
            val settings = File(dir, "settings.json")
            val settingsText = if (settings.isFile) {
                try { settings.readText() } catch (e: Exception) { "" }
            } else {
                ""
            }
            val categories = parseCategories(settingsText)

            val trashFile = File(dir, "trash.json")
            val trashText = if (trashFile.isFile) {
                try { trashFile.readText() } catch (e: Exception) { "" }
            } else {
                ""
            }
            val trashRaw = try { JSONArray(trashText) } catch (e: Exception) { null }
            val problem = if (notes.isEmpty()) {
                "fisierul exista (${file.length()} octeti) dar nu a dat nicio notita"
            } else {
                null
            }
            return Load(
                notes = notes,
                categories = DEFAULT_CATEGORY_COLORS + categories,
                source = file.path,
                problem = problem,
                file = file,
                raw = raw,
                trash = parseNotes(trashText),
                trashFile = trashFile,
                trashRaw = trashRaw,
                retentionDays = parseRetentionDays(settingsText),
            )
        }
        // No notebook here yet. Still name where one would go, so a first
        // captured note can create the file instead of having nowhere to
        // land -- a fresh phone must be able to take a note.
        val destination = dirs.firstOrNull()?.let { File(it, "notes_data.json") }
        return Load(
            emptyList(), DEFAULT_CATEGORY_COLORS,
            destination?.path ?: "?",
            "nu exista inca un fisier de notite aici",
            destination,
            null,
        )
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
    ): SaveResult {
        if (date.isNotBlank() && !isDate(date)) {
            return SaveResult.Failed("data trebuie scrisa ca 2026-09-12")
        }
        val file = load.file
            ?: return SaveResult.Failed("nu stiu unde sa scriu fisierul de notite")
        if (file.exists() && load.raw == null) {
            return SaveResult.Failed(
                "fisierul exista dar nu s-a putut citi -- nu scriu peste el")
        }
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
        note.put("comments", "")
        note.put("category", category.trim().ifEmpty { "General" })
        note.put("date", date.trim())
        note.put("color", sanitizeColor(color))
        note.put("attachments", JSONArray())
        note.put("updated", now)
        note.put("created", now)
        note.put("recurrence", defaultRecurrence())
        note.put("exceptions", JSONArray())
        note.put("overrides", JSONObject())
        note.put("pinned", pinned)
        note.put("checklist", JSONArray())
        note.put("paragraphs", JSONArray())

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
    ): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed("nu stiu unde e fisierul de notite")
        val array = load.raw
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi -- nu scriu peste el")

        val note = findById(array, id)
            ?: return SaveResult.Failed("notita nu mai exista in fisier")

        val oldTitle = (note.opt("title") as? String) ?: ""
        val oldBody = (note.opt("body") as? String) ?: ""
        val newTitle = mendSurrogates(title)
        val newBody = mendSurrogates(body)

        // A date is refused rather than silently dropped: the user typed
        // it, so a wrong one is worth saying out loud.
        if (date != null && date.isNotBlank() && !isDate(date)) {
            return SaveResult.Failed("data trebuie scrisa ca 2026-09-12")
        }
        // The coupling that is easy to miss: `normalize_note` THROWS AWAY a
        // recurrence rule whose note has no valid date. Clearing the date
        // of a repeating note here would look harmless and would delete
        // the repeat on the desktop's next read.
        val repeats = ((note.opt("recurrence") as? JSONObject)?.opt("freq") as? String)
            ?.isNotEmpty() == true
        if (repeats && date != null && date.isBlank()) {
            return SaveResult.Failed(
                "notita se repeta — stergerea datei i-ar sterge si repetarea")
        }

        val spans = readSpans(note.opt("formatting") as? JSONArray)
        val metaChanged = (formatting != null && formatting != spans) ||
            (category != null && category != note.opt("category")) ||
            (color != null && sanitizeColor(color) != (note.opt("color") ?: "")) ||
            (date != null && date.trim() != (note.opt("date") ?: "")) ||
            (pinned != null && pinned != (note.opt("pinned") == true))
        if (oldTitle == newTitle && oldBody == newBody && !metaChanged) {
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
        note.put("paragraphs", movedParagraphs(note, oldBody, newBody))
        if (category != null) {
            note.put("category", category.trim().ifEmpty { "General" })
        }
        if (color != null) note.put("color", sanitizeColor(color))
        if (date != null) note.put("date", date.trim())
        if (pinned != null) note.put("pinned", pinned)
        note.put("updated",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        // `created` is deliberately untouched: an edit is not a birth.

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
            ?: return SaveResult.Failed("nu stiu unde e fisierul de notite")
        val array = load.raw
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi -- nu scriu peste el")
        val note = findById(array, id)
            ?: return SaveResult.Failed("notita nu mai exista in fisier")

        val checklist = note.opt("checklist") as? JSONArray
            ?: return SaveResult.Failed("notita nu are checklist")
        val item = checklist.opt(index) as? JSONObject
            ?: return SaveResult.Failed("randul nu mai exista")
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
            ?: return SaveResult.Failed("nu stiu unde e fisierul de notite")
        val array = load.raw
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi -- nu sterg nimic")
        val trashFile = load.trashFile
            ?: return SaveResult.Failed("nu stiu unde e cosul")
        if (trashFile.exists() && load.trashRaw == null) {
            return SaveResult.Failed("cosul exista dar nu s-a putut citi -- nu sterg nimic")
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
        if (index < 0) return SaveResult.Failed("notita nu mai exista in fisier")
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
            return SaveResult.Failed("nu am putut scrie in cos: $trashProblem")
        }
        trash.put(entry)

        val candidate = copyWithout(array, index)
        val problem = writeAtomic(file, candidate)
        if (problem != null) {
            // The copy in the trash stays. Say so plainly -- a duplicate the
            // user can see beats a silent half-delete.
            return SaveResult.Failed(
                "$problem — notita e in cos dar si in lista; nu s-a pierdut nimic")
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
            ?: return SaveResult.Failed("nu stiu unde e fisierul de notite")
        val array = load.raw
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi")
        val trashFile = load.trashFile ?: return SaveResult.Failed("nu stiu unde e cosul")
        val trash = load.trashRaw ?: return SaveResult.Failed("cosul nu s-a putut citi")

        var index = -1
        for (i in 0 until trash.length()) {
            val o = trash.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() == id) {
                index = i
                break
            }
        }
        if (index < 0) return SaveResult.Failed("notita nu mai e in cos")
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
                "$trashProblem — notita e restaurata dar a ramas si in cos")
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
                return@editChecklist "checklistul a atins limita de $MAX_CHECKLIST_ITEMS randuri"
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
                ?: return@editChecklist "randul nu mai exista"
            // In place, so a field this app does not model survives a rename.
            item.put("text", mendSurrogates(text))
            null
        }
    }

    fun removeChecklistItem(load: Load, id: Int, index: Int): SaveResult {
        return editChecklist(load, id) { checklist ->
            if (index < 0 || index >= checklist.length()) {
                return@editChecklist "randul nu mai exista"
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
            ?: return SaveResult.Failed("nu stiu unde e fisierul de notite")
        val array = load.raw
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi -- nu scriu peste el")
        val note = findById(array, id)
            ?: return SaveResult.Failed("notita nu mai exista in fisier")

        val current = note.opt("checklist") as? JSONArray ?: JSONArray()
        // A DEEP copy. A shallow one shares each row object with the
        // original, so renaming a row in the "candidate" would rename it
        // in the real note too -- and the rollback below would restore
        // the same, already-changed object.
        val candidate = try {
            JSONArray(current.toString())
        } catch (e: Exception) {
            return SaveResult.Failed("checklistul nu s-a putut citi")
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
            ?: return SaveResult.Failed("notebook-ul nu s-a putut citi")
        val note = findById(array, id)
            ?: return SaveResult.Failed("notita nu mai exista in fisier")
        val body = (note.opt("body") as? String) ?: ""
        val blocks = tableBlocks(body)
        if (blockIndex !in blocks.indices) {
            return SaveResult.Failed("tabelul nu mai e acolo")
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
     * Creates notes from a pasted share code.
     *
     * Every note is CREATED, never matched against an existing one: a code
     * carries no id, and guessing which note it "is" would be the merge
     * problem in disguise. Importing twice gives two notes, which is
     * visible and fixable; importing over the wrong note would not be.
     */
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
        ) : Inspection()
    }

    fun inspectShareCode(load: Load, code: String): Inspection {
        val decoded = Sharing.decode(code)
        if (decoded is Sharing.Decoded.Failed) return Inspection.Failed(decoded.reason)
        val notes = (decoded as Sharing.Decoded.Notes).notes
        val array = load.raw
        var known = 0
        if (array != null) {
            for (note in notes) {
                if (findByUid(array, note.opt("uid") as? String) != null) known += 1
            }
        }
        return Inspection.Ready(notes, known)
    }

    /**
     * The note that IS this one, or null.
     *
     * Identity, not resemblance: matching on the text would say "new
     * note" exactly when a note has been edited, which is the only time
     * anyone sends one back.
     */
    private fun findByUid(array: JSONArray, uid: String?): JSONObject? {
        val at = indexByUid(array, uid)
        return if (at < 0) null else array.opt(at) as? JSONObject
    }

    /**
     * Where that note sits, or -1.
     *
     * The position, not just the object, because a write replaces the
     * entry rather than editing it -- see [applyShareCode].
     */
    private fun indexByUid(array: JSONArray, uid: String?): Int {
        val wanted = uid?.trim()
        // An empty identity is not an identity. Notes that reached this
        // device before the desktop minted any carry none, and letting
        // "" match "" would make every one of them the same note.
        if (wanted.isNullOrEmpty()) return -1
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            if ((o.opt("uid") as? String)?.trim() == wanted) return i
        }
        return -1
    }

    /**
     * Writes an inspected code's notes.
     *
     * [update] decides what happens to the ones this device already has:
     * true replaces their content in place, false adds them beside the
     * originals with fresh identities. Notes nothing here matches are
     * always created either way.
     */
    fun applyShareCode(
        load: Load,
        notes: List<JSONObject>,
        update: Boolean,
    ): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed("nu stiu unde sa scriu fisierul de notite")
        if (file.exists() && load.raw == null) {
            return SaveResult.Failed(
                "fisierul exista dar nu s-a putut citi -- nu scriu peste el")
        }
        val array = load.raw ?: JSONArray()
        val candidate = copyOf(array)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        var maxId = 0
        for (i in 0 until candidate.length()) {
            val o = candidate.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() > maxId) maxId = itsId.toInt()
        }

        var created = 0
        var updated = 0
        var firstId = 0
        for (shared in notes) {
            val at = indexByUid(candidate, shared.opt("uid") as? String)
            if (at >= 0 && update) {
                // The candidate array holds the SAME note objects as the
                // live one -- copying an array copies references. So the
                // update is made on a copy that replaces the entry here;
                // editing the note in place would change what the screen
                // shows before the write has had a chance to fail.
                val mine = JSONObject((candidate.opt(at) as JSONObject).toString())
                applySharedUpdate(mine, shared, now)
                candidate.put(at, mine)
                updated += 1
                val itsId = mine.opt("id")
                if (firstId == 0 && itsId is Number) firstId = itsId.toInt()
                continue
            }
            val note = JSONObject(shared.toString())
            // A copy the user asked to keep SEPARATE must not wear the
            // identity of the note it came from: one note with two ids is
            // the collision the field exists to prevent.
            if (at >= 0) note.remove("uid")
            maxId += 1
            if (firstId == 0) firstId = maxId
            note.put("id", maxId)
            fillNewNoteDefaults(note, now)
            candidate.put(note)
            created += 1
        }

        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        // Only now does the array the screen reads change.
        load.raw?.let { live ->
            while (live.length() > 0) live.remove(live.length() - 1)
            for (i in 0 until candidate.length()) live.put(candidate.get(i))
        }
        return SaveResult.Ok(firstId, imported = created + updated)
    }

    /**
     * Overwrite a note's content from an incoming copy, keeping a way back.
     *
     * The rescue copy goes into `_prev`, which is the desktop's own
     * one-step undo -- the editor there reads it for "↩ Restore
     * previous". Writing it means an update made by mistake is one button
     * away from being undone, on the machine that has the button.
     */
    private fun applySharedUpdate(note: JSONObject, incoming: JSONObject, now: String) {
        val previous = JSONObject()
        previous.put("title", note.opt("title") ?: "")
        previous.put("body", note.opt("body") ?: "")
        previous.put("formatting", note.opt("formatting") ?: JSONArray())
        previous.put("paragraphs", note.opt("paragraphs") ?: JSONArray())
        previous.put("comments", note.opt("comments") ?: "")
        note.put("_prev", previous)
        // The same fields the desktop's UPDATABLE_FROM_SHARED replaces --
        // never the id, the attachments (a code carries none) or the
        // per-occurrence maps, which belong to this device's copy.
        for (field in listOf("title", "body", "formatting", "paragraphs",
                             "comments", "category", "date", "color",
                             "recurrence", "checklist")) {
            if (incoming.has(field)) note.put(field, incoming.get(field))
        }
        note.put("updated", now)
    }

    /** The fields a code never carries, filled as a new note fills them. */
    private fun fillNewNoteDefaults(note: JSONObject, now: String) {
        note.put("attachments", JSONArray())
        note.put("exceptions", JSONArray())
        note.put("overrides", JSONObject())
        note.put("pinned", false)
        note.put("updated", now)
        note.put("created", now)
        if (!note.has("recurrence")) note.put("recurrence", defaultRecurrence())
        if (!note.has("uid")) {
            note.put("uid", java.util.UUID.randomUUID().toString())
        }
        for (field in listOf("title", "body", "comments", "category", "date", "color")) {
            if (!note.has(field)) {
                note.put(field, if (field == "category") "General" else "")
            }
        }
        for (field in listOf("formatting", "paragraphs", "checklist")) {
            if (!note.has(field)) note.put(field, JSONArray())
        }
    }

    fun importShareCode(load: Load, code: String): SaveResult {
        val file = load.file
            ?: return SaveResult.Failed("nu stiu unde sa scriu fisierul de notite")
        if (file.exists() && load.raw == null) {
            return SaveResult.Failed(
                "fisierul exista dar nu s-a putut citi -- nu scriu peste el")
        }
        val decoded = Sharing.decode(code)
        if (decoded is Sharing.Decoded.Failed) return SaveResult.Failed(decoded.reason)
        val incoming = (decoded as Sharing.Decoded.Notes).notes

        val array = load.raw ?: JSONArray()
        var maxId = 0
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: continue
            val itsId = o.opt("id")
            if (itsId is Number && itsId.toInt() > maxId) maxId = itsId.toInt()
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val candidate = copyOf(array)
        var firstId = 0
        for ((index, shared) in incoming.withIndex()) {
            val note = JSONObject(shared.toString())
            val id = maxId + index + 1
            if (index == 0) firstId = id
            note.put("id", id)
            // The fields a code never carries, filled the way a new note
            // is filled -- so what lands is what normalize_note expects.
            note.put("attachments", JSONArray())
            note.put("exceptions", JSONArray())
            note.put("overrides", JSONObject())
            note.put("pinned", false)
            note.put("updated", now)
            note.put("created", now)
            if (!note.has("recurrence")) note.put("recurrence", defaultRecurrence())
            for (field in listOf("title", "body", "comments", "category", "date", "color")) {
                if (!note.has(field)) note.put(field, if (field == "category") "General" else "")
            }
            for (field in listOf("formatting", "paragraphs", "checklist")) {
                if (!note.has(field)) note.put(field, JSONArray())
            }
            candidate.put(note)
        }

        val problem = writeAtomic(file, candidate)
        if (problem != null) return SaveResult.Failed(problem)
        for (i in array.length() until candidate.length()) array.put(candidate.get(i))
        return SaveResult.Ok(firstId, imported = incoming.size)
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

    private fun spansToJson(spans: List<FormatSpan>): JSONArray {
        val array = JSONArray()
        for (span in spans) {
            val o = JSONObject()
            o.put("start", span.start)
            o.put("end", span.end)
            o.put("style", span.style)
            if (span.value.isNotEmpty()) o.put("value", span.value)
            array.put(o)
        }
        return array
    }

    private fun movedParagraphs(note: JSONObject, old: String, new: String): JSONArray {
        val source = note.opt("paragraphs") as? JSONArray ?: return JSONArray()
        val byLine = LinkedHashMap<Int, JSONObject>()
        for (i in 0 until source.length()) {
            val o = source.opt(i) as? JSONObject ?: continue
            val line = o.opt("line")
            if (line is Number) byLine[line.toInt()] = o
        }
        val survivors = remapParagraphLines(old, new, byLine.keys.toList())
        val kept = JSONArray()
        for ((oldLine, newLine) in survivors) {
            val marker = byLine[oldLine] ?: continue
            val moved = JSONObject()
            moved.put("line", newLine)
            moved.put("kind", marker.opt("kind") ?: "bullet")
            kept.put(moved)
        }
        return kept
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
     * Dump to a sibling `.tmp`, force it to the disk, then rename over the
     * real file. A crash mid-write leaves the old notebook whole, because
     * rename is atomic within a filesystem; a half-written file never
     * carries the real name.
     *
     * The `sync()` is one step stronger than the desktop's, on purpose:
     * a phone's process is killed far more casually than a PC's, so
     * "written" has to mean "on the flash", not "in a buffer".
     *
     * @return null when it worked, otherwise the reason.
     */
    private fun writeAtomic(file: File, array: JSONArray): String? {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            // indent 2, non-ASCII left alone: the same bytes the desktop
            // writes, so a file can move between them unchanged.
            val bytes = array.toString(2).toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
        } catch (e: Exception) {
            tmp.delete()
            return e.message ?: e.toString()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return "nu s-a putut inlocui ${file.name}"
        }
        return null
    }

    fun parseNotes(text: String): List<Note> {
        val array = try {
            JSONArray(text)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<Note>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(readNote(obj))
        }
        return out
    }

    fun parseCategories(text: String): Map<String, String> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return emptyMap()
        }
        val saved = root.opt("categories") as? JSONObject ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (key in saved.keys()) {
            val name = key.trim()
            val color = saved.opt(key)
            // The desktop sanitises here too: settings.json is hand-editable
            // and travels inside backups, so "#not-a-colour" must not reach
            // the renderer.
            if (name.isNotEmpty() && color is String && isHexColor(color)) {
                out[name] = color
            }
        }
        return out
    }

    /**
     * The retention window from settings, clamped the way the desktop
     * clamps it: a hand-edited file saying 0, or -3, or "forever" must not
     * make anything look due for deletion. Anything unusable reads as the
     * default.
     */
    fun parseRetentionDays(settingsText: String): Int {
        val root = try {
            JSONObject(settingsText)
        } catch (e: Exception) {
            return TRASH_RETENTION_DAYS
        }
        val days = root.opt("trash_retention_days")
        if (days !is Number || days is Double || days is Float) {
            return TRASH_RETENTION_DAYS
        }
        return days.toInt().coerceIn(1, 365)
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

    /** The colour if it is one, otherwise [fallback]. */
    fun sanitizeColor(value: String, fallback: String = ""): String =
        if (isHexColor(value)) value.trim() else fallback

    /**
     * True for "YYYY-MM-DD" naming a day that exists.
     *
     * Not lenient: left lenient, "2026-02-30" would quietly become the 2nd
     * of March, and a note would show a date the user never chose.
     */
    fun isDate(value: String): Boolean {
        val v = value.trim()
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(v)) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        return try {
            format.parse(v) != null
        } catch (e: Exception) {
            false
        }
    }

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
            pinned = o.opt("pinned") == true,
            comments = o.str("comments"),
            attachments = o.arrayLength("attachments"),
            formatting = readSpans(o.opt("formatting") as? JSONArray),
            paragraphs = o.arrayLength("paragraphs"),
            checklist = readChecklist(o.opt("checklist") as? JSONArray),
            recurrenceFreq = rule?.str("freq") ?: "",
            recurrenceInterval = rule?.intOr("interval", 1) ?: 1,
            exceptions = o.arrayLength("exceptions"),
            overrides = (o.opt("overrides") as? JSONObject)?.length() ?: 0,
            deletedAt = o.str("deleted_at"),
            uid = o.str("uid"),
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
            out.add(FormatSpan(start, end, style, o.str("value")))
        }
        return out
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
