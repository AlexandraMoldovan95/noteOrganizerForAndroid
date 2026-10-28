package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.file.Files as NioFiles

/**
 * Bulk changes, the categories and the Trash's permanent side, each held
 * to the desktop's rule. Every test reads the FILES back, not the arrays:
 * the screen draws what the program believes, the file is what it did.
 */
class OrganizingTest {

    private fun notebook(notes: String, trash: String? = null, settings: String? = null,
                         templates: String? = null): Notebook.Load {
        val dir = NioFiles.createTempDirectory("organizing").toFile()
        File(dir, "notes_data.json").writeText(notes)
        if (trash != null) File(dir, "trash.json").writeText(trash)
        if (settings != null) File(dir, "settings.json").writeText(settings)
        if (templates != null) File(dir, "templates.json").writeText(templates)
        File(dir, "attachments").mkdirs()
        return Notebook.load(dir)
    }

    private fun dir(load: Notebook.Load) = load.file!!.parentFile!!
    private fun attachments(load: Notebook.Load) = File(dir(load), "attachments")
    private fun notesOnDisk(load: Notebook.Load) = JSONArray(load.file!!.readText())
    private fun trashOnDisk(load: Notebook.Load) = JSONArray(load.trashFile!!.readText())
    private fun titles(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it).optString("title") }

    // ---- duplicate ----

    @Test
    fun `a duplicate is a separate note, with files of its own`() {
        val load = notebook("""
            [{"id": 4, "uid": "u", "title": "Trip", "body": "b",
              "attachments": [{"filename": "map.png", "stored_as": "map.png"}],
              "_prev": {"title": "older"}}]
        """.trimIndent())
        File(attachments(load), "map.png").writeText("MAP")
        val result = Organizing.duplicate(load, 4, attachments(load)) as SaveResult.Ok
        assertEquals(5, result.id)
        val copy = notesOnDisk(load).getJSONObject(1)
        assertEquals("Trip (copy)", copy.getString("title"))
        assertNotEquals("u", copy.getString("uid"))
        assertFalse("a copy has no earlier version of its own", copy.has("_prev"))
        val stored = copy.getJSONArray("attachments").getJSONObject(0).getString("stored_as")
        assertNotEquals("map.png", stored)
        assertEquals("MAP", File(attachments(load), stored).readText())
    }

    @Test
    fun `an untitled note's duplicate is called (copy)`() {
        val load = notebook("""[{"id": 1, "title": "  "}]""")
        Organizing.duplicate(load, 1, null)
        assertEquals("(copy)", notesOnDisk(load).getJSONObject(1).getString("title"))
    }

    // ---- several at once, and Undo ----

    @Test
    fun `several notes go to the Trash together, and Undo brings exactly those back`() {
        val load = notebook("""[{"id": 1, "title": "a"}, {"id": 2, "title": "b"}, {"id": 3, "title": "c"}]""")
        val result = Organizing.trashNotes(load, listOf(1, 3)) as SaveResult.Ok
        assertEquals(2, result.imported)
        assertEquals(listOf("b"), titles(notesOnDisk(load)))
        assertEquals(listOf("a", "c"), titles(trashOnDisk(load)))

        val keys = Notebook.load(dir(load)).trash.map { Organizing.TrashKey(it.id, it.deletedAt) }
        val back = Organizing.restore(Notebook.load(dir(load)), keys) as SaveResult.Ok
        assertEquals(2, back.imported)
        assertEquals(listOf("b", "a", "c"), titles(notesOnDisk(load)))
        assertEquals(0, trashOnDisk(load).length())
    }

    @Test
    fun `restored notes get fresh ids, because their old ones may be taken`() {
        val load = notebook("""[{"id": 1, "title": "new 1"}]""",
            trash = """[{"id": 1, "title": "old 1", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(1, "2026-10-01 10:00:00")))
        val ids = (0 until notesOnDisk(load).length()).map { notesOnDisk(load).getJSONObject(it).getInt("id") }
        assertEquals(listOf(1, 2), ids)
    }

    @Test
    fun `a restored note keeps its uid unless another note now has it, as the desktop's restore_many_from_trash`() {
        // A copy came by code while the note sat in the Trash, and took its identity.
        val load = notebook("""[{"id": 1, "uid": "x", "title": "came by code"}]""",
            trash = """[{"id": 1, "uid": "x", "title": "old", "deleted_at": "2026-10-01 10:00:00"},
                        {"id": 2, "uid": "y", "title": "other", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(1, "2026-10-01 10:00:00", "x"),
                                        Organizing.TrashKey(2, "2026-10-01 10:00:00", "y")))
        val uids = (0 until notesOnDisk(load).length()).map { notesOnDisk(load).getJSONObject(it).getString("uid") }
        assertEquals("x", uids[0])
        assertNotEquals("the restored twin gets an identity of its own", "x", uids[1])
        assertEquals("a uid nobody else has is kept, so an older code still finds it", "y", uids[2])
    }

    @Test
    fun `two Trash entries sharing an id and a moment are told apart by their uid`() {
        // An id is handed out again after a delete: two entries deleted in
        // one second can share both, as the desktop's _trash_tick_key learnt.
        val load = notebook("[]", trash = """
            [{"id": 2, "uid": "a", "title": "first", "deleted_at": "2026-10-01 10:00:00"},
             {"id": 2, "uid": "b", "title": "second", "deleted_at": "2026-10-01 10:00:00"}]
        """.trimIndent())
        val second = load.trash.first { it.title == "second" }
        Organizing.restore(load, listOf(Organizing.keyOf(second)))
        assertEquals(listOf("second"), titles(notesOnDisk(load)))
        assertEquals(listOf("first"), titles(trashOnDisk(load)))
    }

    @Test
    fun `every Trash row has a key of its own, twins in all three included`() {
        // A list keyed twice closes the app; only a hand-edited file holds such twins.
        val load = notebook("[]", trash = """
            [{"id": 2, "uid": "a", "deleted_at": "2026-10-01 10:00:00"},
             {"id": 2, "uid": "a", "deleted_at": "2026-10-01 10:00:00"},
             {"id": 2, "uid": "a#", "deleted_at": "2026-10-01 10:00:00"},
             {"id": 3, "uid": "c", "deleted_at": "2026-10-01 10:00:00"}]
        """.trimIndent())
        val keys = Organizing.rowKeys(load.trash)
        assertEquals(4, keys.size)
        assertEquals(4, keys.toSet().size)
    }

    @Test
    fun `ids shared or not numbers are repaired as the desktop's repair_note_ids repairs them`() {
        // The first holder keeps its number; the rest take the next free ones.
        val load = notebook("""[{"id": 1, "title": "a"}, {"id": 1, "title": "b"}, {"id": "3", "title": "c"},
                                {"id": 0, "title": "d"}, {"id": true, "title": "e"}]""")
        assertTrue(Organizing.repairIds(load))
        assertEquals(listOf(1, 4, 3, 5, 6), (0..4).map { notesOnDisk(load).getJSONObject(it).get("id") })
        assertFalse("a notebook already right is not written", Organizing.repairIds(Notebook.load(dir(load))))
    }

    @Test
    fun `a pin is not an edit, its note's updated time stays, as the desktop's toggle_pin`() {
        val load = notebook("""[{"id": 1, "updated": "2026-01-01 00:00:00"}, {"id": 2, "updated": "2026-01-01 00:00:00"}]""")
        Organizing.setPinned(load, listOf(1, 2), true)
        assertEquals(listOf("2026-01-01 00:00:00", "2026-01-01 00:00:00"),
                     (0..1).map { notesOnDisk(load).getJSONObject(it).getString("updated") })
        // A pin from the note's details, the same.
        val one = notebook("""[{"id": 1, "title": "t", "body": "b", "updated": "2026-01-01 00:00:00"}]""")
        Notebook.updateNote(one, 1, "t", "b", pinned = true)
        assertEquals("2026-01-01 00:00:00", notesOnDisk(one).getJSONObject(0).getString("updated"))
        assertTrue(notesOnDisk(one).getJSONObject(0).getBoolean("pinned"))
    }

    @Test
    fun `pin and category apply to every chosen note, and to no other`() {
        val load = notebook("""[{"id": 1}, {"id": 2}, {"id": 3}]""")
        Organizing.setPinned(load, listOf(1, 2), true)
        Organizing.moveToCategory(load, listOf(2, 3), "Work")
        val all = notesOnDisk(load)
        assertEquals(listOf(true, true, false), (0..2).map { all.getJSONObject(it).optBoolean("pinned") })
        assertEquals(listOf("", "Work", "Work"), (0..2).map { all.getJSONObject(it).optString("category") })
    }

    // ---- categories ----

    @Test
    fun `a renamed category is renamed everywhere it is held, and keeps its place`() {
        val load = notebook("""
            [{"id": 1, "category": "Work", "_prev": {"title": "t", "category": "Work"},
              "overrides": {"2026-10-12": {"category": "Work", "title": "day"}}},
             {"id": 2, "category": "Personal"}]
        """.trimIndent(),
            trash = """[{"id": 3, "category": "Work", "deleted_at": "2026-10-01 10:00:00"}]""",
            settings = """{"categories": {"General": "#7c5cff", "Work": "#4cc9f0", "Personal": "#f9c74f"}}""",
            templates = """[{"name": "Weekly", "category": "Work"}]""")
        assertTrue(Organizing.renameCategory(load, "Work", "Job") is SaveResult.Ok)
        val note = notesOnDisk(load).getJSONObject(0)
        assertEquals("Job", note.getString("category"))
        assertEquals("Job", note.getJSONObject("_prev").getString("category"))
        assertEquals("Job", note.getJSONObject("overrides").getJSONObject("2026-10-12").getString("category"))
        assertEquals("Job", trashOnDisk(load).getJSONObject(0).getString("category"))
        assertEquals("Job", JSONArray(File(dir(load), "templates.json").readText()).getJSONObject(0).getString("category"))
        val categories = JSONObject(File(dir(load), "settings.json").readText()).getJSONObject("categories")
        assertEquals("#4cc9f0", categories.getString("Job"))
        assertFalse(categories.has("Work"))
    }

    @Test
    fun `a renamed category keeps its place in the list`() {
        // Asked of the map before it is written: the computer's JSON library
        // does not keep key order, the phone's does, so a read-back here
        // could not tell. The phone's own order is looked at on the emulator.
        val map = linkedMapOf("General" to "#1", "Work" to "#2", "Personal" to "#3")
        assertEquals(listOf("General", "Job", "Personal"), Organizing.renamed(map, "Work", "Job").keys.toList())
        assertEquals("#2", Organizing.renamed(map, "Work", "Job")["Job"])
    }

    @Test
    fun `General can be neither renamed nor deleted, and a name is checked`() {
        val load = notebook("[]")
        assertTrue(Organizing.renameCategory(load, "General", "x") is SaveResult.Failed)
        assertTrue(Organizing.deleteCategory(load, "General", false) is SaveResult.Failed)
        assertTrue(Organizing.addCategory(load, "Work", "#000000") is SaveResult.Failed)
        assertTrue(Organizing.addCategory(load, "z".repeat(81), "#000000") is SaveResult.Failed)
        assertTrue(Organizing.addCategory(load, "Trips", "#123456") is SaveResult.Ok)
        assertTrue(Organizing.renameCategory(Notebook.load(dir(load)), "Trips", "Personal") is SaveResult.Failed)
    }

    @Test
    fun `deleting a category moves its notes to General, or to the Trash when asked`() {
        val settings = """{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}"""
        val keep = notebook("""[{"id": 1, "category": "Work"}]""", settings = settings)
        Organizing.deleteCategory(keep, "Work", trashNotes = false)
        assertEquals("General", notesOnDisk(keep).getJSONObject(0).getString("category"))
        assertFalse(JSONObject(File(dir(keep), "settings.json").readText()).getJSONObject("categories").has("Work"))

        val trash = notebook("""
            [{"id": 1, "category": "Work"},
             {"id": 2, "category": "General", "recurrence": {"freq": "weekly"},
              "overrides": {"2026-10-12": {"category": "Work"}}}]
        """.trimIndent(), settings = settings)
        assertEquals(1 to 1, Organizing.categoryCounts(trash, "Work"))
        Organizing.deleteCategory(trash, "Work", trashNotes = true)
        assertEquals("only the note filed there went", 1, notesOnDisk(trash).length())
        val series = notesOnDisk(trash).getJSONObject(0)
        assertEquals("the series' day moved to General instead",
            "General", series.getJSONObject("overrides").getJSONObject("2026-10-12").getString("category"))
        assertEquals("the trashed note no longer names the deleted category",
            "General", trashOnDisk(trash).getJSONObject(0).getString("category"))
    }

    // ---- the Trash, for good ----

    private fun daysAgo(days: Int): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Date().time - days * 86_400_000L))

    @Test
    fun `the start empties what outlived the window, and its files -- not one a note still uses`() {
        val load = notebook("""
            [{"id": 1, "title": "live", "attachments": [{"filename": "shared.png", "stored_as": "shared.png"}]}]
        """.trimIndent(), trash = """
            [{"id": 2, "title": "old", "deleted_at": "${daysAgo(40)}",
              "attachments": [{"filename": "a.png", "stored_as": "a.png"},
                              {"filename": "s.png", "stored_as": "shared.png"}]},
             {"id": 3, "title": "recent", "deleted_at": "${daysAgo(5)}"},
             {"id": 4, "title": "no stamp", "deleted_at": "not a date"}]
        """.trimIndent(), settings = """{"trash_retention_days": 30}""")
        File(attachments(load), "a.png").writeText("A")
        File(attachments(load), "shared.png").writeText("S")
        assertEquals(1, Organizing.purgeExpired(load, attachments(load)))
        assertEquals(listOf("recent", "no stamp"), titles(trashOnDisk(load)))
        assertFalse("the expired note's own file went", File(attachments(load), "a.png").exists())
        assertTrue("the file a note still uses stayed", File(attachments(load), "shared.png").exists())
    }

    @Test
    fun `a shorter window says how many it would take before it takes them`() {
        val load = notebook("[]", trash = """
            [{"id": 1, "deleted_at": "${daysAgo(10)}"}, {"id": 2, "deleted_at": "${daysAgo(2)}"}]
        """.trimIndent())
        assertEquals(1, Organizing.pastWindow(load, 7))
        assertEquals(0, Organizing.pastWindow(load, 30))
        assertEquals("nothing was deleted by asking", 2, trashOnDisk(load).length())
    }

    @Test
    fun `deleting for good takes the entry and its files, sparing what others use`() {
        val load = notebook("[]", trash = """
            [{"id": 1, "title": "x", "deleted_at": "2026-10-01 10:00:00",
              "attachments": [{"filename": "a", "stored_as": "a.png"}, {"filename": "b", "stored_as": "both.png"}]},
             {"id": 2, "title": "y", "deleted_at": "2026-10-02 10:00:00",
              "attachments": [{"filename": "b", "stored_as": "both.png"}]}]
        """.trimIndent())
        File(attachments(load), "a.png").writeText("A")
        File(attachments(load), "both.png").writeText("B")
        Organizing.deleteForever(load, listOf(Organizing.TrashKey(1, "2026-10-01 10:00:00")), attachments(load))
        assertEquals(listOf("y"), titles(trashOnDisk(load)))
        assertFalse(File(attachments(load), "a.png").exists())
        assertTrue(File(attachments(load), "both.png").exists())

        Organizing.emptyTrash(load, attachments(load))
        assertEquals(0, trashOnDisk(load).length())
        assertFalse(File(attachments(load), "both.png").exists())
    }

    @Test
    fun `a file name that tries to leave the folder is never deleted`() {
        val outside = NioFiles.createTempDirectory("outside").toFile()
        val victim = File(outside, "victim.txt").apply { writeText("keep") }
        val load = notebook("[]", trash = """
            [{"id": 1, "deleted_at": "2026-10-01 10:00:00",
              "attachments": [{"filename": "v", "stored_as": "../${outside.name}/victim.txt"}]}]
        """.trimIndent())
        Organizing.emptyTrash(load, attachments(load))
        assertTrue(victim.exists())
    }

    @Test
    fun `a path's bare name is not taken for a file a note uses`() {
        // "../x/shared.png" flattens to "shared.png" -- the very file a live
        // note uses. Deleting by the flattened name would take that note's
        // file; a stored name that is a path is not deleted at all.
        val load = notebook("""
            [{"id": 1, "attachments": [{"filename": "s", "stored_as": "shared.png"}]}]
        """.trimIndent(), trash = """
            [{"id": 2, "deleted_at": "2026-10-01 10:00:00",
              "attachments": [{"filename": "s", "stored_as": "../x/shared.png"}]}]
        """.trimIndent())
        File(attachments(load), "shared.png").writeText("LIVE")
        Organizing.emptyTrash(load, attachments(load))
        assertEquals("LIVE", File(attachments(load), "shared.png").readText())
    }

    // ---- the checklist's order ----

    @Test
    fun `a checklist row moves with its tick`() {
        val load = notebook("""
            [{"id": 1, "checklist": [{"text": "a", "done": false}, {"text": "b", "done": true},
                                     {"text": "c", "done": false}]}]
        """.trimIndent())
        assertTrue(Notebook.moveChecklistItem(load, 1, 1, 0) is SaveResult.Ok)
        var rows = notesOnDisk(load).getJSONObject(0).getJSONArray("checklist")
        assertEquals(listOf("b", "a", "c"), (0..2).map { rows.getJSONObject(it).getString("text") })
        assertTrue(rows.getJSONObject(0).getBoolean("done"))
        // Dragged two places down, a row SLIDES: the ones it passes move up
        // one each, rather than the first and last trading places.
        assertTrue(Notebook.moveChecklistItem(load, 1, 0, 2) is SaveResult.Ok)
        rows = notesOnDisk(load).getJSONObject(0).getJSONArray("checklist")
        assertEquals(listOf("a", "c", "b"), (0..2).map { rows.getJSONObject(it).getString("text") })
        assertTrue(Notebook.moveChecklistItem(load, 1, 0, 9) is SaveResult.Failed)
    }

    // ---- the welcome note ----

    @Test
    fun `the welcome note gives privacy a section of its own, and says where the policy is`() {
        val note = Welcome.build()
        val body = note.getString("body")
        val lines = body.split("\n")
        // Under a heading of its own, the owner's call: a line inside
        // another section did not catch the eye.
        val heading = lines.indexOf("🛡 Your privacy")
        assertTrue(heading >= 0)
        val section = lines.drop(heading + 1).takeWhile { it.isNotEmpty() }
        assertTrue(section.any { it.contains("collects nothing") })
        assertTrue(section.any { it.contains("Settings → About → Privacy policy") })
        val spans = note.getJSONArray("formatting")
        val cp = body.codePoints().toArray()
        assertTrue((0 until spans.length()).map { spans.getJSONObject(it) }.any {
            String(cp, it.getInt("start"), it.getInt("end") - it.getInt("start")) == "🛡 Your privacy"
        })
        // A backup carries this note to the desktop, where Tk draws the
        // emoji variation selector as a blank after the icon.
        assertFalse((note.getString("title") + body).contains("\uFE0F"))
        // 0.9.1 took "Follow the phone" away; the note went on promising it.
        assertFalse(body.contains("follow your phone"))
    }

    @Test
    fun `the welcome note is stored as the editor would store it`() {
        val note = Welcome.build()
        val body = note.getString("body")
        val lines = body.split("\n")
        val paragraphs = note.getJSONArray("paragraphs")
        for (i in 0 until paragraphs.length()) {
            val entry = paragraphs.getJSONObject(i)
            assertEquals("bullet", entry.getString("kind"))
            assertTrue(lines[entry.getInt("line")].startsWith("• "))
        }
        assertEquals(lines.count { it.startsWith("• ") }, paragraphs.length())
        // Every bold span covers exactly a heading or a **word** -- counted
        // in code points, so the emoji before it cannot shift it.
        val spans = note.getJSONArray("formatting")
        val bold = (0 until spans.length()).map { spans.getJSONObject(it) }.map { span ->
            val cp = body.codePoints().toArray()
            String(cp, span.getInt("start"), span.getInt("end") - span.getInt("start"))
        }
        assertTrue(bold.contains("📝 Write"))
        assertTrue(bold.contains("Settings → Backup"))
        assertTrue(bold.none { it.contains("**") || it.isBlank() })
        assertFalse(body.contains("**"))
        // A real note: the shape normalize_note expects, via the same door.
        val load = notebook("[]")
        assertTrue(Notebook.createNote(load, note.getString("title"), body,
            formatting = (0 until spans.length()).map { spans.getJSONObject(it) }
                .map { FormatSpan(it.getInt("start"), it.getInt("end"), it.getString("style")) },
            paragraphs = paragraphs) is SaveResult.Ok)
        assertEquals(paragraphs.length(),
            notesOnDisk(load).getJSONObject(0).getJSONArray("paragraphs").length())
    }

    @Test
    fun `every heading of the welcome note starts with an icon from the emoji block`() {
        // An older symbol such as ✍ was drawn in black and white and
        // narrower, here and on the desktop, so its title started left of
        // the others; the emoji block is drawn by one font, at one width.
        val icons = Welcome.LINES.filter { it.startsWith("# ") }
            .map { it.removePrefix("# ").substringBefore(' ') }
        assertTrue(icons.size >= 5)
        assertEquals(emptyList<String>(), icons.filter {
            it.codePointCount(0, it.length) != 1 || it.codePointAt(0) < 0x1F000
        })
    }

    // ---- duplicates ----

    @Test
    fun `duplicates are the same text twice, a copy's suffix and spacing aside, never a different checklist`() {
        val load = notebook("""
            [{"id": 1, "title": "Groceries", "body": "milk  and eggs", "category": "Home", "date": "2026-10-01"},
             {"id": 2, "title": "Groceries (copy)", "body": "Milk and eggs ", "category": "Home", "color": "#ff0000", "pinned": true},
             {"id": 3, "title": "Groceries", "body": "milk and eggs", "category": "Home",
              "checklist": [{"text": "bread", "done": false}]},
             {"id": 4, "title": "Groceries", "body": "milk and eggs", "category": "Work"},
             {"id": 5, "title": "groceries (copy) (copy)", "body": "milk and eggs", "category": "Home"}]
        """.trimIndent())
        assertEquals(listOf(listOf(1, 2, 5)), Organizing.duplicateGroups(load))
    }

    @Test
    fun `a non-breaking space pasted from the web is a space to the duplicate check, as to the desktop's`() {
        // Python's split() parts words on every Unicode space; Java's \s on ASCII alone.
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "hi world"},
             {"id": 2, "title": "Trip", "body": "hi world"},
             {"id": 3, "title": "Trip", "body": "hi 　world\u0085"}]
        """.trimIndent())
        assertEquals(listOf(listOf(1, 2, 3)), Organizing.duplicateGroups(load))
    }

    @Test
    fun `cleaning up moves the ticked copies to the Trash, their files copied to the copy kept`() {
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "b"},
             {"id": 2, "title": "Trip (copy)", "body": "b", "attachments": [{"filename": "map", "stored_as": "map.png"}]},
             {"id": 3, "title": "Trip", "body": "b", "attachments": [{"filename": "map", "stored_as": "map.png"}]},
             {"id": 4, "title": "Plan", "body": "p"}, {"id": 5, "title": "Plan", "body": "p"}]
        """.trimIndent())
        File(attachments(load), "map.png").writeText("MAP")
        val groups = Organizing.duplicateGroups(load)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), groups)
        assertTrue(Organizing.cleanUpDuplicates(load, groups, setOf(2, 3, 4, 5), attachments(load)) is SaveResult.Ok)
        val left = notesOnDisk(load)
        assertEquals("a group ticked whole goes whole", listOf(1), (0 until left.length()).map { left.getJSONObject(it).getInt("id") })
        val merged = left.getJSONObject(0).getJSONArray("attachments")
        assertEquals("one file, though two copies listed it", 1, merged.length())
        val stored = merged.getJSONObject(0).getString("stored_as")
        assertNotEquals("its own copy, not the Trash's", "map.png", stored)
        assertEquals("MAP", File(attachments(load), stored).readText())
        assertEquals(4, trashOnDisk(load).length())
        val none = Organizing.cleanUpDuplicates(load, groups, emptySet(), attachments(load))
        assertTrue(none is SaveResult.Failed && none.reason.startsWith("Tick the duplicates"))
    }

    @Test
    fun `the copy kept is the first one not ticked, wherever it stands`() {
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "b", "attachments": [{"filename": "a", "stored_as": "a.png"}]},
             {"id": 2, "title": "Trip", "body": "b"},
             {"id": 3, "title": "Trip", "body": "b"}]
        """.trimIndent())
        File(attachments(load), "a.png").writeText("A")
        assertTrue(Organizing.cleanUpDuplicates(load, listOf(listOf(1, 2, 3)), setOf(1, 3), attachments(load)) is SaveResult.Ok)
        val left = notesOnDisk(load)
        assertEquals(1, left.length())
        assertEquals(2, left.getJSONObject(0).getInt("id"))
        assertEquals("the first copy's file went to the one kept", 1, left.getJSONObject(0).getJSONArray("attachments").length())
    }

    @Test
    fun `only a copy in a group goes -- a tick left on a note no longer one is ignored`() {
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "b"}, {"id": 2, "title": "Trip", "body": "b"},
             {"id": 3, "title": "Plan", "body": "p"}]
        """.trimIndent())
        assertTrue(Organizing.cleanUpDuplicates(load, listOf(listOf(1, 2)), setOf(2, 3), attachments(load)) is SaveResult.Ok)
        val left = notesOnDisk(load)
        assertEquals(listOf(1, 3), (0 until left.length()).map { left.getJSONObject(it).getInt("id") })
    }

    // ---- attached files ----

    private fun incoming(name: String, text: String) = Organizing.Incoming(name) { it.writeText(text) }

    @Test
    fun `an attachment's label ends in the type of the file that opens, and hides nothing`() {
        // The desktop's own cases (a7495d5): a backup can write any label
        // over any file, and a bidi mark can hide ".apk" behind ".jpg".
        fun labels(json: String) = Normalize.cleanAttachments(JSONArray(json)).let { a ->
            (0 until a.length()).map { a.getJSONObject(it).getString("filename") } }
        assertEquals(listOf("Holiday photo.rdp", "photo", "x.rdp"), labels("""[
            {"filename": "Holiday photo.jpg", "stored_as": "20261001120000000001_trip.rdp"},
            {"filename": "photo.tar.jpg", "stored_as": "20261001_photo"},
            {"filename": "x", "stored_as": ".rdp"}]"""))
        val hidden = "\u0000\u0007\u001b\u007f\u0085\u009f؜‎‏‪‫‬‭‮" +
            "⁦⁧⁨⁩  ﻿"
        val bidi = JSONArray().put(JSONObject().put("filename", "Invoice‮fdp.exe").put("stored_as", "20261001_inv.exe"))
            .put(JSONObject().put("filename", "a${hidden}b.txt").put("stored_as", "20261001_ab.txt"))
        assertEquals(listOf("Invoicefdp.exe", "ab.txt"), labels(bidi.toString()))
        // A name the app stored itself shows as it was, every time.
        for (n in listOf("report.pdf", "Photo.JPG", "README", ".bashrc", "Ünïcödé €.docx", "a b.tar.gz", "שלום.txt",
                         "family 👨‍👩‍👧.png")) {
            val once = Normalize.attachmentDisplayName(n, "20261028120000000000_$n")
            assertEquals(n, once)
            assertEquals(n, Normalize.attachmentDisplayName(once, "20261028120000000000_$n"))
        }
        // The type handed to another app is the stored file's.
        assertEquals(".html", Normalize.fileTypeOf("20261018_trip.html"))
        assertEquals("", Normalize.fileTypeOf("20261018_payload"))
    }

    @Test
    fun `a stored name is the moment and the file's own name, and one Windows takes`() {
        val at = java.time.LocalDateTime.of(2026, 10, 16, 9, 30, 5, 123456000)
        assertEquals("20261016093005123456_map.png", Organizing.storedName("map.png", at))
        assertEquals("20261016093005123456_a_b_c_d.txt", Organizing.storedName("a:b?c|d.txt", at))
        assertEquals("20261016093005123456_report", Organizing.storedName("folder/report. ", at))
        assertEquals("20261016093005123456_file", Organizing.storedName("...", at))
    }

    @Test
    fun `files are copied in and listed in one write, and a second file with the same name is kept apart`() {
        val load = notebook("""[{"id": 1, "title": "Trip", "updated": "2026-10-01 09:00:00"}]""")
        // The same moment for both, as two files picked together can have.
        val moment = java.time.LocalDateTime.of(2026, 10, 16, 9, 30, 5, 123456000)
        val done = Organizing.attach(load, 1, attachments(load),
            listOf(incoming("map.png", "MAP"), incoming("map.png", "MAP 2")), clock = { moment })
        assertTrue(done.result is SaveResult.Ok)
        assertEquals(2, done.added)
        val atts = notesOnDisk(load).getJSONObject(0).getJSONArray("attachments")
        assertEquals(2, atts.length())
        val stored = (0 until 2).map { atts.getJSONObject(it).getString("stored_as") }
        assertNotEquals(stored[0], stored[1])
        assertEquals(listOf("MAP", "MAP 2"), stored.map { File(attachments(load), it).readText() })
        assertEquals("map.png", atts.getJSONObject(0).getString("filename"))
        assertNotEquals("2026-10-01 09:00:00", notesOnDisk(load).getJSONObject(0).getString("updated"))
    }

    @Test
    fun `a full note takes no more, a copy that fails leaves nothing behind`() {
        val many = JSONArray((1..Normalize.MAX_ATTACHMENTS).map {
            JSONObject().put("filename", "f$it").put("stored_as", "f$it") })
        val full = notebook(JSONArray().put(JSONObject().put("id", 1).put("attachments", many)).toString())
        assertEquals(0, Organizing.roomFor(full, 1))
        val refused = Organizing.attach(full, 1, attachments(full), listOf(incoming("x", "x"))).result
        assertTrue("said in the desktop's words", refused is SaveResult.Failed && refused.reason.startsWith("A note can keep 200"))
        assertTrue(attachments(full).listFiles().isNullOrEmpty())

        val load = notebook("""[{"id": 1, "title": "Trip"}]""")
        val broken = Organizing.Incoming("bad.bin") { it.writeText("half"); throw java.io.IOException("gone") }
        val done = Organizing.attach(load, 1, attachments(load), listOf(broken, incoming("ok.txt", "OK")))
        assertEquals(1, done.added)
        assertEquals(listOf("bad.bin"), done.failed)
        assertEquals(1, attachments(load).listFiles()!!.size)
    }

    @Test
    fun `files picked for a note go only to that note, never to another that took its id`() {
        // The copy outlived the editor; meanwhile the note went to the Trash
        // and a new one took its id -- and the files went to the new one.
        val load = notebook("""[{"id": 4, "uid": "new-note", "title": "Other"}]""")
        val refused = Organizing.attach(load, 4, attachments(load), listOf(incoming("scan.pdf", "PDF")), uid = "holder").result
        assertTrue(refused is SaveResult.Failed && refused.reason.contains("no longer here"))
        assertEquals(0, notesOnDisk(load).getJSONObject(0).optJSONArray("attachments")?.length() ?: 0)
        assertTrue(attachments(load).listFiles().isNullOrEmpty())
        // The note itself, by its uid, takes them.
        assertTrue(Organizing.attach(load, 4, attachments(load), listOf(incoming("scan.pdf", "PDF")), uid = "new-note").result is SaveResult.Ok)
    }

    @Test
    fun `files picked for a note sent to the Trash and brought back by Undo go to it, under its new id`() {
        // Undo restores a note under a new id and its own uid; looked up by
        // the id taken at the pick, it read as gone, and the copies were
        // thrown away with "no longer here" -- the note in the list.
        val load = notebook("""[{"id": 1, "uid": "recipes", "title": "Recipes"},
            {"id": 2, "uid": "house", "title": "House papers"}, {"id": 3, "uid": "todo", "title": "To do"}]""",
            trash = "[]")
        assertTrue(Organizing.trashNotes(load, listOf(2)) is SaveResult.Ok)
        val entry = JSONArray(File(dir(load), "trash.json").readText()).getJSONObject(0)
        assertTrue(Organizing.restore(load, listOf(Organizing.TrashKey(2, entry.getString("deleted_at"), "house"))) is SaveResult.Ok)
        val back = notesOnDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.single { it.optString("uid") == "house" }
        assertEquals("a new id", 4, back.getInt("id"))
        val done = Organizing.attach(load, 2, attachments(load), listOf(incoming("deed.pdf", "PDF")), uid = "house")
        assertEquals(SaveResult.Ok(4), done.result)
        val after = notesOnDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals("deed.pdf", after.single { it.optString("uid") == "house" }.getJSONArray("attachments").getJSONObject(0).getString("filename"))
        assertTrue("no other note took them", after.filter { it.optString("uid") != "house" }.all { (it.optJSONArray("attachments")?.length() ?: 0) == 0 })
    }

    @Test
    fun `files picked for one of two notes wearing one identity go to that one, not the first`() {
        // A code or a backup holding two notes with one uid; looked up by
        // the first note wearing it, files picked in the second went to the
        // first, unsaid. A day's Undo looked its note up the same way.
        val load = notebook("""[{"id": 2, "uid": "dup", "title": "First"}, {"id": 3, "uid": "dup", "title": "Second"}]""")
        assertEquals(SaveResult.Ok(3), Organizing.attach(load, 3, attachments(load), listOf(incoming("pic.png", "PNG")), uid = "dup").result)
        val after = notesOnDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals(listOf(0, 1), after.map { it.optJSONArray("attachments")?.length() ?: 0 })
        assertEquals(1, Organizing.indexOfNote(load.raw!!, 3, "dup"))
        assertEquals("its id gone, the first wearing it", 0, Organizing.indexOfNote(load.raw!!, 9, "dup"))
        assertEquals("its id another note's, nothing", -1, Organizing.indexOfNote(load.raw!!, 2, "elsewhere"))
    }

    @Test
    fun `a file taken off a note is deleted, unless another note or the Trash still uses it`() {
        val load = notebook("""
            [{"id": 1, "attachments": [{"filename": "a", "stored_as": "a.png"}, {"filename": "b", "stored_as": "b.png"},
                                       {"filename": "c", "stored_as": "c.png"}]},
             {"id": 2, "attachments": [{"filename": "b", "stored_as": "b.png"}]}]
        """.trimIndent(), trash = """[{"id": 9, "deleted_at": "2026-10-01 09:00:00",
             "attachments": [{"filename": "c", "stored_as": "c.png"}]}]""")
        listOf("a.png", "b.png", "c.png").forEach { File(attachments(load), it).writeText(it) }
        assertTrue(Organizing.detach(load, 1, "a.png", attachments(load)) is SaveResult.Ok)
        assertFalse(File(attachments(load), "a.png").exists())
        assertTrue(Organizing.detach(load, 1, "b.png", attachments(load)) is SaveResult.Ok)
        assertTrue("note 2 still shows it", File(attachments(load), "b.png").exists())
        assertTrue(Organizing.detach(load, 1, "c.png", attachments(load)) is SaveResult.Ok)
        assertTrue("a note in the Trash still has it", File(attachments(load), "c.png").exists())
        assertEquals(0, notesOnDisk(load).getJSONObject(0).getJSONArray("attachments").length())
        assertTrue(Organizing.detach(load, 1, "b.png", attachments(load)) is SaveResult.Failed)
    }

    // ---- Custom order, arranged by hand ----

    private val four = """
        [{"id": 1, "title": "Bread", "category": "Home", "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00", "order": 0},
         {"id": 2, "title": "Alarm", "category": "Work", "created": "2026-10-02 09:00:00", "updated": "2026-10-02 09:00:00", "order": 1},
         {"id": 3, "title": "Cakes", "category": "Home", "created": "2026-10-03 09:00:00", "updated": "2026-10-03 09:00:00", "order": 2},
         {"id": 4, "title": "Dates", "category": "Work", "created": "2026-10-04 09:00:00", "updated": "2026-10-04 09:00:00", "pinned": true}]
    """.trimIndent()

    private fun orders(load: Notebook.Load): Map<Int, Int> {
        val a = notesOnDisk(load)
        return (0 until a.length()).map { a.getJSONObject(it) }.associate { it.getInt("id") to it.optInt("order", -1) }
    }

    @Test
    fun `a note moved before or after another, every note numbered again, none stamped`() {
        val load = notebook(four)
        // The pinned note first, then the placed ones in their order.
        assertEquals(listOf(4, 1, 2, 3), sortNotes(load.notes, SortOrder.CUSTOM).map { it.id })
        assertTrue(Organizing.moveNote(load, 3, anchor = 1, before = true, grouped = false) is SaveResult.Ok)
        assertEquals(mapOf(4 to 0, 3 to 1, 1 to 2, 2 to 3), orders(load))
        assertEquals(listOf(4, 3, 1, 2), sortNotes(Notebook.load(dir(load)).notes, SortOrder.CUSTOM).map { it.id })
        assertTrue(Organizing.moveNote(load, 3, anchor = 2, before = false, grouped = false) is SaveResult.Ok)
        assertEquals(mapOf(4 to 0, 1 to 1, 2 to 2, 3 to 3), orders(load))
        // A move is not an edit.
        assertEquals("2026-10-03 09:00:00", notesOnDisk(load).getJSONObject(2).getString("updated"))
    }

    @Test
    fun `a move stays inside its block -- pinned with pinned, and a category when grouped`() {
        val load = notebook(four)
        assertTrue(Organizing.moveNote(load, 1, anchor = 4, before = true, grouped = false) is SaveResult.Failed)
        assertTrue(Organizing.moveNote(load, 1, anchor = 2, before = false, grouped = true) is SaveResult.Failed)
        assertEquals(mapOf(1 to 0, 2 to 1, 3 to 2, 4 to -1), orders(load))
        assertTrue(Organizing.moveNote(load, 1, anchor = 3, before = false, grouped = true) is SaveResult.Ok)
        assertTrue(Organizing.moveNote(load, 1, anchor = 2, before = false, grouped = false) is SaveResult.Ok)
    }

    @Test
    fun `Custom order chosen before anything was placed starts from the order shown, every note placed`() {
        val load = notebook("""
            [{"id": 1, "title": "Bread", "created": "2026-10-01 09:00:00"},
             {"id": 2, "title": "Alarm", "created": "2026-10-02 09:00:00"},
             {"id": 3, "title": "", "created": "2026-10-03 09:00:00"},
             {"id": 4, "title": "Dates", "created": "2026-10-04 09:00:00", "pinned": true}]
        """.trimIndent())
        assertTrue(Organizing.placeAsSorted(load, SortOrder.TITLE_AZ) is SaveResult.Ok)
        // As Title A-Z showed them: the pinned note first, the untitled last.
        assertEquals(mapOf(4 to 0, 2 to 1, 1 to 2, 3 to 3), orders(load))
        assertEquals(listOf(4, 2, 1, 3), sortNotes(Notebook.load(dir(load)).notes, SortOrder.CUSTOM).map { it.id })
    }

    @Test
    fun `Move up and Move down find the neighbour a drag would drop next to, and stop at the block's edge`() {
        // Arranging by hand without a drag: a screen reader, Switch Access
        // or a hand that cannot drag had no way at all.
        val shown = listOf(Note(id = 1, pinned = true), Note(id = 2), Note(id = 3, category = "Work"), Note(id = 4))
        assertEquals(2 to true, Organizing.stepPlace(shown, 3, -1, grouped = false))
        assertEquals(4 to false, Organizing.stepPlace(shown, 3, 1, grouped = false))
        assertNull("the last goes no lower", Organizing.stepPlace(shown, 4, 1, grouped = false))
        assertNull("a pinned note stands above the rest", Organizing.stepPlace(shown, 2, -1, grouped = false))
        assertNull("a group is its category", Organizing.stepPlace(shown, 3, -1, grouped = true))
        assertNull(Organizing.stepPlace(shown, 9, -1, grouped = false))
    }
}
