package com.example.noteorganizer

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
 * the Windows app's own `encode_share_code`, from one of a real
 * notebook's notes. The code this test writes back out is then decoded by
 * that same Python. Neither side is asked to trust the other's word.
 *
 * If this file is missing, the test says so rather than passing quietly —
 * a cross-program check that silently skips is worse than none.
 */
class InteropTest {

    private fun desktopCode(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("desktop_code.txt")
        assertNotNull(
            "desktop_code.txt lipseste - regenereaza-l cu encode_share_code din Python",
            stream,
        )
        return stream!!.bufferedReader().readText()
    }

    @Test
    fun `a code made by the DESKTOP is read here, field for field`() {
        val decoded = Sharing.decode(desktopCode())
        assertTrue("codul desktopului nu s-a putut citi: $decoded",
            decoded is Sharing.Decoded.Notes)
        val note = (decoded as Sharing.Decoded.Notes).notes.single()

        // The note this was made from: title "mate", 3 formatting spans,
        // 8 checklist rows, a comment.
        assertEquals("mate", note.getString("title"))
        assertEquals(3, note.getJSONArray("formatting").length())
        assertEquals(8, note.getJSONArray("checklist").length())
        assertEquals("bfdbdf", note.getString("comments"))
        // And the body's maths characters came through unharmed.
        assertTrue(note.getString("body").contains("√"))   // the root sign
    }

    @Test
    fun `a code made HERE is written out for the desktop to check`() {
        // Not an assertion about the desktop -- a hand-off. The Python
        // side reads this file and decodes it with its own function; that
        // check is run beside the suite, not inside it.
        val note = org.json.JSONObject()
        note.put("title", "De pe telefon")
        note.put("body", "Mașină 👣 și ștefan\nlinia a doua")
        note.put("category", "Work")
        note.put("comments", "un comentariu")
        note.put("formatting", org.json.JSONArray().put(
            org.json.JSONObject().put("start", 0).put("end", 7).put("style", "bold")))
        note.put("checklist", org.json.JSONArray().put(
            org.json.JSONObject().put("text", "un rand").put("done", true)))

        val code = Sharing.encode(listOf(note))
        val out = File("build/interop")
        out.mkdirs()
        File(out, "phone_code.txt").writeText(code)

        // It must at least survive its own round trip before being handed on.
        val back = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("De pe telefon", back.getString("title"))
    }
}
