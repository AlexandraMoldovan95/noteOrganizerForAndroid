package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Templates: reusable starting points for new notes, in the same
 * `templates.json` the desktop keeps -- so a template made on either
 * device starts notes on both.
 *
 * A template holds a name and the note fields it fills in: title, body
 * (with its styles and paragraph lines), comments, category, checklist.
 * The desktop's rules, kept:
 *
 *  - Every template read passes `normalize_template`: what is not the
 *    right shape is replaced by its default, never trusted.
 *  - A name is never overwritten silently: saving under a name another
 *    template has asks first ([nameTaken]).
 *  - Templates do not go to the Trash: deleting one is for good, and the
 *    question says so.
 *  - A note made from a template is a new note; nothing done to it
 *    afterwards touches the template.
 *  - A file that cannot be read is kept aside before anything writes over
 *    it, as every other data file is.
 *
 * One rule is not ported: the desktop's per-line clamp on styles guards
 * its text widget's drawing, which the phone does not have, and no note a
 * person writes comes near it.
 */
object Templates {

    const val UNTITLED = "Untitled template"
    const val DEFAULT_NAME = "My template"
    private const val MAX_CHECKLIST_ITEMS = 1000

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
            JSONArray(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Files.preserveCorrupt(file)
            return emptyList()
        }
        val kept = (0 until data.length()).mapNotNull { data.opt(it) as? JSONObject }.map { normalize(it) }
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
    fun newNote(load: Notebook.Load, template: JSONObject, categories: Collection<String>): SaveResult {
        val tpl = normalize(template)
        val body = tpl.optString("body")
        val category = tpl.optString("category").takeIf { it in categories } ?: "General"
        // Styles and lines come only with a text: on the desktop they ride
        // on the body, and a template with none brings none.
        val formatting = if (body.isEmpty()) emptyList() else Exporting.normalizeFormatting(tpl.opt("formatting"))
        val paragraphs = if (body.isEmpty()) JSONArray() else tpl.optJSONArray("paragraphs")
        return Notebook.createNote(load, tpl.optString("title"), body, category,
            formatting = formatting, paragraphs = paragraphs, comments = tpl.optString("comments"),
            checklist = tpl.optJSONArray("checklist"))
    }
}
