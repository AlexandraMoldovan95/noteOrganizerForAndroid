package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ro.noteorganizer.app.ui.theme.LocalPalette
import java.io.File
import java.io.IOException

/**
 * "Share": the note as a PDF, a web page, its words or a share code,
 * chosen on one sheet, then handed to the phone's own share sheet --
 * WhatsApp, a mail app, Teams, Drive, Quick Share, and the people those
 * apps put at its top. It replaced "Send by email" and the 🔗: one door
 * out instead of two, the address typed where the mail is written.
 */
@Composable
fun ShareSheet(state: AppState, noteId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    // The note as saved -- the button saved it before opening this.
    val note = state.rawNote(noteId)
    val table = note != null && Outgoing.hasTable(note)
    fun go(format: String) {
        onDismiss()
        // On the app's own scope: the sheet is gone before the PDF is.
        state.scope.launch { handOver(context, state, noteId, format) }
    }
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.share_as), color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            OptionCard("📄", stringResource(R.string.share_pdf), stringResource(R.string.share_pdf_sub)) { go(Outgoing.PDF) }
            OptionCard("🌐", stringResource(R.string.share_page), stringResource(R.string.share_page_sub)) { go(Outgoing.PAGE) }
            OptionCard("Aa", stringResource(R.string.share_text),
                       stringResource(if (table) R.string.share_text_table else R.string.share_text_sub),
                       enabled = !table) { go(Outgoing.TEXT) }
            OptionCard("🔗", stringResource(R.string.share_code_title), stringResource(R.string.share_code_sub)) {
                go(Outgoing.CODE)
            }
            if (note != null && Outgoing.hasFiles(note)) {
                Text(stringResource(R.string.share_no_files), color = palette.subtext, fontSize = 13.sp)
            }
            Text(stringResource(R.string.share_how), color = palette.subtext, fontSize = 13.sp)
        }
    }
}

/**
 * One choice, as a card: a mark, a name, a line saying what it gives. The
 * Share sheet's and Export's alike, so the two read as one set.
 */
@Composable
internal fun OptionCard(mark: String, title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val ink = if (enabled) palette.text else palette.subtext
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(palette.calendarHasNotes),
            contentAlignment = Alignment.Center) {
            Text(mark, fontSize = 18.sp, color = ink, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = palette.subtext, fontSize = 13.sp)
        }
    }
}

/** The share mark -- three dots joined, as phones draw Share -- for the note's top bar. */
@Composable
fun ShareMark(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val r = size.minDimension * 0.13f
        val right = size.width - r * 1.4f
        val left = r * 1.4f
        val top = Offset(right, r * 1.4f)
        val middle = Offset(left, size.height / 2)
        val bottom = Offset(right, size.height - r * 1.4f)
        val line = size.minDimension * 0.08f
        drawLine(colour, middle, top, line)
        drawLine(colour, middle, bottom, line)
        for (at in listOf(top, middle, bottom)) drawCircle(colour, r, at)
    }
}

/** One share made ready at a time, for the whole app: [Outgoing.OneAtATime]. */
private val preparing = Outgoing.OneAtATime()

/** The note handed over, as [format] -- or, while the last one is still made, a word that it is. */
private suspend fun handOver(context: Context, state: AppState, noteId: Int, format: String) {
    if (!preparing.run { prepare(context, state, noteId, format) }) state.say(context.getString(R.string.share_busy))
}

/**
 * A PDF or a page written to the cache for the app picked to read, the
 * words in the message, or a share code; then the phone's share sheet.
 * One file at a time in the cache: the last one is cleared here, not when
 * the other app returns -- it may still be reading it then.
 */
private suspend fun prepare(context: Context, state: AppState, noteId: Int, format: String) {
    if (format == Outgoing.CODE) {
        state.shareCode(listOf(noteId))?.let { sendText(state, it) }
        return
    }
    // A copy, taken here: the page is worked out away from the screen,
    // which a tick in the list may change the note under meanwhile.
    val note = state.rawNote(noteId)?.let { JSONObject(it.toString()) } ?: return
    val title = Exporting.titleOf(note)
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, title)
    if (format == Outgoing.TEXT) {
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, Outgoing.text(note, title))
    } else {
        val folder = File(context.cacheDir, "share")
        val file = File(folder, Outgoing.fileName(title, format))
        // Off the screen's thread: a note can hold a table of tens of
        // thousands of cells. The PDF's drawing stays on it, as a WebView must.
        val page = withContext(Dispatchers.Default) { Exporting.content(note, Exporting.HTML, title) }
        val written = try {
            folder.deleteRecursively()
            folder.mkdirs()
            if (format == Outgoing.PDF) PdfPrint.print(context, page, file) && file.length() > 0
            else { file.writeText(page, Charsets.UTF_8); true }
        } catch (e: IOException) {
            false
        }
        if (!written) {
            state.say(context.getString(R.string.share_failed))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        send.type = if (format == Outgoing.PDF) "application/pdf" else "text/html"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title)))
    } catch (e: ActivityNotFoundException) {
        state.say(context.getString(R.string.share_no_app))
    }
}
