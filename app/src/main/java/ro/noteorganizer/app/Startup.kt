package ro.noteorganizer.app

import org.json.JSONArray
import java.io.File

/**
 * What happens to the notebook before the first screen is drawn, in the
 * app's own private storage, which no other app can read. On a development
 * build, a notebook the development tools left in the older folder is
 * copied in -- not moved -- the first time there is none here
 * ([olderFolder]). A notebook that did not exist until this start opens on
 * the welcome note, once, as the desktop's does; one copied in or emptied
 * by hand never gets one. Then the Trash is emptied of what has outlived
 * the window the person chose, only when it can be read ([Settings.purgeWindow]).
 */
object Startup {

    private val FILES = listOf(Backup.NOTES, Backup.SETTINGS, Backup.TRASH, Backup.TEMPLATES)

    /**
     * The older folder to copy a notebook in from: on a development build
     * only. The development tools put notebooks there; a release build
     * never kept one there, and took in whatever it found -- written there
     * by another app, on Android 8 to 10 where others could, or a file of
     * 2 GB that stopped the app at every start (seen on the emulator).
     */
    fun olderFolder(debuggable: Boolean, external: File?): File? = if (debuggable) external else null

    /** What the start did that is worth one line at the top of the list, or null. */
    fun prepare(dir: File, older: File?, read: (File) -> ByteArray = File::readBytes): String? {
        dir.mkdirs()
        val notes = File(dir, Backup.NOTES)
        val lines = ArrayList<String>()
        if (!notes.exists()) {
            val old = older?.let { File(it, Backup.NOTES) }
            if (old != null && old.isFile) {
                if (copyIn(older, dir, read)) {
                    lines.add("Your notebook was brought into the app's own storage.")
                } else {
                    lines.add("The notebook in the older folder could not be read, so it was left there.")
                    greet(dir)
                }
            } else {
                greet(dir)
            }
        }
        var load = Notebook.load(dir)
        load.problem?.let { lines.add(it) }
        // The desktop's start says the same of the same notes,
        // and writes them at once: left for the next save, each start made
        // them again under new identities and said so again.
        if (load.setAside > 0) {
            val many = load.setAside != 1
            // Both reasons said, as on the desktop: a day the note deleted is
            // one, and the line once named only the other.
            lines.add("Some days of a repeating note had earlier text that only an older copy of it kept: " +
                "those days now show other text, or were deleted. It is kept as ${load.setAside} " +
                "note${if (many) "s" else ""} of ${if (many) "their" else "its"} own, each on its day, titled " +
                "with the day's or the series' name and \"(earlier text)\".")
            // Notes read, as any made means: a Trash or settings file that
            // could not be read does not hold this write (as on the desktop).
            load.file?.let { file -> load.raw?.let { Organizing.write(file, it) } }
        }
        // Every note a number of its own, as the desktop's repair_note_ids
        // makes it on each load: two notes sharing one were edited as one
        // and sent to the Trash together. Written once, then read again.
        if (load.problem == null && Organizing.repairIds(load)) load = Notebook.load(dir)
        // Settings that could not be read held the Trash's window and the answer
        // to Android's backup: marked lost before a fresh file reads them as
        // 30 days and on. Unknown, the backup is off until the person says.
        load.settings?.let {
            if (it.problem != null) {
                Settings.putAll(it, mapOf(Settings.PHONE_TRASH_WINDOW_UNKNOWN to true, Settings.PHONE_GOOGLE_BACKUP to false))
                lines.add("Android's backup of the app is off until you turn it on again in Settings → Backup.")
            }
        }
        // Restored from Android's backup made while the switch was off: that
        // backup held only the mark saying so, and the answer comes back.
        Settings.takeOffMark(dir, load.settings)
        // The day this install was first used, stamped once and never again,
        // as the desktop's get_first_launch_date stamps it: Statistics says it.
        load.settings?.let { settings ->
            if ((settings.raw.opt(Stats.FIRST_LAUNCH_DATE) as? String).isNullOrEmpty()) {
                Settings.put(settings, Stats.FIRST_LAUNCH_DATE, Recurrence.formatDate(java.time.LocalDate.now()))
            }
        }
        // The Trash emptied only by a known window -- a note kept too long can
        // still go, one deleted too soon cannot come back -- and not when the
        // notes could not be read, whose files it would not spare (as on the desktop).
        val window = load.settings?.let { Settings.purgeWindow(it) }
        if (window != null && load.problem == null) {
            val purged = Organizing.purgeExpired(load, File(dir, Backup.ATTACHMENTS), days = window)
            if (purged > 0) {
                lines.add(if (purged == 1) "1 note older than $window days was removed from the Trash."
                          else "$purged notes older than $window days were removed from the Trash.")
            }
        } else if (window == null && Organizing.pastWindow(load, Settings.TRASH_RETENTION_DAYS) > 0) {
            lines.add("Nothing was removed from the Trash: how long it keeps notes could not be read. " +
                      "Choose it again in Settings → Trash.")
        }
        return lines.joinToString("\n").ifEmpty { null }
    }

    /**
     * A backup unpacked for an import (`import_…`) or a file copied in to be
     * attached (`attach_…`), left in [cache] by an app that went before it
     * was done. Each is deleted once its work is over, so a process just
     * started holds none of its own: whatever is there is a leftover -- a
     * whole notebook's copy among them -- which stayed for good.
     */
    fun clearLeftovers(cache: File) {
        cache.listFiles()?.filter { it.name.startsWith("import_") || it.name.startsWith("attach_") }
            ?.forEach { it.deleteRecursively() }
    }

    /**
     * The older folder's notebook copied in; false, with what was written
     * taken back, when a file of it cannot be read. Thrown, the start
     * stopped -- and since nothing here was written yet, the next start
     * took the same road and stopped again: a 2 GB file is too large for
     * readBytes, an OutOfMemoryError no `catch (e: Exception)` sees.
     */
    private fun copyIn(from: File, to: File, read: (File) -> ByteArray): Boolean {
        val written = ArrayList<File>()
        try {
            for (name in FILES) {
                val source = File(from, name)
                if (source.isFile) File(to, name).let { Files.writeAtomic(it, read(source)); written.add(it) }
            }
            val attachments = File(from, Backup.ATTACHMENTS)
            if (attachments.isDirectory) {
                val target = File(to, Backup.ATTACHMENTS).apply { mkdirs() }
                attachments.listFiles()?.filter { it.isFile }?.forEach { file ->
                    val dest = File(target, file.name)
                    if (!dest.exists()) { Files.writeAtomic(dest, read(file)); written.add(dest) }
                }
            }
            return true
        } catch (e: Exception) {
            written.forEach { it.delete() }
        } catch (e: OutOfMemoryError) {
            written.forEach { it.delete() }
        }
        return false
    }

    /** A brand-new notebook: the welcome note, and the settings that remember it. */
    private fun greet(dir: File) {
        val load = Notebook.load(dir)
        val welcome = Welcome.build()
        val spans = welcome.getJSONArray("formatting").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
                .map { FormatSpan(it.getInt("start"), it.getInt("end"), it.getString("style")) }
        }
        val result = Notebook.createNote(load, welcome.getString("title"), welcome.getString("body"),
            formatting = spans, paragraphs = welcome.getJSONArray("paragraphs") as JSONArray)
        val settings = load.settings ?: return
        Settings.put(settings, Settings.WELCOME_OFFERED, true)
        if (result is SaveResult.Ok) {
            val uid = Notebook.load(dir).notes.firstOrNull { it.id == result.id }?.uid
            if (uid != null) Settings.put(Settings.load(dir), Settings.WELCOME_NOTE_UID, uid)
        }
    }
}
