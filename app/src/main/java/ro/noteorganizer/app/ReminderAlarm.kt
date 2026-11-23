package ro.noteorganizer.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The phone's daily notification ([Reminders]): an alarm at the hour chosen,
 * which reads the notebook, says how many notes fall on the day, and sets
 * itself for the next day. Inexact -- Android 12 and later may hold it up to
 * an hour to spare the battery, Doze longer -- because an exact one needs a permission Google Play
 * reviews, for a reminder that does not need the minute.
 *
 * It only ever reads: no Trash purged, no file rescued or written, as the
 * app's own start would. A notebook it cannot read gets no notification.
 */
object ReminderAlarm {

    const val CHANNEL = "today"
    private const val ACTION_REMIND = "ro.noteorganizer.app.REMIND"
    /** What the notification's tap asks the app for: today, on the calendar. */
    const val ACTION_TODAY = "ro.noteorganizer.app.TODAY"
    private const val NOTIFICATION_ID = 1

    private fun notebook(context: Context) = File(context.filesDir, "notebook")

    // Held by the app's run in this process, not read again: held all run.
    private fun settingsOf(context: Context): JSONObject? = try {
        File(notebook(context), "settings.json").takeIf { !Files.writesHeld(it) }
            ?.let { file -> Files.parseOwn(file) { JSONObject(it) } }
    } catch (e: Exception) {
        null
    }

    /**
     * After a restart, an update or a new clock: set again from the settings,
     * or, they not read, at the default hour -- a restart clears an app's
     * alarms, so "the one set before" kept none, and the daily notification
     * stopped unsaid. Fired over settings still unread, it posts nothing. A new
     * clock clears none, and the one set at the person's hour stays.
     */
    fun rearm(context: Context, action: String? = null) {
        val settings = settingsOf(context)
        if (settings != null) return schedule(context, settings)
        if (action in Reminders.KEEP_ALARMS) return
        val at = Reminders.nextAt(LocalDateTime.now(), Reminders.DEFAULT_HOUR)
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarm(context))
    }

    private fun alarm(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /**
     * The next notification set, or none when it is off: asked at every
     * start, after the phone restarts or the app is updated (both clear an
     * app's alarms), when the setting changes, and after each one fires.
     * Setting it again replaces it, so asking twice costs nothing.
     */
    fun schedule(context: Context, settings: JSONObject? = settingsOf(context)) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // Not read (refused, or damaged): the answer is unknown, so the alarm set
        // before stays; cancelled, the daily notification stopped unsaid.
        if (settings == null) return
        if (!Reminders.notifies(settings)) {
            manager.cancel(alarm(context))
            return
        }
        val at = Reminders.nextAt(LocalDateTime.now(), Reminders.hour(settings))
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarm(context))
    }

    /** Fired: today's count said, if there is one and notifications are allowed, then tomorrow set. */
    fun fire(context: Context) {
        val settings = settingsOf(context)
        if (settings != null && Reminders.notifies(settings)) {
            val today = Recurrence.formatDate(LocalDate.now())
            val due = try {
                Files.parseOwn(File(notebook(context), "notes_data.json")) { Reminders.dueOn(it, today) }
            } catch (e: Exception) {
                null
            }
            if (due != null && due > 0) post(context, due, today)
        }
        if (settings == null) {
            // Unread at its own hour: the next set a day on, at the hour it fired.
            context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 24L * 60 * 60 * 1000, alarm(context))
            return
        }
        schedule(context, settings)
    }

    private fun post(context: Context, count: Int, today: String) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val day = Recurrence.parseDate(today)?.let { Recurrence.showDay(it, year = false) } ?: today
        // The count only, never a title: a notification shows on a locked
        // screen, and a note's words are the person's.
        val words = context.resources.getQuantityString(R.plurals.reminder_today, count, count, day)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(words)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Gone at the day's end: a day with no notes posts nothing, and
            // yesterday's stayed all day saying "for today".
            .setTimeoutAfter(Days.untilNextDay(java.time.ZonedDateTime.now()))
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications taken away between the question and now: nothing said.
        }
    }
}

/**
 * The alarm, and the news that sets it again ([Reminders.RESCHEDULE_ON]: a
 * restart, an update, a new time zone or clock): each reaches [ReminderAlarm].
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in Reminders.RESCHEDULE_ON) ReminderAlarm.rearm(context, intent.action) else ReminderAlarm.fire(context)
    }
}
