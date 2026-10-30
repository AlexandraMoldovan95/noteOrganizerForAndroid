package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The desktop's `test_recurrence.py`, case for case: its acceptance suite
 * for repeating notes is this port's too. Each check keeps the desktop's
 * name, so a failure here can be read against the Python.
 */
class RecurrenceTest {

    private fun note(json: String) = JSONObject(json)
    private fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)
    private fun expand(n: JSONObject, a: LocalDate, b: LocalDate, limit: Int? = null) =
        Recurrence.expand(n, a, b, limit)

    // ---- many moved days, asked of all at once ----

    /** A series with [moves] of its days held on the day after, and [own] days edited, some off its schedule. */
    private fun busySeries(random: kotlin.random.Random, moves: Int, own: Int): JSONObject {
        val freq = listOf("daily", "weekly", "monthly").random(random)
        val rule = JSONObject().put("freq", freq).put("interval", 1 + random.nextInt(2))
        if (freq == "weekly") rule.put("weekdays", JSONArray(listOf(0, 2, 4).shuffled(random).take(1 + random.nextInt(3))))
        if (random.nextBoolean()) rule.put("count", 20 + random.nextInt(300))
        val n = JSONObject().put("id", 1).put("date", "2026-01-05").put("recurrence", rule)
        val days = Recurrence.expand(n, d(2026, 1, 1), d(2031, 12, 31))
        val exceptions = days.shuffled(random).take(days.size / 10)
        n.put("exceptions", JSONArray(exceptions))
        val moved = JSONObject()
        for (day in days.shuffled(random).take(minOf(moves, days.size))) {
            moved.put(day, Recurrence.formatDate(Recurrence.parseDate(day)!!.plusDays(1L + random.nextInt(3))))
        }
        n.put("moved", moved)
        val overrides = JSONObject()
        for (day in (days.shuffled(random).take(own) + listOf("2026-01-06", "2027-03-03"))) {
            overrides.put(day, JSONObject().put("title", "own $day"))
        }
        return n.put("overrides", overrides)
    }

    @Test
    fun `days off the schedule and the banner's counts agree with asking one day at a time`() {
        // The answers are the day-by-day ones exactly, on series of every kind.
        val random = kotlin.random.Random(20261028)
        repeat(120) {
            val n = busySeries(random, moves = random.nextInt(40), own = random.nextInt(30))
            val schedule = JSONObject(n.toString()).put("exceptions", JSONArray())
            val exceptions = (0 until n.getJSONArray("exceptions").length()).map { n.getJSONArray("exceptions").getString(it) }.toSet()
            val overrides = n.getJSONObject("overrides")
            val off = overrides.keys().asSequence().sorted().filter { day ->
                val on = Recurrence.parseDate(day)!!
                day !in exceptions && Recurrence.expand(schedule, on, on) != listOf(day)
            }.toList()
            assertEquals(n.toString(), off, Recurrence.offScheduleDays(n))
            val moved = n.getJSONObject("moved")
            val held = moved.keys().asSequence().count { key ->
                val now = moved.getString(key)
                val on = Recurrence.parseDate(now)!!
                Recurrence.expand(n, on, on) == listOf(now)
            }
            assertEquals(n.toString(), maxOf(0, overrides.length() - off.size) to held, Recurrence.seriesCounts(n))
        }
    }

    @Test
    fun `a series with thousands of moved days reads them once for its banner, its days off schedule and the History`() {
        // 5,000 moved days, as a backup may bring: asked one day at a time,
        // each question read all 5,000, and the note froze as it opened.
        val n = note("""{"id": 1, "title": "Standup", "date": "2026-01-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]}}""")
        val moved = JSONObject()
        val overrides = JSONObject()
        var monday = d(2026, 1, 5)
        repeat(5000) {
            moved.put(Recurrence.formatDate(monday), Recurrence.formatDate(monday.plusDays(1)))
            if (it % 2 == 0) overrides.put(Recurrence.formatDate(monday.plusDays(1)), JSONObject().put("title", "own"))
            monday = monday.plusWeeks(1)
        }
        n.put("moved", moved).put("overrides", overrides)
        Recurrence.movesRead = 0
        assertEquals(2500 to 5000, Recurrence.seriesCounts(n))
        assertTrue(Recurrence.offScheduleDays(n).isEmpty())
        assertTrue("read ${Recurrence.movesRead} times", Recurrence.movesRead <= 4)
        Recurrence.movesRead = 0
        val history = Recurrence.history(listOf(n), today = d(2026, 10, 28))
        assertEquals(1, history.size)
        assertTrue("read ${Recurrence.movesRead} times", Recurrence.movesRead <= 6)
    }

    // ---- the frequencies ----

    @Test
    fun `weekly Mon Wed Fri gives 14 dates in July, all on those days`() {
        val n = note("""{"id":1,"date":"2026-07-01","recurrence":{"freq":"weekly","weekdays":[0,2,4]}}""")
        val days = expand(n, d(2026, 7, 1), d(2026, 7, 31))
        assertEquals(14, days.size)
        assertTrue(days.all { LocalDate.parse(it).dayOfWeek.value - 1 in listOf(0, 2, 4) })
    }

    @Test
    fun `daily every 3 gives the right count`() {
        val n = note("""{"id":2,"date":"2026-01-01","recurrence":{"freq":"daily","interval":3}}""")
        assertEquals(11, expand(n, d(2026, 1, 1), d(2026, 1, 31)).size)
    }

    @Test
    fun `monthly day 31 with clamp lands on Feb 28 and Apr 30`() {
        val n = note("""{"id":3,"date":"2026-01-31","recurrence":{"freq":"monthly","month_days":[31],"month_day_policy":"clamp"}}""")
        val days = expand(n, d(2026, 1, 1), d(2026, 6, 30))
        assertTrue("2026-02-28" in days)
        assertTrue("2026-04-30" in days)
    }

    @Test
    fun `month-end days that clamp onto one date occur on it once, and count once`() {
        val eom = note("""{"id":30,"date":"2026-01-01","recurrence":{"freq":"monthly","month_days":[28,29,30,31]}}""")
        assertEquals(listOf("2026-02-28"), expand(eom, d(2026, 2, 1), d(2026, 2, 28)))
        assertEquals(listOf("2026-04-28", "2026-04-29", "2026-04-30"), expand(eom, d(2026, 4, 1), d(2026, 4, 30)))
        eom.getJSONObject("recurrence").put("count", 8)
        val all = expand(eom, d(2026, 1, 1), d(2026, 12, 31))
        assertEquals("\"8 times\" means 8 different days, not 8 landings", 8, all.toSet().size)
        assertEquals(8, all.size)
        assertEquals("2026-03-30", all.last())
    }

    @Test
    fun `monthly day 31 with skip has no February and keeps March 31`() {
        val n = note("""{"id":4,"date":"2026-01-31","recurrence":{"freq":"monthly","month_days":[31],"month_day_policy":"skip"}}""")
        val days = expand(n, d(2026, 1, 1), d(2026, 6, 30))
        assertFalse(days.any { it.startsWith("2026-02") })
        assertTrue("2026-03-31" in days)
    }

    @Test
    fun `yearly gives 5 occurrences over 5 years`() {
        val n = note("""{"id":5,"date":"2026-03-15","recurrence":{"freq":"yearly"}}""")
        assertEquals(5, expand(n, d(2026, 1, 1), d(2030, 12, 31)).size)
    }

    @Test
    fun `count, end date and exceptions`() {
        val counted = note("""{"id":6,"date":"2026-01-01","recurrence":{"freq":"daily","count":7}}""")
        assertEquals(7, expand(counted, d(2026, 1, 1), d(2027, 1, 1)).size)
        val ended = note("""{"id":7,"date":"2026-01-01","recurrence":{"freq":"daily","end":"2026-01-10"}}""")
        val untilTenth = expand(ended, d(2026, 1, 1), d(2027, 1, 1))
        assertEquals("2026-01-10", untilTenth.last())
        assertEquals(10, untilTenth.size)
        val skipped = note("""{"id":8,"date":"2026-01-01","recurrence":{"freq":"daily","count":5},"exceptions":["2026-01-02","2026-01-04"]}""")
        val left = expand(skipped, d(2026, 1, 1), d(2026, 12, 31))
        assertEquals(3, left.size)
        assertFalse("2026-01-02" in left || "2026-01-04" in left)
    }

    @Test
    fun `the range restricts even an open-ended rule, and a one-off note is its one date`() {
        val n = note("""{"id":9,"date":"2026-01-01","recurrence":{"freq":"daily"}}""")
        assertEquals(listOf("2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05"),
            expand(n, d(2026, 6, 1), d(2026, 6, 5)))
        assertEquals(listOf("2026-05-15"),
            expand(note("""{"id":10,"date":"2026-05-15","recurrence":{}}"""), d(2026, 5, 1), d(2026, 5, 31)))
        assertEquals(emptyList<String>(),
            expand(note("""{"id":11,"recurrence":{}}"""), d(2026, 1, 1), d(2026, 12, 31)))
    }

    // ---- normalising a rule ----

    @Test
    fun `a rule is normalised field by field, with both ceilings`() {
        assertEquals("", Recurrence.normalize(null).getString("freq"))
        assertEquals("", Recurrence.normalize(JSONObject().put("freq", "bogus")).getString("freq"))
        assertEquals(1, Recurrence.normalize(JSONObject().put("freq", "daily").put("interval", -5)).getInt("interval"))
        assertEquals(Recurrence.MAX_INTERVAL,
            Recurrence.normalize(JSONObject().put("freq", "daily").put("interval", 10_000_000_000L)).getInt("interval"))
        assertEquals(3, Recurrence.normalize(JSONObject().put("freq", "daily").put("interval", 3)).getInt("interval"))
        assertEquals(Recurrence.MAX_INTERVAL,
            Recurrence.normalize(JSONObject().put("freq", "daily").put("interval", Recurrence.MAX_INTERVAL)).getInt("interval"))
        assertEquals(Recurrence.MAX_OCCURRENCES,
            Recurrence.normalize(JSONObject().put("freq", "daily").put("count", 1_000_000_000)).getInt("count"))
        assertEquals(5, Recurrence.normalize(JSONObject().put("freq", "daily").put("count", 5)).getInt("count"))
    }

    @Test
    fun `a poisoned interval neither overflows nor reaches past the import`() {
        val poison = note("""{"id":300,"title":"x","date":"2026-01-01","recurrence":{"freq":"daily","interval":10000000000}}""")
        assertEquals(Recurrence.MAX_INTERVAL,
            Normalize.normalizeNote(poison).getJSONObject("recurrence").getInt("interval"))
        expand(poison, d(2026, 9, 1), d(2026, 9, 30))
        Recurrence.nextOnOrAfter(poison, "2026-09-01")
    }

    @Test
    fun `a 36-year-old yearly rule still finds July 2026 at once`() {
        val n = note("""{"id":99,"date":"1990-07-04","recurrence":{"freq":"yearly"}}""")
        val start = System.nanoTime()
        assertEquals(listOf("2026-07-04"), expand(n, d(2026, 7, 1), d(2026, 7, 31)))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500)
    }

    // ---- the next day: the "Calendar: soonest" sort key ----

    @Test
    fun `the next occurrence on or after a date`() {
        val weekly = note("""{"id":200,"date":"2026-01-05","recurrence":{"freq":"weekly"}}""")
        assertEquals("2026-07-06", Recurrence.nextOnOrAfter(weekly, "2026-07-01"))
        assertEquals("2026-07-06", Recurrence.nextOnOrAfter(weekly, "2026-07-06"))
        assertEquals("2026-07-15", Recurrence.nextOnOrAfter(
            note("""{"id":201,"date":"2026-01-01","recurrence":{"freq":"daily"}}"""), "2026-07-15"))
        assertNull(Recurrence.nextOnOrAfter(
            note("""{"id":202,"date":"2026-01-01","recurrence":{"freq":"daily","end":"2026-02-01"}}"""), "2026-07-01"))
        assertNull(Recurrence.nextOnOrAfter(note("""{"id":203,"date":"2026-07-01"}"""), "2026-06-01"))
        assertEquals("2027-01-01", Recurrence.nextOnOrAfter(
            note("""{"id":204,"date":"2026-01-01","recurrence":{"freq":"yearly"}}"""), "2026-01-02"))
        assertNull(Recurrence.nextOnOrAfter(weekly, "not a date"))
    }

    // ---- the History ----

    private val today = d(2026, 9, 9)

    private val historyNotes = listOf(
        note("""{"id":1,"title":"Meeting X","body":"plan Q4","comments":"","date":"2026-07-20"}"""),
        note("""{"id":2,"title":"Old dated","body":"x","comments":"","date":"2023-03-05"}"""),
        note("""{"id":3,"title":"Daily sync","body":"agenda","comments":"","date":"2026-01-01",
                 "recurrence":{"freq":"daily"},"exceptions":["2026-02-02"],
                 "overrides":{
                   "2026-08-15":{"title":"","body":"discussed the budget","comments":"","category":"General","color":""},
                   "2025-12-01":{"title":"Year-end sync","body":"retro","comments":"","category":"General","color":""},
                   "2026-02-02":{"title":"stray","body":"on an excepted day","comments":"","category":"General","color":""}}}"""),
        note("""{"id":4,"title":"Lonely series","body":"no edited days","comments":"","date":"2026-05-01","recurrence":{"freq":"weekly"}}"""),
        note("""{"id":5,"title":"No date note","body":"off-calendar","comments":""}"""),
        note("""{"id":6,"title":"Bounded3","body":"x","comments":"","date":"2026-06-01","recurrence":{"freq":"monthly","count":3},
                 "overrides":{"2026-07-01":{"title":"MidRun","body":"edited","comments":"","category":"General","color":""}}}"""),
        note("""{"id":7,"title":"BigCount","body":"x","comments":"","date":"2026-04-01","recurrence":{"freq":"daily","count":100}}"""),
    )

    private fun series(entries: List<Recurrence.HistoryEntry>, id: Int) =
        entries.first { it.noteId == id } as Recurrence.HistoryEntry.Series

    @Test
    fun `the History lists dated notes and series, newest first, and skips undated notes`() {
        val all = Recurrence.history(historyNotes, today = today)
        assertEquals(listOf(3, 6, 1, 4, 7, 2), all.map { it.noteId })
    }

    @Test
    fun `a bounded small series lists all its days, with honest edited flags`() {
        val s6 = series(Recurrence.history(historyNotes, today = today), 6)
        assertEquals("occurrences", s6.daysKind)
        assertEquals(listOf("2026-08-01", "2026-07-01", "2026-06-01"), s6.days.map { it.date })
        assertEquals(3, s6.occurrences)
        assertEquals(listOf(false, true, false), s6.days.map { it.edited })
        assertEquals(1, s6.edited)
        assertEquals("MidRun", s6.days[1].title)
    }

    @Test
    fun `a big count falls back to edited days, and a childless series jumps somewhere`() {
        val all = Recurrence.history(historyNotes, today = today)
        val s7 = series(all, 7)
        assertEquals("edited", s7.daysKind)
        assertTrue(s7.days.isEmpty())
        assertEquals("a childless ENDED series jumps to its start date", "2026-04-01", s7.jumpDate)
        assertEquals("a childless LIVE series jumps to its next occurrence", "2026-09-11", series(all, 4).jumpDate)
    }

    @Test
    fun `a series lists its edited days, newest first, a deleted day's edit left out`() {
        val s3 = series(Recurrence.history(historyNotes, today = today), 3)
        assertEquals(listOf("2026-08-15", "2025-12-01"), s3.days.map { it.date })
        assertEquals("Daily sync", s3.days[0].title)
        assertEquals("Year-end sync", s3.days[1].title)
    }

    @Test
    fun `search reaches into a day's own edit and opens the group, a base match does not`() {
        val q = Recurrence.history(historyNotes, query = "budget", today = today)
        assertEquals(listOf(3), q.map { it.noteId })
        val s = q[0] as Recurrence.HistoryEntry.Series
        assertEquals(listOf("2026-08-15"), s.days.map { it.date })
        assertTrue(s.autoExpand)
        val q2 = Recurrence.history(historyNotes, query = "agenda", today = today)
        assertEquals(listOf(3), q2.map { it.noteId })
        assertFalse((q2[0] as Recurrence.HistoryEntry.Series).autoExpand)
    }

    @Test
    fun `the year filter keeps only that year's activity`() {
        val last = Recurrence.history(historyNotes, yearBucket = "Last year", today = today)
        assertEquals(listOf(3), last.map { it.noteId })
        assertEquals(listOf("2025-12-01"), (last[0] as Recurrence.HistoryEntry.Series).days.map { it.date })
        assertEquals(listOf(2), Recurrence.history(historyNotes, yearBucket = "Older", today = today).map { it.noteId })
        assertTrue(Recurrence.history(historyNotes, yearBucket = "2 years ago", today = today).isEmpty())
    }

    // ---- days off the schedule ----

    private val own = """{"title":"Own","body":"own text","comments":"","category":"General","color":""}"""

    private fun seriesNote(rule: String, date: String = "2026-09-07",
                           overrides: List<String> = listOf("2026-09-14"),
                           exceptions: List<String> = emptyList()): JSONObject {
        val o = JSONObject()
        for (day in overrides) o.put(day, JSONObject(own))
        return JSONObject().put("id", 1).put("title", "S").put("body", "b").put("date", date)
            .put("recurrence", JSONObject(rule)).put("overrides", o).put("exceptions", JSONArray(exceptions))
    }

    @Test
    fun `a day's own edit falls off the schedule when the rule stops reaching it`() {
        assertEquals(emptyList<String>(), Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[0]}""")))
        assertEquals(listOf("2026-09-14"), Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[1]}""")))
        assertEquals(listOf("2026-09-14"),
            Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[0]}""", date = "2026-09-21")))
        assertEquals(listOf("2026-09-14"), Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[0],"count":1}""")))
        assertEquals(listOf("2026-09-14"),
            Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[0],"end":"2026-09-10"}""")))
        assertEquals("a deleted day is not counted as off", emptyList<String>(),
            Recurrence.offScheduleDays(seriesNote("""{"freq":"weekly","weekdays":[1]}""", exceptions = listOf("2026-09-14"))))
        assertEquals(emptyList<String>(), Recurrence.offScheduleDays(seriesNote("{}")))
        assertEquals(listOf("2026-09-14", "2026-09-21"), Recurrence.offScheduleDays(seriesNote(
            """{"freq":"weekly","weekdays":[2]}""", overrides = listOf("2026-09-21", "not a date", "2026-09-14"))))
    }

    @Test
    fun `a 31st clamped to the 30th is on the schedule, skipped it is off`() {
        assertEquals(emptyList<String>(), Recurrence.offScheduleDays(seriesNote(
            """{"freq":"monthly","month_days":[31]}""", date = "2026-08-31", overrides = listOf("2026-09-30"))))
        assertEquals(listOf("2026-09-30"), Recurrence.offScheduleDays(seriesNote(
            """{"freq":"monthly","month_days":[31],"month_day_policy":"skip"}""", date = "2026-08-31",
            overrides = listOf("2026-09-30"))))
    }

    @Test
    fun `the History marks a day that fell off, endless series and bounded alike`() {
        val endless = Recurrence.history(listOf(seriesNote("""{"freq":"weekly","weekdays":[1]}""",
            overrides = listOf("2026-09-14", "2026-09-15"))), today = today)[0] as Recurrence.HistoryEntry.Series
        assertEquals(listOf("2026-09-15" to false, "2026-09-14" to true), endless.days.map { it.date to it.offSchedule })
        assertEquals(1, endless.off)
        assertEquals(1, endless.edited)
        val bounded = Recurrence.history(listOf(seriesNote("""{"freq":"weekly","weekdays":[0],"count":2}""",
            overrides = listOf("2026-09-14", "2026-09-28"))), today = today)[0] as Recurrence.HistoryEntry.Series
        assertEquals(listOf("2026-09-28" to true, "2026-09-14" to false, "2026-09-07" to false),
            bounded.days.map { it.date to it.offSchedule })
        assertEquals(1, bounded.off)
        assertEquals(1, bounded.edited)
        assertEquals(2, bounded.occurrences)
    }

    // ---- a day that says what the series says ----

    private val standUp = """{"title":"Stand-up","body":"b\n","comments":"c","category":"Work","color":"#ff0000"}"""

    @Test
    fun `a day matches the series only when all five fields do`() {
        val s = JSONObject(standUp)
        assertTrue(Recurrence.dayMatchesSeries(s, JSONObject(standUp)))
        for ((field, other) in listOf("title" to "Stand-up!", "body" to "b2\n", "comments" to "",
                                      "category" to "Personal", "color" to "#00ff00")) {
            assertFalse("...and one differing in its $field does not",
                Recurrence.dayMatchesSeries(s, JSONObject(standUp).put(field, other)))
        }
        assertTrue(Recurrence.dayMatchesSeries(s, JSONObject(standUp).put("title", "  Stand-up ")))
        assertFalse(Recurrence.dayMatchesSeries(s, JSONObject(standUp).put("color", "")))
        val noColour = JSONObject(standUp).apply { remove("color") }
        assertTrue(Recurrence.dayMatchesSeries(JSONObject(standUp).put("color", ""), noColour))
        assertFalse(Recurrence.dayMatchesSeries(s, noColour))
        assertFalse(Recurrence.dayMatchesSeries(s, JSONObject(standUp).put("title", 5)))
        assertFalse(Recurrence.dayMatchesSeries(JSONObject(standUp).put("comments", JSONArray().put("c")), JSONObject(standUp)))
    }

    // ---- a day held on another date ----

    private fun weekly(extra: String = ""): JSONObject = note(
        """{"id":1,"title":"Sync","body":"b","date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[0]}$extra}""")

    @Test
    fun `a moved day is drawn on its new date, and still counts as one of N`() {
        val oct = arrayOf(d(2026, 10, 1), d(2026, 10, 31))
        val moved = weekly(""","moved":{"2026-10-12":"2026-10-13"}""")
        assertEquals(listOf("2026-10-05", "2026-10-13", "2026-10-19", "2026-10-26"), expand(moved, oct[0], oct[1]))
        assertEquals(listOf("2026-10-05", "2026-10-13", "2026-10-19"), expand(note(
            """{"id":1,"date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[0],"count":3},"moved":{"2026-10-12":"2026-10-13"}}"""),
            oct[0], oct[1]))
        assertEquals(listOf("2026-10-13"), expand(moved, d(2026, 10, 13), d(2026, 10, 13)))
        assertEquals(emptyList<String>(), expand(moved, d(2026, 10, 12), d(2026, 10, 12)))
    }

    @Test
    fun `a move outlives neither a deleted day nor a rule that no longer gives it`() {
        val oct = arrayOf(d(2026, 10, 1), d(2026, 10, 31))
        assertEquals(listOf("2026-10-05", "2026-10-19", "2026-10-26"),
            expand(weekly(""","moved":{"2026-10-12":"2026-10-13"},"exceptions":["2026-10-12"]"""), oct[0], oct[1]))
        assertFalse("2026-10-13" in expand(note(
            """{"id":1,"date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[2]},"moved":{"2026-10-12":"2026-10-13"}}"""),
            oct[0], oct[1]))
    }

    @Test
    fun `a day may move past the end or before the start, and the first N include it`() {
        assertTrue("2026-11-02" in expand(note(
            """{"id":1,"date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[0],"end":"2026-10-20"},"moved":{"2026-10-12":"2026-11-02"}}"""),
            d(2026, 10, 1), d(2026, 11, 30)))
        assertEquals(listOf("2026-10-01"),
            expand(weekly(""","moved":{"2026-10-05":"2026-10-01"}"""), d(2026, 10, 1), d(2026, 10, 6)))
        assertEquals(listOf("2026-10-05", "2026-10-06"),
            expand(weekly(""","moved":{"2026-10-19":"2026-10-06"}"""), d(2026, 10, 1), d(2026, 10, 31), limit = 2))
        assertEquals("2026-10-10", Recurrence.nextOnOrAfter(weekly(""","moved":{"2026-10-12":"2026-10-10"}"""), "2026-10-06"))
    }

    @Test
    fun `moving a day records its origin, carries its edit, and coming home undoes it`() {
        val n = weekly(""","overrides":{"2026-10-12":{"title":"Sync (room 2)"}}""")
        assertEquals("2026-10-12", Recurrence.moveDay(n, "2026-10-12", "2026-10-13"))
        assertEquals("2026-10-13", n.getJSONObject("moved").getString("2026-10-12"))
        assertEquals(listOf("2026-10-13"), n.getJSONObject("overrides").keys().asSequence().toList())
        assertEquals("2026-10-12", Recurrence.movedFrom(n, "2026-10-13"))
        assertNull(Recurrence.movedFrom(n, "2026-10-12"))
        Recurrence.moveDay(n, "2026-10-13", "2026-10-15")
        assertEquals("2026-10-15", n.getJSONObject("moved").getString("2026-10-12"))
        assertEquals(1, n.getJSONObject("moved").length())
        assertEquals(listOf("2026-10-15"), n.getJSONObject("overrides").keys().asSequence().toList())
        Recurrence.moveDay(n, "2026-10-15", "2026-10-12")
        assertEquals(0, n.getJSONObject("moved").length())
        assertEquals(listOf("2026-10-12"), n.getJSONObject("overrides").keys().asSequence().toList())
        assertNull("a note that does not repeat has no moved days", Recurrence.movedFrom(
            note("""{"date":"2026-10-05","moved":{"2026-10-05":"2026-10-06"}}"""), "2026-10-06"))
    }

    @Test
    fun `a moved day's own edit is on the schedule where it is now, until the rule drops its origin`() {
        assertEquals(emptyList<String>(), Recurrence.offScheduleDays(
            weekly(""","moved":{"2026-10-12":"2026-10-13"},"overrides":{"2026-10-13":$own}""")))
        assertEquals(listOf("2026-10-13"), Recurrence.offScheduleDays(note(
            """{"id":1,"date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[2]},"moved":{"2026-10-12":"2026-10-13"},"overrides":{"2026-10-13":$own}}""")))
    }

    @Test
    fun `the History lists a moved day, endless series and bounded`() {
        val endless = Recurrence.history(listOf(weekly(""","moved":{"2026-10-12":"2026-10-13"}""")), today = today)[0]
            as Recurrence.HistoryEntry.Series
        assertEquals(listOf("2026-10-13" to "2026-10-12"), endless.days.map { it.date to it.movedFrom })
        assertEquals(1, endless.moved)
        val bounded = Recurrence.history(listOf(note(
            """{"id":1,"title":"Sync","date":"2026-10-05","recurrence":{"freq":"weekly","weekdays":[0],"count":3},"moved":{"2026-10-12":"2026-10-13"}}""")),
            today = today)[0] as Recurrence.HistoryEntry.Series
        assertEquals(listOf("2026-10-19", "2026-10-13", "2026-10-05"), bounded.days.map { it.date })
        assertEquals(listOf(null, "2026-10-12", null), bounded.days.map { it.movedFrom })
    }

    @Test
    fun `a load keeps real moves only, and two series differing by a move differ`() {
        val n = Normalize.normalizeNote(weekly(""","exceptions":["2026-10-26"],"moved":{"2026-10-12":"2026-10-13",
            "2026-10-19":"2026-10-13","2026-10-26":"2026-10-27","2026-11-02":"2026-11-02","nope":"2026-11-10","2026-11-09":5}"""))
        val moved = n.getJSONObject("moved")
        assertEquals(1, moved.length())
        assertEquals("2026-10-13", moved.getString("2026-10-12"))
        assertEquals(0, Normalize.normalizeNote(weekly()).getJSONObject("moved").length())
        assertNotEquals(Normalize.contentSignature(Normalize.normalizeNote(weekly())),
            Normalize.contentSignature(Normalize.normalizeNote(weekly(""","moved":{"2026-10-12":"2026-10-13"}"""))))
    }

    // ---- is it a repeating note at all ----

    @Test
    fun `isRecurring answers for any stored value instead of throwing`() {
        assertTrue(Recurrence.isRecurring(note("""{"recurrence":{"freq":"daily"}}""")))
        assertFalse(Recurrence.isRecurring(JSONObject()))
        assertFalse(Recurrence.isRecurring(note("""{"recurrence":{}}""")))
        assertFalse(Recurrence.isRecurring(note("""{"recurrence":{"count":3}}""")))
        for (junk in listOf<Any>("daily", 0, 1, JSONArray(), JSONArray().put("freq"), 3.5, true)) {
            assertFalse("a $junk recurrence answers no", Recurrence.isRecurring(JSONObject().put("recurrence", junk)))
        }
    }

    // ---- dates the way people write them ----

    private val rdToday = d(2026, 10, 3)
    private val oct17 = d(2026, 10, 17)
    private fun rd(text: Any?, settled: Boolean = false) = Recurrence.readDate(text, settled, rdToday)

    @Test
    fun `the date is read from every form a person writes`() {
        for (form in listOf("17.10.2026", "17/10/2026", "17-10-2026", "17 10 2026", "17. 10. 2026", " 17.10.2026 ",
                            "2026-10-17", "2026.10.17", "2026/10/17", "17 oct 2026", "17 Oct. 2026", "17-oct-2026",
                            "17 octombrie 2026", "17 OCTOMBRIE 2026", "October 17, 2026", "oct 17 2026", "17 octo 2026",
                            "10/17/2026", "10-17-2026", "10.17.2026", "Oct. 17, 2026", "October 17th, 2026",
                            "Oct 17th 2026", "17th October 2026", "17th of October 2026", "the 17th of October 2026",
                            "Saturday, October 17, 2026", "Sat, Oct 17, 2026", "Saturday 17 October 2026",
                            "Sat 17.10.2026", "SATURDAY, OCTOBER 17TH, 2026", "Monday, October 17, 2026")) {
            assertEquals("the date is read from '$form'", oct17, rd(form))
        }
    }

    @Test
    fun `day first, the month first only where it must be`() {
        assertEquals(d(2026, 2, 1), rd("1.2.2026"))
        assertEquals(d(2026, 2, 1), rd("01.02.2026"))
        assertEquals(d(2026, 12, 10), rd("10/12/2026"))
        assertEquals(d(2026, 12, 13), rd("12/13/2026"))
        assertEquals(d(2026, 12, 13), rd("13/12/2026"))
        assertEquals(d(2026, 2, 1), rd("1/2/2026"))
    }

    @Test
    fun `a short or missing year waits until the field is left`() {
        for (form in listOf("10/17/26", "10/17", "Oct 17th", "Saturday, October 17", "17th October",
                            "17.10", "17 oct", "17.10.26", "17 oct 26", "oct 17")) {
            assertNull("'$form' waits", rd(form))
            assertEquals("'$form' is read once the field is left", oct17, rd(form, settled = true))
        }
        assertNull(rd("17.10.20"))
        assertNull(rd("17.10.202"))
        assertEquals(d(2020, 10, 17), rd("17.10.20", settled = true))
    }

    @Test
    fun `month words in both languages`() {
        for ((form, want) in listOf("17 mai 2026" to 5, "17 may 2026" to 5, "3 martie 2026" to 3, "3 mar 2026" to 3,
                                    "5 iun 2026" to 6, "5 iul 2026" to 7, "5 noi 2026" to 11, "5 nov 2026" to 11,
                                    "5 sept 2026" to 9, "5 ianuarie 2026" to 1, "5 decembrie 2026" to 12)) {
            assertEquals("the month in '$form'", want, rd(form)?.monthValue)
        }
        assertEquals(d(2028, 2, 29), rd("29.02.2028"))
        assertNull(rd("29.02.2027"))
    }

    @Test
    fun `what is not a date is not read as one`() {
        for (form in listOf<Any?>("31.09.2026", "00.10.2026", "17.13.2026", "17.10.20262", "2026-10", "abc", "17 ma 2026",
                                  "17 xyz 2026", "117.10.2026", "17.100.2026", "oct 2026", "17 oct 2026 18", "17.10.0999",
                                  "DD.MM.YYYY", "17.10.2026!", "", "   ", "١٧.10.2026", "17·10·2026",
                                  null, 17, "13/13/2026", "02/30/2026", "Saturday", "Sat Sun 17 oct 2026",
                                  "17 th oct 2026", "2026th oct 17", "17thx oct 2026", "October November 17 2026",
                                  "the of", "Moonday 17 oct 2026", "17" + " ".repeat(40) + "10 2026")) {
            assertNull("$form is not a date", rd(form))
            assertNull("$form is not a date, settled", rd(form, settled = true))
        }
    }

    @Test
    fun `a word starting two months' names is no month, and no day name shares a month's start`() {
        val withMarzipan = Recurrence.MONTH_NAMES + ("marzipan" to 7)
        assertNull(Recurrence.readDate("17 mar 2026", false, rdToday, withMarzipan))
        assertEquals(d(2026, 3, 17), rd("17 mar 2026"))
        assertTrue(Recurrence.WEEKDAY_WORDS.none { day -> Recurrence.MONTH_NAMES.keys.any { it.take(3) == day.take(3) } })
    }

    @Test
    fun `a hostile run of figures is refused at once`() {
        val start = System.nanoTime()
        assertNull(rd("1".repeat(26) + "!"))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
    }

    // ---- dates as the app shows them ----

    @Test
    fun `a stored date, a moment and a day, as the app shows them`() {
        assertEquals("17.10.2026", Recurrence.showDate("2026-10-17"))
        assertEquals("05.01.2026", Recurrence.showDate("2026-1-5"))
        assertEquals("17.10", Recurrence.showDate("17.10"))
        assertEquals("", Recurrence.showDate(""))
        assertEquals("", Recurrence.showDate(null))
        assertEquals("01.09.2026 09:00", Recurrence.showStamp("2026-09-01 09:00"))
        assertEquals("odd", Recurrence.showStamp("odd"))
        assertEquals("Saturday, 17 October 2026", Recurrence.showDay(oct17))
        assertEquals("Saturday, 17 October", Recurrence.showDay(oct17, year = false))
        assertEquals("Saturday, 17 Oct", Recurrence.showDay(oct17, year = false, shortMonth = true))
    }

    @Test
    fun `a date and the field showing it are the same date, a half-typed one is not`() {
        assertTrue(Recurrence.sameDate("17.10.2026", "2026-10-17"))
        assertTrue(Recurrence.sameDate(" 30.09.2026 ", "30.09.2026"))
        assertTrue(Recurrence.sameDate("", ""))
        assertFalse(Recurrence.sameDate("17.10", "2026-10-17"))
        assertFalse(Recurrence.sameDate("x", "y"))
    }

    @Test
    fun `an end or a note's date written day first is stored in the one form`() {
        assertEquals("2026-12-31", Recurrence.normalize(JSONObject().put("freq", "daily").put("end", "31.12.2026")).getString("end"))
        assertEquals("", Recurrence.normalize(JSONObject().put("freq", "daily").put("end", "31.12")).getString("end"))
        assertEquals("2026-09-30", Normalize.normalizeNote(note("""{"title":"t","date":"30.09.2026"}""")).getString("date"))
        assertEquals("2026-10-17", Normalize.normalizeNote(note("""{"title":"t","date":"17 oct 2026"}""")).getString("date"))
        assertEquals("31.09.2026", Normalize.normalizeNote(note("""{"title":"t","date":"31.09.2026"}""")).getString("date"))
        assertEquals("17.10", Normalize.normalizeNote(note("""{"title":"t","date":"17.10"}""")).getString("date"))
        assertEquals("weekly", Normalize.normalizeNote(note("""{"title":"t","date":"07.09.2026","recurrence":{"freq":"weekly"}}"""))
            .getJSONObject("recurrence").getString("freq"))
    }

    @Test
    fun `the until of a repeat is shown day first`() {
        assertEquals("Every 2 weeks, until 31.12.2026",
            Recurrence.describe(note("""{"recurrence":{"freq":"weekly","interval":2,"end":"2026-12-31"}}""")))
    }

    // ---- against the desktop's own engine, on cases nobody chose ----

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("recurrence_cases.json")
        org.junit.Assert.assertNotNull(
            "recurrence_cases.json is missing - rebuild it with tools/make_recurrence_fixture.py", stream)
        return JSONObject(stream!!.bufferedReader(Charsets.UTF_8).readText())
    }

    @Test
    fun `three hundred notes expand to exactly the days the desktop gives`() {
        val cases = fixture().getJSONArray("expand")
        assertTrue("the fixture holds its cases", cases.length() >= 300)
        val wrong = ArrayList<String>()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val n = c.getJSONObject("note")
            val limit = if (c.isNull("limit")) null else c.getInt("limit")
            val want = c.getJSONArray("days").let { a -> (0 until a.length()).map { a.getString(it) } }
            val got = expand(n, LocalDate.parse(c.getString("start")), LocalDate.parse(c.getString("end")), limit)
            if (got != want) wrong.add("case $i: $n ${c.getString("start")}..${c.getString("end")} limit=$limit\n  desktop $want\n  phone   $got")
            if (Recurrence.describe(n) != c.getString("said")) wrong.add("case $i said: ${Recurrence.describe(n)} vs ${c.getString("said")}")
            val off = c.getJSONArray("off").let { a -> (0 until a.length()).map { a.getString(it) } }
            if (Recurrence.offScheduleDays(n) != off) wrong.add("case $i off: ${Recurrence.offScheduleDays(n)} vs $off")
        }
        assertTrue(wrong.take(5).joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `four hundred typed dates are read exactly as the desktop reads them`() {
        val typed = fixture().getJSONArray("read")
        val wrong = ArrayList<String>()
        for (i in 0 until typed.length()) {
            val t = typed.getJSONObject(i)
            val want = if (t.isNull("date")) null else t.getString("date")
            val got = Recurrence.readDate(t.getString("text"), t.getBoolean("settled"), d(2026, 10, 3))?.let { Recurrence.formatDate(it) }
            if (got != want) wrong.add("'${t.getString("text")}' settled=${t.getBoolean("settled")}: desktop $want, phone $got")
        }
        assertTrue(wrong.take(10).joinToString("\n"), wrong.isEmpty())
    }

    // ---- beyond the desktop's suite: two survivors of this port's mutants ----

    @Test
    fun `a rule in words, every one of its shapes`() {
        // Not in test_recurrence.py; written when "Every 1 weeks" survived.
        fun said(rule: String) = Recurrence.describe(note("""{"recurrence":$rule}"""))
        assertEquals("Every week on Mon, Thu", said("""{"freq":"weekly","weekdays":[3,0]}"""))
        assertEquals("Every day", said("""{"freq":"daily"}"""))
        assertEquals("Every 3 days, 10 times", said("""{"freq":"daily","interval":3,"count":10}"""))
        assertEquals("Every month on day 1, 15", said("""{"freq":"monthly","month_days":[15,1]}"""))
        assertEquals("Every year", said("""{"freq":"yearly"}"""))
        assertEquals("", said("""{"freq":""}"""))
    }

    @Test
    fun `a counted series looked at years later has run out, at every frequency`() {
        // Written when a mutant that let a counted yearly series jump ahead
        // to the window survived: the jump is only for series with no count,
        // which have nothing to miscount. Answers taken from the desktop.
        for (freq in listOf("daily", "weekly", "monthly", "yearly")) {
            val n = note("""{"id":1,"date":"2024-03-15","recurrence":{"freq":"$freq","count":3}}""")
            assertEquals("$freq, years later", emptyList<String>(), expand(n, d(2030, 1, 1), d(2030, 12, 31)))
            assertEquals("$freq, all of it", 3, expand(n, d(2024, 1, 1), d(2030, 12, 31)).size)
        }
    }

    @Test
    fun `a week's days before the first one never count towards N times`() {
        // Anchored on a Wednesday, repeating Mondays and Wednesdays, three
        // times: the Monday of that first week is before the series began,
        // so the three are Wed, Mon, Wed -- not a phantom Monday and two.
        val n = note("""{"id":1,"date":"2026-07-01","recurrence":{"freq":"weekly","weekdays":[0,2],"count":3}}""")
        assertEquals(listOf("2026-07-01", "2026-07-06", "2026-07-08"), expand(n, d(2026, 6, 1), d(2026, 8, 31)))
    }

    @Test
    fun `the days a note keeps after its repeat is turned off steer nothing while it does not repeat`() {
        // A share code or "Restore previous" that ends a repeat leaves the
        // series' days in the note, for a repeat brought back. Read anyway,
        // the calendar's row of its date showed the first day's old words,
        // and a series whose first day was deleted fell off the calendar and
        // out of the day's reminder. The desktop's test_recurrence, the same.
        val kept = note("""{"id":1,"title":"Weekly sync","body":"SERIES TEXT","date":"2026-09-07",
            "recurrence":{"freq":""},"exceptions":["2026-09-07"],
            "overrides":{"2026-09-07":{"title":"Kickoff","body":"FIRST DAY"}}}""")
        assertTrue("itself on its day, kept days or not", Recurrence.effectiveNote(kept, "2026-09-07") === kept)
        assertEquals("on the calendar on its date though the series deleted that day",
            listOf("2026-09-07"), expand(kept, d(2026, 9, 1), d(2026, 9, 30)))
        assertEquals(listOf(kept), Days.notesOn(listOf(kept), "2026-09-07"))
        assertEquals("and in the day's reminder", 1, Reminders.dueOn(listOf(kept), "2026-09-07"))
        val series = JSONObject(kept.toString()).put("recurrence", JSONObject().put("freq", "weekly"))
        assertEquals("a series still shows its day's own words", "FIRST DAY",
            Recurrence.effectiveNote(series, "2026-09-07").getString("body"))
        assertEquals("and its deleted day stays deleted", listOf("2026-09-14", "2026-09-21", "2026-09-28"),
            expand(series, d(2026, 9, 1), d(2026, 9, 30)))
        assertEquals("what a kept day holds is still read, for the questions that keep it", "FIRST DAY",
            Recurrence.dayAsKept(kept, "2026-09-07").getString("body"))
    }

    @Test
    fun `a hand-made rule is read as the desktop's int() and isinstance read it`() {
        // A code with interval "1_0" and weekdays [true, 3] repeated every
        // ten weeks on Tuesday and Thursday on the desktop, and every
        // Thursday on the phone (the final audit's eighth round). The
        // desktop's answers, from its own normalize_recurrence at 947e9d3
        // (scratch android_fixes_r8/probe_parity.py).
        fun rule(vararg pairs: Pair<String, Any>): JSONObject {
            val o = JSONObject().put("freq", "weekly").put("interval", 2).put("weekdays", JSONArray(listOf(1, 3)))
                .put("month_days", JSONArray()).put("month_day_policy", "clamp").put("end", "").put("count", 0)
            for ((k, v) in pairs) o.put(k, v)
            return o
        }
        fun interval(text: String) = Recurrence.normalize(rule("interval" to text)).getInt("interval")
        assertEquals(10, interval("1_0"))
        assertEquals(1, interval("1__0"))
        assertEquals(1, interval("_1"))
        assertEquals(1, interval("1_"))
        assertEquals(3, interval("+3"))
        assertEquals(1, interval("-3"))
        assertEquals(7, interval(" 0_7 "))
        assertEquals(12, interval("１_２"))
        assertEquals(Recurrence.MAX_INTERVAL, interval("9".repeat(99)))
        assertEquals(Recurrence.MAX_INTERVAL, interval("1".repeat(4_300)))
        assertEquals("past Python's 4,300 digits, refused", 1, interval("1".repeat(4_301)))
        assertEquals(1, interval("-"))
        assertEquals(1, interval("2.9"))
        assertEquals(1_000, Recurrence.normalize(rule("count" to "1_000")).getInt("count"))
        assertEquals(Recurrence.MAX_OCCURRENCES, Recurrence.normalize(rule("count" to "9_999")).getInt("count"))
        fun days(key: String, vararg values: Any) =
            Recurrence.normalize(rule(key to JSONArray(values.toList()))).getJSONArray(key).let { a -> (0 until a.length()).map { a.get(it) } }
        assertEquals("true is 1, as Python's True", listOf(1, 3), days("weekdays", true, 3))
        assertEquals("false is 0, and true and 1 the one day", listOf(0, 1), days("weekdays", false, true, 1))
        assertEquals(listOf(1), days("month_days", true))
        assertEquals("false, 0, is no day of a month", listOf(2), days("month_days", false, 2))
        // "The same series", asked of a copy and its note, as the desktop answers it.
        assertEquals(Recurrence.normalize(rule("weekdays" to JSONArray(listOf(true, 3)))).toString(),
                     Recurrence.normalize(rule("weekdays" to JSONArray(listOf(1, 3)))).toString())
    }
}
