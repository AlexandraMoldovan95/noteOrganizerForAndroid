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
    fun `Android's backup takes the app's data unless the person turned it off, or their answer cannot be read`() {
        // On by default, as it always was: nobody's backup stops silently.
        assertTrue(Settings.googleBackupOn(Settings.load(dir())))
        assertTrue(Settings.googleBackupOn(Settings.load(dir("""{"theme": "dark"}"""))))
        assertTrue(Settings.googleBackupOn(Settings.load(dir("""{"phone_google_backup": true}"""))))
        assertFalse(Settings.googleBackupOn(Settings.load(dir("""{"phone_google_backup": false}"""))))
        // A file that cannot be read may hold an "off": nothing is taken.
        assertFalse(Settings.googleBackupOn(Settings.load(dir("""{"phone_google_backup": fal"""))))
    }

    @Test
    fun `a file that could be neither read nor copied aside is written over by nothing, as the desktop's`() {
        // Settings and templates were written over then, their bytes in no other
        // copy; only the notes held. Held, every writer is refused.
        val folder = dir()
        val settings = File(folder, "settings.json")
        settings.writeText("""{"username": "Ana", "catego""")
        Files.holdWrites(settings)
        assertNotNull(Files.writeAtomic(settings, "{}"))
        assertEquals("""{"username": "Ana", "catego""", settings.readText())
        // ...and preserveCorrupt holds a file it cannot copy: here, one it cannot
        // read at all (a folder where the file should be).
        val unreadable = File(folder, "templates.json").apply { mkdir() }
        assertNull(Files.preserveCorrupt(unreadable))
        assertTrue(Files.writesHeld(unreadable))
        // ...until a later read in the same run copies it aside after all (space
        // freed, the activity made again): writes go on then.
        unreadable.delete()
        unreadable.writeText("[{")
        assertNotNull(Files.preserveCorrupt(unreadable))
        assertFalse("copied aside at last, it is no longer held", Files.writesHeld(unreadable))
        // A template's draft held is not deleted either, when its editor closes.
        val draft = File(folder, Templates.DRAFT).apply { writeText("""{"fields": {"title": "Shop""") }
        Files.holdWrites(draft)
        Templates.deleteDraft(folder)
        assertTrue("a held draft stays", draft.exists())
    }

    @Test
    fun `a phone restored from a backup made while off reads off before its first start, and Android's backup keeps the mark`() {
        // Restored, the notebook holds the mark and no settings.json: read
        // from the file alone that was "on", and Android's next backup,
        // before the app was ever opened, deleted the mark and kept nothing.
        val restored = dir()
        val mark = File(restored, Settings.GOOGLE_BACKUP_OFF_MARK)
        mark.writeText(Settings.offMark())
        assertFalse("Settings → Backup's answer", Settings.googleBackupOn(Settings.load(restored)))
        repeat(2) {
            assertEquals(Settings.FullBackup.OnlyMark(mark), Settings.forFullBackup(restored))
            assertTrue("the mark is still there to restore again", mark.isFile)
            assertEquals(false, JSONObject(mark.readText()).get(Settings.PHONE_GOOGLE_BACKUP))
        }
        assertFalse("nothing else is written", File(restored, "settings.json").exists())
        // Settings without an answer of their own are the same.
        val unanswered = dir("""{"theme": "dark"}""")
        File(unanswered, Settings.GOOGLE_BACKUP_OFF_MARK).writeText(Settings.offMark())
        assertFalse(Settings.googleBackupOn(Settings.load(unanswered)))
        assertTrue(Settings.forFullBackup(unanswered) is Settings.FullBackup.OnlyMark)
        // An answer in the file is the answer: on, the mark goes.
        val answered = dir("""{"phone_google_backup": true}""")
        File(answered, Settings.GOOGLE_BACKUP_OFF_MARK).writeText(Settings.offMark())
        assertTrue(Settings.googleBackupOn(Settings.load(answered)))
        assertEquals(Settings.FullBackup.Everything, Settings.forFullBackup(answered))
        assertFalse(File(answered, Settings.GOOGLE_BACKUP_OFF_MARK).exists())
        // Off in the file: only the mark, written for Android to keep.
        val off = dir("""{"phone_google_backup": false}""")
        assertTrue(Settings.forFullBackup(off) is Settings.FullBackup.OnlyMark)
        assertTrue(File(off, Settings.GOOGLE_BACKUP_OFF_MARK).isFile)
        // Nothing said anywhere: on, as it always was.
        assertEquals(Settings.FullBackup.Everything, Settings.forFullBackup(dir()))
        // And the first start then writes the answer into the file.
        File(restored, "notes_data.json").writeText("[]")
        Startup.prepare(restored, older = null)
        assertEquals(false, Settings.load(restored).raw.opt(Settings.PHONE_GOOGLE_BACKUP))
        assertFalse(Settings.googleBackupOn(Settings.load(restored)))
        assertFalse("read once, the mark goes", mark.exists())
    }

    @Test
    fun `the welcome note says where Android's backup is turned off`() {
        assertTrue(Welcome.build().getString("body").contains("Settings → Backup turns that off for this app"))
    }

    @Test
    fun `a hand-edited retention window is clamped, not obeyed`() {
        // The desktop's CODE is the specification -- `max(1, min(days, 365))` --
        // not its docstring: only something that is not a whole number at all
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
    fun `the purge goes only by a window it can read`() {
        // A clamp is right for showing a window, wrong for purging by one: a
        // hand-edited 0 would empty the Trash of everything a day old.
        val file = File("settings.json")
        fun window(json: String, problem: String? = null) =
            Settings.purgeWindow(Settings.Load(file, JSONObject(json), problem))
        assertEquals(90, window("""{"trash_retention_days": 90}"""))
        assertEquals(1, window("""{"trash_retention_days": 1}"""))
        assertEquals(365, window("""{"trash_retention_days": 365}"""))
        assertEquals("never chosen, the default", 30, window("{}"))
        for (bad in listOf("0", "-3", "366", "\"90\"", "7.5", "true", "null")) {
            assertNull(bad, window("""{"trash_retention_days": $bad}"""))
        }
        assertNull("a file that could not be read", window("{}", problem = "settings.json could not be read"))
        assertNull("lost at an earlier start", window("""{"trash_retention_days": 30, "phone_trash_window_unknown": true}"""))
        assertEquals(30, window("""{"trash_retention_days": 30, "phone_trash_window_unknown": false}"""))
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
    fun `a category's three-digit colour is read as six, and its name counted in characters`() {
        // 78 characters, four of them emoji: 82 in UTF-16, where the phone
        // counted it too long and dropped what the desktop keeps.
        val long = "🌿".repeat(4) + "a".repeat(74)
        val raw = JSONObject().put("categories", JSONObject().put("Home", "#abc").put(long, "#123456"))
        val map = Settings.categories(raw)
        assertEquals("#aabbcc", map["Home"])
        assertEquals("#123456", map[long])
        assertNull(Settings.categoryNameRefusal(long, map, adding = false))
        assertTrue(Settings.categoryNameRefusal("🌿" + "a".repeat(80), map, adding = false)!!.contains("this one has 81"))
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
    fun `a notebook not yet settled wears the theme for the phone's mode, as it did`() {
        val raw = JSONObject("""{"theme": "light_mint",
            "phone_theme_light": "light_sky", "phone_theme_dark": "dark_forest"}""")
        assertEquals("dark_forest", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light_sky", Settings.theme(raw, phoneIsDark = false))
        raw.put(Settings.PHONE_FOLLOWS_PHONE, false)
        assertEquals("light_mint", Settings.theme(raw, phoneIsDark = true))
    }

    @Test
    fun `a fresh phone starts in its own mode, on the originals`() {
        val raw = JSONObject("{}")
        assertTrue(Settings.followsPhone(raw))
        assertEquals("dark", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light", Settings.theme(raw, phoneIsDark = false))
    }

    @Test
    fun `the desktop's own choice is where a phone starts from`() {
        // A notebook that came from the desktop on Ocean Blue opens on Ocean
        // Blue on a dark phone, and on Soft Lavender on a light one.
        val raw = JSONObject("""{"theme": "dark_ocean"}""")
        assertEquals("dark_ocean", Settings.theme(raw, phoneIsDark = true))
        assertEquals("light", Settings.theme(raw, phoneIsDark = false))
    }

    @Test
    fun `a theme chosen is worn, kept as its mode's last, and the phone followed no more`() {
        assertEquals(mapOf(Settings.THEME to "dark_slate", Settings.PHONE_DARK_THEME to "dark_slate",
                           Settings.PHONE_FOLLOWS_PHONE to false), Settings.choosing("dark_slate"))
        assertEquals(mapOf(Settings.THEME to "light_sky", Settings.PHONE_LIGHT_THEME to "light_sky",
                           Settings.PHONE_FOLLOWS_PHONE to false), Settings.choosing("light_sky"))
    }

    @Test
    fun `Light and Dark go to the last of their mode, or the originals`() {
        val raw = JSONObject("""{"theme": "light_mint", "phone_theme_dark": "dark_ocean",
            "phone_theme_follows_phone": false}""")
        assertEquals("dark_ocean", Settings.themeForMode(raw, dark = true))
        assertEquals("light_mint", Settings.themeForMode(raw, dark = false))
        val none = JSONObject("""{"theme": "light_mint", "phone_theme_follows_phone": false}""")
        assertEquals("dark", Settings.themeForMode(none, dark = true))
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
