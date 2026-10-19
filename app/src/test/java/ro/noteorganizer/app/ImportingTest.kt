package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files as NioFiles
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bringing notes in, held to the desktop's import_data: the three
 * questions, what each answer does, and what a backup brings besides its
 * notes. Nothing is written before every question is answered.
 */
class ImportingTest {

    private fun notebook(notes: String, trash: String? = null, settings: String? = null): Notebook.Load {
        val dir = NioFiles.createTempDirectory("importing").toFile()
        File(dir, "notes_data.json").writeText(notes)
        if (trash != null) File(dir, "trash.json").writeText(trash)
        if (settings != null) File(dir, "settings.json").writeText(settings)
        File(dir, "attachments").mkdirs()
        return Notebook.load(dir)
    }

    private fun dir(load: Notebook.Load) = load.file!!.parentFile!!
    private fun onDisk(load: Notebook.Load) = JSONArray(load.file!!.readText())
    private fun notes(json: String) = JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun backupPlan(load: Notebook.Load, incoming: String) =
        Importing.plan(load.raw!!, notes(incoming), Importing.Source.BACKUP)

    // ---- the plan, which writes nothing ----

    @Test
    fun `the plan sorts what arrived into the three questions, and writes nothing`() {
        val load = notebook("""
            [{"id": 1, "uid": "a", "title": "Mine", "updated": "2026-10-10 10:00:00"},
             {"id": 2, "uid": "b", "title": "Same", "category": "General"},
             {"id": 3, "uid": "c", "title": "Newer here", "updated": "2026-10-10 10:00:00"}]
        """.trimIndent())
        val before = load.file!!.readText()
        val plan = backupPlan(load, """
            [{"uid": "a", "title": "Changed", "updated": "2026-10-11 10:00:00"},
             {"uid": "b", "title": "Same", "category": "General"},
             {"uid": "c", "title": "Older there", "updated": "2026-01-01 10:00:00"},
             {"uid": "z", "title": "Fresh"},
             {"title": "No identity"}]
        """.trimIndent())
        assertEquals(5, plan.total)
        assertEquals(listOf(0, 2), plan.differing.map { it.mineIndex })
        assertEquals(listOf(1), plan.unchanged.map { it.mineIndex })
        assertEquals(listOf("Fresh", "No identity"), plan.fresh.map { it.getString("title") })
        assertEquals("the backup's copy of c is older", 1, plan.older)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `a note is compared as a load shapes it, so what no person changed is no change`() {
        // Filed here under General when it came, from a category this phone
        // lacks; a day's empty colour; a table closing the body, with and
        // without its line. (Styles in another order are a difference to the
        // desktop too: its normalize_formatting keeps their order.)
        val load = notebook("""
            [{"id": 1, "uid": "a", "title": "T", "body": "x\n| a | b |\n", "category": "General",
              "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]},
              "overrides": {"2026-10-12": {"title": "own", "color": ""}},
              "formatting": [{"start": 0, "end": 1, "style": "bold"}, {"start": 0, "end": 1, "style": "underline"}]}]
        """.trimIndent())
        val incoming = """
            [{"uid": "a", "title": "T", "body": "x\n| a | b |", "category": "Errands",
              "date": "2026-10-05", "recurrence": {"freq": "weekly", "weekdays": [0]},
              "overrides": {"2026-10-12": {"title": "own"}},
              "formatting": [{"start": 0, "end": 1, "style": "bold"}, {"start": 0, "end": 1, "style": "underline"}]}]
        """.trimIndent()
        val plan = Importing.plan(load.raw!!, notes(incoming), Importing.Source.CODE, setOf("General", "Work"))
        assertEquals("unchanged, not \"edited elsewhere\"", 1, plan.unchanged.size)
        assertEquals(0, plan.differing.size)
    }

    @Test
    fun `an imported note takes the desktop's shape -- styles, line entries, a closing table's line`() {
        val shaped = Normalize.normalizeNote(org.json.JSONObject("""
            {"title": "t", "body": "a\n| x | y |",
             "formatting": [{"start": 0, "end": 1, "style": "sparkle"}, {"start": 0, "end": 1, "style": "bold"}],
             "paragraphs": [{"line": 0, "align": "center"}, {"line": 9, "align": "center"}]}""".trimIndent()))
        assertEquals("a\n| x | y |\n", shaped.getString("body"))
        assertEquals(1, shaped.getJSONArray("formatting").length())
        assertEquals("bold", shaped.getJSONArray("formatting").getJSONObject(0).getString("style"))
        assertEquals("a line past the body's end is no line", 1, shaped.getJSONArray("paragraphs").length())
    }

    @Test
    fun `a code's notes are never called older -- a code does not say when it was saved`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "x", "updated": "2026-10-10 10:00:00"}]""")
        val plan = Importing.plan(load.raw!!, notes("""[{"uid": "a", "title": "y", "updated": "2020-01-01 00:00:00"}]"""),
            Importing.Source.CODE)
        assertEquals(0, plan.older)
    }

    // ---- question 1: update, or add as new ----

    @Test
    fun `updating from a backup brings its timeline and its pin, and adds its files`() {
        val load = notebook("""
            [{"id": 1, "uid": "a", "title": "Old", "pinned": false,
              "exceptions": [], "attachments": [{"filename": "mine.png", "stored_as": "mine.png"}]}]
        """.trimIndent())
        File(dir(load), "attachments/mine.png").writeText("MINE")
        val unpacked = NioFiles.createTempDirectory("unpacked").toFile()
        File(unpacked, "theirs.png").writeText("THEIRS")
        val plan = backupPlan(load, """
            [{"uid": "a", "title": "New", "pinned": true, "exceptions": ["2026-10-12"],
              "attachments": [{"filename": "theirs.png", "stored_as": "theirs.png"}]}]
        """.trimIndent())
        val result = Importing.apply(load, plan, Importing.Answers(update = true),
            File(dir(load), "attachments"), Importing.Extras(attachments = unpacked))
        assertTrue(result is Importing.Result.Done)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("New", note.getString("title"))
        assertTrue(note.getBoolean("pinned"))
        assertEquals("2026-10-12", note.getJSONArray("exceptions").getString(0))
        val files = (0 until note.getJSONArray("attachments").length())
            .map { note.getJSONArray("attachments").getJSONObject(it).getString("stored_as") }
        assertEquals("a file here is never swapped out", listOf("mine.png", "theirs.png"), files)
        assertEquals("THEIRS", File(dir(load), "attachments/theirs.png").readText())
        assertEquals("the rescue copy holds the old pin", false,
            note.getJSONObject("_prev").getBoolean("pinned"))
    }

    @Test
    fun `a backup's new notes keep their rescue copies, a code's never take one`() {
        // A notebook moved to a new phone by a backup lost every "Restore previous".
        val load = notebook("""[]""")
        val plan = backupPlan(load, """[{"uid": "n", "title": "Now", "_prev": {"title": "Before", "body": ""}}]""")
        Importing.apply(load, plan, Importing.Answers(), null)
        assertEquals("Before", onDisk(load).getJSONObject(0).getJSONObject("_prev").getString("title"))
        val coded = notebook("""[]""")
        val fromCode = Importing.plan(coded.raw!!, notes("""[{"uid": "n", "title": "Now", "_prev": {"title": "Before"}}]"""),
                                      Importing.Source.CODE)
        Importing.apply(coded, fromCode, Importing.Answers(), null)
        assertFalse(onDisk(coded).getJSONObject(0).has("_prev"))
    }

    @Test
    fun `adding as new keeps both, and the copy does not wear the original's identity`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "Old"}]""")
        val plan = backupPlan(load, """[{"uid": "a", "title": "New"}]""")
        Importing.apply(load, plan, Importing.Answers(update = false), null)
        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("Old", all.getJSONObject(0).getString("title"))
        assertEquals("New", all.getJSONObject(1).getString("title"))
        assertNotEquals("a", all.getJSONObject(1).getString("uid"))
        assertEquals(2, all.getJSONObject(1).getInt("id"))
    }

    // ---- question 2: the notes that are already here, unchanged ----

    @Test
    fun `unchanged notes are skipped unless copies are asked for`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "Same"}]""")
        val plan = backupPlan(load, """[{"uid": "a", "title": "Same"}]""")
        val skipped = Importing.apply(load, plan, Importing.Answers(), null) as Importing.Result.Done
        assertEquals(1, skipped.outcome.unchangedSkipped)
        assertEquals(1, onDisk(load).length())

        val again = Importing.apply(load, plan, Importing.Answers(copyUnchanged = true), null)
            as Importing.Result.Done
        assertEquals(1, again.outcome.added)
        val all = onDisk(load)
        assertEquals(2, all.length())
        assertNotEquals("a", all.getJSONObject(1).getString("uid"))
    }

    @Test
    fun `a backup holding a file this copy lacks is a change, not an unchanged note`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "Same", "attachments": []}]""")
        val plan = backupPlan(load, """
            [{"uid": "a", "title": "Same", "attachments": [{"filename": "p.png", "stored_as": "p.png"}]}]
        """.trimIndent())
        assertEquals(1, plan.differing.size)
        assertEquals(0, plan.unchanged.size)
    }

    // ---- question 3: look-alikes ----

    @Test
    fun `a new note that looks like one here is a question, and skipping it skips only it`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "Groceries", "body": "milk  eggs"}]""")
        // No shared identity -- a note typed twice, or a backup from a
        // phone that never saw this notebook. Case, spacing and "(copy)"
        // do not make it different.
        val plan = backupPlan(load, """
            [{"uid": "x", "title": "groceries (copy)", "body": "Milk eggs"},
             {"uid": "y", "title": "Something else"}]
        """.trimIndent())
        assertEquals(1, Importing.lookAlikes(load.raw!!, plan, update = false))
        val result = Importing.apply(load, plan, Importing.Answers(skipDuplicates = true), null)
            as Importing.Result.Done
        assertEquals(1, result.outcome.duplicatesSkipped)
        assertEquals(listOf("Groceries", "Something else"),
            (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getString("title") })
    }

    @Test
    fun `a different checklist is a different note, however alike the words`() {
        val load = notebook("""[{"id": 1, "uid": "a", "title": "List", "checklist": [{"text": "a", "done": false}]}]""")
        val plan = backupPlan(load, """[{"uid": "x", "title": "List", "checklist": [{"text": "a", "done": true}]}]""")
        assertEquals(0, Importing.lookAlikes(load.raw!!, plan, update = false))
    }

    @Test
    fun `look-alikes are judged against the notebook the updates will leave`() {
        // The note here is about to be updated to "B"; a fresh note "A"
        // then looks like nothing, and a fresh note "B" like the update.
        val load = notebook("""[{"id": 1, "uid": "a", "title": "A"}]""")
        val plan = backupPlan(load, """
            [{"uid": "a", "title": "B"}, {"uid": "x", "title": "A"}, {"uid": "y", "title": "B"}]
        """.trimIndent())
        assertEquals(1, Importing.lookAlikes(load.raw!!, plan, update = true))
        val result = Importing.apply(load, plan,
            Importing.Answers(update = true, skipDuplicates = true), null) as Importing.Result.Done
        assertEquals(listOf("B", "A"),
            (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getString("title") })
        assertEquals(1, result.outcome.duplicatesSkipped)
    }

    // ---- files ----

    @Test
    fun `a file another note already uses is given a copy of its own`() {
        val load = notebook("""
            [{"id": 1, "uid": "a", "title": "Mine", "attachments": [{"filename": "p.png", "stored_as": "p.png"}]}]
        """.trimIndent())
        File(dir(load), "attachments/p.png").writeText("PHOTO")
        val plan = backupPlan(load, """
            [{"uid": "x", "title": "Theirs", "attachments": [{"filename": "p.png", "stored_as": "p.png"}]}]
        """.trimIndent())
        Importing.apply(load, plan, Importing.Answers(), File(dir(load), "attachments"))
        val theirs = onDisk(load).getJSONObject(1).getJSONArray("attachments").getJSONObject(0)
        val name = theirs.getString("stored_as")
        assertNotEquals("p.png", name)
        assertTrue(name.endsWith("_p.png"))
        assertEquals("PHOTO", File(dir(load), "attachments/$name").readText())
        assertEquals("the display name stays", "p.png", theirs.getString("filename"))
    }

    @Test
    fun `an attachment path that tries to leave the folder is flattened to a bare name`() {
        val load = notebook("[]")
        val plan = backupPlan(load, """
            [{"uid": "x", "title": "t", "attachments": [
               {"filename": "a", "stored_as": "../../evil.txt"},
               {"filename": "b", "stored_as": "C:\\Windows\\system.ini"},
               {"filename": "", "stored_as": ""}]}]
        """.trimIndent())
        Importing.apply(load, plan, Importing.Answers(), File(dir(load), "attachments"))
        val atts = onDisk(load).getJSONObject(0).getJSONArray("attachments")
        assertEquals(listOf("evil.txt", "system.ini"),
            (0 until atts.length()).map { atts.getJSONObject(it).getString("stored_as") })
    }

    // ---- what a backup brings besides its notes ----

    @Test
    fun `a backup's categories join this device's, which keep their own colours`() {
        val load = notebook("""[]""", settings = """{"categories": {"General": "#111111", "Work": "#222222"}}""")
        val plan = backupPlan(load, """[{"uid": "x", "title": "t", "category": "Trips"}]""")
        val extras = Importing.Extras(categories = JSONObject("""
            {"Work": "#999999", "Trips": "#333333", "Bad": "#nope", "${"y".repeat(81)}": "#444444"}
        """.trimIndent()))
        val result = Importing.apply(load, plan, Importing.Answers(), null, extras) as Importing.Result.Done
        assertEquals(1, result.outcome.addedCategories)
        assertEquals(1, result.outcome.refusedCategories)
        val saved = JSONObject(File(dir(load), "settings.json").readText()).getJSONObject("categories")
        assertEquals("#222222", saved.getString("Work"))
        assertEquals("#333333", saved.getString("Trips"))
        assertEquals("the note keeps its category", "Trips", onDisk(load).getJSONObject(0).getString("category"))
    }

    @Test
    fun `a template comes in only when none here has its name`() {
        val load = notebook("[]")
        File(dir(load), "templates.json").writeText("""[{"name": "Weekly", "title": "mine"}]""")
        val plan = backupPlan(load, "[]")
        val extras = Importing.Extras(templates = JSONArray("""
            [{"name": "Weekly", "title": "theirs"}, {"name": "Trip", "title": "t"}, {"title": "nameless"}]
        """.trimIndent()))
        val result = Importing.apply(load, plan, Importing.Answers(), null, extras) as Importing.Result.Done
        assertEquals(1, result.outcome.addedTemplates)
        val saved = JSONArray(File(dir(load), "templates.json").readText())
        assertEquals(2, saved.length())
        assertEquals("mine", saved.getJSONObject(0).getString("title"))
    }

    @Test
    fun `re-importing your own backup does not double the Trash`() {
        val load = notebook("[]", trash = """
            [{"id": 4, "uid": "t", "title": "Gone", "body": "b", "deleted_at": "2026-10-01 10:00:00"}]
        """.trimIndent())
        val plan = backupPlan(load, "[]")
        val extras = Importing.Extras(trash = JSONArray("""
            [{"id": 9, "uid": "t", "title": "Gone", "body": "b", "deleted_at": "2026-10-01 10:00:00"},
             {"id": 5, "title": "Also gone", "deleted_at": "2026-10-02 10:00:00"}]
        """.trimIndent()))
        val result = Importing.apply(load, plan, Importing.Answers(), null, extras) as Importing.Result.Done
        assertEquals(1, result.outcome.addedTrash)
        val trash = JSONArray(File(dir(load), "trash.json").readText())
        assertEquals(2, trash.length())
        assertEquals("2026-10-02 10:00:00", trash.getJSONObject(1).getString("deleted_at"))
    }

    @Test
    fun `painted days join, and a day already painted here keeps its own`() {
        val load = notebook("[]", settings = """{"day_colors": {"2026-10-12": "#111111"}}""")
        val plan = backupPlan(load, "[]")
        val extras = Importing.Extras(
            dayColors = JSONObject("""{"2026-10-12": "#ffffff", "2026-10-13": "#222222", "2026-10-14": "red"}"""),
            dayEmoji = JSONObject("""{"2026-10-15": ["🎂\uFE0F", "", "🎁", "🔔", "💰"], "2026-10-16": "🚗"}"""),
        )
        val result = Importing.apply(load, plan, Importing.Answers(), null, extras) as Importing.Result.Done
        assertEquals(3, result.outcome.restoredDays)
        val saved = JSONObject(File(dir(load), "settings.json").readText())
        assertEquals("#111111", saved.getJSONObject("day_colors").getString("2026-10-12"))
        assertEquals("#222222", saved.getJSONObject("day_colors").getString("2026-10-13"))
        assertFalse(saved.getJSONObject("day_colors").has("2026-10-14"))
        val markers = saved.getJSONObject("day_emoji").getJSONArray("2026-10-15")
        assertEquals(listOf("🎂", "🎁", "🔔"), (0 until markers.length()).map { markers.getString(it) })
        assertEquals("🚗", saved.getJSONObject("day_emoji").getJSONArray("2026-10-16").getString(0))
    }

    @Test
    fun `an imported note keeps its place in Custom order and its stamps`() {
        val load = notebook("[]")
        val plan = backupPlan(load, """
            [{"uid": "x", "title": "t", "order": 3.5, "created": "2024-01-01 10:00:00",
              "updated": "2024-02-01 10:00:00"}]
        """.trimIndent())
        Importing.apply(load, plan, Importing.Answers(), null)
        val note = onDisk(load).getJSONObject(0)
        assertEquals(3.5, note.getDouble("order"), 0.0)
        assertEquals("2024-01-01 10:00:00", note.getString("created"))
        assertEquals("2024-02-01 10:00:00", note.getString("updated"))
    }
}
