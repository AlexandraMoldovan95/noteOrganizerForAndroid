package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import ro.noteorganizer.app.ui.theme.LocalPalette

// ---- in the editor ----

/** "📎 Use template": the desktop's picker, a line per template. */
@Composable
fun TemplatePicker(templates: List<JSONObject>, onPick: (JSONObject) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_pick_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (tpl in templates) {
                    Text(
                        "🧩  " + glimpse(tpl.optString("name")), color = palette.text, fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.sidebar)
                            .clickable { onPick(tpl) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
        titleContentColor = palette.text,
    )
}

/**
 * "🧩 Save as template": the name, offered from the note's title. A name
 * another template has asks before it replaces that one -- never silently,
 * as the same name may hold something entirely different.
 */
@Composable
fun SaveTemplateDialog(state: AppState, template: JSONObject, defaultName: String, onDone: () -> Unit) {
    val palette = LocalPalette.current
    var name by remember { mutableStateOf(defaultName) }
    // Kept to the desktop's 200 letters as its box is, the words already
    // there whole; said under the box, as this dialog stands over the
    // window the app's messages are drawn in.
    var nameField by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    var capSaid by remember { mutableStateOf<String?>(null) }
    val nameCap = remember { Editing.BoxCap({ capSaid = it }, Editing.TITLE_CHARS, "A template name") }
    var askReplace by remember { mutableStateOf(false) }
    val savedSaid = stringResource(R.string.template_saved)
    val failedSaid = stringResource(R.string.templates_not_saved)
    val fullSaid = stringResource(R.string.templates_full, Templates.MAX_TEMPLATES)

    fun write() {
        val chosen = PyText.strip(name)
        val tpl = Templates.normalize(JSONObject(template.toString()).put("name", chosen))
        val list = Templates.load(state.dir)
        if (Templates.full(list, chosen)) {
            state.say(fullSaid)
            onDone()
            return
        }
        val problem = Templates.save(state.dir, Templates.saveAs(list, tpl))
        // The name offered is the note's title, which a share code can make
        // 150,000 letters long: the bar quotes it, never lays out all of it.
        state.say(if (problem != null) failedSaid.format(problem) else savedSaid.format(quoted(chosen)))
        onDone()
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.template_save_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { next ->
                        val kept = keptBy(nameCap, nameField, next)
                        // An edit that fits takes the words away: they were about the last cut.
                        if (kept.text != nameField.text && kept.text == next.text) capSaid = null
                        nameField = kept
                        name = kept.text
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.template_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                capSaid?.let {
                    // Read out as it appears ([saidAloud]), as the app's messages are.
                    Text(it, color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).saidAloud())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = PyText.strip(name).isNotEmpty(),
                onClick = {
                    if (Templates.nameTaken(Templates.load(state.dir), PyText.strip(name))) askReplace = true else write()
                },
            ) { Text(stringResource(R.string.save), color = palette.accentText) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
        titleContentColor = palette.text,
    )
    if (askReplace) {
        NameTakenQuestion(PyText.strip(name), onReplace = { askReplace = false; write() }, onAnother = { askReplace = false })
    }
}

/** The desktop's question; the safe answer -- another name -- is the highlighted one. */
@Composable
private fun NameTakenQuestion(name: String, onReplace: () -> Unit, onAnother: () -> Unit) {
    Ask(
        title = stringResource(R.string.template_taken_title),
        text = stringResource(R.string.template_taken_text, quoted(name)),
        confirm = stringResource(R.string.template_replace),
        onConfirm = onReplace,
        onDismiss = onAnother,
        dismiss = stringResource(R.string.template_another_name),
        destructive = true,
    )
}

// ---- Settings → Templates ----

/**
 * Every template, each with its pencil and its bin, and a way to make one
 * from nothing -- the desktop's Settings panel, its words.
 */
@Composable
fun TemplatesPage(state: AppState) {
    val palette = LocalPalette.current
    var templates by remember { mutableStateOf(Templates.load(state.dir)) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    val failedSaid = stringResource(R.string.templates_not_saved)
    // A template being written when Android ended the app: asked about as
    // the page opens, and again before the editor opens on anything else --
    // never written over unasked by the next one.
    var draft by remember { mutableStateOf(Templates.loadDraft(state.draftDir)) }
    var continuing by remember { mutableStateOf<Templates.Draft?>(null) }
    var askDraft by remember { mutableStateOf(draft != null) }
    // What was tapped while a draft waited: opened once it is answered.
    var thenOpen by remember { mutableStateOf<Int?>(null) }
    val goneSaid = stringResource(R.string.template_draft_gone)

    // Every way into the editor asks first while a draft waits.
    fun open(index: Int?) {
        if (draft != null) {
            thenOpen = index ?: -1
            askDraft = true
            return
        }
        if (index == null) creating = true else editing = index
    }

    fun store(next: List<JSONObject>): Boolean {
        val problem = Templates.save(state.dir, next)
        templates = Templates.load(state.dir)
        if (problem != null) state.say(failedSaid.format(problem))
        return problem == null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.templates_intro), color = palette.subtext, fontSize = 13.sp,
             modifier = Modifier.padding(bottom = 12.dp))
        if (templates.isEmpty()) {
            Text(stringResource(R.string.templates_empty), color = palette.subtext, fontSize = 14.sp,
                 modifier = Modifier.padding(vertical = 10.dp))
        }
        for ((i, tpl) in templates.withIndex()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.sidebar)
                    .clickable { open(i) }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧩", fontSize = 16.sp)
                Text(glimpse(tpl.optString("name")), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                     modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp))
                val editSaid = stringResource(R.string.template_edit_title)
                val deleteSaid = stringResource(R.string.template_delete_title)
                IconButton(onClick = { open(i) }, modifier = Modifier.semantics { contentDescription = editSaid }) {
                    Text("✏", fontSize = 16.sp, color = palette.text)
                }
                IconButton(onClick = { deleting = i }, modifier = Modifier.semantics { contentDescription = deleteSaid }) {
                    Text("🗑", fontSize = 16.sp, color = palette.dangerText)
                }
            }
        }
        Button(
            onClick = { open(null) },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(stringResource(R.string.template_new)) }
        Spacer(Modifier.height(32.dp))
    }

    deleting?.let { i ->
        val name = templates.getOrNull(i)?.optString("name") ?: return@let
        // Honest about the stakes: notes come back from the Trash, templates do not.
        Ask(
            title = stringResource(R.string.template_delete_title),
            text = stringResource(R.string.template_delete_text, quoted(name)),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                store(templates.filterIndexed { j, _ -> j != i })
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
    val waiting = draft
    if (askDraft && waiting != null) {
        // Continue is the safe answer, highlighted; Discard, which cannot be
        // taken back, is quieter, in the warning colour. Dismissed, the draft
        // stays, asked about again next time.
        AlertDialog(
            onDismissRequest = { askDraft = false; thenOpen = null },
            title = { Text(stringResource(R.string.template_draft_title)) },
            text = { Text(stringResource(R.string.template_draft_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askDraft = false
                    thenOpen = null
                    draft = null
                    val at = Templates.draftTarget(templates, waiting)
                    if (Templates.draftLostItsTemplate(templates, waiting)) state.say(goneSaid)
                    continuing = waiting
                    if (at == null) creating = true else editing = at
                }) { Text(stringResource(R.string.template_draft_continue), color = palette.accentText, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Templates.deleteDraft(state.draftDir)
                    draft = null
                    askDraft = false
                    val then = thenOpen
                    thenOpen = null
                    if (then != null) open(then.takeIf { it >= 0 })
                }) { Text(stringResource(R.string.template_draft_discard), color = palette.dangerText) }
            },
            containerColor = palette.card,
            titleContentColor = palette.text,
            textContentColor = palette.subtext,
        )
    }
    if (creating || editing != null) {
        val index = editing
        val fullSaid = stringResource(R.string.templates_full, Templates.MAX_TEMPLATES)
        TemplateEditor(
            state = state,
            original = index?.let { templates.getOrNull(it) },
            others = templates.filterIndexed { j, _ -> j != index },
            draft = continuing,
            onSave = { tpl ->
                // A new one past the ceiling is refused, the editor left open.
                if (Templates.full(templates, tpl.optString("name"), index)) {
                    state.say(fullSaid)
                    false
                } else {
                    store(Templates.saveEdited(templates, index, tpl))
                }
            },
            // However it closes -- saved, its changes not saved, or nothing
            // to keep -- the draft is spent.
            onClose = {
                Templates.deleteDraft(state.draftDir)
                continuing = null
                creating = false
                editing = null
            },
        )
    }
}

/**
 * A template's body: its words to edit, and its tables drawn as tables,
 * never as their pipe rows. The desktop's template editor hides a table's
 * rows from the body box and shows the table in a preview -- "raw pipe
 * rows with merge markers read as noise" -- and the phone drew both, the
 * rows in the box and the grid under it, which looked like a broken table
 * above a whole one (a first try on a real phone). Here the body is cut as
 * the note editor cuts it: each run of words a field of its own, each
 * table a grid in its place, all in one box; a table is shown, not
 * edited, as in the desktop's preview.
 */
@Composable
private fun TemplateBody(body: String, ownCopy: Pasting.OwnCopy?, onChange: (String) -> Unit) {
    val palette = LocalPalette.current
    val label = stringResource(R.string.template_field_body)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = label },
    ) {
        Text(label, color = palette.subtext, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        for ((index, segment) in Editing.segments(body).withIndex()) {
            key(index, segment.table, segment.long) {
                if (segment.long) {
                    // Too long to lay out: as the note editor shows it.
                    LongText(segment.text, segment.heavy)
                } else if (segment.table && !segment.drawn) {
                    // Too large to draw as a grid: as the note editor shows it.
                    LargeTable(segment)
                } else if (segment.table) {
                    TableView(parseTable(segment.text.split("\n")), Modifier.padding(vertical = 6.dp))
                } else {
                    val following = rememberFollowing()
                    BasicTextField(
                        value = segment.text,
                        onValueChange = { given ->
                            // A CR that came in is one line break, as in a note ([Editing.breaksCame]).
                            val now = Editing.breaksCame(segment.text, given)?.first ?: given
                            // A table pasted in comes without the widths its
                            // separator's dashes spell, as in a note.
                            val kept = Pasting.forgetPastedWidths(segment.text, now, ownCopy)
                            following.typed(segment.text, kept)
                            onChange(Editing.replaceProse(body, segment, kept))
                        },
                        onTextLayout = following::laidOut,
                        textStyle = TextStyle(color = palette.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (body.isEmpty()) 72.dp else 24.dp)
                            .bringIntoViewRequester(following.requester),
                    )
                }
            }
        }
    }
}

/**
 * One template, on the whole screen: its name, the note's title, text and
 * comments, a checklist to tick and add to, and a category. Closing with
 * changes asks, as the desktop's does, whether to keep them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditor(
    state: AppState,
    original: JSONObject?,
    others: List<JSONObject>,
    onSave: (JSONObject) -> Boolean,
    onClose: () -> Unit,
    draft: Templates.Draft? = null,
) {
    val palette = LocalPalette.current
    // What the template holds, and what the editor starts from: the draft
    // kept aside as the app last went away, when it is continued, its fields
    // as they were typed -- over the template it edits, which stays what
    // a change is measured from, so Save and ✕ still ask about the draft.
    val held = remember { Templates.normalize(original ?: JSONObject()) }
    val start = remember { draft?.fields ?: held }
    var name by remember { mutableStateOf(if (draft != null) start.optString("name") else if (original == null) "" else start.optString("name")) }
    var title by remember { mutableStateOf(start.optString("title")) }
    var body by remember { mutableStateOf(start.optString("body")) }
    // Held by the field itself: a field over a TextFieldState keeps its
    // cursor in sight as it is typed into, where one over a String does not
    // and has no layout to ask with ([Following]). Remembered as the fields
    // beside it are, never saved for the system to hold while the app is
    // away: 600,000 letters of comments were more than that handover
    // carries, and the app stopped as Home was pressed (seen on the
    // emulator, TransactionTooLargeException). Saved, they bought nothing --
    // an app started again opens on its list, not on this editor.
    val comments = remember { TextFieldState(start.optString("comments")) }
    val categories = state.load.categories.keys.toList()
    fun categoryOf(t: JSONObject) = t.optString("category").takeIf { it in categories } ?: "General"
    var category by remember { mutableStateOf(categoryOf(start)) }
    fun rowsOf(t: JSONObject): List<Pair<String, Boolean>> {
        val list = t.optJSONArray("checklist") ?: JSONArray()
        return (0 until list.length()).mapNotNull { k -> list.optJSONObject(k)?.let { it.optString("text") to it.optBoolean("done") } }
    }
    val checklist = remember { mutableStateListOf<Pair<String, Boolean>>().apply { addAll(rowsOf(start)) } }
    // With its cursor, so a paste cut to fit leaves it after what came in.
    var newItemField by remember { mutableStateOf(TextFieldValue(draft?.waiting ?: "")) }
    // Kept to a row's length, as the note editor's box and the desktop's
    // are ([Editing.fitRow]): it took any length, and a long paste became a
    // row every note made from the template showed read-only.
    val rowCap = remember { Editing.BoxCap({ state.say(it) }) }
    // The name and the title with their cursors, kept to the desktop's 200
    // letters as its boxes are -- the words already there whole, what came
    // in cut, said in its words. `take(200)` of each new value cut their own
    // end, in UTF-16 units (the final audit's seventh round).
    var nameField by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    var titleField by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    val nameCap = remember { Editing.BoxCap({ state.say(it) }, Editing.TITLE_CHARS, "A template name") }
    val titleCap = remember { Editing.BoxCap({ state.say(it) }, Editing.TITLE_CHARS, "A note title") }
    var askName by remember { mutableStateOf(false) }
    var askReplace by remember { mutableStateOf(false) }
    var askClose by remember { mutableStateOf(false) }

    // The styles and lines it had, moved with the words at every change, as
    // the note editor moves them -- the desktop keeps them as tags. Kept at
    // their offsets, a line typed above them put the bold on other words.
    var formatting by remember { mutableStateOf(JSONArray((start.optJSONArray("formatting") ?: JSONArray()).toString())) }
    var paragraphs by remember { mutableStateOf(JSONArray((start.optJSONArray("paragraphs") ?: JSONArray()).toString())) }
    fun edited(new: String) {
        val (styles, lines) = Notebook.throughEdit(formatting, paragraphs, body, new)
        formatting = styles
        paragraphs = lines
        body = new
    }

    fun collect(): JSONObject = Templates.collected(name, title, body, comments.text.toString(), category,
        checklist, newItemField.text, formatting, paragraphs)

    // What the editor opened on, the template as it is saved -- never the
    // draft, which is a change nobody saved yet.
    val opened = remember {
        Templates.collected(if (original == null) "" else held.optString("name"), held.optString("title"),
            held.optString("body"), held.optString("comments"), categoryOf(held), rowsOf(held), "",
            held.optJSONArray("formatting") ?: JSONArray(), held.optJSONArray("paragraphs") ?: JSONArray()).toString()
    }
    // Words waiting with no room for them are a change nobody saved: ✕ asks.
    fun changed() = collect().toString() != opened || Templates.waitingLeftOut(checklist.size, newItemField.text)

    // Closed by the person -- saved, not saved, or nothing to keep -- the
    // editor leaves no draft; gone any other way, it keeps one (below).
    var closed by remember { mutableStateOf(false) }
    fun leave() {
        closed = true
        onClose()
    }

    fun save() {
        if (PyText.strip(name).isEmpty()) {
            askName = true
            return
        }
        // Never dropped unsaid: a full list keeps the editor open, the words
        // in their box, and says why.
        if (Templates.waitingLeftOut(checklist.size, newItemField.text)) {
            state.say(Notebook.CHECKLIST_FULL)
            return
        }
        val tpl = collect()
        if (others.any { it.optString("name") == tpl.optString("name") }) {
            askReplace = true
            return
        }
        if (onSave(tpl)) leave()
    }

    fun close() = if (changed()) askClose = true else leave()

    // Kept aside, every field and the words waiting in "A new item…", to be
    // offered back, whenever the editor goes where nobody saved it: the app
    // to the background, Android able to end it there -- and the editor off
    // the screen by the app's own doing, with no ✕ pressed: the reminder's
    // notification tapped while the app is in front opens the calendar, an
    // import's Open the note, and everything typed went unsaved, unasked and
    // undrafted (the final audit's seventh round). Nothing changed, no draft
    // stays behind ([Templates.keepDraft]).
    fun keep() = Templates.keepDraft(state.draftDir, changed()) {
        Templates.Draft(original?.optString("name"),
            Templates.draftFields(name, title, body, comments.text.toString(), category, checklist.toList(),
                formatting, paragraphs), newItemField.text)
    }
    DisposableEffect(Unit) {
        state.keepTemplateDraft = { keep() }
        onDispose {
            state.keepTemplateDraft = null
            if (!closed) keep()
        }
    }

    // Edge to edge, with the bars' and the keyboard's room kept below: left
    // to the window, the keyboard pushed the whole dialog up and its top
    // bar -- Save with it -- went under the status bar, out of reach.
    Dialog(onDismissRequest = { close() },
           properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { close() }
        // Drawn under the status bar now, so its icons take the theme's
        // side: white on a light page they all but vanished.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }
        }
        Column(Modifier.fillMaxSize().background(palette.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }, modifier = Modifier.said(R.string.close_said)) {
                    Text("✕", fontSize = 18.sp, color = palette.text)
                }
                Text(stringResource(if (original == null) R.string.template_new_title else R.string.template_edit_title),
                     color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(onClick = { save() }) {
                    Text(stringResource(R.string.save), color = palette.accentText, fontWeight = FontWeight.Medium)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                OutlinedTextField(nameField, { next ->
                        nameField = keptBy(nameCap, nameField, next)
                        name = nameField.text
                    }, singleLine = true,
                    label = { Text(stringResource(R.string.template_field_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(titleField, { next ->
                        titleField = keptBy(titleCap, titleField, next)
                        title = titleField.text
                    }, singleLine = true,
                    label = { Text(stringResource(R.string.template_field_title)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                TemplateBody(body, state.ownCopy) { edited(it) }
                // At most ten lines tall, scrolling inside past them: grown
                // to fit very long comments, the field outgrew the tallest
                // size Compose can measure and the app stopped as the
                // template opened (600,000 letters, on the emulator).
                // Comments too long to lay out are shown, not edited, as a
                // note's are, and kept as they were.
                val commentsText = comments.text.toString()
                if (Editing.tooLongToLayOut(commentsText)) {
                    // Under the name its field had: the label was the field's
                    // own, and the block alone read as the body's (the walk,
                    // 2026-10-29).
                    Text(stringResource(R.string.template_field_comments), color = palette.subtext, fontSize = 12.sp,
                         modifier = Modifier.padding(top = 12.dp))
                    LongText(commentsText)
                } else OutlinedTextField(comments, lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 10),
                    label = { Text(stringResource(R.string.template_field_comments)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                Text(stringResource(R.string.template_checklist), color = palette.subtext, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                if (checklist.isEmpty()) {
                    Text(stringResource(R.string.template_checklist_empty), color = palette.subtext, fontSize = 13.sp)
                }
                TemplateRows(checklist)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(newItemField, { next -> newItemField = keptBy(rowCap, newItemField, next) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.checklist_new)) }, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val text = Templates.newRow(newItemField.text)
                            // At the cap the row was drawn, then dropped by Save unsaid.
                            val full = Templates.noRoom(checklist.size)
                            if (text.isNotEmpty() && full != null) state.say(full)
                            else if (text.isNotEmpty()) {
                                checklist.add(text to false)
                                newItemField = TextFieldValue("")
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text(stringResource(R.string.template_add_item)) }
                }

                Text(stringResource(R.string.template_category), color = palette.subtext, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cat in categories) {
                        val chosen = cat == category
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .then(if (chosen) Modifier.background(palette.sidebar)
                                      else Modifier.border(1.dp, palette.hairline, RoundedCornerShape(16.dp)))
                                .clickable { category = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Dot(state.load.categories[cat] ?: "", 10)
                            Spacer(Modifier.width(6.dp))
                            Text(cat, color = palette.text, fontSize = 14.sp,
                                 fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
        // The app's messages, over the editor: said in the window under this
        // one, they were drawn beneath it and never seen -- a full list's
        // refusal, a list too full to save, the row cap's words (the walk,
        // 2026-10-29). Shown here alone while the editor is open
        // ([MessagesShown]): shown by both, each was two live regions.
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            contentAlignment = Alignment.BottomCenter) {
            MessagesShown.Over(state.snackbar)
            SnackbarHost(state.snackbar, Modifier.padding(bottom = 16.dp))
        }
        if (askName) {
            AlertDialog(
                onDismissRequest = { askName = false },
                title = { Text(stringResource(R.string.template_name_needed_title)) },
                text = { Text(stringResource(R.string.template_name_needed)) },
                confirmButton = { TextButton(onClick = { askName = false }) { Text(stringResource(R.string.ok)) } },
                containerColor = palette.card,
            )
        }
        if (askReplace) {
            NameTakenQuestion(PyText.strip(name),
                onReplace = { askReplace = false; if (onSave(collect())) leave() },
                onAnother = { askReplace = false })
        }
        if (askClose) {
            // Save is the default, as the desktop's: closing usually means done.
            AlertDialog(
                onDismissRequest = { askClose = false },
                title = { Text(stringResource(R.string.template_save_changes_title)) },
                text = { Text(stringResource(R.string.template_save_changes_text)) },
                confirmButton = {
                    TextButton(onClick = { askClose = false; save() }) {
                        Text(stringResource(R.string.save), color = palette.accentText, fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { askClose = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { askClose = false; leave() }) {
                            Text(stringResource(R.string.template_dont_save), color = palette.dangerText)
                        }
                    }
                },
                containerColor = palette.card,
            )
        }
    }
}

/**
 * A template's checklist rows, drawn as the note editor draws a long list:
 * the first [Editing.CHECKLIST_ROWS] and the last few, "Show" drawing more
 * ([Editing.checklistShown]). Every row was drawn at once -- a box and two
 * buttons each, up to 1,000 of them, as a template saved from a long list
 * holds -- and drawn again at each letter typed in the template's name or
 * title, whose scope they shared (the final audit's sixth round). A row
 * moved by its arrow past the rows not drawn stays drawn, its key moving
 * with it, and a row longer than a glimpse is read whole on asking.
 */
@Composable
private fun TemplateRows(checklist: SnapshotStateList<Pair<String, Boolean>>) {
    val palette = LocalPalette.current
    var head by remember { mutableIntStateOf(Editing.CHECKLIST_ROWS) }
    var tail by remember { mutableIntStateOf(Editing.CHECKLIST_TAIL_ROWS) }
    val shown = remember(checklist.size, head, tail) { Editing.checklistShown(checklist.size, head, tail = tail) }
    val keys = rowKeys(checklist.map { it.first })
    var drawnUpTo = -1
    for (k in shown) key(keys[k]) {
        val item = checklist[k]
        if (k > drawnUpTo + 1) {
            val hidden = k - drawnUpTo - 1
            val upTo = drawnUpTo
            Text(stringResource(R.string.checklist_rows_hidden, hidden, minOf(hidden, Editing.CHECKLIST_ROWS)),
                 color = palette.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                 modifier = Modifier.fillMaxWidth().clickable { head = upTo + 1 + Editing.CHECKLIST_ROWS }
                     .padding(vertical = 10.dp))
        }
        drawnUpTo = k
        // Each control says what it does to which row: read by their
        // symbols, "upwards arrow" and "multiplication x" named no row,
        // and the box was a checkbox with no name at all.
        val rowName = glimpse(item.first)
        val upSaid = stringResource(R.string.template_row_up, rowName)
        val removeSaid = stringResource(R.string.checklist_remove_said, rowName)
        val readOnly = Editing.rowTooLong(item.first) || drawnRow(item.first) != item.first
        // A row read whole keeps its box and buttons beside its start.
        Row(verticalAlignment = if (readOnly) Alignment.Top else Alignment.CenterVertically) {
            Checkbox(item.second, { checklist[k] = item.first to it },
                     colors = CheckboxDefaults.colors(checkedColor = palette.accent),
                     modifier = Modifier.semantics { contentDescription = rowName })
            // Kept whole: a row longer than a glimpse is shown by its start,
            // and read whole on asking, as the note editor shows one.
            if (readOnly) {
                ReadOnlyRow(item.first, item.second, modifier = Modifier.weight(1f))
            } else Text(item.first, fontSize = 15.sp,
                 color = if (item.second) palette.subtext else palette.text,
                 textDecoration = if (item.second) TextDecoration.LineThrough else null,
                 modifier = Modifier.weight(1f))
            if (k > 0) {
                TextButton(onClick = {
                               checklist.add(k - 1, checklist.removeAt(k))
                               val (h, t) = Editing.keepMovedInSight(checklist.size, head, tail, -1, k, k - 1)
                               head = h
                               tail = t
                           },
                           modifier = Modifier.semantics { contentDescription = upSaid }) {
                    Text("↑", color = palette.subtext)
                }
            }
            TextButton(onClick = { checklist.removeAt(k) },
                       modifier = Modifier.semantics { contentDescription = removeSaid }) {
                Text("✕", color = palette.subtext)
            }
        }
    }
}
