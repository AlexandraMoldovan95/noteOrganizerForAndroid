package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a note from OUTSIDE becomes before it joins the notebook, and the
 * two ways the desktop decides that two notes are the same.
 *
 * A backup's notes, and its Trash, arrive as JSON anyone can write. The
 * desktop sends every one of them through `normalize_note`, its single
 * choke point; the phone's import does the same with [normalizeNote], a
 * port of the parts that guard the file: every field present and of the
 * right type, an attachment's stored name flattened to a bare file name
 * (so no record can aim a later delete outside the attachments folder),
 * and the ceilings on lists a crafted file could inflate for free.
 */
object Normalize {

    /** The desktop's ceilings: anti-bomb limits, not budgets. */
    const val MAX_CHECKLIST_ITEMS = 1000
    const val MAX_ATTACHMENTS = 200
    const val MAX_RECURRENCE_INTERVAL = 1000
    const val MAX_OCCURRENCES = 5000

    /** NOTE_DEFAULTS, field for field, in the desktop's order. */
    private val TEXT_FIELDS = listOf("title", "body", "comments", "date", "color",
                                     "updated", "created")
    private val LIST_FIELDS = listOf("formatting", "attachments", "exceptions",
                                     "checklist", "paragraphs")
    private val MAP_FIELDS = listOf("overrides", "moved")

    /**
     * A note as `normalize_note` would hand it back, for the parts that
     * matter to a file another program will read: types, the attachment
     * names, the checklist and attachment ceilings, the repeat rule. Its
     * `id` is the caller's to give; its `uid` is kept, or minted when it
     * has none -- as the desktop mints one at its own door.
     */
    fun normalizeNote(raw: JSONObject): JSONObject {
        val out = JSONObject()
        val id = raw.opt("id")
        out.put("id", if (id is Number && id !is Double && id !is Float) id.toInt() else 0)
        val uid = (raw.opt("uid") as? String)?.trim().orEmpty()
        out.put("uid", uid.ifEmpty { java.util.UUID.randomUUID().toString() })
        for (field in TEXT_FIELDS) out.put(field, raw.opt(field) as? String ?: "")
        out.put("category", (raw.opt("category") as? String)?.ifEmpty { null } ?: "General")
        out.put("color", Notebook.sanitizeColor(out.optString("color")))
        for (field in LIST_FIELDS) out.put(field, raw.opt(field) as? JSONArray ?: JSONArray())
        for (field in MAP_FIELDS) out.put(field, raw.opt(field) as? JSONObject ?: JSONObject())
        out.put("pinned", raw.opt("pinned") == true)
        // A note's place in Custom order: not a default ("not placed yet"
        // is its own answer), kept only when it is a finite number.
        val order = raw.opt("order")
        if (order is Number && order.toDouble().isFinite()) out.put("order", order)
        out.put("recurrence", normalizeRecurrence(raw.opt("recurrence")))
        out.put("checklist", cleanChecklist(out.getJSONArray("checklist")))
        out.put("attachments", cleanAttachments(out.getJSONArray("attachments")))
        // The rescue copy is carried through, as the desktop carries it:
        // dropped here, "Restore previous" would promise what the file no
        // longer holds.
        (raw.opt("_prev") as? JSONObject)?.let { out.put("_prev", JSONObject(it.toString())) }
        return out
    }

    private fun cleanChecklist(items: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until items.length()) {
            val item = items.opt(i) as? JSONObject ?: continue
            out.put(JSONObject()
                .put("text", item.opt("text") as? String ?: "")
                .put("done", item.opt("done") == true))
            if (out.length() >= MAX_CHECKLIST_ITEMS) break
        }
        return out
    }

    /**
     * Each record kept to its two names, the stored one flattened to a
     * bare file name: "../../x" and a Windows path both become "x". A
     * record naming no file at all is dropped.
     */
    fun cleanAttachments(records: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until records.length()) {
            val att = records.opt(i) as? JSONObject ?: continue
            var stored = (att.opt("stored_as") as? String)?.let { bareFileName(it) }.orEmpty()
            var display = att.opt("filename") as? String ?: ""
            if (display.isEmpty()) display = stored
            if (stored.isEmpty() && display.isEmpty()) continue
            if (stored.isEmpty()) stored = bareFileName(display)
            if (stored.isEmpty()) continue
            out.put(JSONObject().put("filename", display).put("stored_as", stored))
            if (out.length() >= MAX_ATTACHMENTS) break
        }
        return out
    }

    /**
     * The desktop's `_bare_file_name`: the last part of a path, either
     * slash, without the trailing dots and spaces Windows ignores when it
     * opens a file.
     */
    fun bareFileName(name: String): String =
        name.replace('\\', '/').substringAfterLast('/').trimEnd('.', ' ')

    /**
     * `normalize_recurrence`: every field checked on its own, anything
     * unexpected degrading to "does not repeat" rather than to a crash.
     * The end date is kept only in its stored form, YYYY-MM-DD.
     */
    fun normalizeRecurrence(value: Any?): JSONObject {
        val out = JSONObject()
            .put("freq", "").put("interval", 1).put("weekdays", JSONArray())
            .put("month_days", JSONArray()).put("month_day_policy", "clamp")
            .put("end", "").put("count", 0)
        val rule = value as? JSONObject ?: return out
        val freq = rule.opt("freq") as? String
        out.put("freq", if (freq in listOf("daily", "weekly", "monthly", "yearly")) freq else "")
        out.put("interval", wholeNumber(rule.opt("interval"))?.coerceIn(1, MAX_RECURRENCE_INTERVAL) ?: 1)
        out.put("weekdays", sortedInts(rule.opt("weekdays"), 0..6))
        out.put("month_days", sortedInts(rule.opt("month_days"), 1..31))
        val policy = rule.opt("month_day_policy") as? String
        out.put("month_day_policy", if (policy == "skip") "skip" else "clamp")
        val end = rule.opt("end") as? String ?: ""
        out.put("end", if (Notebook.isDate(end)) end.trim() else "")
        out.put("count", wholeNumber(rule.opt("count"))?.coerceIn(0, MAX_OCCURRENCES) ?: 0)
        return out
    }

    /** `int(value)` as Python takes it: a whole number, or digits as text. */
    private fun wholeNumber(value: Any?): Int? = when (value) {
        is Boolean -> if (value) 1 else 0
        is Int -> value
        is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is Number -> value.toDouble().toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun sortedInts(value: Any?, range: IntRange): JSONArray {
        val list = value as? JSONArray ?: return JSONArray()
        val kept = sortedSetOf<Int>()
        for (i in 0 until list.length()) {
            val v = list.opt(i)
            if (v is Int && v in range) kept.add(v)
            else if (v is Long && v in range.first.toLong()..range.last.toLong()) kept.add(v.toInt())
        }
        return JSONArray(kept.toList())
    }

    // ---- the same note? ----

    /** Any JSON value as text that compares by meaning, keys in order. */
    private fun stable(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted()
            .joinToString(",", "{", "}") { "\"$it\":" + stable(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { stable(value.opt(it)) }
        is String -> JSONObject.quote(value)
        is Number -> if (value.toDouble() == Math.floor(value.toDouble()) &&
            !value.toDouble().isInfinite()) value.toLong().toString() else value.toString()
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }

    private fun checklistSignature(value: Any?): String {
        val list = value as? JSONArray ?: return "[]"
        val out = ArrayList<String>()
        for (i in 0 until list.length()) {
            val item = list.opt(i) as? JSONObject ?: continue
            out.add(stable(JSONArray().put((item.opt("text") as? String)?.trim() ?: "")
                .put(item.opt("done") == true)))
        }
        return out.joinToString(",", "[", "]")
    }

    private fun recurrenceSignature(value: Any?): String {
        if (value !is JSONObject) return ""
        val rule = normalizeRecurrence(value)
        if (rule.optString("freq").isEmpty()) return ""
        return stable(rule)
    }

    private fun attachmentSignature(value: Any?): String {
        val list = value as? JSONArray ?: return "[]"
        val names = ArrayList<String>()
        for (i in 0 until list.length()) {
            val att = list.opt(i) as? JSONObject ?: continue
            names.add(att.opt("stored_as") as? String ?: "")
        }
        return names.sorted().joinToString("\u0000")
    }

    private fun stripCopySuffix(title: String): String {
        var t = title.trim()
        while (t.lowercase().endsWith("(copy)")) t = t.dropLast("(copy)".length).trimEnd()
        return t
    }

    private fun squeeze(text: String): String = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ")

    private fun str(note: JSONObject, key: String): String = note.opt(key) as? String ?: ""

    /**
     * The desktop's `_dup_key`: when two notes look like the same note to
     * a person. Title, body and comments compared without case or extra
     * whitespace, a trailing "(copy)" ignored; the category, the checklist
     * and the repeat rule exactly. The date, the colour, the files and the
     * pin are not part of it, on purpose.
     */
    fun dupKey(note: JSONObject): String = listOf(
        squeeze(stripCopySuffix(str(note, "title"))).lowercase(),
        squeeze(str(note, "body")).lowercase(),
        squeeze(str(note, "comments")).lowercase(),
        str(note, "category"),
        checklistSignature(note.opt("checklist")),
        recurrenceSignature(note.opt("recurrence")),
    ).joinToString("\u0001")

    /**
     * The desktop's `content_signature`: THE identity of a note's content,
     * strict -- everything but its id, uid and updated stamp. Asked where
     * something is skipped with nobody asked, such as a backup's Trash
     * entry that is already in this Trash.
     */
    fun contentSignature(note: JSONObject): String = listOf(
        str(note, "title").trim(),
        str(note, "body").trim(),
        str(note, "comments").trim(),
        (note.opt("category") ?: "").toString(),
        (note.opt("date") ?: "").toString(),
        checklistSignature(note.opt("checklist")),
        attachmentSignature(note.opt("attachments")),
        recurrenceSignature(note.opt("recurrence")),
        stable(note.opt("formatting")),
        stable(note.opt("paragraphs")),
        stable(note.opt("overrides")),
        stable(note.opt("exceptions")),
        stable(note.opt("moved")),
        (note.opt("pinned") == true).toString(),
        str(note, "color").trim(),
    ).joinToString("\u0001")
}
