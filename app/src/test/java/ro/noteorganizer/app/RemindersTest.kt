package ro.noteorganizer.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The day's reminder: what it counts, when the bar shows, when the notification is next due. */
class RemindersTest {

    @Test
    fun `a new time zone or clock sets the next reminder again, as a restart does, and does not ring it`() {
        // Kept as an instant, a 09:00 reminder carried from Bucharest to New
        // York rang at 03:00 until the app was next opened.
        for (action in listOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED",
                              "android.intent.action.TIMEZONE_CHANGED", "android.intent.action.TIME_SET")) {
            assertTrue(action, action in Reminders.RESCHEDULE_ON)
        }
        assertFalse("the alarm itself rings", "ro.noteorganizer.app.REMIND" in Reminders.RESCHEDULE_ON)
    }

    // A Monday series from 5 October, a note on Friday 23 October, one undated.
    private val notes = """[
        {"id": 1, "uid": "s", "title": "Stand-up", "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]}},
        {"id": 2, "uid": "d", "title": "Dentist", "date": "2026-10-23"},
        {"id": 3, "uid": "u", "title": "Ideas", "date": ""}
    ]"""

    @Test
    fun `a day counts its own notes and each day of a repeat, as the calendar draws them`() {
        assertEquals(1, Reminders.dueOn(notes, "2026-10-23"))
        assertEquals("a Monday of the series", 1, Reminders.dueOn(notes, "2026-10-26"))
        assertEquals("a Tuesday", 0, Reminders.dueOn(notes, "2026-10-27"))
        assertEquals("before the series began", 0, Reminders.dueOn(notes, "2026-09-28"))
    }

    @Test
    fun `a notes file that is not a notebook says nothing`() {
        assertNull(Reminders.dueOn("{ half a file", "2026-10-23"))
        assertEquals(0, Reminders.dueOn("[]", "2026-10-23"))
    }

    @Test
    fun `the bar shows once a day, and only on a day with notes, by the desktop's key`() {
        val fresh = JSONObject()
        assertTrue(Reminders.barDue(fresh, "2026-10-23", 2))
        assertFalse("no notes, no bar", Reminders.barDue(fresh, "2026-10-23", 0))
        val shown = JSONObject().put("reminder_last_shown", "2026-10-23")
        assertFalse("shown already today", Reminders.barDue(shown, "2026-10-23", 2))
        assertTrue("shown yesterday", Reminders.barDue(shown, "2026-10-24", 2))
    }

    @Test
    fun `the notification is off until turned on, at nine unless an hour is chosen`() {
        assertFalse(Reminders.notifies(JSONObject()))
        assertFalse("anything but true is off", Reminders.notifies(JSONObject().put("phone_reminder_notify", "yes")))
        assertTrue(Reminders.notifies(JSONObject().put("phone_reminder_notify", true)))
        assertEquals(9, Reminders.hour(JSONObject()))
        assertEquals(7, Reminders.hour(JSONObject().put("phone_reminder_hour", 7)))
        assertEquals("not an hour of the day", 9, Reminders.hour(JSONObject().put("phone_reminder_hour", 24)))
        assertEquals("not a whole hour", 9, Reminders.hour(JSONObject().put("phone_reminder_hour", 7.5)))
        assertEquals("not a number", 9, Reminders.hour(JSONObject().put("phone_reminder_hour", "7")))
    }

    @Test
    fun `the next notification is today while its hour is still to come, else tomorrow`() {
        val morning = LocalDateTime.of(2026, 10, 23, 8, 59)
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), Reminders.nextAt(morning, 9))
        // Fired at nine sharp, the next is tomorrow's -- never the same one again.
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Reminders.nextAt(LocalDateTime.of(2026, 10, 23, 9, 0), 9))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Reminders.nextAt(LocalDateTime.of(2026, 10, 23, 21, 0), 9))
        // The last day of a month and of a year.
        assertEquals(LocalDateTime.of(2027, 1, 1, 7, 0), Reminders.nextAt(LocalDateTime.of(2026, 12, 31, 22, 0), 7))
        assertEquals("09:00", Reminders.showHour(9))
        assertEquals("21:00", Reminders.showHour(21))
    }
}
