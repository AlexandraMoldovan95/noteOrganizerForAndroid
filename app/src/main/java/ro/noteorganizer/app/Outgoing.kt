package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * A note handed to another app -- WhatsApp, a mail app, Teams, Drive --
 * through the phone's own share sheet: what goes, worked out here. The
 * app itself sends nothing and reads no contacts; the app picked does the
 * sending, and the person, chat or channel is chosen there.
 *
 * As what is the person's choice: the note's page as a PDF, which opens
 * on any phone or computer; the page itself, for a browser; the note's
 * words in the message, when it has no table -- a message cannot draw a
 * table, and written as lines one read badly, the owner found; or a share
 * code, for Note Organizer at the other end.
 */
object Outgoing {

    const val PDF = "pdf"
    const val PAGE = "html"
    const val TEXT = "text"
    const val CODE = "code"

    /**
     * Whether the note holds a table -- any line a person reads as one, by
     * the rule the card uses to say "▦ 1 table" -- so its words alone,
     * where no table can be drawn, are not offered.
     */
    fun hasTable(note: JSONObject): Boolean =
        previewText(note.opt("body") as? String ?: "").second > 0 ||
            previewText(note.opt("comments") as? String ?: "").second > 0

    /**
     * One share made ready at a time. Asked for again before the last one
     * is ready -- a long note's PDF takes a moment on a slow phone, and the
     * Share button is there again meanwhile -- the second is not started:
     * it would clear the folder the first is still writing into. Here, not
     * on the screen's side, so the order is held by a test: an emulator's
     * taps could not tell this refusal's message from the first share's
     * failing. Used on the main thread only, so a plain flag is enough.
     */
    class OneAtATime {
        private var busy = false

        /** True when [block] ran; false, without running it, when one was under way. */
        suspend fun run(block: suspend () -> Unit): Boolean {
            if (busy) return false
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
            return true
        }
    }

    /** True when the note has files of its own, which nothing shared carries -- as an export does not. */
    fun hasFiles(note: JSONObject): Boolean = ((note.opt("attachments") as? JSONArray)?.length() ?: 0) > 0

    /** The file a page or a PDF is shared as: the export's name, Windows' forbidden characters replaced. */
    fun fileName(title: String, format: String): String {
        val page = Exporting.fileName(title, Exporting.HTML)
        return if (format == PDF) page.removeSuffix(".html") + ".pdf" else page
    }

    /**
     * The note's words for a message: its title, its body, the checklist's
     * boxes drawn as ☐ and ☑, which every app draws, and its comments. The
     * text the export writes otherwise, mended as it mends it.
     */
    fun text(note: JSONObject, title: String): String {
        val body = note.opt("body") as? String ?: ""
        val comments = PyText.strip(note.opt("comments") as? String ?: "")
        val checklist = (note.opt("checklist") as? JSONArray)?.let { list ->
            (0 until list.length()).mapNotNull { list.opt(it) as? JSONObject }
        } ?: emptyList()
        var plain = "$title\n\n$body"
        if (checklist.isNotEmpty()) {
            plain += "\n\nChecklist:\n" + checklist.joinToString("\n") {
                "${if (Exporting.truthy(it.opt("done"))) "☑" else "☐"} ${it.opt("text") as? String ?: ""}"
            }
        }
        if (comments.isNotEmpty()) plain += "\n\nComments:\n$comments"
        return Exporting.mend("$plain\n")
    }

    /**
     * The subject a mail app is handed with a share: the title, cut as a
     * glimpse with "…" when a share code or a backup made it very long. The
     * words, the page and the PDF still carry it whole.
     */
    fun subject(title: String): String = glimpse(title).let { if (it.length < title.length) "$it…" else title }

    /**
     * Whether [words] -- with the [subject] beside them -- are more than may
     * be handed to another app in one go ([Sharing.tooLongToHand], the
     * ceiling a share code is held to): Android passes them through a buffer
     * of about a megabyte, and a note of 300,000 characters handed as words
     * stopped the app at the share sheet. Weighed by what they come to in
     * that buffer, not by their letters: a Chinese letter takes as much
     * room as five bytes, a Latin one three. The page carries any length, as
     * a file the other app reads.
     */
    fun tooLongForWords(words: String, subject: String): Boolean = Sharing.tooLongToHand(words, subject)

    /**
     * What the refusal says, as numbers that agree with it: the characters
     * handed, the subject's among them -- the rule counts them, and a note
     * refused with 99,953 under a ceiling of 100,000 read as a contradiction
     * -- and how many characters like these may be handed at most.
     */
    fun tooLongNumbers(words: String, subject: String): Pair<Long, Int> =
        Sharing.characters(words, subject) to Sharing.handedAtMost(words, subject)

    /** Whether one note's share code may be handed over, and if not, why. */
    sealed class Code {
        object Fits : Code()
        /** Past what may be handed in one go: its length, and the most. */
        data class TooLong(val characters: Long, val most: Int) : Code()
        /** Past what a code may hold at all ([Sharing.encode] refuses it). */
        object TooMuch : Code()
    }

    /**
     * [note]'s share code weighed as the Share sheet's card shows it, before
     * the tap -- the ceiling the code is refused by after it, in
     * [AppState.shareCode]: a person learnt of it only once it was too late
     * to choose the web page or a backup instead.
     */
    fun codeFor(note: JSONObject): Code {
        val code = try {
            Sharing.encode(listOf(note))
        } catch (e: Sharing.TooLarge) {
            return Code.TooMuch
        }
        return if (Sharing.tooLongToHand(code)) Code.TooLong(Sharing.characters(code), Sharing.handedAtMost(code)) else Code.Fits
    }

    /**
     * The note's page as the PDF prints it: any line may break anywhere at
     * the paper's edge. The page itself lets only a table's cells do so --
     * a browser scrolls -- and the PDF, drawn at the paper's width, cut a
     * link's unbreakable tail at the margin and left it out, unsaid. Printed
     * only: the page shared or exported stays as the desktop writes it.
     */
    fun forPrint(html: String): String {
        val style = "<style>\nbody, body * { overflow-wrap: anywhere; }\n</style>\n"
        val at = html.indexOf("</head>")
        return if (at < 0) style + html else html.substring(0, at) + style + html.substring(at)
    }

    /** [tooLongForWords] for [note] as it is now, and the words it would send. */
    fun tooLongForWords(note: JSONObject): Boolean {
        val title = Exporting.titleOf(note)
        return tooLongForWords(text(note, title), subject(title))
    }
}
