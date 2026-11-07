package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Templates: reusable starting points for new notes, in the same
 * `templates.json` the desktop keeps, so a template made on either device
 * starts notes on both. A template holds a name and the note fields it
 * fills in. The desktop's rules, kept: every template read passes
 * `normalize_template`; a name is never overwritten silently
 * ([nameTaken]); a delete is for good, not the Trash, and says so; nothing
 * done to a note made from one touches the template; a file that cannot be
 * read is kept aside before anything writes over it. Not ported: the
 * per-line clamp on styles, which guards the desktop's text widget alone.
 */
object Templates {

    const val UNTITLED = "Untitled template"
    const val DEFAULT_NAME = "My template"
    private const val MAX_CHECKLIST_ITEMS = 1000

    /**
     * The desktop's MAX_TEMPLATES, far past anyone's use: a load keeps the
     * first thousand, an import stops there, and a new one past it is
     * refused with a word, so a start never drops one a person made. A
     * 394 KB backup brought 140,000, appended on the screen's thread and
     * read again at every opening of Templates and "Use template".
     */
    const val MAX_TEMPLATES = 1000

    /**
     * Whether [templates] has no room for one named [name]: a new name past
     * [MAX_TEMPLATES]. Replacing one by name, or editing one in place
     * ([edited]), always has room.
     */
    fun full(templates: List<JSONObject>, name: String, edited: Int? = null): Boolean =
        edited == null && templates.size >= MAX_TEMPLATES && templates.none { it.optString("name") == name }

    private fun truthy(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotEmpty()
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    /** Styles as the file holds them: a size as a number, the rest as text. */
    internal fun spansJson(spans: List<FormatSpan>): JSONArray {
        val out = JSONArray()
        for (span in spans) {
            val o = JSONObject().put("start", span.start).put("end", span.end).put("style", span.style)
            if (span.value.isNotEmpty()) o.put("value", if (span.style == "font_size") span.value.toInt() else span.value)
            out.put(o)
        }
        return out
    }

    internal fun paragraphsJson(paragraphs: List<Exporting.Paragraph>): JSONArray {
        val out = JSONArray()
        for (p in paragraphs) {
            val o = JSONObject().put("line", p.line)
            if (p.kind != null) o.put("kind", p.kind)
            if (p.indent != 0) o.put("indent", p.indent)
            if (p.align != null) o.put("align", p.align)
            out.put(o)
        }
        return out
    }

    /** `normalize_template`: any value in, a template of the known shape out. */
    fun normalize(value: Any?): JSONObject {
        val tpl = value as? JSONObject ?: JSONObject()
        fun text(key: String) = tpl.opt(key) as? String ?: ""
        val body = text("body")
        val checklist = JSONArray()
        (tpl.opt("checklist") as? JSONArray)?.let { list ->
            // The cap first, then what is a real item -- the desktop's order.
            for (i in 0 until minOf(list.length(), MAX_CHECKLIST_ITEMS)) {
                val item = list.opt(i) as? JSONObject ?: continue
                checklist.put(JSONObject()
                    .put("text", item.opt("text") as? String ?: "")
                    .put("done", truthy(item.opt("done"))))
            }
        }
        return JSONObject()
            .put("name", text("name").ifEmpty { UNTITLED })
            .put("title", text("title"))
            .put("body", body)
            .put("formatting", spansJson(Exporting.normalizeFormatting(tpl.opt("formatting"))))
            .put("paragraphs", paragraphsJson(Exporting.normalizeParagraphs(tpl.opt("paragraphs"), body.split("\n").size)))
            .put("comments", text("comments"))
            .put("category", text("category").ifEmpty { "General" })
            .put("checklist", checklist)
    }

    /** The templates in [dir]: none when there is no file; a file that cannot be read is kept aside first. */
    fun load(dir: File): List<JSONObject> {
        val file = File(dir, Backup.TEMPLATES)
        if (!file.exists()) return emptyList()
        val data = try {
            Files.parseOwn(file) { JSONArray(it) }
        } catch (e: Exception) {
            Files.preserveCorrupt(file)
            return emptyList()
        }
        val kept = (0 until data.length()).mapNotNull { data.opt(it) as? JSONObject }.take(MAX_TEMPLATES).map { normalize(it) }
        // A list, but all of it junk: kept aside like an unreadable file.
        if (data.length() > 0 && kept.isEmpty()) Files.preserveCorrupt(file)
        return kept
    }

    /** Writes the whole list; null when the file now holds it, otherwise why not. */
    fun save(dir: File, templates: List<JSONObject>): String? =
        Files.writeAtomic(File(dir, Backup.TEMPLATES), JSONArray(templates).toString(2))

    /** Whether a note gives a template anything: a title, text, comments or a checklist. */
    fun hasContent(title: String, body: String, comments: String, checklist: JSONArray?): Boolean =
        PyText.strip(title).isNotEmpty() || PyText.strip(body).isNotEmpty() ||
            PyText.strip(comments).isNotEmpty() || (checklist?.length() ?: 0) > 0

    /** The name offered when saving a note as a template: its title's first 30 characters. */
    fun defaultName(title: String): String {
        val stripped = PyText.strip(title)
        if (stripped.isEmpty()) return DEFAULT_NAME
        val cps = stripped.codePoints().toArray()
        return String(cps, 0, minOf(30, cps.size))
    }

    /**
     * The template editor's checklist as its Save writes it: its rows, and
     * the words waiting in "A new item…" last, as Add puts them -- as the
     * note editor's save takes the words in its own box. Left out, Save
     * dropped them unsaid, and ✕ closed with no question when they were the
     * only change.
     */
    fun checklistWith(rows: List<Pair<String, Boolean>>, waiting: String): JSONArray {
        val out = JSONArray(rows.map { JSONObject().put("text", it.first).put("done", it.second) })
        val added = newRow(waiting)
        if (added.isNotEmpty()) out.put(JSONObject().put("text", added).put("done", false))
        return out
    }

    /**
     * The words of "A new item…" as a row: stripped, and no longer than a
     * row is written in -- the desktop's `strip()[:MAX_CHECKLIST_ITEM_CHARS]`,
     * in its Add and in what its Save takes from the box.
     */
    fun newRow(words: String): String {
        val text = PyText.strip(words)
        return if (Editing.rowChars(text) <= Editing.CHECKLIST_ROW_CHARS) text
               else text.substring(0, text.offsetByCodePoints(0, Editing.CHECKLIST_ROW_CHARS))
    }

    /**
     * Why a template of [rows] rows takes no more: past [MAX_CHECKLIST_ITEMS]
     * a row is dropped by every read of it -- its Save among them -- so Add
     * is refused there, in the note editor's words. Null when it has room.
     */
    fun noRoom(rows: Int): String? = if (rows >= MAX_CHECKLIST_ITEMS) Notebook.CHECKLIST_FULL else null

    /**
     * Whether words waiting in "A new item…" have no room in a template of
     * [rows] rows: then Save cannot keep them, and they are not saved. Taken
     * with the rows and cut by the cap, they were dropped unsaid, and ✕ found
     * nothing changed.
     */
    fun waitingLeftOut(rows: Int, waiting: String): Boolean = newRow(waiting).isNotEmpty() && noRoom(rows) != null

    /**
     * The template editor's fields as its Save writes them: the template
     * [collected] from what the editor holds, the words waiting in "A new
     * item…" its last row. One function for Save, for what the editor
     * opened with, and for a draft continued, so the three never differ in
     * what counts as a change.
     */
    fun collected(name: String, title: String, body: String, comments: String, category: String,
                  rows: List<Pair<String, Boolean>>, waiting: String, formatting: JSONArray, paragraphs: JSONArray): JSONObject =
        normalize(JSONObject()
            .put("name", name).put("title", title).put("body", body).put("comments", comments).put("category", category)
            .put("checklist", checklistWith(rows, waiting))
            .put("formatting", formatting)
            .put("paragraphs", paragraphs))

    /** The file a template being written waits in, in the app's own storage that Android's backup leaves out. */
    const val DRAFT = "template_draft.json"

    /**
     * A template being written, kept aside when the app goes to the
     * background with changes nobody saved: its [fields] as the editor held
     * them -- the name as typed, empty or not -- the words [waiting] in "A new
     * item…", and the name of the template it [edits], null for a new one:
     * the editor saves only on Save, and Android ending the app behind Home
     * lost everything typed. Offered back, by the owner's choice.
     */
    data class Draft(val edits: String?, val fields: JSONObject, val waiting: String)

    /** The editor's fields as a draft keeps them: as typed, not as a template normalizes them. */
    fun draftFields(name: String, title: String, body: String, comments: String, category: String,
                    rows: List<Pair<String, Boolean>>, formatting: JSONArray, paragraphs: JSONArray): JSONObject =
        JSONObject().put("name", name).put("title", title).put("body", body).put("comments", comments)
            .put("category", category)
            .put("checklist", JSONArray(rows.map { JSONObject().put("text", it.first).put("done", it.second) }))
            .put("formatting", formatting).put("paragraphs", paragraphs)

    /** Writes [draft] in [dir] -- null when it is there, otherwise why not. One draft: a newer one replaces it. */
    fun saveDraft(dir: File, draft: Draft): String? {
        dir.mkdirs()
        val json = JSONObject().put("fields", draft.fields).put("waiting", draft.waiting)
            .put("edits", draft.edits ?: JSONObject.NULL)
        return Files.writeAtomic(File(dir, DRAFT), json.toString())
    }

    /**
     * The draft in [dir], or null when there is none, or none that can be
     * read -- set aside then, as any file that cannot be read is, never
     * offered half.
     */
    fun loadDraft(dir: File): Draft? {
        val file = File(dir, DRAFT)
        if (!file.isFile) return null
        val json = try {
            Files.parseOwn(file) { JSONObject(it) }
        } catch (e: Exception) {
            Files.preserveCorrupt(file)
            return null
        }
        val fields = json.opt("fields") as? JSONObject ?: return null
        return Draft(json.opt("edits") as? String, fields, json.opt("waiting") as? String ?: "")
    }

    /** The draft gone: saved, discarded, or the editor closed with nothing to keep. */
    fun deleteDraft(dir: File) {
        File(dir, DRAFT).delete()
    }

    /**
     * The editor's draft as it is now, kept in [dir] whenever the editor goes
     * where nobody saved it: the [draft] when something [changed], and none
     * when nothing did -- or an older draft, offered back after a revert,
     * wrote the words taken out. One editor is open at a time, and none
     * opens while a draft waits unanswered, so the draft there is its own.
     */
    fun keepDraft(dir: File, changed: Boolean, draft: () -> Draft): String? {
        if (!changed) {
            deleteDraft(dir)
            return null
        }
        return saveDraft(dir, draft())
    }

    /**
     * Where a draft continues: the place of the template it edits, by the
     * name a template is known by -- or null, for a new one, and for one
     * whose template is gone, which then opens as new ([draftLostItsTemplate]).
     */
    fun draftTarget(templates: List<JSONObject>, draft: Draft): Int? =
        draft.edits?.let { name -> templates.indexOfFirst { it.optString("name") == name }.takeIf { it >= 0 } }

    /** Whether [draft] was editing a template that is no longer there. */
    fun draftLostItsTemplate(templates: List<JSONObject>, draft: Draft): Boolean =
        draft.edits != null && draftTarget(templates, draft) == null

    /** Whether another template than the one at [except] already has [name]. */
    fun nameTaken(templates: List<JSONObject>, name: String, except: Int? = null): Boolean =
        templates.withIndex().any { (i, t) -> i != except && t.optString("name") == name }

    /** "Save as template": any template of that name replaced, the new one last. */
    fun saveAs(templates: List<JSONObject>, template: JSONObject): List<JSONObject> {
        val name = template.optString("name")
        return templates.filter { it.optString("name") != name } + template
    }

    /**
     * The Settings editor's Save: a new template goes last; an edited one
     * stays where it was; any OTHER template with its name -- the person
     * having said Replace -- goes.
     */
    fun saveEdited(templates: List<JSONObject>, edited: Int?, template: JSONObject): List<JSONObject> {
        val name = template.optString("name")
        val out = ArrayList<JSONObject>()
        var placed = false
        for ((i, t) in templates.withIndex()) {
            when {
                i == edited -> {
                    out.add(template)
                    placed = true
                }
                t.optString("name") == name -> Unit
                else -> out.add(t)
            }
        }
        if (!placed) out.add(template)
        return out
    }

    /**
     * The template a note gives, from the fields the editor holds -- the
     * desktop's `save_current_as_template`.
     */
    fun fromNote(name: String, title: String, body: String, formatting: JSONArray?, paragraphs: JSONArray?,
                 comments: String, category: String, checklist: JSONArray?): JSONObject =
        normalize(JSONObject()
            .put("name", PyText.strip(name))
            .put("title", title)
            .put("body", body)
            .put("formatting", formatting ?: JSONArray())
            .put("paragraphs", paragraphs ?: JSONArray())
            .put("comments", comments)
            .put("category", category)
            .put("checklist", checklist ?: JSONArray()))

    /**
     * A new note from a template: its title, text with its styles and lines,
     * comments, category -- when that category still exists -- and checklist,
     * ticks and all. The desktop's `_apply_template`, as a note written at
     * once rather than one waiting in the editor.
     */
    fun newNote(load: Notebook.Load, template: JSONObject, categories: Collection<String>, date: String = ""): SaveResult {
        val tpl = normalize(template)
        val body = tpl.optString("body")
        val category = tpl.optString("category").takeIf { it in categories } ?: "General"
        // Styles and lines come only with a text: on the desktop they ride
        // on the body, and a template with none brings none.
        val formatting = if (body.isEmpty()) emptyList() else Exporting.normalizeFormatting(tpl.opt("formatting"))
        val paragraphs = if (body.isEmpty()) JSONArray() else tpl.optJSONArray("paragraphs")
        return Notebook.createNote(load, tpl.optString("title"), body, category, date = date,
            formatting = formatting, paragraphs = paragraphs, comments = tpl.optString("comments"),
            checklist = tpl.optJSONArray("checklist"))
    }
}
