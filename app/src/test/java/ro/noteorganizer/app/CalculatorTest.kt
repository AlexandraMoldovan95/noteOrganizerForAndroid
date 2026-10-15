package ro.noteorganizer.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's calculator. The first half is the desktop's own
 * `test_calculator.py`, case for case; then the fixture of sums the
 * desktop's evaluator answered; then the panel's keys and where a result
 * lands in the note.
 */
class CalculatorTest {

    private fun ev(expr: Any?) = Calculator.evaluate(expr)
    private fun fmt(value: Double) = Calculator.format(value)

    // ---- test_calculator.py ----

    @Test
    fun `the four operations`() {
        assertEquals(8.0, ev("5+3"))
        assertEquals(6.0, ev("10−4"))
        assertEquals(42.0, ev("6×7"))
        assertEquals(5.0, ev("20÷4"))
    }

    @Test
    fun `times and divide before plus and minus`() {
        assertEquals(11.0, ev("5+3×2"))
        assertEquals(4.0, ev("10−2×3"))
        assertEquals(4.0, ev("2+6÷3"))
    }

    @Test
    fun `brackets, nested and side by side`() {
        assertEquals(16.0, ev("(5+3)×2"))
        assertEquals(25.0, ev("5×(3+2)"))
        assertEquals(9.0, ev("((1+2)×3)"))
        assertEquals(15.0, ev("(2+3)×(4−1)"))
        assertEquals(2.0, ev("10÷(2+3)"))
    }

    @Test
    fun `a minus sign at the start and after an operator`() {
        assertEquals(-2.0, ev("−5+3"))
        assertEquals(-10.0, ev("5×−2"))
    }

    @Test
    fun `decimals`() {
        assertEquals(4.0, ev("1.5+2.5"))
        assertEquals(2.5, ev("5÷2"))
    }

    @Test
    fun `the three bracket styles are one, even mismatched`() {
        assertEquals(16.0, ev("{5+3}×2"))
        assertEquals(16.0, ev("[5+3]×2"))
        assertEquals(8.0, ev("(5+3]"))
        assertEquals(15.0, ev("[(2+3)×{4−1}]"))
    }

    @Test
    fun `the square root, and none for a negative number`() {
        assertEquals(3.0, ev("√9"))
        assertEquals(3.0, ev("√(4+5)"))
        assertNull(ev("√(0−9)"))
    }

    @Test
    fun `powers - right to left, under a sign, over plus and times`() {
        assertEquals(25.0, ev("5^2"))
        assertEquals(27.0, ev("3^3"))
        assertEquals(1024.0, ev("2^10"))
        assertEquals(25.0, ev("(2+3)^2"))
        assertEquals("2^(3^2), not (2^3)^2", 512.0, ev("2^3^2"))
        assertEquals("−(2^2)", -4.0, ev("−2^2"))
        assertEquals(5.0, ev("1+2^2"))
        assertEquals(18.0, ev("2×3^2"))
        assertNull("a negative number to a fraction has no real answer", ev("(0−8)^0.5"))
        assertNull("zero to a negative power", ev("0^(0−1)"))
    }

    @Test
    fun `percent`() {
        assertEquals(0.5, ev("50%"))
        assertEquals("the desktop's percent is plain /100, not a share of 200", 200.1, ev("200+10%"))
        assertEquals("on the whole power before it", 0.25, ev("5^2%"))
    }

    @Test
    fun `division by zero has no answer`() {
        assertNull(ev("5÷0"))
        assertNull(ev("1+5÷0"))
        // Refused where it happens, not left to become infinity: divided
        // away again, an infinity would come back as an answer.
        assertNull(ev("1÷(1÷0)"))
        assertNull(ev("(1÷0)^0"))
    }

    @Test
    fun `a root of a negative number is refused where it happens`() {
        // NaN would also end as no answer -- unless a power of 0 turned it
        // back into 1 on the way.
        assertNull(ev("(√(0−9))^0"))
    }

    @Test
    fun `a long sum is a loop, not a nesting`() {
        assertEquals(2001.0, ev("1+".repeat(2000) + "1"))
        assertEquals(1.0, ev("2÷2".plus("×1".repeat(3000))))
    }

    @Test
    fun `what is not a sum has no answer`() {
        for (bad in listOf("", "   ", "5+", "(5+3", "5+3)", "abc", ".", "5+.")) {
            assertNull("“$bad”", ev(bad))
        }
        assertNull(ev(null))
        assertNull(ev(42))
    }

    @Test
    fun `an answer too big to show has none`() {
        assertNull(ev("9".repeat(400)))
        assertNull(ev("9".repeat(350) + "×" + "9".repeat(350)))
    }

    @Test
    fun `the display - whole numbers clean, float noise hidden`() {
        assertEquals("4", fmt(4.0))
        assertEquals("-10", fmt(-10.0))
        assertEquals("2.5", fmt(2.5))
        assertEquals("0.3", fmt(ev("0.1+0.2")!!))
    }

    // ---- what the desktop's Python answers, and the JVM would not ----

    @Test
    fun `digits from any script count, a superscript does not`() {
        assertEquals("an Arabic-Indic three", 4.0, ev("٣+1"))
        assertEquals("a digit outside the basic plane", 10.0, ev("𝟗+1"))
        assertNull(ev("2²"))
    }

    @Test
    fun `the spaces Python counts are spaces, and keyboard signs are not keys`() {
        assertEquals(8.0, ev("5 + 3"))
        assertEquals(8.0, ev("5\u0085+3"))
        assertNull("the desktop takes its own − only", ev("5-3"))
        assertNull(ev("5*3"))
    }

    @Test
    fun `the edges of a power, as Python settles them`() {
        assertEquals("1 to an infinite power is 1, where the JVM says NaN", 1.0, ev("1^" + "9".repeat(400)))
        assertEquals(1.0, ev("(0−1)^" + "9".repeat(400)))
        assertEquals("anything to 0, even infinity", 1.0, ev("9".repeat(400) + "^0"))
        assertEquals("even NaN", 1.0, ev("(" + "9".repeat(400) + "−" + "9".repeat(400) + ")^0"))
        assertEquals(-8.0, ev("(0−2)^3"))
        assertNull("an overflowing power is an error, not infinity divided away", ev("1÷10^400"))
        assertEquals("too small to hold is only 0", 0.0, ev("0.5^1075"))
    }

    @Test
    fun `the display rounds half to even on the exact value`() {
        assertEquals("1/2048 ends in ...125: a true tie at ten places", "0.0004882812", fmt(1.0 / 2048))
        assertEquals("123456789.1234567761", fmt(123456789.12345678))
        assertEquals("0", fmt(-0.0))
        assertEquals("0", fmt(ev("0×−1")!!))
        assertEquals("10000000000000000000000", fmt(1e22))
    }

    @Test
    fun `nesting the desktop answers is answered, nesting it raises on is refused`() {
        assertEquals(1.0, ev("(".repeat(150) + "1" + ")".repeat(150)))
        assertEquals(1.0, ev("−".repeat(800) + "1"))
        // Python raises RecursionError here; the phone says Error rather
        // than overflowing its stack.
        assertNull(ev("(".repeat(250) + "1" + ")".repeat(250)))
        assertNull(ev("(".repeat(50_000) + "1" + ")".repeat(50_000)))
        assertNull(ev("√".repeat(50_000) + "4"))
    }

    // ---- the fixture: what the desktop's own evaluator answered ----

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("calculator_cases.json")
        assertNotNull("calculator_cases.json is missing - rebuild it with tools/make_calculator_fixture.py", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    /**
     * The answer, to the last bit for everything but a fractional power:
     * there the platform's own pow is asked, on both sides, and two
     * platforms may round its last bit apart. What the display shows must
     * match exactly either way.
     */
    @Test
    fun `every sum in the fixture is answered as the desktop answered it`() {
        val sums = fixture().getJSONArray("sums")
        assertTrue("the fixture is too small to mean anything", sums.length() > 500)
        val wrong = ArrayList<String>()
        var answered = 0
        for (i in 0 until sums.length()) {
            val case = sums.getJSONObject(i)
            val expr = case.getString("expr")
            val want = if (case.isNull("value")) null else case.getString("value").toDouble()
            val got = ev(expr)
            val shown = got?.let { fmt(it) }
            val same = when {
                want == null || got == null -> want == got
                "^" in expr -> Math.abs(want - got) <= 2 * Math.ulp(want)
                else -> want.toRawBits() == got.toRawBits()
            }
            if (!same || shown != (if (case.isNull("shown")) null else case.getString("shown"))) {
                wrong.add("“${expr.take(60)}”: desktop ${case.opt("value")} / ${case.opt("shown")}, phone $got / $shown")
            }
            if (got != null) answered++
        }
        assertEquals(wrong.take(15).joinToString("\n"), 0, wrong.size)
        assertTrue("most of the fixture should be sums with an answer", answered > 300)
    }

    @Test
    fun `every value in the fixture is shown as the desktop shows it`() {
        val shown = fixture().getJSONArray("shown")
        val wrong = ArrayList<String>()
        for (i in 0 until shown.length()) {
            val case = shown.getJSONObject(i)
            val value = case.getString("value").toDouble()
            val got = fmt(value)
            if (got != case.getString("shown")) wrong.add("${case.getString("value")}: desktop ${case.getString("shown")}, phone $got")
        }
        assertEquals(wrong.take(15).joinToString("\n"), 0, wrong.size)
    }

    // ---- the panel's keys: the desktop's _calc_press ----

    private fun keys(vararg pressed: String, from: Calculator.Pad = Calculator.Pad()) =
        pressed.fold(from) { pad, key -> pad.press(key) }

    @Test
    fun `equals shows the answer, keeps the sum, and writes the history newest first`() {
        val pad = keys("5", "+", "3", "=", "C", "2", "×", "4", "=")
        assertEquals("8", pad.display)
        assertTrue(pad.canInsert)
        assertEquals("2×4=8", pad.formula)
        assertEquals(listOf("2×4=8", "5+3=8"), pad.history)
    }

    @Test
    fun `an operator carries on from an answer, a digit starts afresh`() {
        assertEquals("8×2", keys("5", "+", "3", "=", "×", "2").display)
        assertEquals("7", keys("5", "+", "3", "=", "7").display)
        assertEquals("(", keys("5", "+", "3", "=", "(").display)
        assertEquals("√", keys("5", "+", "3", "=", "√").display)
    }

    @Test
    fun `a sum with no answer says Error, and the next key clears it`() {
        val error = keys("5", "÷", "0", "=")
        assertEquals(Calculator.ERROR, error.display)
        assertFalse(error.canInsert)
        assertNull(error.formula)
        assertEquals("4", error.press("4").display)
        assertEquals("+", error.press("+").display)
        assertEquals("equals on Error does nothing", error, error.press("="))
        assertEquals("history keeps only answers", emptyList<String>(), error.history)
    }

    @Test
    fun `backspace takes one character, or the whole answer`() {
        assertEquals("12", keys("1", "2", "3", "⌫").display)
        assertEquals("the whole answer, not its last digit", "", keys("9", "+", "3", "=", "⌫").display)
        assertEquals("a whole emoji, not half of one", "5", Calculator.Pad(display = "5😀").press("⌫").display)
        assertEquals("", Calculator.Pad().press("⌫").display)
    }

    @Test
    fun `only an answer just given can be inserted`() {
        assertFalse(keys("5", "+", "3").canInsert)
        val answered = keys("5", "+", "3", "=")
        assertTrue(answered.canInsert)
        assertFalse("typing into the display makes it a sum again", answered.typed("8+1").canInsert)
        assertFalse(answered.press("+").canInsert)
        assertFalse(answered.press("C").canInsert)
        assertEquals("equals on an empty display does nothing", Calculator.Pad(), Calculator.Pad().press("="))
    }

    @Test
    fun `a typed or pasted sum works out like a pressed one`() {
        val pad = Calculator.Pad().typed("(12.5+7.5)÷4").press("=")
        assertEquals("5", pad.display)
        assertEquals("(12.5+7.5)÷4=5", pad.formula)
    }

    @Test
    fun `clearing the history leaves the display`() {
        val pad = keys("5", "+", "3", "=").clearHistory()
        assertEquals(emptyList<String>(), pad.history)
        assertEquals("8", pad.display)
        assertTrue(pad.canInsert)
    }

    // ---- where an inserted calculation lands ----

    @Test
    fun `the first lands at the cursor, the next on its own line after it`() {
        val first = Calculator.place("Total: \nend", "5+3=8", mark = null, caret = 7)
        assertEquals("Total: 5+3=8\nend", first.body)
        assertEquals(12, first.mark)
        val second = Calculator.place(first.body, "8×2=16", mark = first.mark, caret = 0)
        assertEquals("the remembered place wins over the cursor", "Total: 5+3=8\n8×2=16\nend", second.body)
    }

    @Test
    fun `with no cursor in the note, at its end`() {
        assertEquals("Shopping\n5+3=8", Calculator.place("Shopping\n", "5+3=8", null, null).body)
        assertEquals("5+3=8", Calculator.place("", "5+3=8", null, null).body)
    }

    @Test
    fun `never glued to a table`() {
        val body = "Prices\n| a | b |\n| --- | --- |\n| 1 | 2 |"
        val tableEnd = body.length
        assertEquals("no cursor: on the line after the table",
            "$body\n5+3=8", Calculator.place(body, "5+3=8", null, null).body)
        assertEquals("the empty field under a closing table: a new line",
            "$body\n5+3=8", Calculator.place(body, "5+3=8", null, tableEnd + 1).body)
        assertEquals("a remembered place inside the table moves after it",
            "$body\n5+3=8", Calculator.place(body, "5+3=8", 10, null).body)
        val after = "$body\nnotes"
        assertEquals("a cursor in the prose after a table stays there",
            "$body\nnotes 5+3=8", Calculator.place(after, " 5+3=8", null, after.length).body)
    }

    @Test
    fun `positions are code points, as the file counts`() {
        val body = "😀 cost "
        val placed = Calculator.place(body, "2×3=6", null, 7)
        assertEquals("😀 cost 2×3=6", placed.body)
        assertEquals(12, placed.mark)
        assertEquals("a digit outside the basic plane is one place", 6,
            Calculator.place("", "𝟗+1=10", null, null).mark)
    }

    @Test
    fun `the remembered place follows edits elsewhere`() {
        assertEquals("typing before it pushes it on", 9, Calculator.follow("ab=3\nxyz", "XYZ!!ab=3\nxyz", 4))
        assertEquals("typing after it leaves it", 4, Calculator.follow("ab=3\nxyz", "ab=3\nxyz!!", 4))
        assertEquals("typing right at it goes before it, like a mark with right gravity", 7,
            Calculator.follow("ab=3\nxyz", "ab=3abc\nxyz", 4))
        assertEquals("deleting across it brings it to the cut", 1, Calculator.follow("ab=3\nxyz", "a\nxyz", 4))
        assertNull(Calculator.follow("a", "b", null))
    }
}
