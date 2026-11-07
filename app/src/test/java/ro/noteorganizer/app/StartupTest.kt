package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.file.Files as NioFiles

/** What the start does to a notebook before the first screen. */
class StartupTest {

    private fun temp(name: String) = NioFiles.createTempDirectory(name).toFile()

    @Test
    fun `an import's unpacked backup and a file being attached, left by an app that went, are cleared`() {
        // Rebuilt while an import's question was up, the app left the whole
        // unpacked notebook in its cache for good.
        val cache = temp("cache")
        File(cache, "import_1700000000000/attachments").mkdirs()
        File(cache, "import_1700000000000/notes_data.json").writeText("[]")
        File(cache, "import_1700000000001.zip").writeText("zip")
        File(cache, "attach_123456").writeText("photo")
        File(cache, "share").mkdirs()
        File(cache, "share/Note.pdf").writeText("pdf")
        Startup.clearLeftovers(cache)
        assertEquals("only what an import or an attaching leaves goes", listOf("share"),
            cache.listFiles()!!.map { it.name }.sorted())
        assertTrue(File(cache, "share/Note.pdf").isFile)
    }

    @Test
    fun `a brand-new notebook opens on the welcome note, once`() {
        val dir = File(temp("phone"), "notebook")
        assertNull(Startup.prepare(dir, older = null))
        val notes = JSONArray(File(dir, "notes_data.json").readText())
        assertEquals(1, notes.length())
        assertEquals(Welcome.TITLE, notes.getJSONObject(0).getString("title"))
        val settings = JSONObject(File(dir, "settings.json").readText())
        assertEquals(true, settings.getBoolean("welcome_offered"))
        assertEquals(notes.getJSONObject(0).getString("uid"), settings.getString("welcome_note_uid"))
        // The second start greets nobody.
        Startup.prepare(dir, older = null)
        assertEquals(1, JSONArray(File(dir, "notes_data.json").readText()).length())
    }

    @Test
    fun `days a series' copy set aside become notes at the start, written at once, made once`() {
        // Left for the next save, each start made them again under new
        // identities and said so again.
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "gym", "title": "Gym", "body": "x",
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "_set_aside": [{"day": "2026-11-09", "body": "Earlier words"}]}]""")
        val said = Startup.prepare(dir, older = null)!!
        assertTrue(said.contains("(earlier text)"))
        // ...for both reasons, a day the note deleted among them (such notes
        // were once said to be of days holding text of their own).
        assertTrue(said, said.contains("those days now show other text, or were deleted"))
        val first = JSONArray(File(dir, "notes_data.json").readText())
        assertEquals(2, first.length())
        assertEquals("Gym (earlier text)", first.getJSONObject(1).getString("title"))
        assertFalse(first.getJSONObject(0).has("_set_aside"))
        Startup.prepare(dir, older = null)
        val again = JSONArray(File(dir, "notes_data.json").readText())
        assertEquals(2, again.length())
        assertEquals(first.getJSONObject(1).getString("uid"), again.getJSONObject(1).getString("uid"))
        // ...and a Trash file the start could not read does not hold that
        // write: the notes were read.
        val damaged = temp("phone")
        File(damaged, "notes_data.json").writeText("""[{"id": 1, "uid": "gym", "title": "Gym", "body": "x",
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "_set_aside": [{"day": "2026-11-16", "body": "More"}]}]""")
        File(damaged, "trash.json").writeText("{")
        Startup.prepare(damaged, older = null)
        assertEquals(2, JSONArray(File(damaged, "notes_data.json").readText()).length())
    }

    @Test
    fun `an emptied notebook is never greeted again`() {
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("[]")
        Startup.prepare(dir, older = null)
        assertEquals(0, JSONArray(File(dir, "notes_data.json").readText()).length())
    }

    @Test
    fun `a notebook in the older folder is copied in, files and all, and nothing is deleted`() {
        val older = temp("older")
        File(older, "notes_data.json").writeText("""[{"id": 1, "title": "Kept"}]""")
        File(older, "settings.json").writeText("""{"username": "Maria"}""")
        File(older, "attachments").mkdirs()
        File(older, "attachments/a.png").writeText("A")
        val dir = File(temp("phone"), "notebook")
        val notice = Startup.prepare(dir, older)
        assertTrue(notice!!.contains("brought into"))
        assertEquals("Kept", JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0).getString("title"))
        assertEquals("A", File(dir, "attachments/a.png").readText())
        assertTrue("the older copy stays where it was", File(older, "notes_data.json").exists())
        assertTrue("no welcome note in a notebook that arrived",
            JSONArray(File(dir, "notes_data.json").readText()).length() == 1)
    }

    @Test
    fun `only a development build looks in the older folder`() {
        // A release build took in whatever was there: written by another app
        // on Android 8 to 10, or a file of 2 GB that stopped every start.
        val older = temp("older")
        assertEquals(older, Startup.olderFolder(debuggable = true, external = older))
        assertNull(Startup.olderFolder(debuggable = false, external = older))
    }

    @Test
    fun `an older notebook too large to read is left there, the start goes on, and the next one does not try again`() {
        val older = temp("older")
        File(older, "notes_data.json").writeText("""[{"id": 1, "title": "Kept"}]""")
        File(older, "settings.json").writeText("""{"username": "Maria"}""")
        val dir = File(temp("phone"), "notebook")
        // The notes read, the settings too large: what was written goes back.
        val tooLarge: (File) -> ByteArray = { f ->
            if (f.name == "settings.json") throw OutOfMemoryError("File $f is too big to fit in memory.") else f.readBytes()
        }
        val notice = Startup.prepare(dir, older, read = tooLarge)
        assertTrue(notice!!, notice.contains("could not be read"))
        val notes = JSONArray(File(dir, "notes_data.json").readText())
        assertEquals("a notebook that starts whole: the welcome note, not half the older one",
            Welcome.TITLE, notes.getJSONObject(0).getString("title"))
        assertTrue("the older copy stays where it was", File(older, "notes_data.json").exists())
        val again = Startup.prepare(dir, older, read = tooLarge)
        assertTrue("the next start does not try again", again == null || !again.contains("could not be read"))
    }

    @Test
    fun `the start empties what outlived the window, and says so`() {
        val dir = temp("phone")
        val old = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Date().time - 40L * 86_400_000))
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "$old"}]""")
        val notice = Startup.prepare(dir, older = null)
        assertTrue(notice!!.contains("removed from the Trash"))
        assertEquals(0, JSONArray(File(dir, "trash.json").readText()).length())
    }

    private fun deletedDaysAgo(days: Int): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Date().time - days * 86_400_000L))

    private fun trashLeft(dir: File) = JSONArray(File(dir, "trash.json").readText()).length()

    @Test
    fun `a window of 90 days keeps a note deleted 45 days ago through every start`() {
        // The owner's question: a release made while such a note waits must
        // not take it. The window is the person's, written as Settings → Trash
        // writes it, read back by the start.
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "${deletedDaysAgo(45)}"}]""")
        File(dir, "settings.json").writeText("""{"trash_retention_days": 90}""")
        Startup.prepare(dir, older = null)
        Startup.prepare(dir, older = null)
        assertEquals(1, trashLeft(dir))
    }

    @Test
    fun `settings that cannot be read purge nothing, at this start and the ones after`() {
        // The window chosen was in the file. Its fresh replacement reads as
        // "never chosen", 30 days: without the mark, the next start would
        // purge what was kept for 90.
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "${deletedDaysAgo(40)}"}]""")
        File(dir, "settings.json").writeText("""{"trash_retention_days": 90, "theme": """)
        val notice = Startup.prepare(dir, older = null)
        assertEquals(1, trashLeft(dir))
        assertTrue(notice, notice!!.contains("Nothing was removed from the Trash"))
        assertEquals(true, JSONObject(File(dir, "settings.json").readText()).opt(Settings.PHONE_TRASH_WINDOW_UNKNOWN))
        Startup.prepare(dir, older = null)
        assertEquals("the start after it, the fresh file readable, still nothing", 1, trashLeft(dir))
        // A window chosen again, as Settings → Trash chooses it: the purge
        // goes by it from the next start.
        val settings = Settings.load(dir)
        Settings.putAll(settings, mapOf(Settings.RETENTION_DAYS to 30, Settings.PHONE_TRASH_WINDOW_UNKNOWN to null))
        Startup.prepare(dir, older = null)
        assertEquals(0, trashLeft(dir))
    }

    @Test
    fun `Android's backup turned off stays off after settings that cannot be read, and says so`() {
        // The answer was in the file. The fresh one written at the same start
        // read as on, and Android's next backup took the notes to the account.
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "settings.json").writeText("""{"phone_google_backup": fal""")
        val notice = Startup.prepare(dir, older = null)
        assertTrue(notice, notice!!.contains("Android's backup of the app is off until you turn it on again"))
        assertEquals(false, Settings.googleBackupOn(Settings.load(dir)))
        Startup.prepare(dir, older = null)
        assertEquals("the start after it, the fresh file readable, still off", false, Settings.googleBackupOn(Settings.load(dir)))
    }

    @Test
    fun `a restore from a backup made while off brings the answer back with it`() {
        // Handed nothing while off, Android kept the copy made while on, and
        // a reinstall put it back with backups on. The backup made while off
        // now holds only the mark, and the restored phone starts on it.
        val restored = File(temp("phone"), "notebook").apply { mkdirs() }
        File(restored, Settings.GOOGLE_BACKUP_OFF_MARK).writeText(Settings.offMark())
        Startup.prepare(restored, older = null)
        assertEquals(false, Settings.googleBackupOn(Settings.load(restored)))
        assertTrue("the mark goes once read", !File(restored, Settings.GOOGLE_BACKUP_OFF_MARK).exists())
        // A notebook whose settings already answer keeps its answer.
        val answered = temp("phone")
        File(answered, "notes_data.json").writeText("[]")
        File(answered, "settings.json").writeText("""{"phone_google_backup": true}""")
        File(answered, Settings.GOOGLE_BACKUP_OFF_MARK).writeText(Settings.offMark())
        Startup.prepare(answered, older = null)
        assertEquals(true, Settings.googleBackupOn(Settings.load(answered)))
        assertTrue(!File(answered, Settings.GOOGLE_BACKUP_OFF_MARK).exists())
        assertEquals(false, JSONObject(Settings.offMark()).get(Settings.PHONE_GOOGLE_BACKUP))
    }

    @Test
    fun `a window no app writes purges nothing`() {
        for (value in listOf("0", "-3", "400", "\"forever\"", "7.5", "true", "null")) {
            val dir = temp("phone")
            File(dir, "notes_data.json").writeText("[]")
            File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "${deletedDaysAgo(40)}"}]""")
            File(dir, "settings.json").writeText("""{"trash_retention_days": $value}""")
            Startup.prepare(dir, older = null)
            assertEquals("a window of $value", 1, trashLeft(dir))
        }
    }

    @Test
    fun `a start that cannot read the notes purges nothing, nor a file they may use`() {
        // The list is empty at such a start, so a file only those notes use
        // would not be spared; the desktop skips that start's purge too.
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("""[{"id": 1, "title": "half""")
        File(dir, "trash.json").writeText("""[{"id": 2, "deleted_at": "${deletedDaysAgo(40)}",
            "attachments": [{"filename": "a.png", "stored_as": "shared.png"}]}]""")
        File(dir, "attachments").mkdirs()
        File(dir, "attachments/shared.png").writeText("A")
        Startup.prepare(dir, older = null)
        assertEquals(1, trashLeft(dir))
        assertTrue(File(dir, "attachments/shared.png").exists())
    }

    @Test
    fun `a window never chosen is the default, as the desktop reads it`() {
        val dir = temp("phone")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "${deletedDaysAgo(40)}"},
            {"id": 2, "deleted_at": "${deletedDaysAgo(20)}"}]""")
        File(dir, "settings.json").writeText("""{"theme": "dark"}""")
        Startup.prepare(dir, older = null)
        assertEquals(1, trashLeft(dir))
    }
}
