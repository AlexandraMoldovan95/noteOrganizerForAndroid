package ro.noteorganizer.app

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
    fun `a note past what a code may hold is refused before it is deflated`() {
        // Weighed after the deflate at level 9, the dearest step, such a note
        // paid all of it to be refused, on the screen's thread. Timed against writing
        // its text out once, the work the refusal still does, so no machine moves the margin.
        val words = listOf("milk", "bread", "a long day", "walked to the river", "Tuesday", "7:30")
        val random = java.util.Random(11)
        val body = StringBuilder()
        while (body.length <= Sharing.MAX_DECOMPRESSED_BYTES) body.append(words[random.nextInt(words.size)]).append(' ').append(random.nextInt(1000)).append('\n')
        val big = note("{}").put("body", body.toString())
        fun ms(work: () -> Unit): Long {
            val started = System.nanoTime()
            work()
            return (System.nanoTime() - started) / 1_000_000
        }
        fun refused() = try { Sharing.encode(listOf(big)); false } catch (e: Sharing.TooLarge) { true }
        assertTrue(refused())
        val written = (1..2).minOf { ms { big.toString().toByteArray(Charsets.UTF_8) } }
        val weighed = (1..2).minOf { ms { assertTrue(refused()) } }
        assertTrue("refused in $weighed ms, the text written out in $written ms", weighed <= 4 * written + 150)
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

    @Test
    fun `a code holding a list holds no notes, as the desktop says, and a bare word is damage`() {
        // decode_share_code's own answers: a list is whole JSON with no
        // notes in it; text Python's reader refuses is damage.
        for (text in listOf("[]", " [1, 2]")) {
            val result = Sharing.decode(codeOfText(text)) as Sharing.Decoded.Failed
            assertEquals(text, "This share code doesn't contain any notes.", result.reason)
        }
        val word = Sharing.decode(codeOfText("abc")) as Sharing.Decoded.Failed
        assertTrue(word.reason, word.reason.startsWith("This share code is damaged"))
    }

    @Test
    fun `a code holding one plain value holds no notes, and text after its object is damage, as the desktop says`() {
        // decode_share_code's answers, asked 2026-11-13: the phone called the
        // first damaged, and took the last two as whole.
        for (text in listOf("42", " \"hello\" ", "true", "null", "NaN", "-Infinity", " -1.5e3 ")) {
            val result = Sharing.decode(codeOfText(text)) as Sharing.Decoded.Failed
            assertEquals(text, "This share code doesn't contain any notes.", result.reason)
        }
        for (text in listOf("01", "1.", ".5", "\"tab\there\"", "{\"notes\": []} x", "{\"notes\": [{\"title\": \"a\"}]} {}")) {
            val result = Sharing.decode(codeOfText(text)) as Sharing.Decoded.Failed
            assertTrue(text + ": " + result.reason, result.reason.startsWith("This share code is damaged"))
        }
        // One long string, plain or all escapes: matched by a regex, it recursed
        // past the thread's stack, an Error no catch here takes.
        for (long in listOf("\"" + "a".repeat(300_000) + "\"", "\"" + "\\n".repeat(150_000) + "\"")) {
            val result = Sharing.decode(codeOfText(long)) as Sharing.Decoded.Failed
            assertEquals("This share code doesn't contain any notes.", result.reason)
        }
        val unclosed = Sharing.decode(codeOfText("\"abc\\\"")) as Sharing.Decoded.Failed
        assertTrue(unclosed.reason, unclosed.reason.startsWith("This share code is damaged"))
    }

    /** Builds a code around an arbitrary payload, the way a forger would. */
    private fun codeOf(payload: JSONObject): String = codeOfText(payload.toString())

    /** ...or around any text at all. */
    private fun codeOfText(text: String): String {
        val deflate = java.util.zip.Deflater(9)
        val raw = text.toByteArray(Charsets.UTF_8)
        deflate.setInput(raw)
        deflate.finish()
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflate.finished()) out.write(buffer, 0, deflate.deflate(buffer))
        deflate.end()
        return Sharing.PREFIX + java.util.Base64.getUrlEncoder()
            .encodeToString(out.toByteArray())
    }

    @Test
    fun `a code whose words are not UTF-8 is damage, never letters turned into marks`() {
        // Read leniently, its letters came in as replacement marks; the
        // desktop's decode("utf-8") refuses such a code.
        val title = Char(0x0218) + "tampil" + Char(0x0103)
        val raw = "{\"v\": 1, \"notes\": [{\"title\": \"$title\"}]}".toByteArray(charset("windows-1250"))
        val deflate = java.util.zip.Deflater(9)
        deflate.setInput(raw)
        deflate.finish()
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflate.finished()) out.write(buffer, 0, deflate.deflate(buffer))
        deflate.end()
        val code = Sharing.PREFIX + java.util.Base64.getUrlEncoder().encodeToString(out.toByteArray())
        assertTrue(Sharing.decode(code) is Sharing.Decoded.Failed)
    }

    // ---- nesting deeper than any note ----

    @Test
    fun `a code nested deeper than any note is damage, refused before it is parsed`() {
        // Android's org.json goes a call deeper per bracket; the computer's parser
        // stops at 512 by itself, so this nests 150 deep: that parser takes it,
        // and only the guard refuses it.
        val nested = { depth: Int -> "[".repeat(depth) + "]".repeat(depth) }
        val code = codeOf(JSONObject("""{"v": 1, "notes": [{"title": "x", "deep": ${nested(150)}}]}"""))
        val result = Sharing.decode(code)
        assertTrue(result is Sharing.Decoded.Failed)
        assertTrue((result as Sharing.Decoded.Failed).reason.startsWith("This share code is damaged"))
        // A note's own depth is far inside the line.
        val fine = codeOf(JSONObject("""{"v": 1, "notes": [{"title": "x", "deep": ${nested(20)}}]}"""))
        assertTrue(Sharing.decode(fine) is Sharing.Decoded.Notes)
    }

    // ---- what a pasted code can be ----

    @Test
    fun `whitespace from a chat app is not damage`() {
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        val wrapped = code.chunked(40).joinToString("\n  ")
        assertTrue(Sharing.decode(wrapped) is Sharing.Decoded.Notes)
    }

    @Test
    fun `a NEL inside a code is wrapping, as Python's whitespace is`() {
        // Python's \s takes U+0085; isWhitespace does not, and the phone cut
        // the code at it and called it damaged where the desktop read it.
        val code = Sharing.encode(listOf(note("""{"title": "x"}""")))
        val middle = Sharing.PREFIX.length + 8
        assertTrue(Sharing.decode(code.take(middle) + "\u0085" + code.drop(middle)) is Sharing.Decoded.Notes)
    }

    @Test
    fun `what the desktop's base64 reading drops is dropped here too`() {
        // A code ending a sentence, or followed by a closing quote or
        // bracket, imported on the computer and was called damaged on the
        // phone. (Before the code, either device reads it as no code.)
        val code = Sharing.encode(listOf(note("""{"title": "Lista de cumpărături"}""")))
        val middle = Sharing.PREFIX.length + 8
        // And the invisible break marks a chat app puts into a long word.
        for (pasted in listOf("$code.", "$code\"", "$code)", "$code>", code.take(middle) + "​" + code.drop(middle),
                              code.take(middle) + "­" + code.drop(middle))) {
            val result = Sharing.decode(pasted)
            assertTrue(pasted.takeLast(3), result is Sharing.Decoded.Notes)
            assertEquals("Lista de cumpărături", (result as Sharing.Decoded.Notes).notes.single().getString("title"))
        }
    }

    @Test
    fun `a code is found among the words around it, padded or not, as the desktop's code_in finds it`() {
        // A greeting before it, a mail signature or "Thanks" after it: read
        // only now and then, as the code's padding happened to fall.
        for (title in listOf("x", "xy", "xyz", "wxyz")) {
            val code = Sharing.encode(listOf(note("""{"title": "$title"}""")))
            for (pasted in listOf("Hi Ana!\n$code\nThanks", "$code Sent from my phone",
                                  "Here: ${code.take(30)}\n${code.drop(30)}", "$code and NOTE1:another")) {
                val result = Sharing.decode(pasted)
                assertTrue("$title in ${pasted.take(12)}…", result is Sharing.Decoded.Notes)
                assertEquals(title, (result as Sharing.Decoded.Notes).notes.single().getString("title"))
            }
            // ...and a code whose "=" was lost on the way still reads, as it did
            // before the cut for words after a code.
            assertTrue("$title without its padding", Sharing.decode(code.trimEnd('=')) is Sharing.Decoded.Notes)
        }
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
    // By the screen's own route -- inspect, then write with the answer -- not
    // a function nothing calls, which a check would keep alive for nothing.

    /** What the screen does: look first, then write with the answer. */
    private fun bring(load: Notebook.Load, code: String,
                      update: Boolean = false): SaveResult {
        val found = Notebook.inspectShareCode(load, code)
        if (found is Notebook.Inspection.Failed) return SaveResult.Failed(found.reason)
        return Shares.apply(
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
        // A code with no identity is always created: two notes are visible and
        // fixable where overwriting the wrong one is not.
        val load = scratch("[]")
        val anonymous = Sharing.encode(listOf(note("""{"title": "x"}""")))
        bring(load, anonymous)
        bring(load, anonymous)
        assertEquals("a code with no identity makes two notes, as before",
            2, onDisk(load).length())
        assertEquals(listOf(1, 2),
            (0 until 2).map { onDisk(load).getJSONObject(it).getInt("id") })

        // A code made from a SAVED note carries its identity, so the second
        // arrival is recognised, not guessed at: two notes here now because
        // that answer was chosen, not because nothing could tell.
        val mine = Shares.codeFor(load, 1)!!
        val found = Notebook.inspectShareCode(load, mine) as Notebook.Inspection.Ready
        assertEquals("a code made here is recognised when it comes back",
            1, found.known)
        Shares.apply(load, found.notes, update = false)
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
        val code = Shares.codeFor(load, 3)!!
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("footnotes", back.getString("comments"))
        assertEquals(1, back.getJSONArray("paragraphs").length())
    }

    // ---- a text file shared in ----

    private fun shared(bytes: ByteArray, most: Int = 500_000) = Files.sharedText(bytes.inputStream(), most)

    @Test
    fun `a shared file in UTF-16 is read as its letters, not letters and NULs`() {
        val words = "Cumpărături de joi"
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + words.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + words.toByteArray(Charsets.UTF_16BE)
        assertEquals(words, shared(le)?.first)
        assertEquals(words, shared(be)?.first)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + words.toByteArray()
        assertEquals(words, shared(bom)?.first)
    }

    @Test
    fun `a shared file of a BOM, spaces or a NEL alone holds no text`() {
        assertEquals(null, shared(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())))
        assertEquals(null, shared(" \n\t ".toByteArray()))
        assertEquals(null, shared("\u0085".toByteArray()))
        assertEquals(null, shared(ByteArray(0)))
    }

    @Test
    fun `a shared file not in UTF-8 is refused, not read as replacement marks`() {
        // Notepad's old "ANSI" on a Romanian Windows: ă is 0xE3 in code page 1250.
        val ansi = "mere, p".toByteArray() + byteArrayOf(0xE2.toByte()) + "ine".toByteArray()
        var refused = false
        try {
            shared(ansi)
        } catch (e: java.io.IOException) {
            refused = true
        }
        assertTrue(refused)
    }

    @Test
    fun `a shared file in UTF-16 without its BOM is refused, not read as letters and NULs`() {
        val bare = "Shopping list: milk, eggs".toByteArray(Charsets.UTF_16LE)
        var refused = false
        try {
            shared(bare)
        } catch (e: java.io.IOException) {
            refused = true
        }
        assertTrue(refused)
    }

    @Test
    fun `NULs padding a shared file's end are left out, the file read`() {
        assertEquals("Shopping list", shared("Shopping list".toByteArray() + ByteArray(8))?.first)
    }

    @Test
    fun `a code followed by more equals signs reads, as the desktop reads it`() {
        val code = Sharing.encode(listOf(note("""{"id": 1, "title": "Padded", "body": "x"}""")))
        for (tail in listOf("=)", "\n=====", "==")) {
            assertTrue(tail, Sharing.decode(code + tail) is Sharing.Decoded.Notes)
        }
    }

    @Test
    fun `a code that lost one of its two equals signs is damaged, as on the desktop`() {
        // Python refuses too little padding ("Incorrect padding"); one "=" of
        // two lost must not read here either.
        val code = (1..60).asSequence()
            .map { Sharing.encode(listOf(note("""{"id": 1, "title": "${"x".repeat(it)}", "body": "y"}"""))) }
            .first { it.endsWith("==") }
        assertTrue(Sharing.decode(code) is Sharing.Decoded.Notes)
        assertTrue(Sharing.decode(code.dropLast(1)) is Sharing.Decoded.Failed)
    }

    @Test
    fun `an emoji cut in half at the end leaves no half behind`() {
        val text = "abcd😀"   // an emoji at characters 5 and 6
        val got = shared(text.toByteArray(), most = 5)
        assertEquals("abcd", got?.first)
        assertEquals(true, got?.second)
    }

    @Test
    fun `a shared file is said cut only when it went on past what is read`() {
        assertEquals(false, shared("abcde".toByteArray(), most = 5)?.second)
        assertEquals(true, shared("abcdef".toByteArray(), most = 5)?.second)
        assertEquals("abcde", shared("abcdef".toByteArray(), most = 5)?.first)
    }
}
