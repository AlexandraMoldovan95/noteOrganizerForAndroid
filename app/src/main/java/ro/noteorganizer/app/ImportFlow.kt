package ro.noteorganizer.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.noteorganizer.app.ui.theme.LocalPalette

/**
 * Pasting a code in. Arrives with the code already filled in when it was
 * shared from another app (WhatsApp, email): "Share → Note Organizer".
 */
@Composable
fun CodeImportDialog(state: AppState, initial: String, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<Importing.Plan?>(null) }

    val ready = plan
    if (ready != null) {
        ImportQuestions(state, ready, onCancel = onClose) { answers ->
            finishImport(state, ready, answers, attachmentsDir = null, extras = Importing.Extras())
            onClose()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.import_code)) },
        text = {
            Column {
                Text(stringResource(R.string.import_code_explain), color = palette.subtext, fontSize = 13.sp)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; problem = null },
                    // A share code is Base64: one "corrected" letter and it
                    // is no longer the same code. The keyboard is told to
                    // keep its hands off.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(top = 10.dp),
                    placeholder = { Text(stringResource(R.string.import_code_hint, Sharing.PREFIX)) },
                )
                problem?.let { Text(it, color = palette.dangerText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                TextButton(onClick = { clipboard.getText()?.text?.let { draft = it; problem = null } }) {
                    Text(stringResource(R.string.paste))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = draft.isNotBlank(), onClick = {
                when (val found = Notebook.inspectShareCode(state.load, draft)) {
                    is Notebook.Inspection.Failed -> problem = found.reason
                    is Notebook.Inspection.Ready -> plan = found.plan
                }
            }) { Text(stringResource(R.string.import_action), fontWeight = FontWeight.Medium) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
    )
}

/** Where the three questions have got to. */
private enum class Step { KNOWN, UNCHANGED, LOOKALIKES }

/**
 * The desktop's three questions, asked in its order and in its words,
 * each answered before anything is written:
 *
 * 1. notes already here and changed -- update them, or add them as new;
 * 2. notes already here, unchanged -- skip them, or add copies;
 * 3. new notes that look like ones here -- import anyway, or skip them.
 *
 * A question with nothing to ask about is not asked. Cancel, on any
 * question that offers it, leaves everything exactly as it was.
 */
@Composable
fun ImportQuestions(
    state: AppState,
    plan: Importing.Plan,
    // Worked out before, away from the screen, for a backup's thousands;
    // a code's few are counted here, once each.
    alikes: Importing.LookAlikes? = null,
    onCancel: () -> Unit,
    onAnswered: (Importing.Answers) -> Unit,
) {
    var answers by remember { mutableStateOf(Importing.Answers()) }
    var step by remember { mutableStateOf(firstStep(plan)) }
    val source = plan.source.word
    val counts = alikes ?: remember(plan) { Importing.LookAlikes(state.load.raw ?: org.json.JSONArray(), plan) }

    fun next(from: Step, now: Importing.Answers) {
        answers = now
        val following = Step.entries.filter { it.ordinal > from.ordinal }
            .firstOrNull { asks(it, plan, counts, now) }
        if (following == null) onAnswered(now) else step = following
    }

    LaunchedSkipIfNothing(plan, counts) { onAnswered(Importing.Answers()) }
    if (!asks(Step.KNOWN, plan, counts, answers) && !asks(Step.UNCHANGED, plan, counts, answers) &&
        !asks(Step.LOOKALIKES, plan, counts, answers)) return

    when (step) {
        Step.KNOWN -> {
            val count = plan.differing.size
            val one = count == 1
            val title = when {
                one && plan.total == 1 -> stringResource(R.string.q_same_note_title)
                one -> stringResource(R.string.q_known_title_one)
                else -> stringResource(R.string.q_known_title, count)
            }
            val text = buildString {
                when {
                    one && plan.total == 1 -> append(stringResource(R.string.q_same_note_text))
                    one -> append(stringResource(R.string.q_known_text_one, plan.total, source))
                    count == plan.total -> append(stringResource(R.string.q_known_all, count, source))
                    else -> append(stringResource(R.string.q_known_text, count, plan.total, source))
                }
                if (plan.older > 0) {
                    append("\n\n")
                    append(if (one) stringResource(R.string.q_older_one) else stringResource(R.string.q_older_many, plan.older))
                }
            }
            Choice(
                title = title,
                text = text,
                options = listOf(
                    stringResource(if (one) R.string.q_update_this else R.string.q_update_n, count) to {
                        next(Step.KNOWN, answers.copy(update = true))
                    },
                    stringResource(if (one) R.string.q_add_new else R.string.q_add_new_n) to {
                        next(Step.KNOWN, answers.copy(update = false))
                    },
                ),
                cancel = onCancel,
            )
        }
        Step.UNCHANGED -> {
            val count = plan.unchanged.size
            // One note is said as one, as the desktop says it: "All 1 notes"
            // and "Skip the 1" came of counting it.
            val one = count == 1
            Choice(
                title = stringResource(R.string.q_unchanged_title),
                text = when {
                    count == plan.total && one -> stringResource(R.string.q_unchanged_one, source)
                    count == plan.total -> stringResource(R.string.q_unchanged_all, count, source)
                    one -> stringResource(R.string.q_unchanged_some_one, plan.total, source)
                    else -> stringResource(R.string.q_unchanged_some, count, plan.total, source)
                },
                // Skipping is the safe answer and changes nothing, so it is
                // the one that stands out -- and there is no Cancel: skipping
                // already is one.
                options = listOf(
                    (if (one) stringResource(R.string.q_skip_one) else stringResource(R.string.q_skip_n, count)) to
                        { next(Step.UNCHANGED, answers.copy(copyUnchanged = false)) },
                    stringResource(if (one) R.string.q_add_copy else R.string.q_add_copies) to
                        { next(Step.UNCHANGED, answers.copy(copyUnchanged = true)) },
                ),
                cancel = null,
            )
        }
        Step.LOOKALIKES -> {
            val count = counts.of(answers.update)
            val remaining = plan.fresh.size + if (answers.update) 0 else plan.differing.size
            if (count == remaining) {
                Choice(
                    title = stringResource(R.string.q_all_imported_title),
                    text = if (count == 1) stringResource(R.string.q_all_imported_one, source)
                           else stringResource(R.string.q_all_imported_text, count, source),
                    options = listOf(stringResource(R.string.q_import_anyway) to {
                        onAnswered(answers.copy(skipDuplicates = false))
                    }),
                    cancel = onCancel,
                )
            } else {
                Choice(
                    title = stringResource(R.string.q_duplicates_title),
                    text = if (count == 1) stringResource(R.string.q_duplicates_one, remaining, source)
                           else stringResource(R.string.q_duplicates_text, count, remaining, source),
                    options = listOf(
                        (if (count == 1) stringResource(R.string.q_skip_duplicate)
                         else stringResource(R.string.q_skip_duplicates, count)) to { onAnswered(answers.copy(skipDuplicates = true)) },
                        stringResource(R.string.q_import_all) to { onAnswered(answers.copy(skipDuplicates = false)) },
                    ),
                    cancel = onCancel,
                )
            }
        }
    }
}

private fun firstStep(plan: Importing.Plan): Step = when {
    plan.differing.isNotEmpty() -> Step.KNOWN
    plan.unchanged.isNotEmpty() -> Step.UNCHANGED
    else -> Step.LOOKALIKES
}

private fun asks(step: Step, plan: Importing.Plan, counts: Importing.LookAlikes, answers: Importing.Answers): Boolean =
    when (step) {
        Step.KNOWN -> plan.differing.isNotEmpty()
        Step.UNCHANGED -> plan.unchanged.isNotEmpty()
        Step.LOOKALIKES -> counts.of(answers.update) > 0
    }

/** When an import raises no question at all, it simply goes ahead. */
@Composable
private fun LaunchedSkipIfNothing(plan: Importing.Plan, counts: Importing.LookAlikes, go: () -> Unit) {
    val nothing = remember(plan) {
        plan.differing.isEmpty() && plan.unchanged.isEmpty() && counts.of(false) == 0
    }
    androidx.compose.runtime.LaunchedEffect(plan) { if (nothing) go() }
}

/**
 * A question with its answers stacked, the first being the one the eye
 * lands on; Cancel last and plain.
 */
@Composable
private fun Choice(title: String, text: String, options: List<Pair<String, () -> Unit>>, cancel: (() -> Unit)?) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = { cancel?.invoke() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                options.forEachIndexed { index, (label, act) ->
                    TextButton(onClick = act) {
                        Text(label, color = palette.accentText,
                             fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (cancel != null) {
                    TextButton(onClick = cancel) { Text(stringResource(R.string.cancel), color = palette.text) }
                }
            }
        },
        containerColor = palette.card,
        titleContentColor = palette.text,
        textContentColor = palette.subtext,
    )
}

/**
 * Writes what the answers agreed to, then says what happened, counted the
 * way the desktop's "Import complete" counts it.
 */
fun finishImport(
    state: AppState,
    plan: Importing.Plan,
    answers: Importing.Answers,
    attachmentsDir: java.io.File?,
    extras: Importing.Extras,
) {
    // A code shared to the app can arrive over an open note -- the very
    // note it may update. A new note saved here lands at the end of the
    // file, so the places the plan counted stay where they were.
    if (!state.beforeImport()) return
    when (val result = Importing.apply(state.load, plan, answers, attachmentsDir, extras)) {
        is Importing.Result.Failed -> state.say(result.reason)
        is Importing.Result.Done -> {
            state.afterImport()
            // One note arrived: a tap opens it, where the desktop opens it
            // for review. The phone said "Added 1 note" and left it to be
            // found in the list.
            val o = result.outcome
            if (o.added + o.updated == 1 && o.firstId > 0) {
                state.offer(importMessage(o), "Open") { state.openNote(o.firstId) }
            } else {
                state.say(importMessage(o))
            }
        }
    }
}

fun importMessage(o: Importing.Outcome): String {
    val parts = ArrayList<String>()
    parts.add(if (o.added == 1) "Added 1 note." else "Added ${o.added} notes.")
    if (o.updated > 0) {
        parts.add("Updated ${o.updated} you already had (“↩ Restore previous” undoes each one).")
    }
    if (o.unchangedSkipped > 0) parts.add("${o.unchangedSkipped} were already here, unchanged, and were skipped.")
    if (o.duplicatesSkipped > 0) parts.add("${o.duplicatesSkipped} duplicates skipped.")
    val also = ArrayList<String>()
    if (o.addedCategories > 0) also.add("${o.addedCategories} categor${if (o.addedCategories == 1) "y" else "ies"}")
    if (o.restoredDays > 0) also.add("${o.restoredDays} calendar day marking${if (o.restoredDays == 1) "" else "s"}")
    if (o.addedTemplates > 0) also.add("${o.addedTemplates} template${if (o.addedTemplates == 1) "" else "s"}")
    if (o.addedTrash > 0) also.add("${o.addedTrash} trashed note${if (o.addedTrash == 1) "" else "s"}")
    if (also.isNotEmpty()) parts.add("Also restored " + also.joinToString(" and ") + ".")
    if (o.refusedCategories > 0) {
        parts.add("${o.refusedCategories} categories could not be added, so their notes are in General.")
    }
    return parts.joinToString(" ")
}
