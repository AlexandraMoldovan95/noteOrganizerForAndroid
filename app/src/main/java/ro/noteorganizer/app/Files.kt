package ro.noteorganizer.app

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
     * real file. A crash mid-write leaves the old file whole, because
     * rename is atomic within a filesystem; a half-written file never
     * carries the real name.
     *
     * The `sync()` is one step stronger than the desktop's, on purpose: a
     * phone's process is killed far more casually than a PC's, so
     * "written" has to mean "on the flash", not "in a buffer".
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
     * the original, never deleted by anything here.
     *
     * The desktop's `preserve_corrupt_file`, name and all, so a person who
     * learnt where to look on one device finds it on the other. The same
     * bytes are kept once: a file nobody can read is read again at every
     * start, and each start used to keep one more identical copy.
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
     * first, before any parsing: Android's org.json goes one call deeper per
     * bracket, and a million of them ended the app with StackOverflowError,
     * an Error that no `catch (e: Exception)` sees. A parser that runs out of
     * stack all the same -- through a comment or a quote the measure reads
     * differently -- is damage too, as the desktop's JSON_UNREADABLE counts
     * RecursionError (ISSUES_LOG, "A million brackets past a catch for
     * exceptions").
     */
    fun <T> parseOutside(text: String, parse: (String) -> T): T {
        if (nestedPast(text, MAX_JSON_DEPTH)) throw Unreadable(TOO_DEEP)
        return try {
            parse(text)
        } catch (e: StackOverflowError) {
            throw Unreadable(TOO_DEEP)
        }
    }

    private const val TOO_DEEP = "It is nested deeper than any note."

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
