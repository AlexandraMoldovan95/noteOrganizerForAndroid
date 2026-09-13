package com.example.noteorganizer

import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import org.json.JSONArray
import org.json.JSONObject

/**
 * Share codes: moving notes between devices as copy-paste text.
 *
 * A code is a small JSON payload, zlib-compressed, Base64'd, behind a
 * version prefix. Measured on a real notebook, one note comes to 630
 * characters at the median and 72 of 77 fit under 2000 — small enough for
 * a message or an email, which is the whole point.
 *
 * **This is not encryption.** Anyone holding the code can read the note.
 * It is a compact, pasteable spelling of the same text, nothing more.
 *
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
        // "uid" travels; "id" does not. An id is only a note's place in
        // one file and means nothing anywhere else. The uid is what lets
        // the far end recognise a note it already has instead of guessing
        // from the text -- a guess that fails exactly when the note was
        // edited, which is the only time anyone re-sends one.
        "uid",
        "title", "body", "formatting", "comments", "category", "date",
        "color", "recurrence", "paragraphs", "checklist",
    )

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

    fun encode(notes: List<JSONObject>): String {
        val payload = JSONObject()
        payload.put("v", 1)
        val array = JSONArray()
        for (note in notes) array.put(shareableView(note))
        payload.put("notes", array)
        // A lone surrogate here would fail the UTF-8 encoding below. The
        // desktop hit exactly this: an emoji typed but not yet saved took
        // the crash path on sharing, because mending only ran on save.
        val raw = mendSurrogates(payload.toString()).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().encodeToString(deflate(raw))
    }

    fun decode(code: String): Decoded {
        // Chat apps wrap long codes; whitespace anywhere is not damage.
        val cleaned = code.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return Decoded.Failed("Nu ai lipit niciun cod.")
        if (cleaned.length > MAX_CODE_CHARS) {
            return Decoded.Failed("Codul e prea mare ca sa fie importat in siguranta.")
        }
        if (!cleaned.startsWith(PREFIX)) {
            return Decoded.Failed(
                "Asta nu e un cod Note Organizer. Un cod valid incepe cu “$PREFIX”.")
        }
        val compressed = try {
            Base64.getUrlDecoder().decode(cleaned.substring(PREFIX.length))
        } catch (e: IllegalArgumentException) {
            return Decoded.Failed(
                "Codul e deteriorat sau incomplet. Cere-l din nou intreg — " +
                    "aplicatiile de chat taie uneori codurile lungi.")
        }
        val raw = inflate(compressed, MAX_DECOMPRESSED_BYTES)
            ?: return Decoded.Failed(
                "Codul e deteriorat, sau prea mare ca sa fie importat in siguranta.")

        val payload = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (e: Exception) {
            return Decoded.Failed("Codul e deteriorat sau incomplet.")
        }
        val version = payload.opt("v")
        if (version is Number && version.toInt() > 1) {
            return Decoded.Failed(
                "Codul e facut de o versiune mai noua a aplicatiei.")
        }
        val notes = payload.opt("notes") as? JSONArray
        if (notes == null || notes.length() == 0) {
            return Decoded.Failed("Codul nu contine nicio notita.")
        }
        if (notes.length() > MAX_SHARED_NOTES) {
            return Decoded.Failed(
                "Codul contine ${notes.length()} notite, peste limita de " +
                    "$MAX_SHARED_NOTES pentru un singur cod.")
        }
        // Projected through the SAME slice `encode` produces, so a
        // hand-crafted code can only carry the fields a real one would.
        // This is the boundary that stops an imported note smuggling in an
        // attachment reference -- one whose `stored_as` could later aim a
        // delete at a file outside the attachments folder.
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
