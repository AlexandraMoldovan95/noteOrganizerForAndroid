package ro.noteorganizer.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Where the app is. The calendar joins these in Phase 2. */
enum class Tab { NOTES, SETTINGS }

/** One page of Settings, or the list of them. */
enum class SettingsPage { NAME, APPEARANCE, CATEGORIES, BACKUP, TRASH, ABOUT }

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

    var tab by mutableStateOf(Tab.NOTES)
    var settingsPage by mutableStateOf<SettingsPage?>(null)

    /** The note open in the editor; null for none, 0 for one not saved yet. */
    var openId by mutableStateOf<Int?>(null)

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
    }

    /** From the disk, for when the files changed under the app (an import). */
    fun reload() {
        load = Notebook.load(dir)
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

    fun openNote(id: Int) {
        openId = id
    }

    fun newNote() {
        openId = 0
    }

    fun closeNote() {
        openId = null
    }

    fun note(id: Int): Note? = load.notes.firstOrNull { it.id == id }

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
        if (openId != null && openId in ids) openId = null
        scope.launch {
            // Eight seconds, as the desktop's banner: long enough to read it
            // and decide. Android's own lengths are four and ten.
            val timer = launch {
                delay(8_000)
                snackbar.currentSnackbarData?.dismiss()
            }
            val answer = snackbar.showSnackbar(
                message = if (count == 1) "Moved to Trash" else "Moved $count notes to Trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite,
                withDismissAction = false,
            )
            timer.cancel()
            if (answer == SnackbarResult.ActionPerformed) {
                report(Organizing.restore(load, keys)) { ok ->
                    if (openAfterUndo || count == 1) openId = ok.id
                }
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
