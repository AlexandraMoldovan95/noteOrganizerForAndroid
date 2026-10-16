package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.paletteOf

/** What Settings → Backup needs from the activity: Android's file pickers. */
class BackupActions(
    val export: () -> Unit,
    val import: () -> Unit,
    val busy: () -> String?,
)

/**
 * Settings: a list of pages, one shown at a time -- the desktop's split
 * page, folded for a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, backup: BackupActions, appVersion: String, openFeedback: () -> Unit) {
    val palette = LocalPalette.current
    val page = state.settingsPage
    BackHandler(enabled = page != null) { state.settingsPage = null }
    Column(Modifier.fillMaxSize().background(palette.bg)) {
        TopAppBar(
            title = { Text(page?.let { stringResource(titleOf(it)) } ?: stringResource(R.string.nav_settings)) },
            navigationIcon = {
                if (page != null) IconButton(onClick = { state.settingsPage = null }) { Text("←", fontSize = 22.sp) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg),
        )
        when (page) {
            null -> SettingsList(state)
            SettingsPage.NAME -> NamePage(state)
            SettingsPage.APPEARANCE -> AppearancePage(state)
            SettingsPage.CATEGORIES -> CategoriesPage(state)
            SettingsPage.TEMPLATES -> TemplatesPage(state)
            SettingsPage.BACKUP -> BackupPage(state, backup)
            SettingsPage.TRASH -> TrashPage(state)
            SettingsPage.DUPLICATES -> DuplicatesPage(state)
            SettingsPage.STATS -> StatsPage(state)
            SettingsPage.ABOUT -> AboutPage(state, appVersion, openFeedback)
        }
    }
}

private fun titleOf(page: SettingsPage): Int = when (page) {
    SettingsPage.NAME -> R.string.settings_name
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.CATEGORIES -> R.string.settings_categories
    SettingsPage.TEMPLATES -> R.string.settings_templates
    SettingsPage.BACKUP -> R.string.settings_backup
    SettingsPage.TRASH -> R.string.settings_trash
    SettingsPage.DUPLICATES -> R.string.settings_duplicates
    SettingsPage.STATS -> R.string.settings_stats
    SettingsPage.ABOUT -> R.string.settings_about
}

@Composable
private fun SettingsList(state: AppState) {
    val rows = listOf(
        Triple(SettingsPage.NAME, R.string.settings_name, R.string.settings_name_sub),
        Triple(SettingsPage.APPEARANCE, R.string.settings_appearance, R.string.settings_appearance_sub),
        Triple(SettingsPage.CATEGORIES, R.string.settings_categories, R.string.settings_categories_sub),
        Triple(SettingsPage.TEMPLATES, R.string.settings_templates, R.string.settings_templates_sub),
        Triple(SettingsPage.BACKUP, R.string.settings_backup, R.string.settings_backup_sub),
        Triple(SettingsPage.TRASH, R.string.settings_trash, R.string.settings_trash_sub),
        Triple(SettingsPage.DUPLICATES, R.string.settings_duplicates, R.string.settings_duplicates_sub),
        Triple(SettingsPage.STATS, R.string.settings_stats, R.string.settings_stats_sub),
        Triple(SettingsPage.ABOUT, R.string.settings_about, R.string.settings_about_sub),
    )
    val palette = LocalPalette.current
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(rows) { (page, title, sub) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { state.settingsPage = page }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(stringResource(title), color = palette.text, fontSize = 16.sp)
                Text(stringResource(sub), color = palette.subtext, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        content()
        Spacer(Modifier.height(32.dp))
    }
}

// ---- Duplicates ----

/**
 * The desktop's duplicates panel: the notebook's groups of the same note
 * twice, a tick on each copy to remove, the rest kept. The ticked copies go
 * to the Trash after a question, their files handed to a copy kept.
 */
@Composable
private fun DuplicatesPage(state: AppState) {
    val palette = LocalPalette.current
    val groups = remember(state.revision) { Organizing.duplicateGroups(state.load) }
    var ticked by remember(groups) { mutableStateOf(setOf<Int>()) }
    var asking by remember { mutableStateOf<Set<Int>?>(null) }
    val nothing = stringResource(R.string.dup_nothing)

    fun ask(remove: Set<Int>) {
        if (remove.isEmpty()) state.say(nothing) else asking = remove
    }

    PageColumn {
        Text(stringResource(R.string.dup_explain), color = palette.subtext, fontSize = 14.sp,
             modifier = Modifier.padding(bottom = 12.dp))
        if (groups.isEmpty()) {
            Text(stringResource(R.string.dup_none), color = palette.subtext, fontSize = 15.sp)
            return@PageColumn
        }
        Text(stringResource(R.string.dup_found, groups.size, groups.sumOf { it.size - 1 }),
             color = palette.subtext, fontSize = 13.sp)
        Text(stringResource(R.string.dup_merge_note), color = palette.accentText, fontSize = 13.sp,
             fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(bottom = 10.dp))
        for (group in groups) {
            val notes = group.mapNotNull { state.rawNote(it) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.card)
                    .padding(12.dp),
            ) {
                val title = notes.firstOrNull()?.optString("title")?.trim()?.ifEmpty { null } ?: "(untitled)"
                Text(stringResource(R.string.dup_group, title.take(36), group.size), color = palette.text,
                     fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val withFiles = notes.map { (it.optJSONArray("attachments")?.length() ?: 0) > 0 }
                if (withFiles.any { it } && withFiles.toSet().size > 1) {
                    Text(stringResource(R.string.dup_files_differ), color = palette.accentText, fontSize = 12.sp,
                         fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                for (note in notes) {
                    val id = note.optInt("id")
                    val created = note.optString("created")
                    val updated = note.optString("updated")
                    var meta = if (created.isNotEmpty()) "created ${Recurrence.showDate(created.take(10))}" else ""
                    if (updated.isNotEmpty() && updated != created) meta += " · updated ${Recurrence.showDate(updated.take(10))}"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ticked = if (id in ticked) ticked - id else ticked + id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (id in ticked) "☑" else "☐", color = palette.accentText, fontSize = 18.sp,
                             modifier = Modifier.padding(end = 10.dp))
                        Text(if (meta.isEmpty()) stringResource(R.string.dup_delete_copy_plain)
                             else stringResource(R.string.dup_delete_copy, meta),
                             color = palette.text, fontSize = 14.sp)
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(stringResource(R.string.dup_all_but_first), color = palette.accentText, fontSize = 13.sp,
                         fontWeight = FontWeight.Medium,
                         modifier = Modifier.clickable { ticked = ticked - group.toSet() + group.drop(1) })
                    Text(stringResource(R.string.dup_clear), color = palette.subtext, fontSize = 13.sp,
                         modifier = Modifier.clickable { ticked = ticked - group.toSet() })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // The precise action in the accent; the sweeping one outlined in the
        // warning's colour, as the desktop paints the two (its finding C-6).
        Text(
            stringResource(R.string.dup_move),
            color = palette.onAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent)
                .clickable { ask(ticked) }
                .padding(14.dp),
        )
        if (groups.size > 1) {
            Text(
                stringResource(R.string.dup_clean_all, groups.size),
                color = palette.dangerText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.dangerText, RoundedCornerShape(10.dp))
                    .clickable { ask(groups.flatMap { it.drop(1) }.toSet()) }
                    .padding(14.dp),
            )
        }
    }
    asking?.let { remove ->
        Ask(
            title = stringResource(R.string.dup_ask_title),
            text = stringResource(R.string.dup_ask_text, remove.size, state.load.retentionDays),
            confirm = stringResource(R.string.move_to_trash),
            onConfirm = {
                asking = null
                state.cleanUpDuplicates(groups, remove)
                ticked = emptySet()
            },
            onDismiss = { asking = null },
        )
    }
}

// ---- Statistics ----

/**
 * The desktop's snapshot: figures that lead, captions that follow quietly,
 * the time split as a small table, the categories as plain lines. Read
 * only: nothing here is a way to filter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsPage(state: AppState) {
    val palette = LocalPalette.current
    val snap = remember(state.revision) {
        Stats.of(state.rawNotes(), state.load.trash.size, state.templatesCount(), state.load.categories.keys.toList(), state.settings)
    }
    PageColumn {
        Text(stringResource(R.string.stats_explain), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        val tiles = listOf(
            snap.total to R.string.stats_total, snap.added.week to R.string.stats_added_week,
            snap.categories to R.string.stats_categories, snap.templates to R.string.stats_templates,
            snap.withChecklist to R.string.stats_checklists, snap.inTrash to R.string.stats_trash,
        )
        // Every tile the same size: each caption keeps room for two lines,
        // and the tiles of a row stretch to the tallest -- "Added this
        // week" wrapping used to make its tile stand taller than the rest.
        for (row in tiles.chunked(3)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((value, caption) in row) {
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(palette.card).padding(14.dp)) {
                        Text(value.toString(), color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(caption), color = palette.subtext, fontSize = 12.sp, minLines = 2)
                    }
                }
            }
        }
        // The time split: a header row said once, the figures lined up under it.
        val segments = listOf(R.string.stats_week, R.string.stats_month, R.string.stats_year, R.string.stats_older)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
            Spacer(Modifier.weight(1.3f))
            for (seg in segments) Text(stringResource(seg), color = palette.subtext, fontSize = 11.sp,
                                       modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))
        for ((caption, p) in listOf(R.string.stats_added to snap.added, R.string.stats_updated to snap.updated)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(caption), color = palette.subtext, fontSize = 13.sp, modifier = Modifier.weight(1.3f))
                for (value in listOf(p.week, p.month, p.year, p.older)) {
                    Text(value.toString(), color = if (value > 0) palette.text else palette.subtext, fontSize = 15.sp,
                         fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                         textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        SectionLabel(stringResource(R.string.stats_by_category), Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (snap.byCategory.isEmpty()) {
            Text(stringResource(R.string.no_notes_short), color = palette.subtext, fontSize = 13.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, count) in snap.byCategory) {
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(palette.card).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Dot(state.load.categories[name] ?: palette.hex.getValue("subtext"), 10)
                        Text("  $name  ", color = palette.text, fontSize = 14.sp)
                        Text(count.toString(), color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(stringResource(R.string.stats_since, snap.since), color = palette.subtext, fontSize = 13.sp,
             modifier = Modifier.padding(top = 18.dp))
    }
}

// ---- Your name ----

@Composable
private fun NamePage(state: AppState) {
    val palette = LocalPalette.current
    var draft by remember { mutableStateOf(Settings.username(state.settings)) }
    PageColumn {
        Text(stringResource(R.string.name_explain), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(80) }, singleLine = true,
                          label = { Text(stringResource(R.string.category_name)) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { if (state.putSetting(Settings.USERNAME, draft.trim())) state.say("Saved") },
            modifier = Modifier.padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        ) { Text(stringResource(R.string.save)) }
    }
}

// ---- Appearance ----

/**
 * The desktop's eight themes, four dark and four light, and one choice the
 * desktop does not have: following the phone's dark mode. When it follows,
 * a dark tile picks the theme for dark mode and a light one the theme for
 * light; when it does not, any tile is the theme.
 */
@Composable
private fun AppearancePage(state: AppState) {
    val palette = LocalPalette.current
    val raw = state.settings
    val follows = Settings.followsPhone(raw)
    PageColumn {
        // The whole row answers a tap, not only the switch: a thumb lands
        // on the words as often as on the control.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { state.putSetting(Settings.PHONE_FOLLOWS_PHONE, !follows) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.follow_phone), color = palette.text, fontSize = 16.sp)
                Text(stringResource(R.string.follow_phone_sub), color = palette.subtext, fontSize = 13.sp)
            }
            Switch(checked = follows, onCheckedChange = { state.putSetting(Settings.PHONE_FOLLOWS_PHONE, it) })
        }
        for ((label, names) in listOf(R.string.themes_dark to Themes.DARK, R.string.themes_light to Themes.LIGHT)) {
            SectionLabel(stringResource(label))
            for (name in names) {
                val chosen = if (follows) {
                    name == Settings.darkTheme(raw) || name == Settings.lightTheme(raw)
                } else {
                    name == Themes.valid(raw.opt(Settings.THEME) as? String)
                }
                ThemeTile(name, chosen) {
                    when {
                        !follows -> state.putSetting(Settings.THEME, name)
                        Themes.isDark(name) -> state.putSetting(Settings.PHONE_DARK_THEME, name)
                        else -> state.putSetting(Settings.PHONE_LIGHT_THEME, name)
                    }
                }
            }
        }
    }
}

/** A theme drawn in its own colours: the page, a card, the accent. */
@Composable
private fun ThemeTile(name: String, chosen: Boolean, onPick: () -> Unit) {
    val own = paletteOf(name)
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(if (chosen) 2.dp else 1.dp, if (chosen) palette.accent else palette.hairline, RoundedCornerShape(12.dp))
            .background(own.bg)
            .clickable(onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(own.card)) {
            Box(Modifier.padding(6.dp).size(width = 24.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(own.accent))
        }
        Text(Themes.LABELS.getValue(name), color = own.text, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (chosen) Text("✓", color = own.accent, fontSize = 18.sp)
    }
}

// ---- Categories ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesPage(state: AppState) {
    val palette = LocalPalette.current
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val counts = remember(state.load) {
        state.load.categories.keys.associateWith { Organizing.categoryCounts(state.load, it).first }
    }
    PageColumn {
        for ((name, colour) in state.load.categories) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = name }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(colour, 16)
                Text(name, color = palette.text, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
                Text(pluralStringResource(R.plurals.note_count, counts[name] ?: 0, counts[name] ?: 0),
                     color = palette.subtext, fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.add_category))
        }
    }
    if (adding) {
        CategoryEditor(
            title = stringResource(R.string.add_category),
            name = "",
            colour = Themes.PALETTE_COLORS[state.load.categories.size % Themes.PALETTE_COLORS.size],
            protected = false,
            onSave = { name, colour ->
                if (state.report(Organizing.addCategory(state.load, name, colour))) adding = false
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { old ->
        val colour = state.load.categories[old] ?: return@let
        var asking by remember(old) { mutableStateOf(false) }
        CategoryEditor(
            title = old,
            name = old,
            colour = colour,
            protected = old == Themes.PROTECTED_CATEGORY,
            onSave = { name, newColour ->
                var ok = true
                if (newColour != colour) ok = state.report(Organizing.recolourCategory(state.load, old, newColour))
                if (ok && name.trim() != old) ok = state.report(Organizing.renameCategory(state.load, old, name))
                if (ok) editing = null
            },
            onDelete = { asking = true },
            onDismiss = { editing = null },
        )
        if (asking) {
            val (filed, daysOnly) = Organizing.categoryCounts(state.load, old)
            DeleteCategoryQuestion(old, filed, daysOnly, state.load.retentionDays,
                onAnswer = { trashNotes ->
                    asking = false
                    if (trashNotes != null && state.report(Organizing.deleteCategory(state.load, old, trashNotes))) {
                        editing = null
                    }
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditor(
    title: String,
    name: String,
    colour: String,
    protected: Boolean,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var draftName by remember(name) { mutableStateOf(name) }
    var draftColour by remember(colour) { mutableStateOf(colour) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(title, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                enabled = !protected,
                singleLine = true,
                label = { Text(stringResource(R.string.category_name)) },
                supportingText = { if (protected) Text(stringResource(R.string.general_protected)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(stringResource(R.string.colour), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Swatches(Themes.PALETTE_COLORS, draftColour, allowNone = false) { draftColour = it }
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null && !protected) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = palette.dangerText) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(draftName, draftColour) },
                    enabled = draftName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/**
 * The desktop's question, in its words: a category with notes asks what
 * happens to them, and "delete the notes too" says they can be restored --
 * the label once said "permanent", which was untrue and frightened people
 * off the safe choice.
 */
@Composable
private fun DeleteCategoryQuestion(name: String, filed: Int, daysOnly: Int, days: Int, onAnswer: (Boolean?) -> Unit) {
    val palette = LocalPalette.current
    val dayLine = if (daysOnly > 0) "\n\n" + stringResource(R.string.category_days_move, daysOnly, name) else ""
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text(stringResource(R.string.delete_category)) },
        text = {
            Text(
                if (filed == 0) stringResource(R.string.delete_category_empty, name) + dayLine
                else pluralStringResource(R.plurals.delete_category_with, filed, name, filed) + dayLine,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (filed > 0) {
                    TextButton(onClick = { onAnswer(false) }) {
                        Text(stringResource(R.string.move_them_general), color = palette.accentText)
                    }
                    TextButton(onClick = { onAnswer(true) }) {
                        Text(stringResource(R.string.delete_notes_too, days), color = palette.dangerText)
                    }
                } else {
                    TextButton(onClick = { onAnswer(false) }) {
                        Text(stringResource(R.string.delete_the_category), color = palette.dangerText)
                    }
                }
                TextButton(onClick = { onAnswer(null) }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium, color = palette.text)
                }
            }
        },
        containerColor = palette.card,
    )
}

// ---- Backup ----

@Composable
private fun BackupPage(state: AppState, backup: BackupActions) {
    val palette = LocalPalette.current
    val last = state.settings.opt(Settings.LAST_EXPORT_AT) as? String
    val busy = backup.busy()
    PageColumn {
        Text(stringResource(R.string.backup_explain), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(
            onClick = backup.export,
            enabled = busy == null,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_export)) }
        Text(
            if (last.isNullOrEmpty()) stringResource(R.string.backup_never) else stringResource(R.string.backup_last, last),
            color = palette.subtext,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        OutlinedButton(onClick = backup.import, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_import))
        }
        Text(stringResource(R.string.backup_import_explain), color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        busy?.let { Text(it, color = palette.accentText, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp)) }
    }
}

// ---- Trash ----

@Composable
private fun TrashPage(state: AppState) {
    val palette = LocalPalette.current
    val load = state.load
    var picked by remember { mutableStateOf(setOf<Organizing.TrashKey>()) }
    var askEmpty by remember { mutableStateOf(false) }
    var askForever by remember { mutableStateOf<List<Organizing.TrashKey>?>(null) }
    var askWindow by remember { mutableStateOf<Int?>(null) }
    val entries = load.trash.sortedByDescending { it.deletedAt }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.trash_window, load.retentionDays), color = palette.subtext, fontSize = 13.sp)
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (days in listOf(7, 14, 30, 60, 90)) {
                    val on = days == load.retentionDays
                    Text(
                        "$days",
                        color = if (on) palette.onAccent else palette.text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) palette.accent else palette.card)
                            .clickable {
                                // Only a SHORTER window can take anything,
                                // and then it says what before it does.
                                if (days < load.retentionDays && Organizing.pastWindow(load, days) > 0) askWindow = days
                                else state.putSetting(Settings.RETENTION_DAYS, days)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(stringResource(R.string.days), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.trash_empty), color = palette.subtext, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(entries, key = { "${it.id}|${it.deletedAt}" }) { note ->
                val key = Organizing.TrashKey(note.id, note.deletedAt)
                val left = Notebook.daysLeftInTrash(note.deletedAt, load.retentionDays)
                val on = key in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) palette.calendarHasNotes else palette.card)
                        .clickable { picked = if (on) picked - key else picked + key }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (on) "☑" else "☐", color = palette.accentText, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(note.headline, color = palette.text, fontSize = 15.sp, maxLines = 1)
                        Text(
                            when (left) {
                                null -> stringResource(R.string.trash_unknown)
                                0 -> stringResource(R.string.trash_next_start)
                                else -> pluralStringResource(R.plurals.trash_days_left, left, left)
                            },
                            color = if (left != null && left <= 3) palette.dangerText else palette.subtext,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (picked.isNotEmpty()) {
                Button(
                    onClick = { state.restoreFromTrash(picked); picked = emptySet() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) { Text(pluralStringResource(R.plurals.restore_n, picked.size, picked.size)) }
                TextButton(onClick = { askForever = picked.toList() }) {
                    Text(stringResource(R.string.delete_forever), color = palette.dangerText)
                }
            } else if (entries.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { askEmpty = true }) { Text(stringResource(R.string.empty_trash), color = palette.dangerText) }
            }
        }
    }
    if (askEmpty) {
        Ask(
            title = stringResource(R.string.empty_trash),
            text = pluralStringResource(R.plurals.empty_trash_text, entries.size, entries.size),
            confirm = stringResource(R.string.empty_trash),
            destructive = true,
            onConfirm = { askEmpty = false; state.emptyTrash() },
            onDismiss = { askEmpty = false },
        )
    }
    askForever?.let { keys ->
        Ask(
            title = stringResource(R.string.delete_forever),
            text = pluralStringResource(R.plurals.delete_forever_text, keys.size, keys.size),
            confirm = stringResource(R.string.delete_forever),
            destructive = true,
            onConfirm = { askForever = null; state.deleteForever(keys); picked = emptySet() },
            onDismiss = { askForever = null },
        )
    }
    askWindow?.let { days ->
        val doomed = Organizing.pastWindow(load, days)
        Ask(
            title = stringResource(R.string.shorter_window),
            text = pluralStringResource(R.plurals.shorter_window_text, doomed, doomed, days, load.retentionDays),
            confirm = stringResource(R.string.keep_for_days, days),
            destructive = true,
            onConfirm = { askWindow = null; state.putSetting(Settings.RETENTION_DAYS, days) },
            onDismiss = { askWindow = null },
        )
    }
}

// ---- About ----

@Composable
private fun AboutPage(state: AppState, appVersion: String, openFeedback: () -> Unit) {
    val palette = LocalPalette.current
    PageColumn {
        Text(stringResource(R.string.about_version, appVersion), color = palette.text, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.about_storage), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        SectionLabel(stringResource(R.string.welcome_note))
        Text(stringResource(R.string.welcome_note_sub), color = palette.subtext, fontSize = 14.sp)
        OutlinedButton(onClick = { showWelcome(state) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.show))
        }
        SectionLabel(stringResource(R.string.feedback))
        Text(stringResource(R.string.feedback_sub), color = palette.subtext, fontSize = 14.sp)
        Button(
            onClick = openFeedback,
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        ) { Text(stringResource(R.string.send_feedback)) }
    }
}

/**
 * Opens the welcome note -- or, when it was deleted, puts a fresh copy back
 * and opens that. Pressed again, it opens the same note rather than adding
 * another: the desktop's Welcome note → Show.
 */
fun showWelcome(state: AppState) {
    val uid = state.settings.opt(Settings.WELCOME_NOTE_UID) as? String
    state.load.notes.firstOrNull { uid != null && it.uid == uid }?.let {
        state.tab = Tab.NOTES
        state.settingsPage = null
        state.openNote(it.id)
        return
    }
    val welcome = Welcome.build()
    val spans = welcome.getJSONArray("formatting").let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
            .map { FormatSpan(it.getInt("start"), it.getInt("end"), it.getString("style")) }
    }
    val result = Notebook.createNote(state.load, welcome.getString("title"), welcome.getString("body"),
        formatting = spans, paragraphs = welcome.getJSONArray("paragraphs"))
    state.report(result) { ok ->
        state.note(ok.id)?.let { state.putSetting(Settings.WELCOME_NOTE_UID, it.uid) }
        state.tab = Tab.NOTES
        state.settingsPage = null
        state.openNote(ok.id)
    }
}
