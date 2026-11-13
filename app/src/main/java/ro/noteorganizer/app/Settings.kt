package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * `settings.json`, read and written as the desktop reads and writes it, and
 * shared with it, since a backup carries the file both ways. **A partial
 * reader must not become a total writer:** the desktop keeps a dozen keys
 * this app never looks at, so every write edits the object that was READ,
 * one key at a time, and everything else survives byte for byte. Shared
 * keys are the desktop's names and values; the phone's own are prefixed
 * `phone_`, and come back unharmed from a round trip through the desktop.
 */
object Settings {

    // Shared with the desktop, by the desktop's names.
    const val THEME = "theme"
    const val USERNAME = "username"
    const val RETENTION_DAYS = "trash_retention_days"
    const val CATEGORIES = "categories"
    const val NOTE_SORT = "note_sort"
    const val NOTE_FILTER = "note_filter"
    const val GROUP_BY_CATEGORY = "group_by_category"
    const val COLLAPSED_GROUPS = "collapsed_groups"
    const val WELCOME_OFFERED = "welcome_offered"
    const val WELCOME_NOTE_UID = "welcome_note_uid"
    const val LAST_EXPORT_AT = "last_export_at"

    // The phone's own.
    /**
     * Whether the theme follows the phone's dark mode: once the app's way, on
     * by default. The choice is gone; a notebook that still follows is settled
     * once ([AppState.settleTheme]) on the theme it wears, and this is then
     * false for good.
     */
    const val PHONE_FOLLOWS_PHONE = "phone_theme_follows_phone"
    /** The last light and the last dark theme worn: where Light | Dark goes back to. */
    const val PHONE_LIGHT_THEME = "phone_theme_light"
    const val PHONE_DARK_THEME = "phone_theme_dark"
    /**
     * Set when settings.json could not be read: the Trash's window chosen
     * before is lost with it, and nothing is purged until one is chosen
     * again ([purgeWindow]). Without it, the fresh file written next would
     * read as "never chosen", and the next start would purge at 30 days
     * what the person had kept for 90.
     */
    const val PHONE_TRASH_WINDOW_UNKNOWN = "phone_trash_window_unknown"

    /**
     * `false` when the person turned off, at Settings → Backup, the app's
     * part in Android's own backup to their Google account; on while it is
     * missing, as it always was (the owner's call, 2026-10-27).
     */
    const val PHONE_GOOGLE_BACKUP = "phone_google_backup"

    /**
     * Whether Android's backup may take the app's data: not when the person
     * turned it off, nor when settings.json cannot be read (a copy not made
     * can be made later, one made against their word cannot be taken back),
     * nor when it holds no answer and a restore's off mark lies beside it
     * ([GOOGLE_BACKUP_OFF_MARK]), the answer until the first start writes it
     * in ([takeOffMark]): read alone, a phone restored and unopened read as on.
     */
    fun googleBackupOn(load: Load): Boolean {
        if (load.problem != null) return false
        if (load.raw.has(PHONE_GOOGLE_BACKUP)) return load.raw.opt(PHONE_GOOGLE_BACKUP) != false
        return load.file.parentFile?.let { File(it, GOOGLE_BACKUP_OFF_MARK).isFile } != true
    }

    /**
     * What Android's full backup of the notebook in [dir] may take, decided
     * here so the agent and Settings → Backup answer by one rule
     * ([googleBackupOn]). On: what Android's rules take, and a restore's mark
     * deleted, or it would turn off what the person turned on. Off: only the
     * mark, written again; when it cannot be written, nothing at all.
     */
    fun forFullBackup(dir: File): FullBackup {
        val mark = File(dir, GOOGLE_BACKUP_OFF_MARK)
        if (googleBackupOn(load(dir))) {
            mark.delete()
            return FullBackup.Everything
        }
        return if (Files.writeAtomic(mark, offMark()) == null) FullBackup.OnlyMark(mark) else FullBackup.Nothing
    }

    /** [forFullBackup]'s answer. */
    sealed class FullBackup {
        object Everything : FullBackup()
        data class OnlyMark(val mark: File) : FullBackup()
        object Nothing : FullBackup()
    }

    /**
     * The one file Android's backup is handed while the switch is off
     * (`NotesBackupAgent`), beside the notebook: it says so, and nothing
     * else, replacing the copy made while on (smaller lessons: the backup's
     * off mark); a restore brings the answer back ([takeOffMark]).
     */
    const val GOOGLE_BACKUP_OFF_MARK = "phone_google_backup_off.json"

    /** What [GOOGLE_BACKUP_OFF_MARK] holds. */
    fun offMark(): String = JSONObject().put(PHONE_GOOGLE_BACKUP, false).toString()

    /**
     * After a restore from a backup made while the switch was off, the
     * answer it carried: off, unless this notebook's settings already hold
     * one. The mark is then deleted; the next backup made while off writes
     * it again.
     */
    fun takeOffMark(dir: File, load: Load?) {
        val mark = File(dir, GOOGLE_BACKUP_OFF_MARK)
        if (load == null || !mark.isFile) return
        if (!load.raw.has(PHONE_GOOGLE_BACKUP) && put(load, PHONE_GOOGLE_BACKUP, false) !is SaveResult.Ok) return
        mark.delete()
    }

    /** The desktop's ceilings on what a category map may hold. */
    const val MAX_CATEGORIES = 200
    const val MAX_CATEGORY_NAME = 80

    /** The desktop's default window, and its clamp on a hand-edited one. */
    const val TRASH_RETENTION_DAYS = 30

    /**
     * What a read produced. [raw] is never null: a file that is missing,
     * or that could not be read and was kept aside, reads as `{}` -- the
     * desktop's answer too. [problem] says so when it was not simply
     * missing.
     */
    data class Load(
        val file: File,
        val raw: JSONObject,
        val problem: String? = null,
    )

    fun load(dir: File): Load {
        val file = File(dir, "settings.json")
        if (!file.isFile) return Load(file, JSONObject())
        val parsed = try {
            Files.parseOwn(file) { JSONObject(it) }
        } catch (e: Exception) {
            null
        }
        if (parsed != null) return Load(file, parsed)
        // Kept aside BEFORE the first write can replace it: the bytes are
        // still somebody's preferences, and their categories' colours.
        val kept = Files.preserveCorrupt(file)
        return Load(
            file, JSONObject(),
            if (kept != null) "settings.json could not be read; a copy was kept as ${kept.name}"
            else "settings.json could not be read, and could not be copied aside",
        )
    }

    /**
     * Sets one key and writes the file. On failure nothing in [load]
     * changes, so the screen never shows a setting the disk does not have.
     */
    fun put(load: Load, key: String, value: Any?): SaveResult = putAll(load, mapOf(key to value))

    /** Several keys in one write: all of them, or -- on failure -- none. */
    fun putAll(load: Load, changes: Map<String, Any?>): SaveResult {
        val candidate = JSONObject(load.raw.toString())
        for ((key, value) in changes) if (value == null) candidate.remove(key) else candidate.put(key, value)
        val problem = Files.writeAtomic(load.file, candidate.toString(2))
        if (problem != null) return SaveResult.Failed(problem)
        for ((key, value) in changes) if (value == null) load.raw.remove(key) else load.raw.put(key, value)
        return SaveResult.Ok(0)
    }

    // ---- Reading, each with the desktop's own fallback ----

    fun username(raw: JSONObject): String = (raw.opt(USERNAME) as? String)?.let(PyText::strip) ?: ""

    /** The categories folded shut in a grouped list: names, as the desktop keeps them. */
    fun collapsedGroups(raw: JSONObject): Set<String> {
        val list = raw.opt(COLLAPSED_GROUPS) as? org.json.JSONArray ?: return emptySet()
        return (0 until list.length()).mapNotNull { list.opt(it) as? String }.toSet()
    }

    /**
     * The theme to draw with: the one stored under the desktop's own key.
     * A notebook not yet settled ([followsPhone]) still wears the one for
     * the phone's mode, as it did, so settling it changes nothing on the
     * screen. Every name is checked, so a theme a later version removed
     * falls back instead of drawing nothing.
     */
    fun theme(raw: JSONObject, phoneIsDark: Boolean): String {
        if (followsPhone(raw)) {
            return if (phoneIsDark) darkTheme(raw) else lightTheme(raw)
        }
        return Themes.valid(raw.opt(THEME) as? String)
    }

    /** Not yet settled: on unless written false, as following was by default. */
    fun followsPhone(raw: JSONObject): Boolean = raw.opt(PHONE_FOLLOWS_PHONE) != false

    /**
     * The keys written when [name] is chosen: the theme itself, under the
     * desktop's key; the last of its mode, for Light | Dark to go back to;
     * and that the phone is followed no more.
     */
    fun choosing(name: String): Map<String, Any?> = mapOf(
        THEME to name,
        (if (Themes.isDark(name)) PHONE_DARK_THEME else PHONE_LIGHT_THEME) to name,
        PHONE_FOLLOWS_PHONE to false,
    )

    /** Where Light | Dark moves the app: the last theme worn in that mode. */
    fun themeForMode(raw: JSONObject, dark: Boolean): String = if (dark) darkTheme(raw) else lightTheme(raw)

    fun lightTheme(raw: JSONObject): String {
        val name = raw.opt(PHONE_LIGHT_THEME) as? String
        if (name != null && name in Themes.LIGHT) return name
        val chosen = raw.opt(THEME) as? String
        return if (chosen != null && chosen in Themes.LIGHT) chosen else "light"
    }

    fun darkTheme(raw: JSONObject): String {
        val name = raw.opt(PHONE_DARK_THEME) as? String
        if (name != null && name in Themes.DARK) return name
        val chosen = raw.opt(THEME) as? String
        return if (chosen != null && chosen in Themes.DARK) chosen else "dark"
    }

    /**
     * The retention window as the Trash page shows it: a hand-edited 0, or
     * -3, or "forever" reads as the default, and a whole number is held to
     * 1..365. What the start's purge goes by is [purgeWindow], which, as the
     * desktop's `trash_retention_days`, refuses an unusable window.
     */
    fun retentionDays(raw: JSONObject): Int {
        // A whole number only, as `isinstance(days, int)` asks: 7.5 and 7.0 are
        // refused by type (Android's parser reads a Double, the test library a
        // BigDecimal), so the answer does not depend on the parser.
        val days = raw.opt(RETENTION_DAYS)
        if (days !is Number || days is Double || days is Float ||
            days is java.math.BigDecimal
        ) {
            return TRASH_RETENTION_DAYS
        }
        return days.toLong().coerceIn(1, 365).toInt()
    }

    /**
     * The window the start-up purge may go by, or null when it is not known,
     * and then nothing is purged (a note kept too long can still go, one
     * deleted too soon cannot come back): the file unreadable ([Load.problem],
     * then [PHONE_TRASH_WINDOW_UNKNOWN]), or not a whole number from 1 to 365
     * -- a clamp would read 0 as one day. Never chosen, it is the default.
     */
    fun purgeWindow(load: Load): Int? {
        if (load.problem != null || load.raw.opt(PHONE_TRASH_WINDOW_UNKNOWN) == true) return null
        if (!load.raw.has(RETENTION_DAYS)) return TRASH_RETENTION_DAYS
        return chosenWindow(load.raw)
    }

    /**
     * The window [raw] holds as a person chooses one -- a whole number of
     * days from 1 to 365 -- or null: absent, "forever", 7.5, 0.
     */
    fun chosenWindow(raw: JSONObject): Int? {
        val days = raw.opt(RETENTION_DAYS)
        if (days !is Number || days is Double || days is Float || days is java.math.BigDecimal) return null
        return days.toLong().takeIf { it in 1..365 }?.toInt()
    }

    /**
     * Whether [load] has never had a window chosen: no value, and none lost
     * with a file that could not be read -- that one was chosen, only not
     * known.
     */
    fun windowNeverChosen(load: Load): Boolean =
        load.problem == null && !load.raw.has(RETENTION_DAYS) && load.raw.opt(PHONE_TRASH_WINDOW_UNKNOWN) != true

    /**
     * The category map, as the desktop's `load_categories` builds it: the
     * saved one (well-formed names and `#hex` colours, at most
     * [MAX_CATEGORIES], a name past [MAX_CATEGORY_NAME] dropped, never cut),
     * else the starting set, General always first. Then, as
     * `adopt_categories_of`, a name a note or a day is filed under comes back
     * with a default colour: settings.json lost must not empty the filing.
     */
    fun categories(raw: JSONObject, notes: JSONArray? = null): LinkedHashMap<String, String> {
        val saved = raw.opt(CATEGORIES) as? JSONObject
        val result = LinkedHashMap<String, String>()
        if (saved != null) {
            var seen = 0
            for (key in saved.keys()) {
                if (seen >= MAX_CATEGORIES) break
                seen += 1
                val colour = saved.opt(key) as? String ?: continue
                // Python's strip, as categories.py reads a name: trim() kept U+0085.
                val name = PyText.strip(key)
                if (name.isEmpty() || !Notebook.isHexColor(colour)) continue
                if (characters(name) > MAX_CATEGORY_NAME) continue
                result[name] = Notebook.sanitizeColor(colour)
            }
        }
        if (result.isEmpty()) result.putAll(Themes.DEFAULT_CATEGORY_COLORS)
        val general = Themes.PROTECTED_CATEGORY
        val ordered = LinkedHashMap<String, String>()
        ordered[general] = result[general] ?: Themes.DEFAULT_CATEGORY_COLORS.getValue(general)
        for ((name, colour) in result) if (name != general) ordered[name] = colour
        if (notes != null) adoptCategoriesOf(ordered, notes)
        return ordered
    }

    /**
     * A name's length as the desktop's `len` counts it, in characters: in
     * UTF-16 units an emoji counted twice, and a 78-character name with a
     * few was dropped here that the desktop keeps -- its notes refiled
     * under General.
     */
    private fun characters(name: String): Int = name.codePointCount(0, name.length)

    private fun adoptCategoriesOf(map: LinkedHashMap<String, String>, notes: JSONArray) {
        val palette = Themes.DEFAULT_CATEGORY_COLORS.values.toList()
        for (i in 0 until notes.length()) {
            val note = notes.opt(i) as? JSONObject ?: continue
            val names = ArrayList<Any?>()
            names.add(note.opt("category"))
            (note.opt("overrides") as? JSONObject)?.let { overrides ->
                for (day in overrides.keys()) {
                    names.add((overrides.opt(day) as? JSONObject)?.opt("category"))
                }
            }
            for (name in names) {
                // Only a name the note would match exactly: a padded name
                // adopted in stripped form would still miss.
                if (name !is String || name.isEmpty() || name != name.trim()) continue
                if (name in map) continue
                if (characters(name) > MAX_CATEGORY_NAME || map.size >= MAX_CATEGORIES) continue
                map[name] = palette[map.size % palette.size]
            }
        }
    }

    /**
     * Why [name] cannot join the map, or null when it can: the rule the
     * loader applies on the next start, so a category that works today
     * does not vanish tomorrow. The desktop's `category_name_refusal`.
     */
    fun categoryNameRefusal(name: String, current: Map<String, String>, adding: Boolean = true): String? {
        if (characters(name) > MAX_CATEGORY_NAME) {
            return "A category name can be at most $MAX_CATEGORY_NAME characters long, " +
                "and this one has ${characters(name)}."
        }
        if (adding && current.size >= MAX_CATEGORIES) {
            return "You already have $MAX_CATEGORIES categories, which is the most the " +
                "app keeps. Delete or rename one first."
        }
        return null
    }

    /** The category map as settings.json stores it. */
    fun categoriesJson(map: Map<String, String>): JSONObject {
        val out = JSONObject()
        for ((name, colour) in map) out.put(name, colour)
        return out
    }
}
