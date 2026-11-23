package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list's sorts, its "Show", and its groups: the desktop's note_list rules. */
class ListingTest {

    private fun notes(vararg json: String): List<Note> = notesFrom("[" + json.joinToString(",") + "]")
    private fun ids(list: List<Note>) = list.map { it.id }

    @Test
    fun `Calendar soonest puts what is coming first, then the past newest first, then the undated`() {
        val list = notes(
            """{"id": 1, "date": "2026-10-01"}""",           // past
            """{"id": 2, "date": "2026-10-20"}""",           // coming
            """{"id": 3}""",                                  // undated
            """{"id": 4, "date": "2026-10-16"}""",           // coming, sooner
            """{"id": 5, "date": "2026-09-01"}""",           // past, older
            """{"id": 6, "date": "2026-10-05", "recurrence": {"freq": "weekly"}}""",  // next: 10-19
        )
        val next = mapOf(6 to "2026-10-19")
        assertEquals(listOf(4, 6, 2, 1, 5, 3), ids(sortNotes(list, SortOrder.SOONEST, "2026-10-15") { next[it.id] }))
    }

    @Test
    fun `a series that has ended stands with the past by its start date`() {
        val list = notes(
            """{"id": 1, "date": "2026-10-10"}""",
            """{"id": 2, "date": "2026-01-01", "recurrence": {"freq": "daily", "count": 3}}""",
        )
        assertEquals(listOf(1, 2), ids(sortNotes(list, SortOrder.SOONEST, "2026-10-15") { null }))
    }

    @Test
    fun `Calendar soonest asks each series its next day once, not at every comparison`() {
        // Asked inside the comparison: 7,584 times for 2,000 notes with a
        // fifth repeating, each answer read over the whole notebook.
        val list = notes(*(1..2000).map { i ->
            if (i % 5 == 0) """{"id": $i, "date": "2026-01-01", "recurrence": {"freq": "weekly"}}"""
            else """{"id": $i, "date": "2026-${"%02d".format(1 + i % 12)}-${"%02d".format(1 + i % 28)}"}"""
        }.toTypedArray())
        var asked = 0
        val sorted = sortNotes(list, SortOrder.SOONEST, "2026-10-15") { asked += 1; "2026-10-${10 + it.id % 20}" }
        assertEquals(400, asked)
        assertEquals(2000, sorted.size)
        assertEquals("a series' next day first", "2026-10-10", sorted.first().let { if (it.isRecurring) "2026-10-${10 + it.id % 20}" else it.date })
    }

    @Test
    fun `Calendar oldest is plain chronology, the undated last`() {
        val list = notes("""{"id": 1}""", """{"id": 2, "date": "2026-10-20"}""", """{"id": 3, "date": "2025-01-01"}""")
        assertEquals(listOf(3, 2, 1), ids(sortNotes(list, SortOrder.OLDEST_DATE)))
    }

    @Test
    fun `Custom order is where the desktop put each note, the unplaced above, newest first`() {
        val list = notes(
            """{"id": 1, "order": 2, "created": "2026-01-01 00:00:00"}""",
            """{"id": 2, "order": 1, "created": "2026-01-02 00:00:00"}""",
            """{"id": 3, "created": "2026-02-01 00:00:00"}""",
            """{"id": 4, "created": "2026-03-01 00:00:00"}""",
        )
        assertEquals(listOf(4, 3, 2, 1), ids(sortNotes(list, SortOrder.CUSTOM)))
    }

    @Test
    fun `every sort label is the desktop's, so the stored choice reads back on both`() {
        assertEquals(listOf("Recently updated", "Oldest updated", "Title A–Z", "Title Z–A", "Category",
                            "Calendar: soonest", "Calendar: oldest", "Custom order"),
                     SortOrder.entries.map { it.label })
        assertEquals(SortOrder.SOONEST, SortOrder.fromLabel("Calendar: soonest"))
    }

    @Test
    fun `Show passes repeating, dated and files by the desktop's one judgement`() {
        val list = notes(
            """{"id": 1, "date": "2026-10-01", "recurrence": {"freq": "weekly"}}""",
            """{"id": 2, "date": "2026-10-01"}""",
            """{"id": 3, "attachments": [{"filename": "a.png", "stored_as": "a.png"}]}""",
            """{"id": 4, "category": "Work"}""",
        )
        assertEquals(listOf(1), ids(list.filter { Showing.passes(it, Showing.REPEATING) }))
        assertEquals("a series is not a dated note", listOf(2), ids(list.filter { Showing.passes(it, Showing.DATED) }))
        assertEquals(listOf(3), ids(list.filter { Showing.passes(it, Showing.FILES) }))
        assertEquals(listOf(4), ids(list.filter { Showing.passes(it, "Work") }))
        assertEquals(4, list.count { Showing.passes(it, Showing.ALL) })
    }

    @Test
    fun `notes hidden in different ways are said of them all, every group they are in to unfold`() {
        // Of the first alone, "its folded group" was said of two, and Show all
        // unfolded that group only (the desktop's _what_hides_notes, the same).
        val list = notes("""{"id": 1, "title": "Into Work", "category": "Work"}""",
                         """{"id": 2, "title": "Into Personal", "category": "Personal"}""")
        val folded = setOf("Work", "Personal")
        assertEquals("their folded groups", Showing.hiddenBy(list, "", Showing.ALL, true, folded))
        assertEquals("its folded group", Showing.hiddenBy(list.take(1), "", Showing.ALL, true, folded))
        assertEquals("Show: Work and their folded group", Showing.hiddenBy(list, "", "Work", true, setOf("Work")))
        assertEquals("", Showing.hiddenBy(list, "", Showing.ALL, false, folded))
        assertEquals(setOf("Work", "Personal"), Showing.groupsOf(list))
    }

    @Test
    fun `a stored Show that names a category since deleted falls back to All`() {
        assertEquals(Showing.ALL, Showing.valid("Gone", listOf("General", "Work")))
        assertEquals("Work", Showing.valid("Work", listOf("General", "Work")))
        assertEquals(Showing.DATED, Showing.valid(Showing.DATED, listOf("General")))
        assertEquals(Showing.ALL, Showing.valid(null, listOf("General")))
    }

    @Test
    fun `grouped, each category heads its notes in Settings order, a stray one kept, a folded one shut`() {
        val list = notes(
            """{"id": 1, "category": "Work"}""",
            """{"id": 2, "category": "Gone"}""",
            """{"id": 3, "category": "General"}""",
            """{"id": 4, "category": "Work"}""",
        )
        val rows = Showing.rows(list, grouped = true, collapsed = setOf("General"), categoryOrder = listOf("General", "Work"))
        val shape = rows.map {
            when (it) {
                is Showing.Row.Header -> "[${it.category} ${it.count}${if (it.collapsed) " shut" else ""}]"
                is Showing.Row.Item -> it.note.id.toString()
            }
        }
        assertEquals(listOf("[General 1 shut]", "[Work 2]", "1", "4", "[Gone 1]", "2"), shape)
        assertEquals(4, Showing.rows(list, grouped = false, collapsed = emptySet(), categoryOrder = emptyList()).size)
        // Select all takes what shows: never the folded group's note, which
        // the Trash button would then have sent away unseen.
        assertEquals(setOf(1, 4, 2), Showing.visibleIds(rows))
    }

    @Test
    fun `only the cards a change moved are brought into sight`() {
        fun flat(vararg ids: Int) = Showing.rows(ids.map { notes("""{"id": $it}""").single() }, false, emptySet(), emptyList())
        // Filed under a sort a move does not reorder: nothing moved, nothing to bring.
        assertEquals(emptySet<Int>(), Showing.movedIds(flat(1, 2, 3, 4), flat(1, 2, 3, 4), setOf(1, 4)))
        // A pin of 3 with 1 already pinned: 3 moved, 1 kept its place.
        assertEquals(setOf(3), Showing.movedIds(flat(1, 2, 3, 4), flat(1, 3, 2, 4), setOf(1, 3)))
        // A note arriving -- a copy, a note put back -- moved.
        assertEquals(setOf(9), Showing.movedIds(flat(1, 2), flat(1, 9, 2), setOf(9)))
    }

    @Test
    fun `a moved card is brought into sight when it was the first item of the list or is out of the window the list kept`() {
        // Pinned, put back by Undo, pinned under a sliver, grouped.
        fun flat(vararg ids: Int) = Showing.rows(ids.map { notes("""{"id": $it}""").single() }, false, emptySet(), emptyList())
        // In the window it was asked over: no scroll.
        assertEquals(null, Showing.revealAt(flat(1, 2, 3, 4, 5), setOf(3), listOf(2, 3, 4), atTop = false, anchor = 2, lead = 0))
        // Pinned from under a sliver (2, the item the list keeps): the window
        // counted from it, and the pinned card (3) is now above it.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6), setOf(3), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
        // Two pinned together from under a sliver: both above it now.
        assertEquals(0, Showing.revealAt(flat(3, 4, 1, 2, 5, 6), setOf(3, 4), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
        // Several moved: the first of them in the list's NEW order, which the
        // move reordered.
        assertEquals(1, Showing.revealAt(flat(1, 7, 6, 2, 3, 4, 5, 8, 9), setOf(6, 7), listOf(8, 9), atTop = false, anchor = 8, lead = 0))
        // Several put back by Undo above a list at its top: the top, so all show.
        assertEquals(0, Showing.revealAt(flat(8, 9, 1, 2, 3), setOf(8, 9), listOf(1, 2, 3), atTop = true, anchor = 1, lead = 0))
        // Below the window, scrolled down: the card itself.
        assertEquals(4, Showing.revealAt(flat(1, 2, 3, 4, 7), setOf(7), listOf(1, 2), atTop = true, anchor = 1, lead = 0))
        // Not listed, hidden by Show or a search: nothing.
        assertEquals(null, Showing.revealAt(flat(1, 2), setOf(7), listOf(1, 2), atTop = true, anchor = 1, lead = 0))
        // Grouped: the header comes with the card at the top of its group.
        val grouped = Showing.rows(notes("""{"id": 1, "category": "General"}""", """{"id": 2, "category": "General"}""",
                                         """{"id": 6, "category": "Work"}""", """{"id": 5, "category": "Work"}"""),
                                   true, emptySet(), listOf("General", "Work"))
        assertEquals(3, Showing.revealAt(grouped, setOf(6), listOf("h:General", 1), atTop = false, anchor = "h:General", lead = 0))
        // The list's own first card among those moved, and moved
        // down by them. The list followed it, so the card now first went off
        // the top: filed together from the top...
        assertEquals(0, Showing.revealAt(flat(4, 5, 1, 2, 3, 9), setOf(4, 5), listOf(5, 4, 1, 2), atTop = true, anchor = 5, lead = 0))
        // ...pinned together, one already pinned...
        assertEquals(0, Showing.revealAt(flat(3, 6, 1, 2, 4), setOf(6, 3), listOf(6, 3, 1, 2), atTop = true, anchor = 6, lead = 0))
        // ...a sliver pinned, the list following it to the top.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6, 7), setOf(3), listOf(4, 5, 6), atTop = false, anchor = 3, lead = 1))
        // Moved far down: there, not the top, which shows none of them.
        assertEquals(8, Showing.revealAt(flat(1, 2, 3, 4, 5, 6, 7, 8, 10, 11), setOf(10, 11), listOf(11, 1, 2), atTop = true, anchor = 11, lead = 0))
        // Past about a hundred rows the list does NOT follow its first
        // card, and keeps its place by index -- so a moved first card is always
        // brought into sight: pinned from 150 rows down...
        assertEquals(0, Showing.revealAt(flat(*(listOf(151) + (1..150) + (152..200)).toIntArray()), setOf(151),
                                         listOf(151, 152, 153, 154), atTop = false, anchor = 151, lead = 0))
        // ...and filed from the top of "Oldest updated", its new stamp sending it last.
        assertEquals(139, Showing.revealAt(flat(*((2..140) + 1).toIntArray()), setOf(1),
                                           listOf(1, 2, 3, 4), atTop = true, anchor = 1, lead = 0))
        // Where it goes: a list at its top goes back to it when the
        // card shows from there -- the top card filed five rows down...
        assertEquals(0, Showing.revealAt(flat(1, 2, 3, 4, 5, 9), setOf(9), listOf(9, 1, 2, 3, 4, 5),
                                         atTop = true, anchor = 9, lead = 0))
        // ...and in a group, the header comes with the card now first in it.
        val pinnedFirst = Showing.rows(notes("""{"id": 1, "category": "General"}""", """{"id": 2, "category": "General"}""",
                                             """{"id": 5, "category": "Work", "pinned": true}""", """{"id": 6, "category": "Work"}"""),
                                       true, emptySet(), listOf("General", "Work"))
        assertEquals(3, Showing.revealAt(pinnedFirst, setOf(5), listOf(5, 6), atTop = false, anchor = 5, lead = 0))
        // The item it kept gone from the list: counted from a card in sight that did not move.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6), setOf(3), listOf(3, 4, 5), atTop = false, anchor = 99, lead = 1))
        // ...the first of them that is neither of two pinned together, both above it now.
        assertEquals(0, Showing.revealAt(flat(3, 4, 1, 2, 5, 6), setOf(3, 4), listOf(3, 4, 5), atTop = false, anchor = 99, lead = 1))
        // The item it kept (2, a sliver) standing still, the window counted
        // from the row under it: a card moved one place up is in sight. Counted
        // from the card in sight, the window started a row low...
        assertEquals(null, Showing.revealAt(flat(1, 2, 4, 3, 5, 6), setOf(4), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
        // ...and one moved down to the window's last row too; counted from
        // the sliver itself, the window ended a row short.
        assertEquals(null, Showing.revealAt(flat(1, 2, 3, 5, 4, 6), setOf(4), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
    }

    @Test
    fun `a dragged card trades only while its slot stays in the viewport`() {
        // A viewport from -10 to 1000. Down: a card 200 tall past a
        // neighbour 300 tall whose top has just come in at 990 -- its slot would
        // start at 1090, out of the viewport, and the card was dropped.
        assertFalse(Showing.tradeStaysInSight(1, 790, 200, 990, 300, leads = false, start = -10, end = 1000))
        // ...and once the neighbour is well in, it trades.
        assertTrue(Showing.tradeStaysInSight(1, 500, 200, 700, 300, leads = false, start = -10, end = 1000))
        // Up past a taller neighbour mostly above the screen: its slot would end
        // at -280 + 200, above the viewport.
        assertFalse(Showing.tradeStaysInSight(-1, 20, 200, -280, 300, leads = false, start = -10, end = 1000))
        assertTrue(Showing.tradeStaysInSight(-1, 250, 200, -50, 300, leads = false, start = -10, end = 1000))
        // Leading the screen it keeps its place as it steps up, wherever the neighbour is.
        assertTrue(Showing.tradeStaysInSight(-1, -20, 200, -320, 300, leads = true, start = -10, end = 1000))
        // But once only, until it stops leading -- the next card above
        // lay in the top padding again, and trades chained ten or twenty places.
        assertFalse(Showing.tradeStaysInSight(-1, -5, 200, -305, 300, leads = true, start = -10, end = 1000,
                                              ledOnce = true))
        // Not leading, the rule is the slot's, whatever went before.
        assertTrue(Showing.tradeStaysInSight(-1, 250, 200, -50, 300, leads = false, start = -10, end = 1000,
                                             ledOnce = true))
    }

    @Test
    fun `a stepped card is scrolled wholly into sight and no further`() {
        // A viewport of 1000: in sight, nothing.
        assertEquals(0, Showing.intoSight(100, 250, 1000))
        // Above it: up by what is hidden.
        assertEquals(-180, Showing.intoSight(-180, 250, 1000))
        // Below it, or a strip at the foot: down by what is cut off...
        assertEquals(150, Showing.intoSight(900, 250, 1000))
        assertEquals(250, Showing.intoSight(1000, 250, 1000))
        // ...but never so far that a card taller than the screen loses its top.
        assertEquals(300, Showing.intoSight(300, 1500, 1000))
        // Stepped down past the foot, out of the layout -- brought in
        // beside the card it passed, now ending at 1100: up by 1100 + 250 - 1000,
        // not to the top of the screen, which hid the card it passed.
        assertEquals(350, Showing.afterStep(null, 250, 850, 250, step = 1, end = 1000))
        // ...stepped up past the top: above the card it passed, now at 40.
        assertEquals(-210, Showing.afterStep(null, 250, 40, 250, step = -1, end = 1000))
        // Both laid out and in sight: nothing; neither laid out: null, to its index.
        assertEquals(0, Showing.afterStep(400, 250, 150, 250, step = 1, end = 1000))
        assertEquals(null, Showing.afterStep(null, 250, null, 0, step = 1, end = 1000))
        // The top card on screen, mostly hidden, stepped down past a
        // shorter card -- the pair comes into sight, the card it passed whole
        // above it, not the moved card alone with the passed one above the screen.
        assertEquals(-92, Showing.afterStep(-12, 106, null, 80, step = 1, end = 500))
        // ...and the last card stepped up past a card now under the foot: the pair.
        assertEquals(180, Showing.afterStep(330, 250, null, 180, step = -1, end = 580))
        // A pair taller than the screen (a phone on its side) keeps
        // the card stepped down on top, as a step up does -- the pair's top
        // left it under the + button.
        assertEquals(106, Showing.afterStep(106, 106, 0, 106, step = 1, end = 74))
        assertEquals(70, Showing.afterStep(80, 80, 0, 80, step = 1, end = 90))
    }

    @Test
    fun `a drag scrolls at an edge only past its press, the way it is heading`() {
        // A slop of 8 and a top edge of 56 from 0 to 1000: the top
        // card nudged down and put back, a hair above the press, scrolls nothing...
        assertEquals(0, Showing.edgeScroll(-1, -3f, 8f, 10f, 106, 0, 1000, 56f))
        // ...past the press by more than the slop, it scrolls up...
        assertEquals(-1, Showing.edgeScroll(-1, -20f, 8f, 10f, 106, 0, 1000, 56f))
        // ...but not away from the edge, nor against the finger.
        assertEquals(0, Showing.edgeScroll(-1, -20f, 8f, 500f, 106, 0, 1000, 56f))
        assertEquals(0, Showing.edgeScroll(1, 20f, 8f, 10f, 106, 0, 1000, 56f))
        // The foot alike: a card nudged up and put back there scrolls nothing,
        // one held past its press goes on down.
        assertEquals(0, Showing.edgeScroll(1, 2f, 8f, 900f, 106, 0, 1000, 56f))
        assertEquals(1, Showing.edgeScroll(1, 20f, 8f, 900f, 106, 0, 1000, 56f))
    }

    @Test
    fun `an edge scrolls slowly at first, and its zone fits a short list`() {
        // The zone is 56, or an eighth of a short list -- on its side
        // (a list of 172) every resting card sat in a zone of 56.
        assertEquals(56f, Showing.edgeZone(-4, 516, 56f))
        assertEquals(21.5f, Showing.edgeZone(-4, 168, 56f))
        // No edge, no step; at first a pixel, whatever the depth...
        assertEquals(0f, Showing.edgeStep(0, 50f, 50f, 3000, 50f))
        assertEquals(1f, Showing.edgeStep(1, 50f, 50f, 0, 50f))
        assertEquals(-1f, Showing.edgeStep(-1, 5f, 50f, 0, 50f))
        // ...held two seconds and deep in the zone, the most...
        assertEquals(50f, Showing.edgeStep(1, 50f, 50f, 2000, 50f))
        assertEquals(-50f, Showing.edgeStep(-1, 80f, 50f, 5000, 50f))
        // ...and in between, rising with both.
        assertEquals(50f * (1f - 1f / 32f), Showing.edgeStep(1, 25f, 50f, 2000, 50f), 0.001f)
        assertTrue(Showing.edgeStep(1, 50f, 50f, 1500, 50f) > Showing.edgeStep(1, 50f, 50f, 1000, 50f))
        // A drag of one place takes under a second: deep in the zone all the
        // while, its passes scroll less than a card (80) -- at one speed of 14
        // a pass, 45 passes went 630, two or three places.
        val quick = (0 until 45).sumOf { Showing.edgeStep(1, 50f, 50f, it * 17L, 50f).toDouble() }
        assertTrue("$quick", quick < 80.0)
    }

    @Test
    fun `an edge's clock runs while the card stays in its zone, and a scrolling edge holds`() {
        // The clock begins with the first pass that scrolls in a zone...
        assertEquals(0L, Showing.edgeSince(0L, 0, 1, 0, 1000))
        assertEquals(1000L, Showing.edgeSince(0L, 0, 1, 1, 1000))
        assertEquals(1500L, Showing.edgeSince(0L, 1, 1, 1, 1500))
        // ...and a turn of the finger inside the zone keeps it...
        assertEquals(1000L, Showing.edgeSince(1000L, 1, 1, 0, 1800))
        assertEquals(1000L, Showing.edgeSince(1000L, 1, 1, 1, 2000))
        // ...until the card leaves the zone, or enters the other.
        assertEquals(0L, Showing.edgeSince(1000L, 1, 0, 0, 2100))
        assertEquals(2200L, Showing.edgeSince(1000L, 1, -1, -1, 2200))
        // A list scrolling the way the drag heads stays armed past the
        // press; turned, or not yet scrolling, it is not.
        assertTrue(Showing.edgeLatched(-1, -1))
        assertFalse(Showing.edgeLatched(-1, 1))
        assertFalse(Showing.edgeLatched(0, 1))
    }

    @Test
    fun `a stepped pair leaves the moved card's own gap out`() {
        // A pair 4 px too tall counting the gap below the moved card
        // (130 + 80 = 210 against 206) still comes in whole, the passed card first.
        assertEquals(-57, Showing.afterStep(23, 130, -57, 80, step = 1, end = 206, gap = 8))
        // Without the gap it fell back to the moved card alone, the passed one above.
        assertEquals(0, Showing.afterStep(23, 130, -57, 80, step = 1, end = 206))
    }

    @Test
    fun `a drag heads a way only past the touch slop and turns only back past it`() {
        // A slop of 8: a tap's pixel of shift is no drag...
        assertEquals(Pair(0, 0f), Showing.dragHeading(0, 0f, 3f, 8f))
        // ...past the slop it heads that way...
        assertEquals(Pair(1, 12f), Showing.dragHeading(0, 0f, 12f, 8f))
        // ...a hair's recoil at an edge leaves it heading on...
        assertEquals(Pair(1, 300f), Showing.dragHeading(1, 300f, 297f, 8f))
        // ...going on moves its furthest point...
        assertEquals(Pair(1, 320f), Showing.dragHeading(1, 300f, 320f, 8f))
        // ...and back past the slop it turns.
        assertEquals(Pair(-1, 290f), Showing.dragHeading(1, 300f, 290f, 8f))
        assertEquals(Pair(1, -40f), Showing.dragHeading(-1, -50f, -40f, 8f))
    }

    // ---- a category's name, wherever settings hold it ----

    private fun workNotebook(settings: String) = scratch("""[{"id": 1, "category": "Work"}]""", settings = settings)

    @Test
    fun `renaming a category carries the folded group and the chosen Show along`() {
        val load = workNotebook("""{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"},
            "collapsed_groups": ["Work"], "note_filter": "Work"}""")
        assertTrue(Organizing.renameCategory(load, "Work", "Job") is SaveResult.Ok)
        val saved = JSONObject(load.settings!!.file.readText())
        assertEquals(JSONArray(listOf("Job")).toString(), saved.getJSONArray("collapsed_groups").toString())
        assertEquals("Job", saved.getString("note_filter"))
    }

    @Test
    fun `deleting a category drops its folded group and returns Show to All`() {
        val load = workNotebook("""{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"},
            "collapsed_groups": ["Work", "General"], "note_filter": "Work"}""")
        assertTrue(Organizing.deleteCategory(load, "Work", trashNotes = false) is SaveResult.Ok)
        val saved = JSONObject(load.settings!!.file.readText())
        assertEquals(JSONArray(listOf("General")).toString(), saved.getJSONArray("collapsed_groups").toString())
        assertEquals("All", saved.getString("note_filter"))
        assertFalse(saved.getJSONObject("categories").has("Work"))
    }

    @Test
    fun `a search's words are handed to Android only while they are short`() {
        // Android hands the app's state over in one transaction as Home is
        // pressed; the list's search, kept twice, stopped the app from one
        // paste of a quarter of a million letters.
        val short = "milk ".repeat(Showing.SEARCH_KEPT / 5)
        assertEquals(Showing.SEARCH_KEPT, short.length)
        assertEquals(short, Showing.searchKept(short))
        assertEquals(null, Showing.searchKept(short + "x"))
        assertEquals(null, Showing.searchKept("x".repeat(300_000)))
        assertEquals("", Showing.searchKept(""))
    }
}
