package ro.noteorganizer.app

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A note's identity, on the phone's side of it.
 *
 * `id` is a note's place in one file; two devices both hand a new note
 * `max + 1` and both invent 87. `uid` is minted independently and cannot
 * collide, which is what lets an arriving copy be recognised as a note
 * this device already has rather than guessed at from its text — a guess
 * that is wrong exactly when the note was edited, which is the only
 * reason anyone sends one back.
 */
class IdentityTest {

    private fun scratch(contents: String?): Notebook.Load {
        val dir = Files.createTempDirectory("notebook").toFile()
        dir.deleteOnExit()
        if (contents != null) File(dir, "notes_data.json").writeText(contents)
        return Notebook.load(listOf(dir))
    }

    private fun onDisk(load: Notebook.Load): JSONArray =
        JSONArray(load.file!!.readText())

    private fun ready(load: Notebook.Load, code: String) =
        Notebook.inspectShareCode(load, code) as Notebook.Inspection.Ready

    // ---- minting, and NOT minting ----

    @Test
    fun `a note written here is born with an identity`() {
        val load = scratch("[]")
        Notebook.createNote(load, "t", "b")
        val uid = onDisk(load).getJSONObject(0).getString("uid")
        assertTrue(uid.length > 8)
    }

    @Test
    fun `two notes written here get different identities`() {
        val load = scratch("[]")
        Notebook.createNote(load, "a", "")
        Notebook.createNote(load, "b", "")
        assertNotEquals(onDisk(load).getJSONObject(0).getString("uid"),
                        onDisk(load).getJSONObject(1).getString("uid"))
    }

    @Test
    fun `a note that arrived WITHOUT one is left alone`() {
        // Deliberate. The desktop mints identities for every note at its
        // own door, once. If this app minted them too, a notebook copied
        // here before the desktop had seen it would end up with two
        // different identities for the same note -- the exact collision
        // the field exists to prevent, manufactured by the fix for it.
        val load = scratch("""[{"id": 1, "title": "old"}]""")
        Notebook.createNote(load, "new", "")
        assertFalse("no identity is invented for the old note",
            onDisk(load).getJSONObject(0).has("uid"))
        assertTrue("...but the new one has one",
            onDisk(load).getJSONObject(1).getString("uid").isNotEmpty())
    }

    // ---- carrying it in a code ----

    @Test
    fun `a share code carries the identity`() {
        val load = scratch("[]")
        Notebook.createNote(load, "t", "b")
        val id = onDisk(load).getJSONObject(0).getInt("id")
        val code = Notebook.shareCodeFor(load, id)!!
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals(onDisk(load).getJSONObject(0).getString("uid"),
                     back.getString("uid"))
        assertFalse("but not the local number", back.has("id"))
    }

    // ---- recognising, before writing anything ----

    @Test
    fun `a code holding a note we already have is counted, not acted on`() {
        val load = scratch("""[{"id": 1, "uid": "abc", "title": "Here"}]""")
        val before = load.file!!.readText()
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Here, edited")))
        val found = ready(load, code)
        assertEquals(1, found.known)
        assertEquals("inspecting writes nothing", before, load.file!!.readText())
    }

    @Test
    fun `a code holding only new notes reports none known`() {
        val load = scratch("""[{"id": 1, "uid": "abc"}]""")
        val code = Sharing.encode(listOf(JSONObject().put("title", "A stranger")))
        assertEquals(0, ready(load, code).known)
    }

    @Test
    fun `a damaged code is reported before any question is asked`() {
        val load = scratch("[]")
        assertTrue(Notebook.inspectShareCode(load, "not a code")
            is Notebook.Inspection.Failed)
    }

    // ---- updating in place ----

    @Test
    fun `updating replaces the content and leaves the note where it was`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Old", "body": "old body",
              "created": "2020-01-01 00:00:00",
              "attachments": [{"filename": "p.png"}]}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New").put("body", "new body")))
        val found = ready(load, code)
        val result = Notebook.applyShareCode(load, found.notes, update = true)
        assertTrue(result is SaveResult.Ok)

        val all = onDisk(load)
        assertEquals("no extra note", 1, all.length())
        val note = all.getJSONObject(0)
        assertEquals("New", note.getString("title"))
        assertEquals("new body", note.getString("body"))
        assertEquals("the id does not move", 7, note.getInt("id"))
        assertEquals("the identity does not move", "abc", note.getString("uid"))
        assertEquals("an update is not a birth",
            "2020-01-01 00:00:00", note.getString("created"))
        assertEquals("the files stay -- a code never carries them",
            "p.png", note.getJSONArray("attachments").getJSONObject(0).getString("filename"))
    }

    @Test
    fun `updating leaves the rescue copy the desktop can restore from`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Old", "body": "old body",
              "comments": "my note"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New").put("body", "new body")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        val prev = onDisk(load).getJSONObject(0).getJSONObject("_prev")
        assertEquals("Old", prev.getString("title"))
        assertEquals("old body", prev.getString("body"))
        assertEquals("my note", prev.getString("comments"))
    }

    @Test
    fun `an update carries everything the code brought, not only the words`() {
        // Written after a mutant that dropped `checklist` from the field
        // list survived: the first version of this suite only ever looked
        // at the title and the body, so nine of the ten fields an update
        // replaces were never checked at all.
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Old", "category": "General",
              "color": "", "date": "", "checklist": [], "comments": ""}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc")
            .put("title", "New")
            .put("body", "body")
            .put("comments", "a comment that came along")
            .put("category", "Work")
            .put("date", "2026-09-20")
            .put("color", "#ff0000")
            .put("formatting", JSONArray().put(JSONObject()
                .put("start", 0).put("end", 4).put("style", "bold")))
            // "line", as the desktop writes an entry: one without it is
            // dropped, as normalize_paragraphs drops it.
            .put("paragraphs", JSONArray().put(JSONObject()
                .put("line", 0).put("kind", "bullet")))
            .put("checklist", JSONArray().put(JSONObject()
                .put("text", "to do").put("done", false)))))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)

        val note = onDisk(load).getJSONObject(0)
        assertEquals("a comment that came along", note.getString("comments"))
        assertEquals("Work", note.getString("category"))
        assertEquals("2026-09-20", note.getString("date"))
        assertEquals("#ff0000", note.getString("color"))
        assertEquals(1, note.getJSONArray("formatting").length())
        assertEquals(1, note.getJSONArray("paragraphs").length())
        assertEquals("to do",
            note.getJSONArray("checklist").getJSONObject(0).getString("text"))
    }

    @Test
    fun `an update stamps the note as changed`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Old",
              "updated": "2020-01-01 00:00:00"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        assertNotEquals("2020-01-01 00:00:00",
            onDisk(load).getJSONObject(0).getString("updated"))
    }

    // ---- an empty identity is not an identity ----

    @Test
    fun `a note that has no identity is not the same note as another`() {
        // Notes that reached this device before the desktop minted any
        // carry no uid, and a code made from one carries none either. If
        // "" matched "", every such note would be the same note -- and
        // the first one in the file would be silently overwritten.
        val load = scratch("""
            [{"id": 1, "uid": "", "title": "No identity"},
             {"id": 2, "title": "Nor this one"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(
            JSONObject().put("uid", "").put("title", "Arrived")))
        val found = ready(load, code)
        assertEquals("none is recognised", 0, found.known)
        Notebook.applyShareCode(load, found.notes, update = true)
        val all = onDisk(load)
        assertEquals("added, not written over", 3, all.length())
        assertEquals("No identity", all.getJSONObject(0).getString("title"))
    }

    // ---- the screen and the disk ----

    @Test
    fun `a note that was added shows up on the screen, not only on disk`() {
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Here"}]""")
        val code = Sharing.encode(listOf(JSONObject().put("title", "A stranger")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = false)
        assertEquals(listOf("Here", "A stranger"),
            Notebook.parseNotes(load.raw!!.toString()).map { it.title })
    }

    @Test
    fun `a write that fails leaves the screen exactly as it was`() {
        // The candidate array shares its note objects with the live one,
        // so an update applied in place would reach the screen before the
        // write could fail -- showing a version that exists nowhere. A
        // mutant proved this was held up by that sharing rather than by
        // the code, so the check is here now.
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Old"}]""")
        val nowhere = load.copy(file = File(load.file!!.parentFile,
            "no/such/notes_data.json"))
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New")))
        val result = Notebook.applyShareCode(
            nowhere, ready(load, code).notes, update = true)

        assertTrue("the write must fail", result is SaveResult.Failed)
        assertEquals("the screen stays on the version on disk",
            "Old", Notebook.parseNotes(load.raw!!.toString()).single().title)
        assertEquals("and the real file is untouched",
            "Old", onDisk(load).getJSONObject(0).getString("title"))
    }

    @Test
    fun `the list on screen matches the disk after an update`() {
        // The screen reads load.raw. If the write lands and that array is
        // not brought along, the note stays on screen as it was and the
        // next edit is made against a version that no longer exists.
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Old"}]""")
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        assertEquals("New", Notebook.parseNotes(load.raw!!.toString()).single().title)
    }

    // ---- adding beside it instead ----

    @Test
    fun `adding leaves both notes, each with its own identity`() {
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Old"}]""")
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Another version")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = false)

        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("abc", all.getJSONObject(0).getString("uid"))
        assertNotEquals("the copy cannot wear the original's identity",
            "abc", all.getJSONObject(1).getString("uid"))
        assertEquals("Another version", all.getJSONObject(1).getString("title"))
        assertEquals(8, all.getJSONObject(1).getInt("id"))
    }

    @Test
    fun `a note nothing here matches is created whichever answer is given`() {
        for (update in listOf(true, false)) {
            val load = scratch("""[{"id": 1, "uid": "abc"}]""")
            val code = Sharing.encode(listOf(JSONObject().put("title", "A stranger")))
            Notebook.applyShareCode(load, ready(load, code).notes, update)
            assertEquals("update=$update", 2, onDisk(load).length())
        }
    }

    @Test
    fun `a mixed batch updates what it knows and creates what it does not`() {
        val load = scratch("""[{"id": 1, "uid": "abc", "title": "Here"}]""")
        val code = Sharing.encode(listOf(
            JSONObject().put("uid", "abc").put("title", "Here, edited"),
            JSONObject().put("title", "Brand new"),
        ))
        val found = ready(load, code)
        assertEquals(1, found.known)
        Notebook.applyShareCode(load, found.notes, update = true)
        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("Here, edited", all.getJSONObject(0).getString("title"))
        assertEquals("Brand new", all.getJSONObject(1).getString("title"))
    }

    @Test
    fun `an imported note arrives with the whole shape a note needs`() {
        val load = scratch("[]")
        val code = Sharing.encode(listOf(JSONObject().put("title", "x")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = false)
        val note = onDisk(load).getJSONObject(0)
        for (field in listOf("id", "uid", "title", "body", "formatting", "comments",
                             "category", "date", "color", "attachments", "updated",
                             "created", "recurrence", "exceptions", "overrides",
                             "pinned", "checklist", "paragraphs")) {
            assertTrue("missing: $field", note.has(field))
        }
    }

    @Test
    fun `a notebook that cannot be read is kept aside before an import writes`() {
        val load = scratch("{ not json")
        val kept = load.file!!.parentFile!!.listFiles()!!
            .single { it.name.startsWith("notes_data_corrupt_") }
        val code = Sharing.encode(listOf(JSONObject().put("title", "x")))
        assertTrue(Notebook.inspectShareCode(load, code) is Notebook.Inspection.Ready)
        assertTrue(Notebook.applyShareCode(load, listOf(JSONObject().put("title", "x")), false)
            is SaveResult.Ok)
        assertEquals("the unreadable bytes are still kept", "{ not json", kept.readText())
    }

    @Test
    fun `a category this device does not have files the note under General`() {
        // normalize_note on the desktop: a share code brings a category's
        // NAME, never its colour, so a name nothing here knows is General.
        // Only a backup brings its categories along (see ImportingTest).
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Old", "category": "Work"}]""")
        val code = Sharing.encode(listOf(
            JSONObject().put("uid", "abc").put("title", "New").put("category", "Nowhere"),
            JSONObject().put("uid", "fresh").put("title", "Other").put("category", "Nowhere")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        val all = onDisk(load)
        assertEquals("General", all.getJSONObject(0).getString("category"))
        assertEquals("General", all.getJSONObject(1).getString("category"))
    }

    // ---- the rescue copy, whole, and the way back ----

    @Test
    fun `an update keeps everything it replaced, not only the words`() {
        // The desktop's apply_shared_update: "Restore previous" brings the
        // checklist's ticks back too, and the date and the repeat rule.
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Old", "body": "old body",
              "category": "Work", "date": "2026-01-02", "color": "#e63946",
              "checklist": [{"text": "milk", "done": true}],
              "recurrence": {"freq": "weekly", "interval": 1}}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "New").put("body", "new body")
            .put("category", "General").put("date", "").put("color", "")
            .put("checklist", JSONArray())))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        val prev = onDisk(load).getJSONObject(0).getJSONObject("_prev")
        assertEquals("Work", prev.getString("category"))
        assertEquals("2026-01-02", prev.getString("date"))
        assertEquals("#e63946", prev.getString("color"))
        assertTrue(prev.getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
        assertEquals("weekly", prev.getJSONObject("recurrence").getString("freq"))
    }

    @Test
    fun `an update that changes nothing leaves the note and its rescue copy alone`() {
        // Otherwise the one earlier version is replaced by a duplicate of
        // what is already there -- the desktop's shared_update_changes.
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Same", "body": "same",
              "updated": "2020-01-01 00:00:00",
              "_prev": {"title": "The real earlier version", "body": "kept"}}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Same").put("body", "same")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("The real earlier version", note.getJSONObject("_prev").getString("title"))
        assertEquals("2020-01-01 00:00:00", note.getString("updated"))
    }

    @Test
    fun `restoring brings back everything the update replaced, and keeps what it replaced`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "New", "body": "new", "category": "General",
              "checklist": [],
              "_prev": {"title": "Old", "body": "old", "comments": "c",
                        "formatting": [], "paragraphs": [],
                        "category": "Work",
                        "checklist": [{"text": "milk", "done": true}]}}]
        """.trimIndent())
        val before = load.notes.single()
        assertTrue(before.hasPrevious)
        assertEquals(listOf("checklist", "category"), before.previousAlso)

        assertTrue(Notebook.restorePrevious(load, 7) is SaveResult.Ok)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("Old", note.getString("title"))
        assertEquals("old", note.getString("body"))
        assertEquals("c", note.getString("comments"))
        assertEquals("Work", note.getString("category"))
        assertTrue(note.getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
        // The version it replaced is the copy now, its own extras with it.
        val kept = note.getJSONObject("_prev")
        assertEquals("New", kept.getString("title"))
        assertEquals("new", kept.getString("body"))
        assertEquals("General", kept.getString("category"))
        assertEquals(0, kept.getJSONArray("checklist").length())
        assertEquals("the identity stays", "abc", note.getString("uid"))
    }

    @Test
    fun `restoring over an emptied note keeps nothing, so no banner offers the empty note back`() {
        val load = scratch("""
            [{"id": 7, "title": "t", "body": "",
              "_prev": {"title": "t", "body": "a long text that was deleted by accident"}}]
        """.trimIndent())
        assertFalse("the question does not promise to keep it", Notebook.previousKeeps(load, 7))
        assertTrue(Notebook.restorePrevious(load, 7) is SaveResult.Ok)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("a long text that was deleted by accident", note.getString("body"))
        assertFalse(note.has("_prev"))
    }

    @Test
    fun `a note whose words a restore would replace says so, for the question to promise it`() {
        val load = scratch("""
            [{"id": 7, "title": "t", "body": "written since",
              "_prev": {"title": "t", "body": "a long text that was deleted by accident"}}]
        """.trimIndent())
        assertTrue(Notebook.previousKeeps(load, 7))
    }

    @Test
    fun `whether a restore keeps the note's words is answered in time that grows with the words, not their square`() {
        // A backup's rescue copy of four million letters, and a note of
        // twenty thousand that nearly matches it: the JVM's contains took
        // fourteen seconds a call, and was asked of every note at every
        // load -- each start, each save. Linear, it takes milliseconds;
        // two seconds is a margin no machine speed crosses either way.
        val dir = Files.createTempDirectory("notebook").toFile()
        val note = JSONObject().put("id", 7).put("title", "t").put("body", "a".repeat(20_000) + "b")
            .put("_prev", JSONObject().put("title", "t").put("body", "a".repeat(4_000_000)))
        File(dir, "notes_data.json").writeText(JSONArray().put(note).toString())
        val started = System.nanoTime()
        val load = Notebook.load(listOf(dir))
        assertTrue("restoring it would replace words the copy lacks", Notebook.previousKeeps(load, 7))
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test
    fun `the linear search finds what the JVM's contains finds`() {
        val random = java.util.Random(7)
        val cases = mutableListOf("" to "", "abc" to "", "" to "a", "aaab" to "aab", "abababc" to "ababc",
                                  "aabaabaaa" to "aabaaa", "abc" to "abcd", "xyz" to "xyz")
        repeat(3000) {
            val hay = (0 until random.nextInt(12)).map { "ab"[random.nextInt(2)] }.joinToString("")
            val needle = (0 until random.nextInt(5)).map { "ab"[random.nextInt(2)] }.joinToString("")
            cases.add(hay to needle)
        }
        for ((hay, needle) in cases) {
            assertEquals("$needle in $hay", hay.contains(needle), PyText.contains(hay, needle))
        }
    }

    @Test
    fun `restoring twice brings back what the first restore replaced`() {
        val load = scratch("""
            [{"id": 7, "title": "t", "body": "written since",
              "_prev": {"title": "t", "body": "a long text that was deleted by accident"}}]
        """.trimIndent())
        assertTrue(Notebook.restorePrevious(load, 7) is SaveResult.Ok)
        assertTrue(Notebook.restorePrevious(Notebook.load(load.file!!.parentFile!!), 7) is SaveResult.Ok)
        assertEquals("written since", onDisk(load).getJSONObject(0).getString("body"))
    }

    @Test
    fun `a save that puts back what the copy kept spends it, and no banner stays`() {
        // A big deletion keeps a copy; Undo brings the words back, and the save
        // that follows holds what the copy holds.
        val long = "a long text, long enough that deleting it keeps a rescue copy of the note"
        val load = scratch("""[{"id": 7, "title": "t", "body": "$long"}]""")
        assertTrue(Notebook.updateNote(load, 7, "t", "") is SaveResult.Ok)
        assertTrue("the deletion kept a copy", onDisk(load).getJSONObject(0).has("_prev"))
        assertTrue(Notebook.updateNote(Notebook.load(load.file!!.parentFile!!), 7, "t", long) is SaveResult.Ok)
        assertFalse("the words are back: nothing to rescue", onDisk(load).getJSONObject(0).has("_prev"))
    }

    @Test
    fun `a save that differs from the copy keeps it`() {
        val long = "a long text, long enough that deleting it keeps a rescue copy of the note"
        val load = scratch("""[{"id": 7, "title": "t", "body": "$long"}]""")
        Notebook.updateNote(load, 7, "t", "")
        Notebook.updateNote(Notebook.load(load.file!!.parentFile!!), 7, "t", "something else")
        assertTrue(onDisk(load).getJSONObject(0).has("_prev"))
    }

    @Test
    fun `a shrink's rescue copy brings back the text and leaves the rest`() {
        val load = scratch("""
            [{"id": 7, "title": "t", "body": "", "category": "Work",
              "checklist": [{"text": "keep me", "done": false}],
              "_prev": {"title": "t", "body": "a long text that was deleted by accident"}}]
        """.trimIndent())
        assertEquals(emptyList<String>(), load.notes.single().previousAlso)
        Notebook.restorePrevious(load, 7)
        val note = onDisk(load).getJSONObject(0)
        assertEquals("a long text that was deleted by accident", note.getString("body"))
        assertEquals("Work", note.getString("category"))
        assertEquals("keep me", note.getJSONArray("checklist").getJSONObject(0).getString("text"))
    }

    @Test
    fun `a category deleted since the copy was made comes back as General`() {
        val load = scratch("""
            [{"id": 7, "title": "t", "body": "b",
              "_prev": {"title": "t", "body": "b0", "category": "Gone"}}]
        """.trimIndent())
        Notebook.restorePrevious(load, 7)
        assertEquals("General", onDisk(load).getJSONObject(0).getString("category"))
    }

    @Test
    fun `restoring a note with no copy says so and writes nothing`() {
        val load = scratch("""[{"id": 7, "title": "t"}]""")
        val before = load.file!!.readText()
        assertTrue(Notebook.restorePrevious(load, 7) is SaveResult.Failed)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `a missing repeat rule is the default rule, not a change`() {
        // A copy that omits the rule's empty fields must not read as an
        // update, or it spends the rescue copy for nothing.
        // Dated: to normalize_note, a rule with no first day is a one-off note.
        val mine = JSONObject("""{"title": "x", "date": "2026-10-01", "recurrence":
            {"freq": "", "interval": 1, "weekdays": [], "month_days": [],
             "month_day_policy": "clamp", "end": "", "count": 0}}""")
        val theirs = JSONObject("""{"title": "x", "date": "2026-10-01", "recurrence": {"freq": ""}}""")
        assertFalse(Notebook.sharedUpdateChanges(mine, theirs, Notebook.UPDATABLE_FROM_SHARED))
        theirs.getJSONObject("recurrence").put("freq", "daily")
        assertTrue(Notebook.sharedUpdateChanges(mine, theirs, Notebook.UPDATABLE_FROM_SHARED))
    }

    @Test
    fun `the comments are saved with the note`() {
        val load = scratch("""[{"id": 7, "title": "t", "body": "b", "comments": ""}]""")
        assertTrue(Notebook.updateNote(load, 7, "t", "b", comments = "a side note") is SaveResult.Ok)
        assertEquals("a side note", onDisk(load).getJSONObject(0).getString("comments"))
    }

    @Test
    fun `a notebook that can neither be read nor kept aside is never written over`() {
        val load = scratch("[]")
        val stuck = Notebook.Load(emptyList(), load.categories, "x", file = load.file, raw = null)
        load.file!!.writeText("{ unreadable")
        val result = Notebook.applyShareCode(stuck, listOf(JSONObject().put("title", "x")), false)
        assertTrue(result is SaveResult.Failed)
        assertEquals("{ unreadable", load.file!!.readText())
    }
}
