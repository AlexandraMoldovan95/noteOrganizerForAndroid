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

    /** Where the first note wearing [uid] stands in [array], or -1. */
    internal fun indexOfUid(array: JSONArray, uid: String): Int {
        for (i in 0 until array.length()) {
            if (((array.opt(i) as? JSONObject)?.opt("uid") as? String) == uid) return i
        }
        return -1
    }

    /**
     * Where the note an action was taken on stands now, taken on [id] and
     * known by [uid] when it has one: the note at [id] while it still wears
     * that uid; else the first note wearing it -- Undo brought it back from
     * the Trash under a new id; else -1, its id freed and perhaps another
     * note's now. Never the first wearing the uid while the note at [id]
     * wears it too: two notes can wear one, from a code or a backup that
     * held them so, and files picked for the second went to the first.
     */
    internal fun indexOfNote(array: JSONArray, id: Int, uid: String?): Int {
        val byId = indexOfId(array, id)
        if (uid.isNullOrEmpty()) return byId
        if (byId >= 0 && ((array.opt(byId) as? JSONObject)?.opt("uid") as? String) == uid) return byId
        return indexOfUid(array, uid)
    }

    /**
     * A note's id as this phone holds it: `normalize_note`'s
     * `int(str(id).strip())` -- a whole number as it is, text read as
     * Python's int() reads it after str.strip(), a float, "7.5" or "7.0",
     * none (the final audit's ninth round) -- and then only a positive
     * number an Int holds; 0 for anything else, which [repairIds] gives a
     * new number. Read with toInt(), a number past an Int wrapped onto
     * another's, 4,294,967,301 read as 5, and text past it was held at the
     * edge, where the next new note's id wrapped (the tenth).
     */
    internal fun heldId(raw: Any?): Int {
        val value = when (raw) {
            is Boolean -> return 0
            is Int, is Long, is Short, is Byte -> java.math.BigInteger.valueOf((raw as Number).toLong())
            is java.math.BigInteger -> raw
            is String -> Recurrence.pythonBigIntOf(PyText.strip(raw)) ?: return 0
            else -> return 0
        }
        return if (value.signum() > 0 && value.bitLength() < Int.SIZE_BITS) value.toInt() else 0
    }

    /** An id as the file holds it, when it is a whole number a Long holds; null otherwise -- to tell an id written as wanted. */
    private fun written(raw: Any?): Long? =
        if (raw is Int || raw is Long || raw is Short || raw is Byte) (raw as Number).toLong() else null

    /**
     * New note ids, for a notebook whose notes hold [inUse]: each one more
     * than the highest, as the desktop's `max(ids) + 1` gives them -- and,
     * once the highest is the most an Int holds, the lowest number no note
     * has, which there always is. `max + 1` there wrapped to
     * -2,147,483,648, and every new note after took that same id: two notes
     * edited as one, the second's words written over the first's (the final
     * audit's tenth round). Every road that makes a note asks here: a new
     * note, a duplicate, a Trash restore, an import, a day made a note.
     */
    internal class Ids(inUse: Iterable<Int>) {
        private val used = HashSet<Int>()
        private var top = 0
        private var free = 1

        init {
            for (id in inUse) if (id > 0) {
                used.add(id)
                if (id > top) top = id
            }
        }

        fun next(): Int {
            val id = if (top < Int.MAX_VALUE) top + 1 else {
                while (free in used) free++
                free
            }
            used.add(id)
            if (id > top) top = id
            return id
        }
    }

    /** [Ids] for the notes of [array], each id read as [heldId] reads it. */
    internal fun ids(array: JSONArray): Ids =
        Ids((0 until array.length()).map { heldId((array.opt(it) as? JSONObject)?.opt("id")) })

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
        clone.put("id", ids(array).next())
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
     * Notes to the Trash, one or several -- every delete in the app. The
     * entries keep their ids, so [restore] can bring exactly these back:
     * the Undo after a delete.
     *
     * The Trash is written FIRST, and that order is the safety argument: a
     * phone killed between the two writes leaves the notes in both files,
     * a duplicate a person can see, never in neither. The desktop writes
     * them in this order too. If the Trash cannot be written, the notes
     * file is not touched.
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
    /**
     * Pinned or not, its "updated" time left alone, as the desktop's
     * `toggle_pin` leaves it: a pin is where a note sits, not an edit, and
     * stamped, an unpinned note from last month jumped to the top of
     * "Recently updated" and said "Last updated: today".
     */
    fun setPinned(load: Notebook.Load, ids: Collection<Int>, pinned: Boolean): SaveResult =
        editEach(load, ids, stamp = false) { it.put("pinned", pinned) }

    /** Files several notes under one category; one write. */
    fun moveToCategory(load: Notebook.Load, ids: Collection<Int>, category: String): SaveResult {
        val name = category.trim().ifEmpty { Themes.PROTECTED_CATEGORY }
        return editEach(load, ids) { it.put("category", name) }
    }

    // ---- duplicates ----

    /**
     * Notes that are the same note twice: equal in title -- a trailing
     * "(copy)" aside -- text and comments, case and spacing aside, and in
     * category, checklist, repeat and a series' own days. Date, colour, pin and files do not
     * count. The desktop's `find_duplicate_groups`, on its own key, the one
     * the import's look-alike question asks too. Groups of two or more, each
     * in the notebook's order, by id.
     */
    fun duplicateGroups(load: Notebook.Load): List<List<Int>> {
        val raw = load.raw ?: return emptyList()
        val groups = LinkedHashMap<String, MutableList<Int>>()
        for (i in 0 until raw.length()) {
            val note = raw.opt(i) as? JSONObject ?: continue
            val id = (note.opt("id") as? Number)?.toInt() ?: continue
            groups.getOrPut(Normalize.dupKey(note)) { ArrayList() }.add(id)
        }
        return groups.values.filter { it.size > 1 }
    }

    /**
     * The ticked copies ([remove]) of each group to the Trash, and their
     * files handed to the first copy of the group left, up to the files a
     * note holds. Each file is copied for it: the copy going to the Trash
     * keeps its own, and the Trash's purge deletes those. A group ticked
     * whole goes whole. The desktop's
     * `_cleanup_selected_duplicates`, after its question -- the files are
     * merged only once it is answered, or every No would copy them again.
     */
    fun cleanUpDuplicates(load: Notebook.Load, groups: List<List<Int>>, remove: Set<Int>,
                          attachmentsDir: File?): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val going = groups.flatten().filter { it in remove }.toSet()
        if (going.isEmpty()) return SaveResult.Failed("Tick the duplicates you want to remove first.")
        val candidate = JSONArray(array.toString())
        var grew = false
        for (group in groups) {
            if (group.none { it in going }) continue
            val keptAt = group.firstOrNull { it !in going }?.let { indexOfId(candidate, it) }?.takeIf { it >= 0 } ?: continue
            val kept = candidate.getJSONObject(keptAt)
            val merged = kept.opt("attachments") as? JSONArray ?: JSONArray().also { kept.put("attachments", it) }
            val seen = (0 until merged.length())
                .mapNotNull { (merged.opt(it) as? JSONObject)?.opt("stored_as") as? String }.toMutableSet()
            for (id in group.filter { it in going }) {
                val at = indexOfId(candidate, id).takeIf { it >= 0 } ?: continue
                val atts = candidate.getJSONObject(at).opt("attachments") as? JSONArray ?: continue
                for (j in 0 until atts.length()) {
                    // A note holds MAX_ATTACHMENTS files, as the import's
                    // adoptFiles stops: past them, the list hid the rest and
                    // the purge took them. They stay with their copy, in the Trash.
                    if (merged.length() >= Normalize.MAX_ATTACHMENTS) break
                    val att = atts.opt(j) as? JSONObject ?: continue
                    val stored = att.opt("stored_as") as? String ?: continue
                    if (!seen.add(stored)) continue
                    // Duplicate gave the copy each file again under a new
                    // name: the kept note holds it already, and merged, it
                    // listed every file twice (the final audit's wave 3).
                    if (attachmentsDir != null && (0 until merged.length()).any { k ->
                            (merged.opt(k) as? JSONObject)?.let { sameFile(attachmentsDir, att, it) } == true }) continue
                    // A file that cannot be copied is left with the copy in
                    // the Trash, never shared with one the purge will empty.
                    val copy = attachmentsDir?.let { Importing.copyAttachment(it, stored) } ?: continue
                    merged.put(JSONObject(att.toString()).put("stored_as", copy))
                    grew = true
                }
            }
        }
        if (grew) {
            write(file, candidate)?.let { return SaveResult.Failed(it) }
            replace(array, candidate)
        }
        return trashNotes(load, going)
    }

    /** Whether two attachments are one file twice: the same name shown and the same bytes, under two stored names. */
    private fun sameFile(dir: File, one: JSONObject, other: JSONObject): Boolean {
        if ((one.opt("filename") as? String ?: "") != (other.opt("filename") as? String ?: "")) return false
        val first = Normalize.bareFileName(one.opt("stored_as") as? String ?: "")
        val second = Normalize.bareFileName(other.opt("stored_as") as? String ?: "")
        if (first.isEmpty() || second.isEmpty()) return false
        val a = File(dir, first)
        val b = File(dir, second)
        if (!a.isFile || !b.isFile || a.length() != b.length()) return false
        return try {
            java.io.DataInputStream(a.inputStream().buffered()).use { x ->
                java.io.DataInputStream(b.inputStream().buffered()).use { y ->
                    val p = ByteArray(65536)
                    val q = ByteArray(65536)
                    var left = a.length()
                    while (left > 0) {
                        val n = minOf(left, p.size.toLong()).toInt()
                        x.readFully(p, 0, n)
                        y.readFully(q, 0, n)
                        for (i in 0 until n) if (p[i] != q[i]) return false
                        left -= n
                    }
                    true
                }
            }
        } catch (e: java.io.IOException) {
            false
        }
    }

    // ---- attached files ----

    /** A file handed over to attach: the name to show, and a way to put its bytes into a file. */
    class Incoming(val name: String, val copyTo: (File) -> Unit)

    /** What [attach] did: the write, how many files joined, and the names that would not copy. */
    data class Attached(val result: SaveResult, val added: Int, val failed: List<String>)

    private val STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSS", Locale.US)

    /**
     * How many more files a note may take: the desktop's ceiling less what
     * it holds. The ceiling is enforced by truncating when a note is read,
     * which is right for a hostile code and wrong for a person's own files
     * -- copied, listed, then silently dropped -- so adding asks first.
     */
    fun roomFor(load: Notebook.Load, id: Int): Int {
        val note = load.raw?.let { a -> indexOfId(a, id).takeIf { it >= 0 }?.let { a.getJSONObject(it) } } ?: return 0
        val held = (note.opt("attachments") as? JSONArray)?.length() ?: 0
        return (Normalize.MAX_ATTACHMENTS - held).coerceAtLeast(0)
    }

    /**
     * The name a file is kept under in the attachments folder: the moment,
     * to the microsecond, then its own name -- the desktop's `add_attachment`
     * -- and a name Windows will take, because a backup carries it there:
     * no folder in it, none of `\ / : * ? " < > |` or a control character,
     * no trailing dot or space.
     */
    fun storedName(display: String, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
        // bareFileName has taken the folder and the trailing dots and spaces.
        val own = Normalize.bareFileName(display)
            .map { if (it < ' ' || it in "\\/:*?\"<>|") '_' else it }
            .joinToString("").ifEmpty { "file" }
        // Within what the phone's storage holds: a long name in Cyrillic,
        // legal where it came from, could not be copied once stamped.
        return Backup.fittedPlace(STAMP.format(now) + "_" + own)
    }

    /**
     * [incoming] attached to note [id]: each copied into [attachmentsDir]
     * under a name of its own, then the note written once with all of them.
     * At most the room left -- the caller asks first, as the desktop does,
     * and says which would not copy. A write that fails takes the copied
     * files back out, so nothing is left in the folder that no note names.
     */
    fun attach(load: Notebook.Load, id: Int, attachmentsDir: File, incoming: List<Incoming>,
               clock: () -> java.time.LocalDateTime = { java.time.LocalDateTime.now() },
               uid: String? = null): Attached {
        val file = load.file ?: return Attached(SaveResult.Failed(NOWHERE), 0, emptyList())
        val array = load.raw ?: return Attached(SaveResult.Failed(UNREADABLE), 0, emptyList())
        // The note the files were picked for, found by its [uid] when it has
        // one: a copy that outlives the editor ends after the person may have
        // sent that note to the Trash and made another, which takes the freed
        // id -- and the files went to the new note, unsaid. And by the uid
        // when the id no longer holds it: Undo brings a note back from the
        // Trash under a new id, and its files were refused as "no longer
        // here" with the note in the list ([indexOfNote]).
        val index = indexOfNote(array, id, uid)
        if (index < 0) {
            return Attached(SaveResult.Failed("The note these files were picked for is no longer here, " +
                "so nothing was attached."), 0, emptyList())
        }
        val noteId = ((array.opt(index) as? JSONObject)?.opt("id") as? Number)?.toInt() ?: id
        val room = roomFor(load, noteId)
        if (room == 0) {
            return Attached(SaveResult.Failed("A note can keep ${Normalize.MAX_ATTACHMENTS} attached files. " +
                "Remove one to add another, or split this into two notes."), 0, emptyList())
        }
        attachmentsDir.mkdirs()
        val copied = ArrayList<Pair<String, String>>()
        val failed = ArrayList<String>()
        for (one in incoming.take(room)) {
            // Two files picked at once can share a microsecond and a name.
            val base = storedName(one.name, clock())
            var stored = base
            var n = 2
            while (File(attachmentsDir, stored).exists()) {
                stored = Backup.fittedPlace(base.substringBefore('_') + "-" + (n++) + "_" + base.substringAfter('_'))
            }
            val target = File(attachmentsDir, stored)
            try {
                one.copyTo(target)
                copied.add(Normalize.bareFileName(one.name).ifEmpty { stored } to stored)
            } catch (e: Exception) {
                target.delete()
                failed.add(one.name)
            }
        }
        if (copied.isEmpty()) return Attached(SaveResult.Failed("No file could be attached."), 0, failed)
        val candidate = JSONArray(array.toString())
        val note = candidate.getJSONObject(index)
        val atts = note.opt("attachments") as? JSONArray ?: JSONArray().also { note.put("attachments", it) }
        for ((display, stored) in copied) atts.put(JSONObject().put("filename", display).put("stored_as", stored))
        note.put("updated", now())
        write(file, candidate)?.let { problem ->
            copied.forEach { File(attachmentsDir, it.second).delete() }
            return Attached(SaveResult.Failed(problem), 0, failed)
        }
        replace(array, candidate)
        return Attached(SaveResult.Ok(noteId), copied.size, failed)
    }

    /**
     * The attached file [storedAs] taken off note [id], and its stored copy
     * deleted -- the desktop's `remove_attachment`, which asks first, as the
     * screen does. Spared when another note, or a note in the Trash, still
     * uses the same stored file: the desktop's removal deletes it anyway.
     */
    fun detach(load: Notebook.Load, id: Int, storedAs: String, attachmentsDir: File?): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val index = indexOfId(array, id)
        if (index < 0) return SaveResult.Failed("This note is no longer in the notes file.")
        val candidate = JSONArray(array.toString())
        val note = candidate.getJSONObject(index)
        val atts = note.opt("attachments") as? JSONArray ?: return SaveResult.Failed("That file is no longer attached.")
        val at = (0 until atts.length()).firstOrNull { (atts.opt(it) as? JSONObject)?.opt("stored_as") == storedAs }
            ?: return SaveResult.Failed("That file is no longer attached.")
        val removed = atts.getJSONObject(at)
        atts.remove(at)
        note.put("updated", now())
        write(file, candidate)?.let { return SaveResult.Failed(it) }
        replace(array, candidate)
        deleteFiles(listOf(JSONObject().put("attachments", JSONArray().put(removed))),
                    storedNames(entriesOf(load.raw) + entriesOf(load.trashRaw)), attachmentsDir)
        return SaveResult.Ok(id)
    }

    // ---- Custom order: the list arranged by hand ----

    /**
     * Whether two notes may trade places in Custom order: both pinned or
     * both not, and -- in a list grouped by category -- in the same group.
     * A pinned note always stands above the rest and a group is its
     * category, so a move across either would be undone by the next
     * drawing. The desktop's `_same_list_block`.
     */
    fun sameBlock(a: Note, b: Note, grouped: Boolean): Boolean =
        a.pinned == b.pinned && (!grouped || a.category == b.category)

    /**
     * Where "Move up" ([step] -1) or "Move down" (+1) takes note [id] among
     * [shown], the notes in the order the list shows them: the neighbour it
     * goes before (up) or after (down), as a drag's drop names it for
     * [moveNote] -- or null when there is no place that way in its block.
     * Arranging by hand without a drag, which a screen reader, Switch Access
     * or a hand that cannot drag could not do at all (the final audit).
     */
    fun stepPlace(shown: List<Note>, id: Int, step: Int, grouped: Boolean): Pair<Int, Boolean>? {
        val at = shown.indexOfFirst { it.id == id }
        if (at < 0) return null
        val other = shown.getOrNull(at + step) ?: return null
        if (!sameBlock(shown[at], other, grouped)) return null
        return other.id to (step < 0)
    }

    /**
     * Every note given its place in Custom order from the way [order]
     * sorts them -- all of them, not only those a search or Show lets
     * through, so what is hidden keeps its place among the rest. The
     * desktop's `_place_notes_as_sorted`, for Custom order chosen before
     * anything was placed: the list then stays as it was showing.
     */
    fun placeAsSorted(load: Notebook.Load, order: SortOrder,
                      today: String = Recurrence.formatDate(java.time.LocalDate.now())): SaveResult {
        val raw = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val byId = (0 until raw.length()).mapNotNull { raw.opt(it) as? JSONObject }
            .associateBy { (it.opt("id") as? Number)?.toInt() }
        val sorted = sortNotes(load.notes, order, today) { note -> byId[note.id]?.let { Recurrence.nextOnOrAfter(it, today) } }
        return numbered(load, sorted)
    }

    /**
     * [id] put just before -- or, with [before] false, just after -- [anchor]
     * in Custom order, and every note numbered again in one pass: the
     * desktop's `_move_note`, made in Custom order. A move is not an edit,
     * so no note's `updated` changes. Refused across a pinned note's
     * block or, [grouped], a category's.
     */
    fun moveNote(load: Notebook.Load, id: Int, anchor: Int, before: Boolean, grouped: Boolean): SaveResult {
        val all = sortNotes(load.notes, SortOrder.CUSTOM)
        val note = all.firstOrNull { it.id == id }
        val next = all.firstOrNull { it.id == anchor }
        if (note == null || next == null) return SaveResult.Failed("Those notes are no longer in the notes file.")
        if (note.id == next.id) return SaveResult.Ok(id)
        if (!sameBlock(note, next, grouped)) {
            return SaveResult.Failed(if (note.pinned != next.pinned) "A pinned note stays above the rest."
                                     else "A note moves within its own category here.")
        }
        val arranged = all.filter { it.id != id }.toMutableList()
        val at = arranged.indexOfFirst { it.id == anchor } + (if (before) 0 else 1)
        arranged.add(at, note)
        return numbered(load, arranged)
    }

    /** Each note's `order`, its place in [arranged]; one write, no stamp. */
    private fun numbered(load: Notebook.Load, arranged: List<Note>): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val candidate = JSONArray(array.toString())
        val place = arranged.withIndex().associate { (i, n) -> n.id to i }
        for (i in 0 until candidate.length()) {
            val note = candidate.opt(i) as? JSONObject ?: continue
            val itsId = (note.opt("id") as? Number)?.toInt() ?: continue
            place[itsId]?.let { note.put("order", it) }
        }
        write(file, candidate)?.let { return SaveResult.Failed(it) }
        replace(array, candidate)
        return SaveResult.Ok(arranged.firstOrNull()?.id ?: 0)
    }

    /**
     * Every note a positive id no other note has -- the desktop's
     * `repair_note_ids`, with `normalize_note`'s reading of an id written
     * as text: the first holder of a number keeps it, so a note remembered
     * as open is still the same note, and every later holder takes the next
     * free number. Two notes sharing an id were edited as one and went to
     * the Trash together. Written when anything changed; true then.
     *
     * One difference from the desktop, which Python's numbers never meet: an
     * id this phone cannot hold, past 2,147,483,647, takes a new number like
     * a shared one ([heldId]); the desktop keeps it. Held at the edge, it
     * made the next new note's id wrap to -2,147,483,648, and every new note
     * after took that same id (the final audit's tenth round).
     */
    fun repairIds(load: Notebook.Load): Boolean {
        val file = load.file ?: return false
        val array = load.raw ?: return false
        val notes = (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }
        val ids = notes.map { heldId(it.opt("id")) }
        // The first holders kept, every one, before any number is handed out:
        // a free number given early is not one a later note already holds.
        val seen = HashSet<Int>()
        val keeps = ids.map { id -> id > 0 && seen.add(id) }
        val fresh = Ids(seen)
        val wanted = ids.indices.map { if (keeps[it]) ids[it] else fresh.next() }
        if (notes.indices.all { written(notes[it].opt("id")) == wanted[it].toLong() }) return false
        val candidate = JSONArray(array.toString())
        var k = 0
        for (i in 0 until candidate.length()) {
            val note = candidate.opt(i) as? JSONObject ?: continue
            note.put("id", wanted[k++])
        }
        write(file, candidate)?.let { return false }
        replace(array, candidate)
        return true
    }

    private fun editEach(load: Notebook.Load, ids: Collection<Int>, stamp: Boolean = true,
                         change: (JSONObject) -> Unit): SaveResult {
        val file = load.file ?: return SaveResult.Failed(NOWHERE)
        val array = load.raw ?: return SaveResult.Failed(UNREADABLE)
        val wanted = ids.toSet()
        val candidate = JSONArray(array.toString())
        var changed = 0
        val at = now()
        for (i in 0 until candidate.length()) {
            val note = candidate.opt(i) as? JSONObject ?: continue
            val itsId = note.opt("id")
            if (itsId is Number && itsId.toInt() in wanted) {
                change(note)
                if (stamp) note.put("updated", at)
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

    /**
     * The category map written -- and, when [old] was renamed to [new] or
     * deleted (null), the two settings that hold a category's name with it:
     * the folded groups and the list's "Show", as the desktop moves both.
     * One write, so the list never shows a filter naming a category gone.
     */
    private fun saveCategories(load: Notebook.Load, map: Map<String, String>,
                               old: String? = null, new: String? = null): SaveResult {
        val settings = load.settings ?: return SaveResult.Failed("There is nowhere to keep the settings.")
        val changes = linkedMapOf<String, Any?>(Settings.CATEGORIES to Settings.categoriesJson(map))
        if (old != null) {
            val folded = Settings.collapsedGroups(settings.raw)
            if (old in folded) changes[Settings.COLLAPSED_GROUPS] = JSONArray((folded - old + listOfNotNull(new)).sorted())
            if (settings.raw.opt(Settings.NOTE_FILTER) == old) changes[Settings.NOTE_FILTER] = new ?: Showing.ALL
        }
        return Settings.putAll(settings, changes)
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
        return saveCategories(load, renamed(load.categories, old, clean), old, clean)
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
        return saveCategories(load, map, old = name, new = null)
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
                Files.parseOwn(templatesFile) { JSONArray(it) }
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

    /**
     * One Trash entry: its id, the moment it went in, and its uid. An id is
     * handed out again once its note is deleted, so two entries deleted in
     * one second could share the first two: picking one picked both, and
     * the Trash page, keyed by them, closed the app (the desktop's
     * _trash_tick_key, 2026-10-26).
     */
    data class TrashKey(val id: Int, val deletedAt: String, val uid: String = "")

    /** The key of a Trash entry as the screen holds it. */
    fun keyOf(note: Note) = TrashKey(note.id, note.deletedAt, note.uid)

    /**
     * A key per Trash row that no two rows share, as a list must have or the
     * page closes the app: two entries alike in all three -- a hand-edited
     * file -- are told apart by a mark per twin.
     */
    fun rowKeys(notes: List<Note>): List<String> {
        val used = HashSet<String>()
        return notes.map { note ->
            var key = "${note.id}|${note.deletedAt}|${note.uid}"
            while (!used.add(key)) key += "#"
            key
        }
    }

    private fun keyOf(entry: JSONObject) =
        TrashKey((entry.opt("id") as? Number)?.toInt() ?: 0, entry.opt("deleted_at") as? String ?: "",
                 entry.opt("uid") as? String ?: "")

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

    /**
     * When an entry went to the Trash, read strictly: "2026-02-30" is no
     * date, and neither is a stamp with more after its seconds -- the
     * desktop's strptime refuses both. The purge and the row's "days left"
     * both ask this, so they cannot disagree, as on the desktop.
     */
    internal fun deletedAt(stamp: String?): Date? {
        val text = stamp ?: return null
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.isLenient = false
        val at = java.text.ParsePosition(0)
        val date = format.parse(text, at)
        return if (date != null && at.index == text.length) date else null
    }

    /** Whether an entry has outlived the window; an unreadable stamp never has. */
    internal fun isPast(entry: JSONObject, days: Int, now: Date = Date()): Boolean {
        val deleted = deletedAt(entry.opt("deleted_at") as? String) ?: return false
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
    fun purgeExpired(load: Notebook.Load, attachmentsDir: File?, now: Date = Date(),
                     days: Int = load.retentionDays): Int {
        val trash = load.trashRaw ?: return 0
        val trashFile = load.trashFile ?: return 0
        val all = entriesOf(trash)
        val gone = all.filter { isPast(it, days, now) }
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
     * A uid another note now has is replaced, as the desktop's
     * `restore_many_from_trash` replaces it: a copy that came by code while
     * this one sat in the Trash took its identity, and two notes with one
     * uid meant the next code updated one of them and never the other.
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
        val fresh = ids(array)
        var firstId = 0
        val worn = HashSet<String>()
        for (i in 0 until array.length()) (array.opt(i) as? JSONObject)?.let { worn.add(it.opt("uid") as? String ?: "") }
        for (entry in back) {
            val note = JSONObject(entry.toString())
            note.remove("deleted_at")
            // Read from the Trash's file, as the notes are at load: a series
            // 0.12.0 turned off and deleted takes its days back into the note
            // (Notebook.settleAsRead), as the desktop's restore does.
            Notebook.settleAsRead(note)
            val nextId = fresh.next()
            note.put("id", nextId)
            val uid = note.opt("uid") as? String ?: ""
            if (uid in worn) note.put("uid", java.util.UUID.randomUUID().toString())
            worn.add(note.opt("uid") as? String ?: "")
            if ((note.opt("category") as? String) !in load.categories) note.put("category", Themes.PROTECTED_CATEGORY)
            if (firstId == 0) firstId = nextId
            notesCandidate.put(note)
        }
        // Days a series' copy set aside as one was read, notes of their own.
        Notebook.settleAside(notesCandidate)
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
