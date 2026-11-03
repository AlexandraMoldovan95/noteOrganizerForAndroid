package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ro.noteorganizer.app.ui.theme.LocalPalette
import java.io.IOException
import java.io.OutputStream

/** Starts an export of one note in one format; the rest happens in Android's picker. */
class Exporter internal constructor(private val start: (Int, String, String?) -> Unit) {
    /** The note, or the one [day] of it the editor is open on ([AppState.outgoingNote]). */
    fun export(noteId: Int, format: String, day: String? = null) = start(noteId, format, day)
}

/**
 * The desktop's "Export as file", with Android's own "save as" in place of
 * Windows' -- the person chooses the folder and the name, which starts as
 * the desktop's. What is written is the note as saved, worked out when the
 * picker answers, so nothing typed in between is left out.
 *
 * Afterwards the message offers to open the file, as the desktop asks
 * "Open it now?": the file itself is the only honest preview of how the
 * note lays out. A cancelled picker writes nothing.
 *
 * Remembered by AppRoot, not by the editor: Android may end the app behind
 * the picker and start it again on its list, and an editor's exporter was
 * not there to take the answer -- the file the picker had made stayed
 * empty, and nothing was said.
 */
@Composable
fun rememberExporter(state: AppState): Exporter {
    val context = LocalContext.current
    // Which note, in which format, while the picker -- another activity --
    // is up: kept as text, so it survives that trip, the app's end included.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun finish(uri: Uri?) {
        val job = pending ?: return
        pending = null
        if (uri == null) return
        val parts = job.split(":", limit = 3)
        val id = parts[0].toIntOrNull() ?: return
        val format = parts.getOrNull(1) ?: return
        // A copy, taken here: the page is worked out and written away from
        // the screen, which a tick in the list may change the note under.
        val note = state.outgoingNote(id, parts.getOrNull(2)?.ifEmpty { null }) ?: return
        val title = Exporting.titleOf(note)
        state.scope.launch {
            // Off the screen's thread: a note can hold a table of tens of
            // thousands of cells, and the place another app's.
            val written = withContext(Dispatchers.IO) {
                try {
                    openForWriting(context, uri).use { it.write(Exporting.content(note, format, title).toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
            if (!written) {
                state.say(context.getString(R.string.note_export_failed))
                return@launch
            }
            val name = withContext(Dispatchers.IO) { displayName(context, uri) } ?: Exporting.fileName(title, format)
            var message = context.getString(R.string.note_exported_to, quotedFile(name))
            // The desktop says so too: a single note's file never carries its files.
            if (((note.opt("attachments") as? JSONArray)?.length() ?: 0) > 0) {
                message += " " + context.getString(R.string.note_export_no_files)
            }
            state.offer(message, context.getString(R.string.note_export_open)) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, Exporting.mimeType(format))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    state.say(context.getString(R.string.no_app_opens))
                }
            }
        }
    }

    val html = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Exporting.mimeType(Exporting.HTML))) { finish(it) }
    val txt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Exporting.mimeType(Exporting.TXT))) { finish(it) }

    return remember {
        Exporter { id, format, day ->
            val note = state.outgoingNote(id, day) ?: return@Exporter
            pending = "$id:$format:${day.orEmpty()}"
            val name = Exporting.fileName(Exporting.titleOf(note), format)
            if (format == Exporting.HTML) html.launch(name) else txt.launch(name)
        }
    }
}

/** Truncating where the place allows it: a file written over must not keep its old tail. */
private fun openForWriting(context: Context, uri: Uri): OutputStream {
    val resolver = context.contentResolver
    // Some places refuse "wt" outright, by either exception; plain "w" is
    // then the only way in, and its own failure is the one reported.
    val stream = try {
        resolver.openOutputStream(uri, "wt")
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IOException) {
        null
    } ?: resolver.openOutputStream(uri, "w")
    return stream ?: throw IOException("no stream for $uri")
}

/** The name the file was given -- the person may have changed it in the picker. */
private fun displayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    null
}

/**
 * The desktop's two formats, as two cards in the Share sheet's manner: a
 * mark, a name, what each gives. Saving a file where the person chooses,
 * which Share -- handing the note to an app or a person -- does not.
 */
@Composable
fun ExportDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_export_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.note_export_where), color = palette.subtext, fontSize = 13.sp)
                OptionCard("🌐", stringResource(R.string.note_export_html), stringResource(R.string.note_export_html_sub)) {
                    onPick(Exporting.HTML)
                }
                OptionCard("📄", stringResource(R.string.note_export_txt), stringResource(R.string.note_export_txt_sub)) {
                    onPick(Exporting.TXT)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
        titleContentColor = palette.text,
    )
}
