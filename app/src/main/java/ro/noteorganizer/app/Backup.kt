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
 * checked by the import's own rules before it is handed over -- all but
 * the memory one, which depends on the phone: a notebook grown past what
 * this phone's import reads at once is still saved, the person told so
 * ([exportTo]), since the desktop restores it.
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

    /** Where a backup carries the files kept aside because they could not be read. */
    const val UNREADABLE = "unreadable"
    private val KEPT_ASIDE = Regex("(notes_data|settings|trash|templates)_corrupt_\\d{8}_\\d{6}\\.json")

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
                // An entry's name is the backup's to write, up to 65,535 bytes.
                return "${quoted(name)} would expand to ${sizeText(size)} from ${sizeText(packed)} packed, " +
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
     * files. Returns how many files went in; [out] is closed once written.
     */
    fun export(dir: File, scratch: File, out: OutputStream, stop: () -> Boolean = { false },
               progress: (Int, Int) -> Unit = { _, _ -> }): Int =
        export(dir, scratch, { out }, stop, progress)

    /**
     * The same, the place it goes to [open]ed only once the backup is built
     * and checked. Opened first -- for writing, which empties it -- a backup
     * chosen to be written over was emptied before a full cache could stop
     * the new one, and then deleted as half-written: no backup at all.
     */
    fun export(dir: File, scratch: File, open: () -> OutputStream?, stop: () -> Boolean = { false },
               progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val entries = ArrayList<Pair<File, String>>()
        for (name in listOf(NOTES, SETTINGS, TEMPLATES, TRASH)) {
            val file = File(dir, name)
            if (file.isFile) entries.add(file to name)
        }
        File(dir, ATTACHMENTS).listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach {
            entries.add(it to "$ATTACHMENTS/${it.name}")
        }
        // A file the app could not read, kept aside (Files.preserveCorrupt):
        // in the app's own storage no person can reach it, and a notebook
        // gone unreadable had nowhere else to be. It travels in the backup,
        // under a folder of its own that an import, here or on the desktop,
        // leaves where it is.
        dir.listFiles()?.filter { it.isFile && KEPT_ASIDE.matches(it.name) }?.sortedBy { it.name }?.forEach {
            entries.add(it to "$UNREADABLE/${it.name}")
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
            val out = open() ?: throw IOException("The file could not be opened for writing.")
            out.use { stream ->
                tmp.inputStream().use { copyStopping(it, stream, stop) }
                stream.flush()
            }
            return entries.size
        } finally {
            tmp.delete()
        }
    }

    /** How an export to a place the person chose ended. */
    sealed class Export {
        /**
         * Written. [tooLarge] says why this phone could not read it back at
         * once ([memoryProblem]) -- a notebook that grew past what the import
         * reads, which the desktop can still restore -- or is null.
         */
        data class Saved(val files: Int, val tooLarge: String?) : Export()
        data class Failed(val reason: String) : Export()
    }

    /**
     * [export] to a place the person chose, which may be an older backup
     * to be written over. On failure the place is [discard]ed only when
     * what it holds is a half-written backup -- [open] gave a stream and the
     * writing then failed -- or the empty file the picker made for this one
     * ([emptyPlace]). A place that refused to open, or gave no stream, is
     * as it was: a backup chosen to be replaced was deleted for a refusal,
     * with nothing in its place.
     *
     * A backup this phone could not read back is still written -- the
     * person's only copy is never withheld -- and [Export.Saved.tooLarge]
     * says so, where "Backup saved." was followed, at the import, by a
     * refusal.
     */
    fun exportTo(dir: File, scratch: File, open: () -> OutputStream?, emptyPlace: () -> Boolean,
                 discard: () -> Unit, heap: Long = Runtime.getRuntime().maxMemory(),
                 stop: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> }): Export {
        var opened = false
        return try {
            val files = export(dir, scratch, { open()?.also { opened = true } }, stop, progress)
            val sizes = listOf(NOTES, SETTINGS, TEMPLATES, TRASH).map { File(dir, it) }.filter { it.isFile }
                .map { Entry(it.name, it.length(), it.length()) }
            Export.Saved(files, memoryProblem(sizes, heap))
        } catch (e: Exception) {
            if (opened || emptyPlace()) {
                try {
                    discard()
                } catch (_: Exception) {
                }
            }
            Export.Failed(e.message ?: e.toString())
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

    /** The files of a backup read whole as JSON, which the phone's memory must hold at once. */
    private val JSON_FILES = setOf(NOTES, TRASH, TEMPLATES, SETTINGS)

    /**
     * The most one of a backup's JSON files may hold on a phone that lets
     * the app use [heap] bytes: a sixteenth of it -- 16 MB where an app is
     * given 256. Read, the text takes two bytes a letter, the objects parsed
     * from it several times that, and the notes are copied again on their
     * way in. The bomb guard's floor is the desktop's, 50 MB, sized for a
     * computer: under it, a 48 KB archive unpacked to a 50 MB note, and the
     * phone ran out of memory reading it.
     */
    fun jsonCeiling(heap: Long): Long = heap / 16

    /** Why one of [entries]' JSON files is more than a phone with [heap] can read at once, or null. */
    fun memoryProblem(entries: List<Entry>, heap: Long): String? {
        val ceiling = jsonCeiling(heap)
        val big = entries.firstOrNull { it.name.replace('\\', '/') in JSON_FILES && it.size > ceiling }
            ?: return null
        return "${big.name} holds ${sizeText(big.size)}, more than this phone can read at once " +
            "(${sizeText(ceiling)})."
    }

    /**
     * The longest name an attached file is kept under, in UTF-8 bytes:
     * Android's storage holds 255 (NAME_MAX), less the ".part" a copy is
     * written under before it takes its name.
     */
    const val MAX_NAME_BYTES = 250

    /**
     * Where [place] -- `attachments/<name>` or a data file -- is unpacked:
     * itself, unless its name is longer than this phone's storage holds.
     * Windows counts 255 UTF-16 units to a name, Android 255 bytes, so a
     * desktop file named in Cyrillic or Chinese can be legal there and not
     * here -- and one such name refused the whole backup, with an errno and
     * an inner path for its message. Such a name keeps its type and as much
     * of its start as fits, with a short mark of the whole name so two never
     * meet.
     */
    fun fittedPlace(place: String): String {
        val name = place.substringAfter('/', place)
        if (name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) return place
        val dot = name.lastIndexOf('.')
        val type = if (dot > 0 && name.length - dot <= 16) name.substring(dot) else ""
        val mark = "_%08x".format(name.hashCode())
        var room = MAX_NAME_BYTES - type.toByteArray(Charsets.UTF_8).size - mark.length
        val stem = StringBuilder()
        var i = 0
        val body = name.substring(0, name.length - type.length)
        while (i < body.length) {
            val cp = body.codePointAt(i)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes > room) break
            stem.appendCodePoint(cp)
            room -= bytes
            i += Character.charCount(cp)
        }
        val fitted = stem.toString() + mark + type
        return if (place.contains('/')) place.substringBefore('/') + "/" + fitted else fitted
    }

    /**
     * Reads the backup [zip] into the empty folder [into]: guarded, then
     * unpacked, then its files parsed. [free] is the space left on the disk
     * it unpacks to, [heap] the memory the app may use. A failure says what
     * is wrong in words for a person.
     */
    fun read(zip: File, into: File, free: Long, stop: () -> Boolean = { false },
             heap: Long = Runtime.getRuntime().maxMemory()): Read {
        // Attached files unpacked under a shorter name: the name as stored -> the one here.
        val renamed = HashMap<String, String>()
        val file = try {
            ZipFile(zip)
        } catch (e: Exception) {
            return Read.Failed("That file couldn't be read as a backup:\n${e.message ?: e}")
        }
        file.use { archive ->
            val entries = entriesOf(archive)
            (entriesProblem(entries) ?: spaceProblem(entries, free) ?: memoryProblem(entries, heap))?.let {
                return Read.Failed("That file couldn't be read as a backup:\n$it")
            }
            if (archive.getEntry(NOTES) == null) {
                return Read.Failed("This .zip doesn't look like a Note Organizer backup.")
            }
            try {
                for (entry in archive.entries()) {
                    if (entry.isDirectory) continue
                    val placed = placeOf(entry.name) ?: continue
                    // A name this phone's storage cannot hold goes in under one
                    // it can, and the notes naming it are told below.
                    val place = fittedPlace(placed)
                    if (place != placed) renamed[placed.substringAfter('/')] = place.substringAfter('/')
                    val target = File(into, place)
                    target.parentFile?.mkdirs()
                    // The sizes the guards read are the archive's own claim.
                    // Counted here too, so an entry that unpacks to more
                    // than it declared is stopped at what it declared.
                    // A JSON file that never said its size, no more than the
                    // phone can read.
                    val claimed = if (entry.size >= 0) entry.size else BOMB_MIN_BYTES
                    val limit = if (place in JSON_FILES) minOf(claimed, jsonCeiling(heap)) else claimed
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
        // Every file read as JSON from outside: as UTF-8 or not at all
        // (Files.readData), and measured for nesting before it is parsed
        // (Files.parseOutside).
        fun text(name: String) = Files.readData(File(into, name))
        val notes = try {
            Files.parseOutside(text(NOTES)) { JSONArray(it) }
        } catch (e: Exception) {
            // Never the parser's own words: Android's org.json puts the whole
            // text it was reading into them -- every note in the backup, on
            // the screen, and tens of megabytes of it from a hostile file.
            return Read.Failed("Couldn't read the notes in that backup. " +
                ((e as? Files.Unreadable)?.message ?: "The file is damaged."))
        }
        // Templates, the Trash and the settings ride along when the backup
        // has them; older backups do not, and a bad one is skipped quietly
        // -- the notes are the payload.
        fun optionalArray(name: String): JSONArray? = try {
            File(into, name).takeIf { it.isFile }?.let { Files.parseOutside(text(name)) { s -> JSONArray(s) } }
        } catch (e: Exception) {
            null
        }
        val settings = try {
            File(into, SETTINGS).takeIf { it.isFile }?.let { Files.parseOutside(text(SETTINGS)) { s -> JSONObject(s) } }
        } catch (e: Exception) {
            null
        }
        val attachments = File(into, ATTACHMENTS).takeIf { it.isDirectory }
        val trash = optionalArray(TRASH)
        if (renamed.isNotEmpty()) {
            for (holder in (0 until notes.length()).map { notes.opt(it) } +
                (0 until (trash?.length() ?: 0)).map { trash!!.opt(it) }) {
                val records = (holder as? JSONObject)?.opt("attachments") as? JSONArray ?: continue
                for (i in 0 until records.length()) {
                    val record = records.opt(i) as? JSONObject ?: continue
                    val stored = Normalize.bareFileName(record.opt("stored_as") as? String ?: continue)
                    renamed[stored]?.let { record.put("stored_as", it) }
                }
            }
        }
        return Read.Ok(Contents(
            notes = (0 until notes.length()).mapNotNull { notes.opt(it) as? JSONObject },
            trash = trash,
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
