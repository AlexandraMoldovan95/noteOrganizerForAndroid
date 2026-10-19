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
 * Then the Trash is emptied of what has outlived its window.
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
        // The day this install was first used, stamped once and never again,
        // as the desktop's get_first_launch_date stamps it: Statistics says it.
        load.settings?.let { settings ->
            if ((settings.raw.opt(Stats.FIRST_LAUNCH_DATE) as? String).isNullOrEmpty()) {
                Settings.put(settings, Stats.FIRST_LAUNCH_DATE, Recurrence.formatDate(java.time.LocalDate.now()))
            }
        }
        val purged = Organizing.purgeExpired(load, File(dir, Backup.ATTACHMENTS))
        if (purged > 0) {
            lines.add(if (purged == 1) "1 note older than ${load.retentionDays} days was removed from the Trash."
                      else "$purged notes older than ${load.retentionDays} days were removed from the Trash.")
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
