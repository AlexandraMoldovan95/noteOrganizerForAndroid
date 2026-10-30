package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Repeating notes: the rules, and working out which dates they land on.
 * A port of the desktop's `core/recurrence.py`, whose `test_recurrence.py`
 * is this file's acceptance suite (`RecurrenceTest`).
 *
 * **A repeating note is stored exactly once.** Its days are worked out on
 * demand for whatever range is being shown, and never written to the
 * file: a note repeating daily since 1990 is still one entry. Four fields
 * describe it all -- `date` (the series' first day), `recurrence` (the
 * rule), `exceptions` (days deleted one by one), `overrides` (days edited
 * on their own) -- and since the desktop's 0.92.374 a fifth, `moved` (a day
 * held on another date).
 *
 * Dates are `java.time.LocalDate`, there from Android 8, this app's
 * minimum; the file keeps one form, YYYY-MM-DD, whatever was typed.
 */
object Recurrence {

    val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val FREQS = listOf("daily", "weekly", "monthly", "yearly")

    val FREQ_LABELS = linkedMapOf(
        "" to "Does not repeat", "daily" to "Daily", "weekly" to "Weekly",
        "monthly" to "Monthly", "yearly" to "Yearly",
    )

    /** What one day's own edit may replace; everything else is the series'. */
    val OVERRIDABLE_FIELDS = listOf("title", "body", "comments", "category", "color")

    /** No rule may produce more than this in one expansion: a guard, not a budget. */
    const val MAX_OCCURRENCES = 5000

    /** "Every 1000 years" is past any real use, and far from any overflow. */
    const val MAX_INTERVAL = 1000

    /** What the empty date field shows: the order a typed date is read in. */
    const val DATE_FORM = "DD.MM.YYYY"

    // ---- the stored form ----

    private val STORED = Regex("^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})$")

    /**
     * "2026-07-24" -> that day, or null. Null rather than a throw: dates
     * come from files and from typing, and "not a date" is ordinary. Like
     * Python's `%m` and `%d`, one figure for the month or the day is read.
     */
    fun parseDate(value: Any?): LocalDate? {
        val text = (value as? String)?.trim() ?: return null
        val parts = STORED.matchEntire(text) ?: return null
        return dateOf(parts.groupValues[1].toInt(), parts.groupValues[2].toInt(), parts.groupValues[3].toInt())
    }

    /** The calendar's own date, or null -- years 1 to 9999, as Python's. */
    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? {
        if (year !in 1..9999) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
    }

    /** A day -> "YYYY-MM-DD", the one form the file keeps. */
    fun formatDate(d: LocalDate): String =
        String.format(Locale.US, "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)

    // ---- dates as people write them, and as the app shows them ----
    //
    // In figures the day comes first, as the field's form asks, and the
    // month first only where the day first cannot be: 10/17/2026 is October
    // 17th, the American way, while 10/12/2026 is the tenth of December.

    /**
     * The month names a typed date may use, in English and in Romanian, as
     * the desktop reads them. Read, never shown; none carries a diacritic,
     * and a typed one is folded regardless.
     */
    internal val MONTH_NAMES: Map<String, Int> = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5,
        "june" to 6, "july" to 7, "august" to 8, "september" to 9, "october" to 10,
        "november" to 11, "december" to 12,
        "ianuarie" to 1, "februarie" to 2, "martie" to 3, "aprilie" to 4, "mai" to 5,
        "iunie" to 6, "iulie" to 7, "septembrie" to 9, "octombrie" to 10,
        "noiembrie" to 11, "decembrie" to 12,
    )

    private val LONG_MONTHS = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    private val LONG_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday", "Sunday")

    /** Day names a written-out date may carry: read, and set aside. */
    internal val WEEKDAY_WORDS = LONG_DAYS.map { it.lowercase(Locale.ROOT) }

    /** Words around a date that say nothing about which day it is. */
    private val FILLERS = setOf("the", "of")

    // A part of a typed date: a day with its ordinal ending ("17th"), a run
    // of figures, or a word. Figures are 0-9 only: digits of another script
    // are not a date. Letters are any script's, as Python's [^\W\d_].
    private val DATE_PART = Regex("[0-9]{1,2}(?:st|nd|rd|th)|[0-9]+|\\p{L}+", RegexOption.IGNORE_CASE)
    // What a typed date may be made of, taken away run by run: anything left
    // over means it is not a date. One pass, never a backtracking search.
    // The spaces are Python's `\s`, written out: Android's regex engine and
    // the computer's do not agree on the name of that class.
    private const val SPACES = "\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u0085\\u00A0\\u1680" +
        "\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
    private val DATE_RUNS = Regex("[0-9]+|\\p{L}+|[$SPACES./,\\-]+")
    private val FIGURES = Regex("^[0-9]+")
    private const val DATE_MAX_CHARS = 40

    /** [word] without case or diacritics, for comparing with a name. */
    internal fun fold(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFKD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() &&
                Character.getType(it) != Character.COMBINING_SPACING_MARK.toInt() &&
                Character.getType(it) != Character.ENCLOSING_MARK.toInt() }
            .lowercase(Locale.ROOT)

    private fun namesStartedBy(word: String, names: Collection<String>): List<String> {
        val folded = fold(word)
        if (folded.length < 3) return emptyList()
        return names.filter { it.startsWith(folded) }
    }

    /** The month [word] names: a whole name or the start of one, three letters
     *  at least, every name it starts agreeing on the month -- or null. */
    internal fun monthOf(word: String, names: Map<String, Int> = MONTH_NAMES): Int? {
        val months = namesStartedBy(word, names.keys).map { names.getValue(it) }.toSet()
        return if (months.size == 1) months.single() else null
    }

    /**
     * A date as a person writes it -> that day, or null.
     *
     * Read: 17.10.2026, 17/10/2026, 17-10-2026 (the day first; the month
     * first only where the day first cannot be); 2026-10-17; and the month
     * as a word before or after the day -- 17 oct 2026, 17 octombrie 2026,
     * October 17, 2026. A day name, an ordinal ending, "the" and "of" are
     * read and set aside.
     *
     * [settled]: the person has finished with the field. Only then is a
     * short year read as this century's and a missing one as this year's:
     * read while typing, "17.10.20" on its way to 2026 would be saved as
     * 2020. [today] is for tests.
     */
    fun readDate(value: Any?, settled: Boolean = false, today: LocalDate? = null): LocalDate? =
        readDate(value, settled, today, MONTH_NAMES)

    internal fun readDate(value: Any?, settled: Boolean, today: LocalDate?, months: Map<String, Int>): LocalDate? {
        val text = (value as? String)?.trim() ?: return null
        if (text.isEmpty() || text.length > DATE_MAX_CHARS || DATE_RUNS.replace(text, "").isNotEmpty()) return null
        val numbers = ArrayList<String>()
        val words = ArrayList<String>()
        var weekdays = 0
        for (match in DATE_PART.findAll(text)) {
            val part = match.value
            when {
                part[0] in '0'..'9' -> numbers.add(FIGURES.find(part)!!.value)
                fold(part) in FILLERS -> Unit
                // No three letters start both a day's name and a month's, in
                // either language, so a word is one or the other.
                namesStartedBy(part, WEEKDAY_WORDS).toSet().size == 1 -> weekdays += 1
                else -> words.add(part)
            }
        }
        if (weekdays > 1) return null

        fun yearOf(figures: String): Int? = when {
            figures.length == 4 && figures[0] != '0' -> figures.toInt()
            figures.length == 2 && settled -> 2000 + figures.toInt()
            else -> null
        }
        fun thisYear(): Int? = if (settled) (today ?: LocalDate.now()).year else null

        val year: Int?
        val month: Int
        val day: String
        if (words.isEmpty()) {
            val monthText: String
            if (numbers.size == 3 && numbers[0].length == 4) {
                year = yearOf(numbers[0]); monthText = numbers[1]; day = numbers[2]
            } else if (numbers.size == 2 || numbers.size == 3) {
                val first = numbers[0]
                val second = numbers[1]
                year = if (numbers.size == 3) yearOf(numbers[2]) else thisYear()
                // A second figure past 12 is no month: the American order.
                if (first.length <= 2 && second.length <= 2 && second.toInt() > 12 && 12 >= first.toInt()) {
                    day = second; monthText = first
                } else {
                    day = first; monthText = second
                }
            } else {
                return null
            }
            if (day.length > 2 || monthText.length > 2) return null
            month = monthText.toInt()
        } else if (words.size == 1) {
            month = monthOf(words[0], months) ?: return null
            if (numbers.size == 2) {
                // The year is the figure written as one -- four figures --
                // and where both are short, the second: "17 oct 26".
                if (numbers[0].length == 4) {
                    year = yearOf(numbers[0]); day = numbers[1]
                } else {
                    day = numbers[0]; year = yearOf(numbers[1])
                }
            } else if (numbers.size == 1) {
                day = numbers[0]; year = thisYear()
            } else {
                return null
            }
            if (day.length > 2) return null
        } else {
            return null
        }
        if (year == null) return null
        return dateOf(year, month, day.toInt())
    }

    /**
     * A stored date as the app shows it: 2026-10-17 -> 17.10.2026. Anything
     * that is not a stored date -- empty, or text kept as it was typed --
     * comes back as it was.
     */
    fun showDate(value: Any?): String {
        val text = value as? String ?: return ""
        val d = parseDate(text) ?: return text
        return String.format(Locale.US, "%02d.%02d.%04d", d.dayOfMonth, d.monthValue, d.year)
    }

    /** A stored moment: 2026-10-17 09:30 -> 17.10.2026 09:30, the time kept as it is. */
    fun showStamp(value: Any?): String {
        val text = value as? String ?: return ""
        val gap = text.indexOf(' ')
        if (gap < 0) return showDate(text)
        return showDate(text.substring(0, gap)) + text.substring(gap)
    }

    /** A day written out, the day first: Saturday, 3 October 2026. */
    fun showDay(d: LocalDate, year: Boolean = true, shortMonth: Boolean = false): String {
        var month = LONG_MONTHS[d.monthValue - 1]
        if (shortMonth) month = month.take(3)
        val text = "${LONG_DAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} $month"
        return if (year) "$text ${d.year}" else text
    }

    /** Whether two date texts name the same day, or are the same text. */
    fun sameDate(a: Any?, b: Any?): Boolean {
        val first = (a as? String)?.trim() ?: ""
        val second = (b as? String)?.trim() ?: ""
        if (first == second) return true
        val x = readDate(first)
        return x != null && x == readDate(second)
    }

    // ---- the rule ----

    /**
     * Any stored rule in a known-good shape. Anything unexpected degrades to
     * "does not repeat" rather than to a crash, each field on its own.
     */
    fun normalize(value: Any?): JSONObject {
        val out = JSONObject()
            .put("freq", "").put("interval", 1).put("weekdays", JSONArray())
            .put("month_days", JSONArray()).put("month_day_policy", "clamp")
            .put("end", "").put("count", 0)
        val rule = value as? JSONObject ?: return out
        val freq = rule.opt("freq") as? String
        out.put("freq", if (freq in FREQS) freq else "")
        out.put("interval", pythonInt(rule.opt("interval"))?.coerceIn(1, MAX_INTERVAL) ?: 1)
        out.put("weekdays", sortedInts(rule.opt("weekdays"), 0..6))
        out.put("month_days", sortedInts(rule.opt("month_days"), 1..31))
        out.put("month_day_policy", if (rule.opt("month_day_policy") == "skip") "skip" else "clamp")
        // Read as a typed date is read: an end written day first is that
        // end, not "never ends"; stored in the one form.
        out.put("end", readDate(rule.opt("end"))?.let { formatDate(it) } ?: "")
        out.put("count", pythonInt(rule.opt("count"))?.coerceIn(0, MAX_OCCURRENCES) ?: 0)
        return out
    }

    /** `int(value)` as Python takes it: a whole number, digits as text, a float cut. */
    private fun pythonInt(value: Any?): Int? = when (value) {
        is Boolean -> if (value) 1 else 0
        is Int -> value
        is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is Number -> value.toDouble().let { if (it.isFinite()) it.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() else null }
        is String -> pythonIntOf(value)
        else -> null
    }

    /**
     * Python's `int(text)`, base 10: Python's spaces either side, a sign,
     * then digits -- any script's -- with single underscores between them,
     * as Python 3.6 reads "1_000"; past 4,300 digits Python refuses (its
     * default int_max_str_digits). Null where Python raises; a number past
     * an Int is held at its edge, as the clamp after it would hold it.
     * `toIntOrNull` refused "1_0", which the desktop stores as 10, and so a
     * hand-made code repeated on other days on the two apps (the final
     * audit's eighth round).
     */
    internal fun pythonIntOf(text: String): Int? {
        val t = PyText.strip(text)
        var i = 0
        val negative = t.startsWith("-")
        if (t.startsWith("-") || t.startsWith("+")) i = 1
        val digits = StringBuilder()
        var afterSeparator = true      // an underscore neither leads nor follows another
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (cp == '_'.code) {
                if (afterSeparator) return null
                afterSeparator = true
            } else {
                if (Character.getType(cp) != Character.DECIMAL_DIGIT_NUMBER.toInt()) return null
                digits.append('0' + Character.digit(cp, 10))
                afterSeparator = false
            }
            i += Character.charCount(cp)
        }
        // Nor ends: "1_" is refused, and so is a sign alone.
        if (afterSeparator || digits.isEmpty() || digits.length > PYTHON_INT_DIGITS) return null
        val value = java.math.BigInteger(digits.toString()).let { if (negative) it.negate() else it }
        return value.max(java.math.BigInteger.valueOf(Int.MIN_VALUE.toLong()))
            .min(java.math.BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
    }

    private const val PYTHON_INT_DIGITS = 4300

    /**
     * Python's `sorted({d for d in value if isinstance(d, int) and lo <= d <= hi})`.
     * True and False are ints there, 1 and 0, and kept as the days they are:
     * left out, a hand-made rule's [true, 3] was Thursday alone on the phone
     * and Tuesday and Thursday on the desktop, and "the same series" answered
     * no for a rule the desktop read as the same (the final audit's eighth
     * round). Written as the numbers, which Python reads as equal to them.
     */
    private fun sortedInts(value: Any?, range: IntRange): JSONArray {
        val list = value as? JSONArray ?: return JSONArray()
        val kept = sortedSetOf<Int>()
        for (i in 0 until list.length()) {
            when (val v = list.opt(i)) {
                is Boolean -> (if (v) 1 else 0).let { if (it in range) kept.add(it) }
                is Int -> if (v in range) kept.add(v)
                is Long -> if (v in range.first.toLong()..range.last.toLong()) kept.add(v.toInt())
            }
        }
        return JSONArray(kept.toList())
    }

    /** Python's truthiness, for the one place the desktop asks it of a stored value. */
    private fun truthy(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is String -> value.isNotEmpty()
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    /**
     * Whether this note repeats at all. The type check matters: a Trash
     * entry is read as the file held it, and a rule that is a string must
     * answer "no" rather than take the screen down.
     */
    fun isRecurring(note: JSONObject): Boolean {
        val rule = note.opt("recurrence") as? JSONObject ?: return false
        return truthy(rule.opt("freq"))
    }

    private fun stringsOf(value: Any?): Set<String> {
        val list = value as? JSONArray ?: return emptySet()
        return (0 until list.length()).mapNotNull { list.opt(it) as? String }.toSet()
    }

    /**
     * How many times a note's moved days have been read whole: a count the
     * tests hold to, never a time. Asking about one day at a time read all
     * 5,000 of a backup's moved days for each, and the note froze as it
     * opened and at every letter typed (ISSUES_LOG, round 2 of the final
     * audit).
     */
    internal var movesRead = 0L

    /** A repeating note's days held on other dates: the rule's date -> where it is. */
    private fun moves(note: JSONObject): List<Pair<String, Any?>> {
        if (!isRecurring(note)) return emptyList()
        val moved = note.opt("moved") as? JSONObject ?: return emptyList()
        movesRead += 1
        return moved.keys().asSequence().map { it to moved.opt(it) }.toList()
    }

    /** A rule worked out once: its first day, its normalized rule, and its deleted days. */
    private class RuleParts(note: JSONObject, withExceptions: Boolean = true) {
        val anchor: LocalDate? = parseDate(note.opt("date"))
        val rule: JSONObject = normalize(note.opt("recurrence"))
        val exceptions: Set<String> = if (withExceptions) stringsOf(note.opt("exceptions")) else emptySet()
    }

    /**
     * One note's days asked of many single dates: for each, what
     * `expand(note, day, day)` says, with the rule, the deleted days and the
     * moved days read once for them all -- the desktop's `_drawn_on_days`.
     * A rule that stops after N times is walked once from its first day.
     * [withExceptions] false asks of the schedule alone, deleted days drawn.
     */
    private class Schedule(note: JSONObject, withExceptions: Boolean = true) {
        private val parts = RuleParts(note, withExceptions)
        private val moved = moves(note)
        private val origins = moved.mapTo(HashSet()) { it.first }
        private val landing = HashMap<LocalDate, MutableList<Pair<String, String>>>().also { map ->
            for ((orig, new) in moved) {
                val text = new as? String ?: continue
                val day = parseDate(text) ?: continue
                map.getOrPut(day) { ArrayList(1) }.add(orig to text)
            }
        }
        private val cameFrom = HashMap<Any?, String>().also { map -> for ((orig, new) in moved) map.putIfAbsent(new, orig) }
        private val counted: Set<String>? = parts.anchor?.takeIf { parts.rule.getInt("count") > 0 }?.let {
            expandRule(parts, it, LocalDate.of(9999, 12, 31), null, emptySet()).toHashSet()
        }

        /** Whether the rule itself gives [day], deleted days apart. */
        private fun ruleGives(day: LocalDate): Boolean {
            val text = formatDate(day)
            return counted?.contains(text) ?: (expandRule(parts, day, day, null, emptySet()) == listOf(text))
        }

        /** `expand(note, day, day)`, answered from what was read once. */
        fun on(day: LocalDate): List<String> {
            val text = formatDate(day)
            val out = ArrayList<String>(1)
            if (text !in origins && ruleGives(day)) out.add(text)
            for ((orig, newText) in landing[day].orEmpty()) {
                if (newText in out || orig in parts.exceptions) continue
                val origin = parseDate(orig) ?: continue
                if (formatDate(origin) != orig || !ruleGives(origin)) continue
                out.add(newText)
            }
            out.sort()
            return out
        }

        /** `movedFrom(note, day)`. */
        fun movedFrom(day: String): String? = cameFrom[day]
    }

    /**
     * Every date this note lands on between [start] and [end], inclusive, as
     * YYYY-MM-DD, in order: deleted days left out, a moved day drawn on its
     * new date. [limit] stops after that many -- the first ones.
     *
     * The one place a series' days are worked out: the calendar, the
     * History, the sort by next day and the days off the schedule all ask
     * it, so a moved day is moved everywhere.
     */
    fun expand(note: JSONObject, start: LocalDate, end: LocalDate, limit: Int? = null): List<String> {
        val moved = moves(note)
        val parts = RuleParts(note)
        // The rule's own days, less the ones moved away: each still counts as
        // one of a "10 times" series' ten, wherever it is held.
        val dates = expandRule(parts, start, end, limit, moved.mapTo(HashSet()) { it.first }).toMutableList()
        if (moved.isEmpty()) return dates
        // ...and each moved day on its new date, while the day it was moved
        // from is still one the rule gives and was not deleted.
        val drawn = dates.toMutableSet()
        for ((orig, new) in moved) {
            val newText = new as? String ?: continue
            val day = parseDate(newText) ?: continue
            if (newText in drawn || orig in parts.exceptions || day < start || day > end) continue
            val origin = parseDate(orig) ?: continue
            if (expandRule(parts, origin, origin, null, emptySet()) != listOf(orig)) continue
            dates.add(newText)
            drawn.add(newText)
        }
        dates.sort()
        return if (limit != null) dates.take(limit) else dates
    }

    /** The date the day now on [day] was moved from, or null. */
    fun movedFrom(note: JSONObject, day: String): String? =
        moves(note).firstOrNull { it.second == day }?.first

    /**
     * Holds the series' day drawn on [day] on [to] instead, recorded against
     * the date the rule gives it -- and dropped when [to] is that date: the
     * day is home again. Its own edit goes with it. Changes [note] in place;
     * the caller makes sure [to] holds no other day of the series. Returns
     * the date the rule gives the day.
     */
    fun moveDay(note: JSONObject, day: String, to: String): String {
        var moved = note.opt("moved") as? JSONObject
        if (moved == null) {
            moved = JSONObject()
            note.put("moved", moved)
        }
        val orig = movedFrom(note, day) ?: day
        if (to == orig) moved.remove(orig) else moved.put(orig, to)
        val overrides = note.opt("overrides") as? JSONObject
        if (overrides != null && overrides.has(day) && to != day) {
            overrides.put(to, overrides.remove(day))
        }
        return orig
    }

    private fun dayInMonth(year: Int, month: Int, wanted: Int, policy: String): LocalDate? {
        val last = LocalDate.of(year, month, 1).lengthOfMonth()
        if (wanted <= last) return LocalDate.of(year, month, wanted)
        return if (policy == "clamp") LocalDate.of(year, month, last) else null
    }

    private fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    /**
     * The days the rule gives between [start] and [end], less its exceptions
     * and the dates in [skip] -- which still count towards "N times".
     */
    private fun expandRule(parts: RuleParts, start: LocalDate, end: LocalDate,
                           limit: Int?, skip: Set<String>): List<String> {
        val anchor = parts.anchor ?: return emptyList()
        val rule = parts.rule
        // Asked of both sets, never of their union: built at every call, the
        // union of 5,000 deleted and 5,000 moved days was rebuilt per day.
        fun gone(text: String) = text in parts.exceptions || text in skip
        val freq = rule.getString("freq")
        if (freq.isEmpty()) {
            // The note's deleted days belong to the series it was, kept for
            // a repeat brought back: asked here, a series whose first day had
            // been deleted fell off the calendar, and out of the day's
            // reminder, once its repeat was turned off (the desktop's b26394c).
            val oneOff = formatDate(anchor)
            return if (anchor in start..end && oneOff !in skip) listOf(oneOff) else emptyList()
        }
        val interval = rule.getInt("interval")
        val endDate = parseDate(rule.getString("end"))
        val maxCount = rule.getInt("count")
        val policy = rule.getString("month_day_policy")
        // Never past the requested range, and never past the rule's own end.
        val horizon = if (endDate == null) end else minOf(end, endDate)
        if (horizon < anchor) return emptyList()

        val results = ArrayList<Pair<LocalDate, String>>()
        var produced = 0
        val limited = maxCount > 0

        // One occurrence: counts every one since the anchor (for "N times"),
        // keeps only those inside the range. False when the rule is spent.
        fun take(candidate: LocalDate): Boolean {
            if (candidate < anchor) return true
            if (endDate != null && candidate > endDate) return false
            produced += 1
            if (candidate in start..end) {
                val text = formatDate(candidate)
                if (!gone(text)) {
                    results.add(candidate to text)
                    if (limit != null && results.size >= limit) return false
                }
            }
            return !(limited && produced >= maxCount)
        }

        when (freq) {
            "daily" -> {
                var current = anchor
                // With no count to honour, jump straight to the range.
                if (!limited && start > anchor) {
                    current = anchor.plusDays(daysBetween(anchor, start) / interval * interval)
                }
                var guard = 0
                while (current <= horizon && guard < MAX_OCCURRENCES) {
                    guard += 1
                    if (!take(current)) break
                    if (daysBetween(current, horizon) < interval) break
                    current = current.plusDays(interval.toLong())
                }
            }
            "weekly" -> {
                // Whole weeks from the Monday of the anchor's week.
                val weekdays = rule.getJSONArray("weekdays").let { a -> (0 until a.length()).map { a.getInt(it) } }
                    .ifEmpty { listOf(anchor.dayOfWeek.value - 1) }
                val anchorWeek = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                val stepDays = 7L * interval
                var weekStart = anchorWeek
                if (!limited && start > anchor) {
                    val ahead = daysBetween(anchorWeek, start) / stepDays
                    if (ahead > 0) weekStart = anchorWeek.plusDays(stepDays * ahead)
                }
                var guard = 0
                var stop = false
                while (weekStart <= horizon && guard < MAX_OCCURRENCES && !stop) {
                    guard += 1
                    for (weekday in weekdays) {
                        if (daysBetween(weekStart, horizon) < weekday) continue
                        if (!take(weekStart.plusDays(weekday.toLong()))) {
                            stop = true
                            break
                        }
                    }
                    if (daysBetween(weekStart, horizon) < stepDays) break
                    weekStart = weekStart.plusDays(stepDays)
                }
            }
            "monthly" -> {
                val monthDays = rule.getJSONArray("month_days").let { a -> (0 until a.length()).map { a.getInt(it) } }
                    .ifEmpty { listOf(anchor.dayOfMonth) }
                var step = 0
                if (!limited && start > anchor) {
                    val ahead = (start.year - anchor.year) * 12 + (start.monthValue - anchor.monthValue)
                    step = maxOf(0, ahead / interval)
                }
                var guard = 0
                var stop = false
                while (guard < MAX_OCCURRENCES && !stop) {
                    guard += 1
                    val total = anchor.year * 12 + (anchor.monthValue - 1) + step * interval
                    val year = total / 12
                    val month = total % 12 + 1
                    if (year > horizon.year || (year == horizon.year && month > horizon.monthValue)) break
                    // Resolved first, THEN counted: under "clamp", the 29th,
                    // 30th and 31st all land on Feb 28 -- one day, not three.
                    val landed = monthDays.mapNotNull { dayInMonth(year, month, it, policy) }.toSortedSet()
                    for (candidate in landed) {
                        if (candidate > horizon) continue
                        if (!take(candidate)) {
                            stop = true
                            break
                        }
                    }
                    step += 1
                }
            }
            "yearly" -> {
                var index = 0
                if (!limited && start.year > anchor.year) index = maxOf(0, (start.year - anchor.year) / interval)
                var guard = 0
                while (guard < MAX_OCCURRENCES) {
                    guard += 1
                    val year = anchor.year + index * interval
                    if (year > horizon.year) break
                    // A Feb 29 anchor takes the same clamp or skip as "the 31st".
                    val candidate = dayInMonth(year, anchor.monthValue, anchor.dayOfMonth, policy)
                    if (candidate != null) {
                        if (candidate > horizon) break
                        if (!take(candidate)) break
                    }
                    index += 1
                }
            }
        }
        return results.sortedBy { it.first }.map { it.second }
    }

    /**
     * The first day this note lands on, on or after [date] -- or null when
     * the series never gets there. Asked over growing windows, so the
     * common series answers from the first.
     */
    fun nextOnOrAfter(note: JSONObject, date: String): String? {
        val start = parseDate(date) ?: return null
        if (!isRecurring(note)) return null
        for (window in listOf(31L, 366L, 3660L)) {
            val hits = expand(note, start, start.plusDays(window))
            if (hits.isNotEmpty()) return hits.first()
        }
        return null
    }

    /**
     * The note as it appears on one day: the series with that day's own
     * edit on top -- a copy; the stored note is never changed. A note that
     * no longer repeats is itself on its day: the days a share code or
     * "↩ Restore previous" left in it are kept for a repeat brought back,
     * and read here they stood in for it -- the calendar's row of its date
     * showed the first day's old title and words, with a ✎. The desktop's
     * `effective_note` (b26394c). What a day HOLDS is [dayAsKept].
     */
    fun effectiveNote(note: JSONObject, day: String): JSONObject =
        if (!isRecurring(note)) note else dayAsKept(note, day)

    /**
     * The note with [day]'s own edit on top, whether or not it still
     * repeats: what that day holds, for the questions that decide whether a
     * day is worth keeping -- the desktop's `day_as_kept`. What a day SHOWS
     * is [effectiveNote].
     */
    fun dayAsKept(note: JSONObject, day: String): JSONObject {
        val patch = (note.opt("overrides") as? JSONObject)?.opt(day) as? JSONObject ?: return note
        val merged = JSONObject()
        for (key in note.keys()) merged.put(key, note.opt(key))
        for (field in OVERRIDABLE_FIELDS) if (patch.has(field)) merged.put(field, patch.opt(field))
        return merged
    }

    /** Python's `value or ""`: anything falsy reads as empty text. */
    private fun orEmpty(value: Any?): Any = if (truthy(value)) value!! else ""

    /**
     * Whether one day's own edit says exactly what the series says -- and
     * so needs no copy of its own. A field the day does not hold counts as
     * empty; titles compare without the spaces around them.
     */
    fun dayMatchesSeries(note: JSONObject, patch: JSONObject): Boolean {
        for (field in OVERRIDABLE_FIELDS) {
            var mine = orEmpty(patch.opt(field))
            var theirs = orEmpty(note.opt(field))
            if (mine !is String || theirs !is String) return false
            if (field == "title") {
                mine = mine.trim()
                theirs = theirs.trim()
            }
            if (mine != theirs) return false
        }
        return true
    }

    /**
     * The days a repeating note keeps its own edits for that its schedule
     * no longer reaches, oldest first. Asked of [expand] itself, so it
     * cannot disagree with the calendar. A deleted day is not counted.
     */
    fun offScheduleDays(note: JSONObject): List<String> {
        if (!isRecurring(note)) return emptyList()
        val overrides = note.opt("overrides") as? JSONObject ?: return emptyList()
        if (overrides.length() == 0) return emptyList()
        val exceptions = stringsOf(note.opt("exceptions"))
        // The schedule alone, deleted days drawn, read once for every day.
        val schedule = Schedule(note, withExceptions = false)
        val off = ArrayList<String>()
        for (day in overrides.keys().asSequence().sorted()) {
            val whenDay = parseDate(day) ?: continue
            if (day in exceptions) continue
            if (schedule.on(whenDay) != listOf(day)) off.add(day)
        }
        return off
    }

    /**
     * What the series view says of its days, counted as the desktop's
     * `_update_occurrence_banner` counts them: the days keeping their own
     * edits, less those the schedule no longer reaches (the next line shows
     * those), and the days held on another date the calendar still draws
     * them on. An edit to the series not reaching three days looked like an
     * edit that failed -- the desktop's field report, and the phone's until
     * it said so.
     */
    fun seriesCounts(note: JSONObject): Pair<Int, Int> {
        if (!isRecurring(note)) return 0 to 0
        val own = ((note.opt("overrides") as? JSONObject)?.length() ?: 0) - offScheduleDays(note).size
        val moved = note.opt("moved") as? JSONObject
        val schedule = Schedule(note)
        val held = moved?.keys()?.asSequence()?.count { key ->
            val now = moved.opt(key) as? String
            val whenDay = now?.let { parseDate(it) }
            whenDay != null && schedule.on(whenDay) == listOf(now)
        } ?: 0
        return maxOf(0, own) to held
    }

    /** "Every 2 weeks on Mon, Thu, until 31.12.2026" -- or "" for a one-off note. */
    fun describe(note: JSONObject): String {
        val rule = normalize(note.opt("recurrence"))
        val freq = rule.getString("freq")
        if (freq.isEmpty()) return ""
        val interval = rule.getInt("interval")
        val unit = mapOf("daily" to "day", "weekly" to "week", "monthly" to "month", "yearly" to "year").getValue(freq)
        var text = if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
        val weekdays = rule.getJSONArray("weekdays")
        val monthDays = rule.getJSONArray("month_days")
        if (freq == "weekly" && weekdays.length() > 0) {
            text += " on " + (0 until weekdays.length()).joinToString(", ") { WEEKDAY_NAMES[weekdays.getInt(it)] }
        } else if (freq == "monthly" && monthDays.length() > 0) {
            text += " on day " + (0 until monthDays.length()).joinToString(", ") { monthDays.getInt(it).toString() }
        }
        val count = rule.getInt("count")
        if (count > 0) text += ", $count times"
        else if (rule.getString("end").isNotEmpty()) text += ", until ${showDate(rule.getString("end"))}"
        return text
    }

    // ---- the History ----

    /** One day listed under a series in the History. */
    data class HistoryDay(
        val date: String,
        val title: String,
        val edited: Boolean,
        val offSchedule: Boolean,
        val movedFrom: String?,
    )

    /** One row of the History: a dated note, or a series with its days. */
    sealed class HistoryEntry {
        abstract val noteId: Int

        data class Dated(override val noteId: Int, val date: String, val title: String) : HistoryEntry()

        data class Series(
            override val noteId: Int,
            val title: String,
            val label: String,
            val days: List<HistoryDay>,
            /** "occurrences" (every day of a small bounded series) or "edited". */
            val daysKind: String,
            val occurrences: Int?,
            val edited: Int,
            val off: Int,
            val moved: Int,
            /** A search matched inside a day's own edit: open the group. */
            val autoExpand: Boolean,
            /** Where a series with no days to unfold leads on the calendar. */
            val jumpDate: String?,
        ) : HistoryEntry()
    }

    val YEAR_BUCKETS = listOf("All", "This year", "Last year", "2 years ago", "Older")

    private fun bucketOf(date: String, today: LocalDate): String? {
        val d = parseDate(date) ?: return null
        return when (d.year) {
            today.year -> "This year"
            today.year - 1 -> "Last year"
            today.year - 2 -> "2 years ago"
            else -> "Older"
        }
    }

    /** The sidebar search's plain judgement: case-insensitive "contains", as typed. */
    private fun matches(query: String, vararg texts: Any?): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase(Locale.ROOT)
        return texts.any { ((it as? String) ?: "").lowercase(Locale.ROOT).contains(q) }
    }

    /**
     * The Calendar's History: the days that carry real content, newest
     * first -- not every day a rule gives. A dated note at its date; a
     * series as a group whose days are the ones edited on their own (or,
     * for a small bounded series, all of them), so the list grows with what
     * was written, never with the passage of time. [query] reaches into a
     * day's own edit too, which the list's search cannot see.
     */
    fun history(notes: List<JSONObject>, query: String = "", yearBucket: String = "All",
                today: LocalDate = LocalDate.now()): List<HistoryEntry> {
        fun bucketOk(date: String) = yearBucket == "All" || bucketOf(date, today) == yearBucket
        fun isDate(date: Any?) = parseDate(date) != null
        val entries = ArrayList<Pair<String, HistoryEntry>>()
        for (note in notes) {
            val id = (note.opt("id") as? Number)?.toInt() ?: 0
            val title = note.opt("title") as? String ?: ""
            if (isRecurring(note)) {
                val exceptions = stringsOf(note.opt("exceptions"))
                val overrides = note.opt("overrides") as? JSONObject ?: JSONObject()
                val baseMatches = matches(query, title, note.opt("body"), note.opt("comments"))
                // A small bounded series lists all its days; an endless one
                // would drown the list, so it lists its edited days.
                val rule = normalize(note.opt("recurrence"))
                val anchor = parseDate(note.opt("date"))
                var occDays: List<String>? = null
                if (anchor != null) {
                    val count = rule.getInt("count")
                    if (count in 1..50) {
                        val far = if (ChronoUnit.DAYS.between(anchor, LocalDate.of(9999, 12, 31)) >= 36600)
                            anchor.plusDays(36600) else LocalDate.of(9999, 12, 31)
                        occDays = expand(note, anchor, far)
                    } else if (rule.getString("end").isNotEmpty()) {
                        parseDate(rule.getString("end"))?.let { endDay ->
                            val got = expand(note, anchor, endDay, limit = 51)
                            if (got.size <= 50) occDays = got
                        }
                    }
                }
                val off = offScheduleDays(note).toSet()
                // Every day of this series asked of one reading of its moves.
                val schedule = Schedule(note)
                val days = ArrayList<HistoryDay>()
                var anyChildMatch = false
                val kind: String
                val listed = occDays
                if (listed != null) {
                    kind = "occurrences"
                    for (day in listed.sortedDescending()) {
                        if (!bucketOk(day)) continue
                        val patch = overrides.opt(day) as? JSONObject
                        val edited = patch != null
                        val own = patch ?: JSONObject()
                        val childMatch = edited && matches(query, own.opt("title"), own.opt("body"), own.opt("comments"))
                        if (!(childMatch || baseMatches)) continue
                        if (query.isNotEmpty() && childMatch) anyChildMatch = true
                        days.add(HistoryDay(day, (own.opt("title") as? String)?.ifEmpty { null } ?: title,
                            edited, false, schedule.movedFrom(day)))
                    }
                    // A day the rule no longer gives was in no row at all: listed, marked.
                    for (day in off) {
                        if (!bucketOk(day)) continue
                        val own = overrides.opt(day) as? JSONObject ?: JSONObject()
                        val childMatch = matches(query, own.opt("title"), own.opt("body"), own.opt("comments"))
                        if (!(childMatch || baseMatches)) continue
                        if (query.isNotEmpty() && childMatch) anyChildMatch = true
                        days.add(HistoryDay(day, (own.opt("title") as? String)?.ifEmpty { null } ?: title,
                            true, true, null))
                    }
                } else {
                    kind = "edited"
                    for (day in overrides.keys().asSequence().sortedDescending()) {
                        // A deleted day's edit must not resurface.
                        if (day in exceptions || !isDate(day)) continue
                        if (!bucketOk(day)) continue
                        val own = overrides.opt(day) as? JSONObject ?: JSONObject()
                        val childMatch = matches(query, own.opt("title"), own.opt("body"), own.opt("comments"))
                        if (!(childMatch || baseMatches)) continue
                        if (query.isNotEmpty() && childMatch) anyChildMatch = true
                        days.add(HistoryDay(day, (own.opt("title") as? String)?.ifEmpty { null } ?: title,
                            true, day in off, if (day in off) null else schedule.movedFrom(day)))
                    }
                    // A day moved to another date is listed too: where it went
                    // is worth finding -- while the calendar draws it there.
                    for ((orig, new) in moves(note)) {
                        val newText = new as? String ?: continue
                        if (overrides.has(newText) || !isDate(newText) || !bucketOk(newText) || !baseMatches) continue
                        val day = parseDate(newText)!!
                        if (schedule.on(day) != listOf(newText)) continue
                        days.add(HistoryDay(newText, title, false, false, orig))
                    }
                }
                days.sortByDescending { it.date }
                val noteDate = note.opt("date") as? String ?: ""
                if (days.isNotEmpty() || (baseMatches && (bucketOk(noteDate) || yearBucket == "All"))) {
                    val sortKey = if (days.isNotEmpty()) days[0].date else noteDate
                    val jump = if (days.isEmpty()) nextOnOrAfter(note, formatDate(today)) ?: noteDate else null
                    entries.add(sortKey to HistoryEntry.Series(
                        noteId = id, title = title, label = describe(note), days = days, daysKind = kind,
                        occurrences = listed?.size,
                        edited = days.count { it.edited && !it.offSchedule },
                        off = days.count { it.offSchedule },
                        moved = days.count { !it.movedFrom.isNullOrEmpty() },
                        autoExpand = anyChildMatch, jumpDate = jump,
                    ))
                }
            } else {
                val d = note.opt("date") as? String ?: ""
                if (!isDate(d) || !bucketOk(d)) continue
                if (!matches(query, title, note.opt("body"), note.opt("comments"))) continue
                entries.add(d to HistoryEntry.Dated(id, d, title))
            }
        }
        return entries.sortedByDescending { it.first }.map { it.second }
    }
}
