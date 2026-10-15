package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
