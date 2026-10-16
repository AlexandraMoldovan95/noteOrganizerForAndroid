package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * A note sent by email: what the message holds, worked out here and handed
 * to the phone's mail app, which does the sending. The app itself connects
 * to nothing -- the mail app opens with the message ready, and nothing goes
 * until the person presses Send there.
 *
 * The person chooses what the message carries: the note's words in the
 * message itself, the note as a web page attached, or both. The words are
 * the note as "Export as file -> Plain text" writes it, and the page is the
 * note as "Export as file -> Web page" writes it, its tables drawn and its
 * styles kept: one meaning of "the note as text", one of "the note as a
 * page", wherever the note goes.
 */
object Mailing {

    /** What the mail app is handed. [attachment] is a file's name and its contents. */
    data class Message(
        val to: List<String>,
        val subject: String,
        val text: String?,
        val attachment: Pair<String, String>?,
    )

    /** The addresses typed, however they were separated: commas, semicolons, spaces. */
    fun addresses(typed: String): List<String> =
        typed.split(',', ';', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }

    // Something before the @, and a domain with a dot in it. A check for
    // the slip of a thumb -- "maria" for "maria@gmail.com" -- not a ruling
    // on what a mail server accepts: that is the mail app's to make.
    private val ADDRESS = Regex("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")

    /** The first address typed that cannot be one, to be named back to the person; null when all can. */
    fun notAnAddress(typed: String): String? = addresses(typed).firstOrNull { !ADDRESS.matches(it) }

    /**
     * The message for [note]: its title as the subject, and what the person
     * chose to send. [attachment] is the note's web page and the name of
     * the PDF it is printed to: a mail app shows a PDF where it shows a
     * page's code instead of the page (Gmail did, opened on a computer).
     */
    fun message(note: JSONObject, typed: String, withText: Boolean, withPage: Boolean): Message {
        val title = Exporting.titleOf(note)
        return Message(
            to = addresses(typed),
            subject = title,
            text = if (withText) text(note, title) else null,
            attachment = if (withPage) pdfName(title) to Exporting.content(note, Exporting.HTML, title) else null,
        )
    }

    /** The PDF's name: the export's, Windows' forbidden characters replaced, ".pdf" for ".html". */
    fun pdfName(title: String): String = Exporting.fileName(title, Exporting.HTML).removeSuffix(".html") + ".pdf"

    /**
     * The note's words for a message's body, where neither a table nor a
     * checkbox can be drawn: a mail app takes plain text from another app,
     * and a table's rows came through as lines of bars. The export's text,
     * with each table's rows written as their cells named by their
     * columns -- "• Day: Mon · Task: gym" -- and the checklist's boxes as
     * ☐ and ☑, which every mail app draws.
     */
    fun text(note: JSONObject, title: String): String {
        val body = note.opt("body") as? String ?: ""
        val comments = PyText.strip(note.opt("comments") as? String ?: "")
        val checklist = (note.opt("checklist") as? JSONArray)?.let { list ->
            (0 until list.length()).mapNotNull { list.opt(it) as? JSONObject }
        } ?: emptyList()
        var plain = "$title\n\n${readable(body)}"
        if (checklist.isNotEmpty()) {
            plain += "\n\nChecklist:\n" + checklist.joinToString("\n") {
                "${if (Exporting.truthy(it.opt("done"))) "☑" else "☐"} ${it.opt("text") as? String ?: ""}"
            }
        }
        if (comments.isNotEmpty()) plain += "\n\nComments:\n${readable(comments)}"
        return Exporting.mend("$plain\n")
    }

    /** [text]'s tables, each written as the lines [tableLines] makes; everything else as it is. */
    fun readable(text: String): String {
        val blocks = tableBlocks(text)
        if (blocks.isEmpty()) return text
        val lines = text.split("\n")
        val out = ArrayList<String>()
        var at = 0
        for (block in blocks) {
            while (at < block.first) out.add(lines[at++])
            out.addAll(tableLines(lines.subList(block.first, block.last + 1)))
            at = block.last + 1
        }
        while (at < lines.size) out.add(lines[at++])
        return out.joinToString("\n")
    }

    /**
     * One table as lines: the first row is the header, as the exported page
     * draws it, and every row after it is a line of its cells, each named by
     * its column's header. An empty cell is left out, and so is a row with
     * nothing in it; a hidden column stays hidden; a merged cell is said
     * once, under the column it starts in. A table with no row but its
     * header is that row, its cells side by side.
     */
    fun tableLines(lines: List<String>): List<String> {
        val table = parseTable(lines)
        if (table.rows.isEmpty()) return emptyList()
        val shown = table.columns.indices.filter { !table.columns[it].hidden }
        val header = shown.map { cellText(table.rows.first().getOrNull(it)) }
        if (table.rows.size == 1) {
            return header.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { listOf("• " + it.joinToString(" · ")) }
                ?: emptyList()
        }
        return table.rows.drop(1).mapNotNull { row ->
            val parts = shown.mapIndexedNotNull { k, c ->
                val value = cellText(row.getOrNull(c))
                when {
                    value.isEmpty() -> null
                    header[k].isEmpty() -> value
                    else -> "${header[k]}: $value"
                }
            }
            if (parts.isEmpty()) null else "• " + parts.joinToString(" · ")
        }
    }

    private fun cellText(cell: TableCell?): String = when {
        cell == null -> ""
        cell.checked != null -> if (cell.checked) "☑" else "☐"
        else -> cell.lines.joinToString(" ") { PyText.strip(it) }.trim()
    }

    /** True when the note has files of its own, which the message does not carry -- as an export does not. */
    fun hasFiles(note: JSONObject): Boolean = ((note.opt("attachments") as? JSONArray)?.length() ?: 0) > 0
}
