package ro.noteorganizer.app

import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Share codes: moving notes between devices as copy-paste text -- a small
 * JSON payload, zlib-compressed, Base64'd, behind a version prefix. On a
 * real notebook one note came to 630 characters at the median, and 72 of
 * 77 fit under 2000: small enough for a message or an email.
 *
 * **This is not encryption.** Anyone holding the code can read the note.
 * It carries note CONTENT and never attachments: binary files would push a
 * code past anything a chat box will accept.
 */
object Sharing {

    const val PREFIX = "NOTE1:"

    /** Safety limits, matching the desktop's. A pasted code is untrusted. */
    const val MAX_CODE_CHARS = 5_000_000
    const val MAX_DECOMPRESSED_BYTES = 20_000_000
    const val MAX_SHARED_NOTES = 200

    /**
     * The only fields a code carries.
     *
     * Notably absent: `id` (assigned fresh on import), `attachments`,
     * `updated`, and the per-occurrence maps. `paragraphs` IS here —
     * without it a shared note arrives flush left with flat lists.
     */
    val SHAREABLE_FIELDS = listOf(
        // "uid" travels, "id" does not: an id is a place in one file. The uid lets
        // the far end recognise a note it has instead of guessing from the text --
        // a guess that fails exactly when the note was edited.
        "uid",
        "title", "body", "formatting", "comments", "category", "date",
        "color", "recurrence", "paragraphs", "checklist",
    )

    private const val DAMAGED =
        "This share code is damaged or incomplete. Ask the sender to copy the whole code again."
    private const val NO_NOTES = "This share code doesn't contain any notes."

    /**
     * A whole JSON value that is neither a list nor an object, as Python's
     * json reads one, its spaces around it: a code holding one is whole but
     * holds no notes (decode_share_code), where org.json's object reader
     * called it damaged.
     */
    private val PY_JSON_SCALAR = Regex(
        """[ \t\n\r]*(?:-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null|NaN|-?Infinity""" +
            """|"(?:[^"\\\x00-\x1f]|\\["\\/bfnrt]|\\u\p{XDigit}{4})*")[ \t\n\r]*""")

    /** What a code turned out to be. A failure explains itself. */
    sealed class Decoded {
        data class Notes(val notes: List<JSONObject>) : Decoded()
        data class Failed(val reason: String) : Decoded()
    }

    /** The slice of a note a share code carries -- present keys only. */
    fun shareableView(note: JSONObject): JSONObject {
        val out = JSONObject()
        for (field in SHAREABLE_FIELDS) {
            if (note.has(field)) out.put(field, note.get(field))
        }
        return out
    }

    /**
     * A code past what may be handed over -- the desktop's
     * `ShareCodeTooLarge`, its words for the person sending.
     */
    class TooLarge(message: String) : Exception(message)

    /**
     * What the phone hands another app at once, in characters -- a code or a
     * note's words; longer is refused here, kindly. Measured, not reasoned:
     * the share sheet's text travels at about three bytes a character in a
     * call held to half a megabyte, and past 160,000 the sheet never came,
     * nothing said. 100,000 Latin letters leave room; the ceiling itself is
     * [MAX_HANDED_BYTES], what they come to ([handedBytes]).
     */
    const val MAX_HANDED_CHARS = 100_000

    /**
     * What [texts] come to in Android's buffer, handed to another app: each
     * twice, as the intent's text (two bytes a UTF-16 unit) and as its clip,
     * in UTF-8 since Android 11 (one byte a Latin letter, three a Chinese or
     * Korean one). Measured: 210,000 Latin letters, 634,252 bytes; counted by
     * letters, 99,890 Chinese ones were a parcel the system did not deliver.
     */
    fun handedBytes(vararg texts: String): Long = texts.sumOf { 2L * it.length + utf8Length(it) }

    /** The ceiling on [handedBytes]: [MAX_HANDED_CHARS] Latin letters' worth. */
    const val MAX_HANDED_BYTES = 3L * MAX_HANDED_CHARS

    /** Whether [texts] are more than may be handed to another app in one go ([MAX_HANDED_BYTES]). */
    fun tooLongToHand(vararg texts: String): Boolean = handedBytes(*texts) > MAX_HANDED_BYTES

    /**
     * How many characters like those of [texts] may be handed at most --
     * the ceiling shared out at their bytes a character: 100,000 of the
     * Latin alphabet, about 60,000 Chinese. Under [characters] of them
     * exactly when [tooLongToHand], so a refusal never quotes a number
     * under the one it refuses.
     */
    fun handedAtMost(vararg texts: String): Int {
        val bytes = handedBytes(*texts)
        val count = characters(*texts)
        return if (bytes == 0L) MAX_HANDED_CHARS else (MAX_HANDED_BYTES * count / bytes).toInt()
    }

    /** How many characters [texts] hold, as a person counts them: code points. */
    fun characters(vararg texts: String): Long = texts.sumOf { it.codePointCount(0, it.length).toLong() }

    private fun utf8Length(text: String): Long {
        var bytes = 0L
        var i = 0
        while (i < text.length) {
            val c = text[i]
            bytes += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> { i++; 4 }
                else -> 3
            }
            i++
        }
        return bytes
    }

    /**
     * The code for [notes] -- refused, as the desktop's `encode_share_code`
     * refuses it, past 200 notes or past the size the import accepts: the
     * phone made such codes and neither app would take them, telling the
     * person at the other end to ask for a smaller one.
     */
    fun encode(notes: List<JSONObject>): String {
        if (notes.size > MAX_SHARED_NOTES) {
            throw TooLarge("A code can carry at most $MAX_SHARED_NOTES notes, and ${notes.size} are chosen. " +
                           "Choose fewer, or send a backup instead: Settings → Backup.")
        }
        val payload = JSONObject()
        payload.put("v", 1)
        val array = JSONArray()
        for (note in notes) array.put(shareableView(note))
        payload.put("notes", array)
        // A lone surrogate here would fail the UTF-8 encoding below. The
        // desktop hit exactly this: an emoji typed but not yet saved took
        // the crash path on sharing, because mending only ran on save.
        val raw = mendSurrogates(payload.toString()).toByteArray(Charsets.UTF_8)
        // Weighed before the deflate, the dearest step: a note past what a
        // code may hold was deflated whole at level 9, then refused.
        if (raw.size > MAX_DECOMPRESSED_BYTES) throw tooMuchForOneCode()
        val code = PREFIX + Base64.getUrlEncoder().encodeToString(deflate(raw))
        if (code.length > MAX_CODE_CHARS) throw tooMuchForOneCode()
        return code
    }

    private fun tooMuchForOneCode() = TooLarge("That is too much for one code. Share fewer notes at a time, " +
                                               "or send a backup instead: Settings → Backup.")

    /** What a chat app wraps a code with: whitespace, and the invisible break marks. */
    private fun wrapping(c: Char) =
        // Written by their numbers: the marks themselves are invisible, and an
        // editor may drop one without a trace (lint read the last as a BOM).
        c.isWhitespace() || c == Char(0x00AD) || c in Char(0x200B)..Char(0x200D) || c == Char(0x2060) ||
            c == Char(0xFEFF)

    /**
     * The share code inside [text], its wrapping taken out -- the desktop's
     * `code_in`: from its prefix to its padding, or to the first character a
     * code is not written in (`+` and `/` read as `-` and `_`), so that
     * "Hi! NOTE1:… Thanks" reads. Without padding, letters past the last
     * whole group of four are left (a word after it); a code that lost its
     * "=" still reads. [text], whitespace out, when it holds no prefix.
     */
    internal fun codeIn(text: String): String {
        val at = text.indexOf(PREFIX)
        if (at < 0) return text.filterNot { it.isWhitespace() }
        val body = StringBuilder()
        var padded = false
        for (i in at + PREFIX.length until text.length) {
            val c = text[i]
            if (wrapping(c)) continue
            if (c == '=') {
                padded = true
                body.append(c)
                continue
            }
            if (padded) break
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' -> body.append(c)
                c == '+' -> body.append('-')
                c == '/' -> body.append('_')
                else -> break
            }
        }
        if (!padded) body.setLength(body.length - body.length % 4)
        return PREFIX + body
    }

    fun decode(code: String): Decoded {
        val cleaned = codeIn(code)
        // The desktop's words, from decode_share_code, so the same damaged
        // code is described the same way on both devices.
        if (cleaned.isEmpty()) return Decoded.Failed("No code was pasted.")
        if (cleaned.length > MAX_CODE_CHARS) {
            return Decoded.Failed(
                "That code is far too large to import safely. For a transfer this " +
                    "large, send a backup instead: Settings → Backup → Export, then " +
                    "Import on the other device.")
        }
        if (!cleaned.startsWith(PREFIX)) {
            return Decoded.Failed(
                "This isn't a Note Organizer share code. A valid code starts with “$PREFIX”.")
        }
        val compressed = try {
            Base64.getUrlDecoder().decode(cleaned.substring(PREFIX.length))
        } catch (e: IllegalArgumentException) {
            return Decoded.Failed(
                "This share code is damaged or incomplete. Ask the sender to copy " +
                    "the whole code again — chat apps sometimes cut long codes short.")
        }
        val raw = inflate(compressed, MAX_DECOMPRESSED_BYTES)
            ?: return Decoded.Failed(
                "This share code is damaged, or too large to import safely. Ask the " +
                    "sender to send it again, or to send a backup instead (Settings → " +
                    "Backup → Export).")

        // A code holding a list is whole but holds no notes, as decode_share_code
        // says; a list is asked of by name, since org.json's own reader takes a
        // bare word for text where Python's refuses it.
        val payload = try {
            val text = Files.utf8(raw)
            if (PyText.lstrip(text).startsWith("[")) {
                Files.parseOutside(text) { JSONArray(it) }
                return Decoded.Failed(NO_NOTES)
            }
            if (PY_JSON_SCALAR.matches(text)) return Decoded.Failed(NO_NOTES)
            // Whole, as Python's reader takes it: anything after the object
            // is damage there, and org.json's reader stopped at the brace.
            Files.parseOutside(text) { whole ->
                val reader = JSONTokener(whole)
                val value = reader.nextValue() as? JSONObject ?: throw JSONException("not an object")
                if (reader.nextClean() != 0.toChar()) throw JSONException("text after the object")
                value
            }
        } catch (e: Exception) {
            return Decoded.Failed(DAMAGED)
        }
        if (!payload.has("notes")) return Decoded.Failed(NO_NOTES)
        // The version is the code's own claim, in text anyone can edit: a
        // string or a true there is damage, not version 1. JSON's true is a
        // Boolean here, never a Number, so the one test covers both.
        val version = payload.opt("v") ?: 1
        if (version !is Number) return Decoded.Failed(DAMAGED)
        if (version.toDouble() > 1) {
            return Decoded.Failed(
                "This code was made by a newer version of Note Organizer. " +
                    "Update the app to import it.")
        }
        val notes = payload.opt("notes") as? JSONArray
        if (notes == null || notes.length() == 0) return Decoded.Failed(NO_NOTES)
        if (notes.length() > MAX_SHARED_NOTES) {
            return Decoded.Failed(
                "This code contains ${notes.length()} notes, which is over the " +
                    "$MAX_SHARED_NOTES-note limit for a single code. Ask the sender to " +
                    "split it into smaller codes, or to send a backup instead " +
                    "(Settings → Backup → Export).")
        }
        // Through the SAME slice `encode` makes, so a hand-made code carries only
        // a real one's fields: no attachment reference whose `stored_as` could
        // later aim a delete outside the attachments folder.
        val out = ArrayList<JSONObject>(notes.length())
        for (i in 0 until notes.length()) {
            val note = notes.opt(i) as? JSONObject ?: JSONObject()
            out.add(shareableView(note))
        }
        return Decoded.Notes(out)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(9)
        deflater.setInput(input)
        deflater.finish()
        val out = java.io.ByteArrayOutputStream(input.size / 2 + 64)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    /**
     * Decompress, but never produce more than [limit] bytes.
     *
     * A few hundred bytes can expand into gigabytes, and a share code
     * arrives from wherever someone pasted it. Null means "refuse".
     */
    private fun inflate(input: ByteArray, limit: Int): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = java.io.ByteArrayOutputStream(minOf(input.size * 4, 1 shl 20))
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                if (out.size() > limit) return null
            }
        } catch (e: Exception) {
            return null
        } finally {
            inflater.end()
        }
        if (out.size() == 0) return null
        return out.toByteArray()
    }
}
