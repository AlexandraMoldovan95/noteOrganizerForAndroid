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
}
