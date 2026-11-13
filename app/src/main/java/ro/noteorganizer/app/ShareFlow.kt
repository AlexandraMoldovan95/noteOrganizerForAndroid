package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
 * The phone's share sheet for [send], this app left off it: sent to itself,
 * a note's words came back as a copy of the note, and a code as an import of
 * notes already here.
 */
internal fun chooserWithoutThisApp(context: Context, send: Intent, title: String): Intent =
    Intent.createChooser(send, title)
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))

/**
 * "Share": the note as a PDF, a web page, its words or a share code,
 * chosen on one sheet, then handed to the phone's own share sheet --
 * WhatsApp, a mail app, Teams, Drive, Quick Share, and the people those
 * apps put at its top. It replaced "Send by email" and the 🔗: one door
 * out instead of two, the address typed where the mail is written.
 */
@Composable
fun ShareSheet(state: AppState, noteId: Int, day: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    // The note as saved -- the button saved it before opening this -- or the
    // [day] the editor is on ([AppState.outgoingNote]); copied only for a day:
    // a note of some megabytes copied at every opening held the screen.
    val note = remember(noteId, day, state.revision) { if (day == null) state.rawNote(noteId) else state.outgoingNote(noteId, day) }
    val table = note != null && Outgoing.hasTable(note)
    // Said before the tap, as a table is, so the person can still choose: words
    // past what may be handed over go as a page, and a code past its ceiling is
    // refused. Weighed off the screen's thread; a tap meanwhile is refused after it.
    val subject = remember(note) { note?.let { Outgoing.subject(Exporting.titleOf(it)) } ?: "" }
    val weighed by produceState<Weighed?>(null, note) {
        value = note?.let { withContext(Dispatchers.Default) { Weighed.of(it, table, subject) } }
    }
    val words = weighed?.words
    val tooLong = weighed?.tooLong == true
    val code = weighed?.code
    fun go(format: String) {
        onDismiss()
        // On the app's own scope: the sheet is gone before the PDF is.
        state.scope.launch { handOver(context, state, noteId, day, format) }
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
                       when {
                           table -> stringResource(R.string.share_text_table)
                           tooLong -> Outgoing.tooLongNumbers(words!!, subject).let { (count, most) ->
                               stringResource(R.string.share_text_too_long, count, most)
                           }
                           else -> stringResource(R.string.share_text_sub)
                       },
                       enabled = !table && !tooLong) { go(Outgoing.TEXT) }
            val codeShut = code != null && code !is Outgoing.Code.Fits
            OptionCard("🔗", stringResource(R.string.share_code_title),
                       when (code) {
                           is Outgoing.Code.TooLong -> stringResource(R.string.share_code_too_long, code.characters, code.most)
                           is Outgoing.Code.TooMuch -> stringResource(R.string.share_code_too_much)
                           else -> stringResource(R.string.share_code_sub)
                       },
                       enabled = !codeShut) { go(Outgoing.CODE) }
            if (note != null && Outgoing.hasFiles(note)) {
                Text(stringResource(R.string.share_no_files), color = palette.subtext, fontSize = 13.sp)
            }
            Text(stringResource(R.string.share_how), color = palette.subtext, fontSize = 13.sp)
        }
    }
}

/** What the Share sheet says of a note's words and its code, before the tap. */
internal class Weighed(val words: String?, val tooLong: Boolean, val code: Outgoing.Code) {
    companion object {
        fun of(note: JSONObject, table: Boolean, subject: String): Weighed {
            val words = if (table) null else Outgoing.text(note, Exporting.titleOf(note))
            return Weighed(words, words != null && Outgoing.tooLongForWords(words, subject), Outgoing.codeFor(note))
        }
    }
}

/**
 * Share -> PDF, for a note the PDF would leave part of: what it would leave
 * out, in words, before anything is handed over -- the web page, which
 * carries all of it, first; the PDF as it is; or nothing. Back, or a tap
 * outside, is Cancel.
 */
@Composable
fun PdfQuestionDialog(question: AppState.PdfQuestion, onAnswer: (AppState.PdfChoice) -> Unit) {
    val palette = LocalPalette.current
    val pages = PdfPrint.MAX_PAGES
    val text = when {
        question.firstPagesOnly && question.cutAtEdge -> stringResource(R.string.pdf_ask_both, pages)
        question.firstPagesOnly -> stringResource(R.string.pdf_ask_pages, pages)
        else -> stringResource(R.string.pdf_ask_edge)
    }
    val pdf = if (question.firstPagesOnly) stringResource(R.string.pdf_ask_pdf_pages, pages)
              else stringResource(R.string.pdf_ask_pdf_edge)
    AlertDialog(
        onDismissRequest = { onAnswer(AppState.PdfChoice.CANCEL) },
        title = { Text(stringResource(R.string.pdf_ask_title)) },
        text = { DialogBody { Text(text) } },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onAnswer(AppState.PdfChoice.PAGE) }) {
                    Text(stringResource(R.string.pdf_ask_page), color = palette.accentText, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onAnswer(AppState.PdfChoice.PDF) }) {
                    Text(pdf, color = palette.accentText)
                }
                TextButton(onClick = { onAnswer(AppState.PdfChoice.CANCEL) }) {
                    Text(stringResource(R.string.cancel), color = palette.text)
                }
            }
        },
        containerColor = palette.card,
        titleContentColor = palette.text,
        textContentColor = palette.subtext,
    )
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
private suspend fun handOver(context: Context, state: AppState, noteId: Int, day: String?, format: String) {
    if (!preparing.run { prepare(context, state, noteId, day, format) }) state.say(context.getString(R.string.share_busy))
}

/**
 * A PDF or a page written to the cache for the app picked to read, the
 * words in the message, or a share code; then the phone's share sheet.
 * One file at a time in the cache: the last one is cleared here, not when
 * the other app returns -- it may still be reading it then.
 */
private suspend fun prepare(context: Context, state: AppState, noteId: Int, day: String?, format: String) {
    if (format == Outgoing.CODE) {
        state.shareCode(listOf(noteId), day)?.let { sendText(state, it) }
        return
    }
    // A copy, taken here: the page is worked out away from the screen,
    // which a tick in the list may change the note under meanwhile.
    val note = state.outgoingNote(noteId, day) ?: return
    val title = Exporting.titleOf(note)
    val subject = Outgoing.subject(title)
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, subject)
    if (format == Outgoing.TEXT) {
        val words = withContext(Dispatchers.Default) { Outgoing.text(note, title) }
        // Refused here, kindly, as a code past the same ceiling is: handed
        // over, it stopped the app in the share sheet. The sheet said so
        // before the tap; this is the note as it is now.
        if (Outgoing.tooLongForWords(words, subject)) {
            val (count, most) = Outgoing.tooLongNumbers(words, subject)
            state.say(context.getString(R.string.share_text_too_long, count, most), long = true)
            return
        }
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, words)
    } else {
        val folder = File(context.cacheDir, "share")
        var file = File(folder, Outgoing.fileName(title, format))
        var kind = format
        // Off the screen's thread: a note can hold a table of tens of
        // thousands of cells. The PDF's drawing stays on it, as a WebView must.
        val page = withContext(Dispatchers.Default) { Exporting.content(note, Exporting.HTML, title) }
        var printed: PdfPrint.Printed? = null
        val written = try {
            folder.deleteRecursively()
            folder.mkdirs()
            if (format == Outgoing.PDF) {
                printed = PdfPrint.print(context, Outgoing.forPrint(page), file)
                printed.written && file.length() > 0
            } else { file.writeText(page, Charsets.UTF_8); true }
        } catch (e: IOException) {
            false
        }
        if (!written) {
            state.say(context.getString(R.string.share_failed))
            return
        }
        // A PDF that would leave part of the note out is asked about before
        // anything is handed over: said in a bar, the share sheet covered it
        // until its time ran out. Nothing is written into the PDF itself.
        printed?.takeIf { it.firstPagesOnly || it.cutAtEdge }?.let { p ->
            when (state.askPdf(p.firstPagesOnly, p.cutAtEdge)) {
                AppState.PdfChoice.CANCEL -> {
                    folder.deleteRecursively()
                    return
                }
                AppState.PdfChoice.PAGE -> {
                    file.delete()
                    kind = Outgoing.PAGE
                    file = File(folder, Outgoing.fileName(title, kind))
                    val wrote = try { file.writeText(page, Charsets.UTF_8); true } catch (e: IOException) { false }
                    if (!wrote) {
                        state.say(context.getString(R.string.share_failed))
                        return
                    }
                }
                AppState.PdfChoice.PDF -> Unit
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        send.type = if (kind == Outgoing.PDF) "application/pdf" else "text/html"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(chooserWithoutThisApp(context, send, context.getString(R.string.share_title)))
    } catch (e: ActivityNotFoundException) {
        state.say(context.getString(R.string.share_no_app))
    } catch (e: RuntimeException) {
        // What Android's buffer refuses comes back as "Failure from system",
        // a TransactionTooLargeException inside: said, never the app stopped.
        state.say(context.getString(R.string.share_handed_failed), long = true)
    }
}
