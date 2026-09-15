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
        val load = scratch("""[{"id": 1, "title": "veche"}]""")
        Notebook.createNote(load, "noua", "")
        assertFalse("nu i se inventeaza identitate notitei vechi",
            onDisk(load).getJSONObject(0).has("uid"))
        assertTrue("...dar cea noua o are",
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
        assertFalse("dar nu si numarul local", back.has("id"))
    }

    // ---- recognising, before writing anything ----

    @Test
    fun `a code holding a note we already have is counted, not acted on`() {
        val load = scratch("""[{"id": 1, "uid": "abc", "title": "Aici"}]""")
        val before = load.file!!.readText()
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Aici, editata")))
        val found = ready(load, code)
        assertEquals(1, found.known)
        assertEquals("inspectarea nu scrie nimic", before, load.file!!.readText())
    }

    @Test
    fun `a code holding only new notes reports none known`() {
        val load = scratch("""[{"id": 1, "uid": "abc"}]""")
        val code = Sharing.encode(listOf(JSONObject().put("title", "Straina")))
        assertEquals(0, ready(load, code).known)
    }

    @Test
    fun `a damaged code is reported before any question is asked`() {
        val load = scratch("[]")
        assertTrue(Notebook.inspectShareCode(load, "nu e un cod")
            is Notebook.Inspection.Failed)
    }

    // ---- updating in place ----

    @Test
    fun `updating replaces the content and leaves the note where it was`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Vechi", "body": "corp vechi",
              "created": "2020-01-01 00:00:00",
              "attachments": [{"filename": "p.png"}]}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Nou").put("body", "corp nou")))
        val found = ready(load, code)
        val result = Notebook.applyShareCode(load, found.notes, update = true)
        assertTrue(result is SaveResult.Ok)

        val all = onDisk(load)
        assertEquals("nicio notita in plus", 1, all.length())
        val note = all.getJSONObject(0)
        assertEquals("Nou", note.getString("title"))
        assertEquals("corp nou", note.getString("body"))
        assertEquals("id-ul nu se misca", 7, note.getInt("id"))
        assertEquals("identitatea nu se misca", "abc", note.getString("uid"))
        assertEquals("o actualizare nu e o nastere",
            "2020-01-01 00:00:00", note.getString("created"))
        assertEquals("atasamentele raman -- un cod nu le poarta niciodata",
            "p.png", note.getJSONArray("attachments").getJSONObject(0).getString("filename"))
    }

    @Test
    fun `updating leaves the rescue copy the desktop can restore from`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Vechi", "body": "corp vechi",
              "comments": "nota mea"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Nou").put("body", "corp nou")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        val prev = onDisk(load).getJSONObject(0).getJSONObject("_prev")
        assertEquals("Vechi", prev.getString("title"))
        assertEquals("corp vechi", prev.getString("body"))
        assertEquals("nota mea", prev.getString("comments"))
    }

    @Test
    fun `an update carries everything the code brought, not only the words`() {
        // Written after a mutant that dropped `checklist` from the field
        // list survived: the first version of this suite only ever looked
        // at the title and the body, so nine of the ten fields an update
        // replaces were never checked at all.
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Vechi", "category": "General",
              "color": "", "date": "", "checklist": [], "comments": ""}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc")
            .put("title", "Nou")
            .put("body", "corp")
            .put("comments", "comentariu venit")
            .put("category", "Scoala")
            .put("date", "2026-09-20")
            .put("color", "#ff0000")
            .put("formatting", JSONArray().put(JSONObject()
                .put("start", 0).put("end", 4).put("style", "bold")))
            .put("paragraphs", JSONArray().put(JSONObject()
                .put("index", 0).put("kind", "bullet")))
            .put("checklist", JSONArray().put(JSONObject()
                .put("text", "de facut").put("done", false)))))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)

        val note = onDisk(load).getJSONObject(0)
        assertEquals("comentariu venit", note.getString("comments"))
        assertEquals("Scoala", note.getString("category"))
        assertEquals("2026-09-20", note.getString("date"))
        assertEquals("#ff0000", note.getString("color"))
        assertEquals(1, note.getJSONArray("formatting").length())
        assertEquals(1, note.getJSONArray("paragraphs").length())
        assertEquals("de facut",
            note.getJSONArray("checklist").getJSONObject(0).getString("text"))
    }

    @Test
    fun `an update stamps the note as changed`() {
        val load = scratch("""
            [{"id": 7, "uid": "abc", "title": "Vechi",
              "updated": "2020-01-01 00:00:00"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Nou")))
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
            [{"id": 1, "uid": "", "title": "Fara identitate"},
             {"id": 2, "title": "Nici asta"}]
        """.trimIndent())
        val code = Sharing.encode(listOf(
            JSONObject().put("uid", "").put("title", "Venita")))
        val found = ready(load, code)
        assertEquals("niciuna nu e recunoscuta", 0, found.known)
        Notebook.applyShareCode(load, found.notes, update = true)
        val all = onDisk(load)
        assertEquals("s-a adaugat, nu s-a suprascris", 3, all.length())
        assertEquals("Fara identitate", all.getJSONObject(0).getString("title"))
    }

    // ---- the screen and the disk ----

    @Test
    fun `a note that was added shows up on the screen, not only on disk`() {
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Aici"}]""")
        val code = Sharing.encode(listOf(JSONObject().put("title", "Straina")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = false)
        assertEquals(listOf("Aici", "Straina"),
            Notebook.parseNotes(load.raw!!.toString()).map { it.title })
    }

    @Test
    fun `a write that fails leaves the screen exactly as it was`() {
        // The candidate array shares its note objects with the live one,
        // so an update applied in place would reach the screen before the
        // write could fail -- showing a version that exists nowhere. A
        // mutant proved this was held up by that sharing rather than by
        // the code, so the check is here now.
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Vechi"}]""")
        val nowhere = load.copy(file = File(load.file!!.parentFile,
            "nu/exista/notes_data.json"))
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Nou")))
        val result = Notebook.applyShareCode(
            nowhere, ready(load, code).notes, update = true)

        assertTrue("scrierea trebuie sa esueze", result is SaveResult.Failed)
        assertEquals("ecranul ramane pe varianta de pe disc",
            "Vechi", Notebook.parseNotes(load.raw!!.toString()).single().title)
        assertEquals("si fisierul real e neatins",
            "Vechi", onDisk(load).getJSONObject(0).getString("title"))
    }

    @Test
    fun `the list on screen matches the disk after an update`() {
        // The screen reads load.raw. If the write lands and that array is
        // not brought along, the note stays on screen as it was and the
        // next edit is made against a version that no longer exists.
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Vechi"}]""")
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Nou")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = true)
        assertEquals("Nou", Notebook.parseNotes(load.raw!!.toString()).single().title)
    }

    // ---- adding beside it instead ----

    @Test
    fun `adding leaves both notes, each with its own identity`() {
        val load = scratch("""[{"id": 7, "uid": "abc", "title": "Vechi"}]""")
        val code = Sharing.encode(listOf(JSONObject()
            .put("uid", "abc").put("title", "Alta varianta")))
        Notebook.applyShareCode(load, ready(load, code).notes, update = false)

        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("abc", all.getJSONObject(0).getString("uid"))
        assertNotEquals("copia nu poate purta identitatea originalului",
            "abc", all.getJSONObject(1).getString("uid"))
        assertEquals("Alta varianta", all.getJSONObject(1).getString("title"))
        assertEquals(8, all.getJSONObject(1).getInt("id"))
    }

    @Test
    fun `a note nothing here matches is created whichever answer is given`() {
        for (update in listOf(true, false)) {
            val load = scratch("""[{"id": 1, "uid": "abc"}]""")
            val code = Sharing.encode(listOf(JSONObject().put("title", "Straina")))
            Notebook.applyShareCode(load, ready(load, code).notes, update)
            assertEquals("update=$update", 2, onDisk(load).length())
        }
    }

    @Test
    fun `a mixed batch updates what it knows and creates what it does not`() {
        val load = scratch("""[{"id": 1, "uid": "abc", "title": "Aici"}]""")
        val code = Sharing.encode(listOf(
            JSONObject().put("uid", "abc").put("title", "Aici, editata"),
            JSONObject().put("title", "Cu totul noua"),
        ))
        val found = ready(load, code)
        assertEquals(1, found.known)
        Notebook.applyShareCode(load, found.notes, update = true)
        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("Aici, editata", all.getJSONObject(0).getString("title"))
        assertEquals("Cu totul noua", all.getJSONObject(1).getString("title"))
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
            assertTrue("lipseste $field", note.has(field))
        }
    }

    @Test
    fun `a notebook that cannot be read is never written over`() {
        val load = scratch("{ nu e json")
        val code = Sharing.encode(listOf(JSONObject().put("title", "x")))
        val result = Notebook.applyShareCode(load, listOf(JSONObject().put("title", "x")), false)
        assertTrue(result is SaveResult.Failed)
        assertEquals("{ nu e json", load.file!!.readText())
        assertTrue(Notebook.inspectShareCode(load, code) is Notebook.Inspection.Ready)
    }
}
