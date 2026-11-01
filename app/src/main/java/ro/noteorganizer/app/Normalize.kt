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

    /**
     * The desktop's two ceilings on styles (`formatting.py`): at most this
     * many covering any one line, and this many line-ranges in the whole
     * note -- the shape that stopped Tk finishing a draw, and a note is
     * saved before it is drawn. No note a person writes comes near.
     */
    const val MAX_SPANS_PER_LINE = 500
    const val MAX_LINE_RANGES = 20000

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
     * has none -- as the desktop mints one at its own door. [daysBack]
     * false leaves a turned-off series' days where the copy holds them, for
     * a caller that asks the move itself ([Notebook.daysBackAsRead]).
     */
    fun normalizeNote(raw: JSONObject, daysBack: Boolean = true): JSONObject {
        val out = JSONObject()
        val id = raw.opt("id")
        // A whole number an Int holds, as it is; past one, no id, never one
        // cut down onto another note's (4,294,967,301 read as 5: the final
        // audit's tenth round). The load gives it a new number ([Organizing.repairIds]).
        val whole = if (id is Int || id is Long || id is Short || id is Byte) (id as Number).toLong() else null
        out.put("id", if (whole != null && whole in Int.MIN_VALUE..Int.MAX_VALUE) whole.toInt() else 0)
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
        out.put("formatting", Notebook.spansToJson(clampSpansPerLine(body, Exporting.normalizeFormatting(raw.opt("formatting")))))
        out.put("paragraphs", Templates.paragraphsJson(
            Exporting.normalizeParagraphs(raw.opt("paragraphs"), body.split("\n").size)))
        out.put("exceptions", cleanExceptions(out.getJSONArray("exceptions")))
        out.put("moved", cleanMoves(out.getJSONObject("moved"), out.getJSONArray("exceptions")))
        out.put("overrides", cleanOverrides(out.getJSONObject("overrides")))
        dropHiddenMoves(out)
        out.put("checklist", cleanChecklist(out.getJSONArray("checklist")))
        out.put("attachments", cleanAttachments(out.getJSONArray("attachments")))
        // The rescue copy is carried through, as the desktop carries it:
        // dropped here, "Restore previous" would promise what the file no
        // longer holds. Shaped as the desktop shapes it, not copied whole:
        // a restore puts it straight back into the note.
        (raw.opt("_prev") as? JSONObject)?.let { out.put("_prev", shapePrevious(it)) }
        // A copy's days set aside, kept until the notebook makes them notes
        // of their own ([Notebook.settleAside]), as the desktop keeps them.
        Notebook.shapedAside(raw.opt("_set_aside"))?.let { out.put("_set_aside", it) }
        // A turned-off series' days kept only in its copy come into the
        // note, as the desktop's normalize_note brings them (round six).
        if (daysBack) Notebook.daysBackFromCopy(out)
        return out
    }

    /**
     * A rescue copy as `normalize_note` shapes `_prev`: its words as text,
     * its styles held to the line ceilings against its own text, its line
     * entries sound, and of anything else only what an update from a backup
     * replaces, each shaped by the note's own rules -- the checklist's
     * thousand rows, a repeat rule, the days' edits. An absent key stays
     * absent: that tells an update's copy from a shrink's. Copied whole, a
     * backup's copy brought fifty thousand checklist rows past every
     * ceiling, and a restore wrote them into the note (ISSUES_LOG, "A
     * rescue copy let in whole").
     */
    fun shapePrevious(prev: JSONObject): JSONObject {
        val body = prev.opt("body") as? String ?: ""
        val out = JSONObject()
        out.put("title", prev.opt("title") as? String ?: "")
        out.put("body", body)
        out.put("comments", prev.opt("comments") as? String ?: "")
        out.put("formatting", Notebook.spansToJson(clampSpansPerLine(body, Exporting.normalizeFormatting(prev.opt("formatting")))))
        out.put("paragraphs", Templates.paragraphsJson(Exporting.normalizeParagraphs(prev.opt("paragraphs"), null)))
        val extra = JSONObject()
        for (key in Notebook.UPDATABLE_FROM_BACKUP) {
            if (key !in Notebook.PREV_TEXT_FIELDS && prev.has(key)) extra.put(key, prev.opt(key))
        }
        if (extra.length() > 0) {
            val shaped = normalizeNote(extra)
            for (key in extra.keys()) out.put(key, shaped.opt(key))
        }
        return out
    }

    /**
     * The desktop's `clamp_spans_per_line`: the styles that would pile too
     * deep on one line dropped, counted by the lines a style COVERS, not
     * where it starts -- spans each starting on another line and running to
     * the end all lay on the last one -- and the whole note's line-ranges
     * capped as well. [spans] unchanged unless a ceiling is broken.
     */
    fun clampSpansPerLine(body: String, spans: List<FormatSpan>): List<FormatSpan> {
        if (spans.isEmpty()) return spans
        val cps = codePointsOf(body)
        val breaks = cps.indices.filter { cps[it] == '\n'.code }
        if (breaks.isEmpty()) return if (spans.size <= MAX_SPANS_PER_LINE) spans else spans.take(MAX_SPANS_PER_LINE)
        // Python's bisect_left: the first break at or after [at].
        fun lineOf(at: Int): Int = java.util.Collections.binarySearch(breaks, at).let { if (it >= 0) it else -it - 1 }
        val kept = ArrayList<FormatSpan>()
        val covered = HashMap<Int, Int>()
        val full = java.util.TreeSet<Int>()
        var drawn = 0
        for (span in spans) {
            val first = lineOf(span.start)
            val last = lineOf(maxOf(span.start, span.end - 1))
            val width = last - first + 1
            if (drawn + width > MAX_LINE_RANGES) continue
            val crossed = full.ceiling(first)
            if (crossed != null && crossed <= last) continue
            for (line in first..last) {
                val seen = (covered[line] ?: 0) + 1
                covered[line] = seen
                if (seen >= MAX_SPANS_PER_LINE) full.add(line)
            }
            drawn += width
            kept.add(span)
        }
        return kept
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

    /**
     * Whether two of a note's texts say the same, the line under a closing
     * table counted on both sides or neither: a day's text is stored with it
     * ([cleanOverride]), the series' as typed on this phone, and a day that
     * said what its series said read as text of its own -- cut off from the
     * series, its colour alone taken for words (the final audit's fourteenth
     * round).
     */
    fun sameBody(a: Any?, b: Any?): Boolean =
        if (a is String && b is String) withCaretLine(a) == withCaretLine(b) else a == b

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

    /**
     * Each move of [note] onto a date its rule now gives -- the day drawn
     * nowhere there, a delete there taking it -- dropped; its words stay
     * where they showed, on the rule's day there. Words never move on their
     * own (the owner's choice, the seventeenth round: round sixteen's moving
     * them wrote one day's text over another's). Not off a date whose own day
     * stays deleted or moved away; each chain walked once. The desktop's
     * `drop_hidden_moves`, at every read -- the raw notes too
     * ([Notebook.settleAsRead]) -- and in every save, update and restore.
     */
    internal fun dropHiddenMoves(note: JSONObject) {
        val moved = note.opt("moved") as? JSONObject ?: return
        if (moved.length() == 0 || !Recurrence.isRecurring(note)) return
        val deleted = (note.opt("exceptions") as? JSONArray)
            ?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } }.orEmpty().toSet()
        val links = LinkedHashMap<String, String>()
        for (orig in moved.keys().asSequence().toList().sorted()) (moved.opt(orig) as? String)?.let { links[orig] = it }
        val gives = Recurrence.ruleDaysAmong(note, links.values)
        val goes = linksLeaving(links, { moved.has(it) }) { new -> new in gives && new !in deleted }
        for (orig in links.keys) if (goes[orig] == true) moved.remove(orig)
    }

    /**
     * Which of [links] -- a day to the date it was moved onto -- leave that
     * date: [free] says the date is the rule's own and not deleted, and the
     * day whose date it is must leave it too, when it was moved ([origin]).
     * Each chain walked once, [free] asked once a link: a pass a link took
     * the square of a chain's length (the sixteenth round).
     */
    internal fun linksLeaving(links: Map<String, String>, origin: (String) -> Boolean,
                              free: (String) -> Boolean): Map<String, Boolean> {
        val goes = HashMap<String, Boolean>()
        for (start in links.keys) {
            val path = ArrayList<String>()
            val onPath = HashSet<String>()
            var at = start
            while (at in links && at !in goes && at !in onPath) {
                path.add(at)
                onPath.add(at)
                at = links.getValue(at)
            }
            // The chain's end, a day already settled, or a loop -- none of
            // whose days is ever free to come back to.
            var after = when {
                at in onPath -> false
                at in goes -> goes.getValue(at)
                else -> !origin(at)
            }
            for (orig in path.asReversed()) {
                after = after && free(links.getValue(orig))
                goes[orig] = after
            }
        }
        return goes
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
     * as it files the note's own. A day's text ending in a table gains the
     * line under it ([withCaretLine]), as the desktop's does: without it the
     * same day read as changed in an import, and words added under it were
     * glued onto its table (the final audit's thirteenth round).
     */
    fun cleanOverride(patch: JSONObject): JSONObject {
        val out = JSONObject()
        for (key in Recurrence.OVERRIDABLE_FIELDS) {
            val value = patch.opt(key) as? String ?: continue
            if (key == "color") {
                val colour = Notebook.sanitizeColor(value)
                if (colour.isNotEmpty()) out.put(key, colour)
            } else {
                out.put(key, if (key == "body") withCaretLine(value) else value)
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
            // The label agrees with the file that opens, and hides nothing.
            out.put(JSONObject().put("filename", attachmentDisplayName(display, stored)).put("stored_as", stored))
            if (out.length() >= MAX_ATTACHMENTS) break
        }
        return out
    }

    /**
     * What changes how a name reads without being seen -- controls, bidi
     * marks and overrides ("Invoice<U+202E>fdp.exe" shows as
     * "Invoiceexe.pdf"), line separators, the BOM: the desktop's
     * HIDDEN_IN_NAMES, out of every name shown and every name written.
     */
    internal val HIDDEN_IN_NAMES = Regex(
        "[\\u0000-\\u001f\\u007f-\\u009f\\u061c\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069\\u2028\\u2029\\ufeff]")

    /** The last dot and what follows it, as typed; "" when there is none. */
    private fun typeSuffix(name: String): String {
        val trimmed = name.trimEnd('.', ' ')
        val dot = trimmed.lastIndexOf('.')
        return if (dot >= 0) trimmed.substring(dot) else ""
    }

    /**
     * The kind of file [name] is as Windows opens it -- what follows the
     * last dot, lower-case, once trailing dots and spaces are gone -- or "":
     * the desktop's `file_type_of`. ".rdp" is an .rdp file.
     */
    fun fileTypeOf(name: String): String =
        typeSuffix(name.replace('\\', '/').substringAfterLast('/')).lowercase(java.util.Locale.ROOT)

    /**
     * The name a person is shown for an attachment: [filename], a label a
     * backup can write over any file, cleaned of [HIDDEN_IN_NAMES] and made
     * to end in the type of [storedAs], the file that opens -- "Holiday
     * photo.jpg" over an .html shows "Holiday photo.html". The app's own
     * records already agree and show as they were. The desktop's
     * `attachment_display_name` (a7495d5).
     */
    fun attachmentDisplayName(filename: String?, storedAs: String?): String {
        val stored = storedAs.orEmpty()
        val storedName = HIDDEN_IN_NAMES.replace(stored.replace('\\', '/').substringAfterLast('/'), "")
        var name = HIDDEN_IN_NAMES.replace(filename.orEmpty(), "")
        if (name.trim('.', ' ').isEmpty()) name = storedName
        if (stored.isEmpty()) return name
        // Cleaned too: a type that held a hidden character is no type.
        val real = HIDDEN_IN_NAMES.replace(fileTypeOf(stored), "")
        if (fileTypeOf(name) == real) return name
        var stem = name.trimEnd('.', ' ')
        if (real.isNotEmpty()) {
            stem = stem.substring(0, stem.length - typeSuffix(stem).length)
        } else {
            // A file with no type shows none: "photo.tar.jpg" over one that
            // has none would still read as a .tar.
            while (typeSuffix(stem).isNotEmpty()) stem = stem.substring(0, stem.length - typeSuffix(stem).length).trimEnd('.', ' ')
        }
        return (stem + real).ifEmpty { storedName }
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

    // The desktop's `_normalize_ws`, " ".join(s.split()): Python splits on
    // every Unicode space, Java's \s on ASCII alone -- a non-breaking space
    // pasted from the web made two copies look different here.
    private fun squeeze(text: String): String = PyText.split(text).joinToString(" ")

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
