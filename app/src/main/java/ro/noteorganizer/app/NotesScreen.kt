package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette

/**
 * The list of notes: search, sort and show at the top, the desktop's cards,
 * the + for a new note. Touch and hold a card to select it; while anything
 * is selected, a tap selects too, and the bar at the top acts on them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(state: AppState, onImportCode: () -> Unit) {
    val palette = LocalPalette.current
    val load = state.load
    var query by rememberSaveable { mutableStateOf("") }
    // Remembered across starts by the desktop's own keys and values, so a
    // choice made on one device reads back on the other.
    val order = SortOrder.fromLabel(state.settings.opt(Settings.NOTE_SORT) as? String)
    val show = Showing.valid(state.settings.opt(Settings.NOTE_FILTER) as? String, load.categories.keys)
    val grouped = state.settings.opt(Settings.GROUP_BY_CATEGORY) == true
    val collapsed = Settings.collapsedGroups(state.settings)
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    val shown = remember(state.revision, query, show, order) {
        val today = Recurrence.formatDate(java.time.LocalDate.now())
        sortNotes(load.notes.filter { it.matches(query) && Showing.passes(it, show) }, order, today) { note ->
            state.rawNote(note.id)?.let { Recurrence.nextOnOrAfter(it, today) }
        }
    }
    val rows = Showing.rows(shown, grouped, collapsed, load.categories.keys.toList())
    BackHandler(enabled = selecting) { selected = emptySet() }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                val allPinned = selected.all { id -> load.notes.firstOrNull { it.id == id }?.pinned == true }
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, selected.size, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Text("✕", fontSize = 18.sp) }
                    },
                    actions = {
                        IconButton(onClick = { state.setPinned(selected, !allPinned); selected = emptySet() }) {
                            Text("📌", fontSize = 18.sp)
                        }
                        IconButton(onClick = { choosingCategory = true }) { Text("🗂", fontSize = 18.sp) }
                        IconButton(onClick = { state.trash(selected); selected = emptySet() }) {
                            Text("🗑", fontSize = 18.sp)
                        }
                        IconButton(onClick = { moreMenu = true }) { Text("⋮", fontSize = 20.sp) }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (allPinned) R.string.unpin else R.string.pin)) },
                                onClick = { moreMenu = false; state.setPinned(selected, !allPinned); selected = emptySet() },
                            )
                            if (selected.size == 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = { moreMenu = false; state.duplicate(selected.single()); selected = emptySet() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_code)) },
                                onClick = {
                                    moreMenu = false
                                    state.shareCode(selected)?.let { sendText(state, it) }
                                },
                            )
                            val everything = shown.map { it.id }.toSet()
                            DropdownMenuItem(
                                text = { Text(stringResource(if (selected.containsAll(everything)) R.string.unselect_all else R.string.select_all)) },
                                // Exactly what the list shows -- never a note
                                // the search or the filter is hiding.
                                onClick = {
                                    moreMenu = false
                                    selected = if (selected.containsAll(everything)) emptySet() else everything
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.sidebar),
                )
            } else {
                TopAppBar(
                    title = {
                        val name = Settings.username(state.settings)
                        Text(if (name.isNotEmpty()) stringResource(R.string.greeting, name) else stringResource(R.string.app_name))
                    },
                    actions = {
                        IconButton(onClick = { moreMenu = true }) { Text("⋮", fontSize = 20.sp) }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_code)) },
                                onClick = { moreMenu = false; onImportCode() },
                            )
                            DropdownMenuItem(
                                text = { Text((if (grouped) "✓ " else "") + stringResource(R.string.group_by_category)) },
                                onClick = { moreMenu = false; state.putSetting(Settings.GROUP_BY_CATEGORY, !grouped) },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg),
                )
            }

            SearchBox(query) { query = it }

            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Pill(stringResource(R.string.sort_label, order.label)) { sortMenu = true }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        for (option in SortOrder.entries) {
                            DropdownMenuItem(
                                text = { Text(option.label, fontWeight = if (option == order) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortMenu = false; state.putSetting(Settings.NOTE_SORT, option.label) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    Pill(stringResource(R.string.show_label, if (show == Showing.ALL) stringResource(R.string.show_all) else show)) {
                        showMenu = true
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // The desktop's types first, then the categories.
                        for (option in listOf(Showing.ALL, Showing.REPEATING, Showing.DATED, Showing.FILES)) {
                            DropdownMenuItem(
                                text = { Text(if (option == Showing.ALL) stringResource(R.string.show_all) else option,
                                              fontWeight = if (option == show) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { showMenu = false; state.putSetting(Settings.NOTE_FILTER, option) },
                            )
                        }
                        HorizontalDivider()
                        for ((name, colour) in load.categories) {
                            DropdownMenuItem(
                                leadingIcon = { Dot(colour) },
                                text = { Text(name, fontWeight = if (name == show) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { showMenu = false; state.putSetting(Settings.NOTE_FILTER, name) },
                            )
                        }
                    }
                }
            }

            // What the start did, said once: a calm banner rather than red
            // words -- most of what it says is news, not trouble, and red
            // read as an error over a notebook that had simply arrived.
            state.notice?.let { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line, color = palette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("✕", color = palette.subtext, fontSize = 14.sp,
                         modifier = Modifier.clickable { state.notice = null }.padding(horizontal = 14.dp))
                }
            }

            when {
                load.notes.isEmpty() -> EmptyLine(stringResource(R.string.no_notes))
                shown.isEmpty() -> Column(Modifier.padding(16.dp)) {
                    // The desktop separates "nothing matches" from "no notes":
                    // they ask the reader to do different things.
                    Text(stringResource(R.string.no_match), color = palette.subtext, fontSize = 14.sp)
                    if (query.isNotEmpty()) {
                        Text(
                            stringResource(R.string.clear_search),
                            color = palette.accentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp).clickable { query = "" },
                        )
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)) {
                    items(rows, key = { row ->
                        when (row) {
                            is Showing.Row.Header -> "h:" + row.category
                            is Showing.Row.Item -> row.note.id
                        }
                    }) { row ->
                        when (row) {
                            is Showing.Row.Header -> GroupHeader(row, load.categories[row.category]) {
                                // Folded and unfolded under the desktop's own key.
                                val next = if (row.collapsed) collapsed - row.category else collapsed + row.category
                                state.putSetting(Settings.COLLAPSED_GROUPS, org.json.JSONArray(next.sorted()))
                            }
                            is Showing.Row.Item -> {
                                val note = row.note
                                NoteCard(
                                    note = note,
                                    colour = noteColour(note, load.categories, palette.hex.getValue("accent")),
                                    selected = note.id in selected,
                                    selecting = selecting,
                                    onClick = {
                                        if (selecting) selected = selected.toggle(note.id) else state.openNote(note.id)
                                    },
                                    onLongClick = { selected = selected.toggle(note.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!selecting) {
            FloatingActionButton(
                onClick = { state.newNote() },
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Text("+", fontSize = 28.sp) }
        }
    }

    if (choosingCategory) {
        CategorySheet(
            categories = load.categories,
            chosen = null,
            onPick = { name ->
                choosingCategory = false
                state.moveToCategory(selected, name)
                selected = emptySet()
            },
            onDismiss = { choosingCategory = false },
        )
    }
}

private fun Set<Int>.toggle(id: Int): Set<Int> = if (id in this) this - id else this + id

/** A category's header over its notes, when the list is grouped: tap to fold it. */
@Composable
private fun GroupHeader(row: Showing.Row.Header, colour: String?, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (row.collapsed) "▸" else "▾", color = palette.subtext, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Dot(colour ?: palette.hex.getValue("accent"), 10)
        Text("  ${row.category}  ·  ${row.count}", color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, color = LocalPalette.current.subtext, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
}

@Composable
fun Pill(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        text = "$label ▾",
        color = palette.text,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun SearchBox(query: String, onChange: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.sidebar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔍", fontSize = 14.sp)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 16.sp),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            decorationBox = { field ->
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = palette.subtext, fontSize = 16.sp)
                field()
            },
        )
        if (query.isNotEmpty()) {
            Text("✕", color = palette.subtext, fontSize = 16.sp, modifier = Modifier.clickable { onChange("") }.padding(start = 10.dp))
        }
    }
}

/** Every category, with its colour, to file notes under. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    categories: Map<String, String>,
    chosen: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Text(
            stringResource(R.string.choose_category),
            color = palette.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(categories.entries.toList(), key = { it.key }) { (name, colour) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(name) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(colour, 14)
                    Text(
                        name,
                        color = palette.text,
                        fontSize = 16.sp,
                        fontWeight = if (name == chosen) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 14.dp).weight(1f),
                    )
                    if (name == chosen) Text("✓", color = palette.accentText, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Android's own share sheet: WhatsApp, email, Messages, anything that takes text. */
fun sendText(state: AppState, text: String) {
    state.sendText?.invoke(text)
}
