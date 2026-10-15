package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * settings.json, held to the desktop's rules: `load_categories`,
 * `adopt_categories_of`, the retention clamp, and the partial-writer rule
 * that keeps every key this app does not know.
 *
 * One thing the computer's JSON library does differently from the
 * phone's: it does not keep keys in order. So nothing here asserts the
 * order of a category map read from JSON, except General's first place,
 * which the code enforces itself.
 */
class SettingsTest {

    private fun dir(settings: String? = null): File {
        val dir = NioFiles.createTempDirectory("settings").toFile()
        if (settings != null) File(dir, "settings.json").writeText(settings)
        return dir
    }

    @Test
    fun `a hand-edited retention window is clamped, not obeyed`() {
        // The desktop's CODE is the specification -- `max(1, min(days,
        // 365))` -- not its docstring, which says 0 and -3 fall back to the
        // default. Only something that is not a whole number at all
        // ("forever") reaches the default.
        fun days(json: String) = Settings.retentionDays(JSONObject(json))
        assertEquals(1, days("""{"trash_retention_days": 0}"""))
        assertEquals(1, days("""{"trash_retention_days": -3}"""))
        assertEquals(30, days("""{"trash_retention_days": "forever"}"""))
        assertEquals(30, days("""{"trash_retention_days": 7.5}"""))
        assertEquals(365, days("""{"trash_retention_days": 99999}"""))
        assertEquals(7, days("""{"trash_retention_days": 7}"""))
        assertEquals(30, days("{}"))
    }

    @Test
    fun `only well-formed category colours are kept, and General is always first`() {
        val raw = JSONObject("""
            {"categories": {"Work": "#4cc9f0", "Bad": "#not-a-colour",
                            "Worse": 5, "  ": "#ffffff"}}
        """.trimIndent())
        val map = Settings.categories(raw)
        assertEquals(setOf("General", "Work"), map.keys)
        assertEquals("General", map.keys.first())
        assertEquals("#7c5cff", map["General"])
    }

    @Test
    fun `a saved map is the map -- deleted starting categories stay deleted`() {
        // The phone used to lay the starting set UNDER whatever was saved,
        // so a category deleted on the desktop came back on the phone.
        val raw = JSONObject("""{"categories": {"General": "#111111", "Recipes": "#222222"}}""")
        assertEquals(setOf("General", "Recipes"), Settings.categories(raw).keys)
    }

    @Test
    fun `nothing usable saved means the desktop's five starting categories`() {
        val map = Settings.categories(JSONObject("{}"))
        assertEquals(Themes.DEFAULT_CATEGORY_COLORS, map)
        assertEquals(listOf("General", "Work", "Personal", "Ideas", "Urgent"), map.keys.toList())
    }

    @Test
    fun `a name longer than the limit is dropped, never shortened`() {
        val long = "x".repeat(Settings.MAX_CATEGORY_NAME + 1)
        val raw = JSONObject().put("categories", JSONObject().put(long, "#123456").put("Ok", "#654321"))
        val map = Settings.categories(raw)
        assertFalse(map.keys.any { it.startsWith("xxx") })
        assertTrue("Ok" in map)
    }

    @Test
    fun `the notes are believed -- a category a note is filed under comes back`() {
        val notes = JSONArray("""
            [{"category": "Trips"},
             {"category": "Work"},
             {"category": " padded "},
             {"category": "General",
              "overrides": {"2026-10-12": {"category": "Day only"}}}]
        """.trimIndent())
        val map = Settings.categories(JSONObject("""{"categories": {"General": "#7c5cff"}}"""), notes)
        assertTrue("Trips" in map)
        assertTrue("Work" in map)
        assertTrue("a category only one day of a series uses", "Day only" in map)
        // A padded name is not adopted: the note would still not match it.
        assertFalse(" padded " in map)
        assertFalse("padded" in map)
        assertTrue(map.values.all { Notebook.isHexColor(it) })
    }

    @Test
    fun `a write keeps every key this app does not know`() {
        val dir = dir("""{"day_colors": {"2026-10-12": "#ff0000"}, "spelling": false, "theme": "dark"}""")
        val load = Settings.load(dir)
        assertTrue(Settings.put(load, Settings.USERNAME, "Maria") is SaveResult.Ok)
        val back = JSONObject(File(dir, "settings.json").readText())
        assertEquals("Maria", back.getString("username"))
        assertEquals("#ff0000", back.getJSONObject("day_colors").getString("2026-10-12"))
        assertEquals(false, back.getBoolean("spelling"))
        assertEquals("dark", back.getString("theme"))
    }

    @Test
    fun `a write that fails changes nothing on screen`() {
        val dir = dir("""{"username": "Ana"}""")
        val read = Settings.load(dir)
        // A folder that does not exist: the stream cannot even open. (A
        // directory in the file's place is no failure on Windows, where the
        // rename replaced an empty one.)
        val load = Settings.Load(File(dir, "missing/settings.json"), read.raw)
        val result = Settings.put(load, Settings.USERNAME, "Maria")
        assertTrue(result is SaveResult.Failed)
        assertEquals("Ana", Settings.username(load.raw))
    }

    @Test
    fun `an unreadable settings file is kept aside before anything replaces it`() {
        val dir = dir("{ not json")
        val load = Settings.load(dir)
        assertNotNull(load.problem)
        val kept = dir.listFiles()!!.single { it.name.startsWith("settings_corrupt_") }
        assertEquals("{ not json", kept.readText())
        assertEquals(0, load.raw.length())
    }

    @Test
    fun `the same unreadable bytes are kept once, not again at every start`() {
        // An earlier start already kept these bytes. Two loads within one
        // second would share a timestamped name and hide a second copy, so
        // the earlier copy is given an older name of its own.
        val dir = dir("{ not json")
        val earlier = File(dir, "settings_corrupt_20200101_000000.json")
        earlier.writeText("{ not json")
        val load = Settings.load(dir)
        assertTrue(load.problem!!.contains(earlier.name))
        assertEquals(listOf(earlier.name),
            dir.listFiles()!!.filter { it.name.startsWith("settings_corrupt_") }.map { it.name })
    }

    @Test
    fun `different unreadable bytes are kept as a copy of their own`() {
        val dir = dir("{ newer damage")
        File(dir, "settings_corrupt_20200101_000000.json").writeText("{ older damage")
        Settings.load(dir)
        assertEquals(2, dir.listFiles()!!.count { it.name.startsWith("settings_corrupt_") })
    }

    @Test
    fun `a missing file is an empty one, with nothing to report`() {
        val load = Settings.load(dir())
        assertNull(load.problem)
        assertEquals(0, load.raw.length())
    }

    @Test
    fun `following the phone picks the light or the dark theme for its mode`() {
        val raw = JSONObject("""{"theme": "light_mint",
            "phone_theme_light": "light_sky", "phone_theme_dark": "dark_forest"}""")
        assertEquals("dark_forest", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light_sky", Settings.theme(raw, phoneIsDark = false))
        raw.put(Settings.PHONE_FOLLOWS_PHONE, false)
        assertEquals("light_mint", Settings.theme(raw, phoneIsDark = true))
    }

    @Test
    fun `following is on by default, and a fresh phone follows between the originals`() {
        val raw = JSONObject("{}")
        assertTrue(Settings.followsPhone(raw))
        assertEquals("dark", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light", Settings.theme(raw, phoneIsDark = false))
    }

    @Test
    fun `the desktop's own choice is where following starts from`() {
        // A notebook that came from the desktop on Ocean Blue follows the
        // phone between Ocean Blue and Soft Lavender, not the originals.
        val raw = JSONObject("""{"theme": "dark_ocean"}""")
        assertEquals("dark_ocean", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light", Settings.theme(raw, phoneIsDark = false))
    }

    @Test
    fun `a theme name nobody knows, or of the wrong mode, falls back`() {
        val raw = JSONObject("""{"phone_theme_follows_phone": false, "theme": "neon"}""")
        assertEquals(Themes.DEFAULT, Settings.theme(raw, phoneIsDark = false))
        val crossed = JSONObject("""{"phone_theme_light": "dark", "phone_theme_dark": "light"}""")
        assertEquals("light", Settings.theme(crossed, phoneIsDark = false))
        assertEquals("dark", Settings.theme(crossed, phoneIsDark = true))
    }

    @Test
    fun `a category name is refused by the rule the loader keeps`() {
        val map = Settings.categories(JSONObject("{}"))
        assertNull(Settings.categoryNameRefusal("Trips", map))
        assertNotNull(Settings.categoryNameRefusal("y".repeat(81), map))
        val full = (1..Settings.MAX_CATEGORIES).associate { "c$it" to "#000000" }
        assertNotNull(Settings.categoryNameRefusal("one more", full))
        // A rename does not change the count.
        assertNull(Settings.categoryNameRefusal("renamed", full, adding = false))
    }
}
