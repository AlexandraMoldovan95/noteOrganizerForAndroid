package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val laidOut = Showing.rows(shown, grouped, collapsed, load.categories.keys.toList())
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Custom order, arranged by hand: the handle on each card drags it. While
    // it is dragged the card trades places with its neighbours as it passes
    // them, so its own slot follows the finger -- a card left at its old
    // slot would be dropped from the list by the first long scroll, and its
    // drag with it -- and the one move is written when it is let go.
    val listState = rememberLazyListState()
    // A new search starts its results at the top: kept in place, the first
    // matches of a search typed with the list scrolled down sat above the
    // screen (the owner's call, 2026-10-21, as on the desktop). Back from a
    // note, the search is the one the list was drawn for, and it keeps its
    // place.
    var listedFor by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != listedFor) {
            listedFor = query
            listState.scrollToItem(0)
        }
    }
    val arranging = order == SortOrder.CUSTOM && !selecting
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragBy by remember { mutableFloatStateOf(0f) }
    var dragOrder by remember { mutableStateOf<List<Showing.Row>?>(null) }
    val rows = dragOrder ?: laidOut
    fun noteOf(row: Showing.Row?) = (row as? Showing.Row.Item)?.note

    /** The dragged card one place up or down, when the place is in its block. */
    fun trade(step: Int): Boolean {
        val now = dragOrder ?: return false
        val at = now.indexOfFirst { noteOf(it)?.id == dragged }
        val other = noteOf(now.getOrNull(at + step)) ?: return false
        val me = noteOf(now[at]) ?: return false
        if (!Organizing.sameBlock(me, other, grouped)) return false
        dragOrder = now.toMutableList().also { it[at] = it[at + step]; it[at + step] = now[at] }
        return true
    }

    fun drop() {
        val id = dragged
        val final = dragOrder
        dragged = null
        dragBy = 0f
        dragOrder = null
        if (id == null || final == null || final == laidOut) return
        val at = final.indexOfFirst { noteOf(it)?.id == id }
        val next = noteOf(final.getOrNull(at + 1))
        val previous = noteOf(final.getOrNull(at - 1))
        when {
            next != null && Organizing.sameBlock(noteOf(final[at])!!, next, grouped) ->
                state.moveNote(id, next.id, before = true, grouped = grouped)
            previous != null -> state.moveNote(id, previous.id, before = false, grouped = grouped)
        }
    }

    /** The dragged card followed by the finger, trading places as it passes a neighbour's middle. */
    fun follow(amount: Float) {
        dragBy += amount
        val info = listState.layoutInfo.visibleItemsInfo
        val me = info.firstOrNull { it.key == dragged } ?: return
        // A trade shows on the next frame; until then the layout is the old one.
        if (me.index != (dragOrder ?: return).indexOfFirst { noteOf(it)?.id == dragged }) return
        val centre = me.offset + me.size / 2 + dragBy
        val below = info.firstOrNull { it.index == me.index + 1 }
        val above = info.firstOrNull { it.index == me.index - 1 }
        if (below != null && centre > below.offset + below.size / 2 && trade(1)) dragBy -= below.size
        else if (above != null && centre < above.offset + above.size / 2 && trade(-1)) dragBy += above.size
    }

    // Near the list's top or bottom edge, a dragged card scrolls the list --
    // and goes on trading places as the list passes under it, though the
    // finger is still: otherwise its slot stayed behind, scrolled out of
    // sight, and the card was no longer drawn under the finger at all.
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val info = listState.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == dragged }
            if (me != null) {
                val top = me.offset + dragBy
                val step = when {
                    top < info.viewportStartOffset + edge -> -14f
                    top + me.size > info.viewportEndOffset - edge -> 14f
                    else -> 0f
                }
                if (step != 0f) {
                    dragBy += listState.scrollBy(step)
                    follow(0f)
                }
            }
            delay(16)
        }
    }


    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                val allPinned = selected.all { id -> load.notes.firstOrNull { it.id == id }?.pinned == true }
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, selected.size, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }, modifier = Modifier.said(R.string.clear_selection_said)) {
                            Text("✕", fontSize = 18.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { state.setPinned(selected, !allPinned); selected = emptySet() },
                                   modifier = Modifier.said(if (allPinned) R.string.unpin else R.string.pin)) {
                            Text("📌", fontSize = 18.sp)
                        }
                        IconButton(onClick = { choosingCategory = true }, modifier = Modifier.said(R.string.move_to_category_said)) {
                            Text("🗂", fontSize = 18.sp)
                        }
                        IconButton(onClick = { state.trash(selected); selected = emptySet() },
                                   modifier = Modifier.said(R.string.move_to_trash)) {
                            Text("🗑", fontSize = 18.sp)
                        }
                        IconButton(onClick = { moreMenu = true }, modifier = Modifier.said(R.string.more_options_said)) {
                            Text("⋮", fontSize = 20.sp)
                        }
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
                            val everything = Showing.visibleIds(laidOut)
                            DropdownMenuItem(
                                text = { Text(stringResource(if (selected.containsAll(everything)) R.string.unselect_all else R.string.select_all)) },
                                // Exactly what the list shows -- never a note
                                // the search, the filter or a folded group is hiding.
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
                        IconButton(onClick = { moreMenu = true }, modifier = Modifier.said(R.string.more_options_said)) {
                            Text("⋮", fontSize = 20.sp)
                        }
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
                                onClick = { sortMenu = false; state.chooseSort(option, from = order) },
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

            // The day's reminder, the desktop's bar in its accent: once a
            // day, when notes fall on it. A tap opens the day on the calendar.
            state.reminderDue?.let { due ->
                val today = Recurrence.formatDate(java.time.LocalDate.now())
                val day = Recurrence.parseDate(today)?.let { Recurrence.showDay(it, year = false) } ?: today
                val closeSaid = stringResource(R.string.reminder_close)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent)
                        .clickable { state.todayAsked += 1 }
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔔  " + pluralStringResource(R.plurals.reminder_today, due, due, day),
                         color = palette.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                         modifier = Modifier.weight(1f))
                    Text("✕", color = palette.onAccent, fontSize = 14.sp,
                         modifier = Modifier
                             .semantics { contentDescription = closeSaid }
                             .clickable { state.reminderDue = null }
                             .padding(horizontal = 14.dp))
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
                         modifier = Modifier.said(R.string.dismiss_said).clickable { state.notice = null }.padding(horizontal = 14.dp))
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
                else -> LazyColumn(state = listState,
                                   contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)) {
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
                                val lifted = note.id == dragged
                                val moveLabel = stringResource(R.string.drag_to_move, note.headline)
                                Box(
                                    Modifier
                                        .zIndex(if (lifted) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (lifted) dragBy else 0f
                                            shadowElevation = if (lifted) 8.dp.toPx() else 0f
                                        },
                                ) {
                                    NoteCard(
                                        note = note,
                                        colour = noteColour(note, load.categories, palette.hex.getValue("accent")),
                                        selected = note.id in selected,
                                        selecting = selecting,
                                        onClick = {
                                            if (selecting) selected = selected.toggle(note.id) else state.openNote(note.id)
                                        },
                                        onLongClick = { selected = selected.toggle(note.id) },
                                        // Seen only where the list stays beside the
                                        // note: on a phone the note covers it.
                                        open = note.id == state.openId,
                                        handle = if (!arranging) null else { {
                                            Text(
                                                "⠿",
                                                color = palette.subtext,
                                                fontSize = 22.sp,
                                                modifier = Modifier
                                                    .semantics { contentDescription = moveLabel }
                                                    .pointerInput(note.id) {
                                                        // Grabbed at the touch, not after the
                                                        // finger has travelled: a hand that
                                                        // presses the handle and pauses before
                                                        // moving gave the card under it a long
                                                        // press, and selection took the drag.
                                                        awaitEachGesture {
                                                            val down = awaitFirstDown(requireUnconsumed = false)
                                                            down.consume()
                                                            dragged = note.id
                                                            dragBy = 0f
                                                            dragOrder = laidOut
                                                            try {
                                                                while (true) {
                                                                    val change = awaitPointerEvent().changes
                                                                        .firstOrNull { it.id == down.id } ?: break
                                                                    if (!change.pressed) break
                                                                    val moved = change.positionChange()
                                                                    if (moved.y != 0f) {
                                                                        change.consume()
                                                                        follow(moved.y)
                                                                    }
                                                                }
                                                                drop()
                                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                                // Interrupted, nothing moves.
                                                                dragged = null
                                                                dragBy = 0f
                                                                dragOrder = null
                                                                throw e
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                            )
                                        } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Not while the keyboard is up: the list shrinks over it, and with the
        // phone on its side + sat on the search box. Down, it comes back.
        if (!selecting && !WindowInsets.isImeVisible) {
            FloatingActionButton(
                onClick = { state.newNote() },
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Text("+", fontSize = 28.sp, modifier = Modifier.said(R.string.new_note_said)) }
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
            Text("✕", color = palette.subtext, fontSize = 16.sp,
                 modifier = Modifier.said(R.string.clear_search_said).clickable { onChange("") }.padding(start = 10.dp))
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
    AppSheet(onDismissRequest = onDismiss) {
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
