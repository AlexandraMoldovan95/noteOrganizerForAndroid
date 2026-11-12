package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files as NioFiles
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The backup file, held to the desktop's: the same names inside, the same
 * guards on the way in, and a backup that is only kept when it would
 * import back.
 */
class BackupTest {

    private fun temp(prefix: String) = NioFiles.createTempDirectory(prefix).toFile()

    private fun zipOf(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `a size reads as the desktop's size_text, halves to the even figure`() {
        // The desktop's own answers (core/sharing.py's size_text, 2026-11-12).
        val desktop = mapOf(2_500_000L to "2 MB", 3_500_000L to "4 MB", 400_000L to "1 MB",
            1_250_000_000L to "1.2 GB", 1_350_000_000L to "1.4 GB", 1_150_000_000L to "1.1 GB",
            999_999_999L to "1000 MB")
        for ((bytes, said) in desktop) assertEquals("$bytes", said, Backup.sizeText(bytes))
    }

    @Test
    fun `an attached file named longer than the phone's storage holds comes in under a name it holds`() {
        // 161 UTF-16 units, which Windows takes, and 279 UTF-8 bytes, which
        // Android's storage refuses: the whole backup was refused for it,
        // with an errno and an inner path for a message.
        val long = "20261028093000123456_" + "Договор_аренды_квартиры_".repeat(5) + "подписанный.pdf"
        assertTrue(long.toByteArray().size > 255)
        val zip = File(temp("zip"), "b.zip")
        val notes = """[{"id": 1, "uid": "a", "title": "Lease", "attachments": [{"filename": "Договор.pdf", "stored_as": "$long"}]},
            {"id": 2, "uid": "b", "title": "Second"}]"""
        zipOf(zip, "notes_data.json" to notes, "attachments/$long" to "pdf bytes",
              "trash.json" to """[{"id": 3, "title": "Gone", "attachments": [{"filename": "x.pdf", "stored_as": "$long"}]}]""")
        val read = Backup.read(zip, temp("into"), free = Long.MAX_VALUE) as Backup.Read.Ok
        assertEquals(listOf("Lease", "Second"), read.contents.notes.map { it.getString("title") })
        val stored = read.contents.notes[0].getJSONArray("attachments").getJSONObject(0).getString("stored_as")
        assertTrue(stored, stored.toByteArray().size <= Backup.MAX_NAME_BYTES && stored.endsWith(".pdf"))
        assertTrue(stored.startsWith("20261028093000123456_Договор"))
        assertEquals("pdf bytes", File(read.contents.attachments, stored).readText())
        assertEquals(stored, read.contents.trash!!.getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("stored_as"))
        // A file attached on the phone, its picked name long, fits once stamped too.
        val picked = Organizing.storedName("Договор_аренды_".repeat(15) + ".pdf")
        assertTrue(picked.toByteArray().size <= Backup.MAX_NAME_BYTES && picked.endsWith(".pdf"))
        // A name that fits is left alone.
        assertEquals("attachments/20261028_a.pdf", Backup.fittedPlace("attachments/20261028_a.pdf"))
    }

    @Test
    fun `an export holds the desktop's names, and reads back whole`() {
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "a", "title": "Hello"}]""")
        File(dir, "settings.json").writeText("""{"categories": {"General": "#7c5cff", "Trips": "#333333"}}""")
        File(dir, "templates.json").writeText("""[{"name": "Weekly"}]""")
        File(dir, "trash.json").writeText("""[{"id": 2, "title": "Gone", "deleted_at": "2026-10-01 10:00:00"}]""")
        File(dir, "attachments").mkdirs()
        File(dir, "attachments/p.png").writeText("PHOTO")

        val bytes = ByteArrayOutputStream()
        val count = Backup.export(dir, temp("scratch"), bytes)
        assertEquals(5, count)
        val zip = File(temp("out"), "backup.zip")
        zip.writeBytes(bytes.toByteArray())
        ZipFile(zip).use { archive ->
            assertEquals(setOf("notes_data.json", "settings.json", "templates.json", "trash.json",
                               "attachments/p.png"),
                archive.entries().toList().map { it.name }.toSet())
        }

        val read = Backup.read(zip, temp("unpacked"), free = Long.MAX_VALUE) as Backup.Read.Ok
        assertEquals("Hello", read.contents.notes.single().getString("title"))
        assertEquals("Weekly", read.contents.templates!!.getJSONObject(0).getString("name"))
        assertEquals("Gone", read.contents.trash!!.getJSONObject(0).getString("title"))
        assertEquals("#333333", read.contents.extras().categories!!.getString("Trips"))
        assertEquals("PHOTO", File(read.contents.attachments, "p.png").readText())
    }

    @Test
    fun `a backup made the desktop's way is read`() {
        // Python's zipfile writes "attachments/name" with a forward slash
        // even on Windows; a backslash is read the same way regardless.
        val zip = File(temp("in"), "NotesBackup_2026-10-15.zip")
        zipOf(zip, "notes_data.json" to """[{"id": 1, "title": "From the PC"}]""",
              "attachments\\doc.pdf" to "PDF")
        val read = Backup.read(zip, temp("unpacked"), free = Long.MAX_VALUE) as Backup.Read.Ok
        assertEquals("From the PC", read.contents.notes.single().getString("title"))
        assertEquals("PDF", File(read.contents.attachments, "doc.pdf").readText())
        assertNull("an older backup has no Trash", read.contents.trash)
    }

    @Test
    fun `only the names a backup is made of are ever unpacked`() {
        assertEquals("notes_data.json", Backup.placeOf("notes_data.json"))
        assertEquals("attachments/a.png", Backup.placeOf("attachments/a.png"))
        assertEquals("attachments/a.png", Backup.placeOf("attachments\\a.png"))
        for (bad in listOf("../notes_data.json", "/etc/passwd", "attachments/../../x",
                           "attachments/sub/a.png", "attachments/..", "attachments/",
                           "attachments/a.png. ", "other.json", "C:\\Windows\\x.ini")) {
            assertNull(bad, Backup.placeOf(bad))
        }
    }

    @Test
    fun `an entry that tries to leave the folder is not written anywhere`() {
        val zip = File(temp("in"), "hostile.zip")
        zipOf(zip, "notes_data.json" to "[]", "../escaped.txt" to "x",
              "attachments/../../escaped2.txt" to "y")
        val into = temp("unpacked")
        assertTrue(Backup.read(zip, into, free = Long.MAX_VALUE) is Backup.Read.Ok)
        assertTrue(!File(into.parentFile, "escaped.txt").exists())
        assertTrue(into.walkTopDown().none { it.name.startsWith("escaped") })
    }

    @Test
    fun `an entry that unpacks to more than it declares is stopped at what it declared`() {
        // The guards above read the sizes the archive CLAIMS; a hostile one can
        // claim a small size for a large entry, so the unpacking counts too. An
        // honest zip, then the size its central directory declares lowered.
        val zip = File(temp("in"), "liar.zip")
        zipOf(zip, "notes_data.json" to "[]", "attachments/big.txt" to "x".repeat(10_000))
        val bytes = zip.readBytes()
        val name = "attachments/big.txt".toByteArray()
        var patched = false
        for (i in 0 until bytes.size - 46) {
            val central = bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x01.toByte() && bytes[i + 3] == 0x02.toByte()
            if (!central) continue
            val nameLength = (bytes[i + 28].toInt() and 0xff) or ((bytes[i + 29].toInt() and 0xff) shl 8)
            if (nameLength == name.size && bytes.copyOfRange(i + 46, i + 46 + nameLength).contentEquals(name)) {
                // Uncompressed size, 4 bytes little-endian at offset 24: say 10.
                bytes[i + 24] = 10; bytes[i + 25] = 0; bytes[i + 26] = 0; bytes[i + 27] = 0
                patched = true
            }
        }
        assertTrue("the central directory entry was found", patched)
        zip.writeBytes(bytes)
        val into = temp("unpacked")
        val result = Backup.read(zip, into, free = Long.MAX_VALUE)
        assertTrue(result is Backup.Read.Failed)
        val written = File(into, "attachments/big.txt")
        assertTrue("never more than it declared", !written.exists() || written.length() <= 10)
    }

    @Test
    fun `too many files, or a bomb, is refused before anything is unpacked`() {
        val many = List(Backup.MAX_BACKUP_FILES + 1) { Backup.Entry("attachments/$it", 1, 1) }
        assertTrue(Backup.entriesProblem(many)!!.contains("more than an import takes"))
        val bomb = listOf(Backup.Entry("attachments/x", 60_000_000, 1_000))
        assertTrue(Backup.entriesProblem(bomb)!!.contains("hostile"))
        // A thousand entries each under the line pass one by one -- the
        // backup as a whole does not.
        val spread = List(1000) { Backup.Entry("attachments/$it", 100_000, 10) }
        assertTrue(Backup.entriesProblem(spread)!!.startsWith("The backup would expand"))
        val photos = List(300) { Backup.Entry("attachments/$it.jpg", 4_000_000, 3_900_000) }
        assertNull("a few hundred photos are a real backup", Backup.entriesProblem(photos))
    }

    @Test
    fun `room is asked for before unpacking, the attached files counted twice`() {
        val entries = listOf(Backup.Entry("notes_data.json", 1_000_000, 100_000),
                             Backup.Entry("attachments/v.mp4", 500_000_000, 490_000_000))
        // 1 MB + 500 MB unpacked + 500 MB copied + 200 MB margin.
        assertNull(Backup.spaceProblem(entries, free = 1_300_000_000))
        assertTrue(Backup.spaceProblem(entries, free = 1_100_000_000)!!.contains("free space"))
    }

    @Test
    fun `a file that is not a backup says so`() {
        val notZip = File(temp("in"), "photo.zip").apply { writeText("not a zip at all") }
        assertTrue((Backup.read(notZip, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed)
            .reason.contains("couldn't be read as a backup"))
        val noNotes = File(temp("in"), "other.zip")
        zipOf(noNotes, "readme.txt" to "hi")
        assertTrue((Backup.read(noNotes, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed)
            .reason.contains("doesn't look like a Note Organizer backup"))
        val badNotes = File(temp("in"), "bad.zip")
        zipOf(badNotes, "notes_data.json" to "{ broken")
        assertTrue((Backup.read(badNotes, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed)
            .reason.contains("Couldn't read the notes"))
    }

    @Test
    fun `notes that do not parse are said in words of our own, never the parser's`() {
        // Android's org.json puts the whole text it read into its message, and a
        // notebook missing its last bracket put every note into the bar at the
        // foot. The computer's parser does not, so the sentence is held whole.
        val zip = File(temp("in"), "cut.zip")
        zipOf(zip, "notes_data.json" to """[{"id": 1, "title": "Bank", "body": "Card PIN 4821"}""")
        val reason = (Backup.read(zip, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed).reason
        assertEquals("Couldn't read the notes in that backup. The file is damaged.", reason)
        val deep = File(temp("in"), "deep.zip")
        zipOf(deep, "notes_data.json" to "[".repeat(150) + "]".repeat(150))
        assertEquals("Couldn't read the notes in that backup. It is nested deeper than any note.",
            (Backup.read(deep, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed).reason)
    }

    @Test
    fun `a backup's JSON file larger than the phone can read is refused before it is unpacked`() {
        // 50 MB of JSON packed into 48 KB passed the bomb guard's 50 MB
        // floor -- a computer's number -- and ran the phone out of memory.
        // Here the phone is given 16,000 bytes: a ceiling of 1,000.
        val heap = 16_000L
        val big = File(temp("in"), "big.zip")
        zipOf(big, "notes_data.json" to """[{"title": "${"é".repeat(3000)}"}]""")
        val failed = Backup.read(big, temp("u"), Long.MAX_VALUE, heap = heap) as Backup.Read.Failed
        assertTrue(failed.reason, failed.reason.contains("more than this phone can read"))
        // An attached file is copied, never read whole into memory: only
        // the JSON is held to the ceiling.
        val photo = File(temp("in"), "photo.zip")
        zipOf(photo, "notes_data.json" to "[]", "attachments/photo.jpg" to "x".repeat(5000))
        assertTrue(Backup.read(photo, temp("u"), Long.MAX_VALUE, heap = heap) is Backup.Read.Ok)
    }

    @Test
    fun `a parser out of memory reads as too large, and the app goes on`() {
        try {
            Files.parseOutside("[1]") { throw OutOfMemoryError() }
            fail("a parser out of memory got past")
        } catch (e: Files.Unreadable) {
            assertEquals(Files.TOO_LARGE, e.message)
        }
    }

    // Nested 150 deep: the computer's parser takes that (it stops at 512 by
    // itself), Android's went one call deeper per bracket until the app
    // stopped, and only the guard refuses it here.
    private fun nested(depth: Int) = "[".repeat(depth) + "]".repeat(depth)

    @Test
    fun `a backup whose files nest deeper than any note is read as damaged, never parsed`() {
        val deepNotes = File(temp("in"), "deep.zip")
        zipOf(deepNotes, "notes_data.json" to """[{"title": "x", "deep": ${nested(150)}}]""")
        assertTrue((Backup.read(deepNotes, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed)
            .reason.contains("Couldn't read the notes"))
        // The files that ride along are skipped, as any bad one is.
        val deepRest = File(temp("in"), "rest.zip")
        zipOf(deepRest, "notes_data.json" to """[{"title": "x"}]""",
              "trash.json" to nested(150), "templates.json" to nested(150),
              "settings.json" to """{"deep": ${nested(150)}}""")
        val contents = (Backup.read(deepRest, temp("u"), Long.MAX_VALUE) as Backup.Read.Ok).contents
        assertEquals(1, contents.notes.size)
        assertNull(contents.trash)
        assertNull(contents.templates)
        assertNull(contents.settings)
    }

    @Test
    fun `the app's own files nested deeper than any note are kept aside, never parsed`() {
        // Read with a bare catch for exceptions, Android's StackOverflowError
        // went past it and the app stopped at every start.
        val dir = temp("own")
        File(dir, "notes_data.json").writeText("""[{"title": "x", "deep": ${nested(150)}}]""")
        File(dir, "settings.json").writeText("""{"deep": ${nested(150)}}""")
        File(dir, "templates.json").writeText(nested(150))
        val load = Notebook.load(dir)
        assertTrue(load.problem ?: "no problem said", load.problem!!.contains("could not be read"))
        assertTrue(Settings.load(dir).problem != null)
        assertTrue(Templates.load(dir).isEmpty())
        for (name in listOf("notes_data", "settings", "templates")) {
            assertTrue("$name kept aside", dir.listFiles()!!.any { it.name.startsWith(name + "_corrupt_") })
        }
    }

    @Test
    fun `a file not in UTF-8 is damage, never letters turned into marks`() {
        // A notes file in Windows' Romanian code page came in as replacement marks,
        // and the next save wrote them over the words; the desktop calls it damage.
        // An S with a comma below and an a with a breve: one byte each, not UTF-8.
        val title = Char(0x0218) + "tampil" + Char(0x0103)
        val romanian = "[{\"id\": 1, \"uid\": \"r\", \"title\": \"$title\"}]".toByteArray(charset("windows-1250"))
        val dir = temp("own")
        File(dir, "notes_data.json").writeBytes(romanian)
        val load = Notebook.load(dir)
        assertTrue(load.problem ?: "no problem said", load.problem!!.contains("could not be read"))
        val kept = dir.listFiles()!!.single { it.name.startsWith("notes_data_corrupt_") }
        assertTrue("the bytes kept as they were", kept.readBytes().contentEquals(romanian))
        // In a backup, too.
        val zip = File(temp("in"), "cp1250.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("notes_data.json"))
            out.write(romanian)
            out.closeEntry()
        }
        assertTrue((Backup.read(zip, temp("u"), Long.MAX_VALUE) as Backup.Read.Failed).reason.contains("UTF-8"))
    }

    @Test
    fun `nesting is measured outside strings, and a parser out of stack is damage`() {
        assertTrue(Files.nestedPast(nested(101), 100))
        assertFalse(Files.nestedPast(nested(100), 100))
        // An escaped quote, then brackets, inside a string: words, not nesting.
        val words = "[\"\\\"" + "[".repeat(500) + "\"]"
        assertEquals(1, Files.parseOutside(words) { JSONArray(it) }.length())
        try {
            Files.parseOutside("[1]") { throw StackOverflowError() }
            fail("a parser that ran out of stack got past")
        } catch (e: Files.Unreadable) {
            assertEquals("It is nested deeper than any note.", e.message)
        }
    }

    @Test
    fun `an export that would not import back is never handed over`() {
        // 60 MB of zeros packs to almost nothing -- past the bomb line the
        // import holds every backup to. The desktop refuses to keep such a
        // backup, and so does this: better told now than at the restore.
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "attachments").mkdirs()
        File(dir, "attachments/blank.bmp").outputStream().use { out ->
            val zeros = ByteArray(1 shl 20)
            repeat(60) { out.write(zeros) }
        }
        val scratch = temp("scratch")
        val out = ByteArrayOutputStream()
        val refused = try {
            Backup.export(dir, scratch, out)
            null
        } catch (e: java.io.IOException) {
            e.message
        }
        assertTrue(refused!!.contains("would not import back"))
        assertEquals(0, out.size())
        assertEquals("the scratch copy is gone too", 0, scratch.listFiles()!!.size)
    }

    @Test
    fun `a file kept aside as unreadable travels in the backup, and an import leaves it be`() {
        // Kept in the app's own storage, where no person can reach it: the
        // notice said "beside the notes", and the backup left it out.
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[]")
        File(dir, "notes_data_corrupt_20261015_143000.json").writeText("[{broken")
        File(dir, "notes_data_backup_x.json").writeText("not ours to carry")
        val bytes = ByteArrayOutputStream()
        Backup.export(dir, temp("scratch"), bytes)
        val zip = File(temp("out"), "b.zip").apply { writeBytes(bytes.toByteArray()) }
        ZipFile(zip).use { archive ->
            assertEquals(setOf("notes_data.json", "unreadable/notes_data_corrupt_20261015_143000.json"),
                         archive.entries().toList().map { it.name }.toSet())
            assertEquals("[{broken", archive.getInputStream(archive.getEntry(
                "unreadable/notes_data_corrupt_20261015_143000.json")).readBytes().toString(Charsets.UTF_8))
        }
        val into = temp("u")
        assertTrue(Backup.read(zip, into, Long.MAX_VALUE) is Backup.Read.Ok)
        assertFalse("an import does not unpack it", File(into, "unreadable").exists())
    }

    @Test
    fun `an export opens where it goes only once the backup is built`() {
        // Opened first, for writing, a backup chosen to be written over was
        // emptied before a full cache stopped the new one, then deleted as
        // half-written: no backup at all. Here the cache cannot hold it.
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[]")
        val scratch = temp("scratch")
        File(scratch, "backup_export.zip.tmp/hold").apply { parentFile!!.mkdirs(); writeText("x") }
        var opened = false
        try {
            Backup.export(dir, scratch, { opened = true; ByteArrayOutputStream() })
            fail("an export the cache could not hold went through")
        } catch (e: java.io.IOException) {
            // expected
        }
        assertFalse("the place it goes is never opened", opened)
        // Built, it is opened once and written.
        val out = ByteArrayOutputStream()
        assertEquals(1, Backup.export(dir, temp("scratch"), { opened = true; out }))
        assertTrue(opened && out.size() > 0)
    }

    @Test
    fun `a backup chosen to be replaced is kept when its place refuses to open`() {
        // The flag said "opened" before the open was tried: a place refusing
        // "wt" deleted the older backup the person chose, with nothing new.
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[]")
        var discarded = 0
        val export = { open: () -> java.io.OutputStream?, empty: Boolean ->
            Backup.exportTo(dir, temp("scratch"), open, emptyPlace = { empty }, discard = { discarded += 1 })
        }
        assertTrue(export({ throw java.io.FileNotFoundException("Unsupported mode: wt") }, false) is Backup.Export.Failed)
        assertTrue(export({ null }, false) is Backup.Export.Failed)
        assertEquals("a refusal deletes nothing", 0, discarded)
        // The picker's own empty file goes, as before; so does a half-written one.
        export({ null }, true)
        assertEquals(1, discarded)
        val failing = object : java.io.OutputStream() {
            override fun write(b: Int) = throw java.io.IOException("disk full")
        }
        assertTrue(export({ failing }, false) is Backup.Export.Failed)
        assertEquals("a backup half-written over the older one goes", 2, discarded)
        val out = ByteArrayOutputStream()
        assertEquals(Backup.Export.Saved(1, null), export({ out }, false))
        assertEquals(2, discarded)
    }

    @Test
    fun `a backup this phone could not read back is still saved, and says so`() {
        // "Backup saved." -- then the import refused it: the notes were past
        // what the import reads at once on a phone with this memory.
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[" + "{\"body\": \"${"x".repeat(1000)}\"},".repeat(200) + "{}]")
        val heap = File(dir, "notes_data.json").length() * 16 - 16
        val out = ByteArrayOutputStream()
        val saved = Backup.exportTo(dir, temp("scratch"), { out }, { false }, {}, heap = heap) as Backup.Export.Saved
        assertTrue("written all the same", out.size() > 0)
        assertTrue(saved.tooLarge, saved.tooLarge!!.startsWith("notes_data.json holds"))
        // And the import, on that phone, refuses it for the same reason.
        val zip = File(temp("zip"), "b.zip").apply { writeBytes(out.toByteArray()) }
        val read = Backup.read(zip, temp("into"), free = Long.MAX_VALUE, heap = heap)
        assertTrue(read is Backup.Read.Failed && read.reason.contains(saved.tooLarge!!))
        // A phone with room for it is told nothing.
        assertNull((Backup.exportTo(dir, temp("scratch"), { ByteArrayOutputStream() }, { false }, {},
            heap = heap + 16) as Backup.Export.Saved).tooLarge)
    }

    @Test
    fun `an export whose place will not open leaves nothing behind`() {
        val dir = temp("notebook")
        File(dir, "notes_data.json").writeText("[]")
        val scratch = temp("scratch")
        try {
            Backup.export(dir, scratch, { null })
            fail("an export with nowhere to go went through")
        } catch (e: java.io.IOException) {
            assertEquals("The file could not be opened for writing.", e.message)
        }
        assertEquals("the scratch copy is gone", 0, scratch.listFiles()!!.size)
    }

    @Test
    fun `a backup round trip through the importer restores a notebook`() {
        // The whole route: export from one notebook, read, plan and apply
        // into an empty one, as a new phone restoring the desktop's backup.
        val from = temp("from")
        File(from, "notes_data.json").writeText("""
            [{"id": 1, "uid": "a", "title": "Trip", "category": "Trips",
              "attachments": [{"filename": "map.png", "stored_as": "map.png"}]}]
        """.trimIndent())
        File(from, "settings.json").writeText("""{"categories": {"General": "#7c5cff", "Trips": "#333333"}}""")
        File(from, "attachments").mkdirs()
        File(from, "attachments/map.png").writeText("MAP")
        val bytes = ByteArrayOutputStream()
        Backup.export(from, temp("scratch"), bytes)
        val zip = File(temp("zip"), "b.zip").apply { writeBytes(bytes.toByteArray()) }

        val to = temp("to")
        File(to, "attachments").mkdirs()
        val load = Notebook.load(to)
        val contents = (Backup.read(zip, temp("unpacked"), Long.MAX_VALUE) as Backup.Read.Ok).contents
        val plan = Importing.plan(load.raw!!, contents.notes, Importing.Source.BACKUP)
        val result = Importing.apply(load, plan, Importing.Answers(), File(to, "attachments"),
            contents.extras()) as Importing.Result.Done
        assertEquals(1, result.outcome.added)
        val note = JSONArray(File(to, "notes_data.json").readText()).getJSONObject(0)
        assertEquals("Trips", note.getString("category"))
        assertEquals("MAP", File(to, "attachments/map.png").readText())
        assertEquals("#333333", JSONObject(File(to, "settings.json").readText())
            .getJSONObject("categories").getString("Trips"))
    }
}
