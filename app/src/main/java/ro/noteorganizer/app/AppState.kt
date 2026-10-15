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
import org.json.JSONObject
import java.io.File

/** Where the app is: the desktop's two tabs, and its Settings. */
enum class Tab { NOTES, CALENDAR, SETTINGS }

/** One page of Settings, or the list of them. */
enum class SettingsPage { NAME, APPEARANCE, CATEGORIES, BACKUP, TRASH, STATS, ABOUT }

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
class AppState(val dir: File, private val scope: CoroutineScope) {

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
     * The one day of a repeating note the editor is open on -- the
     * desktop's day mode -- or null for the note as a whole.
     */
    var openDay by mutableStateOf<String?>(null)
        private set

    /** The date a new note starts with: the calendar day it was added on. */
    var newNoteDate: String = ""
        private set

    // ---- the calendar ----

    /** The month the calendar shows, and the day whose list is open. */
    var calendarMonth by mutableStateOf(java.time.YearMonth.now())
    var selectedDay by mutableStateOf(Recurrence.formatDate(java.time.LocalDate.now()))

    /** The History in place of the month, as the desktop's toggle swaps them. */
    var showHistory by mutableStateOf(false)

    /** A share code arriving from another app, waiting for the import panel. */
    var incomingCode by mutableStateOf<String?>(null)

    /** A line said once at the top of the list (a load problem, a purge). */
    var notice by mutableStateOf<String?>(null)

    val snackbar = SnackbarHostState()

    /** Android's share sheet, set by the activity: text out to any app. */
    var sendText: ((String) -> Unit)? = null

    /** Opens an attached file with the phone's own apps, set by the activity. */
    var openFile: ((File, String) -> Unit)? = null

    /**
     * Saves whatever the editor holds. Registered by the editor while it is
     * open; the activity calls it when the app goes to the background, which
     * on a phone is how most notes are left -- not by Back.
     */
    var flush: (() -> Unit)? = null

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

    fun putSetting(key: String, value: Any?): Boolean {
        val settings = load.settings ?: return false
        return report(Settings.put(settings, key, value))
    }

    // ---- the notes ----

    /**
     * Opens a note -- on one [day] of it, when the note repeats and a day is
     * named; as a whole otherwise. A plain note opened from a calendar day
     * is simply the note.
     */
    fun openNote(id: Int, day: String? = null) {
        openDay = if (day != null && rawNote(id)?.let { Recurrence.isRecurring(it) } == true) day else null
        newNoteDate = ""
        openId = id
    }

    fun newNote(date: String = "") {
        openDay = null
        newNoteDate = date
        openId = 0
    }

    fun closeNote() {
        openId = null
        openDay = null
        newNoteDate = ""
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
            offerUndo("Day colour cleared") { putSetting(Days.DAY_COLORS, Days.withColour(settings, day, old)) }
        }
    }

    /** A day's markers, three at most; none clears them, with an Undo. */
    fun markDay(day: String, markers: List<String>) {
        val old = dayMarkers[day]
        if (!putSetting(Days.DAY_EMOJI, Days.withMarkers(settings, day, markers))) return
        if (markers.isEmpty() && old != null) {
            offerUndo("Day markers cleared") { putSetting(Days.DAY_EMOJI, Days.withMarkers(settings, day, old)) }
        }
    }

    /** One day out of its series, with the Undo the desktop's toast offers. */
    fun deleteDay(id: Int, day: String) {
        val (result, gone) = Days.deleteDay(load, id, day)
        if (!report(result) || gone == null) return
        if (openId == id && openDay == day) closeNote()
        offerUndo("Day removed") { report(Days.undoDeleteDay(load, gone)) }
    }

    fun resetDay(id: Int, day: String, dropText: Boolean = true, sendHome: Boolean = true) {
        val home = rawNote(id)?.let { Recurrence.movedFrom(it, day) }
        report(Days.resetDay(load, id, day, dropText, sendHome)) {
            // The editor open on the day goes with it, to where it is now.
            if (sendHome && home != null && openId == id && openDay == day) openDay = home
            say(if (dropText) "This day follows the series again" else "This day is back on its own date")
        }
    }

    fun setRepeat(id: Int, rule: JSONObject?): Boolean = report(Days.setRepeat(load, id, rule))

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
        val result = Organizing.trashNotes(load, ids)
        if (!report(result)) return
        val count = (result as SaveResult.Ok).imported
        val keys = load.trash.takeLast(count).map { Organizing.TrashKey(it.id, it.deletedAt) }
        if (openId != null && openId in ids) closeNote()
        offerUndo(if (count == 1) "Moved to Trash" else "Moved $count notes to Trash") {
            report(Organizing.restore(load, keys)) { ok ->
                if (openAfterUndo || count == 1) openNote(ok.id)
            }
        }
    }

    fun duplicate(id: Int) {
        report(Organizing.duplicate(load, id, attachments)) { ok ->
            say("Duplicated as “${note(ok.id)?.title ?: "(copy)"}”")
        }
    }

    fun setPinned(ids: Collection<Int>, pinned: Boolean) {
        report(Organizing.setPinned(load, ids, pinned))
    }

    fun moveToCategory(ids: Collection<Int>, category: String) {
        report(Organizing.moveToCategory(load, ids, category)) {
            say(if (ids.size == 1) "Moved to “$category”" else "Moved ${ids.size} notes to “$category”")
        }
    }

    fun shareCode(ids: Collection<Int>): String? = load.raw?.let { raw ->
        val wanted = ids.toSet()
        val notes = (0 until raw.length()).mapNotNull { raw.opt(it) as? JSONObject }
            .filter { (it.opt("id") as? Number)?.toInt() in wanted }
        if (notes.isEmpty()) null else Sharing.encode(notes)
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
}
