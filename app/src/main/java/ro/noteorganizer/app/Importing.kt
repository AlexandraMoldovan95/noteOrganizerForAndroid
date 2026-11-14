package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bringing notes in, from a share code or a backup, by the desktop's rules.
 * [plan] writes nothing: it sorts what arrived into notes here that come
 * changed (update them, or add beside them; a backup's older copy is said),
 * notes that come as they are (skip, or copy), and new notes that look like
 * one here (`_dup_key`: import or skip). Every answer comes first; [apply]
 * then writes as `import_data` does: a backup's categories, painted days,
 * templates and Trash join what is here, and a file another note uses gets
 * a copy of its own, so deleting one note never takes another's file. Both
 * routes share this one planner, as on the desktop: two copies of one
 * judgement is how its search rules once drifted apart.
 */
object Importing {

    enum class Source(val word: String) { CODE("code"), BACKUP("backup") }

    /**
     * A note this device has, known by the identity both copies wear
     * ([uid], trimmed), and the copy that arrived for it. Never by its
     * place in the list: a plan is made away from the screen while the list
     * can still change, and a note sent to the Trash meanwhile moved every
     * note after it up one -- Update then rewrote a different note with the
     * arriving copy, and left the one it was for as it was.
     */
    data class Known(val uid: String, val theirs: JSONObject)

    /**
     * Where each identity first stands in [notebook]: how the plan matched
     * an arriving note, and how [apply] finds it again in the list as it is
     * when the answers are written.
     */
    private fun positions(notebook: JSONArray): Map<String, Int> {
        val byUid = HashMap<String, Int>()
        for (i in 0 until notebook.length()) {
            val uid = ((notebook.opt(i) as? JSONObject)?.opt("uid") as? String)?.let { PyText.strip(it) }
            if (!uid.isNullOrEmpty()) byUid.putIfAbsent(uid, i)
        }
        return byUid
    }

    // Stripped as normalize_note strips it (Python's spaces), or a uid kept
    // with a U+0085 by one program was another note to the other.
    private fun uidOf(note: JSONObject): String? = (note.opt("uid") as? String)?.let { PyText.strip(it) }?.ifEmpty { null }

    data class Plan(
        val source: Source,
        /** How many notes arrived, in all. */
        val total: Int,
        val differing: List<Known>,
        val unchanged: List<Known>,
        /** Notes nothing here matches by identity. */
        val fresh: List<JSONObject>,
        /** How many of [differing] are older than the copy here (backups), files alone not counted. */
        val older: Int,
        /**
         * How many of [differing] are notes here holding a rescue copy they
         * still need ([Notebook.holdsUnspentCopy]): a note has one, and an
         * update's takes its place, so the question says so.
         */
        val rescued: Int = 0,
        /**
         * How many of [differing] differ by attached files alone (backups):
         * Update adds the files and keeps no copy, so no Restore previous is
         * promised of them, as the desktop's question says.
         */
        val filed: Int = 0,
        /**
         * How many of [unchanged] hold fields a newer version wrote that the
         * note here lacks: "nothing this device lacks" was untrue of them.
         */
        val newer: Int = 0,
        /** The names the notes were compared under, or null for the notes as they came. */
        val categories: Set<String>? = null,
        /** The notes as they arrived, in the file's order: the order names are adopted in. */
        val arriving: List<JSONObject> = emptyList(),
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
        /** Notes '(earlier text)' made of a series' copy as it was read. */
        val earlierText: Int = 0,
        /** Notes here that only took files the backup had and this device lacked: no rescue copy undoes them. */
        val filesOnly: Int = 0,
        /** The backup's trashed notes left in it, past the [keepDays] this phone's next start purges by. */
        val trashLeftOut: Int = 0,
        val keepDays: Int? = null,
    )

    sealed class Result {
        data class Done(val outcome: Outcome) : Result()
        data class Failed(val reason: String) : Result()
    }

    private fun fields(source: Source) =
        if (source == Source.BACKUP) Notebook.UPDATABLE_FROM_BACKUP else Notebook.UPDATABLE_FROM_SHARED

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

    /**
     * Whether Update is the answer that stands out in [plan]'s question: not
     * when a backup's versions are older than the notes here, where it wrote
     * them over the newer ones; adding loses nothing.
     */
    fun updateStandsOut(plan: Plan): Boolean = plan.older == 0

    /**
     * Asked of no file: what arrived, sorted into the three kinds.
     * [categories] are the ones the notebook will have; a note filed under
     * one it will not have is compared as the General note the import makes
     * it, as normalize_note does. Compared as it came, an unchanged note read
     * as "edited elsewhere", and Update changed nothing but its rescue copy.
     */
    fun plan(notebook: JSONArray, incoming: List<JSONObject>, source: Source,
             categories: Set<String>? = null): Plan {
        val asHere: (JSONObject) -> JSONObject = { note ->
            if (categories == null || !needsFiling(note, categories)) note
            else fileUnder(JSONObject(note.toString()), categories)
        }
        val byUid = positions(notebook)
        // Each identity goes to the first note arriving with it; a second comes
        // in as a note of its own. Both kept it, one note was updated twice
        // (the person's words in no copy), or two notes wore it, files crossed.
        val claimed = HashSet<String>()

        val differing = ArrayList<Known>()
        val unchanged = ArrayList<Known>()
        val fresh = ArrayList<JSONObject>()
        var older = 0
        var rescued = 0
        var filed = 0
        var newer = 0
        for (theirs in incoming) {
            val uid = uidOf(theirs)
            if (uid != null && !claimed.add(uid)) {
                fresh.add(withoutIdentity(theirs))
                continue
            }
            val at = uid?.let { byUid[it] }
            if (uid == null || at == null) {
                fresh.add(theirs)
                continue
            }
            val mine = notebook.getJSONObject(at)
            val known = Known(uid, theirs)
            val here = asHere(mine)
            val there = asHere(theirs)
            // Whether bringing theirs over mine would change anything: a field
            // the route may replace, or -- from a backup, whose files are
            // added and never swapped -- a file this device's copy lacks.
            val fieldsChange = Notebook.sharedUpdateChanges(here, there, fields(source))
            if (fieldsChange || (source == Source.BACKUP && filesToAdopt(here, there).isNotEmpty())) {
                differing.add(known)
                // Files alone trade no words: not OLDER, and no copy made
                // (Notebook.applyUpdate), so no rescue copy replaced either.
                if (!fieldsChange) filed += 1
                if (fieldsChange && source == Source.BACKUP &&
                    (theirs.opt("updated") as? String ?: "") < (mine.opt("updated") as? String ?: "")
                ) older += 1
                if (fieldsChange && Notebook.holdsUnspentCopy(mine)) rescued += 1
            } else {
                unchanged.add(known)
                if (unknownFieldsLacked(there, here)) newer += 1
            }
        }
        return Plan(source, incoming.size, differing, unchanged, fresh, older, rescued, filed, newer, categories, incoming)
    }

    /**
     * Whether [theirs] holds a field this version does not know that [mine]
     * lacks or holds otherwise, kinds told apart: the desktop's
     * `unknown_fields_lacked`. A share code holds none.
     */
    internal fun unknownFieldsLacked(theirs: JSONObject, mine: JSONObject): Boolean {
        val held = Normalize.unknownFields(mine)
        val brought = Normalize.unknownFields(theirs)
        return brought.keys().asSequence().any { !held.has(it) || !Notebook.sameJson(held.opt(it), brought.opt(it)) }
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
     * Whether the import goes on past its look-alikes -- an Update agreed
     * to, unchanged notes kept as copies -- so the question about them,
     * when every new note is one, offers to skip them: Cancel was its only
     * no, and dropped those answers unsaid, as the desktop's did.
     */
    fun goesOnPastLookAlikes(plan: Plan, answers: Answers): Boolean =
        (answers.update && plan.differing.isNotEmpty()) || (answers.copyUnchanged && plan.unchanged.isNotEmpty())

    /**
     * How many of the notes left to import as new look identical to a
     * note the notebook will hold -- the notebook as the updates will
     * leave it: the third question, once the first is answered.
     */
    fun lookAlikes(notebook: JSONArray, plan: Plan, update: Boolean): Int {
        val keys = notebookKeys(notebook, plan, update)
        return remaining(plan, update).count { keyOf(it, plan.categories) in keys }
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
        // The notes the updates replace, found by identity in THIS list.
        val replaced = if (update) positions(notebook).let { at -> plan.differing.mapNotNull { at[it.uid] }.toSet() }
                       else emptySet()
        val keys = HashSet<String>()
        for (i in 0 until notebook.length()) {
            if (i in replaced) continue
            (notebook.opt(i) as? JSONObject)?.let { keys.add(keyOf(it, plan.categories)) }
        }
        if (update) plan.differing.forEach { keys.add(keyOf(it.theirs, plan.categories)) }
        return keys
    }

    /** The duplicate key of [note] as it will be kept: under General where [categories] lack its name. */
    private fun keyOf(note: JSONObject, categories: Set<String>?): String = Normalize.dupKey(Normalize.normalizeNote(
        if (categories == null || !needsFiling(note, categories)) note else fileUnder(JSONObject(note.toString()), categories)))

    /**
     * [plan] against the list as it is now ([at], from [positions]). A note
     * it matched that has gone since, to the Trash while the plan was made,
     * arrives as a new note, as a fresh plan would sort it. A note planned as
     * new whose identity a note here wears now, restored meanwhile, comes in
     * as a note of its own: added wearing it, two notes held one identity.
     */
    private fun stillMatched(plan: Plan, at: Map<String, Int>): Plan {
        val gone = (plan.differing + plan.unchanged).filter { it.uid !in at }.toSet()
        val back = plan.fresh.filter { note -> uidOf(note)?.let { it in at } == true }.toSet()
        if (gone.isEmpty() && back.isEmpty()) return plan
        return plan.copy(
            differing = plan.differing.filter { it !in gone },
            unchanged = plan.unchanged.filter { it !in gone },
            fresh = plan.fresh.map { if (it in back) withoutIdentity(it) else it } + gone.map { it.theirs },
        )
    }

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
     * Writes what the answers agreed to. [attachmentsDir] is this notebook's
     * folder of attached files; [extras] is what a backup carries besides its
     * notes. Every file is written by [Files] or the notebook's own writer,
     * whole or not at all, and only after the work: outside data a reading
     * missed is refused here, not by a crash, and leaves everything as it was.
     */
    fun apply(
        load: Notebook.Load,
        plan: Plan,
        answers: Answers,
        attachmentsDir: File?,
        extras: Extras = Extras(),
    ): Result {
        // The files here before the import: whatever stops it, a write or a
        // thing it could not read, takes back only what it brought ([dropBrought]).
        val filesBefore = attachmentsDir?.list()?.toSet().orEmpty()
        return try {
            applyWhole(load, plan, answers, attachmentsDir, extras, filesBefore)
        } catch (e: Exception) {
            // Exception, not RuntimeException: Android's JSONException is
            // neither, where the tests' org.json makes it one.
            load.raw?.let { notes -> dropBrought(attachmentsDir, filesBefore, notes, load.trashRaw ?: JSONArray()) }
            Result.Failed("The import stopped on something in it the app could not read; your notes are as they were.")
        }
    }

    /** The map an import files by, how many names it adds, and those it refuses. */
    internal data class Merged(val map: LinkedHashMap<String, String>, val added: Int, val refused: MutableSet<String>)

    /**
     * The categories after an import: this notebook's, the backup's settings' added
     * (a name here keeps its colour; one the loader would drop refused whole), then
     * those [arriving] notes are filed under where those settings lack them, as the
     * desktop's adopt_categories_of. The plan compares by its names and apply files
     * by them: planned with this notebook's alone, a note refiled there under General
     * read as unchanged, was skipped, and its category came back empty.
     */
    internal fun merged(here: Map<String, String>, theirs: JSONObject?, arriving: List<JSONObject>): Merged {
        val categories = LinkedHashMap(here)
        var added = 0
        val refused = LinkedHashSet<String>()
        theirs?.let {
            var seen = 0
            for (key in it.keys()) {
                if (seen++ >= Settings.MAX_CATEGORIES) break
                val colour = Notebook.sanitizeColor(it.opt(key) as? String ?: "")
                val name = PyText.strip(key)
                if (name.isEmpty() || colour.isEmpty() || name in categories) continue
                if (Settings.categoryNameRefusal(name, categories) != null) {
                    refused.add(name)
                    continue
                }
                categories[name] = colour
                added += 1
            }
        }
        if (arriving.isNotEmpty()) {
            added += Settings.adoptCategoriesOf(categories, JSONArray().apply { arriving.forEach { put(it) } }, refused)
        }
        return Merged(categories, added, refused)
    }

    private fun applyWhole(
        load: Notebook.Load,
        planned: Plan,
        answers: Answers,
        attachmentsDir: File?,
        extras: Extras,
        filesBefore: Set<String>,
    ): Result {
        val notesFile = load.file ?: return Result.Failed("There is nowhere to keep the notes.")
        val notebook = load.raw
            ?: return Result.Failed("Your notes file could not be read, so nothing will be written over it.")
        val trashFile = load.trashFile
        val trash = load.trashRaw ?: JSONArray()
        val now = Files.now()
        val settings = load.settings

        // 1. The categories, as the plan was made with them ([merged]).
        // In the file's order, as the plan was made: at the ceiling another order kept other names.
        val merged = merged(load.categories, extras.categories,
            if (planned.source == Source.BACKUP) planned.arriving else emptyList())
        val categories = merged.map
        var addedCategories = merged.added
        val refusedNames = merged.refused

        // The files already spoken for, by a note or a Trash entry here. A
        // note arriving with one of them gets a copy of its own.
        val claimed = HashSet<String>()
        for (i in 0 until notebook.length()) (notebook.opt(i) as? JSONObject)?.let { claimed += storedNames(it) }
        for (i in 0 until trash.length()) (trash.opt(i) as? JSONObject)?.let { claimed += storedNames(it) }

        val candidate = JSONArray(notebook.toString())
        // Each note the plan matched, found again by its identity in the
        // list as it is now: the plan was made away from the screen, against
        // the list as it was then.
        val at = positions(candidate)
        val plan = stillMatched(planned, at)

        // 2. The updates agreed to.
        var updated = 0
        var filesOnly = 0
        var firstId = 0
        if (answers.update) {
            for (known in plan.differing) {
                val mine = candidate.getJSONObject(at.getValue(known.uid))
                val theirs = Normalize.normalizeNote(known.theirs)
                // A category this device does not have files the note under
                // General, as normalize_note files it on the desktop.
                fileUnder(theirs, categories.keys)
                val claimedElsewhere = HashSet(claimed).apply { removeAll(storedNames(mine)) }
                // Counted only when the note changed: "Updated 1" was said
                // of an update that changed nothing.
                val changed = Notebook.applyUpdate(mine, theirs, now, fields(plan.source))
                var gotFiles = false
                if (plan.source == Source.BACKUP) {
                    gotFiles = filesToAdopt(mine, known.theirs).isNotEmpty()
                    adoptFiles(mine, known.theirs, claimedElsewhere, attachmentsDir, extras.attachments)
                    claimed += storedNames(mine)
                }
                // Files alone make no rescue copy, so Restore previous does
                // not undo them: they are counted apart, not as "Updated".
                if (!changed) {
                    if (gotFiles) filesOnly += 1
                    continue
                }
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
            val kept = toAdd.filter { keyOf(it, plan.categories) !in keys }
            duplicatesSkipped = toAdd.size - kept.size
            toAdd = kept
        }
        val copies = if (answers.copyUnchanged) plan.unchanged.map { withoutIdentity(it.theirs) } else emptyList()
        val newIds = Organizing.ids(candidate)
        for (incoming in toAdd + copies) {
            // A backup's note keeps its rescue copy ("↩ Restore previous"), as
            // normalize_note does; dropped, a notebook moved to a new phone lost
            // every one. A code never carries one, so one claiming to is dropped.
            val note = Normalize.normalizeNote(incoming)
            if (plan.source != Source.BACKUP) note.remove("_prev")
            fileUnder(note, categories.keys)
            note.put("attachments", placeFiles(note.getJSONArray("attachments"), claimed,
                attachmentsDir, extras.attachments))
            val nextId = newIds.next()
            note.put("id", nextId)
            if (firstId == 0) firstId = nextId
            // In normalize_note's order: a note with no "created" was made
            // when it was last changed, not when it was imported.
            if (note.optString("updated").isEmpty()) note.put("updated", now)
            if (note.optString("created").isEmpty()) note.put("created", note.optString("updated"))
            candidate.put(note)
        }

        // 4. Templates: one comes in only when no template here has its name.
        /** Why a held templates or Trash file stops the import, or null. */
        var heldBy: String? = null
        var addedTemplates = 0
        var templatesCandidate: JSONArray? = null
        val templatesFile = notesFile.parentFile?.let { File(it, "templates.json") }
        if (extras.templates != null && templatesFile != null) {
            // One here that does not read is copied aside, as every reader does,
            // and the backup's come in over it, as into the desktop's empty list.
            val mine = if (Files.writesHeld(templatesFile)) null
                       else readArray(templatesFile) ?: JSONArray().takeIf {
                           !Files.writesHeld(templatesFile) && Files.preserveCorrupt(templatesFile) != null }
            if (mine != null) {
                // Shaped as the backup's are, as the desktop names its own list:
                // one here with no name is "Untitled template" too; an entry that
                // is no template at all names nothing, as the desktop drops it.
                val names = (0 until mine.length()).mapNotNull { i ->
                    (mine.opt(i) as? JSONObject)?.let { Templates.normalize(it).optString("name") } }.toMutableSet()
                val out = JSONArray(mine.toString())
                for (i in 0 until extras.templates.length()) {
                    // No more than a notebook keeps (Templates.MAX_TEMPLATES).
                    if (out.length() >= Templates.MAX_TEMPLATES) break
                    val tpl = extras.templates.opt(i) as? JSONObject ?: continue
                    // Shaped first, the way every reader shapes one, then named:
                    // one with no name comes in "Untitled template", as the desktop's.
                    val shaped = Templates.normalize(tpl)
                    val name = shaped.optString("name")
                    if (name in names) continue
                    names.add(name)
                    out.put(shaped)
                    addedTemplates += 1
                }
                if (addedTemplates > 0) templatesCandidate = out
            } else if (extras.templates.length() > 0) {
                // Held (neither read nor copied aside): said where the import
                // stops, as every refused write is, not skipped without a word.
                heldBy = heldBy ?: Files.heldReason(templatesFile)
            }
        }

        // 7, worked out first. The backup's Trash window: only a longer one than
        //    the default, only with Trash to keep by it, and only where none was
        //    chosen here (smaller lessons: the backup's Trash window).
        val window = extras.trashWindow
        val adoptsWindow = window != null && window > Settings.TRASH_RETENTION_DAYS &&
            (extras.trash?.length() ?: 0) > 0 && settings != null && Settings.windowNeverChosen(settings)
        // The length this phone's next start purges by -- the backup's where it is
        // taken, this phone's own (none while unknown here) -- keeps or leaves out
        // each entry: past it, it came in to be removed with its files (the desktop's).
        val keepDays = if (adoptsWindow) window else settings?.let { Settings.purgeWindow(it) }
        var trashLeftOut = 0
        // 5. The backup's Trash: an entry already in this Trash, content
        //    for content, is skipped -- re-importing your own backup must
        //    not double it -- and nobody is asked, so the test is strict.
        var addedTrash = 0
        var trashCandidate: JSONArray? = null
        if (extras.trash != null && trashFile != null && !Files.writesHeld(trashFile) &&
            (load.trashRaw != null || !trashFile.exists())) {
            val seen = HashSet<String>()
            for (i in 0 until trash.length()) {
                (trash.opt(i) as? JSONObject)?.let { seen.add(Normalize.contentSignature(Normalize.normalizeNote(it))) }
            }
            val out = JSONArray(trash.toString())
            fun shaped(raw: JSONObject) = Normalize.normalizeNote(raw).put("deleted_at", raw.opt("deleted_at") as? String ?: "")
            val incoming = (0 until extras.trash.length()).mapNotNull { extras.trash.opt(it) as? JSONObject }
            // The categories of the entries the window keeps, adopted before any is signed:
            // a twin signed before its name was known read as General, and both came in.
            addedCategories += Settings.adoptCategoriesOf(categories, JSONArray().apply {
                incoming.filter { keepDays == null || !Organizing.isPast(shaped(it), keepDays) }.forEach { put(it) }
            }, refusedNames)
            for (raw in incoming) {
                // One refused stays out of the map, under General, as normalize_note files it.
                val entry = fileUnder(shaped(raw), categories.keys)
                val signature = Normalize.contentSignature(entry)
                if (signature in seen) continue
                if (keepDays != null && Organizing.isPast(entry, keepDays)) {
                    trashLeftOut += 1
                    continue
                }
                // Only one brought in is a twin's original: an older one left out
                // took its newer twin, unsaid, with it.
                seen.add(signature)
                entry.put("attachments", placeFiles(entry.getJSONArray("attachments"), claimed,
                    attachmentsDir, extras.attachments))
                out.put(entry)
                addedTrash += 1
            }
            if (addedTrash > 0) trashCandidate = out
        } else if (extras.trash != null && extras.trash.length() > 0 && trashFile != null && trashFile.exists()) {
            heldBy = heldBy ?: Files.heldReason(trashFile)
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

        // 7. The window, worked out above, kept with the settings.
        if (adoptsWindow) settingsCandidate?.put(Settings.RETENTION_DAYS, window)
        // Over held settings (their marks in memory) the length the next start reads
        // is not known here: brought in, the next start's 30 days, or the real file's
        // own, removed entries with their files. Stopped whole, as over a held Trash.
        if (trashCandidate != null && settings != null && Files.writesHeld(settings.file)) {
            heldBy = heldBy ?: Files.heldReason(settings.file)
        }

        val changed = updated > 0 || filesOnly > 0 || toAdd.isNotEmpty() || copies.isNotEmpty()
        // Days a series' copy set aside become notes of their own, and are
        // said; worked out before the first write, as all the work is, so a
        // failure in it leaves nothing written.
        val earlierText = if (changed) Notebook.settleAside(candidate) else 0

        // The writes, in the desktop's order: settings, templates, Trash, the
        // notes last. One that fails takes back the files nothing uses, and
        // names each part written before it.
        val kept = mutableListOf<String>()
        var trashNow = trash
        fun stopped(problem: String): Result {
            dropBrought(attachmentsDir, filesBefore, notebook, trashNow)
            val parts = if (kept.size < 2) kept.joinToString() else
                kept.dropLast(1).joinToString(", ") + " and " + kept.last()
            val verb = if (kept.size == 1 && kept[0].startsWith("Trash")) "was" else "were"
            return Result.Failed(problem + " None of the ${plan.source.word}'s notes came in" +
                (if (kept.isEmpty()) "." else "; its $parts $verb kept, and importing it again adds only the rest."))
        }
        // A templates or Trash file held stops it before anything is written.
        heldBy?.let { return stopped("$it.") }
        if (settings != null && settingsCandidate != null && (addedCategories > 0 || restoredDays > 0 || adoptsWindow)) {
            Files.writeAtomic(settings.file, settingsCandidate.toString(2))?.let {
                return stopped("settings.json could not be written: $it.")
            }
            for (key in settingsCandidate.keys()) settings.raw.put(key, settingsCandidate.opt(key))
            if (addedCategories > 0) kept += "categories"
            if (restoredDays > 0) kept += "calendar markings"
            if (adoptsWindow) kept += "Trash window"
        }
        if (templatesCandidate != null && templatesFile != null) {
            Files.writeAtomic(templatesFile, templatesCandidate.toString(2))?.let {
                return stopped("templates.json could not be written: $it.")
            }
            kept += "templates"
        }
        if (trashCandidate != null && trashFile != null) {
            Files.writeAtomic(trashFile, trashCandidate.toString(2))?.let {
                return stopped("The Trash could not be written: $it.")
            }
            load.trashRaw?.let { live -> replaceContents(live, trashCandidate) }
            trashNow = trashCandidate
            kept += "Trash"
        }
        if (changed) {
            Files.writeAtomic(notesFile, candidate.toString(2))?.let {
                return stopped("Your notes could not be written: $it.")
            }
            replaceContents(notebook, candidate)
        }
        return Result.Done(Outcome(
            added = toAdd.size + copies.size,
            updated = updated,
            unchangedSkipped = plan.unchanged.size - copies.size,
            duplicatesSkipped = duplicatesSkipped,
            addedCategories = addedCategories,
            refusedCategories = refusedNames.size,
            restoredDays = restoredDays,
            addedTemplates = addedTemplates,
            addedTrash = addedTrash,
            firstId = firstId,
            earlierText = earlierText,
            filesOnly = filesOnly,
            trashLeftOut = trashLeftOut,
            keepDays = keepDays,
        ))
    }

    /**
     * The files an import brought into [attachmentsDir] that no note and no
     * Trash entry uses, after a write that failed: the desktop's
     * `_drop_unclaimed_import_files`. A file here before it is never touched.
     */
    private fun dropBrought(attachmentsDir: File?, before: Set<String>, notes: JSONArray, trash: JSONArray) {
        val dir = attachmentsDir ?: return
        val used = HashSet<String>()
        for (list in listOf(notes, trash)) {
            for (i in 0 until list.length()) (list.opt(i) as? JSONObject)?.let { used += storedNames(it) }
        }
        for (name in dir.list().orEmpty()) {
            if (name !in before && name !in used) File(dir, name).delete()
        }
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
            // Refused, not damaged: held, so no copy aside takes a good file and
            // the backup's templates are not written over it (Files.refusedRead).
            if (Files.refusedRead(e)) Files.holdWrites(file)
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
            // Moved, not copied: the unpacked backup shares the app's disk, so
            // a move costs nothing; copied, a notebook of photos held the screen
            // still for seconds. Copied only where a move cannot be made.
            if (from.isFile) {
                here.parentFile?.mkdirs()
                if (!from.renameTo(here)) copyWhole(from, here)
            }
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
        // Within what the phone's storage holds, the stamp included.
        var name = Backup.fittedPlace("${stamp}_$base")
        var n = 1
        while (File(attachmentsDir, name).exists()) {
            name = Backup.fittedPlace("${stamp}_${n}_$base")
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
