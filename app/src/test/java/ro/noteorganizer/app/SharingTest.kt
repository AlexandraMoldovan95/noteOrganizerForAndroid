package ro.noteorganizer.app

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
            {"id": 7, "title": "Shopping", "body": "milk\nbread",
             "category": "Work", "date": "2026-12-25", "color": "#e63946",
             "formatting": [{"start": 0, "end": 4, "style": "bold"}],
             "checklist": [{"text": "one", "done": true}]}
        """.trimIndent())
        val decoded = Sharing.decode(Sharing.encode(listOf(original)))
        assertTrue(decoded is Sharing.Decoded.Notes)
        val back = (decoded as Sharing.Decoded.Notes).notes.single()
        assertEquals("Shopping", back.getString("title"))
        assertEquals("milk\nbread", back.getString("body"))
        assertEquals("#e63946", back.getString("color"))
        assertEquals(1, back.getJSONArray("formatting").length())
        assertTrue(back.getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
    }

    @Test
    fun `no code is made past what the import takes, as the desktop's encode_share_code refuses`() {
        // 200 notes make a code; 201 a refusal in the desktop's words, never
        // a code both apps would turn away.
        val many = (1..201).map { note("""{"title": "n$it"}""") }
        assertTrue(Sharing.decode(Sharing.encode(many.take(200))) is Sharing.Decoded.Notes)
        val refused = try { Sharing.encode(many); null } catch (e: Sharing.TooLarge) { e.message }
        assertTrue(refused, refused != null && refused.contains("at most 200 notes") && refused.contains("201"))
        // Past the size the import accepts, by its words: a body of random letters
        // compresses badly, so the code itself runs past the limit.
        val random = java.util.Random(7)
        val noise = String(CharArray(Sharing.MAX_DECOMPRESSED_BYTES + 1) { ('a' + random.nextInt(26)) })
        val tooBig = try { Sharing.encode(listOf(note("{}").put("body", noise))); null } catch (e: Sharing.TooLarge) { e.message }
        assertTrue(tooBig, tooBig != null && tooBig.contains("too much for one code"))
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
        assertFalse("the code must not carry attachments", code.contains("stored_as"))

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
        val result = Sharing.decode("good afternoon")
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.contains(Sharing.PREFIX))
    }

    @Test
    fun `an empty paste is its own message`() {
        assertTrue(Sharing.decode("   \n ") is Sharing.Decoded.Failed)
    }

    @Test
    fun `a truncated code is reported as damaged, not crashed on`() {
        val code = Sharing.encode(listOf(note("""{"title": "x", "body": "something longer"}""")))
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
        assertTrue((result as Sharing.Decoded.Failed).reason.contains("newer version"))
    }

    @Test
    fun `a version that is not a number is damage, not version 1`() {
        // The desktop refuses it by type; the phone used to read "2" as
        // "no version given" and import whatever followed.
        for (version in listOf<Any>("2", true, JSONArray())) {
            val payload = JSONObject()
            payload.put("v", version)
            payload.put("notes", JSONArray().put(JSONObject().put("title", "x")))
            val result = Sharing.decode(codeOf(payload))
            assertTrue("v = $version", result is Sharing.Decoded.Failed)
            assertTrue((result as Sharing.Decoded.Failed).reason.contains("damaged"))
        }
    }

    @Test
    fun `a payload with no notes key says so`() {
        val result = Sharing.decode(codeOf(JSONObject().put("v", 1)))
        assertTrue((result as Sharing.Decoded.Failed).reason.contains("doesn't contain any notes"))
    }

    @Test
    fun `a code carrying too many notes is refused`() {
        // Made by hand: this phone no longer makes such a code, another app might.
        val notes = JSONArray().apply { (1..Sharing.MAX_SHARED_NOTES + 1).forEach { put(note("""{"title": "$it"}""")) } }
        val result = Sharing.decode(codeOf(JSONObject().put("v", 1).put("notes", notes)))
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.contains("limit"))
    }

    @Test
    fun `a code with no notes in it is refused`() {
        val payload = JSONObject()
        payload.put("v", 1)
        payload.put("notes", JSONArray())
        assertTrue(Sharing.decode(codeOf(payload)) is Sharing.Decoded.Failed)
    }

    // ---- importing ----
    //
    // These used to drive `importShareCode`, the one import route before
    // a code carried a note's identity. The screen now inspects a code
    // first, so a note this device already has can raise a question
    // instead of being copied silently, and that is the only route there
    // is. Checks that drive a function nothing calls keep it alive while
    // proving nothing about what the app does -- and that one carried its
    // own second copy of the new-note defaults, which is exactly how two
    // copies of a rule drift apart without anyone noticing.

    /** What the screen does: look first, then write with the answer. */
    private fun bring(load: Notebook.Load, code: String,
                      update: Boolean = false): SaveResult {
        val found = Notebook.inspectShareCode(load, code)
        if (found is Notebook.Inspection.Failed) return SaveResult.Failed(found.reason)
        return Notebook.applyShareCode(
            load, (found as Notebook.Inspection.Ready).notes, update)
    }

    @Test
    fun `importing creates a note and never touches the others`() {
        val load = scratch("""[{"id": 4, "title": "old", "a_field": "keep-me"}]""")
        val code = Sharing.encode(listOf(note("""{"title": "received", "body": "hello"}""")))
        val result = bring(load, code)
        assertTrue(result is SaveResult.Ok)
        assertEquals(1, (result as SaveResult.Ok).imported)

        val all = onDisk(load)
        assertEquals(2, all.length())
        assertEquals("keep-me", all.getJSONObject(0).getString("a_field"))
        val fresh = all.getJSONObject(1)
        assertEquals("received", fresh.getString("title"))
        assertEquals(5, fresh.getInt("id"))
    }

    @Test
    fun `an imported note arrives with the whole shape a note needs`() {
        val load = scratch("[]")
        bring(load, Sharing.encode(listOf(note("""{"title": "x"}"""))))
        val fresh = onDisk(load).getJSONObject(0)
        for (field in listOf("id", "uid", "title", "body", "formatting", "comments",
                             "category", "date", "color", "attachments", "updated",
                             "created", "recurrence", "exceptions", "overrides",
                             "pinned", "checklist", "paragraphs")) {
            assertTrue("missing: $field", fresh.has(field))
        }
        assertEquals("General", fresh.getString("category"))
        assertEquals(19, fresh.getString("created").length)
    }

    @Test
    fun `importing the same code twice depends on whether it carries an identity`() {
        // The old rule was: always create. A code carried no id, so there
        // was nothing to match it against, and two notes are visible and
        // fixable where overwriting the wrong one is not. That reasoning
        // is exactly why the identity exists -- and it still governs the
        // half of the world that has none.
        val load = scratch("[]")
        val anonymous = Sharing.encode(listOf(note("""{"title": "x"}""")))
        bring(load, anonymous)
        bring(load, anonymous)
        assertEquals("a code with no identity makes two notes, as before",
            2, onDisk(load).length())
        assertEquals(listOf(1, 2),
            (0 until 2).map { onDisk(load).getJSONObject(it).getInt("id") })

        // A code made from a note this device has SAVED carries that
        // note's identity, and then the second arrival is recognised
        // rather than guessed at -- which is the whole point of the
        // field. Importing it still leaves two notes here, but now
        // because that answer was chosen, not because nothing could tell.
        val mine = Notebook.shareCodeFor(load, 1)!!
        val found = Notebook.inspectShareCode(load, mine) as Notebook.Inspection.Ready
        assertEquals("a code made here is recognised when it comes back",
            1, found.known)
        Notebook.applyShareCode(load, found.notes, update = false)
        assertEquals(3, onDisk(load).length())
        assertEquals("the copy cannot wear the original's identity",
            onDisk(load).getJSONObject(0).getString("uid") ==
                onDisk(load).getJSONObject(2).getString("uid"), false)
    }

    @Test
    fun `several notes in one code all arrive, with distinct ids`() {
        val load = scratch("""[{"id": 9}]""")
        val code = Sharing.encode(
            listOf(note("""{"title": "a"}"""), note("""{"title": "b"}""")))
        assertEquals(2, (bring(load, code) as SaveResult.Ok).imported)
        val ids = (0 until onDisk(load).length()).map { onDisk(load).getJSONObject(it).getInt("id") }
        assertEquals(listOf(9, 10, 11), ids)
    }

    @Test
    fun `a bad code is refused before anything is written`() {
        val load = scratch("""[{"id": 1}]""")
        val before = load.file!!.readText()
        assertTrue(bring(load, "not a code") is SaveResult.Failed)
        assertEquals(before, load.file!!.readText())
    }

    @Test
    fun `a notebook that cannot be read is kept aside before a code is imported`() {
        val load = scratch("{ not json")
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        assertTrue(bring(load, code) is SaveResult.Ok)
        val kept = load.file!!.parentFile!!.listFiles()!!
            .single { it.name.startsWith("notes_data_corrupt_") }
        assertEquals("{ not json", kept.readText())
    }

    // ---- making a code from a stored note ----

    @Test
    fun `a code is made from the note on disk, not from the screen's model`() {
        // The Kotlin model drops fields a code is meant to carry, so the
        // code comes off the raw JSON.
        val load = scratch("""
            [{"id": 3, "title": "x", "comments": "footnotes",
              "paragraphs": [{"line": 0, "kind": "bullet"}]}]
        """.trimIndent())
        val code = Notebook.shareCodeFor(load, 3)!!
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("footnotes", back.getString("comments"))
        assertEquals(1, back.getJSONArray("paragraphs").length())
    }

    @Test
    fun `asking for a code for a note that is gone gives nothing, not a crash`() {
        val load = scratch("""[{"id": 1}]""")
        assertEquals(null, Notebook.shareCodeFor(load, 99))
    }
}
