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
    fun `a moved card is brought into sight only when it is out of the window the list kept`() {
        // Rounds 8-14 of wave 2: pinned, put back by Undo, pinned under a sliver, grouped.
        fun flat(vararg ids: Int) = Showing.rows(ids.map { notes("""{"id": $it}""").single() }, false, emptySet(), emptyList())
        // In the window it was asked over: no scroll.
        assertEquals(null, Showing.revealAt(flat(1, 2, 3, 4, 5), setOf(3), listOf(2, 3, 4), atTop = false, anchor = 2, lead = 0))
        // Pinned from under a sliver (2, the item the list keeps): the window
        // counted from it, and the pinned card (3) is now above it.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6), setOf(3), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
        // Two pinned together from under a sliver: both above it now (round 13).
        assertEquals(0, Showing.revealAt(flat(3, 4, 1, 2, 5, 6), setOf(3, 4), listOf(3, 4, 5), atTop = false, anchor = 2, lead = 1))
        // Several moved: the first of them in the list's NEW order, which the
        // move reordered (round 13).
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
        // Round 14: the list's own first card among those moved, and moved
        // down by them. The list followed it, so the card now first went off
        // the top: filed together from the top...
        assertEquals(0, Showing.revealAt(flat(4, 5, 1, 2, 3, 9), setOf(4, 5), listOf(5, 4, 1, 2), atTop = true, anchor = 5, lead = 0))
        // ...pinned together, one already pinned...
        assertEquals(0, Showing.revealAt(flat(3, 6, 1, 2, 4), setOf(6, 3), listOf(6, 3, 1, 2), atTop = true, anchor = 6, lead = 0))
        // ...a sliver pinned, the list following it to the top.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6, 7), setOf(3), listOf(4, 5, 6), atTop = false, anchor = 3, lead = 1))
        // Followed far down: there, not the top, which shows none of them.
        assertEquals(8, Showing.revealAt(flat(1, 2, 3, 4, 5, 6, 7, 8, 10, 11), setOf(10, 11), listOf(11, 1, 2), atTop = true, anchor = 11, lead = 0))
        // The item it kept gone from the list: counted from a card in sight that did not move.
        assertEquals(0, Showing.revealAt(flat(3, 1, 2, 4, 5, 6), setOf(3), listOf(3, 4, 5), atTop = false, anchor = 99, lead = 1))
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
        // paste of a quarter of a million letters (the final audit's eighth round).
        val short = "milk ".repeat(Showing.SEARCH_KEPT / 5)
        assertEquals(Showing.SEARCH_KEPT, short.length)
        assertEquals(short, Showing.searchKept(short))
        assertEquals(null, Showing.searchKept(short + "x"))
        assertEquals(null, Showing.searchKept("x".repeat(300_000)))
        assertEquals("", Showing.searchKept(""))
    }
}
