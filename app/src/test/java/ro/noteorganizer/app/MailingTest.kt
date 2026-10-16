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
    fun `the words say a table's rows by their columns and draw the checklist's boxes, the PDF is the web page`() {
        val title = Exporting.titleOf(trip)
        val both = Mailing.message(trip, "a@x.ro", withText = true, withPage = true)
        assertEquals(listOf("a@x.ro"), both.to)
        assertEquals("Trip / packing", both.subject)
        assertEquals("Trip / packing\n\nBring:\n• Item: socks · Qty: 3\n\nChecklist:\n☑ passport\n", both.text)
        assertEquals("Trip _ packing.pdf", both.attachment?.first)
        // What is printed to the PDF is the export's page, the table drawn.
        assertEquals(Exporting.content(trip, Exporting.HTML, title), both.attachment?.second)
        assertTrue(both.attachment!!.second.contains("<table"))
    }

    @Test
    fun `a table's rows as lines -- empty cells, empty rows, a header with nothing in it`() {
        assertEquals(listOf("• socks · Qty: 3"),
                     Mailing.tableLines(listOf("|  | Qty |", "| --- | --- |", "| socks | 3 |", "|  |  |")))
        assertEquals("a table with only its header is that row", listOf("• A · B"),
                     Mailing.tableLines(listOf("| A | B |", "| --- | --- |")))
        assertEquals("no separator: the first row is still the header, as the page draws it",
                     listOf("• a: c · b: d"), Mailing.tableLines(listOf("| a | b |", "| c | d |")))
    }

    @Test
    fun `a hidden column stays hidden, a merged cell is said once, boxes and breaks are words`() {
        assertEquals(listOf("• A: 1"), Mailing.tableLines(listOf("| A | B |", "| --- | ~ |", "| 1 | 2 |")))
        assertEquals(listOf("• A: wide · C: x"),
                     Mailing.tableLines(listOf("| A | B | C |", "| --- | --- | --- |", "| wide | < | x |")))
        assertEquals(listOf("• Task: gym · Done: ☑", "• Task: call · Done: ☐"),
                     Mailing.tableLines(listOf("| Task | Done |", "| --- | --- |", "| gym | [x] |", "| call | [ ] |")))
        assertEquals(listOf("• Note: two lines · Day: Mon"),
                     Mailing.tableLines(listOf("| Note | Day |", "| --- | --- |", "| two<br>lines | Mon |")))
    }

    @Test
    fun `the prose around a table is kept as it is, and a box is ticked by the desktop's own truth`() {
        assertEquals("Intro\n• A: 1 · B: 2\nAfter", Mailing.readable("Intro\n| A | B |\n| --- | --- |\n| 1 | 2 |\nAfter"))
        assertEquals("No table | here", Mailing.readable("No table | here"))
        val ticked = JSONObject("""{"title": "T", "body": "", "checklist": [{"text": "a", "done": 1}, {"text": "b", "done": 0}]}""")
        assertTrue(Mailing.text(ticked, "T").contains("☑ a\n☐ b"))
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
        assertEquals("Untitled note.pdf", untitled.attachment?.first)
    }

    @Test
    fun `a note's own files are said, since the message does not carry them`() {
        assertTrue(Mailing.hasFiles(trip))
        assertFalse(Mailing.hasFiles(JSONObject("""{"title": "x"}""")))
        assertFalse(Mailing.hasFiles(JSONObject("""{"attachments": []}""")))
    }
}
