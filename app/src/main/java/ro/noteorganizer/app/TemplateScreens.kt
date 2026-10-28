package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
        state.say(if (problem != null) failedSaid.format(problem) else savedSaid.format(chosen))
        onDone()
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.template_save_title)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(200) }, singleLine = true,
                label = { Text(stringResource(R.string.template_field_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
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
        text = stringResource(R.string.template_taken_text, name),
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
                    .clickable { editing = i }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧩", fontSize = 16.sp)
                Text(glimpse(tpl.optString("name")), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                     modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp))
                val editSaid = stringResource(R.string.template_edit_title)
                val deleteSaid = stringResource(R.string.template_delete_title)
                IconButton(onClick = { editing = i }, modifier = Modifier.semantics { contentDescription = editSaid }) {
                    Text("✏", fontSize = 16.sp, color = palette.text)
                }
                IconButton(onClick = { deleting = i }, modifier = Modifier.semantics { contentDescription = deleteSaid }) {
                    Text("🗑", fontSize = 16.sp, color = palette.dangerText)
                }
            }
        }
        Button(
            onClick = { creating = true },
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
            text = stringResource(R.string.template_delete_text, name),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                deleting = null
                store(templates.filterIndexed { j, _ -> j != i })
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
    if (creating || editing != null) {
        val index = editing
        val fullSaid = stringResource(R.string.templates_full, Templates.MAX_TEMPLATES)
        TemplateEditor(
            state = state,
            original = index?.let { templates.getOrNull(it) },
            others = templates.filterIndexed { j, _ -> j != index },
            onSave = { tpl ->
                // A new one past the ceiling is refused, the editor left open.
                if (Templates.full(templates, tpl.optString("name"), index)) {
                    state.say(fullSaid)
                    false
                } else {
                    store(Templates.saveEdited(templates, index, tpl))
                }
            },
            onClose = { creating = false; editing = null },
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
                    LongText(segment.text)
                } else if (segment.table && !segment.drawn) {
                    // Too large to draw as a grid: as the note editor shows it.
                    LargeTable(segment)
                } else if (segment.table) {
                    TableView(parseTable(segment.text.split("\n")), Modifier.padding(vertical = 6.dp))
                } else {
                    val following = rememberFollowing()
                    BasicTextField(
                        value = segment.text,
                        onValueChange = {
                            // A table pasted in comes without the widths its
                            // separator's dashes spell, as in a note.
                            val kept = Pasting.forgetPastedWidths(segment.text, it, ownCopy)
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
) {
    val palette = LocalPalette.current
    val start = remember { Templates.normalize(original ?: JSONObject()) }
    var name by remember { mutableStateOf(if (original == null) "" else start.optString("name")) }
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
    var category by remember { mutableStateOf(start.optString("category").takeIf { it in categories } ?: "General") }
    val checklist = remember {
        mutableStateListOf<Pair<String, Boolean>>().apply {
            val list = start.optJSONArray("checklist") ?: JSONArray()
            for (k in 0 until list.length()) list.optJSONObject(k)?.let { add(it.optString("text") to it.optBoolean("done")) }
        }
    }
    var newItem by remember { mutableStateOf("") }
    var askName by remember { mutableStateOf(false) }
    var askReplace by remember { mutableStateOf(false) }
    var askClose by remember { mutableStateOf(false) }

    // The styles and lines it had, moved with the words at every change, as
    // the note editor moves them -- the desktop keeps them as tags. Kept at
    // their offsets, a line typed above them put the bold on other words.
    var formatting by remember { mutableStateOf(start.optJSONArray("formatting") ?: JSONArray()) }
    var paragraphs by remember { mutableStateOf(start.optJSONArray("paragraphs") ?: JSONArray()) }
    fun edited(new: String) {
        val (styles, lines) = Notebook.throughEdit(formatting, paragraphs, body, new)
        formatting = styles
        paragraphs = lines
        body = new
    }

    fun collect(): JSONObject = Templates.normalize(JSONObject()
        .put("name", name).put("title", title).put("body", body).put("comments", comments.text.toString()).put("category", category)
        .put("checklist", JSONArray(checklist.map { JSONObject().put("text", it.first).put("done", it.second) }))
        .put("formatting", formatting)
        .put("paragraphs", paragraphs))

    val opened = remember { collect().toString() }
    fun changed() = collect().toString() != opened

    fun save() {
        if (PyText.strip(name).isEmpty()) {
            askName = true
            return
        }
        val tpl = collect()
        if (others.any { it.optString("name") == tpl.optString("name") }) {
            askReplace = true
            return
        }
        if (onSave(tpl)) onClose()
    }

    fun close() = if (changed()) askClose = true else onClose()

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
                OutlinedTextField(name, { name = it.take(200) }, singleLine = true,
                    label = { Text(stringResource(R.string.template_field_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it.take(200) }, singleLine = true,
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
                if (Editing.tooLongToLayOut(commentsText)) LongText(commentsText)
                else OutlinedTextField(comments, lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 10),
                    label = { Text(stringResource(R.string.template_field_comments)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                Text(stringResource(R.string.template_checklist), color = palette.subtext, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                if (checklist.isEmpty()) {
                    Text(stringResource(R.string.template_checklist_empty), color = palette.subtext, fontSize = 13.sp)
                }
                for ((k, item) in checklist.withIndex()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.second, { checklist[k] = item.first to it },
                                 colors = CheckboxDefaults.colors(checkedColor = palette.accent))
                        Text(item.first, fontSize = 15.sp,
                             color = if (item.second) palette.subtext else palette.text,
                             textDecoration = if (item.second) TextDecoration.LineThrough else null,
                             modifier = Modifier.weight(1f))
                        if (k > 0) {
                            TextButton(onClick = { checklist.add(k - 1, checklist.removeAt(k)) }) {
                                Text("↑", color = palette.subtext)
                            }
                        }
                        TextButton(onClick = { checklist.removeAt(k) }) { Text("✕", color = palette.subtext) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(newItem, { newItem = it }, singleLine = true,
                        label = { Text(stringResource(R.string.checklist_new)) }, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val text = PyText.strip(newItem)
                            if (text.isNotEmpty()) {
                                checklist.add(text to false)
                                newItem = ""
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
                onReplace = { askReplace = false; if (onSave(collect())) onClose() },
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
                        TextButton(onClick = { askClose = false; onClose() }) {
                            Text(stringResource(R.string.template_dont_save), color = palette.dangerText)
                        }
                    }
                },
                containerColor = palette.card,
            )
        }
    }
}
