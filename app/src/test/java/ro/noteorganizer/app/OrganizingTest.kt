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

    private fun notebook(notes: String, trash: String? = null, settings: String? = null, templates: String? = null) =
        scratch(notes, trash, settings, templates, attachments = true)

    private fun attachments(load: Notebook.Load) = File(dirOf(load), "attachments")
    private fun titles(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it).optString("title") }

    // ---- duplicate ----

    @Test
    fun `every text that says what a duplicate is names each part of the key`() {
        // Settings → Duplicates once named four of the six parts while the
        // import's questions named all six.
        val phrase = "same title, text, comments, category, checklist, repeat rule and a series' own days"
        val parts = phrase.removePrefix("same ").replace(" and ", ", ").split(", ")
        // ...but the key's last part, the fields a version does not know, which
        // no person sees and the words do not name (UnknownFieldsTest checks it).
        assertEquals(Normalize.dupKey(JSONObject()).split("\u0001").size, parts.size + 1)
        val strings = File("src/main/res/values/strings.xml").readText()
        for (name in listOf("dup_explain", "q_duplicates_text", "q_duplicates_one")) {
            val text = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)
                ?.replace("\\'", "'")
            assertTrue("$name says: $text", text != null && phrase in text)
        }
    }

    @Test
    fun `a duplicate is a separate note, with files of its own`() {
        val load = notebook("""
            [{"id": 4, "uid": "u", "title": "Trip", "body": "b", "created": "2020-01-01 09:00:00",
              "attachments": [{"filename": "map.png", "stored_as": "map.png"}],
              "_prev": {"title": "older"}}]
        """.trimIndent())
        File(attachments(load), "map.png").writeText("MAP")
        val result = Organizing.duplicate(load, 4, attachments(load)) as SaveResult.Ok
        assertEquals(5, result.id)
        val copy = onDisk(load).getJSONObject(1)
        assertEquals("Trip (copy)", copy.getString("title"))
        assertEquals("made now, as the desktop's", copy.getString("updated"), copy.getString("created"))
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
        assertEquals("(copy)", onDisk(load).getJSONObject(1).getString("title"))
    }

    // ---- several at once, and Undo ----

    @Test
    fun `several notes go to the Trash together, and Undo brings exactly those back`() {
        val load = notebook("""[{"id": 1, "title": "a"}, {"id": 2, "title": "b"}, {"id": 3, "title": "c"}]""")
        val result = Organizing.trashNotes(load, listOf(1, 3)) as SaveResult.Ok
        assertEquals(2, result.imported)
        assertEquals(listOf("b"), titles(onDisk(load)))
        assertEquals(listOf("a", "c"), titles(trashOnDisk(load)))

        val keys = Notebook.load(dirOf(load)).trash.map { Organizing.TrashKey(it.id, it.deletedAt) }
        val back = Organizing.restore(Notebook.load(dirOf(load)), keys) as SaveResult.Ok
        assertEquals(2, back.imported)
        assertEquals(listOf("b", "a", "c"), titles(onDisk(load)))
        assertEquals(0, trashOnDisk(load).length())
    }

    @Test
    fun `restored notes get fresh ids, because their old ones may be taken`() {
        val load = notebook("""[{"id": 1, "title": "new 1"}]""",
            trash = """[{"id": 1, "title": "old 1", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(1, "2026-10-01 10:00:00")))
        val ids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getInt("id") }
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
        val uids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getString("uid") }
        assertEquals("x", uids[0])
        assertNotEquals("the restored twin gets an identity of its own", "x", uids[1])
        assertEquals("a uid nobody else has is kept, so an older code still finds it", "y", uids[2])
    }

    @Test
    fun `a note restored with no uid is given one, as the desktop's restore gives it`() {
        // Kept empty, the list knew it by its id ("id:N"), and a note given that
        // id later took its place in a selection.
        val load = notebook("""[{"id": 1, "uid": "x", "title": "here"}]""",
            trash = """[{"id": 3, "title": "no identity", "deleted_at": "2026-10-01 10:00:00"},
                        {"id": 4, "uid": "  ", "title": "blank identity", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(3, "2026-10-01 10:00:00", ""),
                                        Organizing.TrashKey(4, "2026-10-01 10:00:00", "  ")))
        val uids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).optString("uid") }
        assertEquals(3, uids.size)
        assertTrue("each has one of its own: $uids", uids.drop(1).all { it.isNotBlank() && it != "x" } && uids[1] != uids[2])
    }

    @Test
    fun `a restored uid is kept stripped, and one a note wears stripped is worn`() {
        // The desktop's normalize_note strips a uid: " y " came back "y" there
        // and " y " here, and a note wearing " x " did not stop "x" coming back.
        val load = notebook("""[{"id": 1, "uid": " x ", "title": "here"}]""",
            trash = """[{"id": 3, "uid": " y ", "title": "padded", "deleted_at": "2026-10-01 10:00:00"},
                        {"id": 4, "uid": "x", "title": "twin", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(3, "2026-10-01 10:00:00", " y "),
                                        Organizing.TrashKey(4, "2026-10-01 10:00:00", "x")))
        val uids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).optString("uid") }
        assertEquals("y", uids[1])
        assertNotEquals("the twin of a note wearing \" x \" gets its own", "x", uids[2])
    }

    @Test
    fun `a note restored with no stamps is stamped as the desktop stamps it`() {
        // normalize_note gives an empty "updated" the moment, and "created"
        // the same: kept empty, the note sorted oldest here and newest there.
        val load = notebook("""[{"id": 1, "uid": "x", "title": "here"}]""",
            trash = """[{"id": 3, "uid": "s", "title": "unstamped", "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(3, "2026-10-01 10:00:00", "s")))
        val back = onDisk(load).getJSONObject(1)
        assertTrue("updated: ${back.optString("updated")}", back.optString("updated").startsWith("20"))
        assertEquals(back.optString("updated"), back.optString("created"))
    }

    @Test
    fun `a restored stamp of spaces is kept, as the desktop keeps it`() {
        // normalize_note asks only whether the stamp is empty.
        val load = notebook("""[{"id": 1, "uid": "x", "title": "here"}]""",
            trash = """[{"id": 3, "uid": "s", "title": "spaced", "updated": "   ", "created": "\u0085",
                        "deleted_at": "2026-10-01 10:00:00"}]""")
        Organizing.restore(load, listOf(Organizing.TrashKey(3, "2026-10-01 10:00:00", "s")))
        val back = onDisk(load).getJSONObject(1)
        assertEquals("   ", back.optString("updated"))
        assertEquals("\u0085", back.optString("created"))
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
        assertEquals(listOf("second"), titles(onDisk(load)))
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
        assertEquals(listOf(1, 4, 3, 5, 6), (0..4).map { onDisk(load).getJSONObject(it).get("id") })
        assertFalse("a notebook already right is not written", Organizing.repairIds(Notebook.load(dirOf(load))))
    }

    @Test
    fun `an id written as text is read as the desktop's int of its stripped text reads it, a float as none`() {
        // toIntOrNull gave "1_0" and a digit of another script no id, so the
        // next free number, and a float kept its whole part, which Python's
        // int("7.5") refuses. The desktop's normalize_note and repair_note_ids answer.
        val load = notebook("""[{"id": "1_0", "title": "a"}, {"id": "\u0663", "title": "b"},
                                {"id": "\u001c7", "title": "c"}, {"id": 7.5, "title": "d"},
                                {"id": "\u20285", "title": "e"}, {"id": " 2 ", "title": "f"}]""")
        assertTrue(Organizing.repairIds(load))
        assertEquals(listOf(10, 3, 7, 11, 5, 2), (0..5).map { onDisk(load).getJSONObject(it).get("id") })
        val float = notebook("""[{"id": 3, "title": "a"}, {"id": 7.5, "title": "b"}]""")
        assertTrue(Organizing.repairIds(float))
        assertEquals("a float is no id: the next free number", listOf(3, 4),
                     (0..1).map { onDisk(float).getJSONObject(it).get("id") })
        val whole = notebook("""[{"id": 6, "title": "a"}, {"id": 7.0, "title": "b"}]""")
        assertTrue("a float id is written again as the whole number it is given", Organizing.repairIds(whole))
        assertEquals(listOf(6, 7), (0..1).map { onDisk(whole).getJSONObject(it).get("id") })
    }

    @Test
    fun `an id an Int cannot hold takes a new number, and no new note's id wraps onto another's`() {
        // Held at 2,147,483,647, the next new note's id wrapped to -2,147,483,648
        // and every new note after took it; past an Int, 4,294,967,301 read as
        // 5. The desktop keeps such an id: Python's numbers hold it, the phone's cannot.
        val past = notebook("""[{"id": 1, "title": "a"}, {"id": "99999999999", "title": "b"},
                                {"id": 4294967301, "title": "c"}, {"id": 5, "title": "d"}]""")
        assertTrue(Organizing.repairIds(past))
        assertEquals(listOf(1, 6, 7, 5), (0..3).map { onDisk(past).getJSONObject(it).get("id") })
        // A note at the edge keeps its number; every new one takes the lowest
        // number no note holds, never a negative one, never one in use.
        val edge = notebook("""[{"id": 2147483647, "title": "a"}, {"id": 1, "title": "b"}, {"id": "x", "title": "c"}]""")
        assertTrue(Organizing.repairIds(edge))
        assertEquals(listOf(2147483647, 1, 2), (0..2).map { onDisk(edge).getJSONObject(it).get("id") })
        val load = Notebook.load(dirOf(edge))
        val made = listOf(
            (Notebook.createNote(load, "new", "one") as SaveResult.Ok).id,
            (Notebook.createNote(load, "new", "two") as SaveResult.Ok).id,
            (Organizing.duplicate(load, 1, attachments(load)) as SaveResult.Ok).id,
        )
        assertEquals(listOf(3, 4, 5), made)
        val ids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getInt("id") }
        assertEquals("one id a note", ids.size, ids.toSet().size)
        assertFalse(Organizing.repairIds(Notebook.load(dirOf(load))))
        // A note shaped as a load shapes it reads such an id as none, never cut down onto another's.
        assertEquals(0, Normalize.normalizeNote(JSONObject().put("id", 4_294_967_301L)).getInt("id"))
        assertEquals(5, Normalize.normalizeNote(JSONObject().put("id", 5)).getInt("id"))
    }

    @Test
    fun `a pin is not an edit, its note's updated time stays, as the desktop's toggle_pin`() {
        val load = notebook("""[{"id": 1, "updated": "2026-01-01 00:00:00"}, {"id": 2, "updated": "2026-01-01 00:00:00"}]""")
        Organizing.setPinned(load, listOf(1, 2), true)
        assertEquals(listOf("2026-01-01 00:00:00", "2026-01-01 00:00:00"),
                     (0..1).map { onDisk(load).getJSONObject(it).getString("updated") })
        // A pin from the note's details, the same.
        val one = notebook("""[{"id": 1, "title": "t", "body": "b", "updated": "2026-01-01 00:00:00"}]""")
        Notebook.updateNote(one, 1, "t", "b", pinned = true)
        assertEquals("2026-01-01 00:00:00", onDisk(one).getJSONObject(0).getString("updated"))
        assertTrue(onDisk(one).getJSONObject(0).getBoolean("pinned"))
    }

    @Test
    fun `pin and category apply to every chosen note, and to no other`() {
        val load = notebook("""[{"id": 1}, {"id": 2}, {"id": 3}]""")
        Organizing.setPinned(load, listOf(1, 2), true)
        Organizing.moveToCategory(load, listOf(2, 3), "Work")
        val all = onDisk(load)
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
        val note = onDisk(load).getJSONObject(0)
        assertEquals("Job", note.getString("category"))
        assertEquals("Job", note.getJSONObject("_prev").getString("category"))
        assertEquals("Job", note.getJSONObject("overrides").getJSONObject("2026-10-12").getString("category"))
        assertEquals("Job", trashOnDisk(load).getJSONObject(0).getString("category"))
        assertEquals("Job", JSONArray(File(dirOf(load), "templates.json").readText()).getJSONObject(0).getString("category"))
        val categories = JSONObject(File(dirOf(load), "settings.json").readText()).getJSONObject("categories")
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
        assertTrue(Organizing.renameCategory(Notebook.load(dirOf(load)), "Trips", "Personal") is SaveResult.Failed)
    }

    @Test
    fun `deleting a category moves its notes to General, or to the Trash when asked`() {
        val settings = """{"categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}"""
        val keep = notebook("""[{"id": 1, "category": "Work"}]""", settings = settings)
        Organizing.deleteCategory(keep, "Work", trashNotes = false)
        assertEquals("General", onDisk(keep).getJSONObject(0).getString("category"))
        assertFalse(JSONObject(File(dirOf(keep), "settings.json").readText()).getJSONObject("categories").has("Work"))

        val trash = notebook("""
            [{"id": 1, "category": "Work"},
             {"id": 2, "category": "General", "recurrence": {"freq": "weekly"},
              "overrides": {"2026-10-12": {"category": "Work"}}}]
        """.trimIndent(), settings = settings)
        assertEquals(1 to 1, Organizing.categoryCounts(trash, "Work"))
        Organizing.deleteCategory(trash, "Work", trashNotes = true)
        assertEquals("only the note filed there went", 1, onDisk(trash).length())
        val series = onDisk(trash).getJSONObject(0)
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
    fun `a Trash read refused at the start keeps its window from being chosen, as held settings do`() {
        // Held, the Trash read empty: a shorter window asked nothing, and the next
        // start took the old notes and their files unasked.
        val load = notebook("[]", trash = """[{"id": 1, "deleted_at": "${daysAgo(10)}"}]""",
                            settings = """{"trash_retention_days": 30}""")
        assertNull("nothing held, the window can be chosen", Organizing.windowHeldBy(load))
        try {
            Files.holdWrites(load.trashFile!!)
            assertEquals(load.trashFile, Organizing.windowHeldBy(load))
            Files.newStart()
            Files.holdWrites(load.settings!!.file)
            assertEquals(load.settings!!.file, Organizing.windowHeldBy(load))
        } finally {
            Files.newStart()
        }
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
    fun `with the notes file set aside unreadable, deleting for good keeps every file, as the desktop's`() {
        // The notes this run never read may use the Trash's files: the desktop
        // keeps every file until a run that has read them.
        val load = notebook("""[{"id": 1, "title": "unread", "attachments": [{"filename": "s", "stored_as": "shared.png"}]""",
            trash = """[{"id": 2, "title": "x", "deleted_at": "2026-10-01 10:00:00",
                        "attachments": [{"filename": "s", "stored_as": "shared.png"}]}]""")
        File(attachments(load), "shared.png").writeText("S")
        Organizing.deleteForever(load, listOf(Organizing.TrashKey(2, "2026-10-01 10:00:00")), attachments(load))
        assertEquals("the entry went", 0, trashOnDisk(load).length())
        assertTrue("its file, which an unread note may use, stayed", File(attachments(load), "shared.png").exists())
        // ...for the rest of the run: an import writes the notes file and reloads
        // it, and that read cleared the mark.
        load.file!!.writeText("""[{"id": 5, "title": "imported since"}]""")
        File(dirOf(load), "trash.json").writeText("""[{"id": 3, "title": "y", "deleted_at": "2026-10-02 10:00:00",
            "attachments": [{"filename": "s", "stored_as": "shared.png"}]}]""")
        val reloaded = Notebook.load(dirOf(load))
        assertFalse("and Empty Trash names no files it keeps", Organizing.emptyTrashTakesFiles(reloaded))
        Organizing.deleteForever(reloaded, listOf(Organizing.TrashKey(3, "2026-10-02 10:00:00")), attachments(reloaded))
        assertTrue("kept after a reload in the same run", File(attachments(reloaded), "shared.png").exists())
    }

    @Test
    fun `Empty Trash names attached files only where they go`() {
        val withFile = notebook("[]", trash = """[{"id": 2, "title": "x", "deleted_at": "2026-10-01 10:00:00",
            "attachments": [{"filename": "a", "stored_as": "a.png"}]}]""")
        assertTrue(Organizing.emptyTrashTakesFiles(withFile))
        val without = notebook("[]", trash = """[{"id": 2, "title": "x", "deleted_at": "2026-10-01 10:00:00"}]""")
        assertFalse("none held", Organizing.emptyTrashTakesFiles(without))
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
        var rows = onDisk(load).getJSONObject(0).getJSONArray("checklist")
        assertEquals(listOf("b", "a", "c"), (0..2).map { rows.getJSONObject(it).getString("text") })
        assertTrue(rows.getJSONObject(0).getBoolean("done"))
        // Dragged two places down, a row SLIDES: the ones it passes move up
        // one each, rather than the first and last trading places.
        assertTrue(Notebook.moveChecklistItem(load, 1, 0, 2) is SaveResult.Ok)
        rows = onDisk(load).getJSONObject(0).getJSONArray("checklist")
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
        // "Follow the phone" was taken away; the note went on promising it.
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
            onDisk(load).getJSONObject(0).getJSONArray("paragraphs").length())
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
    fun `a series' own days keep two copies apart -- the clean-up merges none of them`() {
        // A copy holding a deleted, an edited or a moved day grouped with one
        // that did not, and the clean-up could drop it.
        val series = """"title": "Gym", "body": "b", "date": "2026-01-05", "recurrence": {"freq": "weekly"}"""
        val load = notebook("""
            [{"id": 1, $series},
             {"id": 2, $series, "exceptions": ["2026-01-12"]},
             {"id": 3, $series, "overrides": {"2026-01-12": {"body": "legs"}}},
             {"id": 4, $series, "moved": {"2026-01-12": "2026-01-13"}},
             {"id": 5, $series, "overrides": {}}]
        """.trimIndent())
        assertEquals(listOf(listOf(1, 5)), Organizing.duplicateGroups(load))
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
        val left = onDisk(load)
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
        val left = onDisk(load)
        assertEquals(1, left.length())
        assertEquals(2, left.getJSONObject(0).getInt("id"))
        assertEquals("the first copy's file went to the one kept", 1, left.getJSONObject(0).getJSONArray("attachments").length())
    }

    @Test
    fun `the copy kept takes files up to the ones a note holds, the rest left with the copy in the Trash`() {
        // Past them the list hid the rest, and the Trash's purge took them;
        // the import's adoptFiles stops there too.
        fun files(prefix: String, count: Int) = JSONArray((0 until count).map {
            JSONObject().put("filename", "$prefix$it").put("stored_as", "$prefix$it.txt") })
        val notes = JSONArray()
            .put(JSONObject().put("id", 1).put("title", "Trip").put("body", "b")
                .put("attachments", files("k", Normalize.MAX_ATTACHMENTS - 1)))
            .put(JSONObject().put("id", 2).put("title", "Trip").put("body", "b").put("attachments", files("x", 3)))
        val load = notebook(notes.toString())
        for (i in 0 until 3) File(attachments(load), "x$i.txt").writeText("X$i")
        assertTrue(Organizing.cleanUpDuplicates(load, listOf(listOf(1, 2)), setOf(2), attachments(load)) is SaveResult.Ok)
        assertEquals(Normalize.MAX_ATTACHMENTS, onDisk(load).getJSONObject(0).getJSONArray("attachments").length())
        assertEquals(3, trashOnDisk(load).getJSONObject(0).getJSONArray("attachments").length())
    }

    @Test
    fun `a file the copy kept already holds is not merged again, as Duplicate copies it under a new name`() {
        // Cleaning up the app's own Duplicate gave the kept note every file
        // twice.
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "b", "attachments": [{"filename": "map.png", "stored_as": "a_map.png"}]},
             {"id": 2, "title": "Trip (copy)", "body": "b", "attachments": [
               {"filename": "map.png", "stored_as": "b_map.png"}, {"filename": "map.png", "stored_as": "c_map.png"}]}]
        """.trimIndent())
        File(attachments(load), "a_map.png").writeText("MAP")
        File(attachments(load), "b_map.png").writeText("MAP")
        File(attachments(load), "c_map.png").writeText("PAM")      // as long, other bytes
        assertTrue(Organizing.cleanUpDuplicates(load, listOf(listOf(1, 2)), setOf(2), attachments(load)) is SaveResult.Ok)
        val kept = onDisk(load).getJSONObject(0).getJSONArray("attachments")
        assertEquals("the same bytes once; the same name with other bytes is another file",
                     listOf("MAP", "PAM"),
                     (0 until kept.length()).map { File(attachments(load), kept.getJSONObject(it).getString("stored_as")).readText() })
    }

    @Test
    fun `only a copy in a group goes -- a tick left on a note no longer one is ignored`() {
        val load = notebook("""
            [{"id": 1, "title": "Trip", "body": "b"}, {"id": 2, "title": "Trip", "body": "b"},
             {"id": 3, "title": "Plan", "body": "p"}]
        """.trimIndent())
        assertTrue(Organizing.cleanUpDuplicates(load, listOf(listOf(1, 2)), setOf(2, 3), attachments(load)) is SaveResult.Ok)
        val left = onDisk(load)
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
        val atts = onDisk(load).getJSONObject(0).getJSONArray("attachments")
        assertEquals(2, atts.length())
        val stored = (0 until 2).map { atts.getJSONObject(it).getString("stored_as") }
        assertNotEquals(stored[0], stored[1])
        assertEquals(listOf("MAP", "MAP 2"), stored.map { File(attachments(load), it).readText() })
        assertEquals("map.png", atts.getJSONObject(0).getString("filename"))
        assertNotEquals("2026-10-01 09:00:00", onDisk(load).getJSONObject(0).getString("updated"))
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
        assertEquals(0, onDisk(load).getJSONObject(0).optJSONArray("attachments")?.length() ?: 0)
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
        val entry = JSONArray(File(dirOf(load), "trash.json").readText()).getJSONObject(0)
        assertTrue(Organizing.restore(load, listOf(Organizing.TrashKey(2, entry.getString("deleted_at"), "house"))) is SaveResult.Ok)
        val back = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.single { it.optString("uid") == "house" }
        assertEquals("a new id", 4, back.getInt("id"))
        val done = Organizing.attach(load, 2, attachments(load), listOf(incoming("deed.pdf", "PDF")), uid = "house")
        assertEquals(SaveResult.Ok(4), done.result)
        val after = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
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
        val after = onDisk(load).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
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
        assertEquals(0, onDisk(load).getJSONObject(0).getJSONArray("attachments").length())
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
        val a = onDisk(load)
        return (0 until a.length()).map { a.getJSONObject(it) }.associate { it.getInt("id") to it.optInt("order", -1) }
    }

    @Test
    fun `a note moved before or after another, every note numbered again, none stamped`() {
        val load = notebook(four)
        // The pinned note first, then the placed ones in their order.
        assertEquals(listOf(4, 1, 2, 3), sortNotes(load.notes, SortOrder.CUSTOM).map { it.id })
        assertTrue(Organizing.moveNote(load, 3, anchor = 1, before = true, grouped = false) is SaveResult.Ok)
        assertEquals(mapOf(4 to 0, 3 to 1, 1 to 2, 2 to 3), orders(load))
        assertEquals(listOf(4, 3, 1, 2), sortNotes(Notebook.load(dirOf(load)).notes, SortOrder.CUSTOM).map { it.id })
        assertTrue(Organizing.moveNote(load, 3, anchor = 2, before = false, grouped = false) is SaveResult.Ok)
        assertEquals(mapOf(4 to 0, 1 to 1, 2 to 2, 3 to 3), orders(load))
        // A move is not an edit.
        assertEquals("2026-10-03 09:00:00", onDisk(load).getJSONObject(2).getString("updated"))
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
        assertEquals(listOf(4, 2, 1, 3), sortNotes(Notebook.load(dirOf(load)).notes, SortOrder.CUSTOM).map { it.id })
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
