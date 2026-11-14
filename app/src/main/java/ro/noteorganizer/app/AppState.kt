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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Said when a day would be the calendar's 20,001st painted or marked one (Days.MAX_DAY_DECORATIONS). */
private const val FULL_CALENDAR = "The calendar keeps 20,000 painted days, and as many marked, at most. Clear one first."

/** Where the app is: the desktop's two tabs, and its Settings. */
enum class Tab { NOTES, CALENDAR, SETTINGS }

/** One page of Settings, or the list of them. LICENSES opens from About, and goes back there. */
enum class SettingsPage { NAME, APPEARANCE, CATEGORIES, TEMPLATES, REMINDERS, BACKUP, TRASH, DUPLICATES, STATS, ABOUT, LICENSES }

/**
 * The app's state, and every action a screen can take on the notebook: one
 * place, because every screen shares one notebook and one rule for changing
 * it. The rules layer writes a candidate whole before memory changes; the
 * screen is refreshed from what was written, or told why not, never left
 * showing a change the file does not have. Holds Compose state, so it lives
 * on the drawing side of the line the layering gate keeps.
 */
class AppState(val dir: File, val scope: CoroutineScope) {

    val attachments: File = File(dir, "attachments").apply { mkdirs() }

    // Never "equal": a write changes the settings INSIDE the object it read,
    // so Compose skipped the redraw, and a switch stayed on over a file that
    // said off (ISSUES_LOG, "A setting saved, and a switch that did not move").
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
     * Whether the notes list has notes selected, set by the list: on a wide
     * screen the + stands in the rail, and steps aside then as the list's
     * own does on a phone.
     */
    var listSelecting by mutableStateOf(false)

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

    /**
     * A new note written since the list last looked: the list asks, once the
     * note is closed, whether it can show it ([NotesScreen]).
     */
    var newNoteMade by mutableStateOf<Int?>(null)

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

    /** Counts the app's returns to the screen: what the phone's settings may have changed meanwhile is read again. */
    var resumed by mutableIntStateOf(0)

    /** A search for the History to take up, handed over by the list ([searchHistoryFor]). */
    var historyQuery by mutableStateOf<String?>(null)

    /**
     * The Calendar's History, searching for [query]: it reads a day's own
     * words, which the list's search does not (the empty list's link).
     */
    fun searchHistoryFor(query: String) {
        historyQuery = query
        showHistory = true
        tab = Tab.CALENDAR
    }

    /**
     * A note's sections folded away to make room for the others -- its text,
     * checklist, comments -- keyed "<note id>:<section>". Kept while the app
     * runs, not in the notebook: how a note is looked at is not the note.
     */
    val folded = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    /**
     * The folds of notes no longer in the notebook go with them: a note
     * deleted left its folds to the next note given its id, a checklist
     * added there folding its text shut.
     */
    private fun dropFoldsOfGone() {
        if (folded.isEmpty()) return
        val held = load.notes.mapTo(HashSet()) { it.id.toString() }
        folded.keys.filter { key -> key.substringBefore(':').let { it != "0" && it !in held } }.forEach { folded.remove(it) }
    }

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
     * not be written still shows the bar; it may show once more at a start,
     * never at each return to the app ([reminderShownOn]).
     */
    fun checkReminder(today: String) {
        if (reminderShownOn == today) return
        val due = Reminders.dueOn(rawNotes(), today)
        if (!Reminders.barDue(settings, today, due)) return
        load.settings?.let { Settings.put(it, Reminders.LAST_SHOWN, today) }
        reminderShownOn = today
        reminderDue = due
    }

    /** The day the bar last showed in this run, kept here too: a held settings file keeps no mark. */
    private var reminderShownOn: String? = null

    /** The welcome note Show brought back this run, kept here too: its uid's write may be refused. */
    internal var welcomeUidRun: String? = null

    /** The calendar on [today], the bar gone: what the bar and the notification open. */
    fun showToday(today: String) {
        Recurrence.parseDate(today)?.let { calendarMonth = java.time.YearMonth.from(it) }
        selectedDay = today
        showHistory = false
        reminderDue = null
        // Reminded already, by the notification: the bar does not say it again.
        if (settings.opt(Reminders.LAST_SHOWN) != today) load.settings?.let { Settings.put(it, Reminders.LAST_SHOWN, today) }
        reminderShownOn = today
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

    /** What a PDF that would leave part of a note out becomes, as the person answers [askPdf]. */
    enum class PdfChoice { PAGE, PDF, CANCEL }

    /** The question [askPdf] asks, waiting for its answer: what the PDF would leave out. */
    class PdfQuestion(val firstPagesOnly: Boolean, val cutAtEdge: Boolean) {
        internal val answer = kotlinx.coroutines.CompletableDeferred<PdfChoice>()
    }

    /** The question about a PDF on the screen now, if one is. */
    var pdfQuestion by mutableStateOf<PdfQuestion?>(null)
        private set

    /**
     * Asks, before a PDF is handed to another app, what to do with one that
     * would leave part of the note out -- its first pages only, or cut at the
     * page's edge: the web page instead, the PDF as it is, or nothing. Said
     * in a bar as the share sheet opened, the sheet covered the bar and its
     * time ran out under it.
     */
    suspend fun askPdf(firstPagesOnly: Boolean, cutAtEdge: Boolean): PdfChoice {
        val question = PdfQuestion(firstPagesOnly, cutAtEdge)
        pdfQuestion = question
        return try {
            question.answer.await()
        } finally {
            if (pdfQuestion === question) pdfQuestion = null
        }
    }

    /** The answer to the question on the screen ([askPdf]). */
    fun answerPdf(choice: PdfChoice) {
        val question = pdfQuestion ?: return
        pdfQuestion = null
        question.answer.complete(choice)
    }

    val snackbar = SnackbarHostState()

    /**
     * The window's top, in pixels, of what the open note's editor draws at
     * the foot of its column (its format bar, a table's bar, the box being
     * typed in), for messages to be drawn above it; null when nothing is
     * there. Drawn just above the keyboard, a message covered the bar's keys
     * and "A new item…", and took the taps meant for them.
     */
    var editorFootTop by mutableStateOf<Float?>(null)

    /**
     * The same for a page's own buttons at its foot while no note is open:
     * the Trash's Restore. A delete's "Moved to Trash · Undo" lay over it for
     * its ten seconds; dismissed instead, an Undo that was the only way back
     * (a day's words) went with it.
     */
    var pageFootTop by mutableStateOf<Float?>(null)

    /** Android's share sheet, set by the activity: text out to any app. */
    var sendText: ((String) -> Unit)? = null

    /** Opens an attached file with the phone's own apps, set by the activity. */
    var openFile: ((File, String) -> Unit)? = null

    // Android's pickers for one note, set by AppRoot, where the answer arrives
    // even after Android ended the app behind the picker; held by the editor,
    // it found nobody (ISSUES_LOG, "Answers sent to a screen that was gone").

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

    /** [flush] for a save the editor stays open after: "A new item…" left in its box (EditorModel.writeStaying). */
    var flushStaying: (() -> String?)? = null

    /** What a save that leaves the editor open runs: [flushStaying], or [flush] when it is not registered. */
    private fun saveStaying(): String? = (flushStaying ?: flush)?.invoke()

    /** [flush] as the app goes to the background, and its undoing when the app is back in front (EditorModel.writeAway). */
    var flushAway: (() -> String?)? = null
    var backAgain: (() -> Unit)? = null

    /**
     * Keeps aside the template being written, when it holds changes nobody
     * saved: registered by the template editor while it is open, called by
     * the activity as the app goes to the background, beside [flush]. Android
     * may end the app there, and the editor saves only on Save.
     */
    var keepTemplateDraft: (() -> Unit)? = null

    /**
     * Saves the name typed in Settings → General: registered by its page
     * while it is open, called by the activity as the app goes to the
     * background, beside [flush]. The page saved it on Save, Done or leaving
     * it, and Android may end the app in the background with none of those.
     */
    var keepNameDraft: (() -> Unit)? = null

    /**
     * Where that draft waits: the app's own storage that Android's backup
     * leaves out -- a draft is this phone's, for a moment. The activity
     * gives it Android's no-backup folder; on the computer, beside the notebook.
     */
    var draftDir: File = File(dir.absoluteFile.parentFile ?: dir, "no_backup")

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

    /**
     * The notes the Trash's Undo just put back, for the list to bring the
     * first of into sight: several taken back from the top of the screen
     * came back above it, and the Undo looked as if it did nothing.
     */
    var revealAfterUndo by mutableStateOf<UndoReveal?>(null)

    /**
     * What [revealAfterUndo] holds: the notes put back, and those no waiting
     * word took back -- decided by the Undo, as the list's word may already
     * be taken when the list asks.
     */
    data class UndoReveal(val ids: Set<Int>, val unsaid: Set<Int>)

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
        dropFoldsOfGone()
        dropArrivalsOfGone()
        revision += 1
    }

    /** From the disk, for when the files changed under the app (an import). */
    fun reload() {
        // The notes kept aside this session are still the session's to spend.
        load = Notebook.load(dir).also { it.keptAside.putAll(load.keptAside) }
        dropFoldsOfGone()
        dropArrivalsOfGone()
        revision += 1
    }

    /**
     * The list's word waiting for it ([arrivals]) without the notes gone
     * since -- taken back, sent to the Trash: said later, it named the next
     * new note, which is given a gone note's id.
     */
    private fun dropArrivalsOfGone() {
        val held = load.notes.mapTo(HashSet()) { it.id }
        // The new note's word ([newNoteMade]) the same way.
        newNoteMade?.let { if (it !in held) newNoteMade = null }
        if (arrivals.all { word -> word.ids.all { it in held } }) return
        arrivals = arrivals.mapNotNull { word ->
            word.ids.filter { it in held }.takeIf { it.isNotEmpty() }?.let { word.copy(ids = it) }
        }
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
                sayRefused(result.reason)
                false
            }
        }
    }

    /** Why a write was refused, said: a bare cause ("the phone's storage is full") said nothing of what went unsaved. */
    fun sayRefused(reason: String) = say(if (Files.bareCause(reason)) "Not saved: $reason." else reason)

    /** The editor's own refusals, which always say "Not saved:": a bare cause in sayRefused's words. */
    fun sayNotSaved(why: String) = say("Not saved: ${Files.ended(why)}")

    /** A message, [long] for one with more to read than a glance takes. */
    fun say(message: String, long: Boolean = false) {
        scope.launch {
            snackbar.showSnackbar(forTheScreen(message), withDismissAction = true,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    /** A message with one thing to do about it: "Exported to…", Open. */
    fun offer(message: String, action: String, onAction: () -> Unit): Job =
        offerFor(emptyList(), message, action, null, onAction)

    private fun offerFor(about: Collection<Int>, message: String, action: String, again: (() -> Unit)?,
                         onAction: () -> Unit): Job {
        val bar = scope.launch {
            val answer = snackbar.showSnackbar(forTheScreen(message), actionLabel = action,
                duration = SnackbarDuration.Long, withDismissAction = true)
            if (answer == SnackbarResult.ActionPerformed) onAction()
        }
        offered.removeAll { it.bar.isCompleted }
        offered.add(Offered(about, message, action, onAction, again, bar))
        return bar
    }

    /**
     * An [offer] as made: the notes it is about, its words, its action, how to
     * ask it again ([again]) and its bar. One Undo at a time is on offer
     * ([undoBar], the newest); the offers it finds waiting go to [heldBack], to
     * be asked again when it is done, not when none of their notes is left;
     * a later offer, or a plain [say], queues behind it as any bar does.
     * [barsAbout] files the bars by note, so notes taken back end their own.
     */
    private class Offered(val about: Collection<Int>, val message: String, val action: String,
                          val onAction: () -> Unit, val again: (() -> Unit)?, val bar: Job)

    /** Every [offer] whose bar is not yet gone, shown or waiting: an Undo goes before them ([offerUndo]). */
    private val offered = ArrayList<Offered>()

    /** The offers an Undo still showing holds back, asked again once it is done. */
    private val heldBack = ArrayList<Offered>()

    /** The bars [offerAbout] put up, by the notes they are about. */
    private val barsAbout = HashMap<Int, MutableList<Job>>()

    /** The last [offerUndo]'s bar, which the next one replaces, shown or waiting. */
    private var undoBar: Job? = null

    /**
     * An [offer] about the notes [ids], which goes with them when [asidesSpent]
     * takes them away, and is not asked again for notes gone; [again], how
     * the one who made it asks it again ([offerUndo]).
     */
    fun offerAbout(ids: Collection<Int>, message: String, action: String, again: (() -> Unit)? = null,
                   onAction: () -> Unit): Job {
        val bar = offerFor(ids, message, action, again, onAction)
        barsAbout.values.removeAll { bars -> bars.removeAll { it.isCompleted }; bars.isEmpty() }
        for (id in ids) barsAbout.getOrPut(id) { ArrayList() }.add(bar)
        return bar
    }

    /**
     * The notes kept aside that a save took back, their words in the note
     * again ([SaveResult.Ok.spentAside]): their bars go, on screen or still
     * waiting their turn, and it is said. Only theirs: dismissing the bar on
     * screen took another action's Undo, or the bar of another note kept
     * aside, whose own then showed.
     */
    fun asidesSpent(ids: List<Int>) {
        if (ids.isEmpty()) return
        for (id in ids) barsAbout.remove(id)?.forEach { it.cancel() }
        say("The words are back in the note; the note kept aside of them is gone again")
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
     * Opens a note -- on one [day] of it, when the series draws that day
     * ([Days.givenDay]); as a whole otherwise, as the desktop's load_note
     * asks. Opened on a day it does not draw, every save there was refused,
     * and every way out that saves first: only "Leave anyway" was left, and
     * what was typed went with it.
     */
    fun openNote(id: Int, day: String? = null) {
        fun onDay() = day?.takeIf { d -> rawNote(id)?.let { Recurrence.isRecurring(it) && Days.givenDay(it, d) } == true }
        // Its own card tapped again: the note stays as it is, cursor and all.
        if (id == openId && onDay() == openDay) {
            saveStaying()
            return
        }
        // The same note on another of its days, or as the whole series: built
        // anew there, the editor carries "A new item…" (NoteEditor), so the save
        // leaves the box -- through leaving(), its words became a row.
        if (id == openId && id != 0) {
            val why = saveStaying() ?: return run { openDay = onDay() }
            snackbar.currentSnackbarData?.dismiss()
            // Only on the note it was asked over: tapped late, it set the day
            // on whatever note was open then, a plain one "on a day".
            offer("Not saved, so the note stays open: ${Files.ended(why)}", "Leave anyway") { if (openId == id) openDay = onDay() }
            return
        }
        // On a wide screen the list stays beside the note, and a tap on
        // another card replaces it: what it holds is saved first -- and the
        // day asked again after, as that save can move the series' start.
        leaving {
            // Looked for again after the save that left the open note: that
            // save can take this one away -- an '(earlier text)' note spent --
            // and an empty new note opened in its place.
            if (id != 0 && rawNote(id) == null) {
                say("That note is no longer here")
                return@leaving
            }
            openDay = onDay()
            newNoteDate = ""
            openId = id
            opening += 1
        }
    }

    /**
     * The note open on one of its days, [id], read back after something else
     * rewrote it (a Restore previous, an import's update): when the series no
     * longer draws the day ([Days.givenDay]), the editor leaves it for the
     * whole note, as [openNote] would open it. Left there, every save was
     * refused, or went to a day no screen shows (ISSUES_LOG, "The day the
     * editor stayed on"). True when it left.
     */
    fun leaveDayIfNotDrawn(id: Int): Boolean {
        val day = openDay
        if (openId != id || day == null) return false
        val raw = rawNote(id)
        if (raw != null && Recurrence.isRecurring(raw) && Days.givenDay(raw, day)) return false
        openDay = null
        return true
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
        offer("Not saved, so the note stays open: ${Files.ended(why)}", "Leave anyway", go)
    }

    /**
     * A new note's first save: it is that note now. Its card is outlined as
     * the open one, and a change to it from the list reaches the editor.
     */
    fun newNoteSaved(id: Int) {
        // What was folded while it was new is this note's now, and no later
        // new note's.
        for (key in folded.keys.filter { it.startsWith("0:") }) {
            folded.remove(key)?.let { folded["$id:" + key.substringAfter(':')] = it }
        }
        if (openId == 0) openId = id
        // Made from a calendar day too: it stands on that day, and the list,
        // when next on screen, says so if it hides it -- unsaid, the note was
        // nowhere on it.
        noteMade(id)
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
     * A change to [ids] from outside the editor. An open note among them is
     * saved first, so the change lands on what is on screen, and read back
     * after when the change [writes] it; null, the change not made, when its
     * words cannot be saved. A change that [closes] the editor takes the words
     * in "A new item…" as a row; any other leaves them in the box
     * ([saveStaying]), carried by an editor built anew (NoteEditor).
     */
    private fun <T> outside(ids: Collection<Int>, writes: Boolean = true, closes: Boolean = false,
                            day: String? = null, change: () -> T): T? {
        val open = openId
        val touches = open != null && open != 0 && open in ids
        if (touches) (if (closes) flush?.invoke() else saveStaying())?.let { why ->
            say("Not saved, so the open note was left as it is: ${Files.ended(why)}")
            return null
        }
        // Read back only after a change that wrote (a day's delete answers
        // first in a pair), and to one [day] only on that day: read back for
        // nothing, the editor lost its Undo and its cursor.
        val shown = day == null || openDay == day
        return change().also { if (touches && writes && shown && !refused(it)) openNoteRewritten += 1 }
    }

    /** Whether [result] -- a change's answer, or one first in a pair -- is a refusal. */
    private fun refused(result: Any?): Boolean =
        result is SaveResult.Failed || (result as? Pair<*, *>)?.first is SaveResult.Failed

    /**
     * Before an import is applied: the open note saved, so an update replaces
     * it whole. False, and said, when it cannot be: the import reads the note
     * back after, over words no file holds.
     */
    fun beforeImport(): Boolean {
        val why = flush?.invoke() ?: return true.also { openBeforeImport = openId?.let { rawNote(it)?.toString() } }
        say("Not saved, so nothing was imported: ${Files.ended(why)}")
        return false
    }

    /** The open note as the notebook held it before an import ([beforeImport]). */
    private var openBeforeImport: String? = null

    /**
     * After an import: the notebook read again, and the open note with it
     * when the import changed it. Read back untouched, it lost its text's
     * Undo and its cursor for nothing.
     */
    fun afterImport() {
        reload()
        val open = openId
        if (open != null && rawNote(open)?.toString() != openBeforeImport) openNoteRewritten += 1
        openBeforeImport = null
    }

    /** From one day back to the whole series, the editor staying on the note. */
    fun openWholeSeries() {
        openDay = null
    }

    fun note(id: Int): Note? = load.notes.firstOrNull { it.id == id }

    /** A note as the file holds it: what the calendar and the day rules read. */
    fun rawNote(id: Int): JSONObject? {
        // Looked for in place: a list of every note was built per question,
        // and the editor asks at every drawing.
        val raw = load.raw ?: return null
        return Organizing.noteById(raw, id)
    }

    /**
     * A copy of note [id] as it goes out -- shared, exported -- or, opened on
     * one [day] of a series, that day as a note of its own on its date, as the
     * desktop's code of a day goes: the series' own words went out, not the
     * day's, and a code carried the whole series and its identity.
     */
    fun outgoingNote(id: Int, day: String? = null): JSONObject? {
        val raw = rawNote(id) ?: return null
        if (day == null || !Recurrence.isRecurring(raw)) return JSONObject(raw.toString())
        val own = (raw.opt("overrides") as? JSONObject)?.optJSONObject(day)
        return JSONObject(Recurrence.effectiveNote(raw, day).toString()).apply {
            // The day's looks, as Make it a note keeps them (Days): the
            // series' bold and lists, put on the day's other words by their
            // places, went out where the editor showed the day plain.
            val sameBody = Normalize.sameBody(optString("body"), raw.optString("body"))
            put("formatting", if (sameBody) JSONArray((raw.opt("formatting") as? JSONArray)?.toString() ?: "[]")
                              else JSONArray())
            put("paragraphs", Paragraphs.dayParagraphs(raw.opt("paragraphs"), optString("body"), sameBody,
                                                       own?.opt("paragraphs")))
            put("date", day)
            put("recurrence", JSONObject())
            remove("uid")
            put("overrides", JSONObject())
            put("exceptions", org.json.JSONArray())
            put("moved", JSONObject())
        }
    }

    /**
     * How many templates the notebook keeps: the desktop's count, of the list
     * [Templates.load] gives (junk and past 1,000 not counted, a held file not
     * read again: its raw array counted what the page said could not be read).
     */
    fun templatesCount(): Int = Templates.load(dir).size

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
        val painted = Days.withColour(settings, day, colour) ?: return say(FULL_CALENDAR)
        if (!putSetting(Days.DAY_COLORS, painted)) return
        if (colour.isEmpty() && old != null) {
            offerUndo("Day colour cleared") { undoDayClear(day, colour = old) }
        }
    }

    /** A day's markers, three at most; none clears them, with an Undo. */
    fun markDay(day: String, markers: List<String>) {
        val old = dayMarkers[day]
        val marked = Days.withMarkers(settings, day, markers) ?: return say(FULL_CALENDAR)
        if (!putSetting(Days.DAY_EMOJI, marked)) return
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
            val painted = Days.withColour(settings, day, colour) ?: return false.also { say(FULL_CALENDAR) }
            return putSetting(Days.DAY_COLORS, painted)
        }
        if (markers != null) {
            if (!dayMarkers[day].isNullOrEmpty()) {
                say("That day has markers again")
                return false
            }
            val marked = Days.withMarkers(settings, day, markers) ?: return false.also { say(FULL_CALENDAR) }
            return putSetting(Days.DAY_EMOJI, marked)
        }
        return false
    }

    /** One day out of its series, with the Undo the desktop's toast offers. */
    fun deleteDay(id: Int, day: String) {
        // The editor closes only when it is on this day: on another it stays,
        // and the words in "A new item…" with it.
        val (result, gone) = outside(listOf(id), closes = openId == id && openDay == day, day = day) {
            Days.deleteDay(load, id, day)
        } ?: return
        if (!report(result) || gone == null) return
        if (openId == id && openDay == day) closeNote()
        offerUndo("Day removed") { report(Days.undoDeleteDay(load, gone)) }
    }

    fun resetDay(id: Int, day: String, dropText: Boolean = true, sendHome: Boolean = true) {
        val home = rawNote(id)?.let { Recurrence.movedFrom(it, day) }
        val leaves = sendHome && home != null && openId == id && openDay == day
        report(outside(listOf(id), day = day) { Days.resetDay(load, id, day, dropText, sendHome) } ?: return) {
            // The calendar on the day goes home with it, as with Move this day:
            // it stayed on the date the day left.
            if (sendHome && home != null && (selectedDay == day || leaves)) followDay(home)
            // The editor open on the day goes with it, to where it is now.
            if (leaves) openDay = home
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
        // The picker answered on the day itself moves nothing; a move made
        // builds the editor anew on its date, so nothing is read back: the
        // read emptied the text's Undo for a move that never happened.
        if (to == day) return
        val (result, move) = outside(listOf(id), writes = false) { Days.moveDay(load, id, day, to) } ?: return
        if (!report(result) || move == null) return
        // The calendar on the day, beside the editor or reached with Back,
        // goes with it: it stayed on the old date, the moved card gone from it
        // and the editor's outline on nothing.
        if (selectedDay == day || (openId == id && openDay == day)) followDay(to)
        if (openId == id && openDay == day) openDay = to
        offerUndo("Moved to ${shownDay(to)}") {
            // Read back only on the day moved, as the move's other changes
            // are: on the series or another day it lost its Undo.
            report(outside(listOf(move.id), day = move.to) { Days.undoMoveDay(load, move) } ?: return@offerUndo) {
                if (selectedDay == move.to) followDay(move.from)
                if (openId == move.id && openDay == move.to) openDay = move.from
                say("Moved back to ${shownDay(move.from)}")
            }
        }
    }

    /** The calendar on [date], its month with it. */
    private fun followDay(date: String) {
        selectedDay = date
        Recurrence.parseDate(date)?.let { calendarMonth = java.time.YearMonth.from(it) }
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
     * A message with an Undo, for Android's long length: ten seconds from when
     * it shows, longer where the person asked for more time ("Time to take
     * action"), which the bar reads. The desktop gives eight; Android's short
     * four was gone before a thumb got there. A timer of its own, started on
     * the asking, flashed it or kept it for good behind another message.
     */
    private fun offerUndo(message: String, undo: () -> Unit) {
        // The newest takes the bar, as the desktop's banner is replaced: two
        // deletes in a row, and the Undo on screen brought back the first.
        // An earlier Undo still waiting goes too.
        undoBar?.cancel()
        // The offers waiting their turn are held back until it is done, after
        // its own words, as the desktop holds a banner back behind an Undo --
        // thrown away, a hidden note's word was never said.
        val shown = snackbar.currentSnackbarData?.visuals
        heldBack.addAll(offered.filter { o ->
            !o.bar.isCompleted && !(shown != null && shown.message == forTheScreen(o.message) && shown.actionLabel == o.action)
        })
        offered.forEach { it.bar.cancel() }
        offered.clear()
        snackbar.currentSnackbarData?.dismiss()
        undoBar = scope.launch {
            val answer = snackbar.showSnackbar(forTheScreen(message), actionLabel = "Undo",
                duration = SnackbarDuration.Long, withDismissAction = false)
            if (answer == SnackbarResult.ActionPerformed) undo()
            askHeldBackAgain()
        }
    }

    /**
     * The offers an Undo held back, asked again now it is done: each as its
     * maker asks it ([Offered.again]) -- shown as made, a list's Show all ran
     * on a list gone and "hidden by" was said of a note nothing hid
     * -- and none about notes gone since.
     */
    private fun askHeldBackAgain() {
        val waiting = heldBack.toList()
        heldBack.clear()
        for (o in waiting) {
            if (o.about.isNotEmpty() && o.about.none { rawNote(it) != null }) continue
            val again = o.again
            when {
                again != null -> again()
                o.about.isEmpty() -> offer(o.message, o.action, o.onAction)
                else -> offerAbout(o.about, o.message, o.action, null, o.onAction)
            }
        }
    }

    /**
     * Moves notes to the Trash and offers them back ([offerUndo]'s ten
     * seconds) -- the desktop's banner, as Android's snackbar. Undo restores
     * exactly those entries, and opens the note when it was the only one.
     */
    fun trash(ids: Collection<Int>) {
        // A new note's word waiting for the list, and the queued words on these
        // notes, which the delete drops: its Undo gives them back, or the note
        // came back unsaid.
        val wasNew = newNoteMade?.takeIf { it in ids }
        val words = arrivals.filter { word -> word.ids.any { it in ids } }
        // The open note goes to the Trash with its last words in it.
        val result = outside(ids, writes = false, closes = true) { Organizing.trashNotes(load, ids) } ?: return
        if (!report(result)) return
        val count = (result as SaveResult.Ok).imported
        val keys = load.trash.takeLast(count).map { Organizing.keyOf(it) }
        if (openId != null && openId in ids) closeNote()
        offerUndo(if (count == 1) "Moved to Trash" else "Moved $count notes to Trash") {
            val before = load.notes.map { it.id }.toSet()
            report(Organizing.restore(load, keys)) { ok ->
                val given = HashSet<Int>()
                if (count == 1) {
                    for (word in words) {
                        val back = word.ids.map { if (it in ids) ok.id else it }
                        // Into the word still waiting when the delete left part of
                        // it, on any tab: beside it, or left out of it, the list said
                        // the same news twice.
                        val at = arrivals.indexOfFirst { it.said == word.said && it.named == word.named && it.ids.all { id -> id in back } }
                        if (at >= 0) {
                            arrivals = arrivals.toMutableList().also { it[at] = word.copy(ids = back) }
                            given += ok.id
                        } else if (tab != Tab.NOTES) waitWord(back, word.said, word.named)
                    }
                }
                // Asked of the list only when the Undo was tapped there, as
                // newNoteMade is: kept from the Calendar, it moved the list later.
                val put = load.notes.map { it.id }.filter { it !in before }.toSet()
                if (tab == Tab.NOTES) revealAfterUndo = UndoReveal(put, put - given)
                else if (count == 1 && wasNew != null) noteMade(ok.id)
                if (count == 1) openNote(ok.id)
            }
        }
    }

    /**
     * A new note from a template, opened. The note that was open is saved
     * already -- the editor saves before it asks -- so nothing is replaced,
     * and the desktop's "Replace current note?" has nothing to ask about.
     * [date]: the calendar day a new note was started on, which it keeps.
     */
    fun useTemplate(template: JSONObject, said: String, date: String = "") {
        report(Templates.newNote(load, template, load.categories.keys, date)) { ok ->
            openNote(ok.id)
            noteMade(ok.id)
            if (date.isNotEmpty()) templateDayNote = ok.id
            say(said)
        }
    }

    /**
     * The note a template began on a calendar day: begun new as well, so a
     * second template used in it keeps the day -- it counted as a note that
     * was there before, and the next one was made undated.
     */
    var templateDayNote: Int? = null

    /**
     * Text shared from another app that holds no code becomes a note, by the
     * owner's choice: its first line the title, the rest its text; a first
     * line longer than a title keeps the whole text. Made at once, then
     * opened as any note is: held for an open note that could not be saved,
     * the words lived in its "Leave anyway" bar alone, which could drop them.
     */
    fun noteFromShared(text: String, said: String, long: Boolean = false) {
        val (title, body) = sharedAsNote(text)
        report(Notebook.createNote(load, title, body)) { ok ->
            noteMade(ok.id)
            say(said, long)
            openNote(ok.id)
        }
    }

    /** The copy's id, for the list to show it where the sort puts it; null when none was made. */
    fun duplicate(id: Int): Int? {
        var copy: Int? = null
        report(outside(listOf(id), writes = false) { Organizing.duplicate(load, id, attachments) } ?: return null) { ok ->
            copy = ok.id
            say("Duplicated as “${quoted(note(ok.id)?.title ?: "(copy)")}”")
        }
        return copy
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
    fun attach(id: Int, incoming: List<Organizing.Incoming>, unreadable: Int = 0, uid: String? = null) {
        val done = Organizing.attach(load, id, attachments, incoming, uid = uid)
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
        val result = outside(remove, writes = false, closes = true) {
            Organizing.cleanUpDuplicates(load, groups, remove, attachments)
        } ?: return
        if (!report(result)) return
        val count = (result as SaveResult.Ok).imported
        val keys = load.trash.takeLast(count).map { Organizing.keyOf(it) }
        if (openId != null && openId in remove) closeNote()
        offerUndo(if (count == 1) "Moved 1 duplicate to the Trash" else "Moved $count duplicates to the Trash") {
            report(Organizing.restore(load, keys))
        }
    }

    /** A note dragged to just before or after another, in Custom order; true when it was saved. */
    fun moveNote(id: Int, anchor: Int, before: Boolean, grouped: Boolean): Boolean =
        report(Organizing.moveNote(load, id, anchor, before, grouped))

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
        offerUndo("Removed “${quoted(kept.optString("text"))}” from the checklist") {
            // Words being typed into another row are written first: put back
            // under them, the row moved the rows below it. "A new item…" is
            // left in its box, made a row by this Undo.
            saveStaying()?.let { why ->
                sayRefused(why)
                return@offerUndo
            }
            report(Notebook.restoreChecklistItem(load, id, index, kept))
        }
    }

    /**
     * Notes filed under one category, with the Undo the desktop's toast
     * offers: each note back where it was filed ([Organizing.putBackFiled]).
     * A tap on the sheet's next row moved them all, and where each had been
     * was kept nowhere.
     */
    fun moveToCategory(ids: Collection<Int>, category: String) {
        var filed = emptyList<Organizing.Filed>()
        val moved = outside(ids) {
            filed = Organizing.filedNow(load, ids)
            Organizing.moveToCategory(load, ids, category)
        } ?: return
        report(moved) {
            val offer = MoveOffer(category, filed).also { moveOffer = it }
            offerUndo(if (ids.size == 1) "Moved to “${quoted(category)}”" else "Moved ${ids.size} notes to “${quoted(category)}”") {
                if (offer.withdrawn) {
                    say("The category they were moved to was deleted since, so they stay where they are.")
                    return@offerUndo
                }
                val notBack = Organizing.notBack(load, offer.filed, offer.category, offer.deleted).size
                report(outside(ids) { Organizing.putBackFiled(load, offer.filed, offer.category, offer.deleted) }
                    ?: return@offerUndo) {
                    // Said, as the desktop says it: their category was deleted meanwhile.
                    if (notBack > 0) say("$notBack ${if (notBack == 1) "note" else "notes"} could not go back to a category that no longer exists.")
                }
            }
        }
    }

    /**
     * A move's Undo while it is offered: the category the notes went to and where each
     * was, carried through a rename as the desktop's offer is; a category deleted since is
     * kept in [deleted] (its notes stay), and the one they went to, deleted, ends it.
     */
    private class MoveOffer(var category: String, var filed: List<Organizing.Filed>) {
        val deleted = HashSet<String>()
        var withdrawn = false
    }

    private var moveOffer: MoveOffer? = null

    /** A category renamed in Settings: a move's Undo on offer follows the new name. */
    fun categoryRenamed(old: String, new: String) {
        val offer = moveOffer ?: return
        if (offer.category == old) offer.category = new
        offer.filed = offer.filed.map { if (it.category == old) it.copy(category = new) else it }
    }

    /** A category deleted in Settings: a move's Undo on offer leaves its notes be. */
    fun categoryDeleted(name: String) {
        val offer = moveOffer ?: return
        if (offer.category == name) offer.withdrawn = true
        offer.deleted.add(name)
    }

    /**
     * The notes [ids] as one share code, or null when it cannot be one,
     * said. Made away from the screen's thread: it is the notes' whole text
     * deflated, and the screen stood still for it at the tap.
     * From copies, taken here, which a tick in the
     * list cannot change under it meanwhile -- of [day] alone, when the
     * editor is open on one day of a series ([outgoingNote]).
     */
    suspend fun shareCode(ids: Collection<Int>, day: String? = null): String? {
        val raw = outside(ids, writes = false) { load.raw } ?: return null
        val wanted = ids.toSet()
        val notes = if (day != null) ids.mapNotNull { outgoingNote(it, day) }
            else (0 until raw.length()).mapNotNull { raw.opt(it) as? JSONObject }
                .filter { (it.opt("id") as? Number)?.toInt() in wanted }
                .map { JSONObject(it.toString()) }
        if (notes.isEmpty()) return null
        val code = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Sharing.encode(notes) }
        } catch (e: Sharing.TooLarge) {
            say(e.message ?: "")
            return null
        }
        if (Sharing.tooLongToHand(code)) {
            say("That is too much to hand to another app as one code. Share fewer notes at a time, " +
                "or send a backup instead: Settings → Backup.")
            return null
        }
        // The desktop's warning: a code carries the words, never the files.
        if (notes.any { ((it.opt("attachments") as? org.json.JSONArray)?.length() ?: 0) > 0 }) {
            say("Sent without its files: a code carries the words only. A backup carries files too.")
        }
        return code
    }

    // ---- the Trash ----

    fun restoreFromTrash(keys: Collection<Organizing.TrashKey>) {
        val before = noteIds()
        report(Organizing.restore(load, keys)) { ok ->
            say(if (ok.imported == 1) "Restored" else "Restored ${ok.imported} notes")
            arrived(before, ok.imported, "restored", "Restored ${ok.imported} notes")
        }
    }

    /** The ids of the notes there are now. */
    fun noteIds(): Set<Int> = load.notes.map { it.id }.toSet()

    /**
     * Notes just brought in -- restored, imported -- with what was said of
     * them, for the list to say when Show, the search or a folded group hides
     * them ([NotesScreen]): they landed where it did not look, under
     * "Restored" and a list that had not changed.
     */
    var arrivals by mutableStateOf<List<Word>>(emptyList())

    /**
     * A word waiting for the list: about [ids], said as [said] -- or, [named],
     * about one note by the name it has when the word is said ([wordText]):
     * named when queued, a note renamed meanwhile was said by its old name
     *.
     */
    data class Word(val ids: List<Int>, val said: String, val named: Boolean = false)

    companion object {
        /** A new note's [Word.named] word: "“Title” saved in" its category as it is when said ([savedWord]). */
        const val SAVED_IN = "saved in"
    }

    /**
     * One more word for the list's next showing ([arrivals]), after those
     * waiting: one slot, a later word wrote over an earlier one, never said
     *.
     */
    fun waitWord(ids: List<Int>, said: String, named: Boolean = false) {
        arrivals = arrivals + Word(ids, said, named)
    }

    /**
     * The next word for the list to say, taken off [arrivals]: one at a time,
     * each as made. Merged, a count a word carried was doubled, and
     * two notes in two folded groups got one Show all;
     * a word about one note names it instead ([namedWord]).
     */
    fun takeWord(): Word? {
        val word = arrivals.firstOrNull() ?: return null
        arrivals = arrivals.drop(1)
        return word
    }

    /**
     * The notes there are now and were not [before], for the list: the first
     * [count], made first, under their own word, and the rest -- '(earlier
     * text)' notes the same read set aside -- under theirs. Counted in, they
     * said "Added 0 notes"; left out, nothing said where they hid.
     */
    fun arrived(before: Set<Int>, count: Int, one: String, several: String) {
        val new = load.notes.map { it.id }.filter { it !in before }.sorted()
        wordOn(new.take(count), one, several)
        val aside = new.drop(count)
        wordOn(aside, "kept as a note of its own", "Earlier text kept as ${aside.size} notes of their own")
    }

    /** A word on [ids] for the list: by its name when one note, '“Taxes” [one]', or [several]. */
    fun wordOn(ids: List<Int>, one: String, several: String) {
        if (ids.size > 1) waitWord(ids, several)
        else if (ids.isNotEmpty()) waitWord(ids, one, named = true)
    }

    /** What [word] says now: a [Word.named] one by its note's name as it is, null when the note is gone. */
    fun wordText(word: Word): String? {
        if (!word.named) return word.said
        val note = word.ids.singleOrNull()?.let { note(it) } ?: return null
        return if (word.said == SAVED_IN) savedWord(note) else namedWord(note, word.said)
    }

    /**
     * A word about one note, by its name, as the desktop's banner names a
     * hidden note: 'Restored' twice, about two notes, read as the same news
     * said again.
     */
    fun namedWord(note: Note, what: String): String = "“${quoted(note.title.trim().ifEmpty { UNTITLED })}” $what"

    /** A new note [id] the list says when it hides it, by name, as the desktop's banner does. */
    fun savedWord(note: Note): String = namedWord(note, "$SAVED_IN ${note.category.ifEmpty { Themes.PROTECTED_CATEGORY }}")

    /**
     * Note [id], just made, for the list to say ([newNoteMade]). One still
     * waiting goes to the list's other words, as made: written over, the
     * first of two new notes in a row was never said.
     */
    fun noteMade(id: Int) {
        newNoteMade?.takeIf { it != id && note(it) != null }?.let { earlier -> waitWord(listOf(earlier), SAVED_IN, named = true) }
        newNoteMade = id
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
     * default of early releases -- keeps the theme it is wearing and follows
     * no more. Nothing changes on the screen; only the next change of the
     * phone's mode no longer changes the app's.
     */
    fun settleTheme(phoneIsDark: Boolean) {
        // Not over a held file: it would be refused, and said, at every activity made.
        val held = load.settings?.let { Files.writesHeld(it.file) } ?: true
        if (!held && Settings.followsPhone(settings)) chooseTheme(themeName(phoneIsDark))
    }
}

/**
 * Shared text as a new note's title and text ([AppState.noteFromShared]):
 * the first line the title, the rest the text -- or, when the first line is
 * longer than a title holds, the whole text kept as the text too.
 */
internal fun sharedAsNote(text: String): Pair<String, String> {
    // Without the byte-order mark a .txt from Notepad starts with: kept, it
    // stood invisible at the head of the title.
    val whole = text.removePrefix(Char(0xFEFF).toString()).replace("\r\n", "\n").trim()
    val lines = whole.split("\n", limit = 2)
    val first = lines[0].trim()
    val title = Editing.firstLetters(first, Editing.TITLE_CHARS)
    return title to (if (title.length < first.length) whole else lines.getOrElse(1) { "" }.trim('\n'))
}
