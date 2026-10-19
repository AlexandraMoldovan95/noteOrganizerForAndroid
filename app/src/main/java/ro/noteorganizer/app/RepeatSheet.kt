package ro.noteorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import ro.noteorganizer.app.ui.theme.LocalPalette
import java.time.LocalDate

/**
 * A note's repeat, chosen rather than typed: the desktop's Repeat row --
 * how often, every how many, on which days, what a short month does, and
 * when it ends -- each a control, so nothing on this sheet can be a value
 * the app cannot read. Under it, the rule in the desktop's words and the
 * next days it gives, so the person sees what they chose before saving.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(state: AppState, model: EditorModel, onDone: () -> Unit) {
    val palette = LocalPalette.current
    val stored = remember { Recurrence.normalize(state.rawNote(model.id)?.opt("recurrence")) }
    var freq by remember { mutableStateOf(stored.getString("freq")) }
    var interval by remember { mutableIntStateOf(stored.getInt("interval")) }
    val weekdays = remember { mutableStateListOf<Int>().apply { addAll(ints(stored.getJSONArray("weekdays"))) } }
    val monthDays = remember { mutableStateListOf<Int>().apply { addAll(ints(stored.getJSONArray("month_days"))) } }
    var policy by remember { mutableStateOf(stored.getString("month_day_policy")) }
    var endMode by remember {
        mutableStateOf(if (stored.getInt("count") > 0) "after" else if (stored.getString("end").isNotEmpty()) "on" else "never")
    }
    var end by remember { mutableStateOf(stored.getString("end")) }
    var count by remember { mutableIntStateOf(stored.getInt("count").coerceAtLeast(1)) }
    var start by remember { mutableStateOf(model.date) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun draft(): JSONObject = JSONObject()
        .put("freq", freq).put("interval", interval)
        .put("weekdays", JSONArray(weekdays.sorted())).put("month_days", JSONArray(monthDays.sorted()))
        .put("month_day_policy", policy)
        .put("end", if (endMode == "on") end else "")
        .put("count", if (endMode == "after") count else 0)

    val anchor = Recurrence.parseDate(start)
    val needsDate = freq.isNotEmpty() && anchor == null
    val noteNow = JSONObject().put("date", start).put("recurrence", draft())
    val next = if (anchor != null && freq.isNotEmpty())
        Recurrence.expand(noteNow, maxOf(anchor, LocalDate.now()), LocalDate.now().plusYears(5), limit = 3) else emptyList()
    // A short month only matters for a day it might not have.
    val shortMonthMatters = anchor != null && when (freq) {
        "monthly" -> (if (monthDays.isEmpty()) listOf(anchor.dayOfMonth) else monthDays).any { it > 28 }
        "yearly" -> anchor.monthValue == 2 && anchor.dayOfMonth == 29
        else -> false
    }

    AppSheet(onDismissRequest = onDone) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.repeat), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp)) {
                for ((key, label) in Recurrence.FREQ_LABELS) Chip(label, key == freq) { freq = key; problem = null }
            }

            DetailRow(stringResource(R.string.repeat_start)) {
                Text(Recurrence.showDate(start).ifBlank { stringResource(R.string.no_date) },
                     color = if (needsDate) palette.dangerText else palette.text, fontSize = 15.sp,
                     modifier = Modifier.clickable { pickingStart = true })
            }

            if (freq.isNotEmpty()) {
                val unit = when (freq) {
                    "daily" -> pluralStringResource(R.plurals.unit_day, interval)
                    "weekly" -> pluralStringResource(R.plurals.unit_week, interval)
                    "monthly" -> pluralStringResource(R.plurals.unit_month, interval)
                    else -> pluralStringResource(R.plurals.unit_year, interval)
                }
                DetailRow(stringResource(R.string.repeat_every)) {
                    Stepper(interval, 1..Recurrence.MAX_INTERVAL, unit) { interval = it }
                }

                if (freq == "weekly") {
                    Text(stringResource(R.string.repeat_on), color = palette.subtext, fontSize = 14.sp,
                         modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Recurrence.WEEKDAY_NAMES.forEachIndexed { index, name ->
                            Chip(name.take(2), index in weekdays) {
                                if (index in weekdays) weekdays.remove(index) else weekdays.add(index)
                            }
                        }
                    }
                    if (weekdays.isEmpty()) Hint(stringResource(R.string.repeat_same_weekday))
                }

                if (freq == "monthly") {
                    Text(stringResource(R.string.repeat_on_days), color = palette.subtext, fontSize = 14.sp,
                         modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (day in 1..31) {
                            DayChip(day, day in monthDays) { if (day in monthDays) monthDays.remove(day) else monthDays.add(day) }
                        }
                    }
                    if (monthDays.isEmpty()) Hint(stringResource(R.string.repeat_same_monthday))
                }

                if (shortMonthMatters) {
                    Text(stringResource(R.string.repeat_short_month), color = palette.subtext, fontSize = 14.sp,
                         modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.repeat_clamp), policy == "clamp") { policy = "clamp" }
                        Chip(stringResource(R.string.repeat_skip), policy == "skip") { policy = "skip" }
                    }
                }

                Text(stringResource(R.string.repeat_ends), color = palette.subtext, fontSize = 14.sp,
                     modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.repeat_never), endMode == "never") { endMode = "never" }
                    Chip(stringResource(R.string.repeat_on_date), endMode == "on") { endMode = "on"; if (end.isEmpty()) pickingEnd = true }
                    Chip(stringResource(R.string.repeat_after), endMode == "after") { endMode = "after" }
                }
                if (endMode == "on") {
                    DetailRow(stringResource(R.string.repeat_on_date)) {
                        Text(Recurrence.showDate(end).ifBlank { stringResource(R.string.no_date) }, color = palette.text,
                             fontSize = 15.sp, modifier = Modifier.clickable { pickingEnd = true })
                    }
                }
                if (endMode == "after") {
                    DetailRow(stringResource(R.string.repeat_after)) {
                        Stepper(count, 1..Recurrence.MAX_OCCURRENCES, stringResource(R.string.repeat_times)) { count = it }
                    }
                }
            }

            // The rule in the desktop's words, and where it lands next.
            val said = Recurrence.describe(noteNow).ifEmpty { Recurrence.FREQ_LABELS.getValue("") }
            Text(said, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp))
            if (next.isNotEmpty()) {
                Text(next.joinToString(", ") { Recurrence.showDate(it) } + if (next.size == 3) ", …" else "",
                     color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (needsDate) Text(stringResource(R.string.repeat_needs_date), color = palette.dangerText, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp))
            problem?.let { Text(it, color = palette.dangerText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }

            val offKept = stringResource(R.string.repeat_off_kept)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.cancel), color = palette.text) }
                TextButton(
                    enabled = !needsDate,
                    onClick = {
                        // The start as stored, before this Save writes a new one.
                        val startWas = state.rawNote(model.id)?.opt("date")
                        if (start != model.date) {
                            model.date = start
                            model.changed()
                        }
                        // The note first -- a new one, or a new start date --
                        // then its repeat, which only a saved note can hold.
                        if (!model.save(force = model.id == 0)) {
                            problem = model.problem
                            return@TextButton
                        }
                        val before = state.rawNote(model.id)?.let { now ->
                            if (startWas != null) JSONObject(now.toString()).put("date", startWas) else now
                        }
                        val losing = freq.isEmpty() && before != null && Days.repeatOffLosesDays(before)
                        if (state.setRepeat(model.id, draft(), startWas)) {
                            if (losing) state.say(offKept)
                            onDone()
                        }
                    },
                ) { Text(stringResource(R.string.save), color = palette.accentText, fontWeight = FontWeight.Medium) }
            }
        }
    }
    if (pickingStart) DateDialog(start, onPick = { start = it; pickingStart = false; problem = null }) { pickingStart = false }
    if (pickingEnd) DateDialog(end.ifEmpty { start }, onPick = { end = it; pickingEnd = false }) { pickingEnd = false }
}

private fun ints(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(label, color = if (on) palette.onAccent else palette.text, fontSize = 14.sp,
         modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) palette.accent else palette.inset)
             .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun DayChip(day: Int, on: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(19.dp))
            .background(if (on) palette.accent else palette.inset)
            .then(if (on) Modifier else Modifier.border(1.dp, palette.hairline, RoundedCornerShape(19.dp)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(day.toString(), color = if (on) palette.onAccent else palette.text, fontSize = 13.sp) }
}

@Composable
private fun Hint(text: String) {
    val palette = LocalPalette.current
    Text(text, color = palette.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

/** − N + : a number chosen, never typed. */
@Composable
private fun Stepper(value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("−", color = if (value > range.first) palette.accentText else palette.subtext, fontSize = 22.sp,
             modifier = Modifier.clickable(enabled = value > range.first) { onChange(value - 1) }.padding(horizontal = 12.dp))
        Text("$value $unit", color = palette.text, fontSize = 15.sp)
        Text("+", color = if (value < range.last) palette.accentText else palette.subtext, fontSize = 22.sp,
             modifier = Modifier.clickable(enabled = value < range.last) { onChange(value + 1) }.padding(horizontal = 12.dp))
    }
}
