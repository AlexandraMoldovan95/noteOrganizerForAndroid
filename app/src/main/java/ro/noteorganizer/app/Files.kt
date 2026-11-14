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
     * Files this run writes nothing over: one it could neither read nor copy
     * aside (the desktop's `hold_writes`). Settings and templates were
     * written over then, their bytes in no other copy; the notes alone held.
     */
    private val writesHeld = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun heldKey(file: File): String =
        try { file.canonicalPath } catch (e: Exception) { file.absolutePath }

    internal fun holdWrites(file: File) { writesHeld.add(heldKey(file)) }

    internal fun writesHeld(file: File): Boolean = heldKey(file) in writesHeld

    /**
     * Whether [e], met reading one of the app's own files, is the system
     * refusing the read (a storage error), not damage: such a file is held,
     * as the desktop's is, never copied aside -- a second read might succeed,
     * take a good file aside, and the next write replace it.
     */
    internal fun refusedRead(e: Exception): Boolean = e is java.io.IOException

    /** Why a held [file] takes no write, in the words every refusal of it uses. */
    internal fun heldReason(file: File): String =
        "${file.name} could not be read or copied aside, so nothing is written over it until " +
            "the app is closed and opened again"

    /**
     * Files a read in this run found unreadable and set aside: the desktop's
     * `_UNREAD_AT_LOAD`, kept, as there, until the next start. A run that
     * never held the notes file's notes deletes no attached file one of them
     * may use, a later reload of the file it wrote since included.
     */
    private val unreadAtLoad = java.util.Collections.synchronizedSet(HashSet<String>())

    internal fun markUnread(file: File) { unreadAtLoad.add(heldKey(file)) }

    /**
     * A new start's reading begins clean ([Startup.prepare]): the unread marks
     * and the holds of an earlier one in this process (Android 8 to 11 reopens
     * in it), each set again if its file still does not read. Held past it,
     * a file that now read fine refused every write.
     */
    internal fun newStart() {
        unreadAtLoad.clear()
        writesHeld.clear()
    }

    internal fun unreadAtLoad(file: File): Boolean = heldKey(file) in unreadAtLoad

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
        if (writesHeld(file)) return heldReason(file)
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
        } catch (e: Exception) {
            tmp.delete()
            // The cause in words of its own: the system's named the app's private path.
            return if (storageFull(e)) STORAGE_FULL else STORAGE_REFUSED
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return "could not replace ${file.name}"
        }
        return null
    }

    const val STORAGE_FULL = "the phone's storage is full"
    const val STORAGE_REFUSED = "the phone's storage refused the write"

    /** Whether [e] is the storage running out. */
    fun storageFull(e: Throwable): Boolean = (e.message ?: "").let { "ENOSPC" in it || "No space left" in it }

    /** Whether [reason], from [writeAtomic], is a bare cause, saying nothing of what went unsaved. */
    fun bareCause(reason: String): Boolean =
        reason == STORAGE_FULL || reason == STORAGE_REFUSED || reason.startsWith("could not replace ")

    /**
     * [e]'s words for a person: the system's own, unless the storage is full or they
     * name a path under the app's private folder [private] ([instead] then).
     */
    fun said(e: Throwable, private: File?, instead: String = STORAGE_REFUSED): String {
        val words = e.message ?: e.toString()
        return when {
            storageFull(e) -> STORAGE_FULL
            private != null && private.path in words -> instead
            else -> words
        }
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
     *   one), or null if even the copy failed -- and then nothing is written
     *   over the original ([holdWrites]) until a later read copies it aside:
     *   the app opened again on Android 8 to 11, where Back ends the activity
     *   and not the process. Safe since a settings file read so holds its
     *   marks in memory ([Settings.load]), which every later write carries.
     */
    fun preserveCorrupt(file: File): File? {
        val kept = copyAside(file)
        if (kept == null) holdWrites(file) else writesHeld.remove(heldKey(file))
        return kept
    }

    private fun copyAside(file: File): File? {
        // Through the seam the first read took: a test making a refusal last
        // saw this read get through, where a phone's fails and holds the file.
        val held = try {
            readBytesOf(file)
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

    /** How a data file's bytes are read: tests make it refuse, as a storage error does. */
    internal var readBytesOf: (File) -> ByteArray = { it.readBytes() }

    /** A data file's text, or [Unreadable] -- see [utf8]. */
    fun readData(file: File): String {
        val bytes = try {
            readBytesOf(file)
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

    /**
     * A shared file's text, up to [most] characters, and whether there was
     * more; null when it holds no text as Python's strip() judges it. In the
     * encoding its first bytes name (UTF-16 read as UTF-8 came in as letters
     * and NULs), and strictly: a code page's letters (Notepad's old "ANSI")
     * became replacement marks; it throws instead.
     */
    fun sharedText(raw: java.io.InputStream, most: Int): Pair<String, Boolean>? {
        val stream = java.io.BufferedInputStream(raw)
        stream.mark(3)
        val head = ByteArray(3)
        var got = 0
        while (got < 3) {
            val n = stream.read(head, got, 3 - got)
            if (n < 0) break
            got += n
        }
        val b = IntArray(3) { if (it < got) head[it].toInt() and 0xFF else -1 }
        stream.reset()
        val (charset, skip) = when {
            b[0] == 0xEF && b[1] == 0xBB && b[2] == 0xBF -> Charsets.UTF_8 to 3L
            b[0] == 0xFF && b[1] == 0xFE -> Charsets.UTF_16LE to 2L
            b[0] == 0xFE && b[1] == 0xFF -> Charsets.UTF_16BE to 2L
            else -> Charsets.UTF_8 to 0L
        }
        stream.skip(skip)
        val reader = java.io.InputStreamReader(stream, charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))
        val buffer = CharArray(most)
        var read = 0
        while (read < buffer.size) {
            val n = reader.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        // Past what is read, a damaged byte is only "more".
        val more = read == buffer.size && try { reader.read() >= 0 } catch (e: java.io.IOException) { true }
        // An emoji cut in half at the end: its first half alone was saved as "?".
        if (more && Character.isHighSurrogate(buffer[read - 1])) read -= 1
        // NULs some apps pad a file's end with are left out; one among the
        // letters (UTF-16 without its BOM, read as UTF-8) refuses it.
        val text = String(buffer, 0, read).trimEnd('\u0000')
        if (text.indexOf('\u0000') >= 0) throw java.io.CharConversionException("a NUL: not text")
        return (text to more).takeIf { PyText.strip(text).isNotEmpty() }
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
