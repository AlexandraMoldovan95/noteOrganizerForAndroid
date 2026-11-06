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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

/** A search box's words, handed to Android only while short ([Showing.searchKept]). */
internal val SearchSaver = androidx.compose.runtime.saveable.Saver<String, String>(
    save = { Showing.searchKept(it) }, restore = { it })

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
    var query by rememberSaveable(stateSaver = SearchSaver) { mutableStateOf("") }
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

    val today = state.today
    val shown = remember(state.revision, query, show, order, today) {
        // The notes as the file holds them, found by id from one reading:
        // looked up one at a time, each series read the whole notebook.
        val byId = if (order == SortOrder.SOONEST) state.rawNotes().associateBy { (it.opt("id") as? Number)?.toInt() }
                   else emptyMap()
        sortNotes(load.notes.filter { it.matches(query) && Showing.passes(it, show) }, order, today) { note ->
            byId[note.id]?.let { Recurrence.nextOnOrAfter(it, today) }
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
    // place: drawn again, the list starts from the search it is given back.
    // Not handed to Android: a second copy of the words, it doubled what the
    // app's stop carried (the final audit's eighth round).
    var listedFor by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != listedFor) {
            listedFor = query
            listState.scrollToItem(0)
        }
    }

    /** In words, what keeps [note] off the list -- or, with none, what keeps notes off it; "" for nothing. */
    fun hiding(note: Note?): String = listOfNotNull(
        "your search".takeIf { PyText.strip(query).isNotEmpty() && (note == null || !note.matches(query)) },
        "Show: $show".takeIf { show != Showing.ALL && (note == null || !Showing.passes(note, show)) },
        "its folded group".takeIf { note != null && grouped && note.category.ifEmpty { Themes.PROTECTED_CATEGORY } in collapsed },
    ).joinToString(" and ")

    // The row Show all scrolls to once the list holds it: [note]'s card, or
    // the top (-1). Left to itself the list keeps the card it had in sight,
    // and the notes Show had hidden came in above it, out of view -- the new
    // note Show all was pressed for among them.
    var reveal by remember { mutableStateOf<Int?>(null) }
    // Another sort, Show or grouping starts at the top, as a new search does:
    // kept on its first card, Title A–Z seemed to begin there, the notes
    // before it above the screen (wave 2, round 8). Before the effect below.
    var arrangedFor by remember { mutableStateOf(Triple(order, show, grouped)) }
    LaunchedEffect(order, show, grouped) {
        val now = Triple(order, show, grouped)
        if (now != arrangedFor) {
            arrangedFor = now
            if (reveal == null) reveal = -1
        }
    }
    LaunchedEffect(reveal, laidOut) {
        val id = reveal ?: return@LaunchedEffect
        val at = if (id == -1) 0 else laidOut.indexOfFirst { (it as? Showing.Row.Item)?.note?.id == id }
        if (at < 0) return@LaunchedEffect
        reveal = null
        listState.scrollToItem(at)
    }
    // A card an action moved -- pinned, copied, filed elsewhere -- brought
    // into sight where the list now has it, when it is out of sight: the top
    // was wrong under grouping, where a pin rises only within its group
    // (round 9). Let go when the list does not hold it, unlike Show all's.
    // In sight is judged from the cards in sight when the action was taken:
    // the list keeps the first of them, and the layout read after the change
    // was still the one before it.
    var revealCard by remember { mutableStateOf<Reveal?>(null) }
    // All the notes an action moved: the first of them is taken in the list's
    // new order (Showing.revealAt). Taken before it, a pin or a move of several
    // reordered them, and the card now first sat above the screen (round 13).
    fun revealLater(ids: Set<Int>) {
        if (ids.isEmpty()) return
        // In sight: half of it at least. A sliver under the list's top padding
        // counted, and a card put back above the screen read as shown (round 10).
        val info = listState.layoutInfo
        val inSight = info.visibleItemsInfo.filter {
            minOf(it.offset + it.size, info.viewportEndOffset) - maxOf(it.offset, 0) >= it.size / 2
        }
        // The item the list keeps, a sliver too: moved, the list goes with it
        // only so far, so the card is then always brought into sight (rounds 14-15).
        val firstIndex = listState.firstVisibleItemIndex
        val anchor = info.visibleItemsInfo.firstOrNull { it.index == firstIndex }?.key
        val lead = inSight.firstOrNull()?.let { (it.index - firstIndex).coerceAtLeast(0) } ?: 0
        revealCard = Reveal(ids, inSight.map { it.key }, firstIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
                            laidOut, anchor, lead)
    }
    LaunchedEffect(revealCard, laidOut) {
        val (ids, seen, atTop, listed, anchor, lead) = revealCard ?: return@LaunchedEffect
        val listedNow = laidOut.any { (it as? Showing.Row.Item)?.note?.id in ids }
        // Not in the list it was asked over: waited for once, as the Trash's
        // Undo puts notes back after the list was drawn; let go after that.
        if (!listedNow && laidOut == listed) return@LaunchedEffect
        Showing.revealAt(laidOut, ids, seen, atTop, anchor, lead)?.let { listState.scrollToItem(it) }
        revealCard = null
    }
    // Notes the Trash's Undo put back, brought into sight the same way.
    LaunchedEffect(state.revealAfterUndo) {
        val ids = state.revealAfterUndo ?: return@LaunchedEffect
        state.revealAfterUndo = null
        revealLater(ids)
    }
    /**
     * The list held where it is by place, for a change that moves the card
     * at the top of the screen without meaning to take the list along:
     * kept by its first card, the list followed a card unpinned there and the
     * notes still pinned went above it (wave 2, round 10); a card stepped or
     * dragged past the top card went above the screen (round 16).
     */
    fun holdByIndex() = listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    /** The list with nothing hiding: the search emptied, Show at All, [note]'s group unfolded, its card in sight. */
    fun showAll(note: Note?) {
        query = ""
        listedFor = ""
        reveal = note?.id ?: -1
        if (show != Showing.ALL) state.putSetting(Settings.NOTE_FILTER, Showing.ALL)
        val group = note?.category?.ifEmpty { Themes.PROTECTED_CATEGORY }
        if (group != null && group in collapsed) {
            state.putSetting(Settings.COLLAPSED_GROUPS, org.json.JSONArray((collapsed - group).sorted()))
        }
    }

    // A new note the list cannot show -- filed under General with Show at
    // Work, or not what the search looks for -- said once it is closed, with
    // the way back: it vanished from the list as it was written (the desktop
    // says it as the note is made).
    val madeNew = state.newNoteMade
    LaunchedEffect(madeNew, state.openId) {
        val id = madeNew ?: return@LaunchedEffect
        if (state.openId == id) return@LaunchedEffect
        state.newNoteMade = null
        val note = state.load.notes.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val hides = hiding(note)
        if (hides.isNotEmpty()) {
            state.offer("Saved in ${note.category.ifEmpty { Themes.PROTECTED_CATEGORY }}, hidden by $hides",
                "Show all") { showAll(note) }
        }
    }
    // Notes restored or imported where the list does not look, said the
    // same way once the list is on screen ([AppState.arrivals]).
    val arrivals = state.arrivals
    LaunchedEffect(arrivals) {
        val (ids, said) = arrivals ?: return@LaunchedEffect
        state.arrivals = null
        val hidden = state.load.notes.filter { it.id in ids && hiding(it).isNotEmpty() }
        val first = hidden.firstOrNull() ?: return@LaunchedEffect
        val tail = if (ids.size == 1) "hidden by ${hiding(first)}" else "${hidden.size} of them hidden by ${hiding(first)}"
        state.offer("$said, $tail", "Show all") { showAll(first) }
    }
    val arranging = order == SortOrder.CUSTOM && !selecting
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragBy by remember { mutableFloatStateOf(0f) }
    var dragOrder by remember { mutableStateOf<List<Showing.Row>?>(null) }
    // Where the dragged card's slot was before its last trade, in the
    // layout's terms, until a layout shows the trade.
    var slotWas by remember { mutableStateOf<Float?>(null) }
    // Which way the finger last moved, 0 before it has: a press held still at
    // the top card scrolled the list (round 17), and the top card dragged
    // down scrolled it up, against the finger (round 18).
    var heading by remember { mutableIntStateOf(0) }
    // How far the finger is from the press: the edge scrolls only past it
    // (Showing.edgeScroll).
    var pulledNow by remember { mutableFloatStateOf(0f) }
    // A trade's hold, asked again once the new order is composed: a finger's
    // event measures the list first, in the old order, and spent it -- the
    // list then followed its top card, and a card carried to the top went
    // out of sight with its drag (round 23).
    val hold = remember { Hold() }
    SideEffect {
        if (hold.index >= 0) {
            listState.requestScrollToItem(hold.index, hold.offset)
            hold.index = -1
        }
    }
    // Whether the dragged card has traded once as it leads the screen
    // (Showing.tradeStaysInSight).
    var ledOnce by remember { mutableStateOf(false) }
    // The space under a card, no part of a stepped pair (Showing.afterStep),
    // and the view a step is said through.
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val view = LocalView.current
    val rows = dragOrder ?: laidOut
    // As they are now, for a handle's gesture, which runs on as it was first
    // touched: read as they were then, a card's second drag started from the
    // order before its first, and wrote a place counted from it (round 17).
    val laidOutNow by rememberUpdatedState(laidOut)
    val groupedNow by rememberUpdatedState(grouped)
    fun noteOf(row: Showing.Row?) = (row as? Showing.Row.Item)?.note

    /**
     * The dragged card one place up or down, when the place is in its block;
     * the list held by place -- a trade with the top card took it along by a
     * card -- and a card leading the screen kept where it is as it steps up.
     */
    fun trade(step: Int, leads: Boolean): Boolean {
        val now = dragOrder ?: return false
        val at = now.indexOfFirst { noteOf(it)?.id == dragged }
        val other = noteOf(now.getOrNull(at + step)) ?: return false
        val me = noteOf(now[at]) ?: return false
        if (!Organizing.sameBlock(me, other, groupedNow)) return false
        // Asked only once the order is composed (the SideEffect): asked now,
        // a finger's measure in the old order laid a short list's card out of
        // the viewport, and cancelled its drag (wave 2, round 24).
        hold.index = if (step < 0 && leads) at - 1 else listState.firstVisibleItemIndex
        hold.offset = listState.firstVisibleItemScrollOffset
        if (step < 0 && leads) ledOnce = true
        dragOrder = now.toMutableList().also { it[at] = it[at + step]; it[at + step] = now[at] }
        return true
    }

    fun drop() {
        val id = dragged
        val final = dragOrder
        dragged = null
        dragBy = 0f
        dragOrder = null
        slotWas = null
        heading = 0
        pulledNow = 0f
        ledOnce = false
        if (id == null || final == null || final == laidOutNow) return
        val at = final.indexOfFirst { noteOf(it)?.id == id }
        val next = noteOf(final.getOrNull(at + 1))
        val previous = noteOf(final.getOrNull(at - 1))
        when {
            next != null && Organizing.sameBlock(noteOf(final[at])!!, next, groupedNow) ->
                state.moveNote(id, next.id, before = true, grouped = groupedNow)
            previous != null -> state.moveNote(id, previous.id, before = false, grouped = groupedNow)
        }
    }

    // The note a step moved, brought wholly into sight once the list shows
    // the step: past the top or the bottom card on screen it went out of
    // sight, or was left a strip (round 17).
    var stepped by remember { mutableStateOf<Stepped?>(null) }
    LaunchedEffect(stepped, laidOut) {
        val (id, step, sizeWas, passedWas) = stepped ?: return@LaunchedEffect
        val at = laidOut.indexOfFirst { noteOf(it)?.id == id }
        if (at < 0) {
            stepped = null
            return@LaunchedEffect
        }
        // The layout of this order: the one read at once was still the old one.
        val layout = withTimeoutOrNull(1_000) {
            snapshotFlow { listState.layoutInfo }.first { info ->
                info.visibleItemsInfo.isNotEmpty() && info.visibleItemsInfo.all {
                    it.index >= laidOut.size || it.key == Showing.key(laidOut[it.index])
                }
            }
        }
        stepped = null
        if (layout == null) return@LaunchedEffect
        // Above the list's foot, which is left for the + button; a card the
        // layout no longer holds is brought in beside the card it passed.
        val card = layout.visibleItemsInfo.firstOrNull { it.key == id }
        val passed = layout.visibleItemsInfo.firstOrNull { it.index == at - step }
        val by = Showing.afterStep(card?.offset, card?.size ?: sizeWas, passed?.offset, passed?.size ?: passedWas, step,
                                   layout.viewportEndOffset - layout.afterContentPadding, gap)
        when {
            by == null -> listState.scrollToItem(at)
            by != 0 -> listState.scrollBy(by.toFloat())
        }
    }

    /**
     * One place up ([step] -1) or down (+1), without a drag: a screen
     * reader's actions on the card, Switch Access, or a tap on its handle.
     * Dragging was the only way, and those could not arrange at all.
     */
    fun stepNote(id: Int, step: Int) {
        val (anchor, before) = Organizing.stepPlace(laidOutNow.mapNotNull { noteOf(it) }, id, step, groupedNow) ?: return
        val at = laidOutNow.indexOfFirst { noteOf(it)?.id == id }
        val shown = listState.layoutInfo.visibleItemsInfo
        val size = shown.firstOrNull { it.key == id }?.size ?: 0
        val passedSize = shown.firstOrNull { it.index == at + step }?.size ?: 0
        val first = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        // Held and scrolled only once the step is saved: a refused one scrolled
        // the list as if it had been made (round 19).
        if (!state.moveNote(id, anchor, before = before, grouped = groupedNow)) return
        // Up from the top of the screen, the card keeps its place there, its
        // neighbour under it; held at the index, it went above (round 17).
        if (step < 0 && at == first) listState.requestScrollToItem(at - 1, offset)
        else listState.requestScrollToItem(first, offset)
        stepped = Stepped(id, step, size, passedSize)
        // Said to a screen reader as it lands: silent, a step read as not
        // taken, and a second one moved the note on (round 22).
        noteOf(laidOutNow.getOrNull(at + step))?.let { passed ->
            view.announceForAccessibility(view.context.getString(
                if (step < 0) R.string.moved_above else R.string.moved_below, passed.headline))
        }
    }
    var stepMenu by remember { mutableStateOf<Int?>(null) }
    val upSaid = stringResource(R.string.move_up)
    val downSaid = stringResource(R.string.move_down)
    val nowhereSaid = stringResource(R.string.move_nowhere)

    /** The dragged card followed by the finger, trading places as it passes a neighbour's middle. */
    fun follow(amount: Float) {
        dragBy += amount
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo
        val me = info.firstOrNull { it.key == dragged } ?: return
        // A trade shows on the next frame; until then the layout is the old one.
        if (me.index != (dragOrder ?: return).indexOfFirst { noteOf(it)?.id == dragged }) return
        // Shown: the card kept under the finger by its slot's real move -- a
        // neighbour's height, or none when it led the screen. Counted as the
        // neighbour's, a tall card drifted off the finger (round 17).
        slotWas?.let { dragBy += it - me.offset }
        slotWas = null
        val centre = me.offset + me.size / 2 + dragBy
        val leads = listState.firstVisibleItemIndex == me.index
        if (!leads) ledOnce = false
        fun staysInSight(other: LazyListItemInfo, step: Int) = Showing.tradeStaysInSight(
            step, me.offset, me.size, other.offset, other.size, leads, layout.viewportStartOffset, layout.viewportEndOffset,
            ledOnce)
        val below = info.firstOrNull { it.index == me.index + 1 }
        val above = info.firstOrNull { it.index == me.index - 1 }
        val traded = when {
            below != null && centre > below.offset + below.size / 2 -> staysInSight(below, 1) && trade(1, leads)
            above != null && centre < above.offset + above.size / 2 -> staysInSight(above, -1) && trade(-1, leads)
            else -> false
        }
        if (traded) slotWas = me.offset.toFloat()
    }

    /** Whether the dragged card has a neighbour [toward] it can trade with, in its block. */
    fun canPass(toward: Int): Boolean {
        val now = dragOrder ?: return false
        val at = now.indexOfFirst { noteOf(it)?.id == dragged }
        val me = noteOf(now.getOrNull(at)) ?: return false
        val other = noteOf(now.getOrNull(at + toward)) ?: return false
        return Organizing.sameBlock(me, other, groupedNow)
    }

    // Near the list's top or bottom edge, a dragged card scrolls the list --
    // and goes on trading places as the list passes under it, though the
    // finger is still: otherwise its slot stayed behind, scrolled out of
    // sight, and the card was no longer drawn under the finger at all.
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val fastest = with(LocalDensity.current) { 20.dp.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    LaunchedEffect(dragged) {
        // The way the list is scrolling at an edge, the zone the card was in,
        // and since when its scroll there began.
        var way = 0
        var sideWas = 0
        var since = 0L
        while (dragged != null) {
            val info = listState.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == dragged }
            var step = 0f
            if (me != null && heading != 0) {
                // A trade's correction first, once the layout shows it, though the
                // finger stopped (round 18) -- before the edge is judged, which saw
                // the card a neighbour off and scrolled a step by itself (round 19).
                if (slotWas != null) follow(0f)
                // Toward the edge the finger is going, never the other way, slowly
                // at first (Showing.edgeStep), and latched while it heads on.
                val start = info.viewportStartOffset
                val end = info.viewportEndOffset
                val zone = Showing.edgeZone(start, end, edge)
                val top = me.offset + dragBy
                val pulled = if (Showing.edgeLatched(way, heading)) heading * (slop + 1f) else pulledNow
                var toward = Showing.edgeScroll(heading, pulled, slop, top, me.size, start, end, zone)
                // Only toward a card it can trade with: past its block's end the
                // list scrolled on until the card's slot went (round 17).
                if (toward != 0 && !canPass(toward)) toward = 0
                val side = when { top < start + zone -> -1; top + me.size > end - zone -> 1; else -> 0 }
                val clock = android.os.SystemClock.uptimeMillis()
                since = Showing.edgeSince(since, sideWas, side, toward, clock)
                sideWas = side
                way = toward
                val depth = if (toward < 0) start + zone - top else top + me.size - (end - zone)
                step = Showing.edgeStep(toward, depth, zone, if (since == 0L) 0L else clock - since, fastest)
            } else {
                way = 0
            }
            // Not while a trade's hold waits for its order to be composed: a
            // scroll now would be undone by it, the card left off the finger.
            if (step != 0f && hold.index < 0) {
                val scrolled = listState.scrollBy(step)
                dragBy += scrolled
                slotWas = slotWas?.minus(scrolled)
                follow(0f)
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
                        // Pinned, the first goes to the top of the list or of its group: shown there.
                        IconButton(onClick = {
                                       if (allPinned) holdByIndex()
                                       state.setPinned(selected, !allPinned)
                                       if (!allPinned) revealLater(selected)
                                       selected = emptySet()
                                   },
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
                                onClick = {
                                    moreMenu = false
                                    if (allPinned) holdByIndex()
                                    state.setPinned(selected, !allPinned)
                                    if (!allPinned) revealLater(selected)
                                    selected = emptySet()
                                },
                            )
                            if (selected.size == 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = {
                                        moreMenu = false
                                        // The copy, shown where the sort puts it.
                                        revealLater(setOfNotNull(state.duplicate(selected.single())))
                                        selected = emptySet()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_code)) },
                                onClick = {
                                    moreMenu = false
                                    val chosen = selected
                                    state.scope.launch { state.shareCode(chosen)?.let { sendText(state, it) } }
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
                                trailingIcon = { Current(option == order) },
                                modifier = Modifier.semantics { this.selected = option == order },
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
                                trailingIcon = { Current(option == show) },
                                modifier = Modifier.semantics { this.selected = option == show },
                            )
                        }
                        HorizontalDivider()
                        for ((name, colour) in load.categories) {
                            DropdownMenuItem(
                                leadingIcon = { Dot(colour) },
                                text = { Text(name, fontWeight = if (name == show) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { showMenu = false; state.putSetting(Settings.NOTE_FILTER, name) },
                                trailingIcon = { Current(name == show) },
                                modifier = Modifier.semantics { this.selected = name == show },
                            )
                        }
                    }
                }
            }

            // The day's reminder, the desktop's bar in its accent: once a
            // day, when notes fall on it. A tap opens the day on the calendar.
            state.reminderDue?.let { due ->
                val day = Recurrence.parseDate(state.today)?.let { Recurrence.showDay(it, year = false) } ?: state.today
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
                    // they ask the reader to do different things. And it
                    // names what is hiding them: with Show picked once and
                    // forgotten, "your search" blamed a search nobody had typed.
                    val hides = hiding(null).ifEmpty { "the list's choices" }
                    Text(stringResource(R.string.no_match, hides), color = palette.subtext, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.show_all_notes),
                        color = palette.accentText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp).clickable { showAll(null) },
                    )
                    // The list does not read a day's own words; the History
                    // does. When a day of a repeating note holds them, said,
                    // with the way there (the owner's choice, wave 2).
                    // A day's own words alone (autoExpand): a dated note Show
                    // hid is in the History too, and the sentence was false.
                    val words = PyText.strip(query)
                    val inADay = remember(state.revision, words) {
                        words.isNotEmpty() && Recurrence.history(state.rawNotes(), words)
                            .any { it is Recurrence.HistoryEntry.Series && it.autoExpand }
                    }
                    if (inADay) {
                        Text(stringResource(R.string.no_match_in_a_day), color = palette.subtext, fontSize = 14.sp,
                             modifier = Modifier.padding(top = 18.dp))
                        Text(
                            stringResource(R.string.search_the_history),
                            color = palette.accentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp).clickable { state.searchHistoryFor(words) },
                        )
                    }
                }
                else -> LazyColumn(state = listState,
                                   contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)) {
                    items(rows, key = Showing::key) { row ->
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
                                        // Arranging without a drag, for a screen
                                        // reader and Switch Access: the card's
                                        // own actions, each where it has a place.
                                        actions = if (!arranging) emptyList() else listOfNotNull(
                                            upSaid.takeIf { Organizing.stepPlace(laidOut.mapNotNull { noteOf(it) }, note.id, -1, grouped) != null }
                                                ?.let { CustomAccessibilityAction(it) { stepNote(note.id, -1); true } },
                                            downSaid.takeIf { Organizing.stepPlace(laidOut.mapNotNull { noteOf(it) }, note.id, 1, grouped) != null }
                                                ?.let { CustomAccessibilityAction(it) { stepNote(note.id, 1); true } },
                                        ),
                                        handle = if (!arranging) null else { { Box {
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
                                                            // Read before the press is ours: a tap that
                                                            // stops a fling only stops it (round 22).
                                                            val stopping = listState.isScrollInProgress
                                                            val down = awaitFirstDown(requireUnconsumed = false)
                                                            down.consume()
                                                            // A second handle while a card is lifted is no
                                                            // new drag: it took the first one over (round 21).
                                                            if (dragged != null) return@awaitEachGesture
                                                            dragged = note.id
                                                            dragBy = 0f
                                                            dragOrder = laidOutNow
                                                            slotWas = null
                                                            heading = 0
                                                            pulledNow = 0f
                                                            ledOnce = false
                                                            var travelled = false
                                                            // How far the finger has gone from the press,
                                                            // and its furthest point the way it is heading.
                                                            var pulled = 0f
                                                            var far = 0f
                                                            var cancelled = false
                                                            try {
                                                                while (true) {
                                                                    val change = awaitPointerEvent().changes
                                                                        .firstOrNull { it.id == down.id } ?: break
                                                                    // A lift the system made up, already taken
                                                                    // (a Back swipe, a call): no drop, no menu
                                                                    // (rounds 17 and 25).
                                                                    if (!change.pressed) {
                                                                        cancelled = change.isConsumed
                                                                        break
                                                                    }
                                                                    val moved = change.positionChange()
                                                                    if (moved.y != 0f) {
                                                                        change.consume()
                                                                        pulled += moved.y
                                                                        // A drag only past the touch slop, and turned
                                                                        // only back past it (Showing.dragHeading).
                                                                        val (way, furthest) = Showing.dragHeading(heading, far, pulled, slop)
                                                                        heading = way
                                                                        far = furthest
                                                                        pulledNow = pulled
                                                                        travelled = travelled || way != 0
                                                                        follow(moved.y)
                                                                    }
                                                                }
                                                                if (cancelled) {
                                                                    dragged = null
                                                                    dragBy = 0f
                                                                    dragOrder = null
                                                                    slotWas = null
                                                                    heading = 0
                                                                    pulledNow = 0f
                                                                    ledOnce = false
                                                                    hold.index = -1
                                                                    return@awaitEachGesture
                                                                }
                                                                drop()
                                                                // Tapped, not dragged: up and down,
                                                                // for a hand that cannot drag -- when
                                                                // there is a way to go: the only note in
                                                                // its block opened an empty menu.
                                                                if (!travelled && !stopping) {
                                                                    val shown = laidOutNow.mapNotNull { noteOf(it) }
                                                                    if (listOf(-1, 1).any { Organizing.stepPlace(shown, note.id, it, groupedNow) != null }) {
                                                                        stepMenu = note.id
                                                                    } else {
                                                                        state.say(nowhereSaid)
                                                                    }
                                                                }
                                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                                // Interrupted, nothing moves.
                                                                dragged = null
                                                                dragBy = 0f
                                                                dragOrder = null
                                                                slotWas = null
                                                                heading = 0
                                                                pulledNow = 0f
                                                                ledOnce = false
                                                                hold.index = -1
                                                                throw e
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                            )
                                            // Closed when its card leaves the list: turned on
                                            // its side, it came back open by itself (round 22).
                                            DisposableEffect(note.id) {
                                                onDispose { if (stepMenu == note.id) stepMenu = null }
                                            }
                                            DropdownMenu(expanded = stepMenu == note.id, onDismissRequest = { stepMenu = null }) {
                                                // Only the ways it can go, in its block.
                                                val shownNotes = laidOut.mapNotNull { noteOf(it) }
                                                for ((step, said) in listOf(-1 to upSaid, 1 to downSaid)) {
                                                    if (Organizing.stepPlace(shownNotes, note.id, step, grouped) == null) continue
                                                    DropdownMenuItem(text = { Text(said) },
                                                                     onClick = { stepMenu = null; stepNote(note.id, step) })
                                                }
                                            }
                                        } } },
                                    )
                                }
                            }
                        }
                    }
                    // Its foot as tall as the keyboard where nothing above has
                    // made room for it: up, the keyboard covered the last
                    // results; counted twice on a wide window, it emptied the pane.
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime)) }
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
                revealLater(selected)
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
    // Folded or open, said as FoldLabel says it; the arrow was all there was.
    val said = stringResource(if (row.collapsed) R.string.section_folded else R.string.section_open)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).semantics { stateDescription = said }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (row.collapsed) "▸" else "▾", color = palette.subtext, fontSize = 13.sp,
             modifier = Modifier.clearAndSetSemantics { })
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
            // Its words, without the arrow's name: "down-pointing small
            // triangle" was read after every choice.
            .semantics { contentDescription = label; role = Role.DropdownList }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** The choice in force, in a menu of choices: a mark beside the bold, which the eye alone could miss. */
@Composable
private fun Current(on: Boolean) {
    if (on) Text("✓", color = LocalPalette.current.accentText, fontSize = 15.sp,
                 modifier = Modifier.clearAndSetSemantics { })
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

/** A card to bring into sight once the list has it: the cards in sight, whether the list was at its top, and the item it keeps, when it was asked for. */
private data class Reveal(val ids: Set<Int>, val seen: List<Any>, val atTop: Boolean, val listed: List<Showing.Row>,
                          val anchor: Any?, val lead: Int)

/** A card stepped one place ([step] -1 up, +1 down), how tall it was, and the card it passed, before the step. */
private data class Stepped(val id: Int, val step: Int, val size: Int, val passedSize: Int)

/** Where a drag's last trade holds the list -- [index] -1 when nothing waits -- until its order is composed. */
private class Hold {
    var index = -1
    var offset = 0
}
