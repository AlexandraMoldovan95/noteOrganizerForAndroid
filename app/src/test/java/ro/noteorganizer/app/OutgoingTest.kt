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
    fun `what is handed over is weighed in the bytes it comes to, and the refusal's numbers agree with it`() {
        // Each text goes twice: the intent's own, two bytes a unit, and the
        // clip's, in UTF-8. Counted by letters, 99,950 Chinese ones passed
        // at 500 KB, past what the emulator was seen to carry.
        assertEquals(3L, Sharing.handedBytes("a"))
        assertEquals(5L, Sharing.handedBytes("中"))
        assertEquals("an emoji, two units and four bytes", 8L, Sharing.handedBytes("😀"))
        assertEquals("a letter with an accent, two bytes in UTF-8", 4L, Sharing.handedBytes("é"))
        val subject = "CJK"
        val cjk = "今日は漢字の練習をしました。".repeat(99_947 / 14)
        assertTrue(cjk.length + subject.length < Sharing.MAX_HANDED_CHARS)
        assertTrue(Outgoing.tooLongForWords(cjk, subject))
        val (count, most) = Outgoing.tooLongNumbers(cjk, subject)
        assertTrue("$most of $count", most < count)
        assertTrue("about 60,000 of them", most in 59_000..61_000)
        // The Latin alphabet is held where it was: 100,000 with the subject.
        assertEquals(Sharing.MAX_HANDED_CHARS, Sharing.handedAtMost("w".repeat(5)))
        // A title of 200 and words of 99,953 were refused with "99,953
        // characters, and another app can be handed 100,000 at most": the
        // subject is counted, so the number said is the one compared.
        val title = "T".repeat(200)
        val words = "w".repeat(99_953)
        assertTrue(Outgoing.tooLongForWords(words, Outgoing.subject(title)))
        assertEquals(100_153L to 100_000, Outgoing.tooLongNumbers(words, Outgoing.subject(title)))
        // Just under, not refused: the number never says under the line.
        assertFalse(Outgoing.tooLongForWords("w".repeat(99_800), Outgoing.subject(title)))
    }

    @Test
    fun `a note's code is weighed before the tap, as its words are`() {
        assertEquals(Outgoing.Code.Fits, Outgoing.codeFor(trip))
        // Letters that do not compress: a code past what may be handed.
        val random = java.util.Random(7)
        val noise = String(CharArray(150_000) { ('a' + random.nextInt(26)) })
        val code = Outgoing.codeFor(JSONObject().put("title", "Noise").put("body", noise))
        assertTrue(code.toString(), code is Outgoing.Code.TooLong)
        code as Outgoing.Code.TooLong
        assertEquals(Sharing.MAX_HANDED_CHARS, code.most)
        assertTrue(code.characters > code.most)
        // The list's route refuses it by the same rule.
        assertEquals(Sharing.tooLongToHand(Sharing.encode(listOf(JSONObject().put("body", noise)))), true)
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

    @Test
    fun `a PDF that would leave part of the note out is asked about, and the answer is what it becomes`() = runBlocking {
        // Said in a bar as the share sheet opened, the sheet covered it.
        val dir = java.nio.file.Files.createTempDirectory("pdfask").toFile()
        java.io.File(dir, "notes_data.json").writeText("[]")
        val state = AppState(dir, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        for (choice in AppState.PdfChoice.entries) {
            val answer = CompletableDeferred<AppState.PdfChoice>()
            launch(start = CoroutineStart.UNDISPATCHED) { answer.complete(state.askPdf(firstPagesOnly = true, cutAtEdge = false)) }
            val asked = state.pdfQuestion
            assertTrue(asked != null && asked.firstPagesOnly && !asked.cutAtEdge)
            state.answerPdf(choice)
            assertEquals(choice, answer.await())
            assertEquals(null, state.pdfQuestion)
        }
    }
}
