package ro.noteorganizer.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The editor's calculator: the desktop's `core/calculator.py`, answer for
 * answer, plus what its panel does with a key press and where a result
 * lands in the note.
 *
 * Three pieces, all without Android:
 *
 *  - [evaluate] reads a sum the way the desktop does -- `+ − × ÷` with the
 *    usual precedence, three interchangeable bracket styles, a prefix `√`,
 *    a right-associative `^` and a postfix `%` -- and gives the number, or
 *    null for anything that is not a sum. One null covers every failure:
 *    a stray character, an unclosed bracket, a division by zero.
 *  - [Pad] is the panel's display and its history, and what each key does
 *    to them: the desktop's `_calc_press`.
 *  - [place] is where an inserted calculation goes: at the cursor the first
 *    time, then each on its own line after the one before.
 *
 * Written by hand rather than handed to a script engine, as the desktop's
 * is: a pasted string is only ever arithmetic here, whatever it holds.
 *
 * Python's arithmetic is IEEE doubles, as Kotlin's is, so most of it ports
 * as written. The places where Python decides something the JVM decides
 * differently are each said where they happen: which characters are digits
 * and spaces, what a power does at the edges, how a result is rounded for
 * the display. `CalculatorTest` asks a few hundred sums of both and fails
 * on any answer that differs.
 */
object Calculator {

    /** What the display shows for a sum that has no answer. */
    const val ERROR = "Error"

    private const val OPEN = "({["
    private const val CLOSE = ")}]"

    /** Every character that is a whole token alone: the keys' own symbols. */
    private const val SINGLE = "+−×÷√^%" + OPEN + CLOSE

    /**
     * How deep a sum may nest before it is refused. The desktop's parser is
     * recursive, and Python stops it at about a thousand calls -- some
     * two hundred brackets inside each other -- where the desktop answers
     * nothing (it catches the RecursionError). The phone counts the same
     * calls, one per parsing step as Python makes them, and says Error a
     * little before that, so the deepest sum anyone
     * will paste is refused rather than taking the app down with a stack
     * overflow. Every sum the desktop answers below this depth, the phone
     * answers the same.
     */
    internal const val MAX_DEPTH = 900

    private class CalcError : Exception()

    /** Python's spaces, which the JVM's whitespace does not quite match. */
    private fun isSpace(cp: Int): Boolean = PyText.isSpace(cp)

    /**
     * The text cut into numbers and one-character symbols. A digit is any
     * decimal digit, in any script -- Python's `isdecimal()`, which a pasted
     * "٣" passes and a superscript "²" does not -- and it is read by its
     * value, since the JVM's number parser knows only 0-9.
     */
    private fun tokenize(expr: String): List<Any> {
        val cps = expr.codePoints().toArray()
        val tokens = ArrayList<Any>()
        var i = 0
        while (i < cps.size) {
            val cp = cps[i]
            if (isSpace(cp)) {
                i++
                continue
            }
            if (cp < 0x10000 && SINGLE.indexOf(cp.toChar()) >= 0) {
                tokens.add(cp.toChar().toString())
                i++
                continue
            }
            if (Character.isDigit(cp) || cp == '.'.code) {
                val ascii = StringBuilder()
                var seenDot = false
                var j = i
                // One number: the dot is allowed once, so "1.2.3" is two.
                while (j < cps.size && (Character.isDigit(cps[j]) || (cps[j] == '.'.code && !seenDot))) {
                    if (cps[j] == '.'.code) {
                        seenDot = true
                        ascii.append('.')
                    } else {
                        ascii.append(Character.digit(cps[j], 10))
                    }
                    j++
                }
                // A dot alone is not a number.
                tokens.add(ascii.toString().toDoubleOrNull() ?: throw CalcError())
                i = j
                continue
            }
            throw CalcError()
        }
        return tokens
    }

    private fun isOddInteger(x: Double): Boolean = Math.abs(x) % 2.0 == 1.0

    /**
     * `base ** exponent` as Python's float power gives it, edges and all:
     * CPython settles the special cases itself before the platform's pow is
     * asked, and the JVM's answers to some of them differ -- `1 ** inf` is 1
     * in Python and NaN on the JVM. Where Python raises (zero to a negative
     * power, an overflow) or turns complex (a negative number to a fraction),
     * this throws, as the desktop's parser then does.
     */
    private fun pyPow(base: Double, exponent: Double): Double {
        if (exponent == 0.0) return 1.0
        if (base.isNaN()) return base
        if (exponent.isNaN()) return if (base == 1.0) 1.0 else exponent
        if (exponent.isInfinite()) {
            val size = Math.abs(base)
            return when {
                size == 1.0 -> 1.0
                (exponent > 0.0) == (size > 1.0) -> Double.POSITIVE_INFINITY
                else -> 0.0
            }
        }
        if (base.isInfinite()) {
            val odd = isOddInteger(exponent)
            return if (exponent > 0.0) (if (odd) base else Math.abs(base))
                   else (if (odd) Math.copySign(0.0, base) else 0.0)
        }
        if (base == 0.0) {
            if (exponent < 0.0) throw CalcError()
            return if (isOddInteger(exponent)) base else 0.0
        }
        var magnitude = base
        var negate = false
        if (base < 0.0) {
            if (exponent != Math.floor(exponent)) throw CalcError()
            magnitude = -base
            negate = isOddInteger(exponent)
        }
        if (magnitude == 1.0) return if (negate) -1.0 else 1.0
        val result = Math.pow(magnitude, exponent)
        // Both sides finite, the answer not: Python's OverflowError. An
        // answer too small to hold becomes 0, which Python lets through.
        if (result.isInfinite()) throw CalcError()
        return if (negate) -result else result
    }

    /**
     * The sum's value, or null when it has none: empty, malformed, a root of
     * a negative number, a power with no real answer, a division by zero, an
     * answer too big to show. Anything that is not text is not a sum.
     */
    fun evaluate(expr: Any?): Double? {
        if (expr !is String) return null
        val tokens = try {
            tokenize(expr)
        } catch (e: CalcError) {
            return null
        }
        // Empty, or only spaces: nothing to work out.
        if (tokens.isEmpty()) return null
        return try {
            Parser(tokens).run()
        } catch (e: CalcError) {
            null
        }
    }

    /**
     * One function per level of precedence, each asking the next for its
     * operands -- that order IS the precedence -- exactly as the desktop's
     * nested functions do. Brackets start again from the top.
     */
    private class Parser(private val tokens: List<Any>) {
        private var pos = 0
        private var depth = 0

        private fun peek(): Any? = tokens.getOrNull(pos)
        private fun advance(): Any = tokens[pos++]

        /** One more call deep, counted as Python counts its frames. */
        private inline fun <T> nested(step: () -> T): T {
            if (++depth > MAX_DEPTH) throw CalcError()
            try {
                return step()
            } finally {
                depth--
            }
        }

        fun run(): Double? {
            val result = expression()
            // Something left over -- a stray closing bracket, "(3)2".
            if (pos != tokens.size) return null
            // An answer that overflowed on the way, with nothing raised.
            if (!result.isFinite()) return null
            return result
        }

        private fun expression(): Double = nested {
            var value = term()
            while (peek() == "+" || peek() == "−") {
                val op = advance()
                val rhs = term()
                value = if (op == "+") value + rhs else value - rhs
            }
            value
        }

        private fun term(): Double = nested {
            var value = percent()
            while (peek() == "×" || peek() == "÷") {
                val op = advance()
                val rhs = percent()
                if (op == "×") {
                    value *= rhs
                } else {
                    if (rhs == 0.0) throw CalcError()
                    value /= rhs
                }
            }
            value
        }

        // Postfix, on the whole power before it: "5^2%" is (5^2)%.
        private fun percent(): Double = nested {
            var value = power()
            while (peek() == "%") {
                advance()
                value /= 100
            }
            value
        }

        // A sign belongs here, so "−2^2" is −(2^2); the exponent is a power
        // itself, so "2^3^2" is 2^(3^2).
        private fun power(): Double = nested {
            when (peek()) {
                "−" -> {
                    advance()
                    -power()
                }
                "+" -> {
                    advance()
                    power()
                }
                else -> {
                    val base = unary()
                    if (peek() == "^") {
                        advance()
                        pyPow(base, power())
                    } else {
                        base
                    }
                }
            }
        }

        private fun unary(): Double = nested {
            val tok = peek()
            when {
                tok == "√" -> {
                    advance()
                    val operand = power()
                    if (operand < 0) throw CalcError()
                    Math.sqrt(operand)
                }
                tok is String && tok in OPEN -> {
                    advance()
                    val value = expression()
                    val next = peek()
                    if (!(next is String && next in CLOSE)) throw CalcError()
                    advance()
                    value
                }
                tok is Double -> {
                    advance()
                    tok
                }
                else -> throw CalcError()
            }
        }
    }

    /**
     * A result as the display shows it: rounded to ten places to hide the
     * float noise in 0.1+0.2, whole numbers without a point, no trailing
     * zeros. Python rounds a float on its exact binary value, half to even,
     * and so does [BigDecimal] built from the double -- which the JVM's own
     * `%.10f` does not.
     */
    fun format(value: Double): String {
        require(value.isFinite()) { "only an answer from evaluate() is shown" }
        val rounded = BigDecimal(value).setScale(10, RoundingMode.HALF_EVEN).toString().toDouble()
        if (rounded == Math.floor(rounded)) return BigDecimal(rounded).toBigInteger().toString()
        return BigDecimal(rounded).setScale(10, RoundingMode.HALF_EVEN).toPlainString()
            .trimEnd('0').trimEnd('.')
    }

    /**
     * The panel: what the display holds, whether it holds an answer just
     * worked out, the sum that answer came from, and this note's past
     * calculations, newest first. Nothing in it is saved with the note.
     */
    data class Pad(
        val display: String = "",
        val justEvaluated: Boolean = false,
        val lastExpr: String = "",
        val history: List<String> = emptyList(),
    ) {
        /** Insert is only for an answer "=" has just given, never a sum half typed. */
        val canInsert: Boolean get() = justEvaluated && display.isNotEmpty() && display != ERROR

        /** What Insert puts in the note: the whole sum, not only its answer. */
        val formula: String? get() = if (canInsert && lastExpr.isNotEmpty()) "$lastExpr=$display" else null

        /**
         * One key. An operator carries on from an answer ("8" then "×2");
         * a digit, a dot, a root or a bracket starts afresh after one.
         */
        fun press(key: String): Pad = when (key) {
            "C" -> copy(display = "", justEvaluated = false)
            "⌫" -> copy(display = if (justEvaluated) "" else dropLastCharacter(display), justEvaluated = false)
            "=" -> {
                if (display.isEmpty() || display == ERROR) {
                    this
                } else {
                    val result = evaluate(display)
                    if (result != null) {
                        val shown = format(result)
                        Pad(shown, true, display, listOf("$display=$shown") + history)
                    } else {
                        copy(display = ERROR, justEvaluated = false)
                    }
                }
            }
            "+", "−", "×", "÷", "^", "%" ->
                copy(display = (if (display == ERROR) "" else display) + key, justEvaluated = false)
            else ->
                copy(display = (if (justEvaluated || display == ERROR) "" else display) + key, justEvaluated = false)
        }

        /** Typed or pasted into the display: a sum again, until the next "=". */
        fun typed(text: String): Pad = copy(display = text, justEvaluated = false)

        fun clearHistory(): Pad = copy(history = emptyList())
    }

    /** One character off the end -- a whole one, not half of an emoji. */
    private fun dropLastCharacter(text: String): String =
        if (text.isEmpty()) text else text.substring(0, text.offsetByCodePoints(text.length, -1))

    /** The body after an insertion, and where the next one goes. */
    data class Placed(val body: String, val mark: Int)

    /**
     * Where a calculation lands in [body]: after the previous one, on a line
     * of its own, when there was one ([mark]); otherwise at the [caret], or
     * at the end when the cursor was never in the note. The desktop keeps the
     * same "own line, after the last insertion" place, so several sums in a
     * row build a list downwards instead of landing wherever the cursor is.
     *
     * Never inside a table: a place that falls in one moves to the line after
     * it, since a sum glued to a row would turn the row into something else.
     * That covers the empty field under a table that ends the note, too: it
     * sits one past the end, and what is written there starts a line.
     * Positions are counted in code points, as the file counts them.
     */
    fun place(body: String, formula: String, mark: Int?, caret: Int?): Placed {
        val length = body.codePointCount(0, body.length)
        var at = (mark ?: caret ?: length).coerceIn(0, length)
        var text = if (mark != null) "\n" + formula else formula
        for (segment in Editing.segments(body)) {
            // Nor inside a line too long to lay out, shown read-only.
            if (segment.field) continue
            val end = segment.cpStart + segment.text.codePointCount(0, segment.text.length)
            if (at in segment.cpStart..end) {
                at = end
                if (!text.startsWith("\n")) text = "\n" + text
            }
        }
        val cut = body.offsetByCodePoints(0, at)
        val next = body.substring(0, cut) + text + body.substring(cut)
        return Placed(next, at + text.codePointCount(0, text.length))
    }

    /**
     * Where a remembered place is after an edit somewhere in the body: the
     * edit is found by what did not change, as [remapSpans] finds it. A place
     * before the edit stays, one after it moves with the text, and one inside
     * it goes to the end of what was written there -- so typing right at the
     * last insertion's end pushes the next one after the typing, as a text
     * mark with right gravity does on the desktop.
     */
    fun follow(old: String, new: String, at: Int?): Int? = follow(listOfNotNull(changeOf(old, new)), at)

    /**
     * The one change from [old] to [new], found by what did not change: at
     * the first place they differ, what was taken out and what was put in,
     * in code points. Null when they are the same. Exact for a change made
     * in one place -- a letter typed, a paste, a cut -- and nothing else: a
     * list's markers put on three lines read as one change from the first to
     * the last, and a place between them went to the end of it.
     */
    fun changeOf(old: String, new: String): Paragraphs.Edit? {
        if (old == new) return null
        val a = old.codePoints().toArray()
        val b = new.codePoints().toArray()
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix &&
            a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
        ) suffix++
        return Paragraphs.Edit(prefix, a.size - suffix - prefix, b.size - suffix - prefix)
    }

    /**
     * A remembered place carried through [edits], each made in the text the
     * one before it left -- as the desktop's Tk mark moves through each
     * insert and delete of an operation in turn ([Paragraphs.moveMark]). A
     * list's markers, Enter in a numbered list, a paste that renumbers: one
     * place followed as if the change were one, the calculator's next answer
     * went into a later line -- "3×3=9Rent", or into "4." (the final audit's
     * ninth round).
     */
    fun follow(edits: List<Paragraphs.Edit>, at: Int?): Int? =
        at?.let { edits.fold(it) { x, e -> Paragraphs.moveMark(x, e) } }

    /**
     * ...and back through them, as Ctrl+Z replays each one's opposite, the
     * last first: an Undo of a step that changed two places put the place
     * between them at the end of the later one.
     */
    fun back(edits: List<Paragraphs.Edit>, at: Int?): Int? =
        at?.let { edits.asReversed().fold(it) { x, e -> Paragraphs.moveMark(x, Paragraphs.Edit(e.at, e.inserted, e.removed)) } }
}
