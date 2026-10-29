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
    fun `a line too long to lay out is a segment of its own, shown read-only, the note unchanged`() {
        // 150,000 letters with no space held the screen 12.6 s as a field; the
        // same with spaces opened in 0.17 s, and stays a field.
        val unbroken = "W".repeat(150_000)
        val spaced = "word ".repeat(30_000)
        val body = "before\n$unbroken\n${unbroken}x\nafter\n$spaced"
        val parts = Editing.segments(body)
        // The line of words, past one field's share of lines, is a field of its own.
        assertEquals(listOf(true, false, true, true), parts.map { it.field })
        assertEquals(listOf(false, true, false, false), parts.map { it.long })
        assertEquals("both long lines, one segment", "$unbroken\n${unbroken}x", parts[1].text)
        assertEquals(listOf("after", spaced), parts.drop(2).map { it.text })
        assertEquals(body, parts.joinToString("\n") { it.text })
        // Typing around it leaves it whole.
        assertEquals("BEFORE\n$unbroken\n${unbroken}x\nafter\n$spaced", Editing.replaceProse(body, parts[0], "BEFORE"))
        // Ending the note, it leaves room to write after it; at the line, it is a field.
        val alone = Editing.segments(unbroken)
        assertEquals(listOf(true, false), alone.map { it.long })
        assertTrue(alone[1].isVirtual && alone[1].field)
        assertTrue(Editing.segments("W".repeat(Editing.MAX_UNBROKEN)).single().field)
        assertFalse(Editing.segments("W".repeat(Editing.MAX_UNBROKEN + 1)).first().field)
        // Find stops at what is shown read-only once a line, where it can be
        // shown; the calculator writes after it.
        assertEquals(listOf(Finding.InText(2, 0, 5)), Finding.scan(body, "after", matchCase = false))
        assertEquals(listOf(Finding.InChecklist(0, 0, 1), Finding.InComments(0, 1)), Finding.scan("x", "W",
            matchCase = false, checklist = listOf(unbroken), comments = unbroken))
        val short = "before\n$unbroken\nafter"
        assertEquals("before\n$unbroken\n1+1\nafter", Calculator.place(short, "1+1", null, 10).body)
        // A list's card gets a glimpse of such a title, never the whole of it.
        val card = Notebook.parseNotes("""[{"id": 1, "title": "$unbroken"}]""").single()
        assertEquals(unbroken, card.title)
        assertTrue(card.headline.length <= 300)
    }

    @Test
    fun `a run is measured by where the layout can break, not by what Kotlin calls whitespace`() {
        // Kotlin's isWhitespace counts the no-break spaces, and 150,000
        // letters joined by them measured as runs of four: given to a field,
        // which can break them nowhere.
        for (glue in listOf('\u00A0', '\u2007', '\u202F', '\u2060', '\uFEFF', '\u001C', '\u001F')) {
            val joined = "word$glue".repeat(30_000)
            assertEquals("U+%04X".format(glue.code), 150_000, Editing.longestUnbroken(joined))
            assertTrue(Editing.tooLongToLayOut(joined))
            val parts = Editing.segments("Above\n$joined\nBelow")
            assertEquals(listOf(true, false, true), parts.map { it.field })
            assertEquals(joined, parts[1].text)
        }
        // Spaces after an opening bracket break nothing (UAX #14's LB14).
        assertTrue(Editing.tooLongToLayOut("( ".repeat(75_000)))
        assertTrue(Editing.tooLongToLayOut("( a".repeat(50_000)))
        // Words, and what ends a sentence or a list item, break as ever.
        for (spaced in listOf("word ".repeat(30_000), "a, ".repeat(50_000), "1. ".repeat(50_000), "Yes! No? ".repeat(20_000))) {
            assertTrue(spaced.take(12), Editing.longestUnbroken(spaced) < 10)
            assertFalse(Editing.runsTooLong(spaced))
        }
        // Ideographs break between any two; joined by a no-break space, not.
        assertFalse(Editing.tooLongToLayOut("中文字".repeat(4_000)))
        assertTrue(Editing.tooLongToLayOut("中\u00A0".repeat(6_000)))
        // One function answers for every place that asks.
        val title = "a\u00A0".repeat(75_000)
        assertEquals(listOf(Finding.InChecklist(0, 0, 1), Finding.InComments(0, 1)), Finding.scan("x", "a",
            matchCase = false, checklist = listOf(title), comments = title))
    }

    @Test
    fun `runs add up across a body's lines, and past what it may give the layout the rest are shown read-only`() {
        // A hundred lines of 9,999 letters passed the line's own test one by
        // one, and all went into one field, each broken letter by letter.
        val heavy = "a".repeat(Editing.MAX_UNBROKEN - 1)
        val hundred = List(100) { heavy }.joinToString("\n")
        val parts = Editing.segments(hundred)
        assertEquals(listOf(true, false, true), parts.map { it.field })
        assertEquals("two fit", 0 to 1, parts[0].firstLine to parts[0].lastLine)
        assertEquals(2 to 99, parts[1].firstLine to parts[1].lastLine)
        assertTrue(parts[2].isVirtual)
        assertEquals(hundred, parts.filterNot { it.isVirtual }.joinToString("\n") { it.text })
        assertTrue(Editing.tooLongToLayOut(hundred))
        assertFalse(Editing.tooLongToLayOut("$heavy\n$heavy"))
        // Ordinary lines between them stay fields, and so does a light line
        // after the limit is reached: only what is too much is read-only.
        val mixed = listOf("x", heavy, "normal words", heavy, "more words", heavy, "end").joinToString("\n")
        val cut = Editing.segments(mixed)
        assertEquals(listOf(true, false, true), cut.map { it.field })
        assertEquals(0 to 4, cut[0].firstLine to cut[0].lastLine)
        assertEquals(5 to 5, cut[1].firstLine to cut[1].lastLine)
        assertEquals("end", cut[2].text)
        assertTrue(cut.filter { it.field }.sumOf { Editing.layoutCost(it.text)!! } <= Editing.MAX_LAYOUT_COST)
        // Each line is counted once: the one after a read-only line, let in
        // when it was asked, is not asked -- and counted -- again.
        val around = listOf(heavy, "b".repeat(Editing.MAX_UNBROKEN + 1), heavy).joinToString("\n")
        assertEquals(listOf(true, false, true), Editing.segments(around).map { it.field })
        // Prose, however long, is far from the limit; a line of it is a field
        // up to what one field may draw ([Editing.MAX_FIELD_WEIGHT]).
        val prose = "She said, \"Let's go (now) to the market -- it's late.\" Then we walked; it was 5:30. "
        val long = prose.repeat(300_000 / prose.length)
        assertTrue(Editing.layoutCost(long)!! < Editing.MAX_LAYOUT_COST / 20)
        assertTrue(Editing.segments(prose.repeat(150_000 / prose.length)).single().field)
    }

    /** What a body's fields and grids draw together: lines, cells, pieces. */
    private fun drawn(parts: List<Editing.Segment>): Triple<Long, Long, Int> = Triple(
        parts.filter { it.field }.sumOf { Editing.drawnLines(it.text) },
        parts.filter { it.table && it.drawn }.sumOf { Editing.gridCells(it.text.split("\n")) },
        parts.size)

    @Test
    fun `what a body draws is counted for the whole body, cells, lines and pieces`() {
        // Ten tables of 1,000 cells, from a code of 350 characters: each at
        // the line, all ten drawn -- 10,000 cells at once.
        val ten = List(10) { grid(50, 20) }.joinToString("\n\n")
        val tables = Editing.segments(ten)
        assertEquals(listOf(true) + List(9) { false }, tables.filter { it.table }.map { it.drawn })
        assertTrue(drawn(tables).second <= Editing.MAX_DRAWN_CELLS)
        assertEquals(ten, tables.filterNot { it.isVirtual }.joinToString("\n") { it.text })
        // Smaller tables share the line: five of 300 cells, three drawn.
        val five = List(5) { grid(30, 10) }.joinToString("\n\n")
        assertEquals(listOf(true, true, true, false, false), Editing.segments(five).filter { it.table }.map { it.drawn })
        // 3,000 tables of two cells, a blank line between: 6,000 pieces, each
        // composed at once. At most [MAX_SEGMENTS], the rest one read-only piece.
        val tiny = List(3_000) { "| a | b |" }.joinToString("\n\n")
        val pieces = Editing.segments(tiny)
        assertTrue(pieces.size.toString(), pieces.size <= Editing.MAX_SEGMENTS)
        assertTrue(pieces.last().long && pieces.last().heavy && !pieces.last().field)
        assertEquals(tiny, pieces.joinToString("\n") { it.text })
        // Two lines of 10,000 letters spend what the layout may be given;
        // then "x" and "" five thousand times came out as 10,001 pieces.
        val spent = List(2) { "a".repeat(Editing.MAX_UNBROKEN) } + List(5_000) { listOf("x", "") }.flatten()
        val alternating = Editing.segments(spent.joinToString("\n"))
        assertTrue(alternating.size.toString(), alternating.size <= 4)
        assertEquals(1, alternating.count { it.long })
        assertEquals(spent.joinToString("\n"), alternating.filterNot { it.isVirtual }.joinToString("\n") { it.text })
        // An empty line between two fields stays in one.
        assertTrue(Editing.segments("a\n\nb").single().field)
    }

    @Test
    fun `an ordinary note of many small tables, or a long journal, is drawn whole to its end`() {
        // 100 pieces were a note's ceiling: fifty weekly tables under their
        // headings, 2,331 characters, made the fiftieth and everything after
        // it read-only, with nowhere to write at the end; and a training log
        // of sixty sessions, 5 KB, hid its last ten from Find.
        val weeks = (1..50).joinToString("\n") { "Week $it\n| Day | Spent |\n|---|---|\n| Mon | $it |" }
        val parts = Editing.segments(weeks)
        assertTrue(parts.none { it.long })
        assertEquals(50, parts.count { it.table && it.drawn })
        assertTrue("somewhere to write after the last", parts.last().isVirtual && parts.last().field)
        val sessions = (1..60).joinToString("\n") { "Session $it\n| Set | Reps | Kg |\n|---|---|---|\n| 1 | 8 | ${40 + it} |" }
        assertTrue(Editing.segments(sessions).none { it.long })
        assertEquals(listOf(Finding.InText(120, 4, 12)),
            Finding.scan("$sessions\nThe deadlift went well.", "deadlift", matchCase = false))
        // A journal of 10,050 lines, 442,000 characters: its end was read-only.
        val journal = Editing.segments("Today I walked to the river and back again.\n".repeat(10_050))
        assertTrue(journal.all { it.field })
        // Two hundred weekly tables -- four years -- still end in a field.
        val years = Editing.segments((1..200).joinToString("\n") { "Week $it\n| Day | Spent |\n|---|---|\n| Mon | $it |" })
        assertTrue(years.none { it.long } && years.last().field)
    }

    @Test
    fun `a checklist's rows are written in up to what one list draws, each up to the desktop's row`() {
        // Each row was weighed alone, every row drawn at once: 120 rows of
        // 150,000 letters, from a code of 38 KB, laid out 18 million letters
        // at every opening.
        assertTrue("a long list written by hand, every row", Editing.checklistFields(List(1_000) { "x".repeat(100) }).all { it })
        val wide = Editing.checklistFields(List(120) { "word ".repeat(30_000) })
        assertTrue("a row past the desktop's 2,000 is shown, not written in", wide.none { it })
        val full = Editing.checklistFields(List(60) { "y".repeat(Editing.CHECKLIST_ROW_CHARS) })
        assertEquals(Editing.CHECKLIST_CHARS / Editing.CHECKLIST_ROW_CHARS, full.count { it })
        assertTrue("from the top", full.take(full.count { it }).all { it })
        // A short row after a long one is still a field.
        assertEquals(listOf(false, true), Editing.checklistFields(listOf("z".repeat(2_001), "milk")).toList())
    }

    @Test
    fun `a long checklist draws its first rows and its last, and a search's row among them`() {
        // 1,000 short rows, every one drawn as the note opened: 5.3 s on the emulator.
        assertEquals((0 until 100).toList(), Editing.checklistShown(100, Editing.CHECKLIST_ROWS))
        val long = Editing.checklistShown(1_000, Editing.CHECKLIST_ROWS)
        assertEquals((0 until 200).toList() + (995 until 1_000).toList(), long)
        assertEquals((0..600).toList() + (995 until 1_000).toList(), Editing.checklistShown(1_000, Editing.CHECKLIST_ROWS, reach = 600))
        assertEquals("a few left out are drawn", (0 until 210).toList(), Editing.checklistShown(210, Editing.CHECKLIST_ROWS))
        assertEquals("Show draws more", 405, Editing.checklistShown(1_000, 400).size)
    }

    @Test
    fun `a note of many short lines is drawn as fields of a share each, the rest read-only`() {
        // A code of 666 characters: 200,000 empty lines, one field -- each of
        // its lines drawn by a call copying the whole field again.
        for (body in listOf("\n".repeat(200_000), "a\n".repeat(200_000))) {
            val parts = Editing.segments(body)
            val (lines, _, count) = drawn(parts)
            assertTrue(count <= Editing.MAX_SEGMENTS)
            assertTrue(lines <= Editing.MAX_DRAWN_LINES)
            assertTrue(parts.filter { it.field }.all { Editing.drawnLines(it.text) <= Editing.FIELD_LINES })
            assertTrue("the rest, read-only", parts.last().long && parts.last().heavy)
            assertEquals(body, parts.joinToString("\n") { it.text })
        }
        // A long journal is all fields, each its share: every line can be typed in.
        val journal = "Today I walked to the river and back again.\n".repeat(7_000)
        val parts = Editing.segments(journal)
        assertTrue(parts.all { it.field })
        assertTrue(parts.size > 1)
        assertTrue(parts.all { Editing.drawnLines(it.text) <= Editing.FIELD_LINES })
        assertEquals(journal, parts.joinToString("\n") { it.text })
        // Typing in one field leaves the fields before it as they were.
        val typed = Editing.replaceProse(journal, parts[1], "Hello\n" + parts[1].text)
        assertEquals(journal.length + 6, typed.length)
        assertEquals(parts[0].text, Editing.segments(typed)[0].text)
        // One line heavier to draw than a field may be is read-only; the
        // title and comments are asked the same, and Find stops at each once.
        val wide = "word ".repeat(60_000)
        val cut = Editing.segments("intro\n$wide\noutro")
        assertEquals(listOf(true, false, true), cut.map { it.field })
        assertTrue(cut[1].heavy)
        val empties = "\n".repeat(200_000)
        assertTrue(Editing.tooLongToLayOut(empties))
        assertTrue(Editing.tooLongToLayOut(wide))
        assertFalse(Editing.tooLongToLayOut("\n".repeat(20_000)))
        assertFalse(Editing.tooLongToLayOut("word ".repeat(30_000)))
        assertEquals(listOf(Finding.InChecklist(0, 0, 4), Finding.InComments(0, 4)), Finding.scan("x", "word",
            matchCase = false, checklist = listOf(wide), comments = "word" + empties))
    }

    @Test
    fun `a ragged table is weighed as it is drawn, every row as wide as the widest`() {
        // 1,000 cells as written -- one row of 500, then 250 of two -- drawn
        // as 251 by 500: 125,500 cells, from a share code of 174 characters.
        val ragged = (listOf((1..500).joinToString("|", "|", "|") { "x" }) + List(250) { "| a | b |" })
            .joinToString("\n")
        val lines = ragged.split("\n")
        assertEquals(1000, lines.sumOf { splitRow(it).size })
        assertEquals(125_500L, Editing.gridCells(lines))
        assertEquals("the grid parseTable builds", 125_500, parseTable(lines).let { t -> t.rows.size * t.columns.size })
        assertEquals(listOf(true, false, true), Editing.segments("Ragged\n$ragged\nafter").map { it.drawn })
        // A table written whole is weighed as before.
        assertEquals(Editing.MAX_DRAWN_CELLS.toLong(), Editing.gridCells(grid(50, 20).split("\n")))
    }

    @Test
    fun `a table holding text too much to lay out is shown read-only, never drawn as a grid`() {
        // One row of two cells passed the count of cells, and its 150,000
        // letters went whole into a cell 26 characters wide.
        val unbroken = "a".repeat(150_000)
        val body = "Words before\n| $unbroken | x |\nwords after"
        val parts = Editing.segments(body)
        assertEquals(listOf(false, true, false), parts.map { it.table })
        assertEquals(listOf(false, true, false), parts.map { it.long })
        assertEquals(listOf(true, false, true), parts.map { it.drawn })
        assertEquals(listOf(true, false, true), parts.map { it.field })
        assertEquals(body, parts.joinToString("\n") { it.text })
        assertEquals("found once, where it is", listOf(Finding.InText(1, 150_005, 150_006)),
            Finding.scan(body, "x", matchCase = false))
        // With a header and a separator; joined by no-break spaces; and
        // three cells just under the limit, which add up past it.
        val heavy = "a".repeat(Editing.MAX_UNBROKEN - 1)
        for (table in listOf("| $unbroken | x |\n|---|---|\n| a | b |",
                             "| ${"word\u00A0".repeat(30_000)} | x |",
                             "| $heavy | $heavy | $heavy |")) {
            val first = Editing.segments(table).first()
            assertTrue(table.take(20), first.table && first.long && !first.drawn)
        }
        // The cells share what a body may give the layout with its lines.
        assertTrue(Editing.segments("| $heavy | $heavy |").first().drawn)
        val shared = Editing.segments("$heavy\n$heavy\n| $heavy | x |\nend")
        assertEquals(listOf(true, false, true), shared.map { it.field })
        assertTrue(shared[1].table && shared[1].long)
        // A table of ordinary words is drawn as ever, in a body of any length.
        val words = "Some words here. ".repeat(2_000)
        assertTrue(Editing.segments("$words\n| a | b |\n|---|---|\n| c | d |").first { it.table }.drawn)
    }

    @Test
    fun `Find stops at a table it does not draw once a line, and still reaches the words after it`() {
        // A stop at every cell, the counter moved through 2,000 cells of a
        // table shown only as its first lines, and the one match on the
        // screen was never reached; skipped, its words were found nowhere.
        // Each line is one stop, and the table shows the line stepped to.
        val big = grid(300, 300).replace(Regex("r\\d+c\\d+"), "1")
        val found = Finding.scan("$big\nTotal 1", "1", matchCase = false)
        assertEquals("every line but the separator", 299, found.count { it.segment == 0 })
        assertEquals(Finding.InText(1, 6, 7), found.last())
        assertEquals(Finding.InText(0, 2, 3), found.first())
        // A table drawn is searched as before.
        assertEquals(2, Finding.scan("| 1 | 2 |\n|---|---|\n| 3 | 1 |", "1", matchCase = false).size)
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

    @Test
    fun `at most the budget is kept by weight, the oldest first, never the newest`() {
        // Each step is the whole note: 200 of a long one were more memory
        // than a phone gives an app.
        val h = Editing.History<String>(budget = 10, weigh = { it.length.toLong() })
        for (s in listOf("aaaa", "bbbb", "cccc")) h.record(s, "step", 0)
        assertEquals(2, h.steps)
        assertEquals("cccc", h.undo("dddd"))
        assertEquals("bbbb", h.undo("cccc"))
        assertEquals(null, h.undo("bbbb"))
        // Redo, then a step heavier than the whole budget: it alone is kept.
        assertEquals("cccc", h.redo("bbbb"))
        h.record("x".repeat(50), "step", 10)
        assertEquals(1, h.steps)
        assertFalse("a new change forgets the way forward", h.canRedo)
        assertEquals("x".repeat(50), h.undo("y"))
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

    @Test
    fun `a row's field keeps its own words whole and cuts only what came in, as the desktop's box does`() {
        // take() over the whole value kept the first 2,000 of the result: a
        // letter at the start of a row at the cap pushed its last letter
        // off, a paste its last words -- out of sight, no Undo, unsaid.
        val key = "a".repeat(2_000 - 16) + "DON'T FORGET KEY"
        val typed = Editing.fitRow(key, "X$key")
        assertEquals("a letter at the cap is refused, the row whole", key, typed.text)
        assertEquals("A checklist item can be up to 2,000 characters", typed.said)
        val recipe = "r".repeat(1_480) + " Serve with rice."
        val pasted = Editing.fitRow(recipe, "p".repeat(800) + recipe)
        assertEquals("503 of the 800 come in, the row whole after them", "p".repeat(503) + recipe, pasted.text)
        assertEquals("the cursor after what came in", 503, pasted.caret)
        assertEquals("Pasted text shortened: a checklist item can be up to 2,000 characters", pasted.said)
        val middle = Editing.fitRow("Recipe: -- from Mum", "Recipe: " + "q".repeat(2_100) + "-- from Mum")
        assertEquals("Recipe: " + "q".repeat(1_981) + "-- from Mum", middle.text)
        // Within the cap, or shorter than it was -- a row from elsewhere already longer: as typed.
        assertEquals(Editing.Fit("milk!", null, null), Editing.fitRow("milk", "milk!"))
        val over = "o".repeat(2_500)
        assertEquals(Editing.Fit(over.dropLast(1), null, null), Editing.fitRow(over, over.dropLast(1)))
        // Words selected and typed over: what came in is cut to the room the selection left.
        assertEquals("yyy" + "x".repeat(1_997), Editing.fitRow("abc" + "x".repeat(1_997), "y".repeat(500) + "x".repeat(1_997)).text)
        // Counted as Python counts: 1,200 emoji are 1,200 letters, kept whole...
        val emoji = "😀".repeat(1_200)
        assertEquals(Editing.Fit(emoji, null, null), Editing.fitRow("", emoji))
        // ... never cut through one: a letter of room, one whole emoji.
        assertEquals("a".repeat(1_999) + "😀", Editing.fitRow("a".repeat(1_999), "a".repeat(1_999) + "😀😀").text)
        // An edit that differs inside an emoji's pair is read as the whole emoji, at
        // either end: the row's own emoji after it kept, one typed over replaced whole.
        val near = "a".repeat(1_999) + "😀"
        assertEquals(near, Editing.fitRow(near, "a".repeat(1_999) + "😁😀").text)
        assertEquals("a".repeat(1_998) + "😁😀",
                     Editing.fitRow("a".repeat(1_998) + "😀", "a".repeat(1_998) + "😁xxxxx😀").text)
        assertEquals("a".repeat(1_999) + "z", Editing.fitRow(near, "a".repeat(1_999) + "zz🈀").text)
        // A desktop row of 1,500 emoji, an ordinary row there, is one the phone writes in.
        assertTrue(Editing.checklistFields(listOf("😀".repeat(1_500))).single())
        assertFalse(Editing.rowTooLong("😀".repeat(1_500)))
        assertTrue(Editing.rowTooLong("b".repeat(2_001)))
    }

    @Test
    fun `a row's cap is said once while the letters meet it, and again after an edit that fitted`() {
        val said = ArrayList<String>()
        val cap = Editing.BoxCap({ said.add(it) })
        val full = "f".repeat(2_000)
        cap.fit(full, full + "a")
        cap.fit(full, full + "b")
        assertEquals(listOf("A checklist item can be up to 2,000 characters"), said)
        cap.fit(full, full.dropLast(1))
        cap.fit(full, "c$full")
        assertEquals(2, said.size)
    }

    @Test
    fun `a second cut in the same editor is said, after Add emptied the box or in another row`() {
        // Said once an editor: the "said" was cleared only by an edit that
        // fitted, and Add empties the box without one, so a second long
        // paste after it, or into another row, was cut without a word.
        val said = ArrayList<String>()
        val cap = Editing.BoxCap({ said.add(it) })
        val first = cap.fit("", "p".repeat(3_000), box = Editing.NEW_ROW_BOX)
        assertEquals(1, said.size)
        // The keys after it, at the cap: no bar for each.
        cap.fit(first.text, first.text + "q", box = Editing.NEW_ROW_BOX)
        assertEquals(1, said.size)
        // Added, the box empty: the next long paste is a cut of its own.
        cap.fit("", "r".repeat(2_500), box = Editing.NEW_ROW_BOX)
        assertEquals(2, said.size)
        // Another row, at the same moment.
        val rowA = "a".repeat(1_990)
        cap.fit(rowA, rowA + "b".repeat(50), box = 0)
        assertEquals(3, said.size)
        val rowB = "c".repeat(1_500)
        cap.fit(rowB, rowB + "d".repeat(900), box = 1)
        assertEquals(4, said.size)
        assertTrue(said.all { it == "Pasted text shortened: a checklist item can be up to 2,000 characters" })
    }

    @Test
    fun `a title and a template's name keep their own words at 200 letters, said in the desktop's words`() {
        // take(200) of the whole new value: a letter at the start of a title of
        // 200 pushed its last letter off; a title of 150 emoji, which the
        // desktop holds, kept 100 at the first letter typed; one typed after
        // 199 letters was cut in two, half of it saved as "?".
        val title = "t".repeat(199) + "!"
        val key = Editing.fitRow(title, "X$title", Editing.TITLE_CHARS, "A note title", "Pasted title shortened to 200 characters")
        assertEquals(title, key.text)
        assertEquals("A note title can be up to 200 characters", key.said)
        val paste = Editing.fitRow("t".repeat(150), "p".repeat(100) + "t".repeat(150), Editing.TITLE_CHARS, "A note title",
                                   "Pasted title shortened to 200 characters")
        assertEquals("p".repeat(50) + "t".repeat(150), paste.text)
        assertEquals("Pasted title shortened to 200 characters", paste.said)
        val apples = "🍎".repeat(150)
        assertEquals(Editing.Fit("$apples!", null, null), Editing.fitRow(apples, "$apples!", Editing.TITLE_CHARS, "A note title"))
        val near = "a".repeat(199)
        assertEquals("the 200th letter an emoji, whole", "$near🍎", Editing.fitRow(near, "$near🍎", Editing.TITLE_CHARS).text)
        val name = Editing.fitRow("n".repeat(200), "n".repeat(200) + "x", Editing.TITLE_CHARS, "A template name")
        assertEquals("A template name can be up to 200 characters", name.said)
        assertEquals("Pasted text shortened: a template name can be up to 200 characters",
                     Editing.fitRow("n", "n" + "x".repeat(300), Editing.TITLE_CHARS, "A template name").said)
        assertEquals("never half an emoji", "🍎🍎", Editing.firstLetters("🍎🍎🍎", 2))
        assertEquals("short", Editing.firstLetters("short", 36))
    }

    @Test
    fun `a search's row already drawn among the last draws no more rows`() {
        // A match in one of the last five -- where the newest item lands --
        // reached past them all and drew every row, at each letter in the bar.
        val drawn = (0 until 200).toList() + (995 until 1_000).toList()
        for (reach in listOf(150, 995, 999)) assertEquals("reach $reach", drawn, Editing.checklistShown(1_000, 200, reach))
        assertEquals((0..600).toList() + (995 until 1_000).toList(), Editing.checklistShown(1_000, 200, 600))
        assertEquals("a tail grown by a move", (0 until 200).toList() + (994 until 1_000).toList(),
                     Editing.checklistShown(1_000, 200, tail = 6))
    }

    @Test
    fun `a row moved across the rows not drawn stays drawn, and every row drawn before with it`() {
        // Counted over every row, Move up on the first of the last five put
        // the row among those not drawn: gone, a row never shown in its place.
        for (reach in listOf(-1, 600)) {
            val before = Editing.checklistShown(1_000, 200, reach)
            for ((from, to) in listOf(995 to 994, 199 to 200, 997 to 199, 5 to 997, 3 to 4, 998 to 996, 600 to 601)) {
                val (h, t) = Editing.keepMovedInSight(1_000, 200, 5, reach, from, to)
                // Each row by what it is: the one at each place after the move.
                val order = (0 until 1_000).toMutableList().apply { add(to, removeAt(from)) }
                val now = Editing.checklistShown(1_000, h, reach, t).map { order[it] }.toSet()
                assertTrue("reach $reach, $from to $to: the row moved is drawn", from in now)
                assertTrue("reach $reach, $from to $to: every row drawn before still is", before.all { it in now })
            }
        }
        // Up from the first of the last rows and down again: back in its place, drawn throughout.
        val (h, t) = Editing.keepMovedInSight(1_000, 200, 5, -1, 995, 994)
        assertEquals(200 to 6, h to t)
        val back = Editing.keepMovedInSight(1_000, h, t, -1, 994, 995)
        assertTrue(995 in Editing.checklistShown(1_000, back.first, -1, back.second))
        assertEquals("a move among rows all drawn changes nothing", 200 to 5, Editing.keepMovedInSight(100, 200, 5, -1, 10, 11))
    }

    @Test
    fun `a dragged row lands on the drawn row nearest where it is let go, never among rows not drawn`() {
        // Rows of 44, the first 200 and the last five, the "N more rows" line of 37 between.
        val middles = HashMap<Int, Float>()
        for (i in 0 until 200) middles[i] = 22f + 44f * i
        for ((k, i) in (995 until 1_000).withIndex()) middles[i] = 200 * 44f + 37f + 22f + 44f * k
        // The first of the last rows dragged up under the 200th lands on it; counted in rows it was 993.
        assertEquals(199, Editing.dropTarget(middles, middles.getValue(995) - 81f, 995))
        assertEquals("a short drag, one row down", 996, Editing.dropTarget(middles, middles.getValue(995) + 30f, 995))
        assertEquals("less than half a row, where it was", 995, Editing.dropTarget(middles, middles.getValue(995) + 10f, 995))
        // A row of the first 200 dragged onto the tail's second row lands there; counted in rows, on 202.
        assertEquals(996, Editing.dropTarget(middles, middles.getValue(198) + 169f, 198))
    }

    @Test
    fun `a long row is read in parts a phone can wrap, every letter in one, no word or emoji cut`() {
        val text = "Lists:\n" + "word ".repeat(1_000) + "😀".repeat(400) + "\nend"
        val parts = Editing.rowParts(text)
        assertEquals("every letter once, in order, no line break", text.replace("\n", ""),
                     parts.joinToString("") { (a, b) -> text.substring(a, b) })
        assertTrue(parts.all { (a, b) -> b - a in 0..Editing.ROW_PART_CHARS })
        assertTrue("never through an emoji", parts.all { (_, b) -> b == text.length || !Character.isLowSurrogate(text[b]) })
        assertTrue("words whole", parts.drop(1).take(10).all { (a, b) -> text.substring(a, b).endsWith(" ") })
        assertEquals("one line, one part", listOf(0 to 4), Editing.rowParts("milk"))
    }

    @Test
    fun `a row with no space is cut into parts reading each letter a few times, not the row before each part`() {
        // Each part looked back to the row's start for a space: the square of
        // the row over 600 letters read -- 66,666,333 for these 200,000, and
        // tens of seconds for the five million a share code of 7 KB brings.
        class Counted(val text: String) : CharSequence {
            var reads = 0L
            override val length get() = text.length
            override fun get(index: Int): Char { reads++; return text[index] }
            override fun subSequence(startIndex: Int, endIndex: Int) = text.subSequence(startIndex, endIndex)
            override fun toString() = text
        }
        for (row in listOf("a".repeat(200_000), " " + "中".repeat(200_000), "😀".repeat(100_000))) {
            val counted = Counted(row)
            val parts = Editing.rowParts(counted)
            assertTrue("read ${counted.reads} letters for ${row.length}", counted.reads <= 2L * 200_000)
            assertEquals("the same parts as the row's own", Editing.rowParts(row), parts)
            assertEquals(row, parts.joinToString("") { (a, b) -> row.substring(a, b) })
        }
        // A space within the slack is still where a part ends.
        val words = "x".repeat(250) + " " + "y".repeat(400)
        assertEquals(251, Editing.rowParts(words).first().second)
    }
}
