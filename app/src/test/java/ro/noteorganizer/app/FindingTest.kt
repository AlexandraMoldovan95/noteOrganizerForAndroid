package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Find in note. The desktop's own checks first (qa1's find section, case
 * for case), then what the phone's segments add: a match's place in its
 * field, and reading order across prose and tables.
 */
class FindingTest {

    private fun row(vararg cells: String) = cells.joinToString(" | ", "| ", " |")
    private val sep = "| --- | --- |"

    private fun scan(body: String, query: String, matchCase: Boolean = false) = Finding.scan(body, query, matchCase)

    // ---- the checklist and the comments, after the body ----

    @Test
    fun `the checklist's rows and the comments are found after the body, as the note reads`() {
        val found = Finding.scan("milk, bread", "milk", false,
                                 checklist = listOf("eggs", "buy Milk", "milk and milk"), comments = "no milk left")
        assertEquals(listOf(
            Finding.InText(0, 0, 4),
            Finding.InChecklist(1, 4, 8), Finding.InChecklist(2, 0, 4), Finding.InChecklist(2, 9, 13),
            Finding.InComments(3, 7),
        ), found)
        assertTrue(found.drop(1).all { it.segment == Finding.OUTSIDE })
        // Case asked: the checklist's "Milk" drops out, as the body's would.
        assertEquals(4, Finding.scan("milk, bread", "milk", true,
                                     checklist = listOf("buy Milk", "milk and milk"), comments = "no milk left").size)
    }

    // ---- qa1's find section ----

    @Test
    fun `prose - case does not count unless asked`() {
        val body = "alpha beta alpha gamma ALPHA"
        assertEquals(3, scan(body, "alpha").size)
        assertTrue(scan(body, "alpha").all { it is Finding.InText })
        assertEquals(2, scan(body, "alpha", matchCase = true).size)
    }

    @Test
    fun `prose folds a letter as Tk's search does - one for one, never into more bytes, the basic plane alone`() {
        // The desktop's _tcl_lower (098d5a8): Tcl lowers a letter only into
        // as many UTF-8 bytes or fewer, and only in the basic plane. The
        // phone folded U+023A and U+023E (two bytes, their lower case three)
        // and letters past the plane (the final audit's tenth round).
        fun hits(body: String, query: String) = scan(body, query).size
        // Lowered: the dotted capital I, the Kelvin sign, capital sharp s, sigma, the ohm sign.
        assertEquals(1, hits("İstanbul", "istanbul"))
        assertEquals(1, hits("5 K", "k"))
        assertEquals(1, hits("Straẞe", "straße"))
        assertEquals(1, hits("Σ", "σ"))
        assertEquals(1, hits("Ω", "ω"))
        // Never sharp s as "ss", nor sigma as the final sigma.
        assertEquals(0, hits("Straße", "strasse"))
        assertEquals(0, hits("Σ", "ς"))
        // Kept: A and T with a stroke, and a letter past the basic plane (Deseret).
        assertEquals(0, hits("Ⱥ", "ⱥ"))
        assertEquals(0, hits("ⱥ", "Ⱥ"))
        assertEquals(0, hits("Ⱦ", "ⱦ"))
        assertEquals(1, hits("Ⱥ x Ⱥ", "Ⱥ x"))
        assertEquals(0, hits("𐐀", "𐐨"))
        // With case asked, exact.
        assertEquals(0, scan("K", "k", matchCase = true).size)
    }

    @Test
    fun `stepping goes round from the last to the first`() {
        assertEquals(1, Finding.step(0, 1, 3))
        assertEquals(0, Finding.step(2, 1, 3))
        assertEquals("back from the first is the last", 2, Finding.step(0, -1, 3))
        assertEquals(-1, Finding.step(0, 1, 0))
    }

    @Test
    fun `a miss counts 0 of 0, and nothing is said before a word is typed`() {
        assertEquals("0/0", Finding.counter(-1, 0, "nowhere-zzz"))
        assertEquals("", Finding.counter(-1, 0, ""))
        assertEquals("2/3", Finding.counter(1, 3, "alpha"))
        assertEquals("1/2000+", Finding.counter(0, Finding.CAP, "a"))
    }

    @Test
    fun `cells - the header is row 0, the separator is not a row`() {
        val body = listOf(row("Fruit", "Note"), sep, row("Milk", "fresh today"),
                          row("Bread<br>loaf", "plain"), row("[x]", "done")).joinToString("\n")
        assertEquals(listOf(Finding.InCell(0, 1, 1)), scan(body, "fresh"))
        assertEquals("a line break reads as a space", listOf(Finding.InCell(0, 2, 0)), scan(body, "bread loaf"))
        assertEquals("a checkbox is never a word", emptyList<Finding.Match>(), scan(body, "[x]"))
    }

    @Test
    fun `a word repeated in one cell is one stop`() {
        val body = listOf(row("X", "Y"), sep, row("foo foo foo", "bar")).joinToString("\n")
        assertEquals(1, scan(body, "foo").size)
    }

    @Test
    fun `an emoji match covers exactly the match`() {
        val text = "see 😀ok maybe"
        val match = scan(text, "😀ok").single() as Finding.InText
        assertEquals("😀ok", text.substring(match.start, match.end))
    }

    // ---- the phone's own ----

    @Test
    fun `matches overlap, as each search starts one character on`() {
        assertEquals(listOf(Finding.InText(0, 0, 2), Finding.InText(0, 1, 3)), scan("aaa", "aa"))
    }

    @Test
    fun `a match is placed in its own field, in the field's units`() {
        val body = "Intro\n" + listOf(row("a", "b"), sep, row("c", "d")).joinToString("\n") + "\nafter 😀 the word"
        val found = scan(body, "word")
        assertEquals(1, found.size)
        val match = found.single() as Finding.InText
        assertEquals("the prose after the table is segment 2", 2, match.segment)
        val field = Editing.segments(body)[2].text
        assertEquals("word", field.substring(match.start, match.end))
    }

    @Test
    fun `reading order - prose, then the table row by row, then the prose after`() {
        val body = listOf("x top", row("x1", "x2"), sep, row("x3", "x4"), "x bottom").joinToString("\n")
        assertEquals(
            listOf(Finding.InText(0, 0, 1), Finding.InCell(1, 0, 0), Finding.InCell(1, 0, 1),
                   Finding.InCell(1, 1, 0), Finding.InCell(1, 1, 1), Finding.InText(2, 0, 1)),
            scan(body, "x"),
        )
    }

    @Test
    fun `merged-away cells and markers are not words`() {
        val body = listOf(row("wide", "<"), sep, row("<", "x")).joinToString("\n")
        assertEquals("the marker swallowed into a merge is nothing", emptyList<Finding.Match>(), scan(body, "<"))
        assertEquals(listOf(Finding.InCell(0, 0, 0)), scan(body, "wide"))
    }

    @Test
    fun `a cell in a hidden column is not found`() {
        val body = listOf(row("shown", "secret"), "| --- | ~ |", row("a", "secret too")).joinToString("\n")
        assertEquals(emptyList<Finding.Match>(), scan(body, "secret"))
        assertEquals(1, scan(body, "shown").size)
    }

    @Test
    fun `case-blind in cells too, and exact when asked`() {
        val body = listOf(row("Milk", "MILK"), sep).joinToString("\n")
        assertEquals(2, scan(body, "milk").size)
        assertEquals(listOf(Finding.InCell(0, 0, 0)), scan(body, "Milk", matchCase = true))
        assertEquals(listOf(Finding.InCell(0, 0, 1)), scan(body, "MILK", matchCase = true))
    }

    @Test
    fun `the count stops at the ceiling`() {
        assertEquals(Finding.CAP, scan("a".repeat(5000), "a").size)
    }

    @Test
    fun `a piece shown read-only is searched as written, each line one stop, and what follows is reached`() {
        // Skipped, the bar said 0/0 in the colour of a word found nowhere
        // while the list's search found the note by it.
        val long = "W".repeat(Editing.MAX_UNBROKEN + 1)
        val body = "intro\n$long needle needle\n$long needle\nafter needle"
        val found = scan(body, "needle")
        assertEquals(listOf(Finding.InText(1, long.length + 1, long.length + 7),
                            Finding.InText(1, 2 * long.length + 16, 2 * long.length + 22),
                            Finding.InText(2, 6, 12)), found)
        // Where a read-only line is cut to show the match stepped to.
        val line = "a".repeat(1_000)
        assertEquals(Finding.Cut(0, 300), Finding.cutAround(line, 300, null))
        assertEquals(Finding.Cut(0, 300), Finding.cutAround(line, 300, 200))
        assertEquals(Finding.Cut(640, 940), Finding.cutAround(line, 300, 700))
        val emoji = "👣".repeat(400)
        val cut = Finding.cutAround(emoji, 300, 501)
        assertTrue(!Character.isLowSurrogate(emoji[cut.from]) && !Character.isLowSurrogate(emoji[cut.to]))
        val starts = Finding.lineStarts(listOf("ab", "", "cde"))
        assertEquals(listOf(0, 3, 4), starts.toList())
        assertEquals(listOf(0, 0, 1, 2, 2, 2), listOf(0, 2, 3, 4, 6, 99).map { Finding.lineAt(starts, it) })
    }

    @Test
    fun `nothing to find, nothing found`() {
        assertEquals(emptyList<Finding.Match>(), scan("anything", ""))
        assertEquals(emptyList<Finding.Match>(), scan("", "x"))
    }

    @Test
    fun `after an edit the current match is the nearest one left`() {
        assertEquals(1, Finding.clamp(5, 2))
        assertEquals(0, Finding.clamp(-1, 2))
        assertEquals(-1, Finding.clamp(3, 0))
    }

    @Test
    fun `the bar opens with the selected words, when they are on one line`() {
        assertEquals("milk", Finding.prefill("milk"))
        assertNull(Finding.prefill("two\nlines"))
        assertNull(Finding.prefill(""))
        assertNull(Finding.prefill(null))
    }

    @Test
    fun `a row the desktop writes in is searched as a field is, weighed in letters`() {
        // 1,000 emoji and 1,000 x's: 3,000 UTF-16 units, 2,000 letters -- a
        // field on the desktop, and on the phone now: every match a stop.
        val row = "x😀".repeat(1_000)
        assertEquals(1_000, Finding.scan("", "x", matchCase = false, checklist = listOf(row)).size)
        // A row past the desktop's 2,000 is read-only: its line one stop.
        assertEquals(1, Finding.scan("", "x", matchCase = false, checklist = listOf("x".repeat(2_001))).size)
    }
}
