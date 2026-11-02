package ro.noteorganizer.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The test that decides whether share codes are a transfer mechanism at
 * all: **a code made by one program has to be readable by the other.**
 *
 * Both halves are real, not simulated. `desktop_code.txt` was produced by
 * the Windows app's own `encode_share_code`. The code this test writes
 * back out is decoded by that same Python's `decode_share_code` in
 * `tools/check_interop.py`, run after the tests. Neither side is asked to
 * trust the other's word.
 *
 * The note inside the fixture is invented, on purpose. What gives this
 * test its value is that the DESKTOP encoded it — not that somebody's
 * real writing is sitting in it. It used to hold a real note, and a share
 * code is not encryption: anyone who can read this repository can read
 * the note. So the note is now written for the test, carrying exactly the
 * properties the test cares about.
 *
 * If this file is missing, the test says so rather than passing quietly —
 * a cross-program check that silently skips is worse than none.
 */
class InteropTest {

    private fun desktopCode(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("desktop_code.txt")
        assertNotNull(
            "desktop_code.txt is missing - rebuild it with tools/make_interop_fixture.py",
            stream,
        )
        return stream!!.bufferedReader().readText()
    }

    @Test
    fun `a code made by the DESKTOP is read here, field for field`() {
        val decoded = Sharing.decode(desktopCode())
        assertTrue("the desktop's code could not be read: $decoded",
            decoded is Sharing.Decoded.Notes)
        val note = (decoded as Sharing.Decoded.Notes).notes.single()

        // What the fixture's note carries: 3 formatting spans, 8 checklist
        // rows, a comment. Regenerate it with the script in the desktop's
        // own Python if it ever has to change.
        assertEquals("Sums for the test", note.getString("title"))
        assertEquals(3, note.getJSONArray("formatting").length())
        assertEquals(8, note.getJSONArray("checklist").length())
        assertEquals("a comment, so there is one of those to carry too",
            note.getString("comments"))
        // And the body's maths characters, diacritics and emoji came through
        // unharmed -- the emoji written by the desktop as four raw bytes.
        assertTrue(note.getString("body").contains("√"))   // the root sign
        assertTrue(note.getString("body").contains("ștefan, mașină, țara"))
        assertTrue(note.getString("body").contains("Steps 👣 counted"))
    }

    @Test
    fun `a code made HERE is written out for the desktop to check`() {
        // Not an assertion about the desktop -- a hand-off: the code, and
        // what the phone meant to send, for tools/check_interop.py to read
        // as the desktop reads a note (decode, then normalize_note) and
        // compare. The note is shaped by the phone's own choke point and
        // carries every field a code does.
        val note = Normalize.normalizeNote(org.json.JSONObject()
            .put("uid", "11111111-2222-4333-8444-555555555555")
            .put("title", "From the phone")
            .put("body", "Mașină 👣 și ștefan\n• a bullet\nthe third line")
            .put("category", "Work")
            .put("comments", "a comment")
            .put("date", "2026-11-02")
            .put("color", "#7c5cff")
            .put("recurrence", org.json.JSONObject().put("freq", "weekly").put("interval", 1)
                .put("weekdays", org.json.JSONArray().put(0)))
            .put("formatting", org.json.JSONArray().put(
                org.json.JSONObject().put("start", 0).put("end", 7).put("style", "bold")))
            .put("paragraphs", org.json.JSONArray().put(
                org.json.JSONObject().put("line", 1).put("kind", "bullet")))
            .put("checklist", org.json.JSONArray().put(
                org.json.JSONObject().put("text", "a row").put("done", true))))
        val sent = org.json.JSONObject()
        for (field in Sharing.SHAREABLE_FIELDS) if (note.has(field)) sent.put(field, note.get(field))
        assertEquals("every field a code carries is in the note sent",
            Sharing.SHAREABLE_FIELDS.size, sent.length())

        val code = Sharing.encode(listOf(note))
        val out = File("build/interop")
        out.mkdirs()
        File(out, "phone_code.txt").writeText(code)
        File(out, "phone_note.json").writeText(sent.toString())

        // It must at least survive its own round trip before being handed on.
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("From the phone", back.getString("title"))
    }
}
