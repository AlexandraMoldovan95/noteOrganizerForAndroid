package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.noteorganizer.app.ui.theme.AppTheme
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.paletteOf
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one Activity. It finds the notebook, readies it, and hands the
 * screens the few things only an Activity can do: Android's share sheet,
 * its file pickers, opening a file in another app, and the browser.
 */
class MainActivity : ComponentActivity() {

    /** A share code handed over by another app, for the import panel. */
    private val incoming = mutableStateOf<String?>(null)

    private var flush: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Private to the app: no other app can read it, and uninstalling the
        // app removes it -- which is why Settings → Backup exists.
        val dir = File(filesDir, "notebook")
        // Where the development tools pushed a notebook before this one.
        val notice = Startup.prepare(dir, getExternalFilesDir(null))
        incoming.value = codeIn(intent)
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { AppState(dir, scope).also { it.notice = notice } }
            LaunchedEffect(state) {
                state.sendText = { text -> share(text) }
                state.openFile = { file, name -> open(file, name) }
                flush = { state.flush?.invoke() }
            }
            val waiting by incoming
            LaunchedEffect(waiting) {
                if (waiting != null) {
                    state.incomingCode = waiting
                    incoming.value = null
                }
            }
            val palette = paletteOf(state.themeName(isSystemInDarkTheme()))
            LaunchedEffect(palette.isDark) {
                // The clock and the battery in the theme's own contrast.
                val transparent = AndroidColor.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (palette.isDark) SystemBarStyle.dark(transparent)
                                     else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (palette.isDark) SystemBarStyle.dark(transparent)
                                         else SystemBarStyle.light(transparent, transparent),
                )
            }
            AppTheme(palette) { AppRoot(state, versionName()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        codeIn(intent)?.let { incoming.value = it }
    }

    /** Whatever the editor holds is saved when the app leaves the screen. */
    override fun onStop() {
        flush?.invoke()
        super.onStop()
    }

    private fun codeIn(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.share_code)))
    }

    private fun open(file: File, name: String) {
        if (!file.isFile) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val type = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, R.string.no_app_opens, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun versionName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
}

/**
 * The app: the note list or Settings, with the bar at the bottom; a note,
 * when one is open, over the whole screen; and the snackbar above it all.
 */
@Composable
fun AppRoot(state: AppState, version: String) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var backupPlan by remember { mutableStateOf<Pair<Importing.Plan, Backup.Contents>?>(null) }
    var askBackup by remember { mutableStateOf<Backup.Contents?>(null) }

    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = context.getString(R.string.exporting)
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        Backup.export(state.dir, context.cacheDir, out)
                    } ?: return@withContext "The file could not be opened for writing."
                    null
                } catch (e: Exception) {
                    // A half-written backup is worse than none: it looks like one.
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    } catch (_: Exception) {
                    }
                    e.message ?: e.toString()
                }
            }
            busy = null
            if (problem == null) {
                state.putSetting(Settings.LAST_EXPORT_AT,
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                state.say(context.getString(R.string.export_done))
            } else {
                state.say(context.getString(R.string.export_failed, problem))
            }
        }
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = context.getString(R.string.importing)
        scope.launch {
            val read = withContext(Dispatchers.IO) { readBackup(context, uri) }
            busy = null
            when (read) {
                is Backup.Read.Failed -> state.say(read.reason)
                is Backup.Read.Ok -> askBackup = read.contents
            }
        }
    }
    val backup = BackupActions(
        export = { exportTo.launch("NotesBackup_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip") },
        import = { importFrom.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        busy = { busy },
    )

    // Back from Settings goes to the notes, as Back from any second place
    // in an app does; only from the notes does it leave the app.
    androidx.activity.compose.BackHandler(
        enabled = state.openId == null && state.tab == Tab.SETTINGS && state.settingsPage == null,
    ) { state.tab = Tab.NOTES }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        val open = state.openId
        if (open != null) {
            NoteEditor(state, open)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (state.tab) {
                        Tab.NOTES -> NotesScreen(state, onImportCode = { importing = true })
                        Tab.SETTINGS -> SettingsScreen(state, backup, version, openFeedback = { openFeedback(context, state, version) })
                    }
                }
                NavigationBar(containerColor = palette.sidebar) {
                    for ((tab, label, mark) in listOf(
                        Triple(Tab.NOTES, R.string.nav_notes, "≣"),
                        Triple(Tab.SETTINGS, R.string.nav_settings, "⚙"),
                    )) {
                        NavigationBarItem(
                            selected = state.tab == tab,
                            onClick = { state.tab = tab; state.settingsPage = null },
                            icon = { Text(mark, fontSize = 20.sp) },
                            label = { Text(stringResource(label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.text,
                                selectedTextColor = palette.text,
                                unselectedIconColor = palette.subtext,
                                unselectedTextColor = palette.subtext,
                                indicatorColor = palette.calendarHasNotes,
                            ),
                        )
                    }
                }
            }
        }
        // Above the bar at the bottom, and on the list above the + as well:
        // a message that half covers the button it sits next to reads as
        // a mistake.
        val onList = open == null && state.tab == Tab.NOTES
        SnackbarHost(
            state.snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (open != null) 8.dp else if (onList) 160.dp else 80.dp),
        )
    }

    val code = state.incomingCode
    if (importing || code != null) {
        CodeImportDialog(state, code ?: "", onClose = { importing = false; state.incomingCode = null })
    }
    askBackup?.let { contents ->
        Ask(
            title = stringResource(R.string.import_notes_title),
            text = stringResource(R.string.import_notes_text),
            confirm = stringResource(R.string.continue_label),
            onConfirm = {
                askBackup = null
                backupPlan = Importing.plan(state.load.raw ?: org.json.JSONArray(), contents.notes,
                    Importing.Source.BACKUP) to contents
            },
            onDismiss = {
                askBackup = null
                contents.folder?.deleteRecursively()
            },
        )
    }
    backupPlan?.let { (plan, contents) ->
        // The unpacked backup leaves the cache however the questions end:
        // nothing of an import that was not taken is kept.
        ImportQuestions(state, plan, onCancel = {
            backupPlan = null
            contents.folder?.deleteRecursively()
        }) { answers ->
            finishImport(state, plan, answers, state.attachments, contents.extras())
            backupPlan = null
            contents.folder?.deleteRecursively()
        }
    }
}

/**
 * A backup the person picked, copied into the app's cache first -- a
 * document from a file picker can only be streamed, and the guards read
 * the archive's directory before unpacking anything -- then read.
 */
private fun readBackup(context: android.content.Context, uri: Uri): Backup.Read {
    val work = File(context.cacheDir, "import_" + System.currentTimeMillis())
    work.mkdirs()
    val zip = File(context.cacheDir, "import_" + System.currentTimeMillis() + ".zip")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            zip.outputStream().use { input.copyTo(it, 1 shl 20) }
        } ?: return Backup.Read.Failed("That file couldn't be opened.")
        Backup.read(zip, work, free = context.cacheDir.usableSpace).also {
            if (it is Backup.Read.Failed) work.deleteRecursively()
        }
    } catch (e: Exception) {
        work.deleteRecursively()
        Backup.Read.Failed("That file couldn't be read as a backup:\n${e.message ?: e}")
    } finally {
        zip.delete()
    }
}

/**
 * The desktop's feedback page, carrying the details the form shows under
 * the person's words -- the version, the theme, how big the notebook is.
 * Never what a note says. The app itself connects to nothing: the browser
 * opens the page, and nothing is sent until Send is pressed there.
 */
private fun openFeedback(context: android.content.Context, state: AppState, version: String) {
    val files = state.load.notes.sumOf { it.attachments }
    val mb = state.load.file?.length()?.let { Math.round(it / 1e5) / 10.0 }
    val details = listOf(
        "version" to "android $version",
        "theme" to (Themes.LABELS[state.themeName(false)] ?: ""),
        "notes" to state.load.notes.size.toString(),
        "files" to files.toString(),
        "mb" to (mb?.toString() ?: ""),
        "build" to "android",
    ).filter { it.second.isNotEmpty() }
    val query = details.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    val page = Uri.parse("https://note-organizer-8f7772.netlify.app/feedback.html?$query")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, page))
    } catch (e: ActivityNotFoundException) {
        state.say(context.getString(R.string.no_browser))
    }
}
