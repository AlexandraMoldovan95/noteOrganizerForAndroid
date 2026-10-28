package ro.noteorganizer.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A note shared: as what it may go, and what goes. */
class OutgoingTest {

    private val trip = JSONObject("""{
        "title": "Trip / packing",
        "body": "Bring:\n| Item | Qty |\n| --- | --- |\n| socks | 3 |",
        "checklist": [{"text": "passport", "done": true}],
        "attachments": [{"name": "map.png", "stored": "a1_map.png"}]
    }""")

    @Test
    fun `a note with a table is not offered as words, which cannot draw one`() {
        // The owner found a table written as lines in a message hard to read.
        assertTrue(Outgoing.hasTable(trip))
        assertTrue("a row left open is a table to a reader too",
                   Outgoing.hasTable(JSONObject("""{"body": "x\n| a | b"}""")))
        assertTrue("in the comments as well", Outgoing.hasTable(JSONObject("""{"body": "x", "comments": "| a | b |\n| 1 | 2 |"}""")))
        assertFalse(Outgoing.hasTable(JSONObject("""{"body": "No table | here"}""")))
    }

    @Test
    fun `the words go with the checklist's boxes drawn, and the comments`() {
        val note = JSONObject("""{"title": "Shop", "body": "milk\neggs", "comments": "  from Ana  ",
            "checklist": [{"text": "bread", "done": 1}, {"text": "jam", "done": 0}]}""")
        assertEquals("Shop\n\nmilk\neggs\n\nChecklist:\n☑ bread\n☐ jam\n\nComments:\nfrom Ana\n",
                     Outgoing.text(note, "Shop"))
    }

    @Test
    fun `half an emoji goes as the replacement mark, as the export writes it`() {
        // A note cut in the middle of an emoji by an older editor: handed on
        // as it is, the half is not a character the other app can read.
        val words = Outgoing.text(JSONObject().put("body", "milk \uD83D"), "Shop")
        assertEquals("Shop\n\nmilk �\n", words)
    }

    @Test
    fun `a page and a PDF are named as the export names them`() {
        val title = Exporting.titleOf(trip)
        assertEquals("Trip _ packing.html", Outgoing.fileName(title, Outgoing.PAGE))
        assertEquals("Trip _ packing.pdf", Outgoing.fileName(title, Outgoing.PDF))
        assertEquals("Untitled note.pdf",
                     Outgoing.fileName(Exporting.titleOf(JSONObject("""{"title": "  ", "body": "x"}""")), Outgoing.PDF))
    }

    @Test
    fun `a share asked for while one is made is not started over it`() = runBlocking {
        val gate = Outgoing.OneAtATime()
        val held = CompletableDeferred<Unit>()
        var made = 0
        // The first held where a PDF is being drawn, as a long note's is.
        val first = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(gate.run { made++; held.await() }) }
        assertFalse("the second is refused", gate.run { made++ })
        assertEquals("and never started", 1, made)
        held.complete(Unit)
        first.join()
        assertTrue("the next, once the first is done, is made", gate.run { made++ })
        assertEquals(2, made)
    }

    @Test
    fun `a share that failed lets the next one through`() = runBlocking {
        val gate = Outgoing.OneAtATime()
        assertTrue(runCatching { gate.run { throw IllegalStateException("the PDF failed") } }.isFailure)
        var made = false
        assertTrue(gate.run { made = true })
        assertTrue(made)
    }

    @Test
    fun `words past what may be handed over are not offered, and a very long title is a short subject`() {
        // A journal of 304,000 characters, words and lines, no table: handed
        // as words it stopped the app at the share sheet, a megabyte past
        // the buffer Android passes a share through.
        val journal = JSONObject().put("title", "Journal")
            .put("body", "Today I walked to the river and back again.\n".repeat(304_000 / 44))
        assertFalse(Outgoing.hasTable(journal))
        assertTrue(Outgoing.text(journal, "Journal").length > Sharing.MAX_HANDED_CHARS)
        assertTrue(Outgoing.tooLongForWords(journal))
        assertFalse(Outgoing.tooLongForWords(trip))
        // The ceiling is the code's, counted with the subject.
        val subject = "Notes"
        assertFalse(Outgoing.tooLongForWords("w".repeat(Sharing.MAX_HANDED_CHARS - subject.length), subject))
        assertTrue(Outgoing.tooLongForWords("w".repeat(Sharing.MAX_HANDED_CHARS - subject.length + 1), subject))
        // A title of 150,000 letters goes as a subject of a glimpse.
        val long = "T".repeat(150_000)
        val cut = Outgoing.subject(long)
        assertTrue(cut.length <= 301)
        assertTrue(cut.endsWith("…"))
        assertEquals("Trip to Cluj", Outgoing.subject("Trip to Cluj"))
        assertTrue("a long title alone is past it, with a one-line body",
            Outgoing.tooLongForWords(JSONObject().put("title", "T".repeat(260_000)).put("body", "x")))
    }

    @Test
    fun `the page printed to a PDF breaks any line at the paper's edge, and the page shared stays the desktop's`() {
        // Only a table's cells could break anywhere; a link's unbreakable
        // tail ran past the margin of a PDF drawn at the paper's width, and
        // was cut away with nothing said.
        val link = "https://www.example-shop.ro/produs/espressor?utm_source=newsletter&utm_medium=email&utm_campaign=octombrie"
        val note = JSONObject().put("title", "Shop").put("body", link)
        val page = Exporting.content(note, Exporting.HTML, "Shop")
        val printed = Outgoing.forPrint(page)
        assertTrue(printed.contains("body, body * { overflow-wrap: anywhere; }"))
        assertTrue("in the page's own head, after its style", printed.indexOf("overflow-wrap: anywhere; }\n</style>\n</head>") > printed.indexOf("th { text-align: left; }"))
        assertEquals("nothing else changes", page, printed.replace("<style>\nbody, body * { overflow-wrap: anywhere; }\n</style>\n", ""))
        assertFalse("the page shared or exported is the desktop's", page.contains("body * {"))
    }

    @Test
    fun `a note's own files are said, since nothing shared carries them`() {
        assertTrue(Outgoing.hasFiles(trip))
        assertFalse(Outgoing.hasFiles(JSONObject("""{"title": "x"}""")))
        assertFalse(Outgoing.hasFiles(JSONObject("""{"attachments": []}""")))
    }
}
