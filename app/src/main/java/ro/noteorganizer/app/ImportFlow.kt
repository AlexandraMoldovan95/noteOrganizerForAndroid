package ro.noteorganizer.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // Keyed by the code: one shared in while the dialog was open kept the
    // first one's text, and Import brought that one, the new code dropped.
    var draft by remember(initial) { mutableStateOf(initial) }
    var problem by remember(initial) { mutableStateOf<String?>(null) }
    var read by remember(initial) { mutableStateOf<CodeRead?>(null) }
    var reading by remember(initial) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val unreadable = stringResource(R.string.code_unreadable)
    // On its side, or at the largest letters with the keyboard up, the problem
    // line fell under the field, out of sight: Import seemed to do nothing.
    val news = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(problem, reading) { if (problem != null || reading) news.bringIntoView() }

    val ready = read
    if (ready != null) {
        ImportQuestions(state, ready.plan, ready.alikes, onCancel = onClose) { answers ->
            finishImport(state, ready.plan, answers, attachmentsDir = null, extras = Importing.Extras())
            onClose()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.import_code)) },
        text = {
            // Scrolled, the field held to a height: grown with a long code, it
            // took the whole room and hid the problem line and Paste below it.
            DialogBody {
                Text(stringResource(R.string.import_code_explain), color = palette.subtext, fontSize = 13.sp)
                // A code is one unbroken word, which a field lays out letter by
                // letter: 120,000 shared to the app froze the screen. A long one
                // shows its start and length; Copy takes it whole, Paste replaces it.
                if (draft.length > CODE_SHOWN_CHARS) {
                    Text(stringResource(R.string.code_too_long, draft.length), color = palette.text, fontSize = 13.sp,
                         modifier = Modifier.padding(top = 10.dp))
                    Text(draft.take(CODE_START_CHARS) + "…", color = palette.text, maxLines = 1, softWrap = false,
                         style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                         modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(draft))
                        problem = null
                    }) { Text(stringResource(R.string.copy_code)) }
                } else OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; problem = null },
                    // A share code is Base64: one "corrected" letter and it
                    // is no longer the same code. The keyboard is told to
                    // keep its hands off.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp).padding(top = 10.dp),
                    placeholder = { Text(stringResource(R.string.import_code_hint, Sharing.PREFIX)) },
                )
                // Said in the dialog alone, so read out as it appears ([saidAloud]).
                Column(Modifier.bringIntoViewRequester(news)) {
                    problem?.let { Text(it, color = palette.dangerText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).saidAloud()) }
                    if (reading) {
                        Text(stringResource(R.string.code_reading), color = palette.accentText, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 8.dp))
                    }
                }
                TextButton(onClick = { clipboard.getText()?.text?.let { draft = it; problem = null } }) {
                    Text(stringResource(R.string.paste))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = draft.isNotBlank() && !reading, onClick = {
                // Read away from the screen, as a backup is: a short code can
                // unpack to 20 MB and 200 notes, the screen still meanwhile. The
                // list is copied here, since a save made meanwhile replaces it.
                val notebook = org.json.JSONArray().also { copy ->
                    state.load.raw?.let { raw -> for (i in 0 until raw.length()) copy.put(raw.opt(i)) }
                }
                val categories = state.load.categories.keys.toSet()
                val code = draft
                reading = true
                scope.launch {
                    val found: Any = withContext(Dispatchers.Default) { readCode(notebook, categories, code) ?: unreadable }
                    reading = false
                    when (found) {
                        is CodeRead -> read = found
                        is String -> problem = found
                    }
                }
            }) { Text(stringResource(R.string.import_action), fontWeight = FontWeight.Medium) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
    )
}

/**
 * The longest code the dialog shows whole in its field: far past one note's
 * code, and a run a field lays out at once (Editing.MAX_UNBROKEN). Past it
 * the dialog shows its first [CODE_START_CHARS] characters and its length.
 */
private const val CODE_SHOWN_CHARS = 5_000
private const val CODE_START_CHARS = 60

/** A code read: its plan, and how many of its notes look like ones here. */
private class CodeRead(val plan: Importing.Plan, val alikes: Importing.LookAlikes)

/**
 * A code decoded and planned against [notebook], with its look-alikes
 * counted -- the work a backup does on a thread of its own -- or why not;
 * null when what it holds stopped the reading (the caller says so).
 */
private fun readCode(notebook: org.json.JSONArray, categories: Set<String>, code: String): Any? = try {
    when (val found = Notebook.inspectShareCode(notebook, categories, code)) {
        is Notebook.Inspection.Failed -> found.reason
        is Notebook.Inspection.Ready -> CodeRead(found.plan, Importing.LookAlikes(notebook, found.plan).warm(found.plan))
    }
} catch (e: Exception) {
    // Exception: Android's JSONException is not a RuntimeException.
    null
} catch (e: StackOverflowError) {
    // A hand-made code nested past what nestedPast sees ('#' comments).
    null
}

/** Where the three questions have got to. */
private enum class Step { KNOWN, UNCHANGED, LOOKALIKES }

/**
 * The desktop's three questions, in its order and words, each answered
 * before anything is written: notes here and changed (update, or add as
 * new); notes here unchanged (skip, or add copies); new notes that look like
 * ones here (import anyway, or skip). A question with nothing to ask about
 * is not asked, and Cancel, wherever offered, leaves everything as it was.
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
                if (plan.filed == 0) when {
                    one && plan.total == 1 -> append(stringResource(R.string.q_same_note_text))
                    one -> append(stringResource(R.string.q_known_text_one, plan.total, source))
                    count == plan.total -> append(stringResource(R.string.q_known_all, count, source))
                    else -> append(stringResource(R.string.q_known_text, count, plan.total, source))
                } else {
                    // A note differing by files alone takes them and keeps no copy:
                    // no Restore previous promised of it, as the desktop says.
                    when {
                        one && plan.total == 1 -> {}
                        one -> append(stringResource(R.string.q_known_lead_one, plan.total, source))
                        count == plan.total -> append(stringResource(R.string.q_known_lead_all, count, source))
                        else -> append(stringResource(R.string.q_known_lead, count, plan.total, source))
                    }
                    if (isNotEmpty()) append("\n\n")
                    append(when {
                        plan.filed == count -> stringResource(if (one) R.string.q_files_only_it else R.string.q_files_only_they)
                        plan.filed == 1 -> stringResource(R.string.q_files_some_one)
                        else -> stringResource(R.string.q_files_some, plan.filed)
                    })
                    append(" ")
                    append(stringResource(if (one && plan.total == 1) R.string.q_adding_both_delete else R.string.q_adding_both))
                }
                if (plan.older > 0) {
                    append("\n\n")
                    append(if (one) stringResource(R.string.q_older_one) else stringResource(R.string.q_older_many, plan.older))
                }
                // One rescue copy a note: an update's takes the place of one
                // the note still needs, which is said, not done in silence.
                if (plan.rescued > 0) {
                    append("\n\n")
                    append(when {
                        one -> stringResource(R.string.q_rescued_it)
                        plan.rescued == 1 -> stringResource(R.string.q_rescued_one)
                        else -> stringResource(R.string.q_rescued_many, plan.rescued)
                    })
                }
            }
            val update = stringResource(if (one) R.string.q_update_this else R.string.q_update_n, count) to {
                next(Step.KNOWN, answers.copy(update = true))
            }
            val add = stringResource(if (one) R.string.q_add_new else R.string.q_add_new_n) to {
                next(Step.KNOWN, answers.copy(update = false))
            }
            Choice(
                title = title,
                text = text,
                // The answer that stands out loses nothing when the arriving
                // versions are older: Update was it, and wrote them over the
                // newer ones here.
                options = if (Importing.updateStandsOut(plan)) listOf(update, add) else listOf(add, update),
                cancel = onCancel,
            )
        }
        Step.UNCHANGED -> {
            val count = plan.unchanged.size
            // One note is said as one, as the desktop says it: "All 1 notes"
            // and "Skip the 1" came of counting it.
            val one = count == 1
            // Fields a newer version wrote that the note here lacks: "nothing
            // this device lacks" was untrue, and Skip leaves them out.
            val alsoHold = if (plan.newer == 1) stringResource(R.string.q_newer_one_of)
                           else stringResource(R.string.q_newer_n_of, plan.newer)
            Choice(
                title = stringResource(R.string.q_unchanged_title),
                text = when {
                    plan.newer > 0 && count == plan.total && one -> stringResource(R.string.q_unchanged_newer_one, source)
                    plan.newer > 0 && count == plan.total -> stringResource(R.string.q_unchanged_newer_all, count, source, alsoHold)
                    plan.newer > 0 && one -> stringResource(R.string.q_unchanged_newer_some_one, plan.total, source)
                    plan.newer > 0 -> stringResource(R.string.q_unchanged_newer_some, count, plan.total, source, alsoHold)
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
            if (count == remaining && Importing.goesOnPastLookAlikes(plan, answers)) {
                Choice(
                    title = stringResource(R.string.q_duplicates_title),
                    text = if (count == 1) stringResource(R.string.q_all_imported_one_going_on, source)
                           else stringResource(R.string.q_all_imported_going_on, count, source),
                    options = listOf(
                        (if (count == 1) stringResource(R.string.q_skip_duplicate)
                         else stringResource(R.string.q_skip_duplicates, count)) to { onAnswered(answers.copy(skipDuplicates = true)) },
                        stringResource(R.string.q_import_anyway) to { onAnswered(answers.copy(skipDuplicates = false)) },
                    ),
                    cancel = onCancel,
                )
            } else if (count == remaining) {
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
        // Scrolled: at the largest letters, or on a phone on its side, its
        // last warning was cut under the answers, nothing showing there was more.
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
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
    // note it may update: its words are saved first. The plan's notes are
    // found again by identity, wherever the list has moved them since.
    if (!state.beforeImport()) return
    val before = state.noteIds()
    when (val result = Importing.apply(state.load, plan, answers, attachmentsDir, extras)) {
        is Importing.Result.Failed -> {
            // What it kept (a Trash, categories) read from the files, as after
            // one that worked: the Trash page showed none of it, and a category
            // added next wrote over those kept. Said long: the reason is long.
            state.afterImport()
            state.say(result.reason, long = true)
        }
        is Importing.Result.Done -> {
            state.afterImport()
            // One note arrived: a tap opens it, where the desktop opens it
            // for review. The phone said "Added 1 note" and left it to be
            // found in the list.
            val o = result.outcome
            if (o.added + o.updated == 1 && o.firstId > 0) {
                // About that note: held back by an Undo, not offered again once it is gone.
                state.offerAbout(listOf(o.firstId), importMessage(o), "Open") { state.openNote(o.firstId) }
            } else {
                state.say(importMessage(o))
            }
            // The notes it added, made first, and its '(earlier text)' notes, each
            // under its own word.
            state.arrived(before, o.added, "added", "Added ${o.added} notes")
        }
    }
}

fun importMessage(o: Importing.Outcome): String {
    val parts = ArrayList<String>()
    parts.add(if (o.added == 1) "Added 1 note." else "Added ${o.added} notes.")
    if (o.updated > 0) {
        parts.add("Updated ${o.updated} you already had (“↩ Restore previous” undoes each one).")
    }
    if (o.filesOnly > 0) {
        parts.add("${o.filesOnly} you already had took attached files this device lacked.")
    }
    if (o.unchangedSkipped > 0) parts.add("${o.unchangedSkipped} were already here, unchanged, and were skipped.")
    if (o.duplicatesSkipped > 0) parts.add("${o.duplicatesSkipped} duplicates skipped.")
    val also = ArrayList<String>()
    if (o.addedCategories > 0) also.add("${o.addedCategories} categor${if (o.addedCategories == 1) "y" else "ies"}")
    if (o.restoredDays > 0) also.add("${o.restoredDays} calendar day marking${if (o.restoredDays == 1) "" else "s"}")
    if (o.addedTemplates > 0) also.add("${o.addedTemplates} template${if (o.addedTemplates == 1) "" else "s"}")
    if (o.addedTrash > 0) also.add("${o.addedTrash} trashed note${if (o.addedTrash == 1) "" else "s"}")
    if (also.isNotEmpty()) parts.add("Also restored " + also.joinToString(" and ") + ".")
    if (o.trashLeftOut > 0 && o.keepDays != null) {
        val many = o.trashLeftOut != 1
        parts.add("${o.trashLeftOut} of the backup's trashed notes ${if (many) "were" else "was"} not brought in: " +
            "older than the ${o.keepDays} days this phone's Trash keeps notes, the next start would have " +
            "removed ${if (many) "them" else "it"}. The backup still holds ${if (many) "them" else "it"}.")
    }
    if (o.refusedCategories > 0) {
        parts.add("${o.refusedCategories} categories could not be added, so their notes are in General.")
    }
    // As the desktop says it after a save, in the same words.
    if (o.earlierText > 0) {
        parts.add("Earlier text of ${o.earlierText} day${if (o.earlierText == 1) "" else "s"} of a repeating note " +
            "kept as notes of their own.")
    }
    return parts.joinToString(" ")
}
