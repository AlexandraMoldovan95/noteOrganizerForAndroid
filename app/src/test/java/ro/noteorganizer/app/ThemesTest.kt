package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour arithmetic, held to the DESKTOP's answers rather than to
 * answers worked out here: every expected value below was printed by the
 * desktop's own `core/themes.py` for the same inputs. A check that computes
 * its expectation the way the code does agrees with the code by
 * construction. The lists themselves are held to the desktop by
 * `tools/check_palettes.py`.
 */
class ThemesTest {

    @Test
    fun blendMatchesTheDesktop() {
        assertEquals("#8e8e97", Themes.blend("#1e1e2f", "#ffffff", 0.5))
        assertEquals("#3c3c54", Themes.blend("#2e2e48", "#f2f2f7", 0.07))
        assertEquals("#474762", Themes.blend("#2e2e48", "#a0a0c0", 0.22))
        assertEquals("#808080", Themes.blend("#000000", "#ffffff", 0.5))
        assertEquals("#a881c1", Themes.blend("#7c5cff", "#f9c74f", 0.35))
        assertEquals("#020202", Themes.blend("#010101", "#020202", 0.5))
    }

    @Test
    fun aHalfRoundsToEvenAsPythonDoes() {
        // 0.5 and 4.5 are where Python's round and Math.round part ways:
        // Python gives 0 and 4, Math.round gives 1 and 5.
        assertEquals("#000000", Themes.blend("#000000", "#010101", 0.5))
        assertEquals("#040404", Themes.blend("#040404", "#050505", 0.5))
    }

    @Test
    fun theWordsOnAFillMatchTheDesktop() {
        assertEquals("#1a1a1a", Themes.lettersOn("#a48fff"))
        assertEquals("#ffffff", Themes.lettersOn("#6a49e6"))
        assertEquals("#1a1a1a", Themes.lettersOn("#2f9be0"))
        assertEquals("#1a1a1a", Themes.lettersOn("#43c07a"))
        assertEquals("#1a1a1a", Themes.lettersOn("#f9c74f"))
        assertEquals("#ffffff", Themes.lettersOn("#b01c3c"))
        assertEquals("#1a1a1a", Themes.lettersOn("#00bbf9"))
        assertEquals(Themes.LETTERS_LIGHT, Themes.lettersOn("not a colour"))
        // Near the line, where a different rule would choose differently:
        // the note colour #e63946 is a near tie (4.176 for dark letters,
        // 4.168 for white), and the two greys sit either side of it.
        assertEquals("#1a1a1a", Themes.lettersOn("#e63946"))
        assertEquals("#1a1a1a", Themes.lettersOn("#808080"))
        assertEquals("#ffffff", Themes.lettersOn("#7a7a7a"))
        assertEquals("#1a1a1a", Themes.lettersOn("#ff0000"))
    }

    @Test
    fun aPickedColourAsWordsMatchesTheDesktop() {
        assertEquals("#936f1a", Themes.legibleOn("#f9c74f", "#ffffff", "#22223a"))
        assertEquals("#1f7e9c", Themes.legibleOn("#4cc9f0", "#ffffff", "#22223a"))
        assertEquals("#9f89fd", Themes.legibleOn("#7c5cff", "#2e2e48", "#f2f2f7"))
        assertEquals("#8da1b4", Themes.legibleOn("#577590", "#2e2e48", "#f2f2f7"))
        assertEquals("#cf3e5a", Themes.legibleOn("#ff5c7c", "#ffffff", "#22223a"))
        assertEquals("#98a1a9", Themes.legibleOn("#6c757d", "#1c3350", "#eaf2fb"))
        // Unreadable input gets the ink, never an exception mid-draw.
        assertEquals("#22223a", Themes.legibleOn("#zzz", "#ffffff", "#22223a"))
    }

    @Test
    fun contrastMatchesTheDesktop() {
        assertEquals(14.680158763775536, Themes.contrastRatio("#f2f2f7", "#1e1e2f")!!, 1e-9)
        assertEquals(14.230451295776492, Themes.contrastRatio("#22223a", "#f5f5fa")!!, 1e-9)
    }

    @Test
    fun anUnknownThemeNameFallsBackRatherThanDrawingNothing() {
        assertEquals("dark_ocean", Themes.valid("dark_ocean"))
        assertEquals(Themes.DEFAULT, Themes.valid("neon"))
        assertEquals(Themes.DEFAULT, Themes.valid(null))
        assertTrue(Themes.isDark("dark_slate"))
        assertTrue(!Themes.isDark("light_mint"))
    }

    @Test
    fun everyThemeHasEveryRole() {
        val roles = Themes.THEMES.getValue("dark").keys
        for ((name, palette) in Themes.THEMES) {
            assertEquals("roles of $name", roles, palette.keys)
            for ((role, hex) in palette) {
                assertTrue("$name.$role = $hex", Notebook.isHexColor(hex) && hex.length == 7)
            }
        }
        assertEquals(8, Themes.THEMES.size)
        assertEquals(Themes.THEMES.keys, (Themes.DARK + Themes.LIGHT).toSet())
        assertEquals(Themes.THEMES.keys, Themes.LABELS.keys)
    }
}
