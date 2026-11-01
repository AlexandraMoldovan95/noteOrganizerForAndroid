package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    // ---- the desktop's door ----

    @Test
    fun `a backup moving in a 0_13_0 notebook says the earlier text it kept as notes`() {
        // Its series turned off and on as it was, the blank-looking day
        // deleted: the read sets the copy's words aside, and the message said
        // only "Added 1 note." (the final audit's twenty-third round).
        val load = notebook("[]")
        val result = Importing.apply(load, backupPlan(load, """[{"id": 1, "uid": "gym", "title": "Gym",
            "body": "plan", "date": "2026-11-02", "recurrence": {"freq": "weekly"}, "exceptions": ["2026-11-09"],
            "_prev": {"title": "Gym", "body": "plan", "comments": "", "formatting": [], "paragraphs": [],
                      "date": "2026-11-02", "recurrence": {"freq": "weekly"}, "exceptions": [], "moved": {},
                      "overrides": {"2026-11-09": {"body": "knee"}}}}]"""), Importing.Answers(), null)
        val outcome = (result as Importing.Result.Done).outcome
        assertEquals(1, outcome.earlierText)
        assertTrue(importMessage(outcome), importMessage(outcome).contains("Earlier text of 1 day of a repeating note"))
        assertTrue(notes(load.file!!.readText()).any { it.getString("title") == "Gym (earlier text)" })
        // ...and an Update of the note already here keeps them too: an Add
        // kept what the arriving note's copy set aside, an Update dropped it
        // (the twenty-second round; the desktop the same).
        val here = notebook("""[{"id": 1, "uid": "gym", "title": "Gym", "body": "here", "date": "2026-11-02",
            "recurrence": {"freq": "weekly"}}]""")
        val updated = Importing.apply(here, backupPlan(here, """[{"id": 1, "uid": "gym", "title": "Gym",
            "body": "plan", "date": "2026-11-02", "recurrence": {"freq": "weekly"}, "exceptions": ["2026-11-09"],
            "_prev": {"title": "Gym", "body": "plan", "comments": "", "formatting": [], "paragraphs": [],
                      "date": "2026-11-02", "recurrence": {"freq": "weekly"}, "exceptions": [], "moved": {},
                      "overrides": {"2026-11-09": {"body": "knee"}}}}]"""), Importing.Answers(update = true), null)
        assertEquals(1, (updated as Importing.Result.Done).outcome.earlierText)
        assertTrue(notes(here.file!!.readText()).any { it.getString("title") == "Gym (earlier text)" && it.getString("body") == "knee" })
    }

    @Test
    fun `a number that is no number comes in as nothing, and the import goes on`() {
        // Android's org.json takes NaN inside an array unchecked, and its
        // toString() of everything holding it then gives null: the import's
        // copy of a backup's template with "checklist": [NaN] stopped the app.
        // This parser refuses to hold one, so it is put there as Android's would.
        val checklist = JSONArray().put(JSONObject().put("text", "milk"))
        val held = JSONArray::class.java.getDeclaredField("myArrayList").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (held.get(checklist) as MutableList<Any?>).addAll(listOf(Double.NaN, Double.POSITIVE_INFINITY))
        val templates = Files.parseOutside("[]") {
            JSONArray().put(JSONObject().put("name", "Hostile template").put("checklist", checklist))
        }
        val read = templates.getJSONObject(0).getJSONArray("checklist")
        assertEquals(listOf(JSONObject.NULL, JSONObject.NULL), listOf(read.opt(1), read.opt(2)))
        val load = notebook("[]")
        File(dir(load), "templates.json").writeText("[]")
        val result = Importing.apply(load, backupPlan(load, "[]"), Importing.Answers(), null,
            Importing.Extras(templates = templates))
        assertEquals(1, (result as Importing.Result.Done).outcome.addedTemplates)
        assertEquals(listOf("milk"), JSONArray(File(dir(load), "templates.json").readText()).getJSONObject(0)
            .getJSONArray("checklist").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("text") } })
        // And whatever else a reading misses is refused, never the app stopped.
        val unreadable = object : JSONObject() {
            override fun opt(key: String?): Any? = throw IllegalStateException("a reading missed this")
        }
        val stale = Importing.Plan(Importing.Source.CODE, 1, emptyList(), emptyList(), listOf(unreadable), 0)
        assertTrue(Importing.apply(load, stale, Importing.Answers(update = true), null) is Importing.Result.Failed)
    }

    @Test
    fun `a day filed under a category this phone lacks comes in under General, as the note does`() {
        // The desktop's clean_override files a day under one it lacks; the
        // phone refiled the note alone, and a day kept a name no Show offers.
        val note = JSONObject("""{"title": "Gym", "category": "Sport", "date": "2026-10-05",
            "recurrence": {"freq": "weekly", "weekdays": [0]},
            "overrides": {"2026-10-12": {"category": "Sport"}, "2026-10-19": {"category": "Work"}, "2026-10-26": {"body": "x"}}}""")
        val filed = Importing.fileUnder(note, setOf("General", "Work"))
        assertEquals("General", filed.getString("category"))
        val days = filed.getJSONObject("overrides")
        assertEquals("General", days.getJSONObject("2026-10-12").getString("category"))
        assertEquals("Work", days.getJSONObject("2026-10-19").getString("category"))
        assertFalse(days.getJSONObject("2026-10-26").has("category"))
    }

    @Test
    fun `styles piled too deep on one line are dropped at the door, as the desktop's clamp drops them`() {
        // The desktop's clamp_spans_per_line, run on these same cases, kept
        // the first 500, 500, 200 and 500.
        fun clamp(body: String, spans: List<FormatSpan>) = Normalize.clampSpansPerLine(body, spans)
        val oneLine = List(600) { FormatSpan(0, 1, "bold") }
        assertEquals(500, clamp("x".repeat(10), oneLine).size)
        val five = List(5) { "abcd" }.joinToString("\n")
        assertEquals(500, clamp(five, List(600) { FormatSpan(0, five.length, "bold") }).size)
        val hundred = List(100) { "ab" }.joinToString("\n")
        assertEquals("the whole note's ranges capped", 200, clamp(hundred, List(300) { FormatSpan(0, hundred.length, "bold") }).size)
        val ten = List(10) { "abc" }.joinToString("\n")
        val toTheEnd = (0 until 10).flatMap { i -> List(60) { FormatSpan(i * 4, ten.length, "bold") } }
        assertEquals("counted where they cover, not where they start", toTheEnd.take(500), clamp(ten, toTheEnd))
        val few = listOf(FormatSpan(0, 2, "bold"), FormatSpan(3, 5, "italic"))
        assertEquals("a note a person writes is unchanged", few, clamp("ab\ncd", few))
    }

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
        assertEquals(listOf("a", "c"), plan.differing.map { it.uid })
        assertEquals(listOf("b"), plan.unchanged.map { it.uid })
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

    @Test
    fun `the update question knows which notes hold a rescue copy an update would replace`() {
        // One copy a note: an update's takes the place of a big delete's,
        // and the question said only that the update could be undone.
        val load = notebook("""
            [{"id": 1, "uid": "recipe", "title": "Recipe", "body": "flour", "_prev": {"title": "Recipe", "body": "flour and 370 letters"}},
             {"id": 2, "uid": "spent", "title": "Spent", "body": "same", "_prev": {"title": "Spent", "body": "same"}},
             {"id": 3, "uid": "plain", "title": "Plain", "body": "p"}]
        """.trimIndent(), trash = "[]")
        val plan = Importing.plan(load.raw!!, notes("""
            [{"uid": "recipe", "title": "Recipe", "body": "flour, salt"}, {"uid": "spent", "title": "Spent", "body": "other"},
             {"uid": "plain", "title": "Plain", "body": "q"}]
        """.trimIndent()), Importing.Source.CODE)
        assertEquals(3, plan.differing.size)
        assertEquals(1, plan.rescued)
        assertTrue(Notebook.holdsUnspentCopy(load.raw!!.getJSONObject(0)))
        assertFalse(Notebook.holdsUnspentCopy(load.raw!!.getJSONObject(1)))
        assertFalse(Notebook.holdsUnspentCopy(load.raw!!.getJSONObject(2)))
    }

    @Test
    fun `a backup that only brings a file is not said to replace the note's earlier version`() {
        // Its words the same as here, a file this phone lacks: the update
        // adds the file and leaves the rescue copy alone, and the question
        // said the copy would be replaced.
        val load = notebook("""
            [{"id": 1, "uid": "trip", "title": "Trip", "body": "passport",
              "_prev": {"title": "Trip", "body": "passport, tickets and 370 more letters"}}]
        """.trimIndent())
        val unpacked = NioFiles.createTempDirectory("unpacked").toFile()
        File(unpacked, "map.png").writeText("MAP")
        val plan = backupPlan(load, """
            [{"uid": "trip", "title": "Trip", "body": "passport",
              "attachments": [{"filename": "map.png", "stored_as": "map.png"}]}]
        """.trimIndent())
        assertEquals("the file is a difference", 1, plan.differing.size)
        assertEquals(0, plan.rescued)
        assertTrue(Importing.apply(load, plan, Importing.Answers(update = true), File(dir(load), "attachments"),
            Importing.Extras(attachments = unpacked)) is Importing.Result.Done)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("the copy left as it was", "passport, tickets and 370 more letters",
            note.getJSONObject("_prev").getString("body"))
        assertEquals("map.png", note.getJSONArray("attachments").getJSONObject(0).getString("stored_as"))
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
    fun `a backup's attached files are moved into place, never copied a second time`() {
        // Copied on the screen's thread, a notebook of photos held it still
        // for seconds; moved, a file costs nothing however large.
        val load = notebook("[]")
        val unpacked = NioFiles.createTempDirectory("unpacked").toFile()
        File(unpacked, "photo.jpg").writeText("PHOTO")
        val plan = backupPlan(load, """
            [{"uid": "p1", "title": "One", "attachments": [{"filename": "photo.jpg", "stored_as": "photo.jpg"}]},
             {"uid": "p2", "title": "Two", "attachments": [{"filename": "photo.jpg", "stored_as": "photo.jpg"}]}]
        """.trimIndent())
        assertTrue(Importing.apply(load, plan, Importing.Answers(), File(dir(load), "attachments"),
            Importing.Extras(attachments = unpacked)) is Importing.Result.Done)
        assertFalse("moved out of the unpacked backup", File(unpacked, "photo.jpg").exists())
        assertEquals("PHOTO", File(dir(load), "attachments/photo.jpg").readText())
        // The second note naming it still gets a file of its own.
        val second = onDisk(load).getJSONObject(1).getJSONArray("attachments").getJSONObject(0).getString("stored_as")
        assertNotEquals("photo.jpg", second)
        assertEquals("PHOTO", File(dir(load), "attachments/$second").readText())
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

    @Test
    fun `a note sent to the Trash while a backup is planned does not turn Update onto another note`() {
        // The plan is made away from the screen, against the list as it was
        // at Continue; the list stays usable meanwhile. Applied by place,
        // the Trash in between moved Diary's place onto the PIN note, and
        // Update rewrote that one with Diary's words.
        val load = notebook("""
            [{"id": 1, "uid": "u-shopping", "title": "Shopping", "body": "milk", "updated": "2026-10-01 09:00:00"},
             {"id": 2, "uid": "u-diary", "title": "Diary", "body": "old diary entry", "updated": "2026-10-01 09:00:00"},
             {"id": 3, "uid": "u-pin", "title": "Bank PIN hint", "body": "the old street", "updated": "2026-10-01 09:00:00"}]
        """.trimIndent(), trash = "[]")
        val atContinue = JSONArray().also { copy -> load.raw!!.let { for (i in 0 until it.length()) copy.put(it.opt(i)) } }
        val plan = Importing.plan(atContinue, notes("""
            [{"uid": "u-diary", "title": "Diary", "body": "old diary entry and a page written on the computer",
              "updated": "2026-10-02 09:00:00"},
             {"title": "Bank PIN hint", "body": "the old street"},
             {"title": "Diary", "body": "old diary entry"}]
        """.trimIndent()), Importing.Source.BACKUP)
        assertTrue(Organizing.trashNotes(load, listOf(1)) is SaveResult.Ok)
        val result = Importing.apply(load, plan, Importing.Answers(update = true, skipDuplicates = true),
            File(dir(load), "attachments")) as Importing.Result.Done
        fun byUid(uid: String) = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .single { it.optString("uid") == uid }
        assertEquals("Bank PIN hint", byUid("u-pin").getString("title"))
        assertEquals("the old street", byUid("u-pin").getString("body"))
        assertFalse("nothing of it was replaced", byUid("u-pin").has("_prev"))
        assertEquals("old diary entry and a page written on the computer", byUid("u-diary").getString("body"))
        assertEquals(1, result.outcome.updated)
        // Look-alikes weighed against the list as it is: the PIN note is
        // still here, so its twin is skipped; the old Diary is being
        // replaced, so its twin is not a duplicate of anything.
        assertEquals(1, result.outcome.duplicatesSkipped)
        assertEquals(1, result.outcome.added)
        assertEquals(listOf("Diary", "Bank PIN hint", "Diary"),
            onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("title") } })
    }

    @Test
    fun `the note an update was for, sent to the Trash while it was planned, comes back as a new note`() {
        // As the plan would have sorted it a moment later: nothing here
        // wears its identity any more, so nothing here is replaced by it.
        val load = notebook("""
            [{"id": 1, "uid": "u-diary", "title": "Diary", "body": "old"},
             {"id": 2, "uid": "u-pin", "title": "Bank PIN hint", "body": "the old street"},
             {"id": 3, "uid": "u-same", "title": "Same", "body": "same"}]
        """.trimIndent(), trash = "[]")
        val atContinue = JSONArray(load.raw!!.toString())
        val plan = Importing.plan(atContinue, notes("""
            [{"uid": "u-diary", "title": "Diary", "body": "new"}, {"uid": "u-same", "title": "Same", "body": "same"}]
        """.trimIndent()), Importing.Source.BACKUP)
        assertEquals(1, plan.differing.size)
        assertEquals(1, plan.unchanged.size)
        assertTrue(Organizing.trashNotes(load, listOf(1, 3)) is SaveResult.Ok)
        val result = Importing.apply(load, plan, Importing.Answers(update = true),
            File(dir(load), "attachments")) as Importing.Result.Done
        assertEquals(0, result.outcome.updated)
        assertEquals(2, result.outcome.added)
        assertEquals(0, result.outcome.unchangedSkipped)
        val titles = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals(listOf("Bank PIN hint" to "the old street", "Diary" to "new", "Same" to "same"),
            titles.map { it.getString("title") to it.getString("body") })
        assertEquals("the arriving copy keeps its identity", "u-diary", titles[1].getString("uid"))
    }

    @Test
    fun `two arriving notes with one identity update one note once, the second coming in as its own`() {
        // Both claimed the note here: the question counted two, Update ran
        // twice on it, and the second's rescue copy was the first arrival --
        // the phone's own words in no copy.
        val load = notebook("""[{"id": 1, "uid": "dup", "title": "Groceries", "body": "MY OWN WORDS typed on the phone"}]""",
            trash = "[]")
        val plan = Importing.plan(load.raw!!, notes("""
            [{"uid": "dup", "title": "Groceries", "body": "version A from the code"},
             {"uid": "dup", "title": "Groceries", "body": "version B from the code"}]
        """.trimIndent()), Importing.Source.CODE)
        assertEquals("one note here, one question", 1, plan.differing.size)
        assertEquals(1, plan.fresh.size)
        assertFalse(plan.fresh.single().has("uid"))
        val result = Importing.apply(load, plan, Importing.Answers(update = true),
            File(dir(load), "attachments")) as Importing.Result.Done
        assertEquals(1, result.outcome.updated)
        assertEquals(1, result.outcome.added)
        val after = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals("version A from the code", after[0].getString("body"))
        assertEquals("the phone's words in the copy", "MY OWN WORDS typed on the phone",
            after[0].getJSONObject("_prev").getString("body"))
        assertEquals("version B from the code", after[1].getString("body"))
        assertTrue(after[1].getString("uid") != "dup")
        // Two new notes with one identity: both added, each its own.
        val fresh = notebook("[]", trash = "[]")
        val pair = Importing.plan(fresh.raw!!, notes("""
            [{"uid": "dup2", "title": "First", "body": "1"}, {"uid": "dup2", "title": "Second", "body": "2"}]
        """.trimIndent()), Importing.Source.CODE)
        Importing.apply(fresh, pair, Importing.Answers(), File(dir(fresh), "attachments"))
        val uids = onDisk(fresh).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("uid") } }
        assertEquals(2, uids.toSet().size)
        assertEquals("dup2", uids[0])
    }

    @Test
    fun `a note restored from the Trash while a backup is planned does not come in a second time wearing its identity`() {
        // Planned as new -- it was in the Trash at Continue -- and back by
        // the time of apply: added wearing the same uid, nobody asked.
        val load = notebook("""[{"id": 1, "uid": "other", "title": "Other", "body": "o"}]""",
            trash = """[{"id": 2, "uid": "trip", "title": "Trip", "body": "the phone's", "deleted_at": "2026-10-28 10:00:00"}]""")
        val plan = Importing.plan(JSONArray(load.raw!!.toString()), notes("""
            [{"uid": "trip", "title": "Trip", "body": "the backup's"}]
        """.trimIndent()), Importing.Source.BACKUP)
        assertEquals(1, plan.fresh.size)
        assertTrue(Organizing.restore(load, listOf(Organizing.TrashKey(2, "2026-10-28 10:00:00", "trip"))) is SaveResult.Ok)
        Importing.apply(load, plan, Importing.Answers(), File(dir(load), "attachments")) as Importing.Result.Done
        val after = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals(listOf("Other", "Trip", "Trip"), after.map { it.getString("title") })
        assertEquals(3, after.map { it.getString("uid") }.toSet().size)
        assertEquals("trip", after.single { it.getString("body") == "the phone's" }.getString("uid"))
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
    fun `look-alikes are counted once for each answer, however often the questions ask`() {
        // Asked from the questions' drawing, two or three times and at every
        // redraw, each time normalizing every note of both notebooks.
        var counted = 0
        val alikes = Importing.LookAlikes { update -> counted += 1; if (update) 2 else 3 }
        repeat(5) {
            assertEquals(3, alikes.of(false))
            assertEquals(2, alikes.of(true))
        }
        assertEquals(2, counted)
        // And the real count is the one lookAlikes gives.
        val load = notebook("""[{"uid": "a", "title": "Milk", "body": "two"}]""")
        val plan = backupPlan(load, """[{"uid": "b", "title": "Milk", "body": "two"}]""")
        assertEquals(Importing.lookAlikes(load.raw!!, plan, false),
                     Importing.LookAlikes(load.raw!!, plan).warm(plan).of(false))
        assertEquals(1, Importing.LookAlikes(load.raw!!, plan).of(false))
    }

    @Test
    fun `a backup's Trash window comes with its Trash where none was ever chosen, and only there`() {
        // Restored onto a new phone, entries kept for 90 days were emptied
        // at the next start by the default 30 nobody had chosen.
        val window = { load: Notebook.Load ->
            Importing.apply(load, backupPlan(load, "[]"), Importing.Answers(), null,
                Importing.Extras(trashWindow = 90, trash = JSONArray(TRASHED_LONG_AGO)))
            File(dir(load), "settings.json").takeIf { it.isFile }
                ?.let { JSONObject(it.readText()).opt(Settings.RETENTION_DAYS) }
        }
        val fresh = notebook("[]")
        assertEquals(90, window(fresh))
        assertEquals("the next start purges by it", 90, fresh.settings?.let { Settings.purgeWindow(it) })
        assertEquals("a window chosen here is kept", 14, window(notebook("[]", settings = """{"trash_retention_days": 14}""")))
        // Lost with a file that could not be read: chosen once, only not known.
        assertNull(window(notebook("[]", settings = """{"phone_trash_window_unknown": true}""")))
    }

    @Test
    fun `a backup never shortens the Trash window, nor sets one with no Trash to keep by it`() {
        // A backup from a 7-day notebook, with no Trash at all, set this
        // phone's window to 7, and the next start emptied the phone's own
        // Trash -- kept under the 30 days promised -- for good, files and all.
        val mine = """[{"uid": "t1", "title": "Mine", "body": "kept", "deleted_at": "%s"}]"""
            .format(java.time.LocalDateTime.now().minusDays(12).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        val window = { days: Int, trash: String? ->
            val load = notebook("[]", trash = mine)
            Importing.apply(load, backupPlan(load, "[]"), Importing.Answers(), null,
                Importing.Extras(trashWindow = days, trash = trash?.let { JSONArray(it) }))
            Settings.purgeWindow(Settings.load(dir(load)))
        }
        for (days in listOf(7, 14, 29, 30)) {
            // The phone's own entry, deleted 12 days ago, stays past the next start.
            assertEquals("a backup's $days days leave the default", 30, window(days, TRASHED_LONG_AGO))
        }
        assertEquals("a longer window with no Trash in the backup is not taken", 30, window(90, null))
        assertEquals("... nor with an empty one", 30, window(90, "[]"))
        assertEquals("a longer one with its Trash is", 31, window(31, TRASHED_LONG_AGO))
    }

    private companion object {
        const val TRASHED_LONG_AGO = """[{"uid": "old-t", "title": "Theirs", "body": "x", "deleted_at": "2026-01-02 10:00"}]"""
    }

    @Test
    fun `only a whole number of days from 1 to 365 is taken from a backup's settings`() {
        val taken = { settings: String ->
            Backup.Contents(emptyList(), null, null, JSONObject(settings), null).extras().trashWindow
        }
        assertEquals(90, taken("""{"trash_retention_days": 90}"""))
        assertEquals(365, taken("""{"trash_retention_days": 365}"""))
        for (bad in listOf("0", "366", "7.5", "\"90\"", "-3", "null", "true")) {
            assertNull(bad, taken("""{"trash_retention_days": $bad}"""))
        }
        assertNull(taken("{}"))
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
