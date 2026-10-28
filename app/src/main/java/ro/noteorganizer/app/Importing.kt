package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bringing notes in -- from a share code or from a backup -- by the
 * desktop's rules, asked as the desktop asks them.
 *
 * Nothing is overwritten on its own. An import is planned first, without
 * writing anything, and the plan says which questions there are:
 *
 * 1. Notes this device already has, by identity, that arrive CHANGED:
 *    update them, or add them as new notes beside the originals. From a
 *    backup the question also says when the arriving copy is OLDER.
 * 2. Notes that arrive exactly as they are here: skip them, or add copies.
 * 3. Notes nothing here matches by identity that still LOOK identical to
 *    one here (the desktop's `_dup_key`): import them anyway, or skip them.
 *
 * Every answer is given before anything is written. Then [apply] writes
 * it all, the way `import_data` writes it: the backup's categories, its
 * painted calendar days, its templates and its Trash join what is here
 * without replacing any of it, and a file another note already uses is
 * given a copy of its own, so deleting one note can never take another's
 * file with it.
 *
 * The two routes share this one planner and this one apply, as they do
 * on the desktop: two hand-rolled copies of one judgement is how its
 * search rules once drifted apart.
 */
object Importing {

    enum class Source(val word: String) { CODE("code"), BACKUP("backup") }

    /** A note this device has, and the copy that arrived for it. */
    data class Known(val mineIndex: Int, val theirs: JSONObject)

    data class Plan(
        val source: Source,
        /** How many notes arrived, in all. */
        val total: Int,
        val differing: List<Known>,
        val unchanged: List<Known>,
        /** Notes nothing here matches by identity. */
        val fresh: List<JSONObject>,
        /** How many of [differing] are older than the copy here (backups). */
        val older: Int,
    )

    /** The answers, one per question the plan raised. */
    data class Answers(
        /** Question 1: true updates the notes, false adds them as new ones. */
        val update: Boolean = false,
        /** Question 2: add the unchanged notes again as copies. */
        val copyUnchanged: Boolean = false,
        /** Question 3: skip the notes that look like ones already here. */
        val skipDuplicates: Boolean = false,
    )

    /** What a backup carries besides its notes. */
    data class Extras(
        val categories: JSONObject? = null,
        val dayColors: JSONObject? = null,
        val dayEmoji: JSONObject? = null,
        /** How many days the backup's notebook kept its Trash, when it chose a usable window. */
        val trashWindow: Int? = null,
        val templates: JSONArray? = null,
        val trash: JSONArray? = null,
        /** Where the backup's attached files were unpacked. */
        val attachments: File? = null,
    )

    /** What [apply] did, counted the way the desktop's message counts it. */
    data class Outcome(
        val added: Int,
        val updated: Int,
        val unchangedSkipped: Int,
        val duplicatesSkipped: Int,
        val addedCategories: Int = 0,
        val refusedCategories: Int = 0,
        val restoredDays: Int = 0,
        val addedTemplates: Int = 0,
        val addedTrash: Int = 0,
        /** The first note it added or updated, for the screen to open. */
        val firstId: Int = 0,
    )

    sealed class Result {
        data class Done(val outcome: Outcome) : Result()
        data class Failed(val reason: String) : Result()
    }

    private fun fields(source: Source) =
        if (source == Source.BACKUP) Notebook.UPDATABLE_FROM_BACKUP else Notebook.UPDATABLE_FROM_SHARED

    /**
     * Asked of no file: what arrived, sorted into the three kinds.
     * [categories] are the ones the notebook will have -- its own, and a
     * backup's it adds: a note filed under one it will not have is
     * compared as the General note the import makes it, as the desktop's
     * normalize_note compares it. Compared as it came, an unchanged note
     * read as "edited elsewhere", and Update changed nothing but its rescue
     * copy.
     */
    /**
     * [note] filed as this device files it: under General when its category
     * is not one here -- and each of its days' own categories too, as the
     * desktop's `clean_override` files a day under one it lacks. Only the
     * note's was refiled; a day's kept a name no Show here offers. Changes
     * [note], and gives it back.
     */
    internal fun fileUnder(note: JSONObject, categories: Set<String>): JSONObject {
        if ((note.opt("category") as? String ?: "").ifEmpty { "General" } !in categories) note.put("category", "General")
        val overrides = note.opt("overrides") as? JSONObject ?: return note
        for (day in overrides.keys()) {
            val patch = overrides.opt(day) as? JSONObject ?: continue
            val category = patch.opt("category") as? String ?: continue
            if (category !in categories) patch.put("category", "General")
        }
        return note
    }

    private fun needsFiling(note: JSONObject, categories: Set<String>): Boolean {
        if ((note.opt("category") as? String ?: "").ifEmpty { "General" } !in categories) return true
        val overrides = note.opt("overrides") as? JSONObject ?: return false
        return overrides.keys().asSequence().any { day ->
            val category = (overrides.opt(day) as? JSONObject)?.opt("category") as? String
            category != null && category !in categories
        }
    }

    fun plan(notebook: JSONArray, incoming: List<JSONObject>, source: Source,
             categories: Set<String>? = null): Plan {
        val asHere: (JSONObject) -> JSONObject = { note ->
            if (categories == null || !needsFiling(note, categories)) note
            else fileUnder(JSONObject(note.toString()), categories)
        }
        val byUid = HashMap<String, Int>()
        for (i in 0 until notebook.length()) {
            val uid = ((notebook.opt(i) as? JSONObject)?.opt("uid") as? String)?.trim()
            if (!uid.isNullOrEmpty()) byUid.putIfAbsent(uid, i)
        }
        val differing = ArrayList<Known>()
        val unchanged = ArrayList<Known>()
        val fresh = ArrayList<JSONObject>()
        var older = 0
        for (theirs in incoming) {
            val at = (theirs.opt("uid") as? String)?.trim()?.ifEmpty { null }?.let { byUid[it] }
            if (at == null) {
                fresh.add(theirs)
                continue
            }
            val mine = notebook.getJSONObject(at)
            val known = Known(at, theirs)
            if (differs(asHere(mine), asHere(theirs), source)) {
                differing.add(known)
                if (source == Source.BACKUP &&
                    (theirs.opt("updated") as? String ?: "") < (mine.opt("updated") as? String ?: "")
                ) older += 1
            } else {
                unchanged.add(known)
            }
        }
        return Plan(source, incoming.size, differing, unchanged, fresh, older)
    }

    /**
     * Whether bringing [theirs] over [mine] would change anything: a field
     * the route may replace, or -- from a backup, whose files are added and
     * never swapped -- a file this device's copy lacks.
     */
    private fun differs(mine: JSONObject, theirs: JSONObject, source: Source): Boolean {
        if (Notebook.sharedUpdateChanges(mine, theirs, fields(source))) return true
        return source == Source.BACKUP && filesToAdopt(mine, theirs).isNotEmpty()
    }

    private fun storedNames(note: JSONObject): Set<String> {
        val list = note.opt("attachments") as? JSONArray ?: return emptySet()
        val out = HashSet<String>()
        for (i in 0 until list.length()) {
            (list.opt(i) as? JSONObject)?.opt("stored_as")?.let { if (it is String) out.add(it) }
        }
        return out
    }

    private fun filesToAdopt(mine: JSONObject, theirs: JSONObject): List<JSONObject> {
        val have = storedNames(mine)
        val theirFiles = Normalize.cleanAttachments(theirs.opt("attachments") as? JSONArray ?: JSONArray())
        return (0 until theirFiles.length()).map { theirFiles.getJSONObject(it) }
            .filter { it.optString("stored_as").isNotEmpty() && it.optString("stored_as") !in have }
    }

    /**
     * How many of the notes left to import as new look identical to a
     * note the notebook will hold -- the notebook as the updates will
     * leave it: the third question, once the first is answered.
     */
    fun lookAlikes(notebook: JSONArray, plan: Plan, update: Boolean): Int {
        val keys = notebookKeys(notebook, plan, update)
        return remaining(plan, update).count { keyOf(it) in keys }
    }

    /**
     * [lookAlikes] for each answer to the first question, each worked out
     * once. The questions asked it from their drawing, two or three times
     * and again at every redraw, every note of both notebooks normalized
     * each time: with a thousand here and a thousand coming, the screen
     * held still 1.3 seconds on the emulator. A backup's are worked out
     * before the questions open, away from the screen ([warm]).
     */
    class LookAlikes(private val count: (Boolean) -> Int) {
        constructor(notebook: JSONArray, plan: Plan) : this({ update -> lookAlikes(notebook, plan, update) })

        private val counted = HashMap<Boolean, Int>()

        fun of(update: Boolean): Int = synchronized(counted) { counted.getOrPut(update) { count(update) } }

        /** Both answers' counts, worked out now: called away from the screen. */
        fun warm(plan: Plan): LookAlikes {
            of(false)
            if (plan.differing.isNotEmpty()) of(true)
            return this
        }
    }

    /**
     * Compared by the desktop's duplicate key, never by object: the copies
     * [remaining] makes are new objects every time it is asked.
     */
    private fun notebookKeys(notebook: JSONArray, plan: Plan, update: Boolean): Set<String> {
        val replaced = if (update) plan.differing.map { it.mineIndex }.toSet() else emptySet()
        val keys = HashSet<String>()
        for (i in 0 until notebook.length()) {
            if (i in replaced) continue
            (notebook.opt(i) as? JSONObject)?.let { keys.add(keyOf(it)) }
        }
        if (update) plan.differing.forEach { keys.add(keyOf(it.theirs)) }
        return keys
    }

    private fun keyOf(note: JSONObject): String = Normalize.dupKey(Normalize.normalizeNote(note))

    /** What would be added as new notes before the look-alike question. */
    private fun remaining(plan: Plan, update: Boolean): List<JSONObject> =
        if (update) plan.fresh else plan.fresh + plan.differing.map { withoutIdentity(it.theirs) }

    /** A copy kept separate must not wear the original's identity. */
    private fun withoutIdentity(note: JSONObject): JSONObject {
        val copy = JSONObject(note.toString())
        copy.remove("uid")
        return copy
    }

    /**
     * Writes what the answers agreed to. [attachmentsDir] is this
     * notebook's folder of attached files; [extras] is what a backup
     * carries besides its notes. Every file is written by [Files] or
     * through the notebook's own writer, and each write is whole or not
     * at all.
     *
     * Outside data a reading missed is refused here, not by stopping the
     * app: every write comes after the work, so a failure before them
     * leaves everything as it was.
     */
    fun apply(
        load: Notebook.Load,
        plan: Plan,
        answers: Answers,
        attachmentsDir: File?,
        extras: Extras = Extras(),
    ): Result = try {
        applyWhole(load, plan, answers, attachmentsDir, extras)
    } catch (e: RuntimeException) {
        Result.Failed("The import stopped on something in it the app could not read; your notes are as they were.")
    }

    private fun applyWhole(
        load: Notebook.Load,
        plan: Plan,
        answers: Answers,
        attachmentsDir: File?,
        extras: Extras,
    ): Result {
        val notesFile = load.file ?: return Result.Failed("There is nowhere to keep the notes.")
        val notebook = load.raw
            ?: return Result.Failed("Your notes file could not be read, so nothing will be written over it.")
        val trashFile = load.trashFile
        val trash = load.trashRaw ?: JSONArray()
        val now = Files.now()
        val settings = load.settings

        // 1. The backup's categories, added -- a name already here keeps
        //    its own colour -- and refused whole where the loader would drop
        //    them on the next start.
        val categories = LinkedHashMap(load.categories)
        var addedCategories = 0
        var refusedCategories = 0
        extras.categories?.let { theirs ->
            var seen = 0
            for (key in theirs.keys()) {
                if (seen++ >= Settings.MAX_CATEGORIES) break
                val colour = Notebook.sanitizeColor(theirs.opt(key) as? String ?: "")
                val name = key.trim()
                if (name.isEmpty() || colour.isEmpty() || name in categories) continue
                if (Settings.categoryNameRefusal(name, categories) != null) {
                    refusedCategories += 1
                    continue
                }
                categories[name] = colour
                addedCategories += 1
            }
        }

        // The files already spoken for, by a note or a Trash entry here. A
        // note arriving with one of them gets a copy of its own.
        val claimed = HashSet<String>()
        for (i in 0 until notebook.length()) (notebook.opt(i) as? JSONObject)?.let { claimed += storedNames(it) }
        for (i in 0 until trash.length()) (trash.opt(i) as? JSONObject)?.let { claimed += storedNames(it) }

        val candidate = JSONArray(notebook.toString())

        // 2. The updates agreed to.
        var updated = 0
        var firstId = 0
        if (answers.update) {
            for (known in plan.differing) {
                val mine = candidate.getJSONObject(known.mineIndex)
                val theirs = Normalize.normalizeNote(known.theirs)
                // A category this device does not have files the note under
                // General, as normalize_note files it on the desktop.
                fileUnder(theirs, categories.keys)
                val claimedElsewhere = HashSet(claimed).apply { removeAll(storedNames(mine)) }
                // Counted only when the note changed: "Updated 1" was said
                // of an update that changed nothing.
                var changed = Notebook.applyUpdate(mine, theirs, now, fields(plan.source))
                if (plan.source == Source.BACKUP) {
                    changed = changed || filesToAdopt(mine, known.theirs).isNotEmpty()
                    adoptFiles(mine, known.theirs, claimedElsewhere, attachmentsDir, extras.attachments)
                    claimed += storedNames(mine)
                }
                if (!changed) continue
                updated += 1
                if (firstId == 0) firstId = mine.optInt("id")
            }
        }

        // 3. The new notes: what nothing matched, the changed ones the
        //    person chose to keep separate, minus the look-alikes skipped,
        //    plus the copies of unchanged notes asked for.
        var toAdd = remaining(plan, answers.update)
        var duplicatesSkipped = 0
        if (answers.skipDuplicates) {
            val keys = notebookKeys(notebook, plan, answers.update)
            val kept = toAdd.filter { keyOf(it) !in keys }
            duplicatesSkipped = toAdd.size - kept.size
            toAdd = kept
        }
        val copies = if (answers.copyUnchanged) plan.unchanged.map { withoutIdentity(it.theirs) } else emptyList()
        var nextId = maxId(candidate) + 1
        for (incoming in toAdd + copies) {
            // A backup's note keeps its rescue copy ("↩ Restore previous"),
            // as the desktop's import keeps it through normalize_note:
            // dropped here, a notebook moved to a new phone lost every one.
            // A code never carries one, and one that claims to is not taken.
            val note = Normalize.normalizeNote(incoming)
            if (plan.source != Source.BACKUP) note.remove("_prev")
            fileUnder(note, categories.keys)
            note.put("attachments", placeFiles(note.getJSONArray("attachments"), claimed,
                attachmentsDir, extras.attachments))
            note.put("id", nextId)
            if (firstId == 0) firstId = nextId
            nextId += 1
            if (note.optString("created").isEmpty()) note.put("created", now)
            if (note.optString("updated").isEmpty()) note.put("updated", now)
            candidate.put(note)
        }

        // 4. Templates: one comes in only when no template here has its name.
        var addedTemplates = 0
        var templatesCandidate: JSONArray? = null
        val templatesFile = notesFile.parentFile?.let { File(it, "templates.json") }
        if (extras.templates != null && templatesFile != null) {
            val mine = readArray(templatesFile)
            if (mine != null) {
                val names = (0 until mine.length()).mapNotNull { (mine.opt(it) as? JSONObject)?.opt("name") as? String }.toMutableSet()
                val out = JSONArray(mine.toString())
                for (i in 0 until extras.templates.length()) {
                    // No more than a notebook keeps (Templates.MAX_TEMPLATES).
                    if (out.length() >= Templates.MAX_TEMPLATES) break
                    val tpl = extras.templates.opt(i) as? JSONObject ?: continue
                    val name = (tpl.opt("name") as? String)?.ifEmpty { null } ?: continue
                    if (name in names) continue
                    names.add(name)
                    // Shaped as a template, the way every reader shapes one,
                    // rather than copied as it came through its own text.
                    out.put(Templates.normalize(tpl))
                    addedTemplates += 1
                }
                if (addedTemplates > 0) templatesCandidate = out
            }
        }

        // 5. The backup's Trash: an entry already in this Trash, content
        //    for content, is skipped -- re-importing your own backup must
        //    not double it -- and nobody is asked, so the test is strict.
        var addedTrash = 0
        var trashCandidate: JSONArray? = null
        if (extras.trash != null && trashFile != null && (load.trashRaw != null || !trashFile.exists())) {
            val seen = HashSet<String>()
            for (i in 0 until trash.length()) {
                (trash.opt(i) as? JSONObject)?.let { seen.add(Normalize.contentSignature(Normalize.normalizeNote(it))) }
            }
            val out = JSONArray(trash.toString())
            for (i in 0 until extras.trash.length()) {
                val raw = extras.trash.opt(i) as? JSONObject ?: continue
                val entry = Normalize.normalizeNote(raw)
                entry.put("deleted_at", raw.opt("deleted_at") as? String ?: "")
                if (!seen.add(Normalize.contentSignature(entry))) continue
                entry.put("attachments", placeFiles(entry.getJSONArray("attachments"), claimed,
                    attachmentsDir, extras.attachments))
                out.put(entry)
                addedTrash += 1
            }
            if (addedTrash > 0) trashCandidate = out
        }

        // 6. Painted calendar days: added, a day already decorated here
        //    keeps its own decoration.
        var restoredDays = 0
        val settingsCandidate = settings?.let { JSONObject(it.raw.toString()) }
        if (settingsCandidate != null) {
            if (addedCategories > 0) {
                settingsCandidate.put(Settings.CATEGORIES, Settings.categoriesJson(categories))
            }
            restoredDays += mergeDays(settingsCandidate, "day_colors", extras.dayColors) { value ->
                (value as? String)?.let { Notebook.sanitizeColor(it) }?.ifEmpty { null }
            }
            restoredDays += mergeDays(settingsCandidate, "day_emoji", extras.dayEmoji) { value ->
                cleanDayMarkers(value)?.takeIf { it.length() > 0 }
            }
        }

        // 7. The backup's Trash window, for a notebook that never chose one:
        //    its entries came with the days they were deleted, and the
        //    default 30 emptied, at the next start, what the person had
        //    kept for 60 or 90 days. A window chosen here is kept -- and so
        //    is the default against a shorter one, which this notebook's
        //    own Trash was promised: a backup from a 7-day notebook, with no
        //    Trash in it at all, emptied it at the next start, files and
        //    all. Only a longer window, and only with Trash to keep by it,
        //    as the desktop's import takes it (e69721e).
        val window = extras.trashWindow
        val adoptsWindow = window != null && window > Settings.TRASH_RETENTION_DAYS &&
            (extras.trash?.length() ?: 0) > 0 && settings != null && Settings.windowNeverChosen(settings)
        if (adoptsWindow) settingsCandidate?.put(Settings.RETENTION_DAYS, window)

        // The writes, in the desktop's order: settings, templates, Trash,
        // and the notes last.
        if (settings != null && settingsCandidate != null && (addedCategories > 0 || restoredDays > 0 || adoptsWindow)) {
            Files.writeAtomic(settings.file, settingsCandidate.toString(2))?.let {
                return Result.Failed("settings.json could not be written: $it")
            }
            for (key in settingsCandidate.keys()) settings.raw.put(key, settingsCandidate.opt(key))
        }
        if (templatesCandidate != null && templatesFile != null) {
            Files.writeAtomic(templatesFile, templatesCandidate.toString(2))?.let {
                return Result.Failed("templates.json could not be written: $it")
            }
        }
        if (trashCandidate != null && trashFile != null) {
            Files.writeAtomic(trashFile, trashCandidate.toString(2))?.let {
                return Result.Failed("The Trash could not be written: $it")
            }
            load.trashRaw?.let { live -> replaceContents(live, trashCandidate) }
        }
        val changed = updated > 0 || toAdd.isNotEmpty() || copies.isNotEmpty()
        if (changed) {
            Files.writeAtomic(notesFile, candidate.toString(2))?.let { return Result.Failed(it) }
            replaceContents(notebook, candidate)
        }
        return Result.Done(Outcome(
            added = toAdd.size + copies.size,
            updated = updated,
            unchangedSkipped = plan.unchanged.size - copies.size,
            duplicatesSkipped = duplicatesSkipped,
            addedCategories = addedCategories,
            refusedCategories = refusedCategories,
            restoredDays = restoredDays,
            addedTemplates = addedTemplates,
            addedTrash = addedTrash,
            firstId = firstId,
        ))
    }

    private fun maxId(array: JSONArray): Int {
        var max = 0
        for (i in 0 until array.length()) {
            val id = (array.opt(i) as? JSONObject)?.opt("id")
            if (id is Number && id.toInt() > max) max = id.toInt()
        }
        return max
    }

    private fun replaceContents(live: JSONArray, from: JSONArray) {
        while (live.length() > 0) live.remove(live.length() - 1)
        for (i in 0 until from.length()) live.put(from.get(i))
    }

    private fun readArray(file: File): JSONArray? {
        if (!file.isFile) return JSONArray()
        return try {
            Files.parseOwn(file) { JSONArray(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The files a backup's copy of a note has and this one lacks, added --
     * never swapped -- each with a copy of its own when another note
     * already uses it. The desktop's `_adopt_backup_attachments`.
     */
    private fun adoptFiles(
        mine: JSONObject, theirs: JSONObject, claimedElsewhere: MutableSet<String>,
        attachmentsDir: File?, unpacked: File?,
    ) {
        val list = mine.optJSONArray("attachments") ?: JSONArray().also { mine.put("attachments", it) }
        val have = storedNames(mine).toMutableSet()
        for (att in filesToAdopt(mine, theirs)) {
            if (list.length() >= Normalize.MAX_ATTACHMENTS) break
            val placed = placeFile(att, claimedElsewhere, attachmentsDir, unpacked) ?: continue
            if (placed.optString("stored_as") in have) continue
            list.put(placed)
            have.add(placed.optString("stored_as"))
        }
    }

    private fun placeFiles(
        records: JSONArray, claimed: MutableSet<String>, attachmentsDir: File?, unpacked: File?,
    ): JSONArray {
        val out = JSONArray()
        for (i in 0 until records.length()) {
            val att = records.optJSONObject(i) ?: continue
            out.put(placeFile(att, claimed, attachmentsDir, unpacked) ?: att)
        }
        return out
    }

    /**
     * One attachment record, made safe to keep: its file brought from the
     * unpacked backup when this device does not hold it yet, and a copy of
     * its own when a note here already uses it. Null when a copy was
     * needed and could not be made -- the record then keeps its name, as
     * the desktop's does.
     */
    private fun placeFile(
        att: JSONObject, claimed: MutableSet<String>, attachmentsDir: File?, unpacked: File?,
    ): JSONObject? {
        val name = Normalize.bareFileName(att.optString("stored_as"))
        if (name.isEmpty() || attachmentsDir == null) return att
        val here = File(attachmentsDir, name)
        if (!here.exists() && unpacked != null) {
            val from = File(unpacked, name)
            if (from.isFile) copyWhole(from, here)
        }
        if (name !in claimed) {
            claimed.add(name)
            return att
        }
        val copy = copyAttachment(attachmentsDir, name) ?: return null
        claimed.add(copy)
        return JSONObject(att.toString()).put("stored_as", copy)
    }

    /**
     * The desktop's `copy_attachment_file`: the same file under a fresh,
     * unique name, or null when the source is missing or the copy fails.
     */
    fun copyAttachment(attachmentsDir: File, storedAs: String): String? {
        val base = Normalize.bareFileName(storedAs)
        val source = File(attachmentsDir, base)
        if (base.isEmpty() || !source.isFile) return null
        val stamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date()) + "000"
        var name = "${stamp}_$base"
        var n = 1
        while (File(attachmentsDir, name).exists()) {
            name = "${stamp}_${n}_$base"
            n += 1
        }
        return if (copyWhole(source, File(attachmentsDir, name))) name else null
    }

    /** Copied beside and renamed into place, so no half file ever has the name. */
    private fun copyWhole(from: File, to: File): Boolean {
        val part = File(to.parentFile, to.name + ".part")
        return try {
            to.parentFile?.mkdirs()
            from.inputStream().use { input -> part.outputStream().use { input.copyTo(it, 1 shl 20) } }
            if (part.renameTo(to)) true else { part.delete(); false }
        } catch (e: Exception) {
            part.delete()
            false
        }
    }

    private fun mergeDays(
        settings: JSONObject, key: String, theirs: JSONObject?, clean: (Any?) -> Any?,
    ): Int {
        if (theirs == null) return 0
        val mine = settings.opt(key) as? JSONObject ?: JSONObject()
        var added = 0
        for (day in theirs.keys()) {
            // Real days only, and no more than the calendar keeps: the
            // desktop's _take_day_decorations. 700,000 keys that were no
            // dates came in whole, read again at every month drawn.
            if (mine.length() >= Days.MAX_DAY_DECORATIONS) break
            if (mine.has(day) || !Days.isCalendarDay(day)) continue
            val value = clean(theirs.opt(day)) ?: continue
            mine.put(day, value)
            added += 1
        }
        if (added > 0) settings.put(key, mine)
        return added
    }

    /**
     * A day's markers as kept: up to three non-empty strings, without the
     * emoji variation selector. One string on its own is the older format.
     */
    fun cleanDayMarkers(value: Any?): JSONArray? =
        // Each an emoji's length, as the calendar keeps them (Days.cleanMarkers).
        if (value is String || value is JSONArray) JSONArray(Days.cleanMarkers(value)) else null
}
