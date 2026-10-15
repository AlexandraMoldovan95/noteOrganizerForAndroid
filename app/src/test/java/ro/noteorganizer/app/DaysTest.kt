package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/** The calendar's days, and one day of a series edited on its own. */
class DaysTest {

    private fun scratch(notes: String): Notebook.Load {
        val dir = NioFiles.createTempDirectory("days").toFile()
        File(dir, "notes_data.json").writeText(notes)
        return Notebook.load(dir)
    }

    private fun onDisk(load: Notebook.Load): JSONObject =
        JSONArray(load.file!!.readText()).getJSONObject(0)

    private val series = """[{"id": 1, "uid": "s", "title": "Stand-up", "body": "agenda", "comments": "",
        "category": "Work", "color": "", "date": "2026-10-05",
        "recurrence": {"freq": "weekly", "weekdays": [0]}, "checklist": [{"text": "x", "done": false}]}]"""

    // ---- the month ----

    @Test
    fun `a month is Monday-first weeks, as Python's calendar draws it`() {
        // October 2026 begins on a Thursday and has 31 days.
        val weeks = Days.weeks(2026, 10)
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 4), weeks.first())
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 0), weeks.last())
        assertEquals(5, weeks.size)
        // February 2027 begins on a Monday and fills four rows exactly.
        assertEquals(4, Days.weeks(2027, 2).size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), Days.weeks(2027, 2).first())
    }

    @Test
    fun `the month's notes by day, a series on every day it lands, a dated note on its own`() {
        val notes = listOf(
            JSONArray(series).getJSONObject(0),
            JSONObject("""{"id": 2, "date": "2026-10-12"}"""),
            JSONObject("""{"id": 3, "date": "2026-11-12"}"""),
            JSONObject("""{"id": 4}"""),
        )
        val byDate = Days.notesByDate(notes, 2026, 10)
        assertEquals(setOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"), byDate.keys)
        assertEquals(listOf(1, 2), byDate.getValue("2026-10-12").map { it.getInt("id") })
        assertEquals(listOf(1, 2), Days.notesOn(notes, "2026-10-12").map { it.getInt("id") })
        assertTrue(Days.notesOn(notes, "not a day").isEmpty())
    }

    @Test
    fun `a note's colour is its own, then its category's, then the accent`() {
        val categories = mapOf("Work" to "#4cc9f0")
        assertEquals("#ff0000", Days.colourOf(JSONObject("""{"color": "#ff0000", "category": "Work"}"""), categories, "#accent"))
        assertEquals("#4cc9f0", Days.colourOf(JSONObject("""{"color": "", "category": "Work"}"""), categories, "#accent"))
        assertEquals("#accent", Days.colourOf(JSONObject("""{"category": "Gone"}"""), categories, "#accent"))
    }

    // ---- painted days and markers ----

    @Test
    fun `painted days keep real colours only, and painting replaces or removes`() {
        val settings = JSONObject("""{"day_colors": {"2026-10-05": "#ff0000", "2026-10-06": "not a colour"}}""")
        assertEquals(mapOf("2026-10-05" to "#ff0000"), Days.dayColours(settings))
        val repainted = Days.withColour(settings, "2026-10-07", "#00ff00")
        assertEquals("#00ff00", repainted.getString("2026-10-07"))
        assertEquals("#ff0000", repainted.getString("2026-10-05"))
        assertFalse(Days.withColour(settings, "2026-10-05", "").has("2026-10-05"))
    }

    @Test
    fun `markers are three at most, the older single-marker format read, the selector dropped`() {
        val settings = JSONObject().put("day_emoji", JSONObject()
            .put("2026-10-05", JSONArray(listOf("⭐", "❤️", "🎉", "✅")))
            .put("2026-10-06", "🎂")
            .put("2026-10-07", JSONArray()))
        val markers = Days.dayMarkers(settings)
        assertEquals(listOf("⭐", "❤", "🎉"), markers["2026-10-05"])
        assertEquals(listOf("🎂"), markers["2026-10-06"])
        assertFalse(markers.containsKey("2026-10-07"))
        assertFalse(Days.withMarkers(settings, "2026-10-06", emptyList()).has("2026-10-06"))
        assertEquals(1, Days.withMarkers(settings, "2026-10-08", listOf("💡")).getJSONArray("2026-10-08").length())
    }

    // ---- one day of a series ----

    @Test
    fun `a day saved on its own keeps the five fields a day may hold, and nothing else`() {
        val load = scratch(series)
        var said: Days.DaySaved? = null
        assertTrue(Days.saveDay(load, 1, "2026-10-12", "  Stand-up (room 2) ", "agenda", "", "Work", "",
            result = { said = it }) is SaveResult.Ok)
        val note = onDisk(load)
        val own = note.getJSONObject("overrides").getJSONObject("2026-10-12")
        assertEquals("Stand-up (room 2)", own.getString("title"))
        assertEquals(Days.DaySaved.OWN, said)
        // The series is untouched: its title, its date, its repeat.
        assertEquals("Stand-up", note.getString("title"))
        assertEquals("2026-10-05", note.getString("date"))
        assertEquals("weekly", note.getJSONObject("recurrence").getString("freq"))
        assertFalse("only the overridable fields", own.has("checklist") || own.has("date"))
    }

    @Test
    fun `a day that says what the series says keeps no copy, and follows the series again`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Different", "agenda", "", "Work", "")
        var said: Days.DaySaved? = null
        Days.saveDay(load, 1, "2026-10-12", "Stand-up", "agenda", "", "Work", "", result = { said = it })
        assertEquals(Days.DaySaved.FOLLOWS, said)
        assertFalse(onDisk(load).getJSONObject("overrides").has("2026-10-12"))
    }

    @Test
    fun `a day of a note that no longer repeats is refused, not written`() {
        val load = scratch("""[{"id": 1, "title": "x", "date": "2026-10-05"}]""")
        assertTrue(Days.saveDay(load, 1, "2026-10-05", "y", "", "", "General", "") is SaveResult.Failed)
        assertEquals("x", onDisk(load).getString("title"))
    }

    @Test
    fun `deleting one day skips it, and the Undo brings it back with its own edit`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Moved room", "agenda", "", "Work", "")
        val (result, gone) = Days.deleteDay(load, 1, "2026-10-12")
        assertTrue(result is SaveResult.Ok)
        val after = onDisk(load)
        assertEquals("2026-10-12", after.getJSONArray("exceptions").getString(0))
        assertFalse(after.getJSONObject("overrides").has("2026-10-12"))
        assertTrue(Days.notesOn(listOf(after), "2026-10-12").isEmpty())

        assertNotNull(gone)
        assertTrue(Days.undoDeleteDay(load, gone!!) is SaveResult.Ok)
        val back = onDisk(load)
        assertEquals(0, back.getJSONArray("exceptions").length())
        assertEquals("Moved room", back.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
    }

    @Test
    fun `deleting a moved day skips the date its rule gives it, and takes the move`() {
        val load = scratch("""[{"id": 1, "uid": "s", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]}, "moved": {"2026-10-12": "2026-10-13"}}]""")
        val (_, gone) = Days.deleteDay(load, 1, "2026-10-13")
        val after = onDisk(load)
        assertEquals("2026-10-12", after.getJSONArray("exceptions").getString(0))
        assertEquals(0, after.getJSONObject("moved").length())
        Days.undoDeleteDay(load, gone!!)
        assertEquals("2026-10-13", onDisk(load).getJSONObject("moved").getString("2026-10-12"))
    }

    @Test
    fun `an Undo finds its note by identity, not by a number since given to another`() {
        val load = scratch(series)
        val (_, gone) = Days.deleteDay(load, 1, "2026-10-12")
        val other = gone!!.copy(uid = "someone-else")
        assertTrue(Days.undoDeleteDay(load, other) is SaveResult.Failed)
    }

    @Test
    fun `resetting a day drops its own edit, and a moved day goes home`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Own", "agenda", "", "Work", "")
        assertTrue(Days.resetDay(load, 1, "2026-10-12") is SaveResult.Ok)
        assertFalse(onDisk(load).getJSONObject("overrides").has("2026-10-12"))

        val moved = scratch("""[{"id": 1, "uid": "s", "title": "S", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]}, "moved": {"2026-10-12": "2026-10-13"},
            "overrides": {"2026-10-13": {"title": "Own"}}}]""")
        Days.resetDay(moved, 1, "2026-10-13", dropText = false)
        val home = onDisk(moved)
        assertEquals("only the date: home, its text with it", 0, home.getJSONObject("moved").length())
        assertEquals("Own", home.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
    }

    // ---- the repeat ----

    @Test
    fun `a repeat needs a start date`() {
        val load = scratch("""[{"id": 1, "title": "x", "date": ""}]""")
        val result = Days.setRepeat(load, 1, JSONObject().put("freq", "weekly"))
        assertTrue(result is SaveResult.Failed)
        assertTrue((result as SaveResult.Failed).reason.contains("start date"))
    }

    @Test
    fun `a repeat is stored normalised`() {
        val load = scratch("""[{"id": 1, "title": "x", "date": "2026-10-05"}]""")
        Days.setRepeat(load, 1, JSONObject().put("freq", "weekly").put("weekdays", JSONArray(listOf(3, 0, 3))).put("interval", 0))
        val rule = onDisk(load).getJSONObject("recurrence")
        assertEquals("weekly", rule.getString("freq"))
        assertEquals(1, rule.getInt("interval"))
        assertEquals(listOf(0, 3), (0 until rule.getJSONArray("weekdays").length()).map { rule.getJSONArray("weekdays").getInt(it) })
    }

    @Test
    fun `turning a repeat off clears its days, and keeps them in the rescue copy when a day had text`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        Days.deleteDay(load, 1, "2026-10-19")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val off = onDisk(load)
        assertEquals("", off.getJSONObject("recurrence").getString("freq"))
        assertEquals(0, off.getJSONObject("overrides").length())
        assertEquals(0, off.getJSONArray("exceptions").length())
        val prev = off.getJSONObject("_prev")
        assertEquals("weekly", prev.getJSONObject("recurrence").getString("freq"))
        assertEquals("Own title", prev.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
        // ...and Restore previous brings the series back whole.
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        val back = onDisk(load)
        assertEquals("weekly", back.getJSONObject("recurrence").getString("freq"))
        assertEquals("Own title", back.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
    }

    @Test
    fun `a day differing only in its colour takes no text with it, so no rescue copy`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Stand-up", "agenda", "", "Work", "#ff0000")
        Days.setRepeat(load, 1, null)
        assertNull(onDisk(load).opt("_prev"))
    }

    // ---- a day held on another date ----

    private fun october(note: JSONObject) =
        Recurrence.expand(note, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31))

    @Test
    fun `a day moved goes with its own edit, recorded by the date its rule gives it`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Stand-up, room 2", "agenda", "", "Work", "")
        val (result, move) = Days.moveDay(load, 1, "2026-10-12", "2026-10-14")
        assertTrue(result is SaveResult.Ok)
        assertEquals(Days.DayMove(1, "s", "2026-10-12", "2026-10-12", "2026-10-14"), move)
        val note = onDisk(load)
        assertEquals("2026-10-14", note.getJSONObject("moved").getString("2026-10-12"))
        assertEquals("Stand-up, room 2", note.getJSONObject("overrides").getJSONObject("2026-10-14").getString("title"))
        assertFalse(note.getJSONObject("overrides").has("2026-10-12"))
        assertEquals(listOf("2026-10-05", "2026-10-14", "2026-10-19", "2026-10-26"), october(note))
        // Moved again, still recorded against the rule's date; home, the record goes.
        Days.moveDay(load, 1, "2026-10-14", "2026-10-15")
        assertEquals("2026-10-15", onDisk(load).getJSONObject("moved").getString("2026-10-12"))
        Days.moveDay(load, 1, "2026-10-15", "2026-10-12")
        assertFalse(onDisk(load).getJSONObject("moved").has("2026-10-12"))
        assertTrue(onDisk(load).getJSONObject("overrides").has("2026-10-12"))
    }

    @Test
    fun `a day cannot move onto another day of its series, nor to a date that is not one`() {
        val load = scratch(series)
        val (taken, move) = Days.moveDay(load, 1, "2026-10-12", "2026-10-19")
        assertTrue(taken is SaveResult.Failed && taken.reason.startsWith("This series already has a day on"))
        assertNull(move)
        assertNull(onDisk(load).optJSONObject("moved"))
        assertTrue(Days.moveDay(load, 1, "2026-10-12", "2026-13-40").first is SaveResult.Failed)
        // Asked to stay where it is, nothing is written.
        val (same, none) = Days.moveDay(load, 1, "2026-10-12", "2026-10-12")
        assertTrue(same is SaveResult.Ok)
        assertNull(none)
        assertNull(onDisk(load).optJSONObject("moved"))
    }

    @Test
    fun `a move's Undo puts the day back, unless it has moved on since`() {
        val load = scratch(series)
        val move = Days.moveDay(load, 1, "2026-10-12", "2026-10-14").second!!
        assertTrue(Days.undoMoveDay(load, move) is SaveResult.Ok)
        assertEquals(0, onDisk(load).getJSONObject("moved").length())
        assertEquals(listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"), october(onDisk(load)))
        val again = Days.moveDay(load, 1, "2026-10-12", "2026-10-14").second!!
        Days.moveDay(load, 1, "2026-10-14", "2026-10-16")
        val late = Days.undoMoveDay(load, again)
        assertTrue(late is SaveResult.Failed && late.reason.startsWith("That day has moved on since"))
        assertEquals("2026-10-16", onDisk(load).getJSONObject("moved").getString("2026-10-12"))
        // ...and when ANOTHER day has since taken the date it went to, the
        // Undo must not move that one instead.
        assertTrue(Days.moveDay(load, 1, "2026-10-19", "2026-10-14").first is SaveResult.Ok)
        val stranger = Days.undoMoveDay(load, again)
        assertTrue(stranger is SaveResult.Failed && stranger.reason.startsWith("That day has moved on since"))
        assertEquals("2026-10-14", onDisk(load).getJSONObject("moved").getString("2026-10-19"))
    }

    @Test
    fun `a day does not come home over another day moved onto its date`() {
        // While the 12th is away on the 14th, the 19th is moved onto the
        // 12th. The desktop let the first come home over it, and the second
        // was drawn nowhere -- its move still in the file.
        val load = scratch(series)
        val away = Days.moveDay(load, 1, "2026-10-12", "2026-10-14").second!!
        assertTrue(Days.moveDay(load, 1, "2026-10-19", "2026-10-12").first is SaveResult.Ok)
        val undo = Days.undoMoveDay(load, away)
        assertTrue(undo is SaveResult.Failed && undo.reason.startsWith("Another day of this series is there now"))
        val home = Days.moveDay(load, 1, "2026-10-14", "2026-10-12").first
        assertTrue(home is SaveResult.Failed && home.reason.startsWith("This series already has a day on"))
        val reset = Days.resetDay(load, 1, "2026-10-14", dropText = false)
        assertTrue(reset is SaveResult.Failed && reset.reason.startsWith("Another day of this series is on"))
        // Every day still drawn: the 12th's on the 14th, the 19th's on the 12th.
        assertEquals(listOf("2026-10-05", "2026-10-12", "2026-10-14", "2026-10-26"), october(onDisk(load)))
    }

    // ---- days off the schedule ----

    /** A Monday series whose 12th keeps its own text, its repeat since moved to Tuesdays. */
    private val offTheSchedule = """[{"id": 1, "uid": "s", "title": "Stand-up", "body": "agenda", "comments": "",
        "category": "Work", "color": "", "date": "2026-10-05",
        "formatting": [{"start": 0, "end": 6, "style": "bold"}],
        "recurrence": {"freq": "weekly", "weekdays": [1]},
        "checklist": [{"text": "x", "done": false}],
        "attachments": [{"filename": "a", "stored_as": "a.png"}],
        "overrides": {"2026-10-12": {"title": "Room 2", "body": "own text"}}}]"""

    @Test
    fun `a day off the schedule becomes a note of its own, on its date, with what it showed`() {
        val load = scratch(offTheSchedule)
        assertEquals(listOf("2026-10-12"), Recurrence.offScheduleDays(onDisk(load)))
        val made = Days.settleOffScheduleDay(load, 1, "2026-10-12", Days.Settle.NOTE)
        assertTrue(made is SaveResult.Ok)
        val notes = JSONArray(load.file!!.readText())
        val fresh = notes.getJSONObject(1)
        assertEquals(2, fresh.getInt("id"))
        assertEquals("Room 2", fresh.getString("title"))
        assertEquals("own text", fresh.getString("body"))
        assertEquals("2026-10-12", fresh.getString("date"))
        assertEquals("Work", fresh.getString("category"))
        assertEquals("its own text: the series' styling would land on the wrong letters",
                     0, fresh.getJSONArray("formatting").length())
        assertEquals("the checklist and files stay the series'", 0, fresh.getJSONArray("checklist").length())
        assertEquals(0, fresh.getJSONArray("attachments").length())
        assertTrue(fresh.optString("uid").isNotEmpty() && fresh.optString("uid") != "s")
        assertTrue(Recurrence.offScheduleDays(notes.getJSONObject(0)).isEmpty())
        assertFalse(notes.getJSONObject(0).getJSONObject("overrides").has("2026-10-12"))
    }

    @Test
    fun `sent to the Trash it leaves the series too, and the series' styling goes where its text is its own`() {
        val load = scratch(offTheSchedule.replace("\"body\": \"own text\"", "\"body\": \"agenda\""))
        assertTrue(Days.settleOffScheduleDay(load, 1, "2026-10-12", Days.Settle.TRASH) is SaveResult.Ok)
        val notes = JSONArray(load.file!!.readText())
        assertEquals(1, notes.length())
        assertFalse(notes.getJSONObject(0).getJSONObject("overrides").has("2026-10-12"))
        val trashed = JSONArray(load.trashFile!!.readText()).getJSONObject(0)
        assertEquals("Room 2", trashed.getString("title"))
        assertTrue(trashed.optString("deleted_at").isNotEmpty())
        assertEquals("the series' own text, so its styling fits", 1, trashed.getJSONArray("formatting").length())
        val again = Days.settleOffScheduleDay(load, 1, "2026-10-12", Days.Settle.NOTE)
        assertTrue(again is SaveResult.Failed && again.reason.startsWith("That day keeps nothing"))
    }

    @Test
    fun `a day moved there takes its move with it`() {
        // Monday the 12th was held on Wednesday the 14th; the repeat, now on
        // Tuesdays, gives the 12th no more, so the move draws nothing.
        val load = scratch(offTheSchedule
            .replace("\"overrides\": {\"2026-10-12\"", "\"moved\": {\"2026-10-12\": \"2026-10-14\"}, \"overrides\": {\"2026-10-14\""))
        assertEquals(listOf("2026-10-14"), Recurrence.offScheduleDays(onDisk(load)))
        assertTrue(Days.settleOffScheduleDay(load, 1, "2026-10-14", Days.Settle.NOTE) is SaveResult.Ok)
        assertEquals(0, onDisk(load).getJSONObject("moved").length())
    }
}
