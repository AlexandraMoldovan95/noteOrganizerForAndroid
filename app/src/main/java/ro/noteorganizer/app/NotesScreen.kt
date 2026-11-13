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
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.Job
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

/** The list's selection, kept while the list is off the screen ([Note.pickedKept]). */
internal val PickedSaver = androidx.compose.runtime.saveable.Saver<Map<Int, String>, ArrayList<String>>(
    save = { Note.pickedKept(it) }, restore = { Note.pickedBack(it) })

/**
 * The list of notes: search, sort and show at the top, the desktop's cards,
 * the + for a new note -- in the rail when [plusInRail], where it covers no
 * card. Touch and hold a card to select it; while anything is selected, a
 * tap selects too, and the bar at the top acts on them all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(state: AppState, onImportCode: () -> Unit, plusInRail: Boolean = false) {
    val palette = LocalPalette.current
    val load = state.load
    var query by rememberSaveable(stateSaver = SearchSaver) { mutableStateOf("") }
    // Remembered across starts by the desktop's own keys and values, so a
    // choice made on one device reads back on the other.
    val order = SortOrder.fromLabel(state.settings.opt(Settings.NOTE_SORT) as? String)
    val show = Showing.valid(state.settings.opt(Settings.NOTE_FILTER) as? String, load.categories.keys)
    val grouped = state.settings.opt(Settings.GROUP_BY_CATEGORY) == true
    val collapsed = Settings.collapsedGroups(state.settings)
    // Kept by the note, not by its id (a freed id taken by a note arriving
    // went ticked into the selection's Trash), and while the list is off the
    // screen (a big phone turned upright with a note open beside it).
    var picked by rememberSaveable(stateSaver = PickedSaver) { mutableStateOf(mapOf<Int, String>()) }
    val byId = remember(state.revision, load) { load.notes.associateBy { it.id } }
    val selected = Note.stillPicked(picked, byId)
    fun select(ids: Set<Int>) { picked = Note.picked(ids, byId) }
    var sortMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    DisposableEffect(selecting) {
        state.listSelecting = selecting
        onDispose { state.listSelecting = false }
    }

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
    BackHandler(enabled = selecting) { select(emptySet()) }

    // Custom order: a card's handle drags it, trading places with neighbours as
    // it passes, so its slot follows the finger (left at its old slot, a long
    // scroll dropped it and its drag); the one move is written when let go.
    val listState = rememberLazyListState()
    // A new search starts its results at the top, as on the desktop (the
    // owner's call); back from a note, the list keeps its place. Not handed to
    // Android: a second copy of the words doubled what the app's stop carried.
    var listedFor by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != listedFor) {
            listedFor = query
            listState.scrollToItem(0)
        }
    }

    /**
     * In words, what keeps [note] off the list -- or, with none, what keeps
     * notes off it; "" for nothing. Asked of the settings as they are now:
     * read off the last drawing, a Show all just taken was not in it, and the
     * next word was said hidden over a list showing all.
     */
    fun hiding(note: Note?): String {
        val showNow = Showing.valid(state.settings.opt(Settings.NOTE_FILTER) as? String, state.load.categories.keys)
        val groupedNow = state.settings.opt(Settings.GROUP_BY_CATEGORY) == true
        val collapsedNow = Settings.collapsedGroups(state.settings)
        return listOfNotNull(
            "your search".takeIf { PyText.strip(query).isNotEmpty() && (note == null || !note.matches(query)) },
            "Show: $showNow".takeIf { showNow != Showing.ALL && (note == null || !Showing.passes(note, showNow)) },
            "its folded group".takeIf {
                note != null && groupedNow && note.category.ifEmpty { Themes.PROTECTED_CATEGORY } in collapsedNow
            },
        ).joinToString(" and ")
    }

    // The row Show all scrolls to once the list holds it: [note]'s card, or the
    // top (-1). Left alone, the notes Show had hidden came in above the card in
    // sight, out of view, the one Show all was pressed for among them.
    var reveal by remember { mutableStateOf<Int?>(null) }
    // Another sort, Show or grouping starts at the top, as a new search does:
    // kept on its first card, Title A–Z seemed to begin there, the notes
    // before it above the screen. Before the effect below.
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
        // Waited for while the list changes to show it; let go once the note
        // is gone, or the next note given its id was scrolled to.
        if (at < 0 && state.load.notes.none { it.id == id }) reveal = null
        if (at < 0) return@LaunchedEffect
        reveal = null
        listState.scrollToItem(at)
    }
    // A card an action moved (pinned, copied, refiled) brought into sight where
    // the list now has it, judged from the cards in sight before the action: the
    // layout read after it is still the old one. Let go when it is not listed.
    var revealCard by remember { mutableStateOf<Reveal?>(null) }
    // All the notes an action moved, the first taken in the list's new order
    // (Showing.revealAt). [arrived]: notes asked over once back (the Trash's
    // Undo), which moved by arriving, not by place.
    fun revealLater(ids: Set<Int>, arrived: Boolean = false) {
        if (ids.isEmpty()) return
        // In sight: half of it at least. A sliver under the list's top padding
        // counted, and a card put back above the screen read as shown.
        val info = listState.layoutInfo
        val inSight = info.visibleItemsInfo.filter {
            minOf(it.offset + it.size, info.viewportEndOffset) - maxOf(it.offset, 0) >= it.size / 2
        }
        // The item the list keeps, a sliver too: moved, the list goes with it
        // only so far, so the card is then always brought into sight.
        val firstIndex = listState.firstVisibleItemIndex
        val anchor = info.visibleItemsInfo.firstOrNull { it.index == firstIndex }?.key
        val lead = inSight.firstOrNull()?.let { (it.index - firstIndex).coerceAtLeast(0) } ?: 0
        revealCard = Reveal(ids, inSight.map { it.key }, firstIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
                            laidOut, anchor, lead, arrived)
    }
    LaunchedEffect(revealCard, laidOut) {
        val reveal = revealCard ?: return@LaunchedEffect
        val (ids, seen, atTop, listed, anchor, lead) = reveal
        val listedNow = laidOut.any { (it as? Showing.Row.Item)?.note?.id in ids }
        // Not in the list it was asked over: waited for once, as the Trash's
        // Undo puts notes back after the list was drawn; let go after that.
        if (!listedNow && laidOut == listed) return@LaunchedEffect
        // Only the cards the change moved (Showing.movedIds), or arrived.
        val moved = if (reveal.arrived) ids else Showing.movedIds(listed, laidOut, ids)
        if (moved.isNotEmpty()) Showing.revealAt(laidOut, moved, seen, atTop, anchor, lead)?.let { listState.scrollToItem(it) }
        revealCard = null
    }
    // Notes the Trash's Undo put back, brought into sight the same way, and
    // said where the list hides them, as an import's are: unsaid, the reveal
    // waiting for them took over the next change to the list.
    LaunchedEffect(state.revealAfterUndo) {
        val put = state.revealAfterUndo ?: return@LaunchedEffect
        state.revealAfterUndo = null
        val ids = put.ids
        val hidden = state.load.notes.filter { it.id in ids && hiding(it).isNotEmpty() }.mapTo(HashSet()) { it.id }
        // Not of a note the Undo gave back to a word still waiting, as
        // the Undo decided it: read here, on two panes the word was taken already.
        val unsaid = put.unsaid.sorted()
        if (unsaid.any { it in hidden }) state.wordOn(unsaid, "put back", "Put back")
        revealLater(ids - hidden, arrived = true)
    }
    /**
     * The list held where it is by place, for a change that moves the card
     * at the top of the screen without meaning to take the list along:
     * kept by its first card, the list followed a card unpinned there and the
     * notes still pinned went above it; a card stepped or
     * dragged past the top card went above the screen.
     */
    fun holdByIndex() = listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    /** The list with nothing hiding: the search emptied, Show at All, [note]'s group unfolded, its card in sight. */
    fun showAll(note: Note?) {
        // Asked over a note gone since -- one kept aside, taken back: the
        // search and Show changed for nothing.
        if (note != null && state.rawNote(note.id) == null) {
            state.say("That note is no longer here")
            return
        }
        query = ""
        listedFor = ""
        reveal = note?.id ?: -1
        // The settings as they are now, as [hiding] reads them: taken from a
        // bar offered earlier, the folds of then were written back.
        if (state.settings.opt(Settings.NOTE_FILTER) != Showing.ALL) state.putSetting(Settings.NOTE_FILTER, Showing.ALL)
        val group = note?.category?.ifEmpty { Themes.PROTECTED_CATEGORY }
        val collapsedNow = Settings.collapsedGroups(state.settings)
        if (group != null && group in collapsedNow) {
            state.putSetting(Settings.COLLAPSED_GROUPS, org.json.JSONArray((collapsedNow - group).sorted()))
        }
    }

    // The list's own bars live while it does, and each is asked again as the
    // word it came of: its Show all acts on this list, and from a list gone
    // it did nothing at all.
    val listBars = remember { ArrayList<Pair<Job, () -> Unit>>() }
    DisposableEffect(Unit) {
        onDispose {
            for ((bar, again) in listBars) if (!bar.isCompleted) { bar.cancel(); again() }
            listBars.clear()
        }
    }
    // A new note the list cannot show (Show or the search hides it) is said by
    // name once closed, with the way back: it vanished from the list as it was
    // written. The desktop says it as the note is made.
    val madeNew = state.newNoteMade
    LaunchedEffect(madeNew, state.openId) {
        val id = madeNew ?: return@LaunchedEffect
        if (state.openId == id) return@LaunchedEffect
        state.newNoteMade = null
        if (state.load.notes.none { it.id == id }) return@LaunchedEffect
        state.waitWord(listOf(id), AppState.SAVED_IN, named = true)
    }
    // Notes restored, imported or made where the list does not look, said once
    // it is on screen: one bar at a time, worded when its turn comes, never
    // merged (ISSUES_LOG, "The list's words, merged and unmerged").
    val hidingNow by rememberUpdatedState<(Note) -> String> { note -> hiding(note) }
    val showAllNow by rememberUpdatedState<(Note) -> Unit> { note -> showAll(note) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { state.arrivals.isNotEmpty() && state.snackbar.currentSnackbarData == null }.first { it }
            val word = state.takeWord() ?: continue
            val hidden = state.load.notes.filter { it.id in word.ids && hidingNow(it).isNotEmpty() }
            val first = hidden.firstOrNull() ?: continue
            val said = state.wordText(word) ?: continue
            // Counted by the word: one about several, trimmed to one by a delete,
            // read "hidden by" as though all were.
            val tail = if (word.named) "hidden by ${hidingNow(first)}"
                       else "${hidden.size} of them hidden by ${hidingNow(first)}"
            val again = { state.waitWord(word.ids, word.said, word.named) }
            // The first of them still here: the first alone, gone, refused them all.
            val bar = state.offerAbout(hidden.map { it.id }, "$said, $tail", "Show all", again) {
                showAllNow(hidden.firstOrNull { state.rawNote(it.id) != null } ?: first)
            }
            listBars += bar to again
            bar.join()
        }
    }
    val arranging = order == SortOrder.CUSTOM && !selecting
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragBy by remember { mutableFloatStateOf(0f) }
    var dragOrder by remember { mutableStateOf<List<Showing.Row>?>(null) }
    // Where the dragged card's slot was before its last trade, in the
    // layout's terms, until a layout shows the trade.
    var slotWas by remember { mutableStateOf<Float?>(null) }
    // Which way the finger last moved, 0 before it has: a press held still at
    // the top card scrolled the list, and the top card dragged
    // down scrolled it up, against the finger.
    var heading by remember { mutableIntStateOf(0) }
    // How far the finger is from the press: the edge scrolls only past it
    // (Showing.edgeScroll).
    var pulledNow by remember { mutableFloatStateOf(0f) }
    // A trade's hold, asked again once the new order is composed: spent by a
    // finger's event measuring the old order, the list followed its top card,
    // and a card carried to the top went out of sight with its drag.
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
    // When the list last stopped scrolling, on the clock a press is read on:
    // a tap that stopped a fling is told by it (the handle's gesture).
    var scrollStoppedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect {
            if (!it) scrollStoppedAt = android.os.SystemClock.uptimeMillis()
        }
    }
    // The space under a card, no part of a stepped pair (Showing.afterStep),
    // and the view a step is said through.
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val view = LocalView.current
    val rows = dragOrder ?: laidOut
    // As they are now, for a handle's gesture, which runs on as it was first
    // touched: read as they were then, a card's second drag started from the
    // order before its first, and wrote a place counted from it.
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
        // the viewport, and cancelled its drag.
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
    // sight, or was left a strip.
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
        // the list as if it had been made.
        if (!state.moveNote(id, anchor, before = before, grouped = groupedNow)) return
        // Up from the top of the screen, the card keeps its place there, its
        // neighbour under it; held at the index, it went above.
        if (step < 0 && at == first) listState.requestScrollToItem(at - 1, offset)
        else listState.requestScrollToItem(first, offset)
        stepped = Stepped(id, step, size, passedSize)
        // Said to a screen reader as it lands: silent, a step read as not
        // taken, and a second one moved the note on.
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
        // neighbour's, a tall card drifted off the finger.
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

    // Near the list's edge, a dragged card scrolls the list and goes on trading
    // places as it passes, the finger still: otherwise its slot stayed behind,
    // out of sight, and the card was no longer drawn under the finger.
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
                // finger stopped -- before the edge is judged, which saw
                // the card a neighbour off and scrolled a step by itself.
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
                // list scrolled on until the card's slot went.
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
                        IconButton(onClick = { select(emptySet()) }, modifier = Modifier.said(R.string.clear_selection_said)) {
                            Text("✕", fontSize = 18.sp)
                        }
                    },
                    actions = {
                        // Pinned, the first goes to the top of the list or of its group: shown there.
                        IconButton(onClick = {
                                       if (allPinned) holdByIndex()
                                       state.setPinned(selected, !allPinned)
                                       if (!allPinned) revealLater(selected)
                                       select(emptySet())
                                   },
                                   modifier = Modifier.said(if (allPinned) R.string.unpin else R.string.pin)) {
                            Text("📌", fontSize = 18.sp)
                        }
                        IconButton(onClick = { choosingCategory = true }, modifier = Modifier.said(R.string.move_to_category_said)) {
                            Text("🗂", fontSize = 18.sp)
                        }
                        IconButton(onClick = { state.trash(selected); select(emptySet()) },
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
                                    select(emptySet())
                                },
                            )
                            if (selected.size == 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = {
                                        moreMenu = false
                                        // The copy, shown where the sort puts it.
                                        revealLater(setOfNotNull(state.duplicate(selected.single())))
                                        select(emptySet())
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
                                    select(if (selected.containsAll(everything)) emptySet() else everything)
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
                    // "Nothing matches" is not "no notes", as on the desktop, and
                    // it names what hides them: with Show picked once and forgotten,
                    // "your search" blamed a search nobody had typed.
                    val hides = hiding(null).ifEmpty { "the list's choices" }
                    Text(stringResource(R.string.no_match, hides), color = palette.subtext, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.show_all_notes),
                        color = palette.accentText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp).clickable { showAll(null) },
                    )
                    // The History reads a day's own words, the list does not: a day
                    // holding them is said, with the way there (the owner's choice).
                    // Its own words alone (autoExpand), not a dated note Show hid.
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
                                   contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp,
                                                                  bottom = if (plusInRail) 16.dp else 96.dp)) {
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
                                            if (selecting) select(selected.toggle(note.id)) else state.openNote(note.id)
                                        },
                                        onLongClick = { select(selected.toggle(note.id)) },
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
                                                        // Grabbed at the touch: a hand pausing
                                                        // before it moved gave the card a long
                                                        // press, and selection took the drag.
                                                        awaitEachGesture {
                                                            val down = awaitFirstDown(requireUnconsumed = false)
                                                            // A tap that stops a fling only stops it, read
                                                            // at the press: read before awaiting it,
                                                            // it was the last lift's.
                                                            val stopping = listState.isScrollInProgress ||
                                                                down.uptimeMillis - scrollStoppedAt < 150
                                                            down.consume()
                                                            // A second handle while a card is lifted is no
                                                            // new drag: it took the first one over.
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
                                                                    //.
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
                                                                // Tapped, not dragged: up and down, for a
                                                                // hand that cannot drag, when there is a
                                                                // way to go (else the menu was empty).
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
                                            // its side, it came back open by itself.
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
        if (!plusInRail && !selecting && !WindowInsets.isImeVisible) {
            NewNoteButton(state, Modifier.align(Alignment.BottomEnd).padding(20.dp))
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
                select(emptySet())
            },
            onDismiss = { choosingCategory = false },
        )
    }
}

/** The + for a new note: over the list's foot on a phone, at the head of the rail on a wide screen. */
@Composable
fun NewNoteButton(state: AppState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    FloatingActionButton(
        onClick = { state.newNote() },
        containerColor = palette.accent,
        contentColor = palette.onAccent,
        modifier = modifier,
    ) { Text("+", fontSize = 28.sp, modifier = Modifier.said(R.string.new_note_said)) }
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
                          val anchor: Any?, val lead: Int, val arrived: Boolean = false)

/** A card stepped one place ([step] -1 up, +1 down), how tall it was, and the card it passed, before the step. */
private data class Stepped(val id: Int, val step: Int, val size: Int, val passedSize: Int)

/** Where a drag's last trade holds the list -- [index] -1 when nothing waits -- until its order is composed. */
private class Hold {
    var index = -1
    var offset = 0
}
