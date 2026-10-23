package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * The note a brand-new notebook on this phone opens with.
 *
 * Built the way the desktop's `core/welcome.py` builds its own: stored
 * exactly as the editor would have stored it had it been typed -- the
 * bullet markers in the text, bold as character spans, the bullet lines as
 * paragraph entries. Only a notebook that did not exist until this start
 * gets it; Settings → About offers it again.
 *
 * The words are this app's own, written for a phone: they name what a
 * thumb does here, not what a mouse does on the desktop. The desktop's
 * were worded with the owner over three drafts; these are a first draft,
 * and the test plan marks them NEEDS YOUR EYE.
 */
object Welcome {

    const val TITLE = "Welcome to Note Organizer 👋"

    /** The bullet the editor writes at the start of a list line. */
    private const val BULLET = "• "

    /**
     * "# " starts a heading, "- " a bullet, "" a blank line. **Bold** is bold.
     * Every heading's icon is from the emoji block (U+1F000 up): an older
     * symbol such as ✍ is drawn in black and white, narrower, and its title
     * starts left of the others -- here, and on the desktop a backup takes
     * the note to.
     */
    val LINES = listOf(
        "Hi, and welcome! This is your own space for everything worth keeping: ideas, " +
            "plans, lists, the things you don't want to forget. It all stays on this phone, " +
            "with no account and no internet needed. Here is a quick look around. When you no " +
            "longer need this note, delete it; Settings → About brings it back.",
        "",
        "# 📝 Write",
        "- The **+** button starts a note. Give it a title and write; it saves itself as you go.",
        "- The bar above the keyboard shapes your words: bold, italic, colours and highlights.",
        "- **⋮** at the top of a note adds a checklist for to-dos, comments and files.",
        "",
        "# 🗂 Organise",
        "- **Search** at the top looks through all your notes at once.",
        "- **Sort** and **Show** arrange the list. Categories give notes a colour.",
        "- **Touch and hold** a note to pin it, move it, duplicate it, share it, or select several.",
        "",
        "# 🔒 Share and keep safe",
        "- **Share as a code** turns a note into text you can send; whoever has Note Organizer can bring it in.",
        "- **Settings → Backup** saves your whole notebook in one file — the same file the desktop app reads.",
        "- Deleted notes wait in **Settings → Trash**, so a slip can be undone.",
        "",
        "# 🛡 Your privacy",
        "- The app collects nothing and cannot use the internet. A note leaves this phone only when " +
            "you share it, save a backup, or your phone backs itself up to your Google account.",
        "- **Settings → About → Privacy policy** has the whole of it.",
        "",
        "# 🎨 Make it yours",
        "- Eight light and dark themes in **Settings → Appearance**, with a Light | Dark switch.",
        "",
        "Good luck, and may your notes bring you success! 🍀",
    )

    private fun runs(line: String): List<Pair<String, Boolean>> =
        line.split("**").mapIndexedNotNull { index, part ->
            if (part.isEmpty()) null else part to (index % 2 == 1)
        }

    /**
     * The note's own fields: title, body, formatting and paragraphs, with
     * offsets counted in code points as the file counts them.
     */
    fun build(): JSONObject {
        val body = StringBuilder()
        val spans = JSONArray()
        val paragraphs = JSONArray()
        var offset = 0
        for ((number, line) in LINES.withIndex()) {
            val pieces = when {
                line.startsWith("# ") -> listOf(line.removePrefix("# ") to true)
                line.startsWith("- ") -> {
                    paragraphs.put(JSONObject().put("line", number).put("kind", "bullet"))
                    listOf(BULLET to false) + runs(line.removePrefix("- "))
                }
                else -> runs(line)
            }
            for ((text, bold) in pieces) {
                val length = text.codePointCount(0, text.length)
                if (bold) {
                    spans.put(JSONObject().put("start", offset).put("end", offset + length).put("style", "bold"))
                }
                body.append(text)
                offset += length
            }
            body.append('\n')
            offset += 1
        }
        return JSONObject()
            .put("title", TITLE)
            .put("body", body.toString().trimEnd('\n'))
            .put("formatting", spans)
            .put("paragraphs", paragraphs)
    }
}
