package ro.noteorganizer.app

/**
 * Python's idea of a space, for the ports that must answer as the desktop's
 * Python does.
 *
 * The JVM's whitespace and Python's differ by a character or two: Kotlin's
 * `trim()` keeps the next-line character U+0085, which Python's `strip()`
 * removes. Where a port is checked against the desktop's own answers, that
 * one character is a difference, so the ports that need Python's exact
 * answer ask here. (`Recurrence` spells the same set as a regex class.)
 */
object PyText {

    /** `str.isspace()`, one code point at a time. */
    fun isSpace(cp: Int): Boolean =
        cp in 0x09..0x0D || cp in 0x1C..0x20 || cp == 0x85 || cp == 0xA0 || cp == 0x1680 ||
            cp in 0x2000..0x200A || cp == 0x2028 || cp == 0x2029 || cp == 0x202F ||
            cp == 0x205F || cp == 0x3000

    /** `str.strip()`. */
    fun strip(text: String): String {
        var from = 0
        var to = text.length
        while (from < to) {
            val cp = text.codePointAt(from)
            if (!isSpace(cp)) break
            from += Character.charCount(cp)
        }
        while (to > from) {
            val cp = text.codePointBefore(to)
            if (!isSpace(cp)) break
            to -= Character.charCount(cp)
        }
        return text.substring(from, to)
    }
}
