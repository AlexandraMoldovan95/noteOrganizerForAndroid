package ro.noteorganizer.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.noteorganizer.app.ui.theme.AppTheme
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.LocalThemeChange
import ro.noteorganizer.app.ui.theme.Palette
import ro.noteorganizer.app.ui.theme.ThemeVeil
import ro.noteorganizer.app.ui.theme.paletteOf
import java.io.File
import java.time.LocalDate
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

    companion object {
        /** Whether this process has cleared the cache's leftovers yet ([Startup.clearLeftovers]). */
        private var leftoversCleared = false
    }

    /** Counts the times the reminder's notification was tapped: today, on the calendar. */
    private val todayTapped = mutableIntStateOf(0)

    /** Counts the returns to the app, where the day may have turned since the last. */
    private val resumes = mutableIntStateOf(0)

    private var flush: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once: the bars transparent, with no scrim over three-button
        // navigation. Their icons' contrast follows the theme (showBars).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // Private to the app: no other app can read it, and uninstalling the
        // app removes it -- which is why Settings → Backup exists.
        val dir = File(filesDir, "notebook")
        // Where the development tools pushed a notebook before this one --
        // on a development build only (Startup.olderFolder).
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val notice = Startup.prepare(dir, Startup.olderFolder(debuggable, getExternalFilesDir(null)))
        // Once a process, before anything of its own can be there.
        if (!leftoversCleared) {
            leftoversCleared = true
            Startup.clearLeftovers(cacheDir)
        }
        // What the app was started with is acted on once. Restored -- after
        // Android ended the process in the background, or a change the app
        // does not take itself -- or reopened from Recents, the activity is
        // handed the same intent again: a share code that started the app
        // was offered again, its "Update this note" ready to put the older
        // words back over the newer.
        val fresh = savedInstanceState == null &&
            ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (fresh) {
            incoming.value = codeIn(intent)
            if (intent?.action == ReminderAlarm.ACTION_TODAY) todayTapped.intValue += 1
        }
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { AppState(dir, scope).also { it.notice = notice; it.draftDir = noBackupFilesDir } }
            LaunchedEffect(state) {
                state.sendText = { text -> share(text) }
                state.openFile = { file, name -> open(file, name) }
                flush = {
                    (state.flushAway ?: state.flush)?.invoke()
                    state.keepTemplateDraft?.invoke()
                }
            }
            val waiting by incoming
            val sharedNoteSaid = stringResource(R.string.shared_text_note)
            LaunchedEffect(waiting) {
                val text = waiting ?: return@LaunchedEffect
                // A code, with words around it or not, is imported; any other
                // text becomes a note of its own (the owner's choice, wave 2).
                if (text.contains(Sharing.PREFIX)) state.incomingCode = text
                else state.noteFromShared(text, sharedNoteSaid)
                incoming.value = null
            }
            val tapped by todayTapped
            LaunchedEffect(tapped) { if (tapped > 0) state.todayAsked += 1 }
            // At every return: the day's bar, once a day, and the next
            // notification set -- an app's alarms go with a restart and an
            // update, and the setting may have changed on another device.
            val resumed by resumes
            LaunchedEffect(resumed) {
                state.backAgain?.invoke()
                state.dayIs(Recurrence.formatDate(LocalDate.now()))
                ReminderAlarm.schedule(this@MainActivity, state.settings)
            }
            // And while the app is in front, the day turns at midnight too --
            // looked at once a minute, as the desktop's `_watch_the_day`, so a
            // clock or zone changed meanwhile is caught as well; and only in
            // front, so a phone with the app behind is never woken for it.
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        delay(minOf(60_000L, Days.untilNextDay(java.time.ZonedDateTime.now()) + 1_000))
                        val now = Recurrence.formatDate(LocalDate.now())
                        if (now != state.today) state.dayIs(now)
                    }
                }
            }
            val phoneDark = isSystemInDarkTheme()
            val palette = paletteOf(state.themeName(phoneDark))
            LaunchedEffect(state) { state.settleTheme(phoneDark) }
            // A theme changed on Appearance dissolves (ThemeVeil); the bars
            // wait for the middle of the fade, as the screen does not turn
            // before it either.
            val veil = remember { ThemeVeil(scope) }
            val barsWait = veil.barsHeld
            SideEffect { if (!barsWait) showBars(palette) }
            CompositionLocalProvider(LocalThemeChange provides veil::change) {
                AppTheme(palette) { veil.Over { AppRoot(state, versionName()) } }
            }
        }
    }

    private var barsDark: Boolean? = null
    private var ground: Int? = null

    /**
     * The clock and the battery in the theme's own contrast, and the window
     * under the app in the theme's own colour: at the start, and halfway
     * through a theme's dissolve (ThemeVeil). Once, an effect ran edge to
     * edge again for every change, laying the window out anew, while under
     * it all the window stayed white.
     */
    private fun showBars(palette: Palette) {
        if (barsDark != palette.isDark) {
            barsDark = palette.isDark
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
        val colour = palette.bg.toArgb()
        if (ground != colour) {
            ground = colour
            window.setBackgroundDrawable(ColorDrawable(colour))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        codeIn(intent)?.let { incoming.value = it }
        if (intent.action == ReminderAlarm.ACTION_TODAY) todayTapped.intValue += 1
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue += 1
    }

    /** Whatever the editor holds is saved when the app leaves the screen. */
    override fun onStop() {
        flush?.invoke()
        super.onStop()
    }

    private fun codeIn(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            // As any text: a sender sharing styled text puts a CharSequence
            // with spans, and getStringExtra gave null for it -- the code
            // never arrived, and nothing was said.
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        // A code is held to the ceiling before it comes here (Sharing.MAX_HANDED_CHARS);
        // whatever Android still refuses is said, never the app stopped.
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_code)))
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, R.string.share_no_app, android.widget.Toast.LENGTH_LONG).show()
        } catch (e: RuntimeException) {
            android.widget.Toast.makeText(this, R.string.share_handed_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun open(file: File, name: String) {
        if (!file.isFile) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        // The type of the file handed over, never of its label: a backup can
        // label any file, and "Holiday photo.jpg" over a page went out as a
        // picture, a name hiding ".apk" behind a bidi mark as a package.
        val type = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(Normalize.fileTypeOf(file.name).removePrefix(".")) ?: "*/*"
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
    var backupPlan by remember { mutableStateOf<Triple<Importing.Plan, Backup.Contents, Importing.LookAlikes>?>(null) }
    var askBackup by remember { mutableStateOf<Backup.Contents?>(null) }

    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = context.getString(R.string.exporting)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // Opened -- for writing, which empties it -- only once the
                // backup is built and checked; a half-written backup is
                // deleted, one that never opened is kept (Backup.exportTo).
                // "wt" only, never the single-note export's fallback to "w":
                // written over a longer backup without emptying it, a zip
                // keeps the old one's tail, and its index, at its end.
                Backup.exportTo(state.dir, context.cacheDir,
                    open = { context.contentResolver.openOutputStream(uri, "wt") },
                    emptyPlace = { isEmptyDocument(context, uri) },
                    discard = { DocumentsContract.deleteDocument(context.contentResolver, uri) })
            }
            busy = null
            when (result) {
                is Backup.Export.Saved -> {
                    state.putSetting(Settings.LAST_EXPORT_AT,
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                    result.tooLarge?.let { state.say(context.getString(R.string.export_done_too_large, it), long = true) }
                        ?: state.say(context.getString(R.string.export_done))
                }
                is Backup.Export.Failed -> state.say(context.getString(R.string.export_failed, result.reason))
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

    // A note's own pickers -- Export as file, Attach files -- answered here
    // with the backup's: where the answer arrives even when Android ended
    // the app behind the picker and started it again on its list
    // (AppState.pickAttachments). Held by the editor, the answer found
    // nobody: the exported file left empty, the files not attached, nothing
    // said. (Android's "allow notifications?" stays with its page: it only
    // pauses the app, which saves nothing then and is not ended for memory.)
    state.exporter = rememberExporter(state)
    var attachFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val attachPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val id = attachFor ?: return@rememberLauncherForActivityResult
        attachFor = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (state.attachHere?.invoke(id, uris) != true) attachAfterReturn(state, context, id, uris)
    }
    state.pickAttachments = { id ->
        attachFor = id
        attachPicker.launch(arrayOf("*/*"))
    }

    // Back from Settings or the Calendar goes to the notes, as Back from any
    // second place in an app does; only from the notes does it leave the
    // app. From the History, it goes back to the month first.
    androidx.activity.compose.BackHandler(
        enabled = state.openId == null && state.tab != Tab.NOTES && state.settingsPage == null,
    ) {
        if (state.tab == Tab.CALENDAR && state.showHistory) state.showHistory = false else state.tab = Tab.NOTES
    }

    // Each tab keeps what it remembers while it is out of sight: on a phone
    // an open note takes the tab's place, and the list built again on Back
    // came back at its top, its search empty -- the History's too.
    val tabsKept = rememberSaveableStateHolder()

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        val open = state.openId
        // Material's window widths: from a medium window -- a phone on its
        // side, a small tablet -- the bar moves to a rail down the left
        // edge, where a short, wide screen can spare it; from an expanded
        // one the note opens beside the notes or the calendar, and the list
        // stays in reach. Settings has no note to sit beside.
        val wide = maxWidth >= RAIL_FROM
        val panesFit = maxWidth >= TWO_PANES_FROM
        val twoPanes = panesFit && state.tab != Tab.SETTINGS
        val listWidth = (maxWidth * 0.38f).coerceIn(320.dp, 420.dp)
        val tabContent: @Composable () -> Unit = {
            tabsKept.SaveableStateProvider(state.tab.name) {
                when (state.tab) {
                    Tab.NOTES -> NotesScreen(state, onImportCode = { importing = true })
                    Tab.CALENDAR -> CalendarScreen(state)
                    Tab.SETTINGS -> SettingsScreen(state, backup, version,
                        openPrivacy = { openInBrowser(context, state, Links.PRIVACY) },
                        openReview = Links.STORE?.let { page -> { openInBrowser(context, state, page) } })
                }
            }
        }
        val goTo = { tab: Tab ->
            val show = {
                state.tab = tab
                state.settingsPage = null
                // A tab asked for is a tab shown, beside the note again.
                state.listAside = false
            }
            // On two panes the note stays open beside the notes and the
            // calendar; anywhere else the rail leaves it, saved -- as Back
            // would, and staying, as Back does, when it cannot be saved.
            if (state.openId != null && !(panesFit && tab != Tab.SETTINGS)) state.saveAndCloseNote(show) else show()
        }
        // Today asked for -- the reminder's bar or its notification: the
        // calendar on the day, the way the rail would go there.
        LaunchedEffect(state.todayAsked) {
            if (state.todayAsked > 0) {
                goTo(Tab.CALENDAR)
                state.showToday(Recurrence.formatDate(LocalDate.now()))
            }
        }
        // One shape at every width, the rail, the panes and the bar each
        // there or not: the list and the editor keep their places in it, so
        // turning the phone moves them without rebuilding them -- an editor
        // built again would lose its find bar, its cursor and whatever it
        // had not saved yet, and the list its selection.
        val tabShown = open == null || (twoPanes && !state.listAside)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    NavigationRail(
                        containerColor = palette.sidebar,
                        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                    ) {
                        for ((tab, label, mark) in TABS) {
                            NavigationRailItem(
                                selected = state.tab == tab,
                                onClick = { goTo(tab) },
                                icon = { Text(mark, fontSize = 20.sp) },
                                label = { Text(stringResource(label)) },
                                colors = NavigationRailItemDefaults.colors(
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
                // Beside the rail, the edges are kept here, once, for
                // whatever is drawn inside -- the editor's own paddings then
                // find nothing left to add. On a phone each screen keeps its
                // own, as it always has.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (!wide) Modifier else Modifier
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        if (tabShown) {
                            Box(
                                (if (twoPanes) Modifier.width(listWidth) else Modifier.weight(1f))
                                    .fillMaxHeight()
                                    .then(if (wide) Modifier else Modifier.statusBarsPadding()),
                            ) { tabContent() }
                        }
                        if (twoPanes && tabShown) VerticalDivider(color = palette.hairline)
                        if (open != null) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                key(state.opening) { NoteEditor(state, open, beside = twoPanes) }
                            }
                        } else if (twoPanes) {
                            Box(Modifier.weight(1f).fillMaxHeight()) { NothingOpen(calendar = state.tab == Tab.CALENDAR) }
                        }
                    }
                }
            }
            // On a phone the note covers the bar, as it always has.
            if (!wide && open == null) {
                NavigationBar(containerColor = palette.sidebar) {
                    for ((tab, label, mark) in TABS) {
                        NavigationBarItem(
                            selected = state.tab == tab,
                            onClick = { goTo(tab) },
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
        // a mistake. And above the keyboard, which covers the bar and the +:
        // under it, a word said while typing -- a letter refused at a row's
        // cap -- was drawn behind the keys (the final audit's eighth round).
        val listShown = state.tab == Tab.NOTES && tabShown
        val density = LocalDensity.current
        val keys = WindowInsets.ime.getBottom(density)
        val typing = keys > 0
        // And above what the open note's editor draws at its foot -- its bar,
        // "A new item…" being typed in -- which sits on the keyboard's top:
        // drawn just above the keys, a message covered them and took their
        // taps for its four seconds (the final audit's ninth round). Kept
        // below the status bar however tall that foot is.
        val footClear = state.editorFootTop?.takeIf { open != null }?.let { top ->
            val below = maxOf(keys, WindowInsets.navigationBars.getBottom(density))
            with(density) { (constraints.maxHeight - below - top).coerceAtLeast(0f).toDp() }
                .coerceAtMost((maxHeight - with(density) { below.toDp() } - 160.dp).coerceAtLeast(0.dp))
        }
        // One host for a message: while a sheet or the template editor draws
        // the app's messages over the window, this one draws none, so each is
        // read out once ([MessagesShown]).
        if (!MessagesShown.elsewhere(state.snackbar)) SnackbarHost(
            state.snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = when {
                    typing || open != null && !twoPanes -> 8.dp
                    !wide -> if (listShown) 160.dp else 80.dp
                    else -> if (listShown) 88.dp else 16.dp
                }.let { rule -> if (footClear == null) rule else maxOf(rule, footClear + 8.dp) }),
        )
    }

    val code = state.incomingCode
    if (importing || code != null) {
        CodeImportDialog(state, code ?: "", onClose = { importing = false; state.incomingCode = null })
    }
    // Share -> PDF, for a note the PDF would leave part of: asked before anything is handed over.
    state.pdfQuestion?.let { PdfQuestionDialog(it, onAnswer = state::answerPdf) }
    askBackup?.let { contents ->
        Ask(
            title = stringResource(R.string.import_notes_title),
            text = stringResource(R.string.import_notes_text),
            confirm = stringResource(R.string.continue_label),
            onConfirm = {
                askBackup = null
                val theirs = contents.extras().categories?.keys()?.asSequence()?.toSet() ?: emptySet()
                // The list as it is now, taken here: a save made meanwhile
                // puts a new object in the live list rather than change one.
                val notebook = org.json.JSONArray().also { copy ->
                    state.load.raw?.let { raw -> for (i in 0 until raw.length()) copy.put(raw.opt(i)) }
                }
                val categories = state.load.categories.keys + theirs
                // Worked out away from the screen: the plan, and how many
                // look like notes already here, which normalizes every note
                // of both notebooks -- 1.3 s with the screen held still, a
                // thousand notes each side (the emulator, 2026-10-27).
                busy = context.getString(R.string.importing)
                scope.launch {
                    val ready = withContext(Dispatchers.Default) {
                        val plan = Importing.plan(notebook, contents.notes, Importing.Source.BACKUP, categories)
                        Triple(plan, contents, Importing.LookAlikes(notebook, plan).warm(plan))
                    }
                    busy = null
                    backupPlan = ready
                }
            },
            onDismiss = {
                askBackup = null
                contents.folder?.deleteRecursively()
            },
        )
    }
    backupPlan?.let { (plan, contents, alikes) ->
        // The unpacked backup leaves the cache however the questions end:
        // nothing of an import that was not taken is kept.
        ImportQuestions(state, plan, alikes, onCancel = {
            backupPlan = null
            contents.folder?.deleteRecursively()
        }) { answers ->
            finishImport(state, plan, answers, state.attachments, contents.extras())
            backupPlan = null
            contents.folder?.deleteRecursively()
        }
    }
}

/** The three places, in the bar's order and the rail's. */
private val TABS = listOf(
    Triple(Tab.NOTES, R.string.nav_notes, "≣"),
    Triple(Tab.CALENDAR, R.string.nav_calendar, "📅"),
    Triple(Tab.SETTINGS, R.string.nav_settings, "⚙"),
)

/** Material's medium window: the rail. */
private val RAIL_FROM = 600.dp

/** Material's expanded window: the note beside the list. */
private val TWO_PANES_FROM = 840.dp

/**
 * The second pane before a note is picked: what it is for. Beside the
 * calendar it offered a + that is only on the list's tab, and a tap on the
 * words did nothing (the owner's phone, 2026-10-23).
 */
@Composable
private fun NothingOpen(calendar: Boolean) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(if (calendar) R.string.pick_a_day_note else R.string.pick_a_note),
             color = palette.subtext, fontSize = 15.sp,
             textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Whether the document at [uri] holds nothing -- a file the picker has just made. */
private fun isEmptyDocument(context: android.content.Context, uri: Uri): Boolean = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
        ?.use { c -> c.moveToFirst() && !c.isNull(0) && c.getLong(0) == 0L } ?: false
} catch (e: Exception) {
    false
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
    } catch (e: OutOfMemoryError) {
        // An Error, which the catch above lets through: a backup too big for
        // the phone ended the app. Its guard (Backup.memoryProblem) should
        // stop one first; this is the boundary for whatever gets past.
        work.deleteRecursively()
        Backup.Read.Failed("That file couldn't be read as a backup:\n${Files.TOO_LARGE}")
    } finally {
        zip.delete()
    }
}

/**
 * A page the app opens -- the privacy policy, or its Google Play page for a
 * review -- in the phone's browser or the store's app; a phone with neither
 * is told so. The app itself connects to nothing.
 */
private fun openInBrowser(context: android.content.Context, state: AppState, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        state.say(context.getString(R.string.no_browser))
    }
}
