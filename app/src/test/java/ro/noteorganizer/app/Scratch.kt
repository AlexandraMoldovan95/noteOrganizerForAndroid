package ro.noteorganizer.app

import org.json.JSONArray
import java.io.File
import java.nio.file.Files as NioFiles

// A notebook in a folder of its own, as the tests make one, read as the app
// reads it ([Notebook.load]); each file is written only when given. The
// folders go when the test task starts again (app/build.gradle.kts).

fun scratch(notes: String?, trash: String? = null, settings: String? = null,
            templates: String? = null, attachments: Boolean = false): Notebook.Load {
    val dir = NioFiles.createTempDirectory("notebook").toFile()
    if (notes != null) File(dir, "notes_data.json").writeText(notes)
    if (trash != null) File(dir, "trash.json").writeText(trash)
    if (settings != null) File(dir, "settings.json").writeText(settings)
    if (templates != null) File(dir, "templates.json").writeText(templates)
    if (attachments) File(dir, "attachments").mkdirs()
    return Notebook.load(dir)
}

/** The notes as the file holds them now. */
fun onDisk(load: Notebook.Load): JSONArray = JSONArray(load.file!!.readText())

/** The Trash as its file holds it now. */
fun trashOnDisk(load: Notebook.Load): JSONArray = JSONArray(load.trashFile!!.readText())

/** The folder the notebook is kept in. */
fun dirOf(load: Notebook.Load): File = load.file!!.parentFile!!

/** The notes the app reads from a file holding [text]. */
fun notesFrom(text: String): List<Note> = scratch(text).notes
