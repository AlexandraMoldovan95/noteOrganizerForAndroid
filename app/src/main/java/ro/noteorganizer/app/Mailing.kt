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

    /** The message for [note]: its title as the subject, and what the person chose to send. */
    fun message(note: JSONObject, typed: String, withText: Boolean, withPage: Boolean): Message {
        val title = Exporting.titleOf(note)
        return Message(
            to = addresses(typed),
            subject = title,
            text = if (withText) Exporting.content(note, Exporting.TXT, title) else null,
            attachment = if (withPage) {
                Exporting.fileName(title, Exporting.HTML) to Exporting.content(note, Exporting.HTML, title)
            } else {
                null
            },
        )
    }

    /** True when the note has files of its own, which the message does not carry -- as an export does not. */
    fun hasFiles(note: JSONObject): Boolean = ((note.opt("attachments") as? JSONArray)?.length() ?: 0) > 0
}
