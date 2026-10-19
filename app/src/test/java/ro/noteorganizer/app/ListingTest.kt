package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/** The list's sorts, its "Show", and its groups: the desktop's note_list rules. */
class ListingTest {

    private fun notes(vararg json: String): List<Note> = Notebook.parseNotes("[" + json.joinToString(",") + "]")
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

    // ---- a category's name, wherever settings hold it ----

    private fun scratch(settings: String): Notebook.Load {
        val dir = NioFiles.createTempDirectory("listing").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "category": "Work"}]""")
        File(dir, "settings.json").writeText(settings)
        return Notebook.load(dir)
    }

    @Test
    fun `renaming a category carries the folded group and the chosen Show along`() {
        val load = scratch("""{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"},
            "collapsed_groups": ["Work"], "note_filter": "Work"}""")
        assertTrue(Organizing.renameCategory(load, "Work", "Job") is SaveResult.Ok)
        val saved = JSONObject(load.settings!!.file.readText())
        assertEquals(JSONArray(listOf("Job")).toString(), saved.getJSONArray("collapsed_groups").toString())
        assertEquals("Job", saved.getString("note_filter"))
    }

    @Test
    fun `deleting a category drops its folded group and returns Show to All`() {
        val load = scratch("""{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"},
            "collapsed_groups": ["Work", "General"], "note_filter": "Work"}""")
        assertTrue(Organizing.deleteCategory(load, "Work", trashNotes = false) is SaveResult.Ok)
        val saved = JSONObject(load.settings!!.file.readText())
        assertEquals(JSONArray(listOf("General")).toString(), saved.getJSONArray("collapsed_groups").toString())
        assertEquals("All", saved.getString("note_filter"))
        assertFalse(saved.getJSONObject("categories").has("Work"))
    }
}
