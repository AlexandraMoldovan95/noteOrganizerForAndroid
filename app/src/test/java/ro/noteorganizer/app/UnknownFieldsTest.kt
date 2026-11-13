package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A note's fields this version does not know are kept, as the desktop keeps
 * them (the owner's decision D2, 2026-11-13): answer for answer against the
 * desktop's own `unknown_fields` (unknown_cases.json, tools/make_unknown_fixture.py),
 * and through what the phone does with a note -- its load, its signatures,
 * a backup's Update and a share code's.
 */
class UnknownFieldsTest {

    /** A JSON value as text that compares by meaning: keys sorted, numbers by value. */
    private fun canon(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canon(value.opt(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canon(value.opt(it)) }
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        null, JSONObject.NULL -> "null"
        else -> "?" + value
    }

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("unknown_cases.json")
        assertNotNull("unknown_cases.json is missing - rebuild it with tools/make_unknown_fixture.py", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `every note in the fixture keeps the fields the desktop keeps, and no others`() {
        val cases = fixture().getJSONArray("cases")
        assertTrue(cases.length() > 250)
        val wrong = ArrayList<String>()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val got = canon(Normalize.unknownFields(case.getJSONObject("raw")))
            val want = canon(case.getJSONObject("kept"))
            if (got != want) wrong.add("#$i\n  desktop ${want.take(200)}\n  phone   ${got.take(200)}")
        }
        assertEquals(wrong.take(3).joinToString("\n"), 0, wrong.size)
    }

    @Test
    fun `a note's load keeps them, but not an app's mark, the Trash's stamp or the removed tags`() {
        val note = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "x", "mood": "calm",
            "weather": {"t": 3}, "_mark": 1, "deleted_at": "2026-11-01 10:00:00", "tags": ["old"]}"""))
        assertEquals("calm", note.opt("mood"))
        assertEquals("{\"t\":3}", canon(note.opt("weather")))
        assertFalse(note.has("_mark") || note.has("deleted_at") || note.has("tags"))
    }

    @Test
    fun `two notes differing only in such a field are two notes to both signatures`() {
        val a = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Same", "body": "b", "mood": "calm"}"""))
        val b = Normalize.normalizeNote(JSONObject("""{"id": 2, "title": "Same", "body": "b", "mood": "storm"}"""))
        assertNotEquals(Normalize.contentSignature(a), Normalize.contentSignature(b))
        assertNotEquals(Normalize.dupKey(a), Normalize.dupKey(b))
    }

    @Test
    fun `a backup's Update brings its own beside this note's, and a share code's never does`() {
        val mine = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Old", "mood": "calm", "mine": "kept"}"""))
        assertTrue(Notebook.applyUpdate(mine, JSONObject("""{"title": "New", "mood": "storm", "theirs": "x"}"""),
                                        "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_BACKUP))
        assertEquals(listOf("New", "storm", "kept", "x"),
                     listOf("title", "mood", "mine", "theirs").map { mine.opt(it) })
        val coded = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Old", "mood": "calm"}"""))
        assertTrue(Notebook.applyUpdate(coded, JSONObject("""{"title": "New", "mood": "storm"}"""),
                                        "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_SHARED))
        assertEquals(listOf("New", "calm"), listOf("title", "mood").map { coded.opt(it) })
        // ...and such a field alone is no change for an Update to make.
        assertFalse(Notebook.sharedUpdateChanges(Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "T", "mood": "a"}""")),
                                                 JSONObject("""{"title": "T", "mood": "b"}"""), Notebook.UPDATABLE_FROM_BACKUP))
    }

    @Test
    fun `Restore previous after a backup's Update puts back a field this version does not know`() {
        val note = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Mine", "body": "b", "reminder": "10:00"}"""))
        assertTrue(Notebook.applyUpdate(note, JSONObject("""{"title": "Older", "body": "b", "reminder": "09:00"}"""),
                                        "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_BACKUP))
        assertEquals("09:00", note.opt("reminder"))
        val load = scratch(JSONArray().put(note).toString())
        assertTrue(Notebook.restorePrevious(load, 1) is SaveResult.Ok)
        val back = onDisk(load).getJSONObject(0)
        assertEquals(listOf("Mine", "10:00"), listOf(back.opt("title"), back.opt("reminder")))
    }

    @Test
    fun `the two sets together are held to the bounds at once, and a huge list is left out unwalked`() {
        val mine = JSONObject("""{"id": 1, "title": "T"}""")
        for (i in 0 until 30) mine.put("m%02d".format(i), i)
        val note = Normalize.normalizeNote(mine)
        val theirs = JSONObject("""{"title": "U"}""")
        for (i in 0 until 7) theirs.put("z%02d".format(i), i)
        Notebook.applyUpdate(note, theirs, "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_BACKUP)
        // The note's own keys, not unknownFields' reading of them, which bounds them as it reads.
        assertEquals((0 until 30).map { "m%02d".format(it) } + listOf("z00", "z01"),
                     note.keys().asSequence().filter { Regex("[mz]\\d\\d").matches(it) }.sorted().toList())
        val huge = JSONArray()
        for (i in 0..Normalize.MAX_UNKNOWN_UNITS) huge.put(0)
        assertFalse(Normalize.normalizeNote(JSONObject().put("id", 1).put("huge", huge)).has("huge"))
    }

    @Test
    fun `a copy whose own 1 the backup wrote over as true is not spent, one the backup only added to is`() {
        val spend = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Old", "flag": 1}"""))
        Notebook.applyUpdate(spend, JSONObject("""{"title": "New", "flag": true}"""),
                             "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_BACKUP)
        spend.put("title", "Old")
        val added = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "Old"}"""))
        Notebook.applyUpdate(added, JSONObject("""{"title": "New", "extra": "x"}"""),
                             "2026-11-13 12:00:00", Notebook.UPDATABLE_FROM_BACKUP)
        added.put("title", "Old")
        assertTrue(Notebook.holdsUnspentCopy(spend))
        assertFalse(Notebook.holdsUnspentCopy(added))
    }

    @Test
    fun `a share code carries none`() {
        val note = Normalize.normalizeNote(JSONObject("""{"id": 1, "title": "x", "mood": "calm"}"""))
        val decoded = Sharing.decode(Sharing.encode(listOf(note)))
        assertTrue(decoded is Sharing.Decoded.Notes)
        assertFalse((decoded as Sharing.Decoded.Notes).notes.single().has("mood"))
    }
}
