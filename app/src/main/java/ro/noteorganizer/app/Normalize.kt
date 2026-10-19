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

    /** The desktop's ceilings: anti-bomb limits, not budgets. A repeat's
     *  own two live with the rule, in [Recurrence]. */
    const val MAX_CHECKLIST_ITEMS = 1000
    const val MAX_ATTACHMENTS = 200

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
        // A date written another readable way (day first, from an older code
        // or a hand edit) is stored in the one form the calendar places notes
        // by; one nobody can read is kept as it came.
        val date = out.getString("date")
        if (date.isNotEmpty() && Recurrence.parseDate(date) == null) {
            Recurrence.readDate(date)?.let { out.put("date", Recurrence.formatDate(it)) }
        }
        // A rule with no first day cannot be placed on a calendar: it becomes
        // a one-off note instead of silently disappearing.
        if (out.getJSONObject("recurrence").getString("freq").isNotEmpty() &&
            Recurrence.parseDate(out.getString("date")) == null) {
            out.put("recurrence", normalizeRecurrence(null))
        }
        // The text's own shape, as normalize_note leaves it: the line under
        // a table that closes the body (with_caret_line), the styles the
        // desktop keeps (normalize_formatting: known styles, sound offsets,
        // bold with italic folded, the ceiling), the line entries bounded by
        // the body's lines (normalize_paragraphs). Left as they came, an
        // imported note was in a form no load of either app gives it.
        val body = withCaretLine(out.getString("body"))
        out.put("body", body)
        out.put("formatting", Notebook.spansToJson(Exporting.normalizeFormatting(raw.opt("formatting"))))
        out.put("paragraphs", Templates.paragraphsJson(
            Exporting.normalizeParagraphs(raw.opt("paragraphs"), body.split("\n").size)))
        out.put("exceptions", cleanExceptions(out.getJSONArray("exceptions")))
        out.put("moved", cleanMoves(out.getJSONObject("moved"), out.getJSONArray("exceptions")))
        out.put("overrides", cleanOverrides(out.getJSONObject("overrides")))
        out.put("checklist", cleanChecklist(out.getJSONArray("checklist")))
        out.put("attachments", cleanAttachments(out.getJSONArray("attachments")))
        // The rescue copy is carried through, as the desktop carries it:
        // dropped here, "Restore previous" would promise what the file no
        // longer holds.
        (raw.opt("_prev") as? JSONObject)?.let { out.put("_prev", JSONObject(it.toString())) }
        return out
    }

    /**
     * The desktop's `with_caret_line`: a body whose last line is a table's
     * row gains the empty line the editor always draws under it, so the
     * note as stored and the note as shown are the same.
     */
    fun withCaretLine(body: String): String {
        if (body.isEmpty() || body.endsWith("\n")) return body
        val last = tableBlocks(body).lastOrNull() ?: return body
        return if (last.last == body.split("\n").size - 1) body + "\n" else body
    }

    /** The deleted days of a series: real dates only, each once, in order. */
    private fun cleanExceptions(days: JSONArray): JSONArray {
        val kept = sortedSetOf<String>()
        for (i in 0 until days.length()) {
            val day = days.opt(i) as? String ?: continue
            if (Recurrence.parseDate(day) != null) kept.add(day)
        }
        return JSONArray(kept.toList())
    }

    /**
     * Days held on another date: both real dates, never the same one, no two
     * days on one date, and no move of a deleted day. Capped as a rule's
     * days are: each is worked out on every calendar draw.
     */
    private fun cleanMoves(moved: JSONObject, exceptions: JSONArray): JSONObject {
        val deleted = (0 until exceptions.length()).map { exceptions.getString(it) }.toSet()
        val out = JSONObject()
        val heldOn = HashSet<String>()
        val pairs = moved.keys().asSequence().mapNotNull { orig -> (moved.opt(orig) as? String)?.let { orig to it } }
            .sortedWith(compareBy({ it.first }, { it.second }))
        for ((orig, new) in pairs) {
            if (Recurrence.parseDate(orig) != null && Recurrence.parseDate(new) != null && orig != new &&
                new !in heldOn && orig !in deleted) {
                out.put(orig, new)
                heldOn.add(new)
                if (out.length() >= Recurrence.MAX_OCCURRENCES) break
            }
        }
        return out
    }

    /** Each day's own edit, under a real date, in the shape a load gives it. */
    private fun cleanOverrides(overrides: JSONObject): JSONObject {
        val out = JSONObject()
        for (day in overrides.keys()) {
            if (Recurrence.parseDate(day) == null) continue
            val patch = overrides.opt(day) as? JSONObject ?: continue
            out.put(day, cleanOverride(patch))
        }
        return out
    }

    /**
     * The desktop's `clean_override`: only the fields a day may replace, as
     * text, a colour that cannot be drawn dropped -- not the whole edit. A
     * category this device lacks is the importer's to file under General,
     * as it files the note's own.
     */
    fun cleanOverride(patch: JSONObject): JSONObject {
        val out = JSONObject()
        for (key in Recurrence.OVERRIDABLE_FIELDS) {
            val value = patch.opt(key) as? String ?: continue
            if (key == "color") {
                val colour = Notebook.sanitizeColor(value)
                if (colour.isNotEmpty()) out.put(key, colour)
            } else {
                out.put(key, value)
            }
        }
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

    /** `normalize_recurrence`, which lives with the rest of the rule: [Recurrence.normalize]. */
    fun normalizeRecurrence(value: Any?): JSONObject = Recurrence.normalize(value)

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
