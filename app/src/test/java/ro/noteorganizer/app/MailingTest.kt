package ro.noteorganizer.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A note sent by email: the addresses, and what the message carries. */
class MailingTest {

    private val trip = JSONObject("""{
        "title": "Trip / packing",
        "body": "Bring:\n| Item | Qty |\n| --- | --- |\n| socks | 3 |",
        "checklist": [{"text": "passport", "done": true}],
        "attachments": [{"name": "map.png", "stored": "a1_map.png"}]
    }""")

    @Test
    fun `the addresses typed, however they were separated`() {
        assertEquals(listOf("a@x.ro", "b@y.com", "c@z.org"), Mailing.addresses(" a@x.ro, b@y.com;c@z.org  "))
        assertEquals(listOf("a@x.ro", "b@y.com"), Mailing.addresses("a@x.ro b@y.com\n"))
        assertEquals(emptyList<String>(), Mailing.addresses("  , ; "))
    }

    @Test
    fun `a typed slip is named back, an address is not, and an empty field is no slip`() {
        assertNull(Mailing.notAnAddress(""))
        assertNull(Mailing.notAnAddress("maria.pop@mail.co.uk, ion@x.ro"))
        assertEquals("maria", Mailing.notAnAddress("ion@x.ro, maria"))
        assertEquals("maria@gmail", Mailing.notAnAddress("maria@gmail"))
        assertEquals("@gmail.com", Mailing.notAnAddress("@gmail.com"))
        assertEquals("a@b@c.com", Mailing.notAnAddress("a@b@c.com"))
        assertEquals("a@.com", Mailing.notAnAddress("a@.com"))
        assertEquals("a@b..com", Mailing.notAnAddress("a@b..com"))
    }

    @Test
    fun `the words are the note as a text export writes them, the page as a web page export does`() {
        val title = Exporting.titleOf(trip)
        val both = Mailing.message(trip, "a@x.ro", withText = true, withPage = true)
        assertEquals(listOf("a@x.ro"), both.to)
        assertEquals("Trip / packing", both.subject)
        assertEquals(Exporting.content(trip, Exporting.TXT, title), both.text)
        assertEquals(Exporting.fileName(title, Exporting.HTML), both.attachment?.first)
        assertEquals(Exporting.content(trip, Exporting.HTML, title), both.attachment?.second)
        // The page draws the table; the words keep its rows, and the checklist goes in both.
        assertTrue(both.attachment!!.second.contains("<table"))
        assertTrue(both.text!!.contains("| socks | 3 |"))
        assertTrue(both.text!!.contains("[x] passport") && both.attachment!!.second.contains("passport"))
    }

    @Test
    fun `only what was chosen goes, and an untitled note is called so`() {
        val words = Mailing.message(trip, "", withText = true, withPage = false)
        assertNull(words.attachment)
        assertEquals(emptyList<String>(), words.to)
        val page = Mailing.message(trip, "", withText = false, withPage = true)
        assertNull(page.text)
        val untitled = Mailing.message(JSONObject("""{"title": "  ", "body": "x"}"""), "", true, true)
        assertEquals("Untitled note", untitled.subject)
        assertEquals("Untitled note.html", untitled.attachment?.first)
    }

    @Test
    fun `a note's own files are said, since the message does not carry them`() {
        assertTrue(Mailing.hasFiles(trip))
        assertFalse(Mailing.hasFiles(JSONObject("""{"title": "x"}""")))
        assertFalse(Mailing.hasFiles(JSONObject("""{"attachments": []}""")))
    }
}
