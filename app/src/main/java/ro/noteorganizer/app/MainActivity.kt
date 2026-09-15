package ro.noteorganizer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reading a real notebook on a phone. Nothing is ever written: the files
 * are opened for reading only, so a bug here cannot cost a single note.
 */

/** The desktop app's `dark` theme, so the two can be compared side by side. */
private val BG = Color(0xFF1E1E2F)
private val CARD = Color(0xFF2E2E48)
private val TEXT = Color(0xFFF2F2F7)
private val SUBTEXT = Color(0xFFA0A0C0)
private val ACCENT = Color(0xFF7C5CFF)
private val WARN = Color(0xFFF9C74F)
private val DANGER = Color(0xFFE5484D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // `adb push` can write to the app's own external folder with no
        // permission and no root; the internal one is the fallback.
        val places = listOfNotNull(getExternalFilesDir(null), filesDir)

        setContent { App { Notebook.load(places) } }
    }
}

@Composable
fun App(read: () -> Notebook.Load) {
    var loaded by remember { mutableStateOf(read()) }
    var open by remember { mutableStateOf<Note?>(null) }
    var writing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var inTrash by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val current = open
    val underEdit = editing

    when {
        inTrash -> {
            BackHandler { inTrash = false; notice = null }
            TrashScreen(
                load = loaded,
                notice = notice,
                onRestore = { note ->
                    when (val result = Notebook.restoreFromTrash(loaded, note.id)) {
                        is SaveResult.Ok -> {
                            loaded = read()
                            notice = null
                            if (loaded.trash.isEmpty()) inTrash = false
                        }
                        is SaveResult.Failed ->
                            notice = "Nu s-a restaurat: ${result.reason}"
                    }
                },
                onBack = { inTrash = false; notice = null },
            )
        }
        underEdit != null -> {
            BackHandler { editing = null }
            ComposeScreen(
                initial = NoteFields(
                    title = underEdit.title,
                    body = underEdit.body,
                    category = underEdit.category,
                    color = underEdit.color,
                    date = underEdit.date,
                    pinned = underEdit.pinned,
                    formatting = underEdit.formatting,
                ),
                categories = categoriesFor(loaded),
                warning = editWarning(underEdit),
                onCancel = { editing = null },
                onSave = { edits ->
                    val result = Notebook.updateNote(
                        loaded, underEdit.id, edits.title, edits.body,
                        category = edits.category, color = edits.color,
                        date = edits.date, pinned = edits.pinned,
                        formatting = edits.formatting,
                    )
                    if (result is SaveResult.Ok) {
                        loaded = read()
                        // Re-open the SAME note from the freshly read file,
                        // so what is on screen came off the disk.
                        open = loaded.notes.firstOrNull { it.id == underEdit.id }
                        notice = if (result.lostStyles > 0) {
                            "${result.lostStyles} stiluri nu au putut fi pastrate " +
                                "(textul lor a fost sters)"
                        } else {
                            null
                        }
                        editing = null
                    }
                    result
                },
            )
        }
        writing -> {
            BackHandler { writing = false }
            ComposeScreen(
                categories = categoriesFor(loaded),
                onCancel = { writing = false },
                onSave = { edits ->
                    val result = Notebook.createNote(
                        loaded, edits.title, edits.body,
                        category = edits.category, color = edits.color,
                        date = edits.date, pinned = edits.pinned,
                        formatting = edits.formatting,
                    )
                    if (result is SaveResult.Ok) {
                        // Re-read from disk rather than patching the list in
                        // memory: what the screen shows is then what was
                        // actually written, not what we hoped was written.
                        loaded = read()
                        writing = false
                    }
                    result
                },
            )
        }
        current != null -> {
            // The phone's own back gesture closes the note, which is what
            // anyone would expect it to do. Without this it would close the
            // whole app from a note, losing the reader's place in the list.
            BackHandler { open = null; notice = null }
            NoteScreen(
                note = current,
                categories = loaded.categories,
                notice = notice,
                onEdit = { editing = current; notice = null },
                onToggleChecklist = { index, done ->
                    when (val result =
                        Notebook.setChecklistDone(loaded, current.id, index, done)) {
                        is SaveResult.Ok -> {
                            loaded = read()
                            open = loaded.notes.firstOrNull { it.id == current.id }
                            notice = null
                        }
                        // A tick that did not reach the disk must not look
                        // like one that did.
                        is SaveResult.Failed ->
                            notice = "Bifa nu s-a salvat: ${result.reason}"
                    }
                },
                onAddChecklist = { text ->
                    applyChecklist(Notebook.addChecklistItem(loaded, current.id, text),
                        onOk = { loaded = read(); open = loaded.notes.firstOrNull { it.id == current.id }; notice = null },
                        onFail = { notice = it })
                },
                onRenameChecklist = { index, text ->
                    applyChecklist(
                        Notebook.renameChecklistItem(loaded, current.id, index, text),
                        onOk = { loaded = read(); open = loaded.notes.firstOrNull { it.id == current.id }; notice = null },
                        onFail = { notice = it })
                },
                onRemoveChecklist = { index ->
                    applyChecklist(Notebook.removeChecklistItem(loaded, current.id, index),
                        onOk = { loaded = read(); open = loaded.notes.firstOrNull { it.id == current.id }; notice = null },
                        onFail = { notice = it })
                },
                onEditTable = { blockIndex, change ->
                    // The table's CURRENT lines come from the note on
                    // screen, which was read off the disk -- not from a
                    // copy the editor has been holding.
                    val lines = current.body.split("\n")
                    val blocks = tableBlocks(current.body)
                    val block = blocks.getOrNull(blockIndex)
                    if (block == null) {
                        notice = "Tabelul nu mai e acolo"
                    } else {
                        val edited = change(
                            TableEdit(lines.subList(block.first, block.last + 1))
                        ).lines()
                        applyChecklist(
                            Notebook.updateTable(loaded, current.id, blockIndex, edited),
                            onOk = {
                                loaded = read()
                                open = loaded.notes.firstOrNull { it.id == current.id }
                                notice = null
                            },
                            onFail = { notice = it },
                        )
                    }
                },
                shareCode = { Notebook.shareCodeFor(loaded, current.id) },
                onDelete = {
                    when (val result = Notebook.deleteNote(loaded, current.id)) {
                        is SaveResult.Ok -> {
                            loaded = read()
                            open = null
                            notice = null
                        }
                        // Including the half-done case, whose message says
                        // the note is in both places and nothing was lost.
                        is SaveResult.Failed -> {
                            loaded = read()
                            notice = "Nu s-a sters: ${result.reason}"
                        }
                    }
                },
                onBack = { open = null; notice = null },
            )
        }
        else -> Box(modifier = Modifier.fillMaxSize()) {
            NotebookScreen(
                loaded = loaded,
                onTrash = { inTrash = true },
                onOpen = { open = it },
                onInspect = { code -> Notebook.inspectShareCode(loaded, code) },
                onApply = { notes, update ->
                    when (val result = Notebook.applyShareCode(loaded, notes, update)) {
                        is SaveResult.Ok -> {
                            loaded = read()
                            notice = when {
                                update && result.imported == 1 -> "O notita actualizata"
                                update -> "${result.imported} notite actualizate"
                                result.imported == 1 -> "O notita importata"
                                else -> "${result.imported} notite importate"
                            }
                        }
                        is SaveResult.Failed -> notice = result.reason
                    }
                },
                notice = notice,
                onDismissNotice = { notice = null },
            )
            NewNoteButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 36.dp)
            ) { writing = true }
        }
    }
}

@Composable
fun NewNoteButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(ACCENT)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Color.White, fontSize = 30.sp)
    }
}

/** Everything one screen of editing can change about a note. */
data class NoteFields(
    val title: String = "",
    val body: String = "",
    val category: String = "General",
    val color: String = "",
    val date: String = "",
    val pinned: Boolean = false,
    val formatting: List<FormatSpan> = emptyList(),
)

/** The desktop's soft highlighter palette, so text on it stays readable. */
private val HIGHLIGHT_COLORS = listOf(
    "#fff3b0", "#c8f4d9", "#cde7ff", "#ffd6e0", "#e8d8ff",
    "#ffe0c2", "#d9f2f2", "#eaeaea",
)

/** The desktop's PRESET_NOTE_COLORS, in its order. */
private val PRESET_COLORS = listOf(
    "#7c5cff", "#4cc9f0", "#f9c74f", "#f77f00", "#ff5c7c",
    "#4cd9a3", "#ff8fa3", "#a3a3ff", "#ffd166", "#06d6a0",
    "#e63946", "#3a86ff", "#c77dff", "#8ac926", "#6c757d",
)

@Composable
fun ComposeScreen(
    initial: NoteFields = NoteFields(),
    categories: List<String> = listOf("General"),
    warning: String? = null,
    onCancel: () -> Unit,
    onSave: (NoteFields) -> SaveResult,
) {
    // Keyed on the note's own text, so opening a different note refills the
    // fields instead of showing the last one's.
    val key = listOf(initial.title, initial.body)
    var title by rememberSaveable(*key.toTypedArray()) { mutableStateOf(initial.title) }
    // A TextFieldValue rather than a String: the toolbar needs to know
    // WHAT IS SELECTED, and only the field's own value carries that.
    var bodyField by remember(*key.toTypedArray()) { mutableStateOf(TextFieldValue(initial.body)) }
    var spans by remember(*key.toTypedArray()) { mutableStateOf(initial.formatting) }
    val body = bodyField.text
    var category by rememberSaveable(*key.toTypedArray()) { mutableStateOf(initial.category) }
    var color by rememberSaveable(*key.toTypedArray()) { mutableStateOf(initial.color) }
    var date by rememberSaveable(*key.toTypedArray()) { mutableStateOf(initial.date) }
    var pinned by rememberSaveable(*key.toTypedArray()) { mutableStateOf(initial.pinned) }
    var panel by rememberSaveable(*key.toTypedArray()) { mutableStateOf("") }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    // An entirely blank note is a mis-tap, not a thought worth keeping.
    val hasSomething = title.isNotBlank() || body.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            // Without this the keyboard slides over the bottom of the
            // screen and covers the format bar entirely -- which is where
            // the bar had to move to escape Android's selection menu. The
            // window is edge-to-edge, so nothing makes room for the
            // keyboard unless it is asked for.
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹  Renunta",
                color = SUBTEXT,
                fontSize = 15.sp,
                modifier = Modifier.clickable { onCancel() },
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Salveaza",
                color = if (hasSomething) ACCENT else SUBTEXT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = hasSomething) {
                    val edited =
                        NoteFields(title, body, category, color, date, pinned, spans)
                    when (val result = onSave(edited)) {
                        // A failure is shown, never swallowed: the desktop's
                        // hardest-earned rule about saving.
                        is SaveResult.Failed -> failure = result.reason
                        is SaveResult.Ok -> failure = null
                    }
                },
            )
        }

        failure?.let {
            Text(
                text = "Nu s-a salvat: $it",
                color = WARN,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        warning?.let {
            Text(
                text = it,
                color = SUBTEXT,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        BasicTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            textStyle = TextStyle(
                color = TEXT, fontSize = 21.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (title.isEmpty()) {
                    Text("Titlu (optional)", color = SUBTEXT,
                         fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                field()
            },
        )

        // Category, colour, date and pin. Chips rather than a form: on a
        // phone the writing space is the thing worth protecting, and these
        // are set once and then left alone.
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Chip("▤  $category", panel == "cat") {
                panel = if (panel == "cat") "" else "cat"
            }
            Box(modifier = Modifier.width(8.dp))
            Chip(
                if (date.isBlank()) "📅  fara data" else "📅  $date",
                panel == "date",
            ) { panel = if (panel == "date") "" else "date" }
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Chip(
                if (color.isBlank()) "○  fara culoare" else "●  culoare",
                panel == "color",
            ) { panel = if (panel == "color") "" else "color" }
            Box(modifier = Modifier.width(8.dp))
            Chip(
                if (pinned) "📌  fixata sus" else "📌  nefixata",
                pinned,
            ) { pinned = !pinned }
        }

        when (panel) {
            "cat" -> MenuPanel(categories, category) { category = it; panel = "" }
            "date" -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CARD)
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = date,
                    onValueChange = { date = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
                    cursorBrush = SolidColor(ACCENT),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        if (date.isEmpty()) {
                            Text("2026-09-12", color = SUBTEXT, fontSize = 15.sp)
                        }
                        field()
                    },
                )
                if (date.isNotBlank()) {
                    Text(
                        text = "Sterge data",
                        color = ACCENT,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable { date = "" },
                    )
                }
            }
            "color" -> ColorPanel(color) { color = it; panel = "" }
        }

        BasicTextField(
            value = bodyField,
            onValueChange = { next ->
                // The styles must follow the text as it is typed, or a
                // letter added near the top would slide every word out
                // from under its own styling. Same rule as a saved edit --
                // reused rather than written again.
                if (next.text != bodyField.text) {
                    spans = remapSpans(bodyField.text, next.text, spans).spans
                }
                bodyField = next
            },
            // Styling shown while writing: the point of a format button is
            // seeing what it did.
            visualTransformation = { _ ->
                TransformedText(
                    renderBody(Note(body = body, formatting = spans), BG).text,
                    OffsetMapping.Identity,
                )
            },
            textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
            decorationBox = { field ->
                if (body.isEmpty()) {
                    Text("Scrie notita...", color = SUBTEXT, fontSize = 15.sp)
                }
                field()
            },
        )

        // BELOW the text, not above it. Selecting a word makes Android
        // raise its OWN floating menu -- copy, share, translate -- directly
        // over the selection, and a format bar sitting there gets covered:
        // the first attempt put it above the text and a tap meant for bold
        // hit "Translate" and launched a web search. The bottom of the
        // screen is the one place that menu does not claim.
        FormatBar(
            spans = spans,
            selection = bodyField.selection.min to bodyField.selection.max,
            text = body,
            onChange = { spans = it },
        )
    }
}

private const val ALL_CATEGORIES = "Toate"

/**
 * Categories a note can be filed under: the ones settings.json knows and
 * the ones notes are already using, together. Settings alone would miss a
 * category imported with a backup; the notes alone would forget one the
 * user made and has not used yet.
 */
fun categoriesFor(load: Notebook.Load): List<String> =
    (load.categories.keys + load.notes.map { it.category }).distinct().sorted()

/**
 * Every checklist change reaches the screen the same way: re-read the
 * file on success, and say what went wrong on failure. Never let a change
 * that did not reach the disk look like one that did.
 */
private inline fun applyChecklist(
    result: SaveResult,
    onOk: () -> Unit,
    onFail: (String) -> Unit,
) {
    when (result) {
        is SaveResult.Ok -> onOk()
        is SaveResult.Failed -> onFail("Lista nu s-a salvat: ${result.reason}")
    }
}

@Composable
fun NotebookScreen(
    loaded: Notebook.Load,
    onTrash: () -> Unit,
    onOpen: (Note) -> Unit,
    onInspect: (String) -> Notebook.Inspection,
    onApply: (List<org.json.JSONObject>, Boolean) -> Unit,
    notice: String?,
    onDismissNotice: () -> Unit,
) {
    var importing by rememberSaveable { mutableStateOf(false) }
    // rememberSaveable, not remember: a phone rotates, and a search the
    // user typed should survive that rather than be thrown away.
    var query by rememberSaveable { mutableStateOf("") }
    var orderName by rememberSaveable { mutableStateOf(SortOrder.UPDATED_NEW.name) }
    var category by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    val order = SortOrder.valueOf(orderName)

    // The categories actually in use, not every one settings.json knows
    // about: a filter offering choices that can only ever return nothing
    // wastes the reader's time.
    val categories = remember(loaded.notes) {
        listOf(ALL_CATEGORIES) + loaded.notes.map { it.category }.distinct().sorted()
    }
    val filtered = remember(query, category, order, loaded.notes) {
        sortNotes(
            loaded.notes.filter {
                it.matches(query) &&
                    (category == ALL_CATEGORIES || it.category == category)
            },
            order,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(top = 48.dp, bottom = 12.dp)) {
            Text(
                text = "Note Organizer",
                color = TEXT,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    loaded.problem != null -> loaded.problem
                    // Anything narrowing the list changes this line, not
                    // just the search box: a category filter left it saying
                    // "77 notite" over a list of one.
                    query.isEmpty() && category == ALL_CATEGORIES ->
                        "${loaded.notes.size} notite citite din fisierul real"
                    else -> "${filtered.size} din ${loaded.notes.size} notite"
                },
                color = if (loaded.problem != null) WARN else SUBTEXT,
                fontSize = 12.sp,
            )
        }

        SearchField(query) { query = it }

        FilterBar(
            order = order,
            category = category,
            categories = categories,
            trashCount = loaded.trash.size,
            onOrder = { orderName = it.name },
            onCategory = { category = it },
            onTrash = onTrash,
            onImport = { importing = !importing },
        )
        if (importing) {
            ImportPanel(
                inspect = onInspect,
                onApply = { notes, update -> onApply(notes, update); importing = false },
                onClose = { importing = false },
            )
        }
        notice?.let {
            Text(
                text = it,
                color = WARN,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .clickable { onDismissNotice() },
            )
        }

        if (filtered.isEmpty() && loaded.notes.isNotEmpty()) {
            // The desktop separates "nothing matches" from "no notes at
            // all", because the two ask the reader to do different things.
            Text(
                text = "Nicio notita nu se potriveste cautarii.",
                color = SUBTEXT,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        LazyColumn {
            items(filtered) { note ->
                NoteCard(note, loaded.categories, onOpen)
            }
        }
    }
}

/**
 * The Trash: what was deleted, how long it has left, and the way back.
 *
 * Nothing here deletes anything permanently, on purpose. The desktop owns
 * the purge — it is the one that can also remove a purged note's
 * attachment files, and a phone that emptied the Trash without them would
 * leave orphans behind on every machine that syncs the folder. So this
 * screen reads the countdown and never runs it.
 */
@Composable
fun TrashScreen(
    load: Notebook.Load,
    notice: String?,
    onRestore: (Note) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(top = 48.dp, bottom = 12.dp)) {
            Text(
                text = "‹  Inapoi",
                color = ACCENT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() }.padding(bottom = 12.dp),
            )
            Text(
                text = "Cos",
                color = TEXT,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Romanian agreement, not "1 notite sterse".
                text = when (load.trash.size) {
                    0 -> "gol"
                    1 -> "o notita stearsa · se sterge definitiv de pe " +
                        "calculator dupa ${load.retentionDays} de zile"
                    else -> "${load.trash.size} notite sterse · se sterg definitiv " +
                        "de pe calculator dupa ${load.retentionDays} de zile"
                },
                color = SUBTEXT,
                fontSize = 12.sp,
            )
        }
        notice?.let {
            Text(text = it, color = WARN, fontSize = 12.sp,
                 modifier = Modifier.padding(bottom = 12.dp))
        }
        LazyColumn {
            items(load.trash) { note ->
                val left = Notebook.daysLeftInTrash(note.deletedAt, load.retentionDays)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CARD)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = note.headline,
                        color = if (note.headlineIsBorrowed) SUBTEXT else TEXT,
                        fontSize = 15.sp,
                        fontWeight = if (note.headlineIsBorrowed) FontWeight.Normal
                                     else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            // No readable stamp means no countdown, never a
                            // guess that it is about to go.
                            left == null -> "sters (data necunoscuta)"
                            left == 0 -> "expira la urmatoarea pornire a aplicatiei de pe calculator"
                            left == 1 -> "mai are o zi"
                            else -> "mai are $left zile"
                        },
                        color = if (left != null && left <= 3) WARN else SUBTEXT,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "Adu inapoi",
                        color = ACCENT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onRestore(note) },
                    )
                }
            }
        }
    }
}

/**
 * Sort and category, as two chips that open an inline list of choices.
 *
 * Inline rather than a floating menu on purpose: a popup that draws over
 * the list hides the very thing the choice is about, and on a phone the
 * list simply moving down a little reads as an answer to the tap.
 */
@Composable
fun FilterBar(
    order: SortOrder,
    category: String,
    categories: List<String>,
    trashCount: Int,
    onOrder: (SortOrder) -> Unit,
    onCategory: (String) -> Unit,
    onTrash: () -> Unit,
    onImport: () -> Unit,
) {
    var openMenu by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Row {
            Chip("⇅  ${order.label}", openMenu == "sort") {
                openMenu = if (openMenu == "sort") "" else "sort"
            }
            Box(modifier = Modifier.width(8.dp))
            Chip("▤  $category", openMenu == "cat") {
                openMenu = if (openMenu == "cat") "" else "cat"
            }
            // Only when there is something in it: an always-present empty
            // Trash is a button that does nothing most of the time.
            if (trashCount > 0) {
                Box(modifier = Modifier.width(8.dp))
                Chip("🗑  $trashCount", false) { onTrash() }
            }
            Box(modifier = Modifier.width(8.dp))
            Chip("↓  cod", false) { onImport() }
        }
        when (openMenu) {
            "sort" -> MenuPanel(
                options = SortOrder.entries.map { it.label },
                chosen = order.label,
            ) { label ->
                onOrder(SortOrder.entries.first { it.label == label })
                openMenu = ""
            }
            "cat" -> MenuPanel(options = categories, chosen = category) {
                onCategory(it)
                openMenu = ""
            }
        }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.White else SUBTEXT,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) ACCENT else CARD)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/**
 * Bold, italic, underline, strike, colour and highlight, over whatever is
 * selected.
 *
 * Greyed out with nothing selected rather than hidden: a bar that appears
 * and disappears makes the writing area jump under the thumb, and a
 * button that is visibly off explains itself.
 *
 * The selection arrives in UTF-16, the way a text field counts. The file
 * counts code points. Converting is not optional — an emoji earlier in
 * the note would otherwise put every style one character late.
 */
@Composable
fun FormatBar(
    spans: List<FormatSpan>,
    selection: Pair<Int, Int>,
    text: String,
    onChange: (List<FormatSpan>) -> Unit,
) {
    var palette by remember { mutableStateOf("") }
    val (fromUtf16, toUtf16) = selection
    val hasSelection = toUtf16 > fromUtf16
    val offsets = remember(text, fromUtf16, toUtf16) {
        codePointOffsets(text, listOf(fromUtf16, toUtf16))
    }
    val from = offsets[fromUtf16] ?: 0
    val to = offsets[toUtf16] ?: 0
    val active = remember(spans, from, to) { stylesOver(spans, from, to) }

    fun apply(style: String, value: String = "") {
        if (!hasSelection) return
        onChange(toggleStyle(spans, from, to, style, value))
    }

    Column(modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)) {
        if (palette.isNotEmpty() && hasSelection) {
            val swatches = if (palette == "text") PRESET_COLORS else HIGHLIGHT_COLORS
            val style = if (palette == "text") "text_color" else "highlight"
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(8.dp))
                    .background(CARD)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BG)
                        .clickable {
                            onChange(clearStyle(spans, from, to, style))
                            palette = ""
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = SUBTEXT, fontSize = 12.sp) }
                for (swatch in swatches) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(hexColor(swatch) ?: CARD)
                            .clickable { apply(style, swatch); palette = "" },
                    )
                }
            }
        }
        if (!hasSelection) {
            Text(
                text = "selecteaza niste text ca sa-l formatezi",
                color = SUBTEXT,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row {
            FormatButton("B", active.contains("bold"), hasSelection,
                FontWeight.Bold) { apply("bold") }
            FormatButton("I", active.contains("italic"), hasSelection,
                FontWeight.Normal, italic = true) { apply("italic") }
            FormatButton("U", active.contains("underline"), hasSelection,
                FontWeight.Normal, underline = true) { apply("underline") }
            FormatButton("S", active.contains("strike"), hasSelection,
                FontWeight.Normal, strike = true) { apply("strike") }
            FormatButton("A", active.contains("text_color"), hasSelection,
                FontWeight.Bold) {
                palette = if (palette == "text") "" else "text"
            }
            FormatButton("▬", active.contains("highlight"), hasSelection,
                FontWeight.Bold) {
                palette = if (palette == "mark") "" else "mark"
            }
        }
    }
}

@Composable
private fun FormatButton(
    label: String,
    on: Boolean,
    enabled: Boolean,
    weight: FontWeight,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = when {
            !enabled -> Color(0xFF55557A)
            on -> Color.White
            else -> TEXT
        },
        fontSize = 15.sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            underline -> TextDecoration.Underline
            strike -> TextDecoration.LineThrough
            else -> null
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(end = 6.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) ACCENT else CARD)
            .clickable(enabled = enabled) { onClick() }
            .padding(top = 9.dp),
    )
}

/**
 * The desktop's fifteen note colours, plus "none".
 *
 * Laid out as a grid of wrapping rows rather than one scrolling line: a
 * palette you have to scroll is a palette where the colour you want is
 * the one off-screen.
 */
@Composable
fun ColorPanel(chosen: String, onPick: (String) -> Unit) {
    val swatches = listOf("") + PRESET_COLORS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(12.dp)
    ) {
        swatches.chunked(8).forEach { line ->
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                for (swatch in line) {
                    val picked = swatch == chosen
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (picked) Color.White else BG)
                            .padding(if (picked) 3.dp else 0.dp)
                            .clip(CircleShape)
                            .background(hexColor(swatch) ?: CARD)
                            .clickable { onPick(swatch) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // "No colour" needs to look like a choice rather
                        // than like a swatch that failed to load.
                        if (swatch.isEmpty()) {
                            Text("✕", color = SUBTEXT, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuPanel(options: List<String>, chosen: String, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
    ) {
        for (option in options) {
            Text(
                text = option,
                color = if (option == chosen) ACCENT else TEXT,
                fontSize = 14.sp,
                fontWeight = if (option == chosen) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(option) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🔍", fontSize = 14.sp)
        Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) {
                Text(text = "Cauta in notite...", color = SUBTEXT, fontSize = 15.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
                cursorBrush = SolidColor(ACCENT),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                text = "✕",
                color = SUBTEXT,
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable { onChange("") }
                    .padding(start = 10.dp),
            )
        }
    }
}

@Composable
fun NoteCard(note: Note, categories: Map<String, String>, onOpen: (Note) -> Unit) {
    val stripe = stripeColor(note, categories)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .clickable { onOpen(note) }
    ) {
        // The desktop's 5-pixel colour stripe down the left edge of the card.
        Row(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(stripe)
        ) {}
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // A borrowed headline is set lighter than a real title, so the
            // card still says which notes the user actually named.
            Text(
                text = badges(note) + note.headline,
                color = if (note.headlineIsBorrowed) SUBTEXT else TEXT,
                fontSize = 15.sp,
                fontWeight = if (note.headlineIsBorrowed) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.snippet.isNotEmpty()) {
                Text(
                    text = note.snippet,
                    color = SUBTEXT,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = note.tagLine(),
                color = stripe,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What editing this particular note as plain text will cost, said before
 * the user starts rather than after they save.
 */
fun editWarning(note: Note): String? {
    val parts = ArrayList<String>()
    if (note.formatting.isNotEmpty()) {
        parts.add("are formatare — stilurile urmaresc textul, dar cele " +
            "peste text sters se pierd")
    }
    if (note.tableCount > 0) {
        parts.add("tabelele apar ca text cu bare verticale")
    }
    if (note.checklistTotal > 0) {
        parts.add("checklistul nu se editeaza aici")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

@Composable
fun NoteScreen(
    note: Note,
    categories: Map<String, String>,
    notice: String?,
    onEdit: () -> Unit,
    onToggleChecklist: (Int, Boolean) -> Unit,
    onAddChecklist: (String) -> Unit,
    onRenameChecklist: (Int, String) -> Unit,
    onRemoveChecklist: (Int) -> Unit,
    onEditTable: (Int, (TableEdit) -> TableEdit) -> Unit,
    shareCode: () -> String?,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val stripe = stripeColor(note, categories)
    var sharing by remember(note.id) { mutableStateOf<String?>(null) }
    // The page this text sits on, so highlights blend toward it instead of
    // washing it out. On a light theme this argument changes, nothing else.
    val rendered = renderBody(note, BG)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹  Inapoi",
                color = ACCENT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() },
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Partajeaza",
                color = ACCENT,
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable { sharing = if (sharing == null) shareCode() else null }
                    .padding(end = 18.dp),
            )
            Text(
                text = "Editeaza",
                color = ACCENT,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onEdit() },
            )
        }
        sharing?.let { code -> SharePanel(code) { sharing = null } }
        notice?.let {
            Text(
                text = it,
                color = WARN,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            text = badges(note) + note.headline,
            color = if (note.headlineIsBorrowed) SUBTEXT else TEXT,
            fontSize = 21.sp,
            fontWeight = if (note.headlineIsBorrowed) FontWeight.Normal else FontWeight.Bold,
        )
        Text(
            text = note.tagLine(),
            color = stripe,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        // The body is drawn in pieces: prose keeps its formatting by being
        // SLICED out of the already-rendered text, and each table becomes
        // a grid. Re-rendering a piece on its own would mean recomputing
        // the formatting offsets for it -- a second copy of a rule that
        // would drift from the first.
        var tableEditing by remember(note.id) { mutableStateOf(-1) }
        var cellEditing by remember(note.id) { mutableStateOf<CellAt?>(null) }
        var blockIndex = 0
        for (part in remember(note.body) { splitBody(note.body) }) {
            when (part) {
                is BodyPart.Prose -> Text(
                    text = rendered.text.subSequence(
                        part.from.coerceIn(0, rendered.text.length),
                        part.to.coerceIn(0, rendered.text.length),
                    ),
                    color = TEXT,
                    fontSize = 15.sp,
                )
                is BodyPart.Grid -> {
                    val index = blockIndex++
                    val isEditing = tableEditing == index
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isEditing) "Gata cu tabelul"
                                   else "Editeaza tabelul",
                            color = ACCENT,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                tableEditing = if (isEditing) -1 else index
                                cellEditing = null
                            },
                        )
                        if (isEditing) {
                            Text(
                                text = "  \u00B7  atinge o celula",
                                color = SUBTEXT,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    TableView(
                        table = part.table,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        editing = isEditing,
                        onCellTap = { cellEditing = it },
                    )
                    val cell = cellEditing
                    if (isEditing && cell != null) {
                        CellEditor(
                            initial = cellTextAt(part.table, cell),
                            // The drawn grid skips the separator row, so a
                            // row taken off the screen has to be turned
                            // back into a file line before it means
                            // anything to the editor.
                            onDone = { text ->
                                val at = part.table.fileRow(cell.row)
                                onEditTable(index) { it.setCell(at, cell.column, text) }
                                cellEditing = null
                            },
                            onCancel = { cellEditing = null },
                            onRowAfter = {
                                val at = part.table.fileRow(cell.row)
                                onEditTable(index) { it.insertRowAfter(at) }
                                cellEditing = null
                            },
                            onRowDelete = {
                                val at = part.table.fileRow(cell.row)
                                onEditTable(index) { it.removeRow(at) }
                                cellEditing = null
                            },
                            onColumnAfter = {
                                onEditTable(index) { it.insertColumnAfter(cell.column) }
                                cellEditing = null
                            },
                            onColumnDelete = {
                                onEditTable(index) { it.removeColumn(cell.column) }
                                cellEditing = null
                            },
                        )
                    }
                }
            }
        }

        var listEditing by remember(note.id) { mutableStateOf(false) }
        if (note.checklist.isNotEmpty() || listEditing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Checklist",
                    color = SUBTEXT,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = if (listEditing) "Gata" else "Editeaza lista",
                    color = ACCENT,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { listEditing = !listEditing },
                )
            }
            note.checklist.forEachIndexed { index, item ->
                if (listEditing) {
                    ChecklistRowEditor(
                        text = item.text,
                        onRename = { onRenameChecklist(index, it) },
                        onRemove = { onRemoveChecklist(index) },
                    )
                } else {
                    Text(
                        text = (if (item.done) "☑  " else "☐  ") + item.text,
                        color = if (item.done) SUBTEXT else TEXT,
                        fontSize = 15.sp,
                        textDecoration =
                            if (item.done) TextDecoration.LineThrough else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The whole row, not just the little box: a 3mm
                            // target is a desktop habit, and a thumb is wider
                            // than a mouse pointer.
                            .clickable { onToggleChecklist(index, !item.done) }
                            .padding(vertical = 7.dp),
                    )
                }
            }
            if (listEditing) ChecklistAdder(onAdd = onAddChecklist)
        } else {
            Text(
                text = "+ adauga un checklist",
                color = SUBTEXT,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clickable { listEditing = true }
                    .padding(vertical = 6.dp),
            )
        }

        if (note.comments.isNotEmpty()) {
            Text(
                text = "Comentarii",
                color = SUBTEXT,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
            )
            Text(text = note.comments, color = TEXT, fontSize = 14.sp)
        }

        // What this screen could not draw, said out loud rather than left
        // for the reader to notice as a difference from the desktop.
        val gaps = buildList {
            // Cell text is drawn plain: the styling spans are offsets into
            // the whole body, and a cell is not a slice of it once the row
            // has been taken apart. Only worth saying when the note has
            // both, since otherwise there is nothing to miss.
            if (note.tableCount > 0 && note.formatting.isNotEmpty()) {
                add("stilurile din celulele de tabel nu se deseneaza")
            }
            if (note.attachments > 0) add("${note.attachments} atasamente neafisate")
            if (rendered.ignoredStyles.isNotEmpty()) {
                add("stiluri necitite: " + rendered.ignoredStyles.joinToString(", "))
            }
        }
        if (gaps.isNotEmpty()) {
            Text(
                text = gaps.joinToString(" · "),
                color = WARN,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        // Delete sits at the FOOT of the note, not in the header beside
        // Back: a thumb reaching for the top-left of a phone passes over
        // the top-right, and the destructive action should not be where a
        // miss lands. Two taps, not a dialog -- the second tap is the
        // confirmation, and it says so.
        var armed by remember(note.id) { mutableStateOf(false) }
        Text(
            text = if (armed) "Sigur? Apasa din nou ca sa mutam in cos"
                   else "Sterge notita",
            color = DANGER,
            fontSize = 14.sp,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .padding(top = 36.dp)
                .clickable { if (armed) onDelete() else armed = true }
                .padding(vertical = 8.dp),
        )
        if (armed) {
            Text(
                text = "Nu se pierde — ramane in cos si poate fi adusa inapoi.",
                color = SUBTEXT,
                fontSize = 12.sp,
            )
        }
        Text(text = "", modifier = Modifier.padding(bottom = 48.dp))
    }
}

/**
 * A note's share code, with the two things anyone wants to do with it.
 *
 * The code itself is shown, not hidden behind the buttons: it is only
 * text, and seeing it is what makes it obvious that this is a copy of the
 * words — **not encryption**. Anyone holding the code can read the note.
 */
@Composable
fun SharePanel(code: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(12.dp)
    ) {
        Text(
            text = "${code.length} caractere · copiaza-l oriunde — " +
                "nu e criptat, e doar textul notitei",
            color = SUBTEXT,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = code,
            color = TEXT,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = "Copiaza",
                color = ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(end = 20.dp),
            )
            Text(
                text = "Trimite",
                color = ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        val send = Intent(Intent.ACTION_SEND)
                        send.type = "text/plain"
                        send.putExtra(Intent.EXTRA_TEXT, code)
                        context.startActivity(Intent.createChooser(send, "Trimite notita"))
                    }
                    .padding(end = 20.dp),
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Inchide",
                color = SUBTEXT,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onClose() },
            )
        }
    }
}

/**
 * Pasting a code in — and, when it holds notes this device already has,
 * asking what to do about them before anything is written.
 *
 * The wording is the desktop's, deliberately: the same question asked in
 * two different ways on two devices is two questions to learn.
 */
@Composable
fun ImportPanel(
    inspect: (String) -> Notebook.Inspection,
    onApply: (List<org.json.JSONObject>, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Notebook.Inspection.Ready?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(12.dp)
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            // A share code is Base64: one capitalised letter or one
            // "corrected" sequence and it is no longer the same code. The
            // keyboard has to be told to keep its hands off, or its help
            // is indistinguishable from damage -- and all the app can then
            // say is "this code is damaged", which is true and useless.
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            ),
            textStyle = TextStyle(color = TEXT, fontSize = 12.sp,
                                  fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text("Lipeste un cod care incepe cu ${Sharing.PREFIX}",
                         color = SUBTEXT, fontSize = 12.sp)
                }
                field()
            },
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = "Lipeste",
                color = ACCENT,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { draft = clipboard.getText()?.text ?: draft }
                    .padding(end = 20.dp),
            )
            Text(
                text = "Inchide",
                color = SUBTEXT,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onClose() },
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Importa",
                color = if (draft.isBlank()) SUBTEXT else ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = draft.isNotBlank()) {
                    when (val found = inspect(draft)) {
                        is Notebook.Inspection.Failed -> problem = found.reason
                        is Notebook.Inspection.Ready -> {
                            problem = null
                            if (found.known == 0) {
                                onApply(found.notes, false)
                                draft = ""
                            } else {
                                // A code carrying notes already here is a
                                // question, not an instruction. Nothing is
                                // written until it is answered.
                                pending = found
                            }
                        }
                    }
                },
            )
        }
        problem?.let {
            Text(text = it, color = WARN, fontSize = 12.sp,
                 modifier = Modifier.padding(top = 10.dp))
        }
        pending?.let { found ->
            val many = found.known > 1
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Text(
                    text = if (many) "${found.known} notite sunt deja aici"
                           else "Notita e deja aici",
                    color = TEXT,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Actualizarea inlocuieste ce e pe telefon; pe " +
                        "calculator o poti anula cu \u201c\u21a9 Restore " +
                        "previous\u201d.\nAdaugarea le lasa pe amandoua, ca " +
                        "sa le poti compara.",
                    color = SUBTEXT,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row {
                    Text(
                        text = "Renunta",
                        color = SUBTEXT,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { pending = null }
                            .padding(end = 18.dp),
                    )
                    Text(
                        text = if (many) "Adauga-le ca notite noi"
                               else "Adauga ca notita noua",
                        color = ACCENT,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                onApply(found.notes, false)
                                pending = null
                                draft = ""
                            }
                            .padding(end = 18.dp),
                    )
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = if (many) "Actualizeaza-le" else "Actualizeaza",
                        color = ACCENT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onApply(found.notes, true)
                            pending = null
                            draft = ""
                        },
                    )
                }
            }
        }
    }
}

/** What a cell holds, spelled the way the file spells it. */
fun cellTextAt(table: TableBlock, at: CellAt): String {
    val cell = table.rows.getOrNull(at.row)?.getOrNull(at.column) ?: return ""
    return when (cell.checked) {
        // A checkbox is drawn as a box but edited as what it really is, so
        // it can be turned back into ordinary text by deleting the marker.
        true -> "[x]"
        false -> "[ ]"
        null -> cell.lines.joinToString(" ")
    }
}

/**
 * Editing one cell, with the structural changes that only make sense
 * while a cell is chosen: a row or column goes in beside THIS one, or
 * this one's row or column goes away.
 */
@Composable
fun CellEditor(
    initial: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    onRowAfter: () -> Unit,
    onRowDelete: () -> Unit,
    onColumnAfter: () -> Unit,
    onColumnDelete: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(12.dp)
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text("celula goala", color = SUBTEXT, fontSize = 15.sp)
                }
                field()
            },
        )
        Row(modifier = Modifier.padding(top = 10.dp)) {
            Text(
                text = "☑ bifa",
                color = SUBTEXT,
                fontSize = 13.sp,
                modifier = Modifier.clickable { draft = "[ ]" }.padding(end = 16.dp),
            )
            Text(
                text = "Renunta",
                color = SUBTEXT,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onCancel() },
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Pune",
                color = ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDone(draft) },
            )
        }
        Row(modifier = Modifier.padding(top = 14.dp)) {
            Text("+ rand", color = ACCENT, fontSize = 13.sp,
                 modifier = Modifier.clickable { onRowAfter() }.padding(end = 14.dp))
            Text("+ coloana", color = ACCENT, fontSize = 13.sp,
                 modifier = Modifier.clickable { onColumnAfter() })
            Box(modifier = Modifier.weight(1f))
            Text("✕ rand", color = DANGER, fontSize = 13.sp,
                 modifier = Modifier.clickable { onRowDelete() }.padding(end = 14.dp))
            Text("✕ coloana", color = DANGER, fontSize = 13.sp,
                 modifier = Modifier.clickable { onColumnDelete() })
        }
    }
}

/**
 * One checklist row while the list is being edited: the text, and a way
 * to take the row out.
 *
 * The rename is committed when the field loses focus rather than on every
 * keystroke — one write per edit instead of one per letter, on a file
 * that is half a megabyte.
 */
@Composable
fun ChecklistRowEditor(text: String, onRename: (String) -> Unit, onRemove: () -> Unit) {
    var draft by remember(text) { mutableStateOf(text) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(CARD)
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .onFocusChanged { state ->
                    if (!state.isFocused && draft != text) onRename(draft)
                },
        )
        Text(
            text = "✕",
            color = DANGER,
            fontSize = 15.sp,
            modifier = Modifier.clickable { onRemove() }.padding(start = 12.dp, end = 4.dp),
        )
    }
}

@Composable
fun ChecklistAdder(onAdd: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = TEXT, fontSize = 15.sp),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(CARD)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text("Un rand nou...", color = SUBTEXT, fontSize = 15.sp)
                }
                field()
            },
        )
        Text(
            text = "Adauga",
            color = if (draft.isBlank()) SUBTEXT else ACCENT,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(enabled = draft.isNotBlank()) {
                    onAdd(draft)
                    draft = ""
                }
                .padding(start = 12.dp),
        )
    }
}

/** The desktop's rule: the note's own colour, else its category's. */
private fun stripeColor(note: Note, categories: Map<String, String>): Color {
    val hex = note.color.ifEmpty { categories[note.category] ?: "" }
    return hexColor(hex) ?: ACCENT
}

private fun badges(note: Note): String {
    val out = StringBuilder()
    if (note.pinned) out.append("📌 ")
    if (note.isRecurring) out.append("🔁 ")
    return out.toString()
}
