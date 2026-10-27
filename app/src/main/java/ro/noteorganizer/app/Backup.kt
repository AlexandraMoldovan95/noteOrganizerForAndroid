package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The full backup: one `.zip` holding the whole notebook, the same file
 * the desktop's Settings → Backup writes and reads -- so a backup made on
 * one device restores on the other.
 *
 * Inside: `notes_data.json`, `settings.json`, `templates.json`,
 * `trash.json`, and every attached file under `attachments/`. A backup
 * the app wrote must be one it agrees to read back, so an export is
 * checked by the import's own rules before it is handed over.
 *
 * A backup arrives from anywhere, so reading one is guarded the way the
 * desktop's `_safe_extract_zip` guards it -- and one step stricter: only
 * the names a backup is made of are unpacked at all. Anything else in the
 * archive is left where it is, so no entry can name a path outside the
 * folder it is unpacked into.
 */
object Backup {

    const val NOTES = "notes_data.json"
    const val SETTINGS = "settings.json"
    const val TEMPLATES = "templates.json"
    const val TRASH = "trash.json"
    const val ATTACHMENTS = "attachments"

    /** The desktop's limits: what a HOSTILE archive could do, not a size cap. */
    const val MAX_BACKUP_FILES = 100_000
    const val BOMB_RATIO = 100L
    const val BOMB_MIN_BYTES = 50_000_000L
    const val DISK_MARGIN = 200_000_000L

    /** One entry as the guards see it. */
    data class Entry(val name: String, val size: Long, val packed: Long)

    /** A byte count as people read it: 350 MB, 1.2 GB. The desktop's size_text. */
    fun sizeText(n: Long): String =
        if (n >= 1_000_000_000) "%.1f GB".format(java.util.Locale.US, n / 1e9)
        else "%d MB".format(maxOf(1L, Math.round(n / 1e6)))

    /**
     * Why these entries cannot be imported, whatever room there is -- or
     * null. Asked before unpacking, and of every export before it is kept.
     */
    fun entriesProblem(entries: List<Entry>): String? {
        if (entries.size > MAX_BACKUP_FILES) {
            return "This backup holds ${entries.size} files, more than an import takes ($MAX_BACKUP_FILES)."
        }
        val all = entries.map { Triple(it.name, maxOf(it.size, 0), maxOf(it.packed, 0)) } +
            Triple("The backup", entries.sumOf { maxOf(it.size, 0) }, entries.sumOf { maxOf(it.packed, 0) })
        for ((name, size, packed) in all) {
            if (size > BOMB_MIN_BYTES && size > BOMB_RATIO * maxOf(packed, 1)) {
                return "$name would expand to ${sizeText(size)} from ${sizeText(packed)} packed, " +
                    "which is how a damaged or hostile backup looks."
            }
        }
        return null
    }

    /**
     * Why there is not room to unpack these entries and keep their files,
     * or null. On a phone the unpacking and the notebook share one disk,
     * so the attached files are counted twice, as the desktop counts them
     * when its two folders share one.
     */
    fun spaceProblem(entries: List<Entry>, free: Long): String? {
        val total = entries.sumOf { maxOf(it.size, 0) }
        val attached = entries.filter { it.name.replace('\\', '/').startsWith("$ATTACHMENTS/") }
            .sumOf { maxOf(it.size, 0) }
        val need = total + attached
        if (need + DISK_MARGIN > free) {
            return "There isn't enough free space to import this backup: it needs about " +
                "${sizeText(need + DISK_MARGIN)}, and ${sizeText(free)} is free. Free up some " +
                "space and try again."
        }
        return null
    }

    /**
     * Where an entry may go when unpacked, as a path relative to the folder
     * it is unpacked into -- or null when it is not part of a backup. The
     * four data files by their exact names, and attached files only as
     * `attachments/<bare name>`. "../x", an absolute path and a nested
     * folder are all refused here, before any byte is written.
     */
    fun placeOf(entryName: String): String? {
        val name = entryName.replace('\\', '/')
        if (name in setOf(NOTES, SETTINGS, TEMPLATES, TRASH)) return name
        if (!name.startsWith("$ATTACHMENTS/")) return null
        val rest = name.removePrefix("$ATTACHMENTS/")
        if (rest == "." || rest == "..") return null
        // A bare name is its own bare name; anything with a folder in it,
        // or a trailing dot Windows would ignore, is not.
        val bare = Normalize.bareFileName(rest)
        if (bare != rest || bare.isEmpty()) return null
        return "$ATTACHMENTS/$bare"
    }

    /** Stopped part-way, by the person or by the app closing. */
    class Stopped : IOException("The backup was stopped before it was finished.")

    /**
     * Writes the notebook in [dir] as a backup to [out]. Built in [scratch]
     * first and checked there by the import's own rules, so a backup that
     * could not be restored is never handed over; [stop] is asked between
     * files. Returns how many files went in.
     */
    fun export(dir: File, scratch: File, out: OutputStream, stop: () -> Boolean = { false },
               progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val entries = ArrayList<Pair<File, String>>()
        for (name in listOf(NOTES, SETTINGS, TEMPLATES, TRASH)) {
            val file = File(dir, name)
            if (file.isFile) entries.add(file to name)
        }
        File(dir, ATTACHMENTS).listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach {
            entries.add(it to "$ATTACHMENTS/${it.name}")
        }
        val tmp = File(scratch, "backup_export.zip.tmp")
        try {
            ZipOutputStream(FileOutputStream(tmp)).use { zip ->
                for ((i, pair) in entries.withIndex()) {
                    if (stop()) throw Stopped()
                    val (file, name) = pair
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { copyStopping(it, zip, stop) }
                    zip.closeEntry()
                    progress(i + 1, entries.size)
                }
            }
            ZipFile(tmp).use { written ->
                entriesProblem(entriesOf(written))?.let {
                    throw IOException("This backup would not import back. $it")
                }
            }
            tmp.inputStream().use { copyStopping(it, out, stop) }
            out.flush()
            return entries.size
        } finally {
            tmp.delete()
        }
    }

    /** What a backup held, once unpacked. */
    data class Contents(
        val notes: List<JSONObject>,
        val trash: JSONArray?,
        val templates: JSONArray?,
        val settings: JSONObject?,
        /** The unpacked attached files, or null when it carried none. */
        val attachments: File?,
        /** Where it was unpacked: removed once the import is over. */
        val folder: File? = null,
    ) {
        fun extras(): Importing.Extras = Importing.Extras(
            categories = settings?.opt(Settings.CATEGORIES) as? JSONObject,
            dayColors = settings?.opt("day_colors") as? JSONObject,
            dayEmoji = settings?.opt("day_emoji") as? JSONObject,
            trashWindow = settings?.let { Settings.chosenWindow(it) },
            templates = templates,
            trash = trash,
            attachments = attachments,
        )
    }

    sealed class Read {
        data class Ok(val contents: Contents) : Read()
        data class Failed(val reason: String) : Read()
    }

    /**
     * Reads the backup [zip] into the empty folder [into]: guarded, then
     * unpacked, then its files parsed. [free] is the space left on the disk
     * it unpacks to. A failure says what is wrong in words for a person.
     */
    fun read(zip: File, into: File, free: Long, stop: () -> Boolean = { false }): Read {
        val file = try {
            ZipFile(zip)
        } catch (e: Exception) {
            return Read.Failed("That file couldn't be read as a backup:\n${e.message ?: e}")
        }
        file.use { archive ->
            val entries = entriesOf(archive)
            (entriesProblem(entries) ?: spaceProblem(entries, free))?.let {
                return Read.Failed("That file couldn't be read as a backup:\n$it")
            }
            if (archive.getEntry(NOTES) == null) {
                return Read.Failed("This .zip doesn't look like a Note Organizer backup.")
            }
            try {
                for (entry in archive.entries()) {
                    if (entry.isDirectory) continue
                    val place = placeOf(entry.name) ?: continue
                    val target = File(into, place)
                    target.parentFile?.mkdirs()
                    // The sizes the guards read are the archive's own claim.
                    // Counted here too, so an entry that unpacks to more
                    // than it declared is stopped at what it declared.
                    val limit = if (entry.size >= 0) entry.size else BOMB_MIN_BYTES
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { copyStopping(input, it, stop, limit) }
                    }
                }
            } catch (e: Stopped) {
                return Read.Failed(e.message ?: "Stopped.")
            } catch (e: Exception) {
                return Read.Failed("That file couldn't be read as a backup:\n${e.message ?: e}")
            }
        }
        val notes = try {
            JSONArray(File(into, NOTES).readText(Charsets.UTF_8))
        } catch (e: Exception) {
            return Read.Failed("Couldn't read the notes in that backup:\n${e.message ?: e}")
        }
        // Templates, the Trash and the settings ride along when the backup
        // has them; older backups do not, and a bad one is skipped quietly
        // -- the notes are the payload.
        fun optionalArray(name: String): JSONArray? = try {
            File(into, name).takeIf { it.isFile }?.let { JSONArray(it.readText(Charsets.UTF_8)) }
        } catch (e: Exception) {
            null
        }
        val settings = try {
            File(into, SETTINGS).takeIf { it.isFile }?.let { JSONObject(it.readText(Charsets.UTF_8)) }
        } catch (e: Exception) {
            null
        }
        val attachments = File(into, ATTACHMENTS).takeIf { it.isDirectory }
        return Read.Ok(Contents(
            notes = (0 until notes.length()).mapNotNull { notes.opt(it) as? JSONObject },
            trash = optionalArray(TRASH),
            templates = optionalArray(TEMPLATES),
            settings = settings,
            attachments = attachments,
            folder = into,
        ))
    }

    private fun entriesOf(archive: ZipFile): List<Entry> =
        archive.entries().toList().filter { !it.isDirectory }
            .map { Entry(it.name, it.size, it.compressedSize) }

    private fun copyStopping(
        input: InputStream, output: OutputStream, stop: () -> Boolean, limit: Long = Long.MAX_VALUE,
    ) {
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            if (stop()) throw Stopped()
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) {
                throw IOException("An entry unpacks to more than it says it holds, which is how " +
                    "a damaged or hostile backup looks.")
            }
            output.write(buffer, 0, n)
        }
    }
}
