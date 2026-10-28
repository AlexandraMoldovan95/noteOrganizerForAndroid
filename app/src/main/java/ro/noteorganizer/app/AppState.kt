package ro.noteorganizer.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Where the app is: the desktop's two tabs, and its Settings. */
enum class Tab { NOTES, CALENDAR, SETTINGS }

/** One page of Settings, or the list of them. LICENSES opens from About, and goes back there. */
enum class SettingsPage { NAME, APPEARANCE, CATEGORIES, TEMPLATES, REMINDERS, BACKUP, TRASH, DUPLICATES, STATS, ABOUT, LICENSES }

/**
 * The app's state, and every action a screen can take on the notebook.
 *
 * One place, because every screen shares the same notebook and the same
 * rule for changing it: the write goes through the rules layer, which
 * writes a candidate whole before anything in memory changes; on success
 * the screen is refreshed from what was written, and on failure it is
 * told why, never left showing a change the file does not have.
 *
 * Holds Compose state, so it lives on the drawing side of the line the
 * layering gate keeps.
 */
class AppState(val dir: File, val scope: CoroutineScope) {

    val attachments: File = File(dir, "attachments").apply { mkdirs() }

    // Never "equal": a write changes the settings INSIDE the object it read,
    // so the refreshed notebook compares equal to the old one, Compose
    // skipped the redraw, and the screen went on showing the setting as it
    // was -- found on the emulator, where a switch stayed on over a file
    // that said off. Every refresh is a redraw.
    var load by mutableStateOf(Notebook.load(dir), neverEqualPolicy())
        private set

    /**
     * Counts the notebook's refreshes: the key for anything worked out from
     * it once per change -- a month's days, the History -- rather than asked
     * whether two whole notebooks are equal at every redraw.
     */
    var revision by mutableIntStateOf(0)
        private set

    var tab by mutableStateOf(Tab.NOTES)
    var settingsPage by mutableStateOf<SettingsPage?>(null)

    /** The note open in the editor; null for none, 0 for one not saved yet. */
    var openId by mutableStateOf<Int?>(null)

    /**
     * On a wide screen, the list (or the calendar) put aside for a wider
     * note -- remembered until the app is closed, the owner's choice. With
     * no note open it shows all the same: there would be nothing else.
     */
    var listAside by mutableStateOf(false)

    /**
     * Counts the notes opened: each gets an editor of its own, scrolled to
     * its top, its sheets shut. On a wide screen the editor stays on screen
     * from one note to the next, and a second new note is "note 0" again,
     * like the first.
     */
    var opening by mutableIntStateOf(0)
        private set

    /**
     * The one day of a repeating note the editor is open on -- the
     * desktop's day mode -- or null for the note as a whole.
     */
    var openDay by mutableStateOf<String?>(null)
        private set

    /** The date a new note starts with: the calendar day it was added on. */
    var newNoteDate: String = ""
        private set

    // ---- the calendar ----

    /**
     * The month the calendar shows, and the day whose list is open. The
     * month stays between January of year 1 and December 9999, the years a
     * date is written in, as the desktop's `shift_calendar` keeps it: past
     * 9999 a day's key no longer read back as a date, and the app stopped.
     */
    private var month by mutableStateOf(java.time.YearMonth.now())
    var calendarMonth: java.time.YearMonth
        get() = month
        set(value) { month = value.coerceIn(java.time.YearMonth.of(1, 1), java.time.YearMonth.of(9999, 12)) }
    var selectedDay by mutableStateOf(Recurrence.formatDate(java.time.LocalDate.now()))

    /** The History in place of the month, as the desktop's toggle swaps them. */
    var showHistory by mutableStateOf(false)

    /**
     * A note's sections folded away to make room for the others -- its text,
     * checklist, comments -- keyed "<note id>:<section>". Kept while the app
     * runs, not in the notebook: how a note is looked at is not the note.
     */
    val folded = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    // ---- the day's reminder ([Reminders]) ----

    /** How many notes today's bar says, while it shows; null when it does not. */
    var reminderDue by mutableStateOf<Int?>(null)

    /** Moves when today is asked for -- the bar tapped, the notification -- for the calendar to show it. */
    var todayAsked by mutableIntStateOf(0)

    /**
     * Today as the screens show it -- the calendar's ring, "Calendar:
     * soonest", the bar's day. Read once, it stayed on the day the app was
     * opened for as long as it stayed open; [dayIs] moves it.
     */
    var today by mutableStateOf(Recurrence.formatDate(java.time.LocalDate.now()))
        private set

    /**
     * The date now, at a return and at midnight with the app open: when it
     * has turned, a bar for the earlier day goes and the new day's is asked
     * for, as the desktop's `_watch_the_day` does it once a minute.
     */
    fun dayIs(now: String) {
        if (now != today) {
            today = now
            reminderDue = null
        }
        checkReminder(today)
    }

    /**
     * The bar, once a day: shown when notes fall on [today] and it was not
     * shown yet that day -- marked as shown when it shows, as the desktop
     * marks it, so a second start that day says nothing. A mark that could
     * not be written still shows the bar; it may show once more.
     */
    fun checkReminder(today: String) {
        val due = Reminders.dueOn(rawNotes(), today)
        if (!Reminders.barDue(settings, today, due)) return
        load.settings?.let { Settings.put(it, Reminders.LAST_SHOWN, today) }
        reminderDue = due
    }

    /** The calendar on [today], the bar gone: what the bar and the notification open. */
    fun showToday(today: String) {
        Recurrence.parseDate(today)?.let { calendarMonth = java.time.YearMonth.from(it) }
        selectedDay = today
        showHistory = false
        reminderDue = null
        // Reminded already, by the notification: the bar does not say it again.
        if (settings.opt(Reminders.LAST_SHOWN) != today) load.settings?.let { Settings.put(it, Reminders.LAST_SHOWN, today) }
    }

    /**
     * The find bar's words and its "Aa", kept from note to note while the
     * app is open: the bar closes with a note, and opens on the next one
     * with the same search, as the desktop's does.
     */
    var findQuery by mutableStateOf("")
    var findMatchCase by mutableStateOf(false)

    /**
     * The last copy made from a note, kept while the app is open: pasted
     * back into a note, it brings its sizes and faces too, which the HTML
     * on the clipboard cannot carry -- the desktop's `_formatted_clip`.
     */
    var ownCopy: Pasting.OwnCopy? = null

    /** A share code arriving from another app, waiting for the import panel. */
    var incomingCode by mutableStateOf<String?>(null)

    /** A line said once at the top of the list (a load problem, a purge). */
    var notice by mutableStateOf<String?>(null)

    val snackbar = SnackbarHostState()

    /** Android's share sheet, set by the activity: text out to any app. */
    var sendText: ((String) -> Unit)? = null

    /** Opens an attached file with the phone's own apps, set by the activity. */
    var openFile: ((File, String) -> Unit)? = null

    // Android's pickers for one note, set by AppRoot: there the answer
    // arrives even when Android ended the app behind the picker and started
    // it again. Held by the editor, it found nobody -- an exported file left
    // empty, files not attached, and nothing said.

    /** Export as file ([rememberExporter]). */
    var exporter: Exporter? = null

    /** Android's file picker, for the note [id] (0: a new note, saved when files are picked). */
    var pickAttachments: ((id: Int) -> Unit)? = null

    /**
     * The open editor's way with picked files, while it is open: a new note
     * saved first, a full one asked about. False when the files are not for
     * its note -- then AppRoot attaches them itself.
     */
    var attachHere: ((id: Int, files: List<android.net.Uri>) -> Boolean)? = null

    /**
     * Saves whatever the editor holds: null when the file holds it, or why
     * it does not. Registered by the editor while it is open; the activity
     * calls it when the app goes to the background, which on a phone is how
     * most notes are left -- not by Back.
     */
    var flush: (() -> String?)? = null

    /**
     * Moves each time something other than the editor wrote the note open
     * in it: the list's pin or category beside it on a wide screen, a day
     * reset from the calendar, an import that updated it. The editor holds
     * its own copy of every field and writes them all back when it saves,
     * so it reads the note again from the file when this moves -- or its
     * next save would quietly put the old fields back.
     */
    var openNoteRewritten by mutableIntStateOf(0)
        private set

    val settings: JSONObject get() = load.settings?.raw ?: JSONObject()

    /** A note's attached files: what it calls each, and its stored name. */
    fun attachmentsOf(id: Int): List<Pair<String, String>> {
        val raw = load.raw ?: return emptyList()
        for (i in 0 until raw.length()) {
            val note = raw.opt(i) as? JSONObject ?: continue
            if ((note.opt("id") as? Number)?.toInt() != id) continue
            val atts = Normalize.cleanAttachments(note.optJSONArray("attachments") ?: org.json.JSONArray())
            return (0 until atts.length()).map {
                val att = atts.getJSONObject(it)
                att.optString("filename") to att.optString("stored_as")
            }
        }
        return emptyList()
    }

    /**
     * The Trash's window, or null when it could not be read: nothing leaves
     * the Trash by itself then, so no question may promise a number of days
     * (the desktop's trash_window_words, 2026-10-26).
     */
    val trashWindow: Int? get() = load.settings?.let { Settings.purgeWindow(it) }

    /** After a write: the screen from the arrays the write left. */
    fun refresh() {
        load = Notebook.refreshed(load)
        revision += 1
    }

    /** From the disk, for when the files changed under the app (an import). */
    fun reload() {
        load = Notebook.load(dir)
        revision += 1
    }

    /** A result shown when it failed; the screen refreshed either way. */
    fun report(result: SaveResult, onOk: (SaveResult.Ok) -> Unit = {}): Boolean {
        refresh()
        return when (result) {
            is SaveResult.Ok -> {
                onOk(result)
                true
            }
            is SaveResult.Failed -> {
                say(result.reason)
                false
            }
        }
    }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message, withDismissAction = true) }
    }

    /** A message with one thing to do about it: "Exported to…", Open. */
    fun offer(message: String, action: String, onAction: () -> Unit) {
        scope.launch {
            val answer = snackbar.showSnackbar(message, actionLabel = action,
                duration = SnackbarDuration.Long, withDismissAction = true)
            if (answer == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun putSetting(key: String, value: Any?): Boolean {
        val settings = load.settings ?: return false
        return report(Settings.put(settings, key, value))
    }

    /** The Trash's window chosen: which also ends a window lost with an unreadable file. */
    fun chooseTrashWindow(days: Int): Boolean {
        val settings = load.settings ?: return false
        return report(Settings.putAll(settings, mapOf(Settings.RETENTION_DAYS to days,
                                                      Settings.PHONE_TRASH_WINDOW_UNKNOWN to null)))
    }

    // ---- the notes ----

    /**
     * Opens a note -- on one [day] of it, when the note repeats and a day is
     * named; as a whole otherwise. A plain note opened from a calendar day
     * is simply the note.
     */
    fun openNote(id: Int, day: String? = null) {
        val onDay = if (day != null && rawNote(id)?.let { Recurrence.isRecurring(it) } == true) day else null
        // Its own card tapped again: the note stays as it is, cursor and all.
        if (id == openId && onDay == openDay) {
            flush?.invoke()
            return
        }
        // On a wide screen the list stays beside the note, and a tap on
        // another card replaces it: what it holds is saved first.
        leaving {
            openDay = onDay
            newNoteDate = ""
            openId = id
            opening += 1
        }
    }

    fun newNote(date: String = "") = leaving {
        openDay = null
        newNoteDate = date
        openId = 0
        opening += 1
    }

    /**
     * [go] -- to another note, a new one, or away from the editor -- once
     * the open note's words are saved. When they cannot be, the editor stays
     * open and says why, offering to leave anyway: closed unsaved, a new
     * note's only copy of its words went with it, and nothing on the screen
     * said so (ISSUES_LOG, "A way out that never asked whether the note was
     * saved"). Said in the bar at the foot, which a screen reader reads out.
     */
    private fun leaving(go: () -> Unit) {
        val why = flush?.invoke() ?: return go()
        // Over whatever the bar was saying: this answers the tap just made.
        snackbar.currentSnackbarData?.dismiss()
        offer("Not saved, so the note stays open: $why", "Leave anyway", go)
    }

    /**
     * A new note's first save: it is that note now. Its card is outlined as
     * the open one, and a change to it from the list reaches the editor.
     */
    fun newNoteSaved(id: Int) {
        if (openId == 0) openId = id
    }

    fun closeNote() {
        openId = null
        openDay = null
        newNoteDate = ""
    }

    /** The open note saved and closed -- by Back, or the rail leaving it -- and [then]. */
    fun saveAndCloseNote(then: () -> Unit = {}) = leaving {
        closeNote()
        then()
    }

    /**
     * A change to [ids] from outside the editor. When the open note is one
     * of them, what the editor holds is saved first -- so the change lands
     * on the note as it is on screen, and a copy or a code made of it
     * carries the last words typed -- and, when the change [writes] it,
     * the editor reads it back after. Null, the change not made, when the
     * editor's words cannot be saved: read back, or the note sent to the
     * Trash, they would be gone.
     */
    private fun <T> outside(ids: Collection<Int>, writes: Boolean = true, change: () -> T): T? {
        val open = openId
        val touches = open != null && open != 0 && open in ids
        if (touches) flush?.invoke()?.let { why ->
            say("Not saved, so the open note was left as it is: $why")
            return null
        }
        return change().also { if (touches && writes) openNoteRewritten += 1 }
    }

    /**
     * Before an import is applied: the open note saved, so an update replaces
     * it whole. False, and said, when it cannot be: the import reads the note
     * back after, over words no file holds.
     */
    fun beforeImport(): Boolean {
        val why = flush?.invoke() ?: return true
        say("Not saved, so nothing was imported: $why")
        return false
    }

    /**
     * After an import: the notebook read again, and the open note with it,
     * which the import may have updated. Read back whichever it touched:
     * an unchanged note read back loses nothing but its cursor.
     */
    fun afterImport() {
        reload()
        if (openId != null) openNoteRewritten += 1
    }

    /** From one day back to the whole series, the editor staying on the note. */
    fun openWholeSeries() {
        openDay = null
    }

    fun note(id: Int): Note? = load.notes.firstOrNull { it.id == id }

    /** A note as the file holds it: what the calendar and the day rules read. */
    fun rawNote(id: Int): JSONObject? = rawNotes().firstOrNull { (it.opt("id") as? Number)?.toInt() == id }

    /** How many templates the notebook keeps: the desktop's count, from templates.json. */
    fun templatesCount(): Int = try {
        val file = File(dir, Backup.TEMPLATES)
        if (file.isFile) org.json.JSONArray(file.readText(Charsets.UTF_8)).length() else 0
    } catch (e: Exception) {
        0
    }

    fun rawNotes(): List<JSONObject> {
        val raw = load.raw ?: return emptyList()
        return (0 until raw.length()).mapNotNull { raw.opt(it) as? JSONObject }
    }

    // ---- the calendar's days ----

    val dayColours: Map<String, String> get() = Days.dayColours(settings)
    val dayMarkers: Map<String, List<String>> get() = Days.dayMarkers(settings)

    /** Paints a day, or clears its paint with "" -- which offers its Undo. */
    fun paintDay(day: String, colour: String) {
        val old = dayColours[day]
        if (!putSetting(Days.DAY_COLORS, Days.withColour(settings, day, colour))) return
        if (colour.isEmpty() && old != null) {
            offerUndo("Day colour cleared") { undoDayClear(day, colour = old) }
        }
    }

    /** A day's markers, three at most; none clears them, with an Undo. */
    fun markDay(day: String, markers: List<String>) {
        val old = dayMarkers[day]
        if (!putSetting(Days.DAY_EMOJI, Days.withMarkers(settings, day, markers))) return
        if (markers.isEmpty() && old != null) {
            offerUndo("Day markers cleared") { undoDayClear(day, markers = old) }
        }
    }

    /**
     * A cleared colour, or cleared markers, put back -- only into a day
     * still empty: anything there now was chosen in the seconds since, and
     * a stale Undo painted over it. The desktop's `_undo_day_clear`, in its
     * words. True when it was put back.
     */
    fun undoDayClear(day: String, colour: String? = null, markers: List<String>? = null): Boolean {
        if (colour != null) {
            if (!dayColours[day].isNullOrEmpty()) {
                say("That day has a colour again")
                return false
            }
            return putSetting(Days.DAY_COLORS, Days.withColour(settings, day, colour))
        }
        if (markers != null) {
            if (!dayMarkers[day].isNullOrEmpty()) {
                say("That day has markers again")
                return false
            }
            return putSetting(Days.DAY_EMOJI, Days.withMarkers(settings, day, markers))
        }
        return false
    }

    /** One day out of its series, with the Undo the desktop's toast offers. */
    fun deleteDay(id: Int, day: String) {
        val (result, gone) = outside(listOf(id)) { Days.deleteDay(load, id, day) } ?: return
        if (!report(result) || gone == null) return
        if (openId == id && openDay == day) closeNote()
        offerUndo("Day removed") { report(Days.undoDeleteDay(load, gone)) }
    }

    fun resetDay(id: Int, day: String, dropText: Boolean = true, sendHome: Boolean = true) {
        val home = rawNote(id)?.let { Recurrence.movedFrom(it, day) }
        report(outside(listOf(id)) { Days.resetDay(load, id, day, dropText, sendHome) } ?: return) {
            // The editor open on the day goes with it, to where it is now.
            if (sendHome && home != null && openId == id && openDay == day) openDay = home
            say(if (dropText) "This day follows the series again" else "This day is back on its own date")
        }
    }

    /**
     * One day of a series held on another date -- the desktop's "📅 Move
     * this day…", for a weekly meeting held on another day one week. The
     * editor open on the day saves first and follows it there; the Undo
     * puts it back while it still can.
     */
    fun moveDay(id: Int, day: String, to: String) {
        val (result, move) = outside(listOf(id)) { Days.moveDay(load, id, day, to) } ?: return
        if (!report(result) || move == null) return
        if (openId == id && openDay == day) openDay = to
        offerUndo("Moved to ${shownDay(to)}") {
            report(outside(listOf(move.id)) { Days.undoMoveDay(load, move) } ?: return@offerUndo) {
                if (openId == move.id && openDay == move.to) openDay = move.from
                say("Moved back to ${shownDay(move.from)}")
            }
        }
    }

    /** A day off its series' schedule made a note of its own, or that note sent to the Trash. */
    fun settleOffScheduleDay(id: Int, day: String, how: Days.Settle): Boolean {
        val result = outside(listOf(id)) { Days.settleOffScheduleDay(load, id, day, how) } ?: return false
        return report(result)
    }

    private fun shownDay(day: String) = Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day

    fun setRepeat(id: Int, rule: JSONObject?, startWas: Any? = null): Boolean {
        val result = outside(listOf(id)) { Days.setRepeat(load, id, rule, startWas) } ?: return false
        return report(result)
    }

    /**
     * A message with an Undo. Eight seconds, as the desktop's banner: long
     * enough to read it and decide. Android's own lengths are four and ten,
     * and four was gone before a thumb could reach it.
     */
    private fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            val timer = launch {
                delay(8_000)
                snackbar.currentSnackbarData?.dismiss()
            }
            val answer = snackbar.showSnackbar(message, actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite, withDismissAction = false)
            timer.cancel()
            if (answer == SnackbarResult.ActionPerformed) undo()
        }
    }

    /**
     * Moves notes to the Trash and offers them back for eight seconds --
     * the desktop's banner, as Android's snackbar. Undo restores exactly
     * those entries and opens the first, as the desktop's does.
     */
    fun trash(ids: Collection<Int>, openAfterUndo: Boolean = false) {
        // The open note goes to the Trash with its last words in it.
        val result = outside(ids, writes = false) { Organizing.trashNotes(load, ids) } ?: return
        if (!report(result)) return
        val count = (result as SaveResult.Ok).imported
        val keys = load.trash.takeLast(count).map { Organizing.keyOf(it) }
        if (openId != null && openId in ids) closeNote()
        offerUndo(if (count == 1) "Moved to Trash" else "Moved $count notes to Trash") {
            report(Organizing.restore(load, keys)) { ok ->
                if (openAfterUndo || count == 1) openNote(ok.id)
            }
        }
    }

    /**
     * A new note from a template, opened. The note that was open is saved
     * already -- the editor saves before it asks -- so nothing is replaced,
     * and the desktop's "Replace current note?" has nothing to ask about.
     */
    fun useTemplate(template: JSONObject, said: String) {
        report(Templates.newNote(load, template, load.categories.keys)) { ok ->
            openNote(ok.id)
            say(said)
        }
    }

    fun duplicate(id: Int) {
        report(outside(listOf(id), writes = false) { Organizing.duplicate(load, id, attachments) } ?: return) { ok ->
            say("Duplicated as “${note(ok.id)?.title ?: "(copy)"}”")
        }
    }

    /**
     * The list's sort, kept under the desktop's key. Custom order chosen
     * before anything was placed starts from the order the list was
     * showing, as the desktop's does -- every note given its place first.
     * The phone arranges only in Custom order, so the desktop's other path,
     * a move made in another sort that replaces an arrangement made by
     * hand, and the question it asks after, never arise here.
     */
    fun chooseSort(order: SortOrder, from: SortOrder) {
        if (order == SortOrder.CUSTOM && from != SortOrder.CUSTOM && load.notes.none { it.order != null }) {
            if (!report(Organizing.placeAsSorted(load, from))) return
        }
        putSetting(Settings.NOTE_SORT, order.label)
    }

    /**
     * Files handed over from the picker, attached to a note in one write --
     * and said, when some could not be read or copied. The editor does not
     * hold a note's files, so its next save has nothing to put back.
     */
    fun attach(id: Int, incoming: List<Organizing.Incoming>, unreadable: Int = 0) {
        val done = Organizing.attach(load, id, attachments, incoming)
        if (!report(done.result)) return
        val missed = done.failed.size + unreadable
        if (missed > 0) say(if (missed == 1) "One file could not be attached." else "$missed files could not be attached.")
    }

    /** An attached file taken off a note, its stored copy deleted unless another note uses it. */
    fun detach(id: Int, storedAs: String) {
        report(Organizing.detach(load, id, storedAs, attachments))
    }

    /**
     * The ticked copies of each group of duplicates to the Trash, their files
     * handed to a copy kept -- with the Undo every move to the Trash offers
     * here. A copy open in the editor goes with its last words in it.
     */
    fun cleanUpDuplicates(groups: List<List<Int>>, remove: Set<Int>) {
        val result = outside(remove, writes = false) { Organizing.cleanUpDuplicates(load, groups, remove, attachments) } ?: return
        if (!report(result)) return
        val count = (result as SaveResult.Ok).imported
        val keys = load.trash.takeLast(count).map { Organizing.keyOf(it) }
        if (openId != null && openId in remove) closeNote()
        offerUndo(if (count == 1) "Moved 1 duplicate to the Trash" else "Moved $count duplicates to the Trash") {
            report(Organizing.restore(load, keys))
        }
    }

    /** A note dragged to just before or after another, in Custom order. */
    fun moveNote(id: Int, anchor: Int, before: Boolean, grouped: Boolean) {
        report(Organizing.moveNote(load, id, anchor, before, grouped))
    }

    fun setPinned(ids: Collection<Int>, pinned: Boolean) {
        report(outside(ids) { Organizing.setPinned(load, ids, pinned) } ?: return)
    }

    /**
     * A checklist row taken off by its ✕, said, with the Undo the desktop's
     * toast offers: the row was gone at once, unsaid, with no way back, and
     * the editor's own Undo does not hold a checklist.
     */
    fun removeChecklistRow(id: Int, index: Int) {
        val row = (rawNote(id)?.opt("checklist") as? JSONArray)?.opt(index) as? JSONObject ?: return
        val kept = JSONObject(row.toString())
        if (!report(Notebook.removeChecklistItem(load, id, index))) return
        offerUndo("Removed “${kept.optString("text")}” from the checklist") {
            report(Notebook.restoreChecklistItem(load, id, index, kept))
        }
    }

    fun moveToCategory(ids: Collection<Int>, category: String) {
        report(outside(ids) { Organizing.moveToCategory(load, ids, category) } ?: return) {
            say(if (ids.size == 1) "Moved to “$category”" else "Moved ${ids.size} notes to “$category”")
        }
    }

    fun shareCode(ids: Collection<Int>): String? = outside(ids, writes = false) { load.raw }?.let { raw ->
        val wanted = ids.toSet()
        val notes = (0 until raw.length()).mapNotNull { raw.opt(it) as? JSONObject }
            .filter { (it.opt("id") as? Number)?.toInt() in wanted }
        if (notes.isEmpty()) return@let null
        val code = try {
            Sharing.encode(notes)
        } catch (e: Sharing.TooLarge) {
            say(e.message ?: "")
            return@let null
        }
        if (code.length > Sharing.MAX_HANDED_CHARS) {
            say("That is too much to hand to another app as one code. Share fewer notes at a time, " +
                "or send a backup instead: Settings → Backup.")
            return@let null
        }
        // The desktop's warning: a code carries the words, never the files.
        if (notes.any { ((it.opt("attachments") as? org.json.JSONArray)?.length() ?: 0) > 0 }) {
            say("Sent without its files: a code carries the words only. A backup carries files too.")
        }
        code
    }

    // ---- the Trash ----

    fun restoreFromTrash(keys: Collection<Organizing.TrashKey>) {
        report(Organizing.restore(load, keys)) { ok ->
            say(if (ok.imported == 1) "Restored" else "Restored ${ok.imported} notes")
        }
    }

    fun deleteForever(keys: Collection<Organizing.TrashKey>) {
        report(Organizing.deleteForever(load, keys, attachments))
    }

    fun emptyTrash() {
        report(Organizing.emptyTrash(load, attachments))
    }

    // ---- the theme ----

    /** The theme to draw with, given whether the phone is in dark mode. */
    fun themeName(phoneIsDark: Boolean): String = Settings.theme(settings, phoneIsDark)

    /** A theme picked on Appearance: worn, and kept as its mode's last. */
    fun chooseTheme(name: String): Boolean {
        val load = load.settings ?: return false
        return report(Settings.putAll(load, Settings.choosing(Themes.valid(name))))
    }

    /** Light | Dark moved: the app wears the last theme of that mode. */
    fun chooseMode(dark: Boolean): Boolean = chooseTheme(Settings.themeForMode(settings, dark))

    /**
     * Once, at a start: a notebook that still followed the phone -- the
     * default until 0.9.0 -- keeps the theme it is wearing and follows no
     * more. Nothing changes on the screen; only the next change of the
     * phone's mode no longer changes the app's.
     */
    fun settleTheme(phoneIsDark: Boolean) {
        if (load.settings != null && Settings.followsPhone(settings)) chooseTheme(themeName(phoneIsDark))
    }
}
