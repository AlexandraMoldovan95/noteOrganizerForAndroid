package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.hexColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Everything the editor holds for one note, and the one way it is saved.
 *
 * A new note is 0 until something worth keeping is in it: an entirely
 * blank note is a mis-tap, not a thought, and nothing is written for it.
 */
class EditorModel(private val state: AppState, note: Note?, val day: String? = null, newDate: String = "") {
    var id by mutableIntStateOf(note?.id ?: 0)
    var title by mutableStateOf(note?.title ?: "")
    var body by mutableStateOf(note?.body ?: "")
    var spans by mutableStateOf(note?.formatting ?: emptyList())
    var comments by mutableStateOf(note?.comments ?: "")
    var category by mutableStateOf(note?.category ?: Themes.PROTECTED_CATEGORY)
    var color by mutableStateOf(note?.color ?: "")
    var date by mutableStateOf(note?.date ?: newDate)
    var pinned by mutableStateOf(note?.pinned ?: false)

    init {
        if (day != null && note != null) showDay(note.id, day)
    }

    /**
     * One day of the series, as that day shows it: its own edit over the
     * series'. A day's own text is plain -- the desktop keeps formatting
     * with the series -- so its spans are drawn only while the day still
     * shows the series' words.
     */
    private fun showDay(id: Int, day: String) {
        val raw = state.rawNote(id) ?: return
        val shown = Recurrence.effectiveNote(raw, day)
        val own = (raw.opt("overrides") as? org.json.JSONObject)?.optJSONObject(day)
        title = shown.opt("title") as? String ?: ""
        body = shown.opt("body") as? String ?: ""
        comments = shown.opt("comments") as? String ?: ""
        category = (shown.opt("category") as? String)?.ifEmpty { null } ?: Themes.PROTECTED_CATEGORY
        color = shown.opt("color") as? String ?: ""
        if (own?.has("body") == true) spans = emptyList()
    }

    /** Counts changes; the autosave runs a moment after the last one. */
    var edits by mutableIntStateOf(0)
    private var saved = 0
    var status by mutableStateOf<String?>(null)
    var problem by mutableStateOf<String?>(null)

    /** Which prose field has the cursor, and its value -- selection and all. */
    var focus by mutableStateOf<Int?>(null)
    var focused by mutableStateOf<TextFieldValue?>(null)

    /**
     * Where the cursor last was in the body, in code points -- kept when
     * the field lets go of it, as the desktop's insert mark is, so the
     * calculator writes where the person was writing.
     */
    var caret: Int? = null

    /** The end of the calculator's last insertion: the next goes after it. */
    private var calcMark: Int? = null

    /** The body has changed: the places remembered in it move with the text. */
    private fun follow(next: String) {
        caret = Calculator.follow(body, next, caret)
        calcMark = Calculator.follow(body, next, calcMark)
    }

    fun changed() {
        edits += 1
        status = null
    }

    val unsaved: Boolean get() = edits != saved

    /** Writes what is on screen. True when the file now holds it. */
    fun save(force: Boolean = false): Boolean {
        if (!force && edits == saved) return true
        val target = edits
        val load = state.load
        var follows = false
        val result = if (day != null && id != 0) {
            // A day of a series saves to that day alone, never to the series.
            Days.saveDay(load, id, day, title, body, comments, category, color) { follows = it == Days.DaySaved.FOLLOWS }
        } else if (id == 0) {
            if (!force && title.isBlank() && body.isBlank() && comments.isBlank()) {
                saved = target
                return true
            }
            Notebook.createNote(load, title, body, category, color, date, pinned, spans, comments = comments)
        } else {
            Notebook.updateNote(load, id, title, body, category, color, date, pinned,
                                formatting = spans, comments = comments)
        }
        state.refresh()
        return when (result) {
            is SaveResult.Ok -> {
                if (id == 0) id = result.id
                saved = target
                problem = null
                status = if (follows) "follows" else "saved"
                if (result.lostStyles > 0) {
                    state.say("${result.lostStyles} styles could not be kept: their text was deleted.")
                }
                true
            }
            is SaveResult.Failed -> {
                problem = result.reason
                false
            }
        }
    }

    /** The note on disk again, after something other than typing changed it. */
    fun reloadFrom(note: Note) {
        title = note.title
        body = note.body
        spans = note.formatting
        comments = note.comments
        category = note.category
        color = note.color
        date = note.date
        pinned = note.pinned
        if (day != null) showDay(note.id, day)
        focused = null
        // A body brought back whole: nothing remembered in the old one applies.
        caret = null
        calcMark = null
        saved = edits
    }

    /** One prose field's new text, the body rebuilt around it. */
    fun typeInto(segment: Editing.Segment, text: String) {
        val next = Editing.replaceProse(body, segment, text)
        if (next == body) return
        // A day's own text is plain: once typed in, it carries no styling.
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        changed()
    }

    /** One table changed through the table rules, the body rebuilt around it. */
    fun editTable(segment: Editing.Segment, change: (TableEdit) -> TableEdit) {
        val lines = body.split("\n").subList(segment.firstLine, segment.lastLine + 1)
        val next = replaceTable(body, segment.firstLine..segment.lastLine, change(TableEdit(lines)).lines())
        if (next == body) return
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        changed()
    }

    /**
     * A calculation into the note: the whole sum, "5+3=8", at the cursor the
     * first time and then each on its own line after the one before -- the
     * desktop's Insert into note.
     */
    fun insertCalculation(formula: String) {
        val placed = Calculator.place(body, formula, calcMark, caret)
        spans = if (day != null) emptyList() else remapSpans(body, placed.body, spans).spans
        body = placed.body
        calcMark = placed.mark
        caret = placed.mark
        // The field's own copy of its text is stale now: it takes the body's.
        focused = null
        changed()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(state: AppState, openId: Int) {
    val palette = LocalPalette.current
    val model = remember(openId, state.openDay) {
        EditorModel(state, state.note(openId), state.openDay, state.newNoteDate)
    }
    val calc = remember(model) { CalculatorState() }
    val find = remember(model) { FindState() }
    val findFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Worked out again as the note or the search changes; nothing at all
    // while the bar is shut.
    val matches = remember(model.body, state.findQuery, state.findMatchCase, find.open) {
        if (find.open) Finding.scan(model.body, state.findQuery, state.findMatchCase) else emptyList()
    }
    val bySegment = remember(matches) { matches.groupBy { it.segment } }
    val currentIndex = Finding.clamp(find.index, matches.size)
    val currentMatch = matches.getOrNull(currentIndex)

    fun closeFind() {
        find.open = false
        focusManager.clearFocus()
    }
    val note = state.note(model.id)
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var askRestore by remember { mutableStateOf(false) }
    var cell by remember { mutableStateOf<Pair<Editing.Segment, CellAt>?>(null) }
    // One day of a series: what it holds of its own, and where it came from.
    val day = model.day
    val raw = state.rawNote(model.id)
    val ownEdit = day != null && (raw?.opt("overrides") as? org.json.JSONObject)?.has(day) == true
    val cameFrom = if (day != null && raw != null) Recurrence.movedFrom(raw, day) else null
    var asking by remember { mutableStateOf<String?>(null) }

    fun leave() {
        model.save()
        state.closeNote()
    }

    BackHandler { leave() }
    // Back closes the find bar first, then the calculator, then the note.
    BackHandler(enabled = calc.open) { closeCalculator(calc, focusManager) }
    BackHandler(enabled = find.open) { closeFind() }
    // Saved a moment after the last change, as the desktop's autosave is,
    // and when the app goes to the background -- where most notes on a
    // phone are left, by Home or by switching apps rather than by Back.
    LaunchedEffect(model.edits) {
        if (model.unsaved) {
            delay(800)
            model.save()
        }
    }
    DisposableEffect(model) {
        state.flush = { model.save() }
        onDispose { state.flush = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Text("←", fontSize = 22.sp, color = palette.text) }
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    model.problem != null -> stringResource(R.string.not_saved)
                    model.status == "follows" -> stringResource(R.string.day_follows)
                    model.status == "saved" && day != null -> stringResource(R.string.day_saved)
                    model.status == "saved" -> stringResource(R.string.auto_saved)
                    else -> ""
                },
                color = if (model.problem != null) palette.dangerText else palette.subtext,
                fontSize = 12.sp,
            )
            IconButton(onClick = {
                if (model.save(force = model.id == 0)) state.shareCode(listOf(model.id))?.let { sendText(state, it) }
            }) { Text("🔗", fontSize = 18.sp) }
            IconButton(onClick = { details = true }) { Text("ⓘ", fontSize = 20.sp, color = palette.text) }
            Box {
                IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp, color = palette.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.find_in_note)) },
                        onClick = {
                            menu = false
                            // Words selected in the note are what is looked for.
                            Finding.prefill(model.focused?.let { it.text.substring(it.selection.min, it.selection.max) })
                                ?.let { state.findQuery = it }
                            // The last search, selected: typing replaces it.
                            find.field = TextFieldValue(state.findQuery, TextRange(0, state.findQuery.length))
                            find.index = 0
                            find.reveal += 1
                            find.open = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.calculator)) },
                        onClick = {
                            menu = false
                            // The note lets go of the keyboard, keeping where its
                            // cursor was: that is where Insert writes.
                            focusManager.clearFocus()
                            calc.open = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        onClick = {
                            menu = false
                            if (model.save(force = model.id == 0)) state.duplicate(model.id)
                        },
                    )
                    if (day != null) {
                        // A day of a series is deleted on its own, or the series
                        // is: the desktop's two answers, as two entries.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.day_delete), color = palette.dangerText) },
                            onClick = { menu = false; model.save(); asking = "day" },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.series_delete), color = palette.dangerText) },
                            onClick = { menu = false; model.save(); asking = "series" },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_trash), color = palette.dangerText) },
                            onClick = {
                                menu = false
                                if (model.id == 0 && model.title.isBlank() && model.body.isBlank()) {
                                    state.closeNote()
                                } else if (model.save(force = model.id == 0)) {
                                    state.trash(listOf(model.id))
                                }
                            },
                        )
                    }
                }
            }
        }

        if (find.open) {
            FindBar(
                find = find,
                counter = Finding.counter(currentIndex, matches.size, state.findQuery),
                missed = state.findQuery.isNotEmpty() && matches.isEmpty(),
                matchCase = state.findMatchCase,
                focus = findFocus,
                onQuery = { value ->
                    find.field = value
                    if (value.text != state.findQuery) {
                        // A new search: its first match, brought into sight.
                        state.findQuery = value.text
                        find.index = 0
                        find.reveal += 1
                    }
                },
                // The same search counted again, where the reader already is.
                onCase = { state.findMatchCase = !state.findMatchCase },
                onStep = { find.step(it, matches.size) },
                onClose = { closeFind() },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            model.problem?.let {
                Text(stringResource(R.string.not_saved_because, it), color = palette.dangerText, fontSize = 13.sp,
                     modifier = Modifier.padding(bottom = 10.dp))
            }
            if (day != null) {
                // Day mode says so, and names the way to the whole series --
                // a field test once could not find how to edit just one day,
                // nor how to leave it.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.day_banner, Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day),
                         color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.day_whole_series), color = palette.accentText, fontSize = 13.sp,
                             fontWeight = FontWeight.Medium,
                             modifier = Modifier.clickable { model.save(); state.openWholeSeries() })
                        if (ownEdit || cameFrom != null) {
                            Text(stringResource(R.string.day_reset), color = palette.accentText, fontSize = 13.sp,
                                 fontWeight = FontWeight.Medium, modifier = Modifier.clickable { model.save(); asking = "reset" })
                        }
                    }
                }
            }
            if (note?.hasPrevious == true) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .clickable { askRestore = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.restore_banner), color = palette.subtext, fontSize = 13.sp,
                         modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.restore_previous), color = palette.accentText,
                         fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            BasicTextField(
                value = model.title,
                onValueChange = {
                    // The desktop's ceiling: far more than a title needs.
                    model.title = it.take(200)
                    model.changed()
                },
                textStyle = TextStyle(color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) model.focus = null },
                decorationBox = { field ->
                    if (model.title.isEmpty()) {
                        Text(stringResource(R.string.title_hint), color = palette.subtext, fontSize = 22.sp,
                             fontWeight = FontWeight.Medium)
                    }
                    field()
                },
            )
            if (model.title.length >= 160) {
                Text("${model.title.length}/200", fontSize = 11.sp,
                     color = if (model.title.length >= 200) palette.dangerText else palette.subtext)
            }
            Text(
                // On one day of a series, the day being written -- not the
                // series' first one, which read as the wrong date here.
                text = listOfNotNull(model.category, Recurrence.showDate(day ?: model.date).ifBlank { null }).joinToString("  ·  "),
                color = palette.legibleOnCard(model.color.ifEmpty { state.load.categories[model.category] ?: palette.hex.getValue("accent") }),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp).clickable { details = true },
            )

            val segments = remember(model.body) { Editing.segments(model.body) }
            segments.forEachIndexed { index, segment ->
                val here = bySegment[index].orEmpty()
                if (segment.table) {
                    TableView(
                        table = remember(segment.text) { parseTable(segment.text.split("\n")) },
                        modifier = Modifier.padding(vertical = 8.dp),
                        editing = true,
                        onCellTap = { at -> cell = segment to at },
                        found = here.mapNotNull { (it as? Finding.InCell)?.let { m -> CellAt(m.row, m.column) } }.toSet(),
                        current = (currentMatch as? Finding.InCell)?.takeIf { it.segment == index }
                            ?.let { CellAt(it.row, it.column) },
                        reveal = find.reveal,
                    )
                } else {
                    ProseField(
                        model, index, segment, placeholder = segments.size == 1,
                        found = here.filterIsInstance<Finding.InText>(),
                        match = (currentMatch as? Finding.InText)?.takeIf { it.segment == index },
                        reveal = find.reveal,
                    )
                }
            }

            ChecklistSection(state, model)
            CommentsSection(model)
            if (model.id != 0) AttachmentsSection(state, model.id)
            Spacer(Modifier.height(48.dp))
        }

        // The calculator stands aside while the keyboard is up for the note --
        // the two together would leave no note to see -- and comes back when
        // it goes down. Typed into, its own display keeps it in place.
        if (calc.open && (!WindowInsets.isImeVisible || calc.displayFocused)) {
            CalculatorPanel(calc, onInsert = { model.insertCalculation(it) },
                            onClose = { closeCalculator(calc, focusManager) })
        } else if (model.focus != null) {
            if (day == null) FormatBar(model)
            else Text(stringResource(R.string.day_plain), color = palette.subtext, fontSize = 12.sp,
                      modifier = Modifier.fillMaxWidth().background(palette.card).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
    if (day != null && asking != null) {
        DayQuestions(state, model.id, day, cameFrom, ownEdit, asking) {
            asking = null
            // A reset changes what this day shows: shown again from the file.
            state.note(model.id)?.let { model.reloadFrom(it) }
        }
    }

    if (details) DetailsSheet(state, model, note, onDismiss = { details = false })
    cell?.let { (segment, at) ->
        CellSheet(
            table = parseTable(segment.text.split("\n")),
            at = at,
            onChange = { change ->
                model.editTable(segment, change)
                cell = null
            },
            onDismiss = { cell = null },
        )
    }
    if (askRestore && note != null) {
        val also = note.previousAlso
        Ask(
            title = stringResource(R.string.restore_title),
            text = if (also.isEmpty()) stringResource(R.string.restore_text_only)
                   else stringResource(R.string.restore_text_with, joinWithAnd(also)),
            confirm = stringResource(R.string.restore_confirm),
            onConfirm = {
                askRestore = false
                // Whatever waits for its autosave is saved first, so the
                // version being replaced is the one on screen.
                model.save()
                if (state.report(Notebook.restorePrevious(state.load, model.id))) {
                    state.note(model.id)?.let { model.reloadFrom(it) }
                    state.say("Restored the previous version")
                }
            },
            onDismiss = { askRestore = false },
        )
    }
}

private fun closeCalculator(calc: CalculatorState, focusManager: androidx.compose.ui.focus.FocusManager) {
    // The display's keyboard goes with it, if it was up.
    if (calc.displayFocused) focusManager.clearFocus()
    calc.displayFocused = false
    calc.open = false
}

/** "a", "a and b", "a, b and c" -- how the desktop's question lists things. */
private fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/**
 * One run of prose, typed into directly, its styling drawn as it is
 * typed. The cursor and the selection are kept in the model, so the format
 * bar knows what they cover.
 */
@Composable
private fun ProseField(
    model: EditorModel,
    index: Int,
    segment: Editing.Segment,
    placeholder: Boolean,
    found: List<Finding.InText> = emptyList(),
    match: Finding.InText? = null,
    reveal: Int = 0,
) {
    val palette = LocalPalette.current
    val local = Editing.localSpans(model.spans, segment)
    val current = model.focused
    val value = if (model.focus == index && current != null && current.text == segment.text) current
                else TextFieldValue(segment.text, TextRange(segment.text.length))
    // The find bar's current match, scrolled into sight when it asks: the
    // line it is on, not merely this field, which may be pages long.
    val requester = remember { BringIntoViewRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(reveal) {
        val at = match ?: return@LaunchedEffect
        val drawn = layout
        requester.bringIntoView(
            if (drawn != null && at.start < drawn.layoutInput.text.length) drawn.getBoundingBox(at.start) else null
        )
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            if (next.text != segment.text) model.typeInto(segment, next.text)
            model.focus = index
            model.focused = next
            model.caret = caretIn(segment, next)
        },
        visualTransformation = { text ->
            val drawn = renderBody(Note(body = text.text, formatting = local), palette.bg).text
            TransformedText(paintFound(drawn, found, match, palette), OffsetMapping.Identity)
        },
        onTextLayout = { layout = it },
        textStyle = TextStyle(color = palette.text, fontSize = 16.sp, lineHeight = 24.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (placeholder) 160.dp else 24.dp)
            .bringIntoViewRequester(requester)
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    model.focus = index
                    if (model.focused?.text != segment.text) model.focused = value
                    model.caret = caretIn(segment, value)
                } else if (model.focus == index) {
                    model.focus = null
                }
            },
        decorationBox = { field ->
            if (placeholder && segment.text.isEmpty()) {
                Text(stringResource(R.string.body_hint), color = palette.subtext, fontSize = 16.sp)
            }
            field()
        },
    )
}

/**
 * The cursor of a field as a place in the whole body, in code points: the
 * field's start plus what comes before the cursor in its own text.
 */
private fun caretIn(segment: Editing.Segment, value: TextFieldValue): Int {
    val end = value.selection.end.coerceIn(0, value.text.length)
    return segment.cpStart + value.text.codePointCount(0, end)
}

/**
 * Bold, italic, underline, strike, a text colour and a highlight, over
 * what is selected in the field that has the cursor -- sitting above the
 * keyboard, where the thumb already is and where Android's own selection
 * menu does not reach.
 */
@Composable
private fun FormatBar(model: EditorModel) {
    val palette = LocalPalette.current
    val segments = Editing.segments(model.body)
    val index = model.focus ?: return
    val segment = segments.getOrNull(index) ?: return
    val value = model.focused ?: return
    val hasSelection = value.selection.max > value.selection.min
    val (from, to) = Editing.bodyRange(segment, value.selection.min, value.selection.max)
    val active = stylesOver(model.spans, from, to)
    var palettePicker by remember { mutableStateOf("") }

    fun apply(style: String, colour: String = "") {
        if (!hasSelection) return
        model.spans = toggleStyle(model.spans, from, to, style, colour)
        model.changed()
    }

    Column(Modifier.fillMaxWidth().background(palette.sidebar)) {
        if (palettePicker.isNotEmpty() && hasSelection) {
            val style = if (palettePicker == "text") "text_color" else "highlight"
            val colours = if (palettePicker == "text") Themes.PRESET_NOTE_COLORS else Themes.HIGHLIGHT_COLOR_PALETTE
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "✕",
                    color = palette.subtext,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.inset)
                        .clickable {
                            model.spans = clearStyle(model.spans, from, to, style)
                            model.changed()
                            palettePicker = ""
                        }
                        .padding(top = 6.dp),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                for (colour in colours) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(hexColor(colour))
                            .clickable { apply(style, colour); palettePicker = "" },
                    )
                }
            }
        }
        if (!hasSelection) {
            Text(stringResource(R.string.select_to_format), color = palette.subtext, fontSize = 11.sp,
                 modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FormatKey("B", "bold" in active, hasSelection, weight = FontWeight.Bold) { apply("bold") }
            FormatKey("I", "italic" in active, hasSelection, italic = true) { apply("italic") }
            FormatKey("U", "underline" in active, hasSelection, decoration = TextDecoration.Underline) { apply("underline") }
            FormatKey("S", "strike" in active, hasSelection, decoration = TextDecoration.LineThrough) { apply("strike") }
            FormatKey("A", "text_color" in active, hasSelection, weight = FontWeight.Bold) {
                palettePicker = if (palettePicker == "text") "" else "text"
            }
            FormatKey("🖍", "highlight" in active, hasSelection) {
                palettePicker = if (palettePicker == "mark") "" else "mark"
            }
        }
    }
}

@Composable
private fun FormatKey(
    label: String,
    on: Boolean,
    enabled: Boolean,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    decoration: TextDecoration? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) palette.calendarHasNotes else palette.card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) palette.text else palette.subtext.copy(alpha = 0.5f),
            fontSize = 16.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration,
        )
    }
}

/**
 * The checklist, under the text. Ticking, adding, renaming and removing
 * each write at once, as the desktop's do; the handle ⠿ drags a row to
 * another place.
 */
@Composable
private fun ChecklistSection(state: AppState, model: EditorModel) {
    val palette = LocalPalette.current
    val note = state.note(model.id)
    val items = note?.checklist ?: emptyList()
    var adding by remember { mutableStateOf("") }
    var open by remember(model.id) { mutableStateOf(items.isNotEmpty()) }

    fun ensureSaved(): Int? {
        if (model.id == 0 && !model.save(force = true)) return null
        return model.id
    }

    if (!open && items.isEmpty()) {
        Text(
            stringResource(R.string.add_checklist),
            color = palette.accentText,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 18.dp).clickable { open = true }.padding(vertical = 6.dp),
        )
        return
    }
    SectionLabel(stringResource(R.string.checklist_label, items.count { it.done }, items.size))
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 44f * (androidx.compose.ui.platform.LocalDensity.current.density)
    items.forEachIndexed { index, item ->
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = if (dragging == index) offset else 0f }
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⠿",
                color = palette.subtext,
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(index, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = index; offset = 0f },
                            onDragEnd = {
                                val to = (index + Math.round(offset / rowHeight)).coerceIn(0, items.size - 1)
                                dragging = -1
                                offset = 0f
                                if (to != index) state.report(Notebook.moveChecklistItem(state.load, model.id, index, to))
                            },
                            onDragCancel = { dragging = -1; offset = 0f },
                            onDrag = { change, amount -> change.consume(); offset += amount.y },
                        )
                    },
            )
            Text(
                if (item.done) "☑" else "☐",
                color = if (item.done) palette.successText else palette.text,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { state.report(Notebook.setChecklistDone(state.load, model.id, index, !item.done)) }
                    .padding(end = 10.dp),
            )
            ChecklistText(item.text, item.done) { text ->
                state.report(Notebook.renameChecklistItem(state.load, model.id, index, text))
            }
            Text(
                "✕",
                color = palette.subtext,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { state.report(Notebook.removeChecklistItem(state.load, model.id, index)) }
                    .padding(8.dp),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        BasicTextField(
            value = adding,
            onValueChange = { adding = it },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { field ->
                if (adding.isEmpty()) Text(stringResource(R.string.checklist_new), color = palette.subtext, fontSize = 15.sp)
                field()
            },
        )
        Text(
            stringResource(R.string.add),
            color = if (adding.isBlank()) palette.subtext else palette.accentText,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(enabled = adding.isNotBlank()) {
                    val id = ensureSaved() ?: return@clickable
                    if (state.report(Notebook.addChecklistItem(state.load, id, adding.trim()))) adding = ""
                }
                .padding(12.dp),
        )
    }
}

/** A row's words, renamed when the field is left -- one write per edit, not per letter. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ChecklistText(
    text: String, done: Boolean, onRename: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var draft by remember(text) { mutableStateOf(text) }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        textStyle = TextStyle(
            color = if (done) palette.subtext else palette.text,
            fontSize = 15.sp,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier
            .weight(1f)
            .onFocusChanged { if (!it.isFocused && draft != text) onRename(draft) },
    )
}

/** Comments: plain text under the note, saved with it. */
@Composable
private fun CommentsSection(model: EditorModel) {
    val palette = LocalPalette.current
    var open by remember(model.id) { mutableStateOf(model.comments.isNotEmpty()) }
    if (!open) {
        Text(
            stringResource(R.string.add_comments),
            color = palette.accentText,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp).clickable { open = true }.padding(vertical = 6.dp),
        )
        return
    }
    SectionLabel(stringResource(R.string.comments_label))
    BasicTextField(
        value = model.comments,
        onValueChange = { model.comments = it; model.changed() },
        textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.card)
            .padding(12.dp),
    )
}

/** The note's attached files, each opened with the phone's own apps. */
@Composable
private fun AttachmentsSection(state: AppState, id: Int) {
    val palette = LocalPalette.current
    val files = state.attachmentsOf(id)
    if (files.isEmpty()) return
    SectionLabel(stringResource(R.string.files_label, files.size))
    for ((name, stored) in files) {
        Text(
            "📎  $name",
            color = palette.accentText,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.openFile?.invoke(File(state.attachments, stored), name) }
                .padding(vertical = 8.dp),
        )
    }
}

/**
 * Everything the desktop keeps around its editor: category, date, colour,
 * pin -- in a sheet that rises from the bottom, so the page stays the
 * note's. Plus how long the note is, and what can be done with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(state: AppState, model: EditorModel, note: Note?, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var picking by remember { mutableStateOf(false) }
    var dating by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    val onADay = model.day != null
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.note_details), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            DetailRow(stringResource(R.string.category)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { picking = true }) {
                    Dot(state.load.categories[model.category] ?: palette.hex.getValue("accent"))
                    Text("  ${model.category} ▾", color = palette.text, fontSize = 15.sp)
                }
            }
            DetailRow(stringResource(R.string.date)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Recurrence.showDate(model.date).ifBlank { stringResource(R.string.no_date) },
                        color = if (onADay) palette.subtext else palette.text,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(enabled = !onADay) { dating = true },
                    )
                    if (!onADay && model.date.isNotEmpty() && note?.isRecurring != true) {
                        Text("  ✕", color = palette.subtext, fontSize = 15.sp,
                             modifier = Modifier.clickable { model.date = ""; model.changed() })
                    }
                }
            }
            DetailRow(stringResource(R.string.repeat)) {
                Text(
                    note?.recurrenceLabel?.ifEmpty { null } ?: Recurrence.FREQ_LABELS.getValue(""),
                    color = if (onADay) palette.subtext else palette.text,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = !onADay) { repeating = true },
                )
            }
            if (onADay) {
                Text(stringResource(R.string.day_series_owned), color = palette.subtext, fontSize = 12.sp)
            } else if (note?.isRecurring == true) {
                Text(stringResource(R.string.repeats_note), color = palette.subtext, fontSize = 12.sp)
            }
            Text(stringResource(R.string.colour), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Swatches(Themes.PRESET_NOTE_COLORS, model.color, allowNone = true) { model.color = it; model.changed() }
            // The pin is the whole note's: a day has nowhere to keep one.
            if (!onADay) {
                DetailRow(stringResource(R.string.pin_to_top)) {
                    Switch(checked = model.pinned, onCheckedChange = { model.pinned = it; model.changed() })
                }
            }
            val words = Editing.wordCount(model.body)
            Text(
                listOf(pluralWords(words), Editing.readingTime(words)).filter { it.isNotEmpty() }.joinToString(" · "),
                color = palette.subtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (picking) {
        CategorySheet(state.load.categories, model.category, onPick = {
            model.category = it
            model.changed()
            picking = false
        }, onDismiss = { picking = false })
    }
    if (dating) DateDialog(model.date, onPick = { model.date = it; model.changed(); dating = false }) { dating = false }
    if (repeating) RepeatSheet(state, model) { repeating = false }
}

private fun pluralWords(n: Int) = if (n == 1) "1 word" else "$n words"

@Composable
internal fun DetailRow(label: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.subtext, fontSize = 14.sp, modifier = Modifier.weight(1f))
        content()
    }
}

/** Android's own calendar picker, kept to the desktop's stored form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val format = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val initial = if (Notebook.isDate(current)) format.parse(current.trim())?.time else null
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { onPick(format.format(Date(it))) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = picker) }
}

/** What a cell holds, spelled the way the file spells it. */
private fun cellTextAt(table: TableBlock, at: CellAt): String {
    val cell = table.rows.getOrNull(at.row)?.getOrNull(at.column) ?: return ""
    return when (cell.checked) {
        // A checkbox is drawn as a box but edited as what it really is, so
        // it can be turned back into words by changing the marker.
        true -> "[x]"
        false -> "[ ]"
        null -> cell.lines.joinToString("<br>")
    }
}

/**
 * One cell, opened: its words, a checkbox, and the changes that only mean
 * something while a cell is chosen -- a row or a column beside this one,
 * or this one's taken away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellSheet(table: TableBlock, at: CellAt, onChange: ((TableEdit) -> TableEdit) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var draft by remember(at) { mutableStateOf(cellTextAt(table, at)) }
    val row = table.fileRow(at.row)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.cell)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft = if (checkboxState(draft) == null) "[ ]" else "" }) {
                    Text(stringResource(R.string.cell_checkbox))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onChange { it.setCell(row, at.column, draft) } },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) { Text(stringResource(R.string.save)) }
            }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { onChange { it.insertRowAfter(row) } }) { Text(stringResource(R.string.row_below)) }
                TextButton(onClick = { onChange { it.insertColumnAfter(at.column) } }) { Text(stringResource(R.string.column_right)) }
                TextButton(onClick = { onChange { it.removeRow(row) } }) {
                    Text(stringResource(R.string.delete_row), color = palette.dangerText)
                }
                TextButton(onClick = { onChange { it.removeColumn(at.column) } }) {
                    Text(stringResource(R.string.delete_column), color = palette.dangerText)
                }
            }
        }
    }
}
