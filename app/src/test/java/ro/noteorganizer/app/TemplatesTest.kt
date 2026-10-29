package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/** Templates: the desktop's normalize_template answer for answer, and what saving and using one does. */
class TemplatesTest {

    /**
     * A JSON value as plain data, so two files' worth compare by content:
     * numbers by value, keys unordered. A paragraph's line of true or false
     * is 1 or 0 -- the desktop writes back the bool it read, the phone the
     * number, and to Python, which reads both, they are the same line.
     */
    private fun plain(value: Any?, key: String? = null): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.opt(it), it) }.toSortedMap()
        is JSONArray -> (0 until value.length()).map { plain(value.opt(it)) }
        is Int -> value.toLong()
        is Long -> value
        is Boolean -> if (key == "line") (if (value) 1L else 0L) else value
        JSONObject.NULL -> null
        else -> value
    }

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("template_cases.json")
        assertNotNull("template_cases.json is missing - rebuild it with tools/make_template_fixture.py", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `every template in the fixture is normalized as the desktop normalizes it`() {
        val cases = fixture().getJSONArray("cases")
        assertTrue(cases.length() > 250)
        val wrong = ArrayList<String>()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val got = plain(Templates.normalize(case.opt("raw")))
            val want = plain(case.getJSONObject("normalized"))
            if (got != want) wrong.add("#$i\n  desktop $want\n  phone   $got")
        }
        assertEquals(wrong.take(4).joinToString("\n"), 0, wrong.size)
    }

    // ---- saving one ----

    private fun named(name: String, title: String = "") = Templates.normalize(JSONObject().put("name", name).put("title", title))

    @Test
    fun `saving as a template replaces one of the same name, and goes last`() {
        val list = listOf(named("A", "old"), named("B"), named("A", "older"))
        val saved = Templates.saveAs(list, named("A", "new"))
        assertEquals(listOf("B", "A"), saved.map { it.getString("name") })
        assertEquals("new", saved.last().getString("title"))
    }

    @Test
    fun `an edited template keeps its place, and another of its new name goes`() {
        val list = listOf(named("A"), named("B"), named("C"))
        val edited = Templates.saveEdited(list, 2, named("A", "renamed C"))
        assertEquals(listOf("B", "A"), edited.map { it.getString("name") })
        assertEquals("renamed C", edited.last().getString("title"))
        assertEquals("a new one goes last", listOf("A", "B", "C", "D"),
            Templates.saveEdited(list, null, named("D")).map { it.getString("name") })
    }

    @Test
    fun `the words waiting in the template editor's A new item box are saved as its last row`() {
        // Left out of what Save writes, "passport" typed and not added was
        // dropped unsaid -- and, the only change, ✕ closed with no question.
        val rows = listOf("towel" to true)
        val saved = Templates.checklistWith(rows, "  passport ")
        assertEquals(2, saved.length())
        assertEquals("passport", saved.getJSONObject(1).getString("text"))
        assertFalse(saved.getJSONObject(1).getBoolean("done"))
        assertTrue(saved.getJSONObject(0).getBoolean("done"))
        assertEquals("a blank box adds nothing", 1, Templates.checklistWith(rows, "   ").length())
    }

    @Test
    fun `a name is taken only by another template`() {
        val list = listOf(named("A"), named("B"))
        assertTrue(Templates.nameTaken(list, "B"))
        assertFalse("its own name is not taken from itself", Templates.nameTaken(list, "B", except = 1))
        assertFalse(Templates.nameTaken(list, "b"))
    }

    @Test
    fun `the name offered is the title's first thirty characters, or My template`() {
        assertEquals("Weekly plan", Templates.defaultName("  Weekly plan  "))
        assertEquals("x".repeat(30), Templates.defaultName("x".repeat(45)))
        assertEquals(Templates.DEFAULT_NAME, Templates.defaultName("   "))
        assertEquals("an emoji is one character", "😀".repeat(30), Templates.defaultName("😀".repeat(31)))
    }

    @Test
    fun `a note gives a template something only with a title, text, comments or a checklist`() {
        assertFalse(Templates.hasContent(" ", "\n", "\u0085", JSONArray()))
        assertTrue(Templates.hasContent("", "", "", JSONArray().put(JSONObject().put("text", "x"))))
        assertTrue(Templates.hasContent("", "body", "", null))
    }

    // ---- the file ----

    private fun dir(): File = NioFiles.createTempDirectory("templates").toFile()

    @Test
    fun `no file is no templates, and a file nobody can read is kept aside`() {
        val d = dir()
        assertEquals(emptyList<JSONObject>(), Templates.load(d))
        File(d, "templates.json").writeText("{ not json")
        assertEquals(emptyList<JSONObject>(), Templates.load(d))
        assertTrue(d.listFiles()!!.any { it.name.startsWith("templates_corrupt_") })
    }

    @Test
    fun `a list of junk is kept aside too, and good templates come in normalized`() {
        val d = dir()
        File(d, "templates.json").writeText("""[1, "x", null]""")
        assertEquals(emptyList<JSONObject>(), Templates.load(d))
        assertTrue(d.listFiles()!!.any { it.name.startsWith("templates_corrupt_") })
        val e = dir()
        File(e, "templates.json").writeText("""[{"name": "Plan", "body": 5}, "junk"]""")
        val read = Templates.load(e)
        assertEquals(1, read.size)
        assertEquals("", read[0].getString("body"))
        assertFalse(e.listFiles()!!.any { it.name.startsWith("templates_corrupt_") })
    }

    @Test
    fun `templates stop at 1,000 - at a load, an import, and a new one saved`() {
        // A 394 KB backup brought 140,000, appended at once and read again at
        // every opening of Templates and Use template.
        val many = JSONArray((1..1500).map { JSONObject().put("name", "T$it").put("body", "b") })
        val d = dir()
        File(d, "templates.json").writeText(many.toString())
        assertEquals(Templates.MAX_TEMPLATES, Templates.load(d).size)
        // The import stops at the ceiling.
        val notes = dir().also { File(it, "notes_data.json").writeText("[]"); File(it, "templates.json").writeText("[]") }
        val load = Notebook.load(notes)
        val plan = Importing.plan(load.raw!!, emptyList(), Importing.Source.BACKUP)
        Importing.apply(load, plan, Importing.Answers(), null, Importing.Extras(templates = many))
        assertEquals(Templates.MAX_TEMPLATES, JSONArray(File(notes, "templates.json").readText()).length())
        // Full: a new name is refused, a replace or an edit is not.
        val full = Templates.load(d)
        assertTrue(Templates.full(full, "New one"))
        assertFalse(Templates.full(full, "T5"))
        assertFalse(Templates.full(full, "Renamed", edited = 3))
        assertFalse(Templates.full(full.drop(1), "New one"))
    }

    @Test
    fun `a template's styles and lines move with its words through every edit`() {
        // Kept at their offsets, a line typed above them put the bold on
        // ", Bo\nMeet", the red on "re final", the bullet on the wrong line.
        val body = "Meeting notes\nDecisions are final\nTo bring: Passport, charger\nNext steps"
        val bold = body.indexOf("Decisions")
        val red = body.indexOf("Passport")
        var formatting = JSONArray("""[{"start": $bold, "end": ${bold + 9}, "style": "bold"},
            {"start": $red, "end": ${red + 8}, "style": "text_color", "value": "#e81123"}]""")
        var paragraphs = JSONArray("""[{"line": 2, "kind": "bullet"}]""")
        var text = body
        // Typed a letter at a time at the start, as a person types, then a line deleted further down.
        for (typed in "Attendees: Ana, Bo\n".indices) {
            val next = "Attendees: Ana, Bo\n".substring(0, typed + 1) + body
            Notebook.throughEdit(formatting, paragraphs, text, next).let { formatting = it.first; paragraphs = it.second }
            text = next
        }
        val shorter = text.replace("Next steps", "")
        Notebook.throughEdit(formatting, paragraphs, text, shorter).let { formatting = it.first; paragraphs = it.second }
        text = shorter
        fun styled(i: Int) = formatting.getJSONObject(i).let { text.substring(it.getInt("start"), it.getInt("end")) }
        assertEquals("Decisions", styled(0))
        assertEquals("Passport", styled(1))
        assertEquals("To bring: Passport, charger", text.split("\n")[paragraphs.getJSONObject(0).getInt("line")])
    }

    @Test
    fun `saved, read back the same`() {
        val d = dir()
        val list = listOf(Templates.fromNote("  Plan ", "Week", "• a", JSONArray(), JSONArray("""[{"line": 0, "kind": "bullet"}]"""),
            "c", "Work", JSONArray("""[{"text": "x", "done": true}]""")))
        assertEquals(null, Templates.save(d, list))
        assertEquals(plain(JSONArray(list)), plain(JSONArray(Templates.load(d))))
        assertEquals("the name is stripped", "Plan", Templates.load(d)[0].getString("name"))
    }

    // ---- using one ----

    private fun notebook(): Notebook.Load {
        val d = dir()
        File(d, "notes_data.json").writeText("[]")
        return Notebook.load(listOf(d))
    }

    @Test
    fun `a note from a template has its fields, ticks and all`() {
        val load = notebook()
        val tpl = Templates.normalize(JSONObject("""{
            "name": "Trip", "title": "Packing", "body": "• passport\nbig",
            "formatting": [{"start": 11, "end": 14, "style": "font_size", "value": 20}],
            "paragraphs": [{"line": 0, "kind": "bullet"}], "comments": "check twice", "category": "Work",
            "checklist": [{"text": "charger", "done": true}, {"text": "tickets", "done": false}]}"""))
        val result = Templates.newNote(load, tpl, listOf("General", "Work"))
        assertTrue(result is SaveResult.Ok)
        val note = JSONArray(load.file!!.readText()).getJSONObject(0)
        assertEquals("Packing", note.getString("title"))
        assertEquals("• passport\nbig", note.getString("body"))
        assertEquals("Work", note.getString("category"))
        assertEquals("check twice", note.getString("comments"))
        assertEquals(20, note.getJSONArray("formatting").getJSONObject(0).get("value"))
        assertEquals("bullet", note.getJSONArray("paragraphs").getJSONObject(0).getString("kind"))
        assertEquals(true, note.getJSONArray("checklist").getJSONObject(0).getBoolean("done"))
        assertEquals("tickets", note.getJSONArray("checklist").getJSONObject(1).getString("text"))
    }

    @Test
    fun `a category that is gone gives General, and no text brings no styles`() {
        val load = notebook()
        val tpl = Templates.normalize(JSONObject("""{"name": "T", "title": "t", "category": "Deleted",
            "paragraphs": [{"line": 0, "align": "center"}]}"""))
        Templates.newNote(load, tpl, listOf("General"))
        val note = JSONArray(load.file!!.readText()).getJSONObject(0)
        assertEquals("General", note.getString("category"))
        assertEquals(0, note.getJSONArray("paragraphs").length())
    }

    @Test
    fun `a template's checklist rows are kept whole and drawn short`() {
        // Saved from a note, a 150,000-letter row went whole into the
        // template editor's wrapping Text and froze it as it opened.
        val long = "a".repeat(150_000)
        val tpl = Templates.fromNote("Long row", "", "", null, null, "", "General",
            JSONArray().put(JSONObject().put("text", long).put("done", false)).put(JSONObject().put("text", "milk")))
        val rows = tpl.getJSONArray("checklist")
        assertEquals("kept whole", long, rows.getJSONObject(0).getString("text"))
        assertTrue(Editing.tooLongToLayOut(long))
        val drawn = drawnRow(long)
        assertTrue(drawn.length <= 301)
        assertTrue(drawn.endsWith("…"))
        assertEquals("a row anyone writes is drawn as it is", "milk", drawnRow("milk"))
        // A thousand rows of 9,999 letters, each under the line's own limit:
        // drawn, together they stay far below what a body may give a layout.
        val heavy = "b".repeat(Editing.MAX_UNBROKEN - 1)
        assertFalse(Editing.tooLongToLayOut(heavy))
        val drawnAll = List(1_000) { drawnRow(heavy) }
        assertTrue(drawnAll.sumOf { Editing.layoutCost(it)!! } < Editing.MAX_LAYOUT_COST)
    }

    @Test
    fun `a template full at 1,000 rows refuses a row, and words waiting with no room are not taken as saved`() {
        // Its Add drew row 1,001 and Save dropped it, and the words waiting
        // in A new item went the same way, unsaid, ✕ finding nothing changed.
        assertEquals(null, Templates.noRoom(999))
        assertEquals("A checklist holds at most 1000 items.", Templates.noRoom(1_000))
        assertEquals(Notebook.CHECKLIST_FULL, Templates.noRoom(1_000))
        assertTrue(Templates.waitingLeftOut(1_000, "Passport"))
        assertFalse("room for them", Templates.waitingLeftOut(999, "Passport"))
        assertFalse("nothing waiting but spaces", Templates.waitingLeftOut(1_000, "   "))
        // Under the cap they are the template's last row, as before.
        val rows = List(999) { "item $it" to false }
        val saved = Templates.normalize(JSONObject().put("checklist", Templates.checklistWith(rows, "Passport")))
            .getJSONArray("checklist")
        assertEquals("Passport", saved.getJSONObject(998 + 1).getString("text"))
    }

    @Test
    fun `A new item's words are cut to a row's length as the desktop's box and Save cut them`() {
        // The desktop's strip()[:2000], in its Add and in what Save takes from
        // the box; the phone saved a paste of 3,500 letters as one row.
        assertEquals("milk", Templates.newRow("  milk  "))
        assertEquals("w".repeat(2_000), Templates.newRow("w".repeat(3_500)))
        assertEquals("counted in letters", "😀".repeat(2_000), Templates.newRow("😀".repeat(2_100)))
        val list = Templates.checklistWith(emptyList(), "w".repeat(3_500))
        assertEquals(2_000, list.getJSONObject(0).getString("text").length)
    }

    @Test
    fun `a template being written is kept aside whole, and found again by the template it edits`() {
        // Android ended the app behind Home and everything typed was gone:
        // the editor saves only on Save. Kept aside instead, and offered back.
        val dir = NioFiles.createTempDirectory("draft").toFile()
        assertNull("no draft", Templates.loadDraft(dir))
        val fields = Templates.draftFields("", "Packing", "Passport\nTickets", "for the trip", "Work",
            listOf("socks" to true, "charger" to false), JSONArray(), JSONArray())
        assertNull(Templates.saveDraft(dir, Templates.Draft("Trip", fields, "toothbrush")))
        val back = Templates.loadDraft(dir)!!
        assertEquals("Trip", back.edits)
        assertEquals("the words waiting in A new item", "toothbrush", back.waiting)
        assertEquals("the name as typed, empty", "", back.fields.getString("name"))
        assertEquals("Passport\nTickets", back.fields.getString("body"))
        assertEquals("for the trip", back.fields.getString("comments"))
        assertEquals("Work", back.fields.getString("category"))
        val rows = back.fields.getJSONArray("checklist")
        assertEquals("socks", rows.getJSONObject(0).getString("text"))
        assertTrue(rows.getJSONObject(0).getBoolean("done"))
        // Continued over the template it edits, found by name -- or as a new one when it is gone.
        val templates = listOf(JSONObject().put("name", "Other"), JSONObject().put("name", "Trip"))
        assertEquals(1, Templates.draftTarget(templates, back))
        assertFalse(Templates.draftLostItsTemplate(templates, back))
        assertEquals(null, Templates.draftTarget(templates.take(1), back))
        assertTrue(Templates.draftLostItsTemplate(templates.take(1), back))
        val new = back.copy(edits = null)
        assertEquals(null, Templates.draftTarget(templates, new))
        assertFalse("a new one lost nothing", Templates.draftLostItsTemplate(templates, new))
        // Gone once saved or discarded.
        Templates.deleteDraft(dir)
        assertNull(Templates.loadDraft(dir))
        // One that cannot be read is set aside, never offered half.
        File(dir, Templates.DRAFT).writeText("{\"fields\": {\"name\": \"Tr")
        assertNull(Templates.loadDraft(dir))
        assertTrue(dir.listFiles()!!.any { it.name.contains("corrupt") })
    }

    @Test
    fun `a draft is kept while the editor holds changes, and none stays once they are taken back`() {
        // Written only when something had changed, a draft from the last time
        // the app went away outlived a revert and was offered back: Continue,
        // then Save, wrote the words the person had taken out.
        val dir = NioFiles.createTempDirectory("draft").toFile()
        val fields = Templates.draftFields("Trip", "Packing x", "", "", "General", emptyList(), JSONArray(), JSONArray())
        assertNull(Templates.keepDraft(dir, changed = true) { Templates.Draft("Trip", fields, "") })
        assertEquals("Packing x", Templates.loadDraft(dir)!!.fields.getString("title"))
        // The "x" taken back by hand: the editor holds the template as saved.
        assertNull(Templates.keepDraft(dir, changed = false) { error("nothing to keep is never built") })
        assertNull("no draft offered back", Templates.loadDraft(dir))
    }

    @Test
    fun `what the template editor opened on is the template, so a draft continued over it is a change`() {
        val template = Templates.normalize(JSONObject().put("name", "Trip").put("title", "Packing")
            .put("checklist", JSONArray().put(JSONObject().put("text", "socks").put("done", false))))
        val opened = Templates.collected("Trip", "Packing", "", "", "General", listOf("socks" to false), "", JSONArray(), JSONArray())
        assertEquals("the editor's own reading of the template is the template", template.toString(), opened.toString())
        val draft = Templates.collected("Trip", "Packing", "", "", "General", listOf("socks" to false), "charger", JSONArray(), JSONArray())
        assertFalse(draft.toString() == opened.toString())
    }
}
