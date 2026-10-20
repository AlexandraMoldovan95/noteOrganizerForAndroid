package ro.noteorganizer.app

import org.json.JSONArray
import java.io.File

/**
 * What happens to the notebook before the first screen is drawn.
 *
 * The notebook lives in the app's own private storage, which no other app
 * can read. Two things can greet it:
 *
 * - A notebook left in the older folder -- where the development tools
 *   put one with `adb push` before this app had a notebook of its own --
 *   is copied in, once, the first time there is none here. Copied, not
 *   moved: nothing is deleted on the way.
 * - A notebook that did not exist until this start opens on the welcome
 *   note, once, as the desktop's does. One that was copied in, or emptied
 *   by hand, never gets one.
 *
 * Then the Trash is emptied of what has outlived its window -- the one
 * the person chose, and only when it can be read ([Settings.purgeWindow]).
 */
object Startup {

    private val FILES = listOf(Backup.NOTES, Backup.SETTINGS, Backup.TRASH, Backup.TEMPLATES)

    /** What the start did that is worth one line at the top of the list, or null. */
    fun prepare(dir: File, older: File?): String? {
        dir.mkdirs()
        val notes = File(dir, Backup.NOTES)
        val lines = ArrayList<String>()
        if (!notes.exists()) {
            val old = older?.let { File(it, Backup.NOTES) }
            if (old != null && old.isFile) {
                copyIn(older, dir)
                lines.add("Your notebook was brought into the app's own storage.")
            } else {
                greet(dir)
            }
        }
        var load = Notebook.load(dir)
        load.problem?.let { lines.add(it) }
        // Every note a number of its own, as the desktop's repair_note_ids
        // makes it on each load: two notes sharing one were edited as one
        // and sent to the Trash together. Written once, then read again.
        if (load.problem == null && Organizing.repairIds(load)) load = Notebook.load(dir)
        // Settings that could not be read held the Trash's window: it is
        // marked as lost before anything writes a fresh file -- or the next
        // start would read "never chosen" and purge at 30 days what the
        // person had kept for 90. The file's bytes were kept aside already.
        load.settings?.let { if (it.problem != null) Settings.put(it, Settings.PHONE_TRASH_WINDOW_UNKNOWN, true) }
        // The day this install was first used, stamped once and never again,
        // as the desktop's get_first_launch_date stamps it: Statistics says it.
        load.settings?.let { settings ->
            if ((settings.raw.opt(Stats.FIRST_LAUNCH_DATE) as? String).isNullOrEmpty()) {
                Settings.put(settings, Stats.FIRST_LAUNCH_DATE, Recurrence.formatDate(java.time.LocalDate.now()))
            }
        }
        // The Trash emptied by the window the person chose -- and only when
        // that window is known: a note kept too long can still be deleted,
        // one deleted too soon cannot come back.
        val window = load.settings?.let { Settings.purgeWindow(it) }
        if (window != null) {
            val purged = Organizing.purgeExpired(load, File(dir, Backup.ATTACHMENTS), days = window)
            if (purged > 0) {
                lines.add(if (purged == 1) "1 note older than $window days was removed from the Trash."
                          else "$purged notes older than $window days were removed from the Trash.")
            }
        } else if (Organizing.pastWindow(load, Settings.TRASH_RETENTION_DAYS) > 0) {
            lines.add("Nothing was removed from the Trash: how long it keeps notes could not be read. " +
                      "Choose it again in Settings → Trash.")
        }
        return lines.joinToString("\n").ifEmpty { null }
    }

    private fun copyIn(from: File, to: File) {
        for (name in FILES) {
            val source = File(from, name)
            if (source.isFile) Files.writeAtomic(File(to, name), source.readBytes())
        }
        val attachments = File(from, Backup.ATTACHMENTS)
        if (attachments.isDirectory) {
            val target = File(to, Backup.ATTACHMENTS).apply { mkdirs() }
            attachments.listFiles()?.filter { it.isFile }?.forEach { file ->
                val dest = File(target, file.name)
                if (!dest.exists()) Files.writeAtomic(dest, file.readBytes())
            }
        }
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
