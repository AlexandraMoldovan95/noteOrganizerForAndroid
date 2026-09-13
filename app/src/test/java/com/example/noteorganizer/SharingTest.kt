package com.example.noteorganizer

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codes, checked the way they will actually be used: a code made
 * here has to be readable by the desktop, and a code the desktop made has
 * to be readable here.
 */
class SharingTest {

    private fun note(json: String) = JSONObject(json)

    private fun scratch(contents: String?): Notebook.Load {
        val dir = Files.createTempDirectory("notebook").toFile()
        dir.deleteOnExit()
        if (contents != null) File(dir, "notes_data.json").writeText(contents)
        return Notebook.load(listOf(dir))
    }

    private fun onDisk(load: Notebook.Load): JSONArray =
        JSONArray(load.file!!.readText())

    // ---- the round trip ----

    @Test
    fun `a note survives being turned into a code and back`() {
        val original = note("""
            {"id": 7, "title": "Cumparaturi", "body": "lapte\npaine",
             "category": "Work", "date": "2026-12-25", "color": "#e63946",
             "formatting": [{"start": 0, "end": 5, "style": "bold"}],
             "checklist": [{"text": "unu", "done": true}]}
        """.trimIndent())
        val decoded = Sharing.decode(Sharing.encode(listOf(original)))
        assertTrue(decoded is Sharing.Decoded.Notes)
        val back = (decoded as Sharing.Decoded.Notes).notes.single()
        assertEquals("Cumparaturi", back.getString("title"))
        assertEquals("lapte\npaine", back.getString("body"))
        assertEquals("#e63946", back.getString("color"))
        assertEquals(1, back.getJSONArray("formatting").length())
        assertTrue(back.getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
    }

    @Test
    fun `diacritics and emoji come back exactly`() {
        val original = note("""{"title": "Mașină", "body": "Ștefan 👣 și"}""")
        val back = (Sharing.decode(Sharing.encode(listOf(original)))
            as Sharing.Decoded.Notes).notes.single()
        assertEquals("Mașină", back.getString("title"))
        assertEquals("Ștefan 👣 și", back.getString("body"))
    }

    @Test
    fun `a code never carries an id, so an import cannot claim to BE a note`() {
        val back = (Sharing.decode(Sharing.encode(listOf(note("""{"id": 7, "title": "x"}"""))))
            as Sharing.Decoded.Notes).notes.single()
        assertFalse(back.has("id"))
    }

    @Test
    fun `a code never carries attachments, in either direction`() {
        // Not a size decision -- a security one. An imported note must not
        // be able to smuggle in a `stored_as` that a later delete would
        // follow out of the attachments folder.
        val original = note("""
            {"title": "x", "attachments": [{"filename": "p.png", "stored_as": "../../x"}]}
        """.trimIndent())
        val code = Sharing.encode(listOf(original))
        assertFalse("codul nu trebuie sa contina atasamente", code.contains("stored_as"))

        // And a hand-made code that DOES contain one has it stripped on the
        // way in, so the two ends are symmetric.
        val hostile = JSONObject()
        hostile.put("v", 1)
        val notes = JSONArray()
        notes.put(original)
        hostile.put("notes", notes)
        val back = (Sharing.decode(codeOf(hostile)) as Sharing.Decoded.Notes).notes.single()
        assertFalse(back.has("attachments"))
    }

    /** Builds a code around an arbitrary payload, the way a forger would. */
    private fun codeOf(payload: JSONObject): String {
        val deflate = java.util.zip.Deflater(9)
        val raw = payload.toString().toByteArray(Charsets.UTF_8)
        deflate.setInput(raw)
        deflate.finish()
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflate.finished()) out.write(buffer, 0, deflate.deflate(buffer))
        deflate.end()
        return Sharing.PREFIX + java.util.Base64.getUrlEncoder()
            .encodeToString(out.toByteArray())
    }

    // ---- what a pasted code can be ----

    @Test
    fun `whitespace from a chat app is not damage`() {
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        val wrapped = code.chunked(40).joinToString("\n  ")
        assertTrue(Sharing.decode(wrapped) is Sharing.Decoded.Notes)
    }

    @Test
    fun `something that is not a code says so, by name`() {
        val result = Sharing.decode("buna ziua")
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.contains(Sharing.PREFIX))
    }

    @Test
    fun `an empty paste is its own message`() {
        assertTrue(Sharing.decode("   \n ") is Sharing.Decoded.Failed)
    }

    @Test
    fun `a truncated code is reported as damaged, not crashed on`() {
        val code = Sharing.encode(listOf(note("""{"title": "x", "body": "ceva mai lung"}""")))
        val cut = code.substring(0, code.length - 20)
        assertTrue(Sharing.decode(cut) is Sharing.Decoded.Failed)
    }

    @Test
    fun `a code from a newer version is refused rather than half-read`() {
        val payload = JSONObject()
        payload.put("v", 2)
        payload.put("notes", JSONArray().put(JSONObject().put("title", "x")))
        val result = Sharing.decode(codeOf(payload))
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.contains("noua"))
    }

    @Test
    fun `a code carrying too many notes is refused`() {
        val notes = (1..Sharing.MAX_SHARED_NOTES + 1).map { note("""{"title": "$it"}""") }
        val result = Sharing.decode(Sharing.encode(notes))
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.contains("limita"))
    }

    @Test
    fun `a code with no notes in it is refused`() {
        val payload = JSONObject()
        payload.put("v", 1)
        payload.put("notes", JSONArray())
        assertTrue(Sharing.decode(codeOf(payload)) is Sharing.Decoded.Failed)
    }

    // ---- importing ----

    @Test
    fun `importing creates a note and never touches the others`() {
        val load = scratch("""[{"id": 4, "title": "veche", "un_camp": "pastreaza-ma"}]""")
        val code = Sharing.encode(listOf(note("""{"title": "primita", "body": "salut"}""")))
        val result = Notebook.importShareCode(load, code)
        assertTrue(result is SaveResult.Ok)
        assertEquals(1, (result as SaveResult.Ok).imported)

        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("pastreaza-ma", all.getJSONObject(0).getString("un_camp"))
        val fresh = all.getJSONObject(1)
        assertEquals("primita", fresh.getString("title"))
        assertEquals(5, fresh.getInt("id"))
    }

    @Test
    fun `an imported note arrives with the whole shape a note needs`() {
        val load = scratch("[]")
        Notebook.importShareCode(load, Sharing.encode(listOf(note("""{"title": "x"}"""))))
        val fresh = onDisk(load).getJSONObject(0)
        for (field in listOf("id", "title", "body", "formatting", "comments", "category",
                             "date", "color", "attachments", "updated", "created",
                             "recurrence", "exceptions", "overrides", "pinned",
                             "checklist", "paragraphs")) {
            assertTrue("lipseste $field", fresh.has(field))
        }
        assertEquals("General", fresh.getString("category"))
        assertEquals(19, fresh.getString("created").length)
    }

    @Test
    fun `importing the same code twice makes two notes, on purpose`() {
        // A code has no id, so there is nothing to match it against. Two
        // notes is visible and fixable; overwriting the wrong one is not.
        val load = scratch("[]")
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        Notebook.importShareCode(load, code)
        Notebook.importShareCode(load, code)
        assertEquals(2, onDisk(load).length())
        assertEquals(listOf(1, 2),
            (0 until 2).map { onDisk(load).getJSONObject(it).getInt("id") })
    }

    @Test
    fun `several notes in one code all arrive, with distinct ids`() {
        val load = scratch("""[{"id": 9}]""")
        val code = Sharing.encode(
            listOf(note("""{"title": "a"}"""), note("""{"title": "b"}""")))
        assertEquals(2, (Notebook.importShareCode(load, code) as SaveResult.Ok).imported)
        val ids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getInt("id") }
        assertEquals(listOf(9, 10, 11), ids)
    }

    @Test
    fun `a bad code is refused before anything is written`() {
        val load = scratch("""[{"id": 1}]""")
        val before = load.file!!.readText()
        assertTrue(Notebook.importShareCode(load, "nu e un cod") is SaveResult.Failed)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `a notebook that cannot be read is never imported over`() {
        val load = scratch("{ nu e json")
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        assertTrue(Notebook.importShareCode(load, code) is SaveResult.Failed)
        assertEquals("{ nu e json", load.file!!.readText())
    }

    // ---- making a code from a stored note ----

    @Test
    fun `a code is made from the note on disk, not from the screen's model`() {
        // The Kotlin model drops fields a code is meant to carry, so the
        // code comes off the raw JSON.
        val load = scratch("""
            [{"id": 3, "title": "x", "comments": "note de subsol",
              "paragraphs": [{"line": 0, "kind": "bullet"}]}]
        """.trimIndent())
        val code = Notebook.shareCodeFor(load, 3)!!
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("note de subsol", back.getString("comments"))
        assertEquals(1, back.getJSONArray("paragraphs").length())
    }

    @Test
    fun `asking for a code for a note that is gone gives nothing, not a crash`() {
        val load = scratch("""[{"id": 1}]""")
        assertEquals(null, Notebook.shareCodeFor(load, 99))
    }
}
