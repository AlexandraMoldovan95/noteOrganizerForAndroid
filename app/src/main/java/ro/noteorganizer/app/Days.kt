package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The calendar's days: which notes fall on them, how a day is painted and
 * marked, and what can be done to ONE day of a repeating note without
 * touching the rest of its series. The desktop's calendar view and its
 * editor's day mode, as rules.
 *
 * A day of a series is edited on its own as the desktop edits one: its
 * title, text, comments, category and colour go into `overrides[day]`,
 * and nothing else -- the date, the repeat, the checklist and the files
 * belong to the series. A day whose edit says exactly what the series
 * says keeps no copy, and follows the series again.
 */
object Days {

    private const val NOWHERE = "There is nowhere to keep the notes."
    private const val UNREADABLE = "Your notes file could not be read, so nothing will be written over it."
    private const val GONE = "This note is no longer in the notes file."

    /** settings.json: a painted day's colour, and a day's markers. */
    const val DAY_COLORS = "day_colors"
    const val DAY_EMOJI = "day_emoji"
    const val MAX_MARKERS = 3

    /**
     * The desktop's ceilings on a calendar's decorations: at most 20,000
     * painted days and as many marked -- every day of 54 years -- and a
     * marker an emoji's length. A backup's settings came in whole: 700,000
     * "day colours" under keys that were no dates, markers of 200,000
     * letters, all read again at every month drawn and rewritten at every
     * setting changed.
     */
    const val MAX_DAY_DECORATIONS = 20_000
    const val MAX_DAY_MARKER_CHARS = 16

    /** A day as the calendar writes one, YYYY-MM-DD and real: the desktop's `is_calendar_day`. */
    fun isCalendarDay(day: String): Boolean = Recurrence.parseDate(day)?.let { Recurrence.formatDate(it) == day } == true

    /** The desktop's small set for flagging a day -- not the whole emoji picker. */
    val MARKERS = listOf("⭐", "❤", "❗", "🎉", "✅", "🔴", "🟡", "🟢", "💡", "🎂", "✈", "📌",
                         "🔔", "🎁", "💰", "🩺", "🍽", "🚗")

    private const val VARIATION_SELECTOR = "️"

    // ---- the day turning ----

    /**
     * Milliseconds from [now] to the first moment of the next date where
     * [now] is -- midnight, or the hour a clock change starts that day at
     * where midnight is skipped. Never 0: a day that has just begun waits
     * for the next one.
     */
    fun untilNextDay(now: java.time.ZonedDateTime): Long =
        java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis()

    // ---- the month ----

    /**
     * The month as rows of weeks, Monday first, a 0 where the row holds a
     * day of the month before or after -- Python's `calendar.monthcalendar`.
     */
    fun weeks(year: Int, month: Int): List<List<Int>> {
        val first = LocalDate.of(year, month, 1)
        val lead = first.dayOfWeek.value - 1
        val days = first.lengthOfMonth()
        val cells = List(lead) { 0 } + (1..days).toList()
        val padded = cells + List((7 - cells.size % 7) % 7) { 0 }
        return padded.chunked(7)
    }

    /** Every note on every day of the month: worked out once per drawing. */
    fun notesByDate(notes: List<JSONObject>, year: Int, month: Int): Map<String, List<JSONObject>> {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        val out = LinkedHashMap<String, MutableList<JSONObject>>()
        for (note in notes) {
            for (day in Recurrence.expand(note, first, last)) out.getOrPut(day) { ArrayList() }.add(note)
        }
        return out
    }

    /** The notes on one day, in the notebook's order. */
    fun notesOn(notes: List<JSONObject>, day: String): List<JSONObject> {
        val d = Recurrence.parseDate(day) ?: return emptyList()
        return notes.filter { Recurrence.expand(it, d, d).isNotEmpty() }
    }

    /**
     * A note's colour on the calendar: its own, or its category's, or the
     * theme's accent -- the desktop's `get_note_color`. Asked of the note
     * as it is on the day, so a day's own colour shows.
     */
    fun colourOf(note: JSONObject, categories: Map<String, String>, accent: String): String {
        val own = note.opt("color") as? String
        if (!own.isNullOrEmpty()) return own
        val category = note.opt("category") as? String ?: Themes.PROTECTED_CATEGORY
        return categories[category] ?: accent
    }

    // ---- painted days and their markers ----

    /** The painted days, as kept: real days and real colours only, [MAX_DAY_DECORATIONS] at most. */
    fun dayColours(settings: JSONObject?): Map<String, String> {
        val saved = settings?.opt(DAY_COLORS) as? JSONObject ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (day in saved.keys()) {
            if (out.size >= MAX_DAY_DECORATIONS) break
            if (!isCalendarDay(day)) continue
            val colour = Notebook.sanitizeColor(saved.opt(day) as? String ?: "")
            if (colour.isNotEmpty()) out[day] = colour
        }
        return out
    }

    /** A day's markers as kept: up to three, each an emoji's length and without
     *  the variation selector older pickers wrote. One marker alone is the
     *  older format. */
    fun cleanMarkers(value: Any?): List<String> {
        val list = when (value) {
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it) as? String }
            else -> return emptyList()
        }
        return list.map { it.replace(VARIATION_SELECTOR, "") }
            .filter { it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_DAY_MARKER_CHARS }.take(MAX_MARKERS)
    }

    fun dayMarkers(settings: JSONObject?): Map<String, List<String>> {
        val saved = settings?.opt(DAY_EMOJI) as? JSONObject ?: return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        for (day in saved.keys()) {
            if (out.size >= MAX_DAY_DECORATIONS) break
            if (!isCalendarDay(day)) continue
            val kept = cleanMarkers(saved.opt(day))
            if (kept.isNotEmpty()) out[day] = kept
        }
        return out
    }

    /**
     * The painted days with [day] painted [colour] -- or unpainted, for "".
     * Null when [day] is new and the calendar holds [MAX_DAY_DECORATIONS]
     * already: refused, so a start never drops one a person made.
     */
    fun withColour(settings: JSONObject?, day: String, colour: String): JSONObject? {
        val out = JSONObject()
        val kept = dayColours(settings)
        for ((d, c) in kept) if (d != day) out.put(d, c)
        val clean = Notebook.sanitizeColor(colour)
        if (clean.isNotEmpty()) {
            if (day !in kept && kept.size >= MAX_DAY_DECORATIONS) return null
            out.put(day, clean)
        }
        return out
    }

    /** The markers with [day] holding [markers] -- none removes the day; null, as [withColour]. */
    fun withMarkers(settings: JSONObject?, day: String, markers: List<String>): JSONObject? {
        val out = JSONObject()
        val kept = dayMarkers(settings)
        for ((d, m) in kept) if (d != day) out.put(d, JSONArray(m))
        val clean = cleanMarkers(JSONArray(markers))
        if (clean.isNotEmpty()) {
            if (day !in kept && kept.size >= MAX_DAY_DECORATIONS) return null
            out.put(day, JSONArray(clean))
        }
        return out
    }

    // ---- one day of a series ----

    private fun edit(load: Notebook.Load, id: Int, change: (JSONObject) -> String?): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val index = Organizing.indexOfId(array, id)
        if (index < 0) return SaveResult.Failed(GONE)
        val candidate = JSONArray(array.toString())
        val note = candidate.getJSONObject(index)
        change(note)?.let { return SaveResult.Failed(it) }
        note.put("updated", Files.now())
        // Days a series' copy set aside, notes of their own in the same write.
        Notebook.settleAside(candidate)
        Organizing.write(file, candidate)?.let { return SaveResult.Failed(it) }
        Organizing.replace(array, candidate)
        return SaveResult.Ok(id)
    }

    private fun overridesOf(note: JSONObject): JSONObject =
        note.opt("overrides") as? JSONObject ?: JSONObject().also { note.put("overrides", it) }

    /**
     * What a save of one day wrote: its own edit, or -- when the day says
     * what the series says -- none, and the day follows the series again.
     */
    enum class DaySaved { OWN, FOLLOWS }

    /**
     * Saves [day] of a repeating note on its own: the five fields a day may
     * hold, shaped as a load shapes them, and nothing of the series. The
     * desktop's `save_current_note` in day mode. [result] receives which of
     * the two it was, for the screen to say.
     */
    fun saveDay(load: Notebook.Load, id: Int, day: String, title: String, body: String,
                comments: String, category: String, color: String,
                result: ((DaySaved) -> Unit)? = null): SaveResult {
        var outcome = DaySaved.OWN
        val written = edit(load, id) { note ->
            if (!Recurrence.isRecurring(note)) return@edit "This note no longer repeats."
            val patch = Normalize.cleanOverride(JSONObject()
                .put("title", Notebook.pyStrip(title)).put("body", body).put("comments", comments)
                .put("category", category).put("color", color))
            // A colour cleared on one day of a coloured series stays cleared
            // in the copy the editor compares with; the next load gives the
            // day the series' colour again, as on the desktop.
            if (color.isEmpty() && !(note.opt("color") as? String).isNullOrEmpty()) patch.put("color", "")
            val overrides = overridesOf(note)
            if (Recurrence.dayMatchesSeries(note, patch)) {
                overrides.remove(day)
                outcome = DaySaved.FOLLOWS
            } else {
                overrides.put(day, patch)
            }
            null
        }
        if (written is SaveResult.Ok) result?.invoke(outcome)
        return written
    }

    /** What [deleteDay] took away, for its Undo to give back. */
    data class DayGone(val id: Int, val uid: String, val day: String, val ownEdit: JSONObject?, val cameFrom: String?)

    /**
     * Takes one day out of a series: its date (the date the rule gives it,
     * for a moved day) joins the deleted days, and its own edit and its
     * move go with it -- kept in the answer for the Undo.
     */
    fun deleteDay(load: Notebook.Load, id: Int, day: String): Pair<SaveResult, DayGone?> {
        var gone: DayGone? = null
        val result = edit(load, id) { note ->
            if (!Recurrence.isRecurring(note)) return@edit "This note no longer repeats."
            if (!givenDay(note, day)) return@edit NOT_A_DAY
            val origin = Recurrence.movedFrom(note, day)
            if (origin != null) (note.opt("moved") as? JSONObject)?.remove(origin)
            val exceptions = sortedSetOf<String>()
            (note.opt("exceptions") as? JSONArray)?.let { a ->
                for (i in 0 until a.length()) (a.opt(i) as? String)?.let { exceptions.add(it) }
            }
            exceptions.add(origin ?: day)
            note.put("exceptions", JSONArray(exceptions.toList()))
            val own = (note.opt("overrides") as? JSONObject)?.remove(day) as? JSONObject
            gone = DayGone(id, note.opt("uid") as? String ?: "", day, own, origin)
            null
        }
        return result to (if (result is SaveResult.Ok) gone else null)
    }

    /**
     * The id of the note a day's action was taken on, as it stands now
     * ([Organizing.indexOfNote]): by the first note wearing its identity,
     * an Undo went to the other of two notes wearing one.
     */
    private fun idOf(array: JSONArray, id: Int, uid: String): Int? =
        Organizing.indexOfNote(array, id, uid).takeIf { it >= 0 }
            ?.let { ((array.opt(it) as? JSONObject)?.opt("id") as? Number)?.toInt() }

    /**
     * The Undo of [deleteDay]: the day back, its own edit and its move with
     * it -- on the note with the same identity, which an id freed since may
     * no longer name.
     */
    fun undoDeleteDay(load: Notebook.Load, gone: DayGone): SaveResult {
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val id = idOf(array, gone.id, gone.uid) ?: return SaveResult.Failed("That note is no longer here.")
        return edit(load, id) { note ->
            if (!Recurrence.isRecurring(note)) return@edit "That note no longer repeats, so the day can't come back."
            val skipped = gone.cameFrom ?: gone.day
            // Back only onto a date the series holds nothing on now, as a move
            // comes home ([dayIsFree]): another day moved there since was hidden
            // under it, its words read as this day's and this day's own lost
            // (the final audit's twelfth round). The delete took this day's edit.
            val on = Recurrence.parseDate(gone.day)
            val stillSkipped = (note.opt("exceptions") as? JSONArray)?.let { a ->
                (0 until a.length()).any { a.opt(it) == skipped } } == true
            val drawn = on != null && Recurrence.expand(note, on, on) == listOf(gone.day)
            if (stillSkipped && on != null && (drawn || keepsWordsOn(note, gone.day))) {
                return@edit if (drawn) {
                    "Another day of this series is on ${Recurrence.showDay(on)} now, so that day can't come back."
                } else {
                    "This series keeps text or a colour of its own on ${Recurrence.showDay(on)} now, so that day can't come back."
                }
            }
            val exceptions = (note.opt("exceptions") as? JSONArray)?.let { a ->
                (0 until a.length()).mapNotNull { a.opt(it) as? String }.filter { it != skipped }
            } ?: emptyList()
            note.put("exceptions", JSONArray(exceptions))
            if (gone.cameFrom != null) {
                val moved = note.opt("moved") as? JSONObject ?: JSONObject().also { note.put("moved", it) }
                moved.put(gone.cameFrom, gone.day)
            }
            // The desktop's `_undo_occurrence_delete` guards: the repeat
            // changed since may no longer give the day -- back, it would be
            // a day off the schedule nobody made -- and an edit the day has
            // gained since is newer than the one taken away.
            if (!givenDay(note, gone.day)) return@edit "The repeat no longer has that day, so it can't come back."
            gone.ownEdit?.let { if (!overridesOf(note).has(gone.day)) overridesOf(note).put(gone.day, JSONObject(it.toString())) }
            null
        }
    }

    /**
     * One day back to following the series, the desktop's "Reset this day":
     * its own edit dropped ([dropText]) and, for a day moved to another
     * date, the day back on the date its rule gives it ([sendHome]). "Only
     * the date" is [dropText] false: the day goes home with its text.
     */
    fun resetDay(load: Notebook.Load, id: Int, day: String,
                 dropText: Boolean = true, sendHome: Boolean = true): SaveResult = edit(load, id) { note ->
        if (!Recurrence.isRecurring(note)) return@edit "This note no longer repeats."
        val origin = Recurrence.movedFrom(note, day)
        if (origin != null && sendHome) {
            // Home only when nothing else is there now (see dayIsFree).
            if (!dayIsFree(note, day, origin)) {
                val home = Recurrence.parseDate(origin)
                val shown = home?.let { Recurrence.showDay(it) } ?: origin
                // Said as it is: a day there, or an edit alone (the twelfth round).
                return@edit if (home != null && Recurrence.expand(note, home, home) != listOf(origin)) {
                    "This series keeps text or a colour of its own on $shown, so this day stays where it is."
                } else {
                    "Another day of this series is on $shown now, so this day stays where it is."
                }
            }
            Recurrence.moveDay(note, day, origin)
            if (dropText) (note.opt("overrides") as? JSONObject)?.remove(origin)
        } else if (dropText) {
            (note.opt("overrides") as? JSONObject)?.remove(day)
        }
        null
    }

    // ---- a day held on another date ----

    /**
     * Whether [note]'s day now on [day] may be held on [to]: a date no
     * other day of the series is on -- two days on one date would be one.
     *
     * The desktop's `_day_is_free` also takes the day's own date as free,
     * whatever is on it now. But while a day is away, another can be moved
     * onto its date; the first one's return then hid the second -- its move
     * still in the file, drawn nowhere, because a moved day is not drawn
     * where the series already has one. So its own date is free here only
     * when it is empty, as any other.
     */
    /**
     * Whether the series draws a day on [day] -- a day its rule gives, or one
     * moved there. A day off the schedule is not: moved or deleted, it went
     * where no calendar on either app would show it, its own text in the
     * file and on no screen.
     */
    fun givenDay(note: JSONObject, day: String): Boolean {
        val date = Recurrence.parseDate(day) ?: return false
        return Recurrence.expand(note, date, date) == listOf(day)
    }

    private const val NOT_A_DAY =
        "This day is no longer one of the series' days. Open the series to settle the days off its schedule."

    fun dayIsFree(note: JSONObject, day: String, to: String): Boolean {
        val target = Recurrence.parseDate(to) ?: return false
        return to == day || (Recurrence.expand(note, target, target) != listOf(to) && !keepsWordsOn(note, to))
    }

    /**
     * Whether the series keeps an edit of its own under [date] that it draws
     * no day on -- a day off its schedule, or one deleted with its edit
     * kept. A day moved there would take that edit's place: its words were
     * written over, and an Undo took the day back without them (the final
     * audit's eleventh round).
     */
    fun keepsWordsOn(note: JSONObject, date: String): Boolean =
        (note.opt("overrides") as? JSONObject)?.has(date) == true

    private fun keptThere(target: java.time.LocalDate) =
        "This series still keeps text or a colour of its own for a day on ${Recurrence.showDay(target)}, " +
            "which its calendar no longer shows. A day can move only to a date the series holds nothing for."

    /** What [moveDay] did, for its Undo to undo: the date the rule gives the day, and where it was and went. */
    data class DayMove(val id: Int, val uid: String, val origin: String, val from: String, val to: String)

    /**
     * Holds the day of a series now on [day] on [to] instead, still one day
     * of its series -- the desktop's "📅 Move this day…", for a weekly
     * meeting held on another day one week. Its own edit goes with it. A
     * date another day of the series is on is refused, in the desktop's
     * words; [to] being [day] already is nothing to do.
     */
    fun moveDay(load: Notebook.Load, id: Int, day: String, to: String): Pair<SaveResult, DayMove?> {
        val target = Recurrence.parseDate(to) ?: return SaveResult.Failed("That is not a date.") to null
        if (to == day) return SaveResult.Ok(id) to null
        var move: DayMove? = null
        val result = edit(load, id) { note ->
            if (!Recurrence.isRecurring(note)) return@edit "This note no longer repeats."
            if (!givenDay(note, day)) return@edit NOT_A_DAY
            if (!dayIsFree(note, day, to)) {
                if (Recurrence.expand(note, target, target) != listOf(to)) return@edit keptThere(target)
                return@edit "This series already has a day on ${Recurrence.showDay(target)}. " +
                    "A day can move only to a date the series does not have."
            }
            val origin = Recurrence.moveDay(note, day, to)
            move = DayMove(id, note.opt("uid") as? String ?: "", origin, day, to)
            null
        }
        return result to (if (result is SaveResult.Ok) move else null)
    }

    /**
     * The Undo of [moveDay]: the day back where it was -- if it is still the
     * day the rule gave it, still on the date it went to, and the date it
     * left is still free. The note is found by its identity, which an id
     * freed since may no longer name. The desktop's `_undo_day_move`.
     */
    fun undoMoveDay(load: Notebook.Load, move: DayMove): SaveResult {
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val id = idOf(array, move.id, move.uid) ?: return SaveResult.Failed("That note is no longer here.")
        return edit(load, id) { note ->
            val on = Recurrence.parseDate(move.to)
            if (!Recurrence.isRecurring(note) || on == null ||
                (Recurrence.movedFrom(note, move.to) ?: move.to) != move.origin ||
                Recurrence.expand(note, on, on) != listOf(move.to)) {
                return@edit "That day has moved on since, so it stays where it is."
            }
            if (!dayIsFree(note, move.to, move.from)) {
                return@edit "Another day of this series is there now, so this one stays where it is."
            }
            Recurrence.moveDay(note, move.to, move.from)
            null
        }
    }

    // ---- days off the schedule ----

    /** What becomes of a day off the schedule: a note of its own, or that note in the Trash. */
    enum class Settle { NOTE, TRASH }

    /**
     * One day a series keeps its own edits for, which its repeat no longer
     * lands on -- so no calendar shows it -- made a note of its own on its
     * date, or that note moved straight to the Trash; either way taken out
     * of the series. The desktop's `_settle_off_schedule_day`: the new note
     * holds what the day showed -- title, text, comments, category, colour
     * -- the series' formatting only where the day's text is the series' own
     * (its offsets would land on the wrong letters anywhere else), and none
     * of the checklist and the files, which were never the day's. A move
     * that brought the day here goes too, or a rule brought back would draw
     * it again. The Trash is written before the notes, as every delete is.
     */
    fun settleOffScheduleDay(load: Notebook.Load, id: Int, day: String, how: Settle): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val index = Organizing.indexOfId(array, id)
        if (index < 0) return SaveResult.Failed(GONE)
        val candidate = JSONArray(array.toString())
        val note = candidate.getJSONObject(index)
        val overrides = note.opt("overrides") as? JSONObject
        if (!Recurrence.isRecurring(note) || overrides?.has(day) != true) {
            return SaveResult.Failed("That day keeps nothing of its own any more.")
        }
        // What the day holds: the note made of it carries the day's words.
        val shown = Recurrence.dayAsKept(note, day)
        val sameBody = Normalize.sameBody(shown.opt("body"), note.opt("body"))
        val stamp = Files.now()
        val freshId = Organizing.ids(array).next()
        val fresh = Normalize.normalizeNote(JSONObject()
            .put("id", freshId)
            .put("title", shown.opt("title") as? String ?: "")
            .put("body", shown.opt("body") as? String ?: "")
            .put("formatting", if (sameBody) JSONArray((note.opt("formatting") as? JSONArray)?.toString() ?: "[]") else JSONArray())
            .put("paragraphs", JSONArray((note.opt("paragraphs") as? JSONArray)?.toString() ?: "[]"))
            .put("comments", shown.opt("comments") as? String ?: "")
            .put("category", (shown.opt("category") as? String)?.ifEmpty { null } ?: note.opt("category") ?: "")
            .put("color", shown.opt("color") as? String ?: "")
            .put("date", day)
            .put("created", stamp)
            .put("updated", stamp))
        overrides.remove(day)
        Recurrence.movedFrom(note, day)?.let { (note.opt("moved") as? JSONObject)?.remove(it) }
        note.put("updated", stamp)
        if (how == Settle.TRASH) {
            val trashFile = load.trashFile ?: return SaveResult.Failed("There is nowhere to keep the Trash.")
            if (trashFile.exists() && load.trashRaw == null) {
                return SaveResult.Failed("The Trash could not be read, so nothing was changed in it.")
            }
            val trash = JSONArray((load.trashRaw ?: JSONArray()).toString()).put(JSONObject(fresh.toString()).put("deleted_at", stamp))
            Organizing.write(trashFile, trash)?.let { return SaveResult.Failed(it) }
            load.trashRaw?.let { Organizing.replace(it, trash) }
        } else {
            candidate.put(fresh)
        }
        Organizing.write(file, candidate)?.let { return SaveResult.Failed(it) }
        Organizing.replace(array, candidate)
        return SaveResult.Ok(freshId)
    }

    /**
     * Whether turning the repeat off would take text a day holds on its own
     * -- the desktop's `_repeat_off_loses_days`. A day differing only in its
     * colour or category loses no text.
     */
    internal fun repeatOffLosesDays(note: JSONObject): Boolean =
        Recurrence.isRecurring(note) && Notebook.daysHoldOwnText(note)

    /** What a turn-off that took days' own text says of the copy its save left. */
    enum class OffWord { SERIES_KEPT, UPDATE_KEPT, NO_COPY }

    /**
     * What the Repeat sheet says of a turn-off, asked of [note] as its save
     * left it -- the desktop's `_repeat_off_line`: the copy it kept is the
     * series, or, after an import's Update, the note as it was before that,
     * which Restore previous brings back instead (the ninth round). A copy
     * the note holds again in full is dropped by the same save
     * ([Notebook.dropSpentCopy]): no Restore previous is promised for it.
     * Said from the copy's kind alone, the sheet promised a button that was
     * gone (the tenth round, the desktop's 04044ce).
     */
    internal fun repeatOffWord(note: JSONObject?): OffWord = when {
        note == null || !Notebook.holdsUnspentCopy(note) -> OffWord.NO_COPY
        Notebook.keepsUpdateCopy(note) -> OffWord.UPDATE_KEPT
        else -> OffWord.SERIES_KEPT
    }

    /**
     * Sets a note's repeat, or turns it off with [rule] null or a rule
     * that does not repeat. A repeating note needs a first day. Turned off,
     * when a day held text of its own, the note as it was goes into the
     * rescue copy first, repeat and days included, so "↩ Restore previous"
     * brings the series back whole -- and the days stay in the note as well,
     * inert while it does not repeat, back if a repeat is: one place for
     * them, the note, as the desktop keeps them (the owner's choice, the
     * final audit's sixth round). Kept only in the copy, as up to 0.12.0,
     * the desktop's next copy of words erased them. Days none of which holds
     * text of its own -- skipped days, a colour -- go, as the desktop's save
     * clears them, the copy aside.
     */
    /**
     * [startWas] is the series' start before the sheet's Save wrote a new
     * one, which it does first: the rescue copy keeps the series as it was,
     * as the desktop's `save_current_note` takes it before the new date --
     * taken after, "Restore previous" brought back a series starting on the
     * new date, its days' own edits off its schedule.
     */
    fun setRepeat(load: Notebook.Load, id: Int, rule: JSONObject?, startWas: Any? = null): SaveResult = edit(load, id) { note ->
        val clean = Recurrence.normalize(rule)
        val repeats = clean.getString("freq").isNotEmpty()
        if (repeats && Recurrence.parseDate(note.opt("date")) == null) {
            return@edit "A repeating note needs a start date. Pick a date first."
        }
        if (!repeats) {
            if (Recurrence.isRecurring(note)) {
                val was = if (startWas != null) JSONObject(note.toString()).put("date", startWas) else note
                // The words of a big delete's copy the note still needs go
                // into the turn-off's copy with the series; an import's
                // Update copy is kept whole, given the series where it lacks
                // it (Notebook.turnOffCopy). Asked of the note as it was
                // before this Save, as the desktop's save asks it.
                if (repeatOffLosesDays(was)) {
                    // Another series' copy, replaced here, held its days
                    // alone: set aside first, asked of the series as it was.
                    Notebook.copyDaysSetAside(note, was)
                    if (was !== note) (note.opt("_prev") as? JSONObject)?.let { was.put("_prev", JSONObject(it.toString())) }
                    note.put("_prev", Notebook.turnOffCopy(wholeNoteCopy(was), was))
                }
            }
            // The desktop's `if not freq and not days_kept`: asked of the
            // days the note holds, whether it repeated or not.
            if (!Notebook.daysHoldOwnText(note)) Notebook.clearDays(note)
        }
        // Turned back on with another start or rule than the series had (the
        // sheet wrote the new start first): its old deleted days, which hold
        // nothing, no longer hide the new series' own -- its rescue copy keeps
        // them -- and days with words of their own stay (Notebook.keptDaysFit).
        if (!Notebook.keptDaysFit(note, note.opt("date"), clean)) note.put("exceptions", JSONArray())
        note.put("recurrence", clean)
        // A day moved onto a date the new rule gives goes home now, not at
        // the next start (Normalize.movesHome, the desktop's moves_home).
        Normalize.movesHome(note)
        // Turned back on as the series its copy holds: the note holds the
        // days again, and the copy's are spent -- kept there, a day reset
        // after came back at every start (the desktop's spend_series_days,
        // the final audit's eighth round).
        Notebook.spendSeriesDays(note)
        // Back on as the series was, the note holds again all its copy kept:
        // spent, as the desktop's save drops it, the banner with it.
        Notebook.dropSpentCopy(note)
        null
    }

    /** The note as it is -- its text, date, repeat and days -- as the rescue copy. */
    private fun wholeNoteCopy(note: JSONObject): JSONObject {
        val prev = JSONObject()
        for (key in listOf("title", "body", "comments")) prev.put(key, note.opt(key) as? String ?: "")
        prev.put("formatting", JSONArray((note.opt("formatting") as? JSONArray)?.toString() ?: "[]"))
        prev.put("paragraphs", JSONArray((note.opt("paragraphs") as? JSONArray)?.toString() ?: "[]"))
        for (key in listOf("date", "recurrence", "exceptions", "overrides", "moved")) {
            val value = note.opt(key)
            prev.put(key, when (value) {
                is JSONObject -> JSONObject(value.toString())
                is JSONArray -> JSONArray(value.toString())
                null -> JSONObject.NULL
                else -> value
            })
        }
        return prev
    }
}
