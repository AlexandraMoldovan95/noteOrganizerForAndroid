package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's segments: the body cut into prose and tables, and put back
 * together after an edit with every byte outside the edit untouched.
 */
class EditingTest {

    private val table = "| a | b |\n| --- | --- |\n| 1 | 2 |"

    @Test
    fun `a body without tables is one prose segment`() {
        val parts = Editing.segments("one\ntwo")
        assertEquals(1, parts.size)
        assertEquals("one\ntwo", parts.single().text)
        assertFalse(parts.single().table)
    }

    @Test
    fun `prose and tables alternate, and a body ending in a table has room after it`() {
        val body = "intro\n$table"
        val parts = Editing.segments(body)
        assertEquals(listOf(false, true, false), parts.map { it.table })
        assertEquals("intro", parts[0].text)
        assertEquals(table, parts[1].text)
        assertTrue(parts[2].isVirtual)
        assertEquals("", parts[2].text)
    }

    /** A table of [rows] rows of [columns] cells, its separator one of them. */
    private fun grid(rows: Int, columns: Int): String =
        (0 until rows).joinToString("\n") { r ->
            (0 until columns).joinToString(" | ", "| ", " |") { c -> if (r == 1) "---" else "r${r}c$c" }
        }

    @Test
    fun `a table too large to draw as a grid is not drawn as one, the note unchanged`() {
        // 300 by 300 came in a 2.9 KB share code: 90,000 cells laid out at
        // once, and the note would not open. Written into the text around
        // it instead, it took nine seconds: a field of 362,000 letters.
        val big = grid(300, 300)
        val body = "before\n$big\nafter\n$table"
        val parts = Editing.segments(body)
        assertEquals(listOf(false, true, false, true, false), parts.map { it.table })
        assertEquals("the big table is not drawn as a grid, the small one is",
            listOf(true, true, true, true, true).mapIndexed { i, _ -> i != 1 }, parts.map { it.drawn })
        assertEquals(big, parts[1].text)
        assertEquals(body, parts.filter { !it.isVirtual }.joinToString("\n") { it.text })
        // At the line, and one cell past it.
        val at = Editing.MAX_DRAWN_CELLS / 20
        assertEquals(listOf(true, true), Editing.segments(grid(at, 20)).map { it.drawn })
        assertEquals(listOf(false, true), Editing.segments(grid(at, 20) + " x |").map { it.drawn })
    }

    @Test
    fun `editing prose rewrites only its own lines`() {
        val body = "before\n$table\nafter one\nafter two"
        val parts = Editing.segments(body)
        val after = parts.last()
        assertEquals("after one\nafter two", after.text)
        val edited = Editing.replaceProse(body, after, "after ONE\nafter two\nthree")
        assertEquals("before\n$table\nafter ONE\nafter two\nthree", edited)
        val first = Editing.replaceProse(body, parts.first(), "BEFORE")
        assertEquals("BEFORE\n$table\nafter one\nafter two", first)
    }

    @Test
    fun `the room after a final table adds a line only when written in`() {
        val body = "x\n$table"
        val room = Editing.segments(body).last()
        assertEquals(body, Editing.replaceProse(body, room, ""))
        assertEquals("$body\nmore", Editing.replaceProse(body, room, "more"))
    }

    @Test
    fun `emptying the prose between two tables keeps them apart`() {
        // Two tables with no line between them would read as ONE table.
        val body = "$table\nmiddle\n$table"
        val parts = Editing.segments(body)
        assertEquals(listOf(true, false, true, false), parts.map { it.table })
        val edited = Editing.replaceProse(body, parts[1], "")
        assertEquals(2, tableBlocks(edited).size)
        assertEquals(2, Editing.segments(edited).count { it.table })
    }

    @Test
    fun `segments start where the file counts them, emoji included`() {
        // "a👣b" is three code points; the table starts on line 1, after
        // those three and the newline.
        val parts = Editing.segments("a👣b\n$table")
        assertEquals(0, parts[0].cpStart)
        assertEquals(4, parts[1].cpStart)
    }

    @Test
    fun `a field draws the spans that fall inside it, counted from its start`() {
        val body = "head\n$table\nbold here"
        val tail = Editing.segments(body).last()
        val spans = listOf(
            FormatSpan(0, 4, "bold"),                                  // in "head"
            FormatSpan(tail.cpStart, tail.cpStart + 4, "italic"),      // "bold"
            FormatSpan(2, tail.cpStart + 2, "underline"),              // spans everything
        )
        val local = Editing.localSpans(spans, tail)
        assertEquals(listOf(FormatSpan(0, 4, "italic"), FormatSpan(0, 2, "underline")), local)
    }

    @Test
    fun `a selection in a field is a range of the whole body`() {
        val body = "x👣y\n$table\nafter"
        val tail = Editing.segments(body).last()
        // "after", UTF-16 1..3 -> "ft", which in the body sits after
        // everything before the segment.
        assertEquals((tail.cpStart + 1) to (tail.cpStart + 3), Editing.bodyRange(tail, 1, 3))
        // Inside a field holding an emoji, UTF-16 and code points part ways.
        val head = Editing.segments(body).first()
        assertEquals(2 to 3, Editing.bodyRange(head, 3, 4))
    }

    @Test
    fun `words and reading time are the desktop's`() {
        assertEquals(4, Editing.wordCount("one two\n| three | four |\n| --- | --- |"))
        assertEquals("", Editing.readingTime(0))
        assertEquals("under a minute to read", Editing.readingTime(199))
        assertEquals("about 1 minute to read", Editing.readingTime(200))
        // 500 / 200 = 2.5: Python's round gives 2, not 3.
        assertEquals("about 2 minutes to read", Editing.readingTime(500))
        assertEquals("about 4 minutes to read", Editing.readingTime(700))
    }

    @Test
    fun `an edit in one field keeps the styling of every other field where it was`() {
        val body = "first\n$table\nsecond word"
        val parts = Editing.segments(body)
        val second = parts.last()
        val spans = listOf(FormatSpan(0, 5, "bold"),
                           FormatSpan(second.cpStart + 7, second.cpStart + 11, "italic"))
        val edited = Editing.replaceProse(body, parts.first(), "first, longer")
        val moved = remapSpans(body, edited, spans).spans
        val newSecond = Editing.segments(edited).last()
        // "word" is at 7..11 of "second word", before the edit and after it.
        assertEquals(listOf(FormatSpan(7, 11, "italic")),
            Editing.localSpans(moved, newSecond).filter { it.style == "italic" })
        assertEquals(listOf(FormatSpan(0, 5, "bold")),
            Editing.localSpans(moved, Editing.segments(edited).first()).filter { it.style == "bold" })
    }

    // ---- a new table ----

    private val pad = " ".repeat(10)

    @Test
    fun `a blank table is padded as the desktop's blank_table pads one`() {
        // The desktop's own output, for 1 x 2, 2 x 3 and 3 x 8.
        assertEquals(listOf("|$pad|$pad|"), Editing.blankTable(1, 2))
        assertEquals(List(2) { "|$pad|$pad|$pad|" }, Editing.blankTable(2, 3))
        assertEquals(List(3) { "|" + List(8) { pad }.joinToString("|") + "|" }, Editing.blankTable(3, 8))
    }

    @Test
    fun `a table at the cursor goes on lines of its own`() {
        val row = "|$pad|$pad|"
        val split = Editing.insertTable("hello world", 5, 1, 2)
        assertEquals("the line is broken around it", "hello\n$row\n world", split.body)
        assertEquals(1, split.firstLine)
        assertEquals("an empty line takes it as it is", Editing.Inserted("$row\n", 0), Editing.insertTable("", 0, 1, 2))
    }

    @Test
    fun `two tables never fuse into one`() {
        val row = "|$pad|$pad|"
        val under = Editing.insertTable("| a | b |\n", 10, 1, 2)
        assertEquals("a line between it and the table above", "| a | b |\n\n$row\n", under.body)
        assertEquals(2, under.firstLine)
        // Two blank lines, as the desktop's _insert_table leaves them: its
        // "\n\n" after the table, and the line break that was already there.
        val over = Editing.insertTable("x\n| a | b |", 1, 1, 2)
        assertEquals("and the table below", "x\n$row\n\n\n| a | b |", over.body)
        val after = Editing.insertTable("| a | b |", 10, 1, 2)
        assertEquals("the empty field under a closing table", "| a | b |\n\n$row\n", after.body)
        assertEquals(tableBlocks(after.body).size, 2)
    }

    @Test
    fun `a size is held to the desktop's picker`() {
        assertEquals("two columns at least", 2, splitRow(Editing.blankTable(1, 1).first()).size + 1)
        assertEquals(2, splitRow(Editing.insertTable("", 0, 1, 1).body.lines().first()).size)
        assertEquals(8, splitRow(Editing.insertTable("", 0, 1, 20).body.lines().first()).size)
        assertEquals(24, Editing.insertTable("", 0, 99, 2).body.trimEnd('\n').lines().size)
    }

    // ---- Undo and Redo ----

    @Test
    fun `typing in one burst is one step, a pause or another kind of change starts the next`() {
        val h = Editing.History<String>(pause = 1000)
        h.record("", Editing.History.TYPING, 0)
        h.record("a", Editing.History.TYPING, 300)
        h.record("ab", Editing.History.TYPING, 600)
        h.record("abc", "bold", 700)
        h.record("abc*", Editing.History.TYPING, 800)
        h.record("abc*d", Editing.History.TYPING, 5000)
        assertEquals("after the pause", "abc*d", h.undo("abc*de"))
        assertEquals("the typing after the bold", "abc*", h.undo("abc*d"))
        assertEquals("the bold", "abc", h.undo("abc*"))
        assertEquals("the first burst whole", "", h.undo("abc"))
        assertEquals(null, h.undo(""))
        assertFalse(h.canUndo)
    }

    @Test
    fun `Redo brings back what Undo took, until something new is done`() {
        val h = Editing.History<String>()
        h.record("one", "bold", 0)
        h.record("two", "bold", 10)
        assertEquals("two", h.undo("three"))
        assertEquals("one", h.undo("two"))
        assertTrue(h.canRedo)
        assertEquals("two", h.redo("one"))
        assertEquals("three", h.redo("two"))
        assertFalse(h.canRedo)
        assertEquals("two", h.undo("three"))
        h.record("two", "italic", 20)
        assertFalse("a new change forgets the way forward", h.canRedo)
        // An undo ends a burst: typing after it is a step of its own.
        val t = Editing.History<String>()
        t.record("", Editing.History.TYPING, 0)
        assertEquals("", t.undo("a"))
        t.record("", Editing.History.TYPING, 100)
        t.record("x", Editing.History.TYPING, 200)
        assertEquals("", t.undo("xy"))
        assertFalse(t.canUndo)
        // ...also with steps still behind it: typing after an Undo must not
        // join the burst before, which the Undo already took back.
        val u = Editing.History<String>()
        u.record("", "bold", 0)
        u.record("b", Editing.History.TYPING, 100)
        assertEquals("b", u.undo("bc"))
        u.record("b", Editing.History.TYPING, 150)
        assertEquals("b", u.undo("bx"))
        assertEquals("", u.undo("b"))
    }

    @Test
    fun `typing in another field is a step of its own, however soon`() {
        val h = Editing.History<String>(pause = 1000)
        h.record("", Editing.History.TYPING + ":title", 0)
        h.record("T", Editing.History.TYPING, 100)
        h.record("T b", Editing.History.TYPING, 200)
        assertEquals("T", h.undo("T bo"))
        assertEquals("", h.undo("T"))
    }

    @Test
    fun `at most the limit is kept, the oldest steps forgotten first`() {
        val h = Editing.History<Int>(limit = 3)
        for (i in 0 until 5) h.record(i, "step", i * 10L)
        assertEquals(4, h.undo(5))
        assertEquals(3, h.undo(4))
        assertEquals(2, h.undo(3))
        assertEquals(null, h.undo(2))
    }

    // ---- a table's caption ----

    @Test
    fun `a caption line is a numbered label, not prose that begins with the word`() {
        assertTrue(Editing.isCaptionLine("Table 3: fuel costs"))
        assertTrue(Editing.isCaptionLine("  Tab. 12: before the labels went English"))
        assertTrue("any decimal digit, as Python's \\d", Editing.isCaptionLine("Table ٣: x"))
        assertFalse(Editing.isCaptionLine("Table of contents"))
        assertFalse(Editing.isCaptionLine("Tables 3: x"))
        assertFalse(Editing.isCaptionLine("See Table 3: x"))
        assertTrue(Editing.isSourceLine(" Source: the bank"))
        assertFalse(Editing.isSourceLine("Sources: none"))
    }

    @Test
    fun `a caption goes under its table, numbered after the note's others, its placeholder selected`() {
        val body = "Tab. 1: old\n| a | b |\n| - | - |\n| 1 | 2 |\nafter"
        val added = Editing.addCaption(body, 3)
        assertEquals("Tab. 1: old\n| a | b |\n| - | - |\n| 1 | 2 |\nTable 2: description\nSource: \nafter", added.body)
        assertEquals(4, added.line)
        val start = added.body.indexOf("description")
        assertEquals(start to start + "description".length, added.selectStart to added.selectEnd)
        val lines = added.body.split("\n")
        assertEquals(listOf(4, 5), Editing.captionLines(lines, 3))
        assertEquals("only one touching its table belongs to it", emptyList<Int>(), Editing.captionLines(lines, 2))
        // Positions are code points: an emoji above counts once.
        val emoji = Editing.addCaption("😀\n| a | b |", 1)
        assertEquals(emoji.body.codePointCount(0, emoji.body.indexOf("description")), emoji.selectStart)
    }

    @Test
    fun `a change ends after what it changed, where the cursor stands after typing`() {
        assertEquals(2, Editing.changeEnd("a", "ab"))
        assertEquals(3, Editing.changeEnd("a\nc", "a\nbc"))
        // Enter at the end of a line ending the text: after the new break,
        // on the line it made -- not on the one before it, which matched the
        // break as well and left the new line under the keyboard.
        assertEquals(3, Editing.changeEnd("a\n", "a\n\n"))
        assertEquals(4, Editing.changeEnd("one", "one\n"))
        // A deletion ends where it was; a paste, after what came in.
        assertEquals(1, Editing.changeEnd("abc", "ac"))
        assertEquals(5, Editing.changeEnd("ab", "aXYZWb"))
        // An emoji is one code point, two UTF-16 units: the field counts the
        // second way, the body and Undo the first.
        assertEquals(4, Editing.changeEnd("a😀", "a😀b"))
        assertEquals(3, Editing.changeEndPoints("a😀", "a😀b"))
    }

    @Test
    fun `a change told twice from the same value at once is taken once`() {
        val once = Editing.Once()
        // Samsung's keyboard's Enter on a word it is composing, as the
        // field's log on the owner's phone showed it: twice, 1 ms apart.
        assertTrue(once.news("word", "word\n", 1000))
        assertFalse(once.news("word", "word\n", 1001))
        // The next change is news, and so is the same change much later --
        // a person's own repeat, never within the window.
        assertTrue(once.news("word\n", "word\nx", 1200))
        assertTrue(once.news("word\n", "word\nx", 1200 + Editing.Once.WINDOW_MS))
        // A different change from the same value is news at once.
        assertTrue(once.news("word\n", "word\ny", 1200 + Editing.Once.WINDOW_MS + 1))
    }
}
