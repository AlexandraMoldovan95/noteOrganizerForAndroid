package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The two things every data file here is written and rescued with.
 *
 * One copy of each, for the notes, the Trash, the settings and the
 * templates alike: the desktop's history is full of a guard that one door
 * had and its neighbour did not.
 */
object Files {

    /**
     * Dump to a sibling `.tmp`, force it to the disk, then rename over the
     * real file: rename is atomic within a filesystem, so a crash leaves the
     * old file whole and a half-written one never carries the real name. The
     * `sync()` is one step past the desktop's, on purpose: a phone's process
     * is killed far more casually, so "written" means "on the flash".
     *
     * @return null when it worked, otherwise the reason.
     */
    fun writeAtomic(file: File, bytes: ByteArray): String? {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
        } catch (e: Exception) {
            tmp.delete()
            return e.message ?: e.toString()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return "could not replace ${file.name}"
        }
        return null
    }

    fun writeAtomic(file: File, text: String): String? =
        writeAtomic(file, text.toByteArray(Charsets.UTF_8))

    /**
     * Copies a file that exists but cannot be read aside, before anything
     * can write over it: `notes_data_corrupt_20261015_143000.json` beside
     * the original, never deleted here -- the desktop's
     * `preserve_corrupt_file`, name and all. The same bytes are kept once:
     * read again at every start, the file left one more copy each time.
     *
     * @return the copy (an earlier one holding the same bytes, if there is
     *   one), or null if even the copy failed -- in which case the caller
     *   must not write over the original.
     */
    fun preserveCorrupt(file: File): File? {
        val held = try {
            file.readBytes()
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            // Too large to hold: not copied, so not written over either.
            return null
        }
        val folder = file.parentFile ?: return null
        val root = file.name.substringBeforeLast('.', file.name)
        val ext = if (file.name.contains('.')) "." + file.name.substringAfterLast('.') else ".json"
        val prefix = root + "_corrupt_"
        val earlier = folder.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.endsWith(ext) }
            ?.sortedBy { it.name }
            .orEmpty()
        for (kept in earlier) {
            if (kept.length() == held.size.toLong() &&
                runCatching { kept.readBytes() }.getOrNull()?.contentEquals(held) == true
            ) {
                return kept
            }
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val copy = File(folder, "$prefix$stamp$ext")
        return if (writeAtomic(copy, held) == null) copy else null
    }

    /** "YYYY-MM-DD HH:MM:SS", the stamp every note and setting carries. */
    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    /**
     * How deep JSON from outside may nest. A note's deepest place -- a day's
     * own edit inside a backup's rescue copy -- is under ten brackets down;
     * a hundred leaves room for anything an app writes, and stays far
     * inside the stack of every thread that parses.
     */
    const val MAX_JSON_DEPTH = 100

    /** JSON from outside that cannot be read, said in words of our own: never the parser's. */
    class Unreadable(message: String) : Exception(message)

    /**
     * JSON that came from outside -- a share code, the files a backup
     * brings -- parsed by [parse], or [Unreadable]. The nesting is measured
     * before parsing, as org.json goes a call deeper per bracket and a stack
     * overflow is an Error no `catch (e: Exception)` sees; one that happens
     * all the same is damage too, as on the desktop (ISSUES_LOG, "A million
     * brackets past a catch for exceptions").
     */
    fun <T> parseOutside(text: String, parse: (String) -> T): T {
        if (nestedPast(text, MAX_JSON_DEPTH)) throw Unreadable(TOO_DEEP)
        return try {
            parse(text).also { finiteOnly(it) }
        } catch (e: StackOverflowError) {
            throw Unreadable(TOO_DEEP)
        } catch (e: OutOfMemoryError) {
            // What it built is let go with the call: a backup too big for
            // the phone is refused, and the app goes on.
            throw Unreadable(TOO_LARGE)
        }
    }

    /**
     * Every number in [value] that is no number -- NaN, or infinity, which
     * `1e999` reads as -- made null, in place, all the way down: org.json
     * takes them in an array unchecked, and toString() of what holds them
     * gives null (a template's `[NaN]` checklist stopped an import). The
     * desktop's normalize drops them as it drops a null.
     */
    fun finiteOnly(value: Any?) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) {
                val item = value.opt(i)
                if (item is Number && !item.toDouble().isFinite()) value.put(i, JSONObject.NULL) else finiteOnly(item)
            }
            is JSONObject -> for (key in value.keys().asSequence().toList()) {
                val item = value.opt(key)
                if (item is Number && !item.toDouble().isFinite()) value.put(key, JSONObject.NULL) else finiteOnly(item)
            }
        }
    }

    /**
     * One of the app's own files -- the notes, the Trash, the settings, the
     * templates -- parsed as [parseOutside] parses what comes from outside:
     * a backup Android put back or the desktop's files copied in can be
     * nested past the stack or too large, and an Error no catch saw stopped
     * the app at every start. The desktop's JSON_UNREADABLE, ported.
     */
    fun <T> parseOwn(file: File, parse: (String) -> T): T = parseOutside(readData(file), parse)

    /** A data file's text, or [Unreadable] -- see [utf8]. */
    fun readData(file: File): String {
        val bytes = try {
            file.readBytes()
        } catch (e: OutOfMemoryError) {
            throw Unreadable(TOO_LARGE)
        }
        return utf8(bytes)
    }

    /**
     * Bytes as the UTF-8 every file and code here is written in, or
     * [Unreadable]. Read leniently, a file saved in another encoding -- a
     * notes file kept in Windows' Romanian code page, say -- came in with
     * its letters turned into replacement marks, and the next save wrote
     * those over the person's words; the desktop counts such bytes as
     * damage (UnicodeDecodeError), and so does this.
     */
    fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        throw Unreadable(NOT_UTF8)
    } catch (e: OutOfMemoryError) {
        throw Unreadable(TOO_LARGE)
    }

    private const val TOO_DEEP = "It is nested deeper than any note."
    const val TOO_LARGE = "It is too large for this phone to read."
    private const val NOT_UTF8 = "Its letters are not written in UTF-8, the way notes are kept."

    /** Whether [text]'s brackets, outside its strings, nest past [limit]: one pass, nothing parsed. */
    fun nestedPast(text: String, limit: Int): Boolean {
        var depth = 0
        var quote = 0.toChar()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != 0.toChar()) {
                if (c == '\\') i++ else if (c == quote) quote = 0.toChar()
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '[' || c == '{') {
                if (++depth > limit) return true
            } else if (c == ']' || c == '}') {
                depth--
            }
            i++
        }
        return false
    }
}
