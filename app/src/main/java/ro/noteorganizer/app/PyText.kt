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

    /** `str.split()` with no separator: the words between runs of Python's spaces, none empty. */
    fun split(text: String): List<String> {
        val words = ArrayList<String>()
        val word = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isSpace(cp)) {
                if (word.isNotEmpty()) words.add(word.toString())
                word.setLength(0)
            } else {
                word.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        if (word.isNotEmpty()) words.add(word.toString())
        return words
    }

    /**
     * `needle in haystack`, in time that grows with the two lengths added,
     * not multiplied (Knuth-Morris-Pratt). Python's `in` is about linear;
     * the JVM's `contains` compares again from each place, and four million
     * letters searched for a near-miss of twenty thousand took fourteen
     * seconds a call where the desktop took twelve milliseconds.
     */
    fun contains(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        if (needle.length > haystack.length) return false
        // For each place in the needle, how much of its start is also the
        // end of what came before it: where a mismatch can go on from.
        val back = IntArray(needle.length)
        var k = 0
        for (i in 1 until needle.length) {
            while (k > 0 && needle[i] != needle[k]) k = back[k - 1]
            if (needle[i] == needle[k]) k++
            back[i] = k
        }
        k = 0
        for (c in haystack) {
            while (k > 0 && c != needle[k]) k = back[k - 1]
            if (c == needle[k]) k++
            if (k == needle.length) return true
        }
        return false
    }

    /** Where [text]'s leading Python spaces end. */
    private fun spacesEnd(text: String): Int {
        var from = 0
        while (from < text.length) {
            val cp = text.codePointAt(from)
            if (!isSpace(cp)) break
            from += Character.charCount(cp)
        }
        return from
    }

    /** `str.lstrip()`. */
    fun lstrip(text: String): String = text.substring(spacesEnd(text))

    /** `str.strip()`. */
    fun strip(text: String): String {
        val from = spacesEnd(text)
        var to = text.length
        while (to > from) {
            val cp = text.codePointBefore(to)
            if (!isSpace(cp)) break
            to -= Character.charCount(cp)
        }
        return text.substring(from, to)
    }
}
