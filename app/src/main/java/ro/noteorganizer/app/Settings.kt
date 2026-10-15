package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * `settings.json`, read and written the way the desktop reads and writes
 * it -- and shared with it, since a backup carries the file both ways.
 *
 * **A partial reader must not become a total writer**, here as in the
 * notebook: the desktop keeps a dozen keys this app never looks at (the
 * calendar's painted days, the spelling dictionary, which groups are
 * folded), and a write that rebuilt the file from what the phone knows
 * would delete them all. So every write edits the object that was READ,
 * one key at a time, and everything else survives byte for byte.
 *
 * The keys the two programs share are the desktop's own names, with the
 * desktop's values. The few that only a phone has a use for are prefixed
 * `phone_`, so whoever opens a shared file can tell whose they are; the
 * desktop keeps keys it does not know, so they come back unharmed from a
 * round trip through it.
 */
object Settings {

    // Shared with the desktop, by the desktop's names.
    const val THEME = "theme"
    const val USERNAME = "username"
    const val RETENTION_DAYS = "trash_retention_days"
    const val CATEGORIES = "categories"
    const val NOTE_SORT = "note_sort"
    const val NOTE_FILTER = "note_filter"
    const val WELCOME_OFFERED = "welcome_offered"
    const val WELCOME_NOTE_UID = "welcome_note_uid"
    const val LAST_EXPORT_AT = "last_export_at"

    // The phone's own.
    /** Whether the theme follows the phone's dark mode. */
    const val PHONE_FOLLOWS_PHONE = "phone_theme_follows_phone"
    /** The light and the dark theme it follows between. */
    const val PHONE_LIGHT_THEME = "phone_theme_light"
    const val PHONE_DARK_THEME = "phone_theme_dark"

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
            JSONObject(file.readText(Charsets.UTF_8))
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
    fun put(load: Load, key: String, value: Any?): SaveResult {
        val candidate = JSONObject(load.raw.toString())
        if (value == null) candidate.remove(key) else candidate.put(key, value)
        val problem = Files.writeAtomic(load.file, candidate.toString(2))
        if (problem != null) return SaveResult.Failed(problem)
        if (value == null) load.raw.remove(key) else load.raw.put(key, value)
        return SaveResult.Ok(0)
    }

    // ---- Reading, each with the desktop's own fallback ----

    fun username(raw: JSONObject): String = (raw.opt(USERNAME) as? String)?.trim() ?: ""

    /**
     * The theme to draw with. When it follows the phone, the dark or the
     * light one the person picked for that mode; otherwise the one stored
     * under the desktop's own key. Every name is checked, so a theme a
     * later version removed falls back instead of drawing nothing.
     */
    fun theme(raw: JSONObject, phoneIsDark: Boolean): String {
        if (followsPhone(raw)) {
            return if (phoneIsDark) darkTheme(raw) else lightTheme(raw)
        }
        return Themes.valid(raw.opt(THEME) as? String)
    }

    /** On unless the person turned it off: what Android apps do. */
    fun followsPhone(raw: JSONObject): Boolean = raw.opt(PHONE_FOLLOWS_PHONE) != false

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
     * The retention window, clamped the way the desktop clamps it: a
     * hand-edited 0, or -3, or "forever" must not make anything look due
     * for deletion. Anything unusable reads as the default.
     */
    fun retentionDays(raw: JSONObject): Int {
        // A whole number only, as `isinstance(days, int)` asks. 7.5 is not
        // one, and neither is 7.0. Android's parser reads those as a
        // Double, the computer's test library as a BigDecimal: both are
        // refused by name, so the answer does not depend on the parser.
        val days = raw.opt(RETENTION_DAYS)
        if (days !is Number || days is Double || days is Float ||
            days is java.math.BigDecimal
        ) {
            return TRASH_RETENTION_DAYS
        }
        return days.toLong().coerceIn(1, 365).toInt()
    }

    /**
     * The category map, as the desktop's `load_categories` builds it: the
     * saved map when there is one (well-formed names and `#hex` colours
     * only, at most [MAX_CATEGORIES], names no longer than
     * [MAX_CATEGORY_NAME] -- a longer one is dropped, never shortened, or
     * every note filed under it would stop matching); the starting set
     * only when nothing usable was saved; and General always present and
     * always first.
     *
     * Then the notes are believed, as `adopt_categories_of` believes them:
     * a name a note (or one day of a repeating note) is filed under that
     * the map does not hold comes back with a colour from the default
     * palette. Deleting settings.json, or copying only the notes to a new
     * phone, must not empty somebody's filing into General.
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
                val name = key.trim()
                if (name.isEmpty() || !Notebook.isHexColor(colour)) continue
                if (name.length > MAX_CATEGORY_NAME) continue
                result[name] = colour.trim()
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
                if (name.length > MAX_CATEGORY_NAME || map.size >= MAX_CATEGORIES) continue
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
        if (name.length > MAX_CATEGORY_NAME) {
            return "A category name can be at most $MAX_CATEGORY_NAME characters long, " +
                "and this one has ${name.length}."
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
