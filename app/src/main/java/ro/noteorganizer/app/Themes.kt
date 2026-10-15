package ro.noteorganizer.app

/**
 * The desktop's palettes and swatches, colour for colour.
 *
 * The desktop's `core/themes.py` is the specification: eight themes with
 * the same role names (`bg`, `card`, `text`, `accent`...), the names it
 * shows for them, the categories a new notebook starts with, and the
 * swatches a person picks from. Copied rather than re-chosen, so a note
 * coloured on one device is the same colour on the other, and a theme is
 * recognisably the same theme. `tools/check_palettes.py` reads both files
 * and fails on any difference.
 *
 * Plain strings and arithmetic, no Compose: like the rest of the rules
 * layer, this is tested on the computer. `AppTheme.kt` turns it into
 * colours to draw with.
 */
object Themes {

    /** The theme a notebook without a readable choice starts on. */
    const val DEFAULT = "dark"

    val DARK = listOf("dark", "dark_ocean", "dark_forest", "dark_slate")
    val LIGHT = listOf("light", "light_sky", "light_mint", "light_warm")

    /** What Settings → Appearance calls each one. */
    val LABELS = mapOf(
        "dark" to "Midnight Purple",
        "dark_ocean" to "Ocean Blue",
        "dark_forest" to "Forest Green",
        "dark_slate" to "Warm Charcoal",
        "light" to "Soft Lavender",
        "light_sky" to "Sky Blue",
        "light_mint" to "Fresh Mint",
        "light_warm" to "Warm Cream",
    )

    /**
     * Every theme defines the same keys. `accent`, `danger` and `success`
     * are FILLS; the `_text` twins are the same roles as words on an
     * ordinary surface, because no single shade can be both on a dark
     * theme. The words ON a fill are not written down: [lettersOn] picks
     * them by measured contrast.
     */
    val THEMES: Map<String, Map<String, String>> = mapOf(
        "dark" to mapOf(
            "bg" to "#1e1e2f",
            "sidebar" to "#26263f",
            "accent" to "#a48fff",
            "accent_hover" to "#bcadff",
            "card" to "#2e2e48",
            "check_well" to "#f7f7fb",
            "text" to "#f2f2f7",
            "subtext" to "#a0a0c0",
            "danger" to "#b01c3c",
            "success" to "#4cd9a3",
            "accent_text" to "#aba4ff",
            "danger_text" to "#ff8d9d",
            "success_text" to "#72e2b9",
            "find_miss" to "#522b33",
            "calendar_has_notes" to "#3d3168",
        ),
        "dark_ocean" to mapOf(
            "bg" to "#0f1b2d",
            "sidebar" to "#16273f",
            "accent" to "#2f9be0",
            "accent_hover" to "#54b0ec",
            "card" to "#1c3350",
            "check_well" to "#f7f7fb",
            "text" to "#eaf2fb",
            "subtext" to "#8fa8c4",
            "danger" to "#b01c3c",
            "success" to "#3ecf8e",
            "accent_text" to "#50b5fa",
            "danger_text" to "#ff8f8f",
            "success_text" to "#62dba4",
            "find_miss" to "#522f2f",
            "calendar_has_notes" to "#1f4266",
        ),
        "dark_forest" to mapOf(
            "bg" to "#14231b",
            "sidebar" to "#1b3227",
            "accent" to "#43c07a",
            "accent_hover" to "#5ed494",
            "card" to "#213e30",
            "check_well" to "#f7f7fb",
            "text" to "#eafaf0",
            "subtext" to "#8fbaa1",
            "danger" to "#b01c3c",
            "success" to "#4fd3e0",
            "accent_text" to "#4ec983",
            "danger_text" to "#ffa09d",
            "success_text" to "#7fdfea",
            "find_miss" to "#523333",
            "calendar_has_notes" to "#254e39",
        ),
        "dark_slate" to mapOf(
            "bg" to "#1c1c22",
            "sidebar" to "#26262e",
            "accent" to "#e0863f",
            "accent_hover" to "#ec9d5a",
            "card" to "#2d2d38",
            "check_well" to "#f7f7fb",
            "text" to "#f3f2f0",
            "subtext" to "#a8a8b0",
            "danger" to "#b01c3c",
            "success" to "#5ad6d0",
            "accent_text" to "#ec914b",
            "danger_text" to "#ff8f8f",
            "success_text" to "#7fe0dc",
            "find_miss" to "#522f2f",
            "calendar_has_notes" to "#3a2f22",
        ),
        "light" to mapOf(
            "bg" to "#f5f5fa",
            "sidebar" to "#e9e9f5",
            "accent" to "#6a49e6",
            "accent_hover" to "#5a38d6",
            "card" to "#ffffff",
            "check_well" to "#f7f7fb",
            "text" to "#22223a",
            "subtext" to "#676780",
            "danger" to "#9e1838",
            "success" to "#2fae82",
            "accent_text" to "#5b2dd2",
            "danger_text" to "#a81a3e",
            "success_text" to "#00704f",
            "find_miss" to "#f9dddd",
            "calendar_has_notes" to "#e3dbff",
        ),
        "light_sky" to mapOf(
            "bg" to "#f0f6fc",
            "sidebar" to "#dcebf8",
            "accent" to "#1a73b8",
            "accent_hover" to "#135f99",
            "card" to "#ffffff",
            "check_well" to "#f7f7fb",
            "text" to "#173049",
            "subtext" to "#536b81",
            "danger" to "#9e1838",
            "success" to "#2fae82",
            "accent_text" to "#005b97",
            "danger_text" to "#a81a3e",
            "success_text" to "#00704f",
            "find_miss" to "#f9dddd",
            "calendar_has_notes" to "#d3e7f8",
        ),
        "light_mint" to mapOf(
            "bg" to "#f1f9f4",
            "sidebar" to "#dcefe3",
            "accent" to "#28a66e",
            "accent_hover" to "#3cbb83",
            "card" to "#ffffff",
            "check_well" to "#f7f7fb",
            "text" to "#183328",
            "subtext" to "#527061",
            "danger" to "#9e1838",
            "success" to "#2db4c2",
            "accent_text" to "#006640",
            "danger_text" to "#a81a3e",
            "success_text" to "#00707c",
            "find_miss" to "#f9dddd",
            "calendar_has_notes" to "#d3edde",
        ),
        "light_warm" to mapOf(
            "bg" to "#faf6f0",
            "sidebar" to "#f1e7da",
            "accent" to "#d97a2b",
            "accent_hover" to "#e58f47",
            "card" to "#ffffff",
            "check_well" to "#f7f7fb",
            "text" to "#3a2c1c",
            "subtext" to "#776653",
            "danger" to "#9e1838",
            "success" to "#33b8b0",
            "accent_text" to "#8a4600",
            "danger_text" to "#a81a3e",
            "success_text" to "#00726b",
            "find_miss" to "#f9dddd",
            "calendar_has_notes" to "#f3e2cd",
        ),
    )

    /** The categories a new notebook starts with. */
    val DEFAULT_CATEGORY_COLORS = linkedMapOf(
        "General" to "#7c5cff",
        "Work" to "#4cc9f0",
        "Personal" to "#f9c74f",
        "Ideas" to "#f77f00",
        "Urgent" to "#ff5c7c",
    )

    /** The one category that can never be renamed or deleted. */
    const val PROTECTED_CATEGORY = "General"

    /** For categories and calendar days. */
    val PALETTE_COLORS = listOf(
        "#7c5cff", "#4cc9f0", "#f9c74f", "#f77f00", "#ff5c7c",
        "#43aa8b", "#90be6d", "#f94144", "#577590", "#9b5de5",
        "#00bbf9", "#00f5d4", "#fee440", "#ff924c", "#ff6392",
        "#8ac926", "#1982c4", "#6a4c93", "#e07a5f", "#b5838d",
    )

    /** A note's own colour, and the text colour. */
    val PRESET_NOTE_COLORS = listOf(
        "#7c5cff", "#4cc9f0", "#f9c74f", "#f77f00", "#ff5c7c",
        "#4cd9a3", "#ff8fa3", "#a3a3ff", "#ffd166", "#06d6a0",
        "#e63946", "#3a86ff", "#c77dff", "#8ac926", "#6c757d",
    )

    /** Soft, like a real highlighter, so words on top stay readable. */
    val HIGHLIGHT_COLOR_PALETTE = listOf(
        "#fff3b0", "#c8f4d9", "#ffd6e8", "#cde7ff", "#ffe0b3",
        "#e0d4ff", "#ffd1d1", "#c8f4ec", "#e8e8e8", "#f0f4c3",
    )

    /** A known theme name, or the default -- never a name nothing draws. */
    fun valid(name: String?): String = if (name != null && name in THEMES) name else DEFAULT

    fun isDark(name: String): Boolean = name in DARK

    // ---- Colour arithmetic, as core/themes.py does it ----

    /** The words on a filled control. */
    const val LETTERS_LIGHT = "#ffffff"
    const val LETTERS_DARK = "#1a1a1a"

    private fun channels(hex: String): DoubleArray? {
        val h = hex.trim()
        if (h.length != 7 || !h.startsWith("#")) return null
        val v = h.substring(1).toIntOrNull(16) ?: return null
        return doubleArrayOf(
            ((v shr 16) and 0xff) / 255.0,
            ((v shr 8) and 0xff) / 255.0,
            (v and 0xff) / 255.0,
        )
    }

    private fun bytes(hex: String): IntArray? {
        val h = hex.trim()
        if (h.length != 7 || !h.startsWith("#")) return null
        val v = h.substring(1).toIntOrNull(16) ?: return null
        return intArrayOf((v shr 16) and 0xff, (v shr 8) and 0xff, v and 0xff)
    }

    /**
     * Python's `round`: halves go to the even neighbour. Kotlin's
     * `Math.round` sends them up, and a blend landing on x.5 then comes out
     * one step lighter than the desktop's -- a different colour by one,
     * which is still a different colour.
     */
    private fun pyRound(x: Double): Int = Math.rint(x).toInt()

    /** Mixes two `#rrggbb` colours: t = 0 gives [a], t = 1 gives [b]. */
    fun blend(a: String, b: String, t: Double): String {
        val x = bytes(a) ?: return a
        val y = bytes(b) ?: return a
        val mixed = IntArray(3) { i -> pyRound(x[i] + (y[i] - x[i]) * t).coerceIn(0, 255) }
        return "#%02x%02x%02x".format(mixed[0], mixed[1], mixed[2])
    }

    /** WCAG 2.1 relative luminance. */
    fun relativeLuminance(hex: String): Double? {
        val c = channels(hex) ?: return null
        val linear = c.map { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }

    /** WCAG 2.1 contrast ratio. */
    fun contrastRatio(one: String, other: String): Double? {
        val a = relativeLuminance(one) ?: return null
        val b = relativeLuminance(other) ?: return null
        val light = maxOf(a, b)
        val dark = minOf(a, b)
        return (light + 0.05) / (dark + 0.05)
    }

    /** White or near-black, whichever reads better on [fill]. */
    fun lettersOn(fill: String): String {
        val dark = contrastRatio(LETTERS_DARK, fill) ?: return LETTERS_LIGHT
        val light = contrastRatio(LETTERS_LIGHT, fill) ?: return LETTERS_LIGHT
        return if (dark > light) LETTERS_DARK else LETTERS_LIGHT
    }

    /**
     * [colour] as words on [ground]: itself when it reads there at
     * [minimum], else the nearest shade of it that does -- darker in the
     * same hue on a light ground, mixed toward [ink] on a dark one. For a
     * colour a person picked (a category's, a note's own) shown as words;
     * the stripe beside it keeps the colour whole. The desktop's
     * `legible_on`.
     */
    fun legibleOn(colour: String, ground: String, ink: String, minimum: Double = 4.6): String {
        val first = contrastRatio(colour, ground) ?: return ink
        if (first >= minimum) return colour
        val darkWins = (contrastRatio(LETTERS_DARK, ground) ?: 0.0) >=
            (contrastRatio(LETTERS_LIGHT, ground) ?: 0.0)
        if (darkWins) {
            val c = channels(colour) ?: return ink
            val hsv = rgbToHsv(c[0], c[1], c[2])
            for (step in 1..100) {
                val rgb = hsvToRgb(hsv[0], minOf(1.0, hsv[1] * (1 + step / 200.0)),
                                   hsv[2] * (1 - step / 100.0))
                val darker = "#%02x%02x%02x".format(
                    pyRound(rgb[0] * 255), pyRound(rgb[1] * 255), pyRound(rgb[2] * 255))
                if ((contrastRatio(darker, ground) ?: 0.0) >= minimum) return darker
            }
        } else {
            for (step in 1..20) {
                val mixed = blend(colour, ink, step / 20.0)
                if ((contrastRatio(mixed, ground) ?: 0.0) >= minimum) return mixed
            }
        }
        return ink
    }

    // Python's colorsys, which the desktop's legible_on walks in.
    private fun rgbToHsv(r: Double, g: Double, b: Double): DoubleArray {
        val maxc = maxOf(r, g, b)
        val minc = minOf(r, g, b)
        val v = maxc
        if (minc == maxc) return doubleArrayOf(0.0, 0.0, v)
        val s = (maxc - minc) / maxc
        val rc = (maxc - r) / (maxc - minc)
        val gc = (maxc - g) / (maxc - minc)
        val bc = (maxc - b) / (maxc - minc)
        var h = when (maxc) {
            r -> bc - gc
            g -> 2.0 + rc - bc
            else -> 4.0 + gc - rc
        }
        h = ((h / 6.0) % 1.0 + 1.0) % 1.0
        return doubleArrayOf(h, s, v)
    }

    private fun hsvToRgb(h: Double, s: Double, v: Double): DoubleArray {
        if (s == 0.0) return doubleArrayOf(v, v, v)
        val i = (h * 6.0).toInt()
        val f = h * 6.0 - i
        val p = v * (1.0 - s)
        val q = v * (1.0 - s * f)
        val t = v * (1.0 - s * (1.0 - f))
        return when (((i % 6) + 6) % 6) {
            0 -> doubleArrayOf(v, t, p)
            1 -> doubleArrayOf(q, v, p)
            2 -> doubleArrayOf(p, v, t)
            3 -> doubleArrayOf(p, q, v)
            4 -> doubleArrayOf(t, p, v)
            else -> doubleArrayOf(v, p, q)
        }
    }
}
