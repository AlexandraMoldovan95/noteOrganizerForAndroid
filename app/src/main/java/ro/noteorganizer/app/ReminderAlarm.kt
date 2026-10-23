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
 * itself for the next day. Inexact -- Android may hold it a few minutes to
 * spare the battery -- because an exact one needs a permission Google Play
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

    private fun settingsOf(context: Context): JSONObject? = try {
        JSONObject(File(notebook(context), "settings.json").readText(Charsets.UTF_8))
    } catch (e: Exception) {
        null
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
        if (settings == null || !Reminders.notifies(settings)) {
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
                Reminders.dueOn(File(notebook(context), "notes_data.json").readText(Charsets.UTF_8), today)
            } catch (e: Exception) {
                null
            }
            if (due != null && due > 0) post(context, due, today)
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
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications taken away between the question and now: nothing said.
        }
    }
}

/** The alarm, the phone's restart and the app's update: each reaches [ReminderAlarm]. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderAlarm.schedule(context)
            else -> ReminderAlarm.fire(context)
        }
    }
}
