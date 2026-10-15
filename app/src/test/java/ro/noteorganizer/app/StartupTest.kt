package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun `the start empties what outlived the window, and says so`() {
        val dir = temp("phone")
        val old = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Date().time - 40L * 86_400_000))
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "trash.json").writeText("""[{"id": 1, "deleted_at": "$old"}]""")
        val notice = Startup.prepare(dir, older = null)
        assertTrue(notice!!.contains("removed from the Trash"))
        assertEquals(0, JSONArray(File(dir, "trash.json").readText()).length())
    }
}
