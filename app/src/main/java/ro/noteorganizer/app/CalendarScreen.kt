package ro.noteorganizer.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.hexColor
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July",
    "August", "September", "October", "November", "December")

/** "2026-10", "2026-10-17": figures as the file writes them, whatever the phone's language. */
private fun monthKey(month: YearMonth) = String.format(Locale.US, "%04d-%02d", month.year, month.monthValue)
private fun dayKey(month: YearMonth, day: Int) = String.format(Locale.US, "%04d-%02d-%02d", month.year, month.monthValue, day)

/**
 * The Calendar tab: the month, a dot for each colour of note on a day,
 * the day's own colour and markers -- and under it the open day's notes.
 * Or, swapped in by the button the desktop has, the History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(state: AppState) {
    val palette = LocalPalette.current
    val month = state.calendarMonth
    val kept = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().background(palette.bg)) {
        TopAppBar(
            title = {
                Text(
                    if (state.showHistory) stringResource(R.string.cal_history).removePrefix("📋 ")
                    else "${MONTHS[month.monthValue - 1]} ${month.year}",
                    modifier = Modifier.clickable(enabled = !state.showHistory,
                        onClickLabel = stringResource(R.string.cal_today)) { goToday(state) },
                )
            },
            actions = {
                if (!state.showHistory) {
                    val previous = stringResource(R.string.cal_previous)
                    val next = stringResource(R.string.cal_next)
                    IconButton(onClick = { state.calendarMonth = month.minusMonths(1) },
                               modifier = Modifier.semantics { contentDescription = previous }) {
                        Text("‹", fontSize = 24.sp, color = palette.text)
                    }
                    IconButton(onClick = { state.calendarMonth = month.plusMonths(1) },
                               modifier = Modifier.semantics { contentDescription = next }) {
                        Text("›", fontSize = 24.sp, color = palette.text)
                    }
                }
                TextButton(onClick = { state.showHistory = !state.showHistory }) {
                    Text(stringResource(if (state.showHistory) R.string.cal_back_to_month else R.string.cal_history),
                         color = palette.accentText, fontSize = 13.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.text),
        )
        // Each keeps its place while the other is shown, as Settings' list does.
        val showing = if (state.showHistory) "history" else "month"
        kept.SaveableStateProvider(showing) { if (state.showHistory) History(state) else Month(state) }
    }
}

/** To the day the grid marks as today ([AppState.today]), and its month. */
private fun goToday(state: AppState) {
    val today = Recurrence.parseDate(state.today) ?: LocalDate.now()
    state.calendarMonth = YearMonth.from(today)
    state.selectedDay = Recurrence.formatDate(today)
}

@Composable
private fun Month(state: AppState) {
    val palette = LocalPalette.current
    val month = state.calendarMonth
    val notes = remember(state.revision) { state.rawNotes() }
    val byDate = remember(state.revision, month) { Days.notesByDate(notes, month.year, month.monthValue) }
    val day = state.selectedDay
    val dayNotes = remember(state.revision, day) {
        if (day.startsWith(monthKey(month))) byDate[day].orEmpty()
        else Days.notesOn(notes, day)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        item { MonthGrid(state, byDate) }
        item { DayHeader(state, day) }
        if (dayNotes.isEmpty()) {
            item {
                Text(stringResource(R.string.cal_no_notes), color = palette.subtext, fontSize = 14.sp,
                     modifier = Modifier.padding(16.dp))
            }
        }
        items(dayNotes, key = { (it.opt("id") as? Number)?.toInt() ?: 0 }) { note -> DayRow(state, note, day) }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun MonthGrid(state: AppState, byDate: Map<String, List<JSONObject>>) {
    val palette = LocalPalette.current
    val month = state.calendarMonth
    val today = state.today
    val colours = state.dayColours
    val markers = state.dayMarkers
    var swipe by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(month) {
                // A sideways swipe turns the month, as a paper calendar's page.
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipe < -80f) state.calendarMonth = month.plusMonths(1)
                        else if (swipe > 80f) state.calendarMonth = month.minusMonths(1)
                        swipe = 0f
                    },
                    onDragCancel = { swipe = 0f },
                ) { _, dx -> swipe += dx }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            for (name in Recurrence.WEEKDAY_NAMES) {
                Text(name, color = palette.subtext, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                     modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (week in Days.weeks(month.year, month.monthValue)) {
            // As tall as its tallest day, every day of it: a cell grows with
            // larger letters, and its neighbours with it.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (number in week) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                        if (number > 0) {
                            val date = dayKey(month, number)
                            DayCell(state, date, number, byDate[date].orEmpty(), date == today,
                                    colours[date], markers[date].orEmpty())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(state: AppState, date: String, number: Int, notes: List<JSONObject>, isToday: Boolean,
                    paint: String?, markers: List<String>) {
    val palette = LocalPalette.current
    val selected = date == state.selectedDay
    var menu by remember { mutableStateOf(false) }
    var colouring by remember { mutableStateOf(false) }
    var marking by remember { mutableStateOf(false) }
    val accent = palette.hex.getValue("accent")
    val dots = notes.map { Days.colourOf(Recurrence.effectiveNote(it, date), state.load.categories, accent) }.distinct()
    val ink = if (paint != null) hexColor(Themes.lettersOn(paint)) else palette.text
    val count = if (notes.isNotEmpty()) pluralStringResource(R.plurals.note_count, notes.size, notes.size) else ""
    val label = listOf(Recurrence.showDay(LocalDate.parse(date), year = false), count, markers.joinToString(" "))
        .filter { it.isNotEmpty() }.joinToString(", ")
    Box(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxWidth()
                // At least 56, and more as the letters grow: held at 56, the
                // number at the phone's largest letters pushed a day's
                // markers half out of the cell and its dots out of sight
                // (seen on the emulator, 2026-10-27).
                .heightIn(min = 56.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(if (paint != null) hexColor(paint) else if (notes.isNotEmpty()) palette.calendarHasNotes else palette.card)
                .then(if (selected) Modifier.border(2.dp, palette.accent, RoundedCornerShape(8.dp)) else Modifier)
                .combinedClickable(onClick = { state.selectedDay = date }, onLongClick = { state.selectedDay = date; menu = true })
                .semantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Today wears an accent bar along its top, apart from the dots, as
            // on the desktop, so the two are never confused.
            Box(Modifier.fillMaxWidth().height(3.dp).background(if (isToday) palette.accent else androidx.compose.ui.graphics.Color.Transparent))
            Text(number.toString(), color = ink, fontSize = 14.sp,
                 fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
            // Under the number, not in the corner over it: a phone's cell is
            // narrow, and the corner's markers covered the day (the owner,
            // 2026-10-25). Sized as the marks they are, so larger letters
            // grow the number without pushing the dots out of the cell.
            if (markers.isNotEmpty()) {
                Text(markers.joinToString(""), fontSize = with(LocalDensity.current) { 11.dp.toSp() },
                     lineHeight = with(LocalDensity.current) { 13.dp.toSp() }, maxLines = 1)
            }
            if (dots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)) {
                    val shown = if (dots.size <= 3) dots else dots.take(2)
                    for (colour in shown) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(hexColor(colour)))
                    if (dots.size > 3) Text("+", color = ink, fontSize = 9.sp, lineHeight = 9.sp)
                }
            }
            // Room under the dots when the cell grows to hold them.
            Spacer(Modifier.height(4.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.cal_add_note)) },
                             onClick = { menu = false; state.newNote(date) })
            DropdownMenuItem(text = { Text(stringResource(R.string.cal_colour_day)) },
                             onClick = { menu = false; colouring = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.cal_mark_day)) },
                             onClick = { menu = false; marking = true })
            if (paint != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.cal_clear_colour)) },
                                 onClick = { menu = false; state.paintDay(date, "") })
            }
            if (markers.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.cal_clear_markers)) },
                                 onClick = { menu = false; state.markDay(date, emptyList()) })
            }
        }
    }
    if (colouring) ColourDaySheet(state, date, paint ?: "") { colouring = false }
    if (marking) MarkDaySheet(state, date, markers) { marking = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourDaySheet(state: AppState, date: String, current: String, onDone: () -> Unit) {
    val palette = LocalPalette.current
    AppSheet(onDismissRequest = onDone) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(Recurrence.showDay(LocalDate.parse(date)), color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                 modifier = Modifier.padding(bottom = 14.dp))
            Swatches(Themes.PALETTE_COLORS, current, allowNone = true) {
                state.paintDay(date, it)
                onDone()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MarkDaySheet(state: AppState, date: String, current: List<String>, onDone: () -> Unit) {
    val palette = LocalPalette.current
    // A working copy: nothing is written until Save, as on the desktop.
    val pending = remember { mutableStateListOf<String>().apply { addAll(current) } }
    val full = pending.size >= Days.MAX_MARKERS
    AppSheet(onDismissRequest = onDone) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.cal_markers_title), color = palette.text, fontSize = 17.sp,
                 fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (marker in Days.MARKERS) {
                    val on = marker in pending
                    val open = on || !full
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) palette.calendarHasNotes else palette.inset)
                            .then(if (on) Modifier.border(2.dp, palette.accent, RoundedCornerShape(10.dp)) else Modifier)
                            // Picked or not, said as a checkbox is: the fill
                            // and ring were all there was.
                            .toggleable(value = on, enabled = open, role = androidx.compose.ui.semantics.Role.Checkbox) {
                                if (on) pending.remove(marker) else pending.add(marker)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(marker, fontSize = 20.sp, color = if (open) palette.text else palette.subtext) }
                }
            }
            // The fourth tap is visibly unavailable before it is made, and says why.
            if (full) Text(stringResource(R.string.cal_markers_full), color = palette.subtext, fontSize = 12.sp,
                           modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.cancel), color = palette.text) }
                TextButton(onClick = { state.markDay(date, pending.toList()); onDone() }) {
                    Text(stringResource(R.string.save), color = palette.accentText, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(state: AppState, day: String) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day, color = palette.text, fontSize = 16.sp,
             fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        TextButton(onClick = { state.newNote(day) }) {
            Text(stringResource(R.string.cal_add_note), color = palette.accentText, fontWeight = FontWeight.Medium)
        }
    }
}

/** One note on the open day, as the desktop's day list draws it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayRow(state: AppState, raw: JSONObject, day: String) {
    val palette = LocalPalette.current
    val id = (raw.opt("id") as? Number)?.toInt() ?: return
    val series = Recurrence.isRecurring(raw)
    val shown = Recurrence.effectiveNote(raw, day)
    // A series' mark only: a note that no longer repeats keeps its days
    // unused, for a repeat brought back.
    val own = series && (raw.opt("overrides") as? JSONObject)?.has(day) == true
    val cameFrom = Recurrence.movedFrom(raw, day)
    var menu by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<String?>(null) }
    val title = shownTitle(shown.opt("title") as? String)
    // A table is said, never drawn, in a row this small -- the desktop's
    // "▦ 1 table" chip, in this row's short form beside 📎×N.
    val body = shown.opt("body") as? String ?: ""
    val tables = remember(body) { previewText(body).second }
    val marks = buildString {
        if (own) append("  ✎")
        if (cameFrom != null) append("  ↷")
        if (tables > 0) append("  ▦×$tables")
        if (!(shown.opt("comments") as? String).isNullOrBlank()) append("  💬")
        val files = (raw.opt("attachments") as? JSONArray)?.length() ?: 0
        if (files > 0) append("  📎×$files")
    }
    val colour = Days.colourOf(shown, state.load.categories, palette.hex.getValue("accent"))
    // Open beside the calendar on a wide screen: this day, or its whole series.
    val open = state.openId == id && (state.openDay == null || state.openDay == day)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.card)
                .then(if (open) Modifier.border(2.dp, palette.accent, RoundedCornerShape(10.dp)) else Modifier)
                .combinedClickable(onClick = { state.openNote(id, day) }, onLongClick = { menu = true }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(hexColor(colour)))
            Text((if (series) "🔁 " else "") + glimpse(title), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                 maxLines = 1, overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
            Text(marks, color = palette.subtext, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(end = 12.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (series) {
                DropdownMenuItem(text = { Text(stringResource(R.string.day_edit_this)) },
                                 onClick = { menu = false; state.openNote(id, day) })
                DropdownMenuItem(text = { Text(stringResource(R.string.day_edit_series)) },
                                 onClick = { menu = false; state.openNote(id) })
                if (own || cameFrom != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.day_reset)) },
                                     onClick = { menu = false; asking = "reset" })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.day_delete), color = palette.dangerText) },
                                 onClick = { menu = false; asking = "day" })
                DropdownMenuItem(text = { Text(stringResource(R.string.series_delete), color = palette.dangerText) },
                                 onClick = { menu = false; asking = "series" })
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.cal_open)) },
                                 onClick = { menu = false; state.openNote(id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_trash), color = palette.dangerText) },
                                 onClick = { menu = false; state.trash(listOf(id)) })
            }
        }
    }
    DayQuestions(state, id, day, cameFrom, own, asking) { asking = null }
}

/**
 * The questions one day of a series can raise -- reset it, delete it, or
 * delete the whole series -- in the desktop's words. Shared by the day's
 * list and the editor's day mode.
 */
@Composable
internal fun DayQuestions(state: AppState, id: Int, day: String, cameFrom: String?, own: Boolean,
                          asking: String?, onDone: () -> Unit) {
    val palette = LocalPalette.current
    val shown = Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day
    when (asking) {
        "reset" -> if (cameFrom != null && own) {
            val home = Recurrence.parseDate(cameFrom)?.let { Recurrence.showDay(it) } ?: cameFrom
            AlertDialog(
                onDismissRequest = onDone,
                title = { Text(stringResource(R.string.day_reset).removePrefix("↺ ")) },
                text = { Text(stringResource(R.string.day_reset_moved_text, home)) },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { state.resetDay(id, day, dropText = false); onDone() }) {
                            Text(stringResource(R.string.day_reset_only_date), color = palette.accentText)
                        }
                        TextButton(onClick = { state.resetDay(id, day); onDone() }) {
                            Text(stringResource(R.string.day_reset_date_and_text), color = palette.accentText)
                        }
                        TextButton(onClick = onDone) { Text(stringResource(R.string.cancel), color = palette.text) }
                    }
                },
                containerColor = palette.card, titleContentColor = palette.text, textContentColor = palette.subtext,
            )
        } else {
            val home = cameFrom?.let { Recurrence.parseDate(it)?.let { d -> Recurrence.showDay(d) } ?: it }
            Ask(
                title = stringResource(R.string.day_reset).removePrefix("↺ "),
                text = if (home != null) stringResource(R.string.day_reset_home, home)
                       else stringResource(R.string.day_reset_text, shown),
                confirm = stringResource(R.string.day_reset).removePrefix("↺ "),
                onConfirm = { state.resetDay(id, day); onDone() },
                onDismiss = onDone,
            )
        }
        "day" -> Ask(
            title = stringResource(R.string.day_delete),
            text = stringResource(R.string.day_delete_text, shown),
            confirm = stringResource(R.string.day_delete),
            onConfirm = { state.deleteDay(id, day); onDone() },
            onDismiss = onDone,
            destructive = true,
        )
        "series" -> Ask(
            title = stringResource(R.string.series_delete),
            text = stringResource(R.string.series_delete_text),
            confirm = stringResource(R.string.series_delete),
            onConfirm = { state.trash(listOf(id)); onDone() },
            onDismiss = onDone,
            destructive = true,
        )
    }
}

// ---- the History ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun History(state: AppState) {
    val palette = LocalPalette.current
    // Handed to Android only while short, as the list's search is ([SearchSaver]).
    var query by rememberSaveable(stateSaver = SearchSaver) { mutableStateOf("") }
    // A search handed over by the list, which does not read a day's own words.
    androidx.compose.runtime.LaunchedEffect(state.historyQuery) {
        state.historyQuery?.let { query = it; state.historyQuery = null }
    }
    var bucket by rememberSaveable { mutableStateOf("All") }
    val open = remember { mutableStateListOf<Int>() }
    val entries = remember(state.revision, query, bucket) {
        Recurrence.history(state.rawNotes(), query.trim(), bucket)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.inset)
                        .padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (query.isEmpty()) Text(stringResource(R.string.history_search), color = palette.subtext, fontSize = 15.sp)
                        field()
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)) {
                for (name in Recurrence.YEAR_BUCKETS) {
                    val on = name == bucket
                    Text(name, color = if (on) palette.onAccent else palette.text, fontSize = 13.sp,
                         modifier = Modifier.clip(RoundedCornerShape(16.dp))
                             .background(if (on) palette.accent else palette.card)
                             .clickable { bucket = name }
                             .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (entries.isEmpty()) {
            // A search or a chip that hides everything is not an empty calendar (the desktop's two texts).
            val filtered = query.isNotBlank() || bucket != "All"
            item { Text(stringResource(if (filtered) R.string.history_no_match else R.string.history_empty), color = palette.subtext,
                        fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
        }
        for (entry in entries) {
            when (entry) {
                is Recurrence.HistoryEntry.Dated -> item(key = "d${entry.noteId}") {
                    HistoryRow(Recurrence.showDate(entry.date), shownTitle(entry.title), "", 0) {
                        state.openNote(entry.noteId)
                    }
                }
                is Recurrence.HistoryEntry.Series -> {
                    val expanded = entry.noteId in open || entry.autoExpand
                    item(key = "s${entry.noteId}") {
                        val count = if (entry.days.isNotEmpty()) pluralStringResource(R.plurals.history_days, entry.days.size, entry.days.size) else ""
                        HistoryRow(
                            lead = if (entry.days.isEmpty()) "🔁" else if (expanded) "▾ 🔁" else "▸ 🔁",
                            title = shownTitle(entry.title),
                            // With no days of its own, the row opens the calendar: say so.
                            tail = listOf(entry.label, count.ifEmpty { stringResource(R.string.history_series_jump) })
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            indent = 0,
                        ) {
                            if (entry.days.isEmpty()) {
                                // Nothing to unfold: the row itself is the door, to
                                // the series' next day on the calendar.
                                Recurrence.parseDate(entry.jumpDate)?.let { day ->
                                    state.calendarMonth = YearMonth.from(day)
                                    state.selectedDay = Recurrence.formatDate(day)
                                    state.showHistory = false
                                }
                            } else if (entry.noteId in open) open.remove(entry.noteId) else open.add(entry.noteId)
                        }
                    }
                    if (expanded) {
                        for (day in entry.days) {
                            item(key = "s${entry.noteId}-${day.date}") {
                                val movedFrom = day.movedFrom?.let { stringResource(R.string.history_moved_from, Recurrence.showDate(it)) }
                                val offSchedule = stringResource(R.string.history_off_schedule)
                                val marks = buildString {
                                    if (day.edited && !day.offSchedule) append("✎ ")
                                    if (movedFrom != null) append("$movedFrom ")
                                    if (day.offSchedule) append(offSchedule)
                                }.trim()
                                HistoryRow(Recurrence.showDate(day.date), shownTitle(day.title), marks, 1) {
                                    // A day off the schedule is no day of the series
                                    // to open: the series opens, its banner leading to
                                    // the questions that settle such days -- the
                                    // desktop's History sends it to the same walk.
                                    // Opened as a day, it could be moved or deleted
                                    // where no calendar would ever show it.
                                    if (day.offSchedule) state.openNote(entry.noteId)
                                    else state.openNote(entry.noteId, day.date)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun HistoryRow(lead: String, title: String, tail: String, indent: Int, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (indent * 20).dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lead, color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
            Text(glimpse(title), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                 overflow = TextOverflow.Ellipsis)
        }
        if (tail.isNotEmpty()) Text(tail, color = palette.subtext, fontSize = 12.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}
