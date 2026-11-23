package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The day's reminder: the desktop's bar, and the phone's notification.
 *
 * The desktop shows a bar once a day, at its start, when notes fall on that
 * day -- "🔔  You have 2 notes for today (Friday, 23 October)." -- and writes
 * the day into `reminder_last_shown`, so a second start that day shows
 * nothing (`maybe_show_reminder`). The phone shows the same bar by the same
 * rule and key. And, when asked to, it posts a notification at an hour the
 * person chose, with the app closed: a phone's own way of reminding, which
 * the desktop has no use for (the owner's ask, 2026-10-23).
 */
object Reminders {

    /** Shared with the desktop, by its name: the day the bar was last shown. */
    const val LAST_SHOWN = "reminder_last_shown"

    /** The phone's own: whether to notify, and at what hour (0 to 23). */
    const val PHONE_NOTIFY = "phone_reminder_notify"
    const val PHONE_HOUR = "phone_reminder_hour"
    const val DEFAULT_HOUR = 9

    /**
     * The phone's news after which the next reminder is set again, rather
     * than shown: a restart and an update, which clear an app's alarms, and
     * a new time zone or clock. The alarm is an instant, worked out for the
     * hour in the zone it was set in; carried to another zone it rang at the
     * old zone's hour -- 03:00 for a 09:00 reminder taken to New York.
     */
    val RESCHEDULE_ON = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.TIME_SET",
    )

    /**
     * Of those, the one after which the alarm set stays right: a new clock keeps its
     * instant at the person's hour; a new time zone does not (the old zone's hour).
     */
    val KEEP_ALARMS = setOf("android.intent.action.TIME_SET")

    /** How many notes fall on [day], each day of a repeat included -- what the calendar draws on it. */
    fun dueOn(notes: List<JSONObject>, day: String): Int = Days.notesOn(notes, day).size

    /**
     * The same count read from the notes file's text, for the notification,
     * which runs with the app closed and must not write a thing: null when
     * the text is not a notebook, and then nothing is said.
     */
    fun dueOn(notesFile: String, day: String): Int? {
        val array = try {
            JSONArray(notesFile)
        } catch (e: Exception) {
            return null
        }
        // Settled as the app's load settles it, in memory alone: a file in an
        // older shape keeps it until the first save, and the count differed
        // from the calendar's on the days the read changes.
        for (i in 0 until array.length()) (array.opt(i) as? JSONObject)?.let { Notebook.settleAsRead(it) }
        Notebook.settleAside(array)
        return dueOn((0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }, day)
    }

    /** Whether today's bar is shown: notes on the day, and the bar not shown yet that day. */
    fun barDue(settings: JSONObject, today: String, due: Int): Boolean =
        due > 0 && settings.opt(LAST_SHOWN) != today

    fun notifies(settings: JSONObject): Boolean = settings.opt(PHONE_NOTIFY) == true

    /** The hour chosen, or nine: anything but a whole hour of the day reads as the default. */
    fun hour(settings: JSONObject): Int {
        val value = settings.opt(PHONE_HOUR) as? Number ?: return DEFAULT_HOUR
        val hour = value.toInt()
        return if (hour.toDouble() == value.toDouble() && hour in 0..23) hour else DEFAULT_HOUR
    }

    /** When the notification is next due: today at [hour] while that is still to come, else tomorrow. */
    fun nextAt(now: LocalDateTime, hour: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(hour, 0))
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    /** "09:00": an hour as the settings page and its choices show it. */
    fun showHour(hour: Int): String = String.format(java.util.Locale.US, "%02d:00", hour)
}
