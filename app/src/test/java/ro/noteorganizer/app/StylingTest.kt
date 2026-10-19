package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Span algebra: what a style means where another one already is.
 *
 * Written as "which spans exist afterwards", because that is what lands
 * in the file and what the desktop will read back.
 */
class StylingTest {

    private fun s(start: Int, end: Int, style: String, value: String = "") =
        FormatSpan(start, end, style, value)

    private fun shape(spans: List<FormatSpan>) =
        spans.sortedWith(compareBy({ it.start }, { it.style }))
            .map { "${it.start}-${it.end}:${it.style}${if (it.value.isEmpty()) "" else "=" + it.value}" }

    // ---- the simple cases ----

    @Test
    fun `applying a style to plain text makes one span`() {
        assertEquals(listOf("2-5:bold"), shape(setStyle(emptyList(), 2, 5, "bold")))
    }

    @Test
    fun `applying the same style beside itself makes one span, not two`() {
        val first = setStyle(emptyList(), 0, 5, "bold")
        assertEquals(listOf("0-10:bold"), shape(setStyle(first, 5, 10, "bold")))
    }

    @Test
    fun `an empty selection changes nothing`() {
        val before = listOf(s(0, 5, "bold"))
        assertEquals(before, setStyle(before, 3, 3, "italic"))
        assertEquals(before, toggleStyle(before, 3, 3, "bold"))
    }

    @Test
    fun `bold and italic stored as two spans are read as one, so B takes the bold alone off`() {
        // A desktop file from before its normalize_formatting folded the two.
        val note = Notebook.parseNotes("""[{"id": 1, "body": "Note it",
            "formatting": [{"start": 0, "end": 4, "style": "bold"}, {"start": 0, "end": 7, "style": "italic"}]}]""").single()
        assertEquals(listOf("0-4:bold_italic", "4-7:italic"), shape(note.formatting))
        assertEquals("the italic stays where the bold goes", listOf("0-7:italic"), shape(toggleStyle(note.formatting, 0, 4, "bold")))
    }

    // ---- removing from the middle ----

    @Test
    fun `clearing the middle of a span leaves the two ends`() {
        val bold = listOf(s(0, 20, "bold"))
        assertEquals(listOf("0-5:bold", "10-20:bold"), shape(clearStyle(bold, 5, 10, "bold")))
    }

    @Test
    fun `clearing the front of a span shortens it`() {
        val bold = listOf(s(0, 20, "bold"))
        assertEquals(listOf("5-20:bold"), shape(clearStyle(bold, 0, 5, "bold")))
    }

    @Test
    fun `clearing all of a span removes it`() {
        assertEquals(emptyList<String>(), shape(clearStyle(listOf(s(0, 20, "bold")), 0, 20, "bold")))
    }

    // ---- the rule the desktop imposes ----

    @Test
    fun `bolding italic text stores bold_italic, not two spans`() {
        // Two Tk tags that both set a font do not compose, so the desktop
        // keeps the combination as one style. Storing "bold" and "italic"
        // separately would make the desktop draw one and lose the other.
        val italic = listOf(s(0, 10, "italic"))
        assertEquals(listOf("0-10:bold_italic"), shape(setStyle(italic, 0, 10, "bold")))
    }

    @Test
    fun `un-bolding bold_italic text leaves it italic`() {
        val both = listOf(s(0, 10, "bold_italic"))
        assertEquals(listOf("0-10:italic"), shape(clearStyle(both, 0, 10, "bold")))
    }

    @Test
    fun `bolding half of an italic run splits it correctly`() {
        val italic = listOf(s(0, 10, "italic"))
        assertEquals(
            listOf("0-5:bold_italic", "5-10:italic"),
            shape(setStyle(italic, 0, 5, "bold")),
        )
    }

    @Test
    fun `a selection that is bold_italic reports as both bold and italic`() {
        val both = listOf(s(0, 10, "bold_italic"))
        val styles = stylesOver(both, 2, 8)
        assertTrue(styles.contains("bold"))
        assertTrue(styles.contains("italic"))
    }

    // ---- groups that do not collide ----

    @Test
    fun `underline and bold live on the same letters happily`() {
        val bold = setStyle(emptyList(), 0, 10, "bold")
        assertEquals(
            listOf("0-10:bold", "0-10:underline"),
            shape(setStyle(bold, 0, 10, "underline")),
        )
    }

    @Test
    fun `a second colour replaces the first rather than stacking`() {
        // Two text colours on one letter is not a thing.
        val red = setStyle(emptyList(), 0, 10, "text_color", "#ff0000")
        assertEquals(
            listOf("0-10:text_color=#0000ff"),
            shape(setStyle(red, 0, 10, "text_color", "#0000ff")),
        )
    }

    @Test
    fun `a colour and a highlight are different groups and coexist`() {
        val colored = setStyle(emptyList(), 0, 5, "text_color", "#ff0000")
        val result = setStyle(colored, 0, 5, "highlight", "#fff3b0")
        assertEquals(
            listOf("0-5:highlight=#fff3b0", "0-5:text_color=#ff0000"),
            shape(result),
        )
    }

    // ---- what a toolbar button does ----

    @Test
    fun `a button turns a style off when the whole selection has it`() {
        val bold = listOf(s(0, 10, "bold"))
        assertEquals(emptyList<String>(), shape(toggleStyle(bold, 0, 10, "bold")))
    }

    @Test
    fun `a button turns it ON when only part of the selection has it`() {
        // Otherwise the user would have to press it twice to make a mixed
        // selection uniformly bold.
        val partly = listOf(s(0, 5, "bold"))
        assertEquals(listOf("0-10:bold"), shape(toggleStyle(partly, 0, 10, "bold")))
    }

    @Test
    fun `asking for a different colour changes it instead of clearing it`() {
        val red = listOf(s(0, 10, "text_color", "#ff0000"))
        assertEquals(
            listOf("0-10:text_color=#0000ff"),
            shape(toggleStyle(red, 0, 10, "text_color", "#0000ff")),
        )
    }

    @Test
    fun `asking for the SAME colour again clears it`() {
        val red = listOf(s(0, 10, "text_color", "#ff0000"))
        assertEquals(
            emptyList<String>(),
            shape(toggleStyle(red, 0, 10, "text_color", "#ff0000")),
        )
    }

    // ---- what a button should look like ----

    @Test
    fun `a style counts as on only when it covers the whole selection`() {
        val half = listOf(s(0, 5, "bold"))
        assertTrue(stylesOver(half, 0, 5).contains("bold"))
        assertFalse(stylesOver(half, 0, 10).contains("bold"))
    }

    @Test
    fun `two touching spans of the same style cover the range between them`() {
        val split = listOf(s(0, 5, "bold"), s(5, 10, "bold"))
        assertTrue(stylesOver(split, 2, 8).contains("bold"))
    }

    @Test
    fun `a gap between two spans means the style is not on`() {
        val gapped = listOf(s(0, 4, "bold"), s(6, 10, "bold"))
        assertFalse(stylesOver(gapped, 0, 10).contains("bold"))
    }

    // ---- the other direction of the offset conversion ----

    @Test
    fun `a text field's UTF-16 position becomes the stored code-point one`() {
        // "a", an emoji, "b": the field says the b is at 3, the file calls
        // it 2. Applying a style at 3 would put it one character late.
        val text = "a👣b"
        val map = codePointOffsets(text, listOf(0, 1, 3, 4))
        assertEquals(0, map[0])
        assertEquals(1, map[1])
        assertEquals(2, map[3])
        assertEquals(3, map[4])
    }

    @Test
    fun `the two conversions are inverses of each other`() {
        val text = "👣 ab👣c"
        val forward = utf16Offsets(text, (0..codePointsOf(text).size).toList())
        for ((codePoint, utf16) in forward) {
            assertEquals(codePoint, codePointOffsets(text, listOf(utf16))[utf16])
        }
    }

    @Test
    fun `plain text needs no conversion either way`() {
        assertEquals(mapOf(0 to 0, 4 to 4), codePointOffsets("abcdef", listOf(0, 4)))
    }

    // ---- what the size box says: _uniform_font_in_selection ----

    @Test
    fun `the size box says the size that covers the whole selection`() {
        val spans = listOf(FormatSpan(0, 5, "font_size", "14"), FormatSpan(5, 10, "font_size", "14"))
        assertEquals("14", uniformValue(spans, 2, 8, "font_size"))
    }

    @Test
    fun `a mix of sizes, or a part with none, is no one size`() {
        val mixed = listOf(FormatSpan(0, 5, "font_size", "14"), FormatSpan(5, 10, "font_size", "20"))
        assertEquals(null, uniformValue(mixed, 2, 8, "font_size"))
        val partly = listOf(FormatSpan(0, 5, "font_size", "14"))
        assertEquals(null, uniformValue(partly, 2, 8, "font_size"))
        assertEquals(null, uniformValue(emptyList(), 0, 3, "font_size"))
        // Another style's value is not a size.
        assertEquals(null, uniformValue(listOf(FormatSpan(0, 5, "font_family", "Georgia")), 0, 5, "font_size"))
        assertEquals("Georgia", uniformValue(listOf(FormatSpan(0, 5, "font_family", "Georgia")), 0, 5, "font_family"))
    }
}
