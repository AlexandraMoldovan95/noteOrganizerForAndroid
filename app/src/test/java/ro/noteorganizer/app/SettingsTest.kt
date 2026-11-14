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
    fun `a settings file that could not be read holds its marks in memory, and every later write carries them`() {
        // The start's write of them refused on a full phone, the file copied aside
        // and not held: the next write that went out dropped them, and the next
        // start purged the Trash at thirty days, Android's backup on.
        val folder = dir("""{"username": "Ana", "trash_retention_days": 365, "phone_google_backup": fal""")
        val load = Settings.load(folder)
        assertEquals(true, load.raw.opt(Settings.PHONE_TRASH_WINDOW_UNKNOWN))
        assertEquals(false, load.raw.opt(Settings.PHONE_GOOGLE_BACKUP))
        assertTrue(Settings.put(load, Reminders.LAST_SHOWN, "2026-11-14") is SaveResult.Ok)
        val again = Settings.load(folder)
        assertNull("the window still unknown", Settings.purgeWindow(again))
        assertFalse("the backup still off", Settings.googleBackupOn(again))
        // ...and turned on, or a window chosen, in that same run, the page reads
        // what was written: asked of the load's problem, it stayed Off and
        // unknown while the file, and Android's backup agent, read on.
        assertTrue(Settings.put(load, Settings.PHONE_GOOGLE_BACKUP, true) is SaveResult.Ok)
        assertTrue(Settings.googleBackupOn(load))
        assertTrue(Settings.putAll(load, mapOf(Settings.RETENTION_DAYS to 90,
            Settings.PHONE_TRASH_WINDOW_UNKNOWN to null)) is SaveResult.Ok)
        assertEquals(90, Settings.purgeWindow(load))
    }

    @Test
    fun `a read the storage refuses holds the file, never copying a good one aside, and a new start reads it again`() {
        // Taken for damage, a good settings file was copied aside and the start
        // wrote its marks over it.
        val folder = dir("""{"username": "Ana", "theme": "dark"}""")
        val settings = File(folder, "settings.json")
        val real = Files.readBytesOf
        // Refused once, then allowed, as on that road: taken for damage, the copy's
        // own read got through and the good file went aside.
        var refusals = 1
        Files.readBytesOf = { if (it.name == "settings.json" && refusals-- > 0) throw java.io.IOException("refused") else real(it) }
        try {
            val load = Settings.load(folder)
            assertNotNull(load.problem)
            assertTrue("held", Files.writesHeld(settings))
            assertTrue("no copy aside", folder.list()!!.none { it.startsWith("settings_corrupt_") })
            assertNotNull("nothing written over it", Settings.put(load, "x", 1).let { it as? SaveResult.Failed })
        } finally {
            Files.readBytesOf = real
        }
        assertEquals("Ana", JSONObject(settings.readText()).getString("username"))
        // Held, it reads as its marks all run though the file now reads, as the
        // desktop's: read for real, the Trash page contradicted its own chips.
        assertNotNull(Settings.load(folder).problem)
        assertEquals("", Settings.load(folder).raw.optString("username"))
        assertEquals(true, Settings.load(folder).raw.opt(Settings.PHONE_TRASH_WINDOW_UNKNOWN))
        // A new start (Android 8 to 11 reopening in the same process) reads it
        // afresh: held past it, every write was refused after it read fine.
        File(folder, "notes_data.json").writeText("[]")
        Startup.prepare(folder, older = null)
        assertFalse(Files.writesHeld(settings))
        assertEquals("Ana", Settings.load(folder).raw.optString("username"))
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
        // ...until a later read copies it aside (space freed, the app opened again
        // on Android 8 to 11, where Back ends the activity, not the process).
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
        // A file the system refuses hands nothing over, and writes no off mark:
        // written, it outlived the read and turned the backup off for good.
        val unread = dir("""{"theme": "dark", "phone_google_backup": true}""")
        val real = Files.readBytesOf
        Files.readBytesOf = { if (it.name == "settings.json") throw java.io.IOException("refused") else real(it) }
        try {
            assertEquals(Settings.FullBackup.Nothing, Settings.forFullBackup(unread))
        } finally {
            Files.readBytesOf = real
        }
        assertFalse(File(unread, Settings.GOOGLE_BACKUP_OFF_MARK).exists())
        // ...nor while the app's own run holds it, though this read got through:
        // Settings → Backup says nothing new is taken while it stays open.
        assertEquals(Settings.FullBackup.Nothing, Settings.forFullBackup(unread))
        Files.newStart()
        assertEquals(Settings.FullBackup.Everything, Settings.forFullBackup(unread))
        // One damaged and copied aside is off by its marks, as the page says.
        val damaged = dir("""{"theme": "dark", "phone_google_ba""")
        assertTrue(Settings.forFullBackup(damaged) is Settings.FullBackup.OnlyMark)
        assertFalse(Settings.googleBackupOn(Settings.load(damaged)))
        // And the first start then writes the answer into the file.
        File(restored, "notes_data.json").writeText("[]")
        Startup.prepare(restored, older = null)
        assertEquals(false, Settings.load(restored).raw.opt(Settings.PHONE_GOOGLE_BACKUP))
        assertFalse(Settings.googleBackupOn(Settings.load(restored)))
        assertFalse("read once, the mark goes", mark.exists())
    }

    @Test
    fun `a restore's off mark outlives a start whose settings read was refused`() {
        // Held in memory alone, the off was taken for the file's: the mark went,
        // the file never said off, and the next start read on and handed it all.
        val restored = dir("""{"welcome_offered": true}""")
        val mark = File(restored, Settings.GOOGLE_BACKUP_OFF_MARK).apply { writeText(Settings.offMark()) }
        File(restored, "notes_data.json").writeText("[]")
        val real = Files.readBytesOf
        Files.readBytesOf = { if (it.name == "settings.json") throw java.io.IOException("refused") else real(it) }
        try {
            Startup.prepare(restored, older = null)
        } finally {
            Files.readBytesOf = real
        }
        assertTrue("kept while its answer cannot be written", mark.isFile)
        Startup.prepare(restored, older = null)
        assertEquals(false, Settings.load(restored).raw.opt(Settings.PHONE_GOOGLE_BACKUP))
        assertFalse("written, the mark goes", mark.exists())
        assertTrue(Settings.forFullBackup(restored) is Settings.FullBackup.OnlyMark)
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
        assertNull("a file that could not be read, as Settings.load gives it",
            Settings.purgeWindow(Settings.load(dir("{ broken"))))
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
        // Nothing of it read but its two marks, held in memory for every write.
        assertEquals(setOf(Settings.PHONE_TRASH_WINDOW_UNKNOWN, Settings.PHONE_GOOGLE_BACKUP),
            load.raw.keys().asSequence().toSet())
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
