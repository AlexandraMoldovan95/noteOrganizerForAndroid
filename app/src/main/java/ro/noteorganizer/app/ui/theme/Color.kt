package ro.noteorganizer.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ro.noteorganizer.app.Themes

/**
 * One of the desktop's eight themes, as colours Compose can draw with.
 *
 * Every value comes from [Themes], which holds the desktop's palettes hex
 * for hex; nothing here chooses a colour of its own. The derived ones --
 * the words on a fill, the hairline, the inset well -- are worked out by
 * the desktop's own arithmetic, so a button reads the same on both.
 */
data class Palette(
    val name: String,
    val isDark: Boolean,
    val bg: Color,
    val sidebar: Color,
    val accent: Color,
    val card: Color,
    val text: Color,
    val subtext: Color,
    val danger: Color,
    val success: Color,
    val accentText: Color,
    val dangerText: Color,
    val successText: Color,
    val calendarHasNotes: Color,
    val onAccent: Color,
    val onDanger: Color,
    val hairline: Color,
    val inset: Color,
    /** The same palette as hex, for the colour arithmetic in [Themes]. */
    val hex: Map<String, String>,
) {
    /**
     * A colour a person picked -- a category's, a note's own -- as words
     * on a card: itself when it reads there, else the nearest shade of it
     * that does. The stripe beside it keeps the colour whole.
     */
    fun legibleOnCard(colour: String): Color =
        hexColor(Themes.legibleOn(colour, hex.getValue("card"), hex.getValue("text")))
}

/** `#rgb` or `#rrggbb` as a Compose colour; black for anything else. */
fun hexColor(value: String): Color {
    val v = value.trim().removePrefix("#")
    val full = if (v.length == 3) v.map { "$it$it" }.joinToString("") else v
    val rgb = full.toLongOrNull(16) ?: return Color.Black
    return Color(0xFF000000 or rgb)
}

fun paletteOf(name: String): Palette {
    val themeName = Themes.valid(name)
    val h = Themes.THEMES.getValue(themeName)
    return Palette(
        name = themeName,
        isDark = Themes.isDark(themeName),
        bg = hexColor(h.getValue("bg")),
        sidebar = hexColor(h.getValue("sidebar")),
        accent = hexColor(h.getValue("accent")),
        card = hexColor(h.getValue("card")),
        text = hexColor(h.getValue("text")),
        subtext = hexColor(h.getValue("subtext")),
        danger = hexColor(h.getValue("danger")),
        success = hexColor(h.getValue("success")),
        accentText = hexColor(h.getValue("accent_text")),
        dangerText = hexColor(h.getValue("danger_text")),
        successText = hexColor(h.getValue("success_text")),
        calendarHasNotes = hexColor(h.getValue("calendar_has_notes")),
        onAccent = hexColor(Themes.lettersOn(h.getValue("accent"))),
        onDanger = hexColor(Themes.lettersOn(h.getValue("danger"))),
        // The desktop's hairline() and inset_surface().
        hairline = hexColor(Themes.blend(h.getValue("card"), h.getValue("subtext"), 0.22)),
        inset = hexColor(Themes.blend(h.getValue("card"), h.getValue("text"), 0.07)),
        hex = h,
    )
}

/** The palette the screen is drawn in; every composable reads it from here. */
val LocalPalette = staticCompositionLocalOf { paletteOf(Themes.DEFAULT) }
