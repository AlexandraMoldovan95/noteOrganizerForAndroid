package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Settings → Statistics: a read-only snapshot of the notebook, figure for
 * figure the desktop's `_render_statistics`. Not another way to filter
 * notes -- nothing in it is a door.
 */
object Stats {

    /** settings.json: the day this install was first used, stamped once. */
    const val FIRST_LAUNCH_DATE = "first_launch_date"

    /** This week, this month, this year -- each counting from its start, so
     *  a year includes its month -- and older, the one true complement. */
    data class Periods(val week: Int, val month: Int, val year: Int, val older: Int)

    data class Snapshot(
        val total: Int,
        val categories: Int,
        val templates: Int,
        val withChecklist: Int,
        val inTrash: Int,
        val added: Periods,
        val updated: Periods,
        /** Category -> how many notes, in Settings' order, a stray name after. */
        val byCategory: List<Pair<String, Int>>,
        /** The first day, as the app shows dates; "—" when none was stamped. */
        val since: String,
    )

    private val STAMP = Regex("^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}) ([0-9]{1,2}):([0-9]{1,2})$")

    /**
     * The day of a stored moment, "YYYY-MM-DD HH:MM...", as the desktop
     * reads it: the first sixteen characters, a real day and a real time,
     * or nothing -- an odd stamp is skipped from that count, never a crash.
     */
    internal fun dayOf(raw: Any?): LocalDate? {
        val text = raw as? String ?: return null
        if (text.isEmpty()) return null
        val parts = STAMP.matchEntire(text.take(16)) ?: return null
        val (y, m, d, hh, mm) = parts.destructured
        if (hh.toInt() > 23 || mm.toInt() > 59) return null
        return try {
            LocalDate.of(y.toInt(), m.toInt(), d.toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun periods(notes: List<JSONObject>, field: String, today: LocalDate): Periods {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val monthStart = today.withDayOfMonth(1)
        val yearStart = today.withDayOfYear(1)
        var week = 0
        var month = 0
        var year = 0
        var older = 0
        for (note in notes) {
            val d = dayOf(note.opt(field)) ?: continue
            if (d >= weekStart) week++
            if (d >= monthStart) month++
            if (d >= yearStart) year++ else older++
        }
        return Periods(week, month, year, older)
    }

    fun of(notes: List<JSONObject>, trash: Int, templates: Int, categoryOrder: List<String>,
           settings: JSONObject?, today: LocalDate = LocalDate.now()): Snapshot {
        val counts = LinkedHashMap<String, Int>()
        for (note in notes) {
            val category = (note.opt("category") as? String)?.ifEmpty { null } ?: Themes.PROTECTED_CATEGORY
            counts[category] = (counts[category] ?: 0) + 1
        }
        val ordered = categoryOrder.filter { it in counts } + counts.keys.filter { it !in categoryOrder }
        val withChecklist = notes.count { ((it.opt("checklist") as? JSONArray)?.length() ?: 0) > 0 }
        val stamped = settings?.opt(FIRST_LAUNCH_DATE) as? String
        return Snapshot(
            total = notes.size,
            categories = counts.size,
            templates = templates,
            withChecklist = withChecklist,
            inTrash = trash,
            added = periods(notes, "created", today),
            updated = periods(notes, "updated", today),
            byCategory = ordered.map { it to counts.getValue(it) },
            since = Recurrence.showDate(stamped ?: "").ifEmpty { "—" },
        )
    }
}
