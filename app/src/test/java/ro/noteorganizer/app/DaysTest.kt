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

    @Test
    fun `the wait for the day to turn ends at the next date's first moment, where midnight is skipped too`() {
        val bucharest = java.time.ZoneId.of("Europe/Bucharest")
        val late = java.time.ZonedDateTime.of(2026, 10, 27, 23, 58, 0, 0, bucharest)
        assertEquals(120_000L, Days.untilNextDay(late))
        // The night the clocks go back is 25 hours from midnight to midnight.
        val autumn = java.time.ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, bucharest)
        assertEquals(25 * 3_600_000L, Days.untilNextDay(autumn))
        // Just begun, a day waits for the next one, never 0.
        assertEquals(24 * 3_600_000L, Days.untilNextDay(java.time.ZonedDateTime.of(2026, 10, 28, 0, 0, 0, 0, bucharest)))
        // Santiago skipped its midnight on 7 September 2025: that day began at 01:00.
        val santiago = java.time.ZoneId.of("America/Santiago")
        val eve = java.time.ZonedDateTime.of(2025, 9, 6, 23, 30, 0, 0, santiago)
        assertEquals(30 * 60_000L, Days.untilNextDay(eve))
    }

    @Test
    fun `a turn-off over an import's Update copy says Restore previous brings the note from before it`() {
        // The copy kept is the update's, and Restore previous brings that
        // back, not the series (the desktop's _repeat_off_line, 8b5d6cc).
        val note = JSONObject(series.trim().removePrefix("[").removeSuffix("]"))
            .put("_prev", JSONObject().put("title", "Stand-up").put("body", "older").put("category", "Work"))
        assertTrue(Notebook.keepsUpdateCopy(note))
        note.put("_prev", JSONObject().put("title", "Stand-up").put("body", "older").put("date", "2026-10-05")
            .put("recurrence", JSONObject().put("freq", "weekly")))
        assertFalse("a series' own copy", Notebook.keepsUpdateCopy(note))
        assertFalse("no copy", Notebook.keepsUpdateCopy(JSONObject()))
    }

    @Test
    fun `a turn-off whose copy the same save drops promises no Restore previous`() {
        // A share code's Update that only turned the repeat on: its copy is
        // the note not repeating, and the turn-off makes the note that again,
        // the copy dropped as spent in the same write. The sheet promised
        // Restore previous, with no copy and no button (the tenth round; the
        // desktop's _REPEAT_OFF_NO_COPY, 04044ce).
        val update = """"_prev": {"title": "Stand-up", "body": "agenda", "comments": "", "category": "Work",
            "color": "", "date": "2026-10-05", "recurrence": {"freq": ""},
            "checklist": [{"text": "x", "done": false}]}, "checklist""""
        val load = scratch(series.replace("\"checklist\"", update))
        Days.saveDay(load, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        assertTrue("the Update's copy, waiting", Notebook.keepsUpdateCopy(onDisk(load)))
        assertTrue(Days.repeatOffLosesDays(onDisk(load)))
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertNull("the copy dropped as spent", onDisk(load).opt("_prev"))
        assertEquals("Own title", dayTitle(onDisk(load), "2026-10-12"))
        assertEquals(Days.OffWord.NO_COPY, Days.repeatOffWord(onDisk(load)))
        // An Update's copy the note does not hold again is kept, and said so.
        val older = scratch(series.replace("\"checklist\"", update.replace("\"body\": \"agenda\"", "\"body\": \"older agenda\"")))
        Days.saveDay(older, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        assertTrue(Days.setRepeat(older, 1, null) is SaveResult.Ok)
        assertEquals(Days.OffWord.UPDATE_KEPT, Days.repeatOffWord(onDisk(older)))
        // The series' own copy, kept, brings the series back.
        val plain = scratch(series)
        Days.saveDay(plain, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        assertTrue(Days.setRepeat(plain, 1, null) is SaveResult.Ok)
        assertEquals(Days.OffWord.SERIES_KEPT, Days.repeatOffWord(onDisk(plain)))
        assertEquals(Days.OffWord.NO_COPY, Days.repeatOffWord(null))
    }

    private val gym = """[{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up", "comments": "",
        "category": "General", "color": "", "date": "2026-11-02",
        "recurrence": {"freq": "weekly", "weekdays": [0]}}]"""

    private val weekly: JSONObject get() = JSONObject().put("freq", "weekly").put("weekdays", JSONArray().put(0))

    @Test
    fun `a day the note only coloured takes the copy's own words for it, its colour kept`() {
        // The tenth round's road in this build: off, on from another start,
        // a day's words written there, Restore previous, then the start put
        // back. The note held that day by its colour alone, and the copy's
        // own words for it were spent with the copy's days, in no file (the
        // desktop's 04044ce: a copy's day is dropped only where the note
        // deleted that day or wrote words of its own for it).
        val load = scratch(gym)
        Days.saveDay(load, 1, "2026-11-09", "Gym", "Leg day - knee hurt", "", "General", "")
        Days.saveDay(load, 1, "2026-11-16", "Gym", "Warm up", "", "General", "#ff6b6b")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        // On again from 9 November: the sheet's two writes.
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up", date = "2026-11-09", scheduleFollows = true) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, weekly, startWas = "2026-11-02") is SaveResult.Ok)
        Days.saveDay(load, 1, "2026-11-16", "Gym", "Dentist at 9, gym after lunch", "", "General", "#ff6b6b")
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        assertEquals("the first series, back", "2026-11-02", onDisk(load).getString("date"))
        // The start put back, from the note's details.
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up", date = "2026-11-09") is SaveResult.Ok)
        val after = onDisk(load)
        val day = after.getJSONObject("overrides").getJSONObject("2026-11-16")
        assertEquals("the copy's words for the day", "Dentist at 9, gym after lunch", day.getString("body"))
        assertEquals("the note's colour for it", "#ff6b6b", day.getString("color"))
        assertEquals("the note's own words win their day", "Leg day - knee hurt",
            after.getJSONObject("overrides").getJSONObject("2026-11-09").getString("body"))
    }

    @Test
    fun `a day the note moved takes the copy's words for it on the date it moved to`() {
        // 0.12.0 turned back on as it was, then that very day moved: the
        // day's words, held in the copy alone, were spent at the first read.
        val days = """"exceptions": [], "overrides": {}, "moved": {"2026-11-09": "2026-11-11"},"""
        val shipped = """[{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up", "comments": "",
            "category": "General", "color": "", "date": "2026-11-02",
            "recurrence": {"freq": "weekly", "weekdays": [0]}, $days
            "_prev": {"title": "Gym", "body": "Warm up", "comments": "", "formatting": [], "paragraphs": [],
                      "date": "2026-11-02", "recurrence": {"freq": "weekly", "weekdays": [0]},
                      "exceptions": [], "overrides": {"2026-11-09": {"body": "Leg day - knee hurt"}}, "moved": {}}}]"""
        val note = scratch(shipped).raw!!.getJSONObject(0)
        assertEquals("on the date the note draws the day", "Leg day - knee hurt",
            note.getJSONObject("overrides").getJSONObject("2026-11-11").getString("body"))
        assertFalse("not under the date it left", note.getJSONObject("overrides").has("2026-11-09"))
        assertEquals("2026-11-11", note.getJSONObject("moved").getString("2026-11-09"))
        // Deleted by the note, the day's words go with it, as the desktop's.
        val gone = scratch(shipped.replace(days, """"exceptions": ["2026-11-09"], "overrides": {}, "moved": {},"""))
        assertEquals(0, gone.raw!!.getJSONObject(0).getJSONObject("overrides").length())
        // An edit the copy keeps under a day it deleted is drawn on no day:
        // it stays under its own date, though the note moved that day away.
        // The desktop's own answer, its normalize_note at 99f041e asked of
        // this very note (scratch android_fixes_r10/probe_deleted_edit.py).
        val stray = scratch(shipped.replace(days, """"exceptions": [], "overrides": {},
                "moved": {"2026-11-09": "2026-11-11", "2026-11-16": "2026-11-18"},""")
            .replace(""""exceptions": [], "overrides": {"2026-11-09": {"body": "Leg day - knee hurt"}}""",
                     """"exceptions": ["2026-11-16"], "overrides": {"2026-11-16": {"body": "Stray words"}}"""))
            .raw!!.getJSONObject(0)
        assertEquals("Stray words", stray.getJSONObject("overrides").getJSONObject("2026-11-16").getString("body"))
        assertFalse("not where the note moved the day", stray.getJSONObject("overrides").has("2026-11-18"))
        assertEquals(0, stray.getJSONArray("exceptions").length())
    }

    @Test
    fun `a second turn-off gives the first one's words only when the note has lost words to them`() {
        // Off, a line added, on as it was, off again: the first copy's older
        // words went into the second's, and Restore previous dropped the
        // line (the tenth round, the desktop's 04044ce rescue_copy_due).
        val body = "Warm up.\nPhysio said: no squats for three weeks, swap in the bike.\nThen the plan."
        val load = scratch(gym.replace("\"Warm up\"", "\"" + body.replace("\n", "\\n") + "\""))
        Days.saveDay(load, 1, "2026-11-09", "Gym", "Leg day - knee hurt", "", "General", "")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val added = "$body\nAnd ten minutes of stretching after, every time."
        assertTrue(Notebook.updateNote(load, 1, "Gym", added) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, weekly) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertEquals("the newest words, in the second copy", added, onDisk(load).getJSONObject("_prev").getString("body"))
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        assertEquals(added, onDisk(load).getString("body"))
        // Words the note has lost still go into the next turn-off's copy.
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up.") is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertEquals(added, onDisk(load).getJSONObject("_prev").getString("body"))
    }

    @Test
    fun `a second turn-off keeps a big delete's words whatever series the first copy held`() {
        // A big delete, off, on again from another start, off again: the
        // first turn-off's copy, of another series, gave no words, and the
        // deleted lines were in no file (the tenth round, the desktop's 99f041e).
        val body = "Warm up.\nPhysio said: no squats for three weeks, swap in the bike.\nThen the plan."
        val load = scratch(gym.replace("\"Warm up\"", "\"" + body.replace("\n", "\\n") + "\""))
        Days.saveDay(load, 1, "2026-11-09", "Gym", "Leg day - knee hurt", "", "General", "")
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up.") is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertEquals(body, onDisk(load).getJSONObject("_prev").getString("body"))
        // On from 9 November: the sheet's two writes.
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up.", date = "2026-11-09", scheduleFollows = true) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, weekly, startWas = "2026-11-02") is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val prev = onDisk(load).getJSONObject("_prev")
        assertEquals("the deleted words, kept", body, prev.getString("body"))
        assertEquals("beside the series as it is now", "2026-11-09", prev.getString("date"))
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        assertEquals(body, onDisk(load).getString("body"))
    }

    @Test
    fun `a read asks only a copy that can be a series' own, never every note an import updated`() {
        // Every Update copy holds all three day fields, and each was shaped
        // twice at every start for nothing (the final audit's ninth round).
        // Measured, not judged by a clock: the read leaves such a copy as it
        // lies, and the time each way is printed for the round's record.
        val notes = JSONArray()
        for (id in 1..1_000) {
            notes.put(JSONObject().put("id", id).put("uid", "u$id").put("title", "Note $id")
                .put("body", "word ".repeat(400)).put("category", "General")
                .put("_prev", JSONObject().put("title", "Note $id").put("body", "older ".repeat(330))
                    .put("category", "Work").put("color", "").put("checklist", JSONArray()).put("pinned", false)
                    .put("date", "").put("recurrence", JSONObject().put("freq", ""))
                    .put("exceptions", JSONArray()).put("overrides", JSONObject()).put("moved", JSONObject())))
        }
        val dir = NioFiles.createTempDirectory("days").toFile()
        File(dir, "notes_data.json").writeText(notes.toString())
        Notebook.load(dir)
        val started = System.nanoTime()
        val load = Notebook.load(dir)
        val read = (System.nanoTime() - started) / 1_000_000
        val shaping = System.nanoTime()
        for (i in 0 until load.raw!!.length()) Normalize.normalizeNote(load.raw!!.getJSONObject(i), daysBack = false)
        val shaped = (System.nanoTime() - shaping) / 1_000_000
        println("1,000 notes with an Update's copy: the read $read ms; the shaping round eight's guard added, once, $shaped ms")
        assertEquals("the copy as it lies", 3, load.raw!!.getJSONObject(0).getJSONObject("_prev")
            .keys().asSequence().count { it in Notebook.DAY_FIELDS })
    }

    /** A daily series turned back on as its copy's, [days] days of its own text in both. */
    private fun yearOfDays(days: Int): JSONObject {
        val start = java.time.LocalDate.of(2025, 1, 1)
        fun dayEdits() = JSONObject().also { edits ->
            for (i in 0 until days) {
                val day = start.plusDays(i.toLong()).toString()
                edits.put(day, JSONObject().put("title", "Journal").put("body", "$day: " + "walked, wrote, read. ".repeat(24)))
            }
        }
        val rule = JSONObject().put("freq", "daily").put("interval", 1)
        return JSONObject().put("id", 1).put("title", "Journal").put("body", "").put("date", "2025-01-01")
            .put("recurrence", rule).put("overrides", dayEdits()).put("exceptions", JSONArray()).put("moved", JSONObject())
            .put("_prev", JSONObject().put("title", "Journal").put("body", "").put("date", "2025-01-01")
                .put("recurrence", JSONObject(rule.toString())).put("overrides", dayEdits())
                .put("exceptions", JSONArray()).put("moved", JSONObject()))
    }

    @Test
    fun `a series turned back on with a year of days of its own takes them back in time that grows with the days`() {
        // The words added to a day were read off a copy of the whole note, made
        // for each day: the turn-on grew with the square of the days, on the
        // main thread (the final audit's thirteenth round). Timed against a
        // quarter as many days in the same run: four times the work, not sixteen.
        fun timed(days: Int): Long = (0 until 3).minOf {
            val note = yearOfDays(days)
            val started = System.nanoTime()
            Notebook.spendSeriesDays(note)
            System.nanoTime() - started
        }
        timed(60)
        val small = timed(90)
        val large = timed(360)
        println("the take-back: 90 days ${small / 1_000_000} ms, 360 days ${large / 1_000_000} ms")
        assertTrue("360 days took ${large / maxOf(small, 1)} times as long as 90", large < small * 9)
    }

    private val series = """[{"id": 1, "uid": "s", "title": "Stand-up", "body": "agenda", "comments": "",
        "category": "Work", "color": "", "date": "2026-10-05",
        "recurrence": {"freq": "weekly", "weekdays": [0]}, "checklist": [{"text": "x", "done": false}]}]"""

    // ---- a day off the schedule ----

    @Test
    fun `a day the series no longer gives is neither moved nor deleted as one of its days`() {
        // Its own text on a Tuesday of a Monday series: off the schedule.
        val load = scratch(series.replace("\"checklist\"", "\"overrides\": {\"2026-10-06\": {\"body\": \"own\"}}, \"checklist\""))
        assertTrue(Days.moveDay(load, 1, "2026-10-06", "2026-10-08").first is SaveResult.Failed)
        assertTrue(Days.deleteDay(load, 1, "2026-10-06").first is SaveResult.Failed)
        assertTrue("its text is still there, to settle", onDisk(load).getJSONObject("overrides").has("2026-10-06"))
        assertTrue("a day the series gives still moves", Days.moveDay(load, 1, "2026-10-12", "2026-10-14").first is SaveResult.Ok)
    }

    @Test
    fun `a deleted day's Undo is refused once the repeat no longer gives it, as the desktop's`() {
        val load = scratch(series)
        val (deleted, gone) = Days.deleteDay(load, 1, "2026-10-12")
        assertTrue(deleted is SaveResult.Ok)
        // The repeat changed to Tuesdays in the meantime.
        val note = onDisk(load)
        note.getJSONObject("recurrence").put("weekdays", JSONArray(listOf(1)))
        load.file!!.writeText(JSONArray().put(note).toString())
        val again = Notebook.load(load.file!!.parentFile!!)
        assertTrue(Days.undoDeleteDay(again, gone!!) is SaveResult.Failed)
    }

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
        val repainted = Days.withColour(settings, "2026-10-07", "#00ff00")!!
        assertEquals("#00ff00", repainted.getString("2026-10-07"))
        assertEquals("#ff0000", repainted.getString("2026-10-05"))
        assertFalse(Days.withColour(settings, "2026-10-05", "")!!.has("2026-10-05"))
    }

    @Test
    fun `a calendar keeps real days only, 20,000 at most of each, markers an emoji long`() {
        // 700,000 "day colours" under keys that were no dates came in whole
        // from a backup, and markers of 200,000 letters, read at every month.
        val colours = JSONObject()
        for (i in 0 until 30_000) colours.put(java.time.LocalDate.of(1990, 1, 1).plusDays(i.toLong()).toString(), "#ff0000")
        colours.put("12345", "#00ff00").put("2026-02-30", "#00ff00").put("2026-1-5", "#00ff00")
        val settings = JSONObject().put("day_colors", colours).put("day_emoji", JSONObject()
            .put("2026-10-05", JSONArray(listOf("⭐", "x".repeat(17), "🎂")))
            .put("not a day", "⭐"))
        val kept = Days.dayColours(settings)
        assertEquals(Days.MAX_DAY_DECORATIONS, kept.size)
        assertTrue(kept.keys.all { Days.isCalendarDay(it) })
        assertEquals(mapOf("2026-10-05" to listOf("⭐", "🎂")), Days.dayMarkers(settings))
        // A 20,001st painted day is refused, never one already there dropped.
        assertNull(Days.withColour(settings, "2100-01-01", "#00ff00"))
        val one = kept.keys.first()
        assertEquals("#00ff00", Days.withColour(settings, one, "#00ff00")!!.getString(one))
        // The import takes only real days, and stops at the ceiling.
        val load = scratch("[]")
        val plan = Importing.plan(load.raw!!, emptyList(), Importing.Source.BACKUP)
        File(load.file!!.parentFile, "settings.json").writeText("{}")
        val again = Notebook.load(load.file!!.parentFile!!)
        Importing.apply(again, plan, Importing.Answers(), null, Importing.Extras(dayColors = colours,
            dayEmoji = settings.getJSONObject("day_emoji")))
        val saved = JSONObject(File(load.file!!.parentFile, "settings.json").readText())
        assertEquals(Days.MAX_DAY_DECORATIONS, saved.getJSONObject("day_colors").length())
        assertTrue(saved.getJSONObject("day_colors").keys().asSequence().all { Days.isCalendarDay(it) })
        assertEquals(listOf("⭐", "🎂"), saved.getJSONObject("day_emoji").getJSONArray("2026-10-05").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertFalse(saved.getJSONObject("day_emoji").has("not a day"))
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
        assertFalse(Days.withMarkers(settings, "2026-10-06", emptyList())!!.has("2026-10-06"))
        assertEquals(1, Days.withMarkers(settings, "2026-10-08", listOf("💡"))!!.getJSONArray("2026-10-08").length())
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
    fun `a day of a series ending in a table typed on the phone follows it, and its colour alone is no text`() {
        // A day's text is kept with the line under a closing table, the
        // series' as typed: compared as they lay, a day saying what the series
        // said was cut off from it, and a colour alone read as words, so the
        // repeat's turn-off made a copy and kept the days (the fourteenth round).
        val load = scratch(series.replace("\"body\": \"agenda\"", "\"body\": \"agenda\\n| a | b |\""))
        var said: Days.DaySaved? = null
        Days.saveDay(load, 1, "2026-10-12", "Stand-up", "agenda\n| a | b |", "", "Work", "", result = { said = it })
        assertEquals(Days.DaySaved.FOLLOWS, said)
        assertFalse(onDisk(load).getJSONObject("overrides").has("2026-10-12"))
        Days.saveDay(load, 1, "2026-10-19", "Stand-up", "agenda\n| a | b |", "", "Work", "#ff6b6b")
        val note = onDisk(load)
        assertTrue("the colour is kept", note.getJSONObject("overrides").has("2026-10-19"))
        assertFalse("and is no text of its own", Notebook.daysHoldOwnText(note))
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
    fun `a day's Undo goes to the note it was taken on, of two wearing one identity`() {
        val two = """[{"id": 1, "uid": "s", "title": "First", "body": "", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]}},
            {"id": 2, "uid": "s", "title": "Second", "body": "", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]}}]"""
        val load = scratch(two)
        val (_, gone) = Days.deleteDay(load, 2, "2026-10-12")
        assertTrue(Days.undoDeleteDay(load, gone!!) is SaveResult.Ok)
        val after = JSONArray(load.file!!.readText())
        assertEquals(0, after.getJSONObject(1).getJSONArray("exceptions").length())
        assertEquals(0, after.getJSONObject(0).optJSONArray("exceptions")?.length() ?: 0)
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

    @Test
    fun `a start moved in the editor onto a moved day's date sends that day home at the save`() {
        // Mondays, the 12th moved to Tuesday the 13th; the editor's date set
        // to a Tuesday makes the 13th a day of the rule's own. Left till the
        // next start, the moved day was drawn nowhere (the fifteenth round).
        val load = scratch("""[{"id": 1, "uid": "s", "title": "S", "body": "", "date": "2026-10-05",
            "recurrence": {"freq": "weekly"}, "moved": {"2026-10-12": "2026-10-13"},
            "overrides": {"2026-10-13": {"title": "Own"}}}]""")
        assertTrue(Notebook.updateNote(load, 1, "S", "", date = "2026-10-06") is SaveResult.Ok)
        val saved = onDisk(load)
        assertEquals(0, saved.getJSONObject("moved").length())
        assertEquals("Own", saved.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
        assertFalse(saved.getJSONObject("overrides").has("2026-10-13"))
    }

    @Test
    fun `a share code making the series daily sends a day moved onto its new days home at once`() {
        // As the desktop's test_data_safety: at the Update, not the next start.
        val load = scratch("""[{"id": 1, "uid": "coded", "title": "Gym", "body": "x", "date": "2026-11-02",
            "recurrence": {"freq": "weekly", "weekdays": [0]}, "moved": {"2026-11-09": "2026-11-10"},
            "overrides": {"2026-11-10": {"body": "Held on Tuesday"}}}]""")
        val incoming = JSONObject("""{"uid": "coded", "title": "Gym", "body": "x", "date": "2026-11-02",
            "recurrence": {"freq": "daily"}}""")
        assertTrue(Importing.apply(load, Importing.plan(load.raw!!, listOf(incoming), Importing.Source.CODE),
            Importing.Answers(update = true), null) is Importing.Result.Done)
        val saved = onDisk(load)
        assertEquals(0, saved.getJSONObject("moved").length())
        assertEquals("Held on Tuesday", saved.getJSONObject("overrides").getJSONObject("2026-11-09").getString("body"))
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
    fun `turning a repeat off keeps a day's own text in the note, inert, and the series whole in the copy`() {
        // The owner's choice, the desktop's rule: one place for a turned-off
        // series' days, the note. Up to 0.12.0 they went into the copy alone,
        // which the desktop's next copy of words erased.
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        Days.deleteDay(load, 1, "2026-10-19")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val off = onDisk(load)
        assertEquals("", off.getJSONObject("recurrence").getString("freq"))
        assertEquals("the day's own text, in the note", "Own title", dayTitle(off, "2026-10-12"))
        assertEquals("and every day with it", "2026-10-19", off.getJSONArray("exceptions").getString(0))
        assertEquals("inert while it does not repeat", "Stand-up", Recurrence.effectiveNote(off, "2026-10-12").getString("title"))
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
    fun `turned off with a new start in the same Save, the rescue copy keeps the start it had`() {
        // The sheet writes the new start first, then the repeat: the copy
        // taken then held a series on the new date, its days' edits off it.
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        val note = onDisk(load).put("date", "2026-10-08")
        load.file!!.writeText(JSONArray().put(note).toString())
        val again = Notebook.load(load.file!!.parentFile!!)
        assertTrue(Days.setRepeat(again, 1, null, startWas = "2026-10-05") is SaveResult.Ok)
        assertEquals("2026-10-05", onDisk(again).getJSONObject("_prev").getString("date"))
    }

    @Test
    fun `a day differing only in its colour takes no text with it, so no rescue copy`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Stand-up", "agenda", "", "Work", "#ff0000")
        Days.setRepeat(load, 1, null)
        assertNull(onDisk(load).opt("_prev"))
    }

    @Test
    fun `a repeat turned off after a big delete keeps the deleted words in its copy, beside the series`() {
        // The delete's copy held the paragraph; the turn-off's, taken from
        // the note as it stood, replaced it, and the paragraph was in no
        // copy. The desktop's _turn_off_rescue_copy, ported.
        val paragraph = "A paragraph of three hundred letters, written once and deleted. ".repeat(5)
        val load = scratch(series.replace("\"body\": \"agenda\"", "\"body\": \"agenda\\n$paragraph\""))
        Days.saveDay(load, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        assertTrue(Notebook.updateNote(load, 1, "Stand-up", "agenda") is SaveResult.Ok)
        assertEquals("agenda\n$paragraph", onDisk(load).getJSONObject("_prev").getString("body"))
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val off = onDisk(load)
        assertEquals("the note keeps its newest words", "agenda", off.getString("body"))
        val prev = off.getJSONObject("_prev")
        assertEquals("agenda\n$paragraph", prev.getString("body"))
        assertEquals("weekly", prev.getJSONObject("recurrence").getString("freq"))
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        val back = onDisk(load)
        assertEquals("agenda\n$paragraph", back.getString("body"))
        assertEquals("Own title", back.getJSONObject("overrides").getJSONObject("2026-10-12").getString("title"))
        // A copy of more than words -- an earlier turn-off's -- whose words
        // the note has not lost gives none: the copy is the note as it stands.
        val whole = scratch(series)
        Days.saveDay(whole, 1, "2026-10-12", "Own title", "agenda", "", "Work", "")
        val note = onDisk(whole).put("_prev", JSONObject().put("title", "Stand-up").put("body", "older words")
            .put("date", "2026-09-01").put("recurrence", JSONObject().put("freq", "daily")))
        whole.file!!.writeText(JSONArray().put(note).toString())
        val reread = Notebook.load(whole.file!!.parentFile!!)
        assertTrue(Days.setRepeat(reread, 1, null) is SaveResult.Ok)
        assertEquals("weekly", onDisk(reread).getJSONObject("_prev").getJSONObject("recurrence").getString("freq"))
        assertEquals("agenda", onDisk(reread).getJSONObject("_prev").getString("body"))
    }

    @Test
    fun `a big delete's copy that carried an earlier turn-off's days still gives its words to the next turn-off`() {
        // Turned off, the series' days stay in the note; turned on again,
        // a day given its own text, a big delete: its copy is the words
        // alone. The second turn-off takes those words into its copy of the
        // series, whose days are the note's own (f1a38aa's chain, the
        // desktop's way).
        val paragraph = "A paragraph of three hundred letters, written once and deleted. ".repeat(5)
        val load = scratch(series.replace("\"body\": \"agenda\"", "\"body\": \"agenda\\n$paragraph\""))
        Days.saveDay(load, 1, "2026-10-12", "Moved to room 4", "agenda\n$paragraph", "", "Work", "")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, JSONObject("""{"freq": "weekly", "weekdays": [0]}""")) is SaveResult.Ok)
        Days.saveDay(load, 1, "2026-10-26", "Cancelled, see email", "agenda\n$paragraph", "", "Work", "")
        assertTrue(Notebook.updateNote(load, 1, "Stand-up", "agenda") is SaveResult.Ok)
        val delete = onDisk(load).getJSONObject("_prev")
        assertEquals("agenda\n$paragraph", delete.getString("body"))
        assertFalse("a copy of words is words alone", delete.has("overrides"))
        assertEquals("the first time's day is the note's", "Moved to room 4", dayTitle(onDisk(load), "2026-10-12"))
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val prev = onDisk(load).getJSONObject("_prev")
        assertEquals("the deleted words", "agenda\n$paragraph", prev.getString("body"))
        assertEquals("weekly", prev.getJSONObject("recurrence").getString("freq"))
        assertEquals("Cancelled, see email", dayTitle(prev, "2026-10-26"))
        assertEquals("Moved to room 4", dayTitle(prev, "2026-10-12"))
        assertEquals("the note keeps its newest words", "agenda", onDisk(load).getString("body"))
        assertEquals("and both days", "Moved to room 4", dayTitle(onDisk(load), "2026-10-12"))
    }

    // ---- a repeat ended by a share code ----

    /** The sender's copy of the series: same uid, the words changed, the repeat as [rule]. */
    private fun fromSender(load: Notebook.Load, body: String, rule: String) {
        val theirs = JSONObject(series.removeSurrounding("[", "]")).put("body", body).put("recurrence", JSONObject(rule))
        val plan = Importing.plan(load.raw!!, listOf(theirs), Importing.Source.CODE)
        assertTrue(Importing.apply(load, plan, Importing.Answers(update = true), null) is Importing.Result.Done)
    }

    private fun dayTitle(note: JSONObject, day: String): String? =
        (note.opt("overrides") as? JSONObject)?.optJSONObject(day)?.optString("title")

    @Test
    fun `a repeat a share code ends keeps its days in the note, and a copy of them beside the repeat`() {
        // Update A turned the repeat off; the Repeat sheet's Save with
        // "Never" then cleared the days with no copy, and Restore previous
        // brought back the weekly rule without them.
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        fromSender(load, "changed", """{"freq": ""}""")
        val off = onDisk(load)
        assertEquals("the note keeps them", "Leg day: squats", dayTitle(off, "2026-10-12"))
        assertEquals("the copy holds them beside the repeat", "Leg day: squats", dayTitle(off.getJSONObject("_prev"), "2026-10-12"))
        assertEquals("weekly", off.getJSONObject("_prev").getJSONObject("recurrence").getString("freq"))
        // The sheet's Save, the repeat still off: nothing of them goes.
        assertTrue(Days.setRepeat(load, 1, JSONObject("""{"freq": ""}""")) is SaveResult.Ok)
        assertEquals("Leg day: squats", dayTitle(onDisk(load), "2026-10-12"))
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        val back = onDisk(load)
        assertEquals("weekly", back.getJSONObject("recurrence").getString("freq"))
        assertEquals("Leg day: squats", Recurrence.effectiveNote(back, "2026-10-12").getString("title"))
    }

    /** A series turned off by a share code: its days kept, and in its copy beside the weekly rule from [date]. */
    private fun keptOff(date: String = "2026-09-07"): JSONObject {
        val days = """"exceptions": ["2026-09-14"], "overrides": {"2026-09-21": {"body": "DAY 21 OWN"}}"""
        return JSONObject("""{"id": 1, "uid": "s", "title": "Gym", "body": "x", "category": "Work", "date": "$date",
            "recurrence": {"freq": ""}, $days,
            "_prev": {"title": "Gym", "body": "x", "date": "2026-09-07",
                      "recurrence": {"freq": "weekly", "weekdays": [0]}, $days}}""")
    }

    @Test
    fun `days a turned-off series kept come back only onto the series they were kept from`() {
        // Turned back on from another date, an old deleted day hid the new
        // series' own first day. The owner's answer, the desktop's
        // kept_days_fit: only with the same start and rule; a deleted day
        // holds nothing, and the rescue copy keeps them all.
        val weekly = JSONObject("""{"freq": "weekly", "weekdays": [0]}""")
        assertTrue(Notebook.keptDaysFit(keptOff(), "2026-09-07", weekly))
        assertFalse(Notebook.keptDaysFit(keptOff(), "2026-09-14", weekly))
        assertFalse(Notebook.keptDaysFit(keptOff(), "2026-09-07", JSONObject("""{"freq": "daily"}""")))
        assertTrue("nothing says what the series was", Notebook.keptDaysFit(keptOff().apply { remove("_prev") }, "2026-09-14", weekly))
        assertTrue("a copy not holding them: the note is their only place",
            Notebook.keptDaysFit(keptOff().apply { getJSONObject("_prev").put("exceptions", JSONArray()) }, "2026-09-14", weekly))
        // A share code restarting it from another date: the deleted day goes, into the copy.
        val load = scratch(JSONArray().put(keptOff()).toString())
        val theirs = JSONObject("""{"uid": "s", "title": "Gym", "body": "x", "date": "2026-09-14",
            "recurrence": {"freq": "weekly", "weekdays": [0]}}""")
        assertTrue(Importing.apply(load, Importing.plan(load.raw!!, listOf(theirs), Importing.Source.CODE),
            Importing.Answers(update = true), null) is Importing.Result.Done)
        val coded = onDisk(load)
        assertEquals(0, coded.getJSONArray("exceptions").length())
        assertEquals("2026-09-14", coded.getJSONObject("_prev").getJSONArray("exceptions").getString(0))
        assertEquals("DAY 21 OWN", coded.getJSONObject("overrides").getJSONObject("2026-09-21").getString("body"))
        assertTrue(Recurrence.expand(coded, java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30))
            .contains("2026-09-14"))
        // The Repeat sheet, its new start saved first: the same.
        val sheet = scratch(JSONArray().put(keptOff(date = "2026-09-14")).toString())
        assertTrue(Days.setRepeat(sheet, 1, weekly, startWas = "2026-09-07") is SaveResult.Ok)
        val restarted = onDisk(sheet)
        assertEquals(0, restarted.getJSONArray("exceptions").length())
        assertEquals("DAY 21 OWN", restarted.getJSONObject("overrides").getJSONObject("2026-09-21").getString("body"))
        // On again from the start it had: every kept day comes back.
        val same = scratch(JSONArray().put(keptOff()).toString())
        assertTrue(Days.setRepeat(same, 1, weekly) is SaveResult.Ok)
        assertEquals("2026-09-14", onDisk(same).getJSONArray("exceptions").getString(0))
    }

    @Test
    fun `a restore that ends the repeat keeps the days, and leaves a copy that brings the series back`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        // A rescue copy of a version that did not repeat.
        val note = onDisk(load).put("_prev", JSONObject("""{"title": "Stand-up", "body": "old", "comments": "",
            "formatting": [], "paragraphs": [], "recurrence": {"freq": ""}}"""))
        load.file!!.writeText(JSONArray().put(note).toString())
        val again = Notebook.load(load.file!!.parentFile!!)
        assertTrue(Notebook.restorePrevious(again, 1) is SaveResult.Ok)
        val off = onDisk(again)
        assertEquals("", off.getJSONObject("recurrence").getString("freq"))
        assertEquals("the note keeps them", "Leg day: squats", dayTitle(off, "2026-10-12"))
        assertEquals("the copy left holds them beside the repeat", "Leg day: squats", dayTitle(off.getJSONObject("_prev"), "2026-10-12"))
        assertTrue(Notebook.restorePrevious(Notebook.load(load.file!!.parentFile!!), 1) is SaveResult.Ok)
        assertEquals("Leg day: squats", Recurrence.effectiveNote(onDisk(again), "2026-10-12").getString("title"))
    }

    @Test
    fun `a second update that brings the repeat back brings every day's text back`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        fromSender(load, "changed", """{"freq": ""}""")
        fromSender(load, "changed again", """{"freq": "weekly", "weekdays": [0]}""")
        val back = onDisk(load)
        assertEquals("weekly", back.getJSONObject("recurrence").getString("freq"))
        assertEquals("Leg day: squats", Recurrence.effectiveNote(back, "2026-10-12").getString("title"))
    }

    @Test
    fun `the days survive a repeat turned back on by hand and the words rewritten after`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        fromSender(load, "changed", """{"freq": ""}""")
        assertTrue(Days.setRepeat(load, 1, JSONObject("""{"freq": "weekly", "weekdays": [0]}""")) is SaveResult.Ok)
        val again = Notebook.load(load.file!!.parentFile!!)
        assertTrue(Notebook.updateNote(again, 1, "Stand-up", "A whole new text, nothing like the old one at all") is SaveResult.Ok)
        assertEquals("Leg day: squats", Recurrence.effectiveNote(onDisk(again), "2026-10-12").getString("title"))
    }

    @Test
    fun `a big rewrite after a turn-off keeps a copy of words alone, the days staying in the note`() {
        // Turned off by hand, the days went into the copy alone (up to
        // 0.12.0); a big rewrite then made a copy of its own over that one
        // slot, and on the desktop the day's text was in no file.
        val load = scratch(series)
        val long = "The agenda for the whole team, item by item, ".repeat(6).trim()
        assertTrue(Notebook.updateNote(load, 1, "Stand-up", long) is SaveResult.Ok)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertEquals("Leg day: squats", dayTitle(onDisk(load), "2026-10-12"))
        val again = Notebook.load(load.file!!.parentFile!!)
        assertTrue(Notebook.updateNote(again, 1, "Stand-up", "") is SaveResult.Ok)
        val prev = onDisk(again).getJSONObject("_prev")
        assertFalse("the rewrite's own copy, not the turn-off's", prev.has("recurrence"))
        assertEquals(long, prev.getString("body"))
        assertFalse("words alone", prev.has("overrides"))
        assertEquals("the day in the note", "Leg day: squats", dayTitle(onDisk(again), "2026-10-12"))
        // ... and a share code's update made over it leaves it there.
        fromSender(Notebook.load(load.file!!.parentFile!!), "theirs", """{"freq": ""}""")
        assertEquals("Leg day: squats", dayTitle(onDisk(again), "2026-10-12"))
    }

    @Test
    fun `a repeat turned off twice by hand keeps the days of the first time in its copy`() {
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Leg day: squats", "agenda", "", "Work", "")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, JSONObject("""{"freq": "weekly", "weekdays": [0]}""")) is SaveResult.Ok)
        Days.saveDay(load, 1, "2026-10-19", "Arm day", "agenda", "", "Work", "")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        val prev = onDisk(load).getJSONObject("_prev")
        assertEquals("Arm day", dayTitle(prev, "2026-10-19"))
        assertEquals("the first time's day, kept in the note and so in its copy", "Leg day: squats", dayTitle(prev, "2026-10-12"))
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
    fun `a day is not moved onto a date that keeps the words of a day the calendar no longer shows`() {
        // The 12th, a Monday of a series now on Tuesdays, keeps its own words
        // and is drawn nowhere. The 13th moved onto it wrote over them, and
        // the move's Undo took the day back without them (the eleventh round).
        val load = scratch(offTheSchedule)
        Days.saveDay(load, 1, "2026-10-13", "Stand-up", "room 4B", "", "Work", "")
        val (refused, move) = Days.moveDay(load, 1, "2026-10-13", "2026-10-12")
        assertTrue(refused is SaveResult.Failed &&
                   refused.reason.startsWith("This series still keeps text or a colour of its own for a day on"))
        assertNull(move)
        val note = onDisk(load)
        assertEquals("own text", note.getJSONObject("overrides").getJSONObject("2026-10-12").getString("body"))
        assertEquals("room 4B", note.getJSONObject("overrides").getJSONObject("2026-10-13").getString("body"))
        assertNull(note.optJSONObject("moved"))
        assertEquals(listOf("2026-10-12"), Recurrence.offScheduleDays(note))
        // A deleted day's words kept under its date are held the same way.
        val deleted = scratch(series.replace("\"checklist\"",
            "\"exceptions\": [\"2026-10-12\"], \"overrides\": {\"2026-10-12\": {\"body\": \"kept\"}}, \"checklist\""))
        assertTrue(Days.moveDay(deleted, 1, "2026-10-19", "2026-10-12").first is SaveResult.Failed)
        assertEquals("kept", onDisk(deleted).getJSONObject("overrides").getJSONObject("2026-10-12").getString("body"))
        // A date the series holds nothing for still takes the day.
        assertTrue(Days.moveDay(load, 1, "2026-10-13", "2026-10-15").first is SaveResult.Ok)
    }

    @Test
    fun `a deleted day's Undo leaves it out while another day stands on its date`() {
        // The 12th deleted, the 19th moved onto its date, then the delete's
        // Undo: the 12th came back over the 19th, which was drawn nowhere,
        // its words read as the 12th's and the 12th's own lost (the twelfth round).
        val load = scratch(series)
        Days.saveDay(load, 1, "2026-10-12", "Stand-up", "Mon 12 own", "", "Work", "")
        Days.saveDay(load, 1, "2026-10-19", "Stand-up", "Mon 19 own", "", "Work", "")
        val gone = Days.deleteDay(load, 1, "2026-10-12").second!!
        assertTrue(Days.moveDay(load, 1, "2026-10-19", "2026-10-12").first is SaveResult.Ok)
        val undo = Days.undoDeleteDay(load, gone)
        assertTrue(undo is SaveResult.Failed && undo.reason.startsWith("Another day of this series is on"))
        val note = onDisk(load)
        assertEquals("2026-10-12", note.getJSONObject("moved").getString("2026-10-19"))
        assertEquals("Mon 19 own", note.getJSONObject("overrides").getJSONObject("2026-10-12").getString("body"))
        assertEquals(listOf("2026-10-12"), (0 until note.getJSONArray("exceptions").length()).map { note.getJSONArray("exceptions").getString(it) })
        // Moved away again, the date is free and the Undo brings the 12th back with its words.
        assertTrue(Days.moveDay(load, 1, "2026-10-12", "2026-10-20").first is SaveResult.Ok)
        assertTrue(Days.undoDeleteDay(load, gone) is SaveResult.Ok)
        assertEquals("Mon 12 own", onDisk(load).getJSONObject("overrides").getJSONObject("2026-10-12").getString("body"))
    }

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

    // ---- what the series says of its days ----

    @Test
    fun `the series counts the days keeping their own edits, and the days moved, as the desktop's banner`() {
        // Mondays: the 19th has its own words; the 7th, a Wednesday, has some
        // the schedule no longer reaches (the next line shows those); the
        // 12th is held on the 14th, and the 26th on the 2nd, a Monday the
        // series gives anyway -- one day there all the same. The desktop's
        // own functions, run on this note, answer 1 and 2.
        val note = JSONObject("""{"id": 1, "title": "Stand-up", "body": "agenda", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]},
            "overrides": {"2026-10-19": {"body": "own"}, "2026-10-07": {"body": "off"}},
            "moved": {"2026-10-12": "2026-10-14", "2026-10-26": "2026-11-02"}}""")
        assertEquals(1 to 2, Recurrence.seriesCounts(note))
        // Ended on the 12th: the 19th's edits are off the schedule now, and
        // the 26th's move draws nothing. The desktop answers 0 and 1.
        note.getJSONObject("recurrence").put("end", "2026-10-12")
        assertEquals(0 to 1, Recurrence.seriesCounts(note))
        assertEquals("a note that does not repeat says nothing", 0 to 0,
                     Recurrence.seriesCounts(JSONObject("""{"id": 2, "date": "2026-10-05", "overrides": {"2026-10-19": {}}}""")))
    }

    // ---- one place for a turned-off series' days: the note ----

    /** A note of a weekly series turned off by the phone's 0.12.0: the days in its copy, none in the note. */
    private fun shipped(days: String = """"exceptions": ["2026-11-16"], "overrides": {"2026-11-09": {"body": "Leg day - knee hurt"}}""",
                        rule: String = """{"freq": "weekly", "interval": 1}"""): String =
        """[{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up.", "comments": "", "category": "General",
            "date": "2026-11-02", "recurrence": {"freq": ""}, "exceptions": [], "overrides": {}, "moved": {},
            "_prev": {"title": "Gym", "body": "Warm up.", "comments": "", "formatting": [], "paragraphs": [],
                      "date": "2026-11-02", "recurrence": $rule, $days, "moved": {}}},
           {"id": 2, "uid": "o", "title": "Other", "body": "x", "category": "General"}]"""

    @Test
    fun `a series' days 0_12_0 kept only in its copy come into the note as it is read, once, and stay`() {
        // Her phone runs 0.12.0, whose turn-offs moved a series' days out of
        // the note into its copy; the desktop's next copy of words erased it.
        // The desktop's _days_back_from_copy, as the phone reads its notebook.
        val load = scratch(shipped())
        val note = load.raw!!.getJSONObject(0)
        assertEquals("Leg day - knee hurt", dayTitleOrBody(note, "2026-11-09"))
        assertEquals("2026-11-16", note.getJSONArray("exceptions").getString(0))
        assertEquals("the copy keeps them too", "Leg day - knee hurt", dayTitleOrBody(note.getJSONObject("_prev"), "2026-11-09"))
        assertEquals("inert: the note on that day is the note", "Warm up.", Recurrence.effectiveNote(note, "2026-11-09").getString("body"))
        // The next write keeps them -- any write, of any note.
        assertTrue(Notebook.updateNote(load, 2, "Other", "y") is SaveResult.Ok)
        assertEquals("Leg day - knee hurt", dayTitleOrBody(onDisk(load), "2026-11-09"))
        // A second read changes nothing, and an import brings them in the same way.
        assertEquals(onDisk(load).toString(), Notebook.load(load.file!!.parentFile!!).raw!!.getJSONObject(0).toString())
        val imported = Normalize.normalizeNote(JSONArray(shipped()).getJSONObject(0))
        assertEquals("Leg day - knee hurt", dayTitleOrBody(imported, "2026-11-09"))
        // Never days that hold no text, nor an update's copy of a note that
        // did not repeat, nor into a note that repeats as another series.
        val empty = scratch(shipped(days = """"exceptions": ["2026-11-16"], "overrides": {"2026-11-09": {"color": "#ff6b6b"}}"""))
        assertEquals(0, empty.raw!!.getJSONObject(0).getJSONArray("exceptions").length())
        val update = scratch(shipped(rule = """{"freq": ""}"""))
        assertEquals(0, update.raw!!.getJSONObject(0).getJSONObject("overrides").length())
        val elsewhere = scratch(shipped().replaceFirst(""""date": "2026-11-02", "recurrence": {"freq": ""}""",
                                                       """"date": "2026-11-09", "recurrence": {"freq": "weekly"}"""))
        assertEquals(0, elsewhere.raw!!.getJSONObject(0).getJSONObject("overrides").length())
        // Into one repeating again as that same series -- 0.12.0's turn-off,
        // then on as it was, which gave the note none of its days back (the
        // seventh round, as the desktop's cdbb89e).
        val back = scratch(shipped().replaceFirst(""""recurrence": {"freq": ""}""", """"recurrence": {"freq": "weekly"}"""))
        assertEquals("Leg day - knee hurt", dayTitleOrBody(back.raw!!.getJSONObject(0), "2026-11-09"))
    }

    private fun dayTitleOrBody(note: JSONObject, day: String): String? =
        (note.opt("overrides") as? JSONObject)?.optJSONObject(day)?.optString("body")

    @Test
    fun `the read asks the move of the note as the desktop's load shapes it, not of the file as it lies`() {
        // The desktop moves the days at the end of normalize_note: its deleted
        // days real dates, its copy shaped, a day's edit its text alone. The
        // phone asked the raw file, and odd shapes moved differently. Each
        // answer here is the desktop's own, run on its core at a4bbf05
        // (scratch probe_read_parity.py): days in the note, or none.
        fun read(over: String, prev: String = ""): JSONObject {
            val copy = """"title": "Gym", "body": "Warm up.", "comments": "", "formatting": [], "paragraphs": [],
                "date": "2026-11-02", "recurrence": {"freq": "weekly", "interval": 1}, "exceptions": [],
                "overrides": {"2026-11-09": {"body": "Leg day"}}, "moved": {}"""
            val note = JSONObject("""{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up.", "comments": "",
                "category": "General", "date": "2026-11-02", "recurrence": {"freq": ""}, "exceptions": [],
                "overrides": {}, "moved": {}, "_prev": {$copy}}""")
            JSONObject("{$over}").let { o -> for (k in o.keys()) note.put(k, o.get(k)) }
            if (prev.isNotEmpty()) JSONObject("{$prev}").let { o ->
                val p = note.getJSONObject("_prev")
                for (k in o.keys()) if (o.isNull(k)) p.remove(k) else p.put(k, o.get(k))
            }
            return scratch(JSONArray().put(note).toString()).raw!!.getJSONObject(0)
        }
        // A deleted day the desktop's load does not keep stops no move.
        assertEquals("Leg day", dayTitleOrBody(read(""""exceptions": ["16.11.2026"]"""), "2026-11-09"))
        assertEquals("Leg day", dayTitleOrBody(read(""""exceptions": [5, "x"]"""), "2026-11-09"))
        // A copy the desktop's load reads as no series, or whose day it drops, moves nothing.
        assertEquals(0, read("", """"date": null""").getJSONObject("overrides").length())
        assertEquals(0, read("", """"recurrence": {"freq": "hourly"}""").getJSONObject("overrides").length())
        assertEquals(0, read("", """"overrides": {"someday": {"body": "Leg day"}}""").getJSONObject("overrides").length())
        assertEquals(0, read("", """"overrides": {"2026-11-09": {"title": 5}}""").getJSONObject("overrides").length())
        // An update's comparison reads the note without its copy, as the
        // desktop's shared_update_changes does: the move from the copy is a
        // read's, never a difference the same backup brings.
        val mine = JSONObject("""{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up.", "category": "General",
            "date": "2026-11-02", "recurrence": {"freq": ""}, "exceptions": [], "overrides": {}, "moved": {},
            "_prev": {"title": "Gym", "body": "Warm up.", "comments": "", "formatting": [], "paragraphs": [],
                      "date": "2026-11-02", "recurrence": {"freq": "weekly", "interval": 1},
                      "overrides": {"2026-11-09": {"body": "Leg day"}}}}""")
        val same = JSONObject(mine.toString()).apply { remove("_prev") }
        assertFalse(Notebook.sharedUpdateChanges(mine, same, Notebook.UPDATABLE_FROM_BACKUP))
        // And a day's own text read as Python's `or ""` reads it: a number is no empty title.
        assertTrue(Notebook.daysHoldOwnText(JSONObject("""{"title": "", "body": "b", "overrides": {"2026-11-09": {"title": 5}}}""")))
        assertTrue(Notebook.daysHoldOwnText(JSONObject("""{"title": "Gym", "body": "b", "overrides": {"2026-11-09": {"title": ""}}}""")))
    }

    @Test
    fun `a one-off's days with no text of their own go at its next edit, as the desktop's save clears them`() {
        val days = """"exceptions": ["2026-11-16"], "overrides": {"2026-11-09": {"color": "#ff6b6b"}}"""
        val load = scratch("""[{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up.", "date": "2026-11-02",
            "recurrence": {"freq": ""}, $days}]""")
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Warm up!") is SaveResult.Ok)
        assertEquals(0, onDisk(load).getJSONArray("exceptions").length())
        assertEquals(0, onDisk(load).getJSONObject("overrides").length())
        // A day's own text stays.
        val own = scratch("""[{"id": 1, "uid": "g", "title": "Gym", "body": "Warm up.", "date": "2026-11-02",
            "recurrence": {"freq": ""}, "overrides": {"2026-11-09": {"body": "Leg day"}}}]""")
        assertTrue(Notebook.updateNote(own, 1, "Gym", "Warm up!") is SaveResult.Ok)
        assertEquals("Leg day", dayTitleOrBody(onDisk(own), "2026-11-09"))
    }

    @Test
    fun `a restarted series' first day stays after a big delete and Restore previous`() {
        // Deleted, then the repeat ended by a share code and started again
        // on that very day: a copy of words carried the old deleted day, and
        // Restore previous put it back over the new series' first day.
        val paragraph = "Warm-up ten minutes on the bike, then squats five by five and lunges three by twelve, plank to finish."
        val load = scratch("""[{"id": 1, "uid": "s", "title": "Gym", "body": "Plan\n$paragraph", "category": "Work",
            "date": "2026-11-02", "recurrence": {"freq": "weekly", "interval": 1}, "exceptions": ["2026-11-09"]}]""")
        val theirs = JSONObject("""{"uid": "s", "title": "Gym", "body": "Plan\n$paragraph", "category": "Work",
            "date": "2026-11-02", "recurrence": {"freq": ""}}""")
        assertTrue(Importing.apply(load, Importing.plan(load.raw!!, listOf(theirs), Importing.Source.CODE),
            Importing.Answers(update = true), null) is Importing.Result.Done)
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Plan\n$paragraph", date = "2026-11-09") is SaveResult.Ok)
        assertTrue(Days.setRepeat(load, 1, JSONObject("""{"freq": "weekly", "interval": 1}"""), startWas = "2026-11-02") is SaveResult.Ok)
        assertTrue(Notebook.updateNote(load, 1, "Gym", "Plan") is SaveResult.Ok)
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        val back = onDisk(load)
        assertEquals("Plan\n$paragraph", back.getString("body"))
        assertEquals(listOf("2026-11-09", "2026-11-16", "2026-11-23", "2026-11-30"),
            Recurrence.expand(back, java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `a share code restarting a series from another start keeps its old deleted days in the copy`() {
        val load = scratch(shipped())
        val theirs = JSONObject("""{"uid": "g", "title": "Gym", "body": "Warm up.", "category": "General",
            "date": "2026-11-09", "recurrence": {"freq": "weekly", "interval": 1}}""")
        assertTrue(Importing.apply(load, Importing.plan(load.raw!!, listOf(theirs), Importing.Source.CODE),
            Importing.Answers(update = true), null) is Importing.Result.Done)
        val note = onDisk(load)
        assertEquals("the old deleted day no longer hides the new series' own", 0, note.getJSONArray("exceptions").length())
        assertEquals("the copy keeps it", "2026-11-16", note.getJSONObject("_prev").getJSONArray("exceptions").getString(0))
        assertEquals("a day's own text stays", "Leg day - knee hurt", dayTitleOrBody(note, "2026-11-09"))
    }

    @Test
    fun `a copy of words that carries days, as the audit's builds made, gives no words to a turn-off`() {
        // The desktop's _turn_off_rescue_copy takes a copy of words alone;
        // one holding days is a copy of more than words.
        val load = scratch("""[{"id": 1, "uid": "s", "title": "Gym", "body": "agenda", "category": "Work",
            "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]},
            "overrides": {"2026-10-12": {"title": "Own"}},
            "_prev": {"title": "Gym", "body": "older words", "comments": "", "formatting": [], "paragraphs": [],
                      "overrides": {"2026-09-28": {"title": "Old own"}}}}]""")
        assertTrue(Days.setRepeat(load, 1, null) is SaveResult.Ok)
        assertEquals("the note as it stands", "agenda", onDisk(load).getJSONObject("_prev").getString("body"))
    }

    /** A weekly stand-up repeating as its copy's series again, a day with words of its own; the copy holds others for it. */
    private fun dayAndCopyDiffer(): String = """[{"id": 1, "uid": "s", "title": "Standup", "body": "SERIES TEXT",
        "category": "Work", "date": "2026-09-07", "recurrence": {"freq": "weekly", "interval": 1},
        "overrides": {"2026-09-14": {"body": "Moved to room 2"}},
        "_prev": {"title": "Standup", "body": "SERIES TEXT", "date": "2026-09-07",
                  "recurrence": {"freq": "weekly", "interval": 1},
                  "overrides": {"2026-09-14": {"body": "DAY 14 OWN TEXT, room 4B"}}}}]"""

    @Test
    fun `a day with words of its own keeps them, the copy's a note of their own, kept by the next write and said at the start`() {
        // Added to the day, four rounds read them four ways, each leaving a
        // case (the final audit's fifteenth round, the owner's choice).
        val load = scratch(dayAndCopyDiffer())
        assertEquals(1, load.setAside)
        val made = load.raw!!.getJSONObject(1)
        assertEquals("Standup (earlier text)", made.getString("title"))
        assertEquals("DAY 14 OWN TEXT, room 4B", made.getString("body"))
        assertEquals("2026-09-14", made.getString("date"))
        assertEquals("Work", made.getString("category"))
        assertTrue(made.getString("created").isNotEmpty() && made.getString("updated").isNotEmpty())
        val note = load.raw!!.getJSONObject(0)
        assertEquals("the day keeps its own", "Moved to room 2", Recurrence.effectiveNote(note, "2026-09-14").getString("body"))
        assertFalse(note.has("_set_aside"))
        // Unwritten, the next start makes the same note from the same file, once;
        // written, none makes it again.
        assertEquals(1, Notebook.load(load.file!!.parentFile!!).setAside)
        assertTrue(Notebook.updateNote(load, 1, "Standup", "SERIES TEXT!") is SaveResult.Ok)
        val disk = JSONArray(load.file!!.readText())
        assertEquals(2, disk.length())
        assertFalse(disk.getJSONObject(0).has("_set_aside"))
        assertEquals(0, Notebook.load(load.file!!.parentFile!!).setAside)
        val fresh = NioFiles.createTempDirectory("days").toFile()
        File(fresh, "notes_data.json").writeText(dayAndCopyDiffer())
        assertTrue(Startup.prepare(fresh, older = null).orEmpty().contains("It is kept as 1 note of its own"))
    }

    @Test
    fun `days set aside read from a file keep a real date in the calendar's form and a word, as the desktop reads them`() {
        val read = Normalize.normalizeNote(JSONObject("""{"id": 28, "title": "Gym", "_set_aside": [
            {"day": " 2026-11-9 ", "body": "Kept", "color": 5}, {"day": 5, "body": "No date"},
            "not a day", {"day": "2026-11-10", "body": "   "}, {"day": "2026-11-11", "title": "Only a title"}]}"""))
        val aside = read.getJSONArray("_set_aside")
        assertEquals(listOf("2026-11-09" to "Kept", "2026-11-11" to ""),
            (0 until aside.length()).map { aside.getJSONObject(it).let { e -> e.getString("day") to e.getString("body") } })
        assertEquals("", aside.getJSONObject(0).getString("color"))
    }

    @Test
    fun `a file's days set aside are read at most as many as a series' moved days`() {
        // Each becomes a note at the next start, and a file can say anything;
        // a save empties the list each time (the desktop's cap).
        val flood = JSONArray()
        for (i in 0..Recurrence.MAX_OCCURRENCES) flood.put(JSONObject().put("day", "2026-11-09").put("body", "words $i"))
        val read = Normalize.normalizeNote(JSONObject().put("id", 30).put("title", "Gym").put("_set_aside", flood))
        val aside = read.getJSONArray("_set_aside")
        assertEquals(Recurrence.MAX_OCCURRENCES, aside.length())
        assertEquals("words 0", aside.getJSONObject(0).getString("body"))
    }

    @Test
    fun `a note restored from the Trash makes its copy's days set aside notes of their own in the same write`() {
        val dir = NioFiles.createTempDirectory("days").toFile()
        File(dir, "notes_data.json").writeText("[]")
        val entry = JSONArray(dayAndCopyDiffer()).getJSONObject(0).put("deleted_at", "2026-10-27 10:00:00")
        File(dir, "trash.json").writeText(JSONArray().put(entry).toString())
        val load = Notebook.load(dir)
        assertTrue(Notebook.restoreFromTrash(load, 1) is SaveResult.Ok)
        val disk = JSONArray(load.file!!.readText())
        assertEquals(listOf("Standup", "Standup (earlier text)"), (0 until disk.length()).map { disk.getJSONObject(it).getString("title") })
        assertEquals(2, load.raw!!.length())
        assertFalse(disk.getJSONObject(0).has("_set_aside"))
    }

    /** A note as both programs' parity is judged: its words, its schedule, its days and its copy, each as plain data. */
    private fun canon(note: JSONObject?): Map<String, Any?>? {
        note ?: return null
        fun field(holder: JSONObject, key: String): Any? =
            if (key == "recurrence") plainJson(Recurrence.normalize(holder.opt(key))) else plainJson(holder.opt(key))
        val out = sortedMapOf<String, Any?>()
        for (key in listOf("title", "body", "comments", "date", "recurrence", "exceptions", "overrides", "moved")) {
            if (note.has(key) && !note.isNull(key)) out[key] = field(note, key)
        }
        (note.opt("_prev") as? JSONObject)?.let { prev ->
            out["_prev"] = prev.keys().asSequence().filter { !prev.isNull(it) }.associateWith { field(prev, it) }.toSortedMap()
        }
        return out
    }

    /** A note made of a day set aside, as both programs' parity is judged: its words, its look and its date. */
    private fun asideOf(note: JSONObject): Map<String, Any?> =
        listOf("title", "body", "comments", "category", "color", "date").associateWith { note.opt(it) }.toSortedMap()

    /** The desktop's notes made of days set aside, as the fixture lists them. */
    private fun asideCanon(made: JSONArray): List<Map<String, Any?>> = (0 until made.length()).map { asideOf(made.getJSONObject(it)) }

    /** The phone's: every note after the one the case walks, made of its days set aside. */
    private fun madeCanon(raw: JSONArray): List<Map<String, Any?>> = (1 until raw.length()).map { asideOf(raw.getJSONObject(it)) }

    private fun plainJson(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plainJson(value.opt(it)) }.toSortedMap()
        is JSONArray -> (0 until value.length()).map { plainJson(value.opt(it)) }
        is Int -> value.toLong()
        is Long -> value
        JSONObject.NULL -> null
        else -> value
    }

    @Test
    fun `every step of the days fixture stores the note the desktop stores, and imports as the desktop imports it`() {
        // days_cases.json: the desktop's own rules, run by
        // tools/make_days_fixture.py, step by step.
        val stream = javaClass.classLoader?.getResourceAsStream("days_cases.json")
        assertNotNull("days_cases.json is missing - rebuild it with tools/make_days_fixture.py", stream)
        val cases = JSONObject(stream!!.readBytes().toString(Charsets.UTF_8)).getJSONArray("cases")
        assertTrue(cases.length() >= 62)
        for (c in 0 until cases.length()) {
            val case = cases.getJSONObject(c)
            val name = case.getString("name")
            val steps = case.getJSONArray("steps")
            // A note that starts in the Trash, as it lay there.
            val trashed = steps.length() > 0 && steps.getJSONObject(0).getString("do") == "untrash"
            var load = if (!trashed) scratch(JSONArray().put(case.getJSONObject("note")).toString()) else {
                val dir = NioFiles.createTempDirectory("days").toFile()
                File(dir, "notes_data.json").writeText("[]")
                File(dir, "trash.json").writeText(JSONArray().put(JSONObject(case.getJSONObject("note").toString())
                    .put("deleted_at", "2026-10-27 10:00:00")).toString())
                Notebook.load(dir)
            }
            if (steps.length() == 0) {
                assertEquals("$name: as read", canon(case.getJSONObject("loaded")), canon(load.raw!!.getJSONObject(0)))
                assertEquals("$name: set aside as read", asideCanon(case.getJSONArray("aside_loaded")), madeCanon(load.raw!!))
            }
            var stored = load.raw!!.optJSONObject(0)
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val fileWas = load.file!!.readText()
                val now = load.raw!!.optJSONObject(0) ?: JSONObject()
                val title = now.optString("title")
                val body = now.optString("body")
                when (step.getString("do")) {
                    // The Repeat sheet's two writes: the start, then the
                    // repeat, which settles the schedule and spends once.
                    "repeat" -> {
                        val was = now.optString("date")
                        val date = step.getString("date")
                        if (date != was) {
                            assertTrue(Notebook.updateNote(load, 1, title, body, date = date, scheduleFollows = true) is SaveResult.Ok)
                        }
                        assertTrue(Days.setRepeat(load, 1, step.getJSONObject("rule"), startWas = if (date != was) was else null) is SaveResult.Ok)
                    }
                    "write" -> assertTrue(Notebook.updateNote(load, 1, step.getString("title"), step.getString("body"),
                        comments = step.getString("comments"),
                        formatting = step.optJSONArray("formatting")?.let { Exporting.normalizeFormatting(it) }) is SaveResult.Ok)
                    "untrash" -> assertTrue("$name: untrash",
                        Organizing.restore(load, load.trash.map { Organizing.keyOf(it) }) is SaveResult.Ok)
                    "read" -> load = Notebook.load(load.file!!.parentFile!!)
                    "restore" -> assertTrue("$name: restore", Notebook.restorePrevious(load, 1) is SaveResult.Ok)
                    // A day reset to the series: its own edit dropped.
                    "reset" -> assertTrue("$name: reset", Days.resetDay(load, 1, step.getString("day")) is SaveResult.Ok)
                    "share", "backup" -> {
                        val source = if (step.getString("do") == "share") Importing.Source.CODE else Importing.Source.BACKUP
                        assertTrue(Importing.apply(load, Importing.plan(load.raw!!, listOf(step.getJSONObject("incoming")), source),
                            Importing.Answers(update = true), null) is Importing.Result.Done)
                    }
                }
                // The file, when the step wrote it; when it wrote nothing -- a
                // read, an import that changed nothing -- the note as the app
                // holds it, which its next write stores: read from the file,
                // a notebook 0.12.0 left showed the days its read had moved
                // (the eighth round's backup of a series turned back on).
                stored = if (load.file!!.readText() != fileWas) onDisk(load) else load.raw!!.getJSONObject(0)
                assertEquals("$name, step ${s + 1} (${step.getString("do")})",
                    canon(case.getJSONArray("after").getJSONObject(s)), canon(stored!!))
                assertEquals("$name, step ${s + 1}: set aside",
                    asideCanon(case.getJSONArray("aside_after").getJSONArray(s)), madeCanon(load.raw!!))
            }
            assertEquals("$name: imported", canon(case.getJSONObject("imported")), canon(Normalize.normalizeNote(stored!!)))
        }
    }
}
