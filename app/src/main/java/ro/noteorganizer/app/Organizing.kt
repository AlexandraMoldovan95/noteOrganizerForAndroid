package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything that changes notes in bulk, or changes what notes are filed
 * under: duplicating, acting on several notes at once, the categories, and
 * the Trash's permanent side. Each one the desktop's own rule.
 *
 * Every write here follows the notebook's pattern: build a candidate copy,
 * write it whole, and only then let the arrays the screen reads change.
 * Where two files change, the one that keeps a note is written first, so
 * an interruption leaves a note in two places, never in none.
 */
object Organizing {

    private const val NOWHERE = "There is nowhere to keep the notes."
    private const val UNREADABLE = "Your notes file could not be read, so nothing will be written over it."
    private const val TRASH_UNREADABLE = "The Trash could not be read, so nothing was changed in it."

    private fun now() = Files.now()

    internal fun indexOfId(array: JSONArray, id: Int): Int {
        for (i in 0 until array.length()) {
            val itsId = (array.opt(i) as? JSONObject)?.opt("id")
            if (itsId is Number && itsId.toInt() == id) return i
        }
        return -1
    }

    private fun maxId(array: JSONArray): Int {
        var max = 0
        for (i in 0 until array.length()) {
            val itsId = (array.opt(i) as? JSONObject)?.opt("id")
            if (itsId is Number && itsId.toInt() > max) max = itsId.toInt()
        }
        return max
    }

    /** The live array the screen reads, given the written candidate's contents. */
    internal fun replace(live: JSONArray, from: JSONArray) {
        while (live.length() > 0) live.remove(live.length() - 1)
        for (i in 0 until from.length()) live.put(from.get(i))
    }

    internal fun write(file: File, array: JSONArray): String? = Files.writeAtomic(file, array.toString(2))

    // ---- one note, or several ----

    /**
     * A full copy as a new, separate note: "(copy)" on its title, a fresh
     * identity, and attached files of its own -- the desktop's
     * duplicate_note, which learnt that sharing a file let a permanent
     * delete of either note take the other's.
     */
    fun duplicate(load: Notebook.Load, id: Int, attachmentsDir: File?): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val at = indexOfId(array, id)
        if (at < 0) return SaveResult.Failed("This note is no longer in the notes file.")
        val original = array.getJSONObject(at)
        val clone = Normalize.normalizeNote(JSONObject(original.toString()).apply { remove("uid") })
        clone.remove("_prev")
        val base = (original.opt("title") as? String ?: "").trim()
        clone.put("title", if (base.isNotEmpty()) "$base (copy)" else "(copy)")
        val stamp = now()
        clone.put("updated", stamp)
        clone.put("id", maxId(array) + 1)
        val atts = clone.getJSONArray("attachments")
        val fresh = JSONArray()
        for (i in 0 until atts.length()) {
            val att = atts.getJSONObject(i)
            val copy = attachmentsDir?.let { Importing.copyAttachment(it, att.optString("stored_as")) }
            fresh.put(if (copy != null) JSONObject(att.toString()).put("stored_as", copy) else att)
        }
        clone.put("attachments", fresh)
        val candidate = JSONArray(array.toString()).put(clone)
        write(file, candidate)?.let { return SaveResult.Failed(it) }
        replace(array, candidate)
        return SaveResult.Ok(clone.getInt("id"))
    }

    /**
     * Several notes to the Trash in one go -- the Trash written first, as
     * a single delete writes it. The entries keep their ids, so [restore]
     * can bring exactly these back: the Undo after a delete.
     */
    fun trashNotes(load: Notebook.Load, ids: Collection<Int>): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val trashFile = load.trashFile ?: return SaveResult.Failed("There is nowhere to keep the Trash.")
        if (trashFile.exists() && load.trashRaw == null) return SaveResult.Failed(TRASH_UNREADABLE)
        val trash = load.trashRaw ?: JSONArray()
        val wanted = ids.toSet()
        val stamp = now()
        val trashCandidate = JSONArray(trash.toString())
        val notesCandidate = JSONArray()
        var moved = 0
        for (i in 0 until array.length()) {
            val note = array.opt(i) as? JSONObject
            val itsId = note?.opt("id")
            if (note != null && itsId is Number && itsId.toInt() in wanted) {
                trashCandidate.put(JSONObject(note.toString()).put("deleted_at", stamp))
                moved += 1
            } else {
                notesCandidate.put(array.get(i))
            }
        }
        if (moved == 0) return SaveResult.Failed("Those notes are no longer in the notes file.")
        write(trashFile, trashCandidate)?.let {
            return SaveResult.Failed("The Trash could not be written ($it), so nothing was deleted.")
        }
        load.trashRaw?.let { replace(it, trashCandidate) }
        write(file, notesCandidate)?.let {
            return SaveResult.Failed("$it — the notes are in the Trash and still in the list; nothing was lost.")
        }
        replace(array, notesCandidate)
        return SaveResult.Ok(0, imported = moved)
    }

    /** Pins or unpins several notes; one write. */
    fun setPinned(load: Notebook.Load, ids: Collection<Int>, pinned: Boolean): SaveResult =
        editEach(load, ids) { it.put("pinned", pinned) }

    /** Files several notes under one category; one write. */
    fun moveToCategory(load: Notebook.Load, ids: Collection<Int>, category: String): SaveResult {
        val name = category.trim().ifEmpty { Themes.PROTECTED_CATEGORY }
        return editEach(load, ids) { it.put("category", name) }
    }

    private fun editEach(load: Notebook.Load, ids: Collection<Int>, change: (JSONObject) -> Unit): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val wanted = ids.toSet()
        val candidate = JSONArray(array.toString())
        var changed = 0
        val stamp = now()
        for (i in 0 until candidate.length()) {
            val note = candidate.opt(i) as? JSONObject ?: continue
            val itsId = note.opt("id")
            if (itsId is Number && itsId.toInt() in wanted) {
                change(note)
                note.put("updated", stamp)
                changed += 1
            }
        }
        if (changed == 0) return SaveResult.Failed("Those notes are no longer in the notes file.")
        write(file, candidate)?.let { return SaveResult.Failed(it) }
        replace(array, candidate)
        return SaveResult.Ok(0, imported = changed)
    }

    // ---- categories ----

    /** How many notes are filed under [name], and how many more only have a day there. */
    fun categoryCounts(load: Notebook.Load, name: String): Pair<Int, Int> {
        val array = load.raw ?: return 0 to 0
        var filed = 0
        var daysOnly = 0
        for (i in 0 until array.length()) {
            val note = array.opt(i) as? JSONObject ?: continue
            if (note.opt("category") == name) {
                filed += 1
                continue
            }
            val overrides = note.opt("overrides") as? JSONObject ?: continue
            if (overrides.keys().asSequence().any { (overrides.opt(it) as? JSONObject)?.opt("category") == name }) {
                daysOnly += 1
            }
        }
        return filed to daysOnly
    }

    private fun saveCategories(load: Notebook.Load, map: Map<String, String>): SaveResult {
        val settings = load.settings ?: return SaveResult.Failed("There is nowhere to keep the settings.")
        return Settings.put(settings, Settings.CATEGORIES, Settings.categoriesJson(map))
    }

    fun addCategory(load: Notebook.Load, name: String, colour: String): SaveResult {
        val clean = name.trim()
        if (clean.isEmpty()) return SaveResult.Failed("A category needs a name.")
        if (clean in load.categories) return SaveResult.Failed("There is already a category called “$clean”.")
        Settings.categoryNameRefusal(clean, load.categories)?.let { return SaveResult.Failed(it) }
        val map = LinkedHashMap(load.categories)
        map[clean] = Notebook.sanitizeColor(colour, Themes.PALETTE_COLORS.first())
        return saveCategories(load, map)
    }

    fun recolourCategory(load: Notebook.Load, name: String, colour: String): SaveResult {
        if (name !in load.categories) return SaveResult.Failed("That category no longer exists.")
        val clean = Notebook.sanitizeColor(colour)
        if (clean.isEmpty()) return SaveResult.Failed("That is not a colour.")
        val map = LinkedHashMap(load.categories)
        map[name] = clean
        return saveCategories(load, map)
    }

    /**
     * A new name for a category, reaching every holder of the old one --
     * notes, their days, their rescue copies, the Trash and the templates
     * -- and keeping its place in the list. The desktop's
     * `_refile_category_everywhere`, after "The category renamed in one
     * place of six".
     */
    fun renameCategory(load: Notebook.Load, old: String, new: String): SaveResult {
        val clean = new.trim()
        if (old == Themes.PROTECTED_CATEGORY) return SaveResult.Failed("“General” can't be renamed.")
        if (old !in load.categories) return SaveResult.Failed("That category no longer exists.")
        if (clean.isEmpty()) return SaveResult.Failed("A category needs a name.")
        if (clean == old) return SaveResult.Ok(0)
        if (clean in load.categories) return SaveResult.Failed("There is already a category called “$clean”.")
        Settings.categoryNameRefusal(clean, load.categories, adding = false)?.let { return SaveResult.Failed(it) }
        refile(load, old, clean)?.let { return SaveResult.Failed(it) }
        return saveCategories(load, renamed(load.categories, old, clean))
    }

    /** The map with one name changed in its own place, its colour kept. */
    internal fun renamed(map: Map<String, String>, old: String, new: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, colour) in map) out[if (name == old) new else name] = colour
        return out
    }

    /**
     * Deletes a category. Its notes move to General, or -- when
     * [trashNotes] -- go to the Trash, restorable like any delete; a
     * repeating note with only a day filed here is never trashed for it,
     * and that day moves to General either way.
     */
    fun deleteCategory(load: Notebook.Load, name: String, trashNotes: Boolean): SaveResult {
        if (name == Themes.PROTECTED_CATEGORY) return SaveResult.Failed("“General” can't be deleted.")
        if (name !in load.categories) return SaveResult.Failed("That category no longer exists.")
        if (trashNotes) {
            val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
            val doomed = (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }
                .filter { it.opt("category") == name }
                .mapNotNull { (it.opt("id") as? Number)?.toInt() }
            if (doomed.isNotEmpty()) {
                val result = trashNotes(load, doomed)
                if (result is SaveResult.Failed) return result
            }
        }
        refile(load, name, Themes.PROTECTED_CATEGORY)?.let { return SaveResult.Failed(it) }
        val map = LinkedHashMap(load.categories)
        map.remove(name)
        return saveCategories(load, map)
    }

    /** Every holder of [old], moved to [new]: notes, Trash, templates. */
    private fun refile(load: Notebook.Load, old: String, new: String): String? {
        val file = load.file ?: return NOWHERE
        val array = load.raw ?: return UNREADABLE
        load.trashRaw?.let { trash ->
            val candidate = JSONArray(trash.toString())
            if (renameIn(candidate, old, new) > 0) {
                load.trashFile?.let { write(it, candidate)?.let { problem -> return problem } }
                replace(trash, candidate)
            }
        }
        val templatesFile = file.parentFile?.let { File(it, "templates.json") }
        if (templatesFile != null && templatesFile.isFile) {
            val templates = try {
                JSONArray(templatesFile.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
            if (templates != null) {
                var moved = false
                for (i in 0 until templates.length()) {
                    val tpl = templates.opt(i) as? JSONObject ?: continue
                    if (tpl.opt("category") == old) {
                        tpl.put("category", new)
                        moved = true
                    }
                }
                if (moved) write(templatesFile, templates)?.let { return it }
            }
        }
        val candidate = JSONArray(array.toString())
        if (renameIn(candidate, old, new) > 0) {
            write(file, candidate)?.let { return it }
            replace(array, candidate)
        }
        return null
    }

    /**
     * The desktop's `rename_category_everywhere`: base notes, their
     * per-day edits and each rescue copy. Returns how many held the name.
     */
    internal fun renameIn(notes: JSONArray, old: String, new: String): Int {
        var held = 0
        for (i in 0 until notes.length()) {
            val note = notes.opt(i) as? JSONObject ?: continue
            var found = false
            for (holder in listOf(note, note.opt("_prev") as? JSONObject)) {
                if (holder == null) continue
                if (holder.opt("category") == old) {
                    holder.put("category", new)
                    found = true
                }
                val overrides = holder.opt("overrides") as? JSONObject ?: continue
                for (day in overrides.keys()) {
                    val patch = overrides.opt(day) as? JSONObject ?: continue
                    if (patch.opt("category") == old) {
                        patch.put("category", new)
                        found = true
                    }
                }
            }
            if (found) held += 1
        }
        return held
    }

    // ---- the Trash, for good ----

    /** One Trash entry: its id and the moment it went in. */
    data class TrashKey(val id: Int, val deletedAt: String)

    private fun keyOf(entry: JSONObject) =
        TrashKey((entry.opt("id") as? Number)?.toInt() ?: 0, entry.opt("deleted_at") as? String ?: "")

    private fun storedNames(entries: List<JSONObject>): Set<String> {
        val out = HashSet<String>()
        for (entry in entries) {
            val atts = entry.opt("attachments") as? JSONArray ?: continue
            for (i in 0 until atts.length()) {
                ((atts.opt(i) as? JSONObject)?.opt("stored_as") as? String)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun entriesOf(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }

    /**
     * The files these entries use, deleted -- except any a note, or
     * another entry, still uses ([spare]), and any whose name tries to
     * leave the attachments folder. Only for PERMANENT deletion.
     */
    private fun deleteFiles(entries: List<JSONObject>, spare: Set<String>, attachmentsDir: File?) {
        val dir = attachmentsDir ?: return
        for (name in storedNames(entries)) {
            if (name in spare) continue
            val bare = Normalize.bareFileName(name)
            if (bare != name || bare.isEmpty()) continue
            val target = File(dir, bare)
            try {
                if (target.isFile) target.delete()
            } catch (e: Exception) {
                // Best effort: a file that will not go must not block the delete.
            }
        }
    }

    /** Whether an entry has outlived the window; an unreadable stamp never has. */
    internal fun isPast(entry: JSONObject, days: Int, now: Date = Date()): Boolean {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.isLenient = false
        val deleted = try {
            format.parse(entry.opt("deleted_at") as? String ?: "")
        } catch (e: Exception) {
            null
        } ?: return false
        val window = days.coerceIn(1, 365).toLong() * 24 * 60 * 60 * 1000
        return deleted.time < now.time - window
    }

    /** How many entries a window of [days] would remove: the question before a shorter one. */
    fun pastWindow(load: Notebook.Load, days: Int): Int = entriesOf(load.trashRaw).count { isPast(it, days) }

    /**
     * The Trash emptied of what has outlived its window, at the start of
     * the app. With a notebook of its own, the phone runs this itself; the
     * reason it once did not -- a folder shared with the desktop, which
     * owns the purge there -- no longer holds. Files a note still uses are
     * spared, a step safer than the desktop's own startup purge.
     */
    fun purgeExpired(load: Notebook.Load, attachmentsDir: File?, now: Date = Date()): Int {
        val trash = load.trashRaw ?: return 0
        val trashFile = load.trashFile ?: return 0
        val all = entriesOf(trash)
        val gone = all.filter { isPast(it, load.retentionDays, now) }
        if (gone.isEmpty()) return 0
        val kept = all.filter { it !in gone }
        val candidate = JSONArray().apply { kept.forEach { put(it) } }
        if (write(trashFile, candidate) != null) return 0
        replace(trash, candidate)
        deleteFiles(gone, storedNames(entriesOf(load.raw) + kept), attachmentsDir)
        return gone.size
    }

    /** These entries deleted for good, with the files nothing else uses. */
    fun deleteForever(load: Notebook.Load, keys: Collection<TrashKey>, attachmentsDir: File?): SaveResult {
        val trash = load.trashRaw ?: return SaveResult.Failed(TRASH_UNREADABLE)
        val trashFile = load.trashFile ?: return SaveResult.Failed(TRASH_UNREADABLE)
        val wanted = keys.toSet()
        val all = entriesOf(trash)
        val gone = all.filter { keyOf(it) in wanted }
        if (gone.isEmpty()) return SaveResult.Failed("Those notes are no longer in the Trash.")
        val kept = all.filter { it !in gone }
        val candidate = JSONArray().apply { kept.forEach { put(it) } }
        write(trashFile, candidate)?.let { return SaveResult.Failed(it) }
        replace(trash, candidate)
        deleteFiles(gone, storedNames(entriesOf(load.raw) + kept), attachmentsDir)
        return SaveResult.Ok(0, imported = gone.size)
    }

    /** The whole Trash, for good. */
    fun emptyTrash(load: Notebook.Load, attachmentsDir: File?): SaveResult =
        deleteForever(load, entriesOf(load.trashRaw).map { keyOf(it) }, attachmentsDir)

    /**
     * These entries back in the notebook, each with a fresh id -- its old
     * one may have been handed to a note made since -- the notebook written
     * first. A category deleted in the meantime files a note under General.
     */
    fun restore(load: Notebook.Load, keys: Collection<TrashKey>): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val trash = load.trashRaw ?: return SaveResult.Failed(TRASH_UNREADABLE)
        val trashFile = load.trashFile ?: return SaveResult.Failed(TRASH_UNREADABLE)
        val wanted = keys.toSet()
        val all = entriesOf(trash)
        val back = all.filter { keyOf(it) in wanted }
        if (back.isEmpty()) return SaveResult.Failed("Those notes are no longer in the Trash.")
        val notesCandidate = JSONArray(array.toString())
        var nextId = maxId(array) + 1
        var firstId = 0
        for (entry in back) {
            val note = JSONObject(entry.toString())
            note.remove("deleted_at")
            note.put("id", nextId)
            if ((note.opt("category") as? String) !in load.categories) note.put("category", Themes.PROTECTED_CATEGORY)
            if (firstId == 0) firstId = nextId
            nextId += 1
            notesCandidate.put(note)
        }
        write(file, notesCandidate)?.let { return SaveResult.Failed(it) }
        replace(array, notesCandidate)
        val trashCandidate = JSONArray().apply { all.filter { it !in back }.forEach { put(it) } }
        write(trashFile, trashCandidate)?.let {
            return SaveResult.Failed("$it — the notes are restored, and are still in the Trash too.")
        }
        replace(trash, trashCandidate)
        return SaveResult.Ok(firstId, imported = back.size)
    }
}
