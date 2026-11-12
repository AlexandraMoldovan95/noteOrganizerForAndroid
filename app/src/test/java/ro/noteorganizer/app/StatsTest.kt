package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.nio.file.Files as NioFiles

/** Settings → Statistics, figure for figure the desktop's snapshot. */
class StatsTest {

    // Thursday 15 October 2026: its week began Monday 12th.
    private val today = LocalDate.of(2026, 10, 15)

    private fun notes(json: String): List<JSONObject> = JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test
    fun `added and updated count from the start of each period, older the complement`() {
        val list = notes("""[
            {"created": "2026-10-13 09:00", "updated": "2026-10-15 08:00"},
            {"created": "2026-10-02 09:00", "updated": "2026-10-02 09:00"},
            {"created": "2026-03-01 09:00:00", "updated": "2026-10-14 10:00"},
            {"created": "2025-12-31 23:59", "updated": "2025-12-31 23:59"},
            {"created": "not a stamp", "updated": ""},
            {"created": "2026-02-30 10:00", "updated": "2026-10-15 24:00"}
        ]""")
        val snap = Stats.of(list, trash = 0, templates = 0, categoryOrder = emptyList(), settings = null, today = today)
        assertEquals(Stats.Periods(week = 1, month = 2, year = 3, older = 1), snap.added)
        assertEquals("an odd stamp is skipped, never counted", Stats.Periods(week = 2, month = 3, year = 3, older = 1), snap.updated)
        assertEquals(6, snap.total)
    }

    @Test
    fun `the tiles and the split by category, Settings' order first, a stray name after`() {
        val list = notes("""[
            {"category": "Work", "checklist": [{"text": "a", "done": false}]},
            {"category": "Gone"},
            {"category": "General", "checklist": []},
            {"category": "Work"},
            {}
        ]""")
        val snap = Stats.of(list, trash = 4, templates = 2, categoryOrder = listOf("General", "Work"),
                            settings = JSONObject().put("first_launch_date", "2026-09-01"), today = today)
        assertEquals(listOf("General" to 2, "Work" to 2, "Gone" to 1), snap.byCategory)
        assertEquals("the tile is Settings' categories; a stray name is listed below it", 2, snap.categories)
        assertEquals(1, snap.withChecklist)
        assertEquals(4, snap.inTrash)
        assertEquals(2, snap.templates)
        assertEquals("01.09.2026", snap.since)
        assertEquals("—", Stats.of(list, 0, 0, emptyList(), JSONObject(), today).since)
    }

    @Test
    fun `the Categories tile counts every category, those holding no note too, as the desktop's`() {
        val list = notes("""[{"category": "General"}, {"category": "General"}, {}]""")
        val order = listOf("General", "Work", "Personal", "Ideas", "Urgent")
        val snap = Stats.of(list, trash = 0, templates = 0, categoryOrder = order, settings = null, today = today)
        assertEquals(5, snap.categories)
        assertEquals("the list below still shows only those holding notes", listOf("General" to 3), snap.byCategory)
    }

    @Test
    fun `a stamp's day is read only from a real day and a real time`() {
        assertEquals(LocalDate.of(2026, 10, 15), Stats.dayOf("2026-10-15 09:30:12"))
        assertEquals(LocalDate.of(2026, 1, 5), Stats.dayOf("2026-1-5 9:05"))
        assertNull(Stats.dayOf("2026-10-15"))
        assertNull(Stats.dayOf("2026-10-15 23:60"))
        assertNull(Stats.dayOf(null))
    }

    @Test
    fun `the first day this install was used is stamped once, and never again`() {
        val dir = NioFiles.createTempDirectory("stats").toFile()
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "settings.json").writeText("""{"first_launch_date": "2025-01-01"}""")
        Startup.prepare(dir, older = null)
        assertEquals("2025-01-01", JSONObject(File(dir, "settings.json").readText()).getString("first_launch_date"))
        val fresh = File(NioFiles.createTempDirectory("stats").toFile(), "notebook")
        Startup.prepare(fresh, older = null)
        assertEquals(Recurrence.formatDate(LocalDate.now()),
            JSONObject(File(fresh, "settings.json").readText()).getString("first_launch_date"))
    }
}
