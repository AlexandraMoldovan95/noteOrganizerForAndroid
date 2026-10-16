package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ro.noteorganizer.app.ui.theme.LocalPalette
import java.io.File
import java.io.IOException

/**
 * What the person sent last time -- the address and the two choices -- kept
 * on this phone alone, in the app's private preferences: never in
 * settings.json, which a backup carries to the desktop.
 */
private const val PREFS = "mail"

/**
 * "Send by email…": an address, what the message carries, and the phone's
 * mail app opened with it ready. The app sends nothing itself; it stays
 * the offline app it is, and says so on the dialog.
 */
@Composable
fun MailDialog(state: AppState, noteId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var to by rememberSaveable { mutableStateOf(prefs.getString("to", "") ?: "") }
    var withText by rememberSaveable { mutableStateOf(prefs.getBoolean("text", true)) }
    var withPage by rememberSaveable { mutableStateOf(prefs.getBoolean("page", true)) }
    // The note as saved -- the menu saved it before opening this.
    val note = state.rawNote(noteId)
    val slip = Mailing.notAnAddress(to)
    val ready = note != null && slip == null && (withText || withPage)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mail_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text(stringResource(R.string.mail_to)) },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                    isError = slip != null,
                    supportingText = {
                        Text(if (slip != null) stringResource(R.string.mail_not_address, slip)
                             else stringResource(R.string.mail_to_hint))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Choice(stringResource(R.string.mail_with_text), stringResource(R.string.mail_with_text_sub), withText) {
                    withText = it
                }
                Choice(stringResource(R.string.mail_with_page), stringResource(R.string.mail_with_page_sub), withPage) {
                    withPage = it
                }
                if (!withText && !withPage) {
                    Text(stringResource(R.string.mail_choose_one), color = palette.dangerText, fontSize = 13.sp)
                }
                if (note != null && Mailing.hasFiles(note)) {
                    Text(stringResource(R.string.mail_no_files), color = palette.subtext, fontSize = 13.sp,
                         modifier = Modifier.padding(top = 6.dp))
                }
                Text(stringResource(R.string.mail_nothing_sent), color = palette.subtext, fontSize = 13.sp,
                     modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = {
                val saved = note ?: return@TextButton
                prefs.edit().putString("to", to.trim()).putBoolean("text", withText).putBoolean("page", withPage).apply()
                onDismiss()
                openMail(context, state, Mailing.message(saved, to, withText, withPage))
            }) { Text(stringResource(R.string.mail_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
        titleContentColor = palette.text,
        textContentColor = palette.text,
    )
}

/** A choice the whole row answers, the box and the words alike. */
@Composable
private fun Choice(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, color = palette.text, fontSize = 15.sp)
            Text(sub, color = palette.subtext, fontSize = 12.sp)
        }
    }
}

/**
 * The message handed to a mail app. ACTION_SEND carries an attachment,
 * which a mailto: link cannot; and it goes to the apps that answer a
 * mailto: link, each by name, so a chat app is never offered a message
 * with an address on it.
 *
 * By name, not by a mailto: selector, the usual way of saying "mail apps
 * only": Android 17 refused that on the emulator (START_INTENT_NOT_RESOLVED,
 * Gmail installed), the selector's SENDTO and the message's SEND being two
 * different actions for the filter that matched.
 */
private fun openMail(context: Context, state: AppState, message: Mailing.Message) {
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, message.subject)
    if (message.to.isNotEmpty()) send.putExtra(Intent.EXTRA_EMAIL, message.to.toTypedArray())
    message.text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
    val page = message.attachment
    if (page == null) {
        send.type = "text/plain"
    } else {
        // One page at a time in the cache: the last one is cleared here, not
        // when the mail app returns -- it may still be reading it then.
        val folder = File(context.cacheDir, "mail")
        val file = File(folder, page.first)
        try {
            folder.deleteRecursively()
            folder.mkdirs()
            file.writeText(page.second, Charsets.UTF_8)
        } catch (e: IOException) {
            state.say(context.getString(R.string.mail_failed))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        send.type = "text/html"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.clipData = ClipData.newUri(context.contentResolver, page.first, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // The window each mail app writes a mailto: link in -- by its name, not
    // its app's: Gmail takes a SEND in two places, its mail and its chat,
    // and an app named alone made Android ask which.
    val manager = context.packageManager
    @Suppress("DEPRECATION") // the flags-object form is from API 33; this one serves them all
    fun activities(intent: Intent) = manager.queryIntentActivities(intent, 0).map { it.activityInfo }
    val writers = activities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        .distinctBy { it.packageName to it.name }
    // ...and of those, the ones that take this message too: a window that
    // writes mail may not take an attachment.
    val targets = writers.filter { writer ->
        activities(Intent(send).setPackage(writer.packageName)).any { it.name == writer.name }
    }.map { Intent(send).setClassName(it.packageName, it.name) }
    try {
        when (targets.size) {
            0 -> state.say(context.getString(R.string.mail_no_app))
            1 -> context.startActivity(targets[0])
            else -> context.startActivity(
                Intent.createChooser(targets[0], context.getString(R.string.mail_title))
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray()))
        }
    } catch (e: ActivityNotFoundException) {
        state.say(context.getString(R.string.mail_no_app))
    }
}
