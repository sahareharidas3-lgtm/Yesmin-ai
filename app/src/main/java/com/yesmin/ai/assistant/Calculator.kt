package com.yesmin.ai.assistant

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

object Calculator {
    /** Turns "5 plus 3 times 2" / "12 ÷ 4" into "5+3*2" / "12/4". Returns null if it is not math. */
    fun extract(text: String): String? {
        var t = " " + text.lowercase() + " "
        val repl = listOf(
            " divided by " to "/", " multiplied by " to "*", " times " to "*", " into " to "*", " gunaa " to "*",
            " plus " to "+", " minus " to "-", " power " to "^", " ÷ " to "/", " × " to "*", " jod " to "+", " ghata " to "-",
            " percent of " to "%*"
        )
        repl.forEach { (a, b) -> t = t.replace(a, b) }
        t = t.replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        val kept = t.filter { it.isDigit() || it in "+-*/().%^ ." }.replace(" ", "")
        if (!Regex("\\d.*[+*/^%-].*\\d").containsMatchIn(kept)) return null
        return kept
    }

    fun eval(src: String): Double {
        val p = Parser(src)
        val v = p.expr()
        if (p.pos != p.s.length) throw IllegalArgumentException("Unexpected '${p.s[p.pos]}'")
        return v
    }

    fun format(d: Double): String =
        if (d == floor(d) && abs(d) < 1e15) d.toLong().toString()
        else "%.6f".format(d).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var pos = 0
        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val op = s[pos++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = s[pos++]; val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> { if (r == 0.0) throw ArithmeticException("Division by zero"); v / r }
                    else -> if (r == 0.0) throw ArithmeticException("Division by zero") else v % r
                }
            }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (peek() == '^') { pos++; return b.pow(power()) }
            return b
        }
        fun unary(): Double {
            if (peek() == '-') { pos++; return -unary() }
            if (peek() == '+') { pos++; return unary() }
            return atom()
        }
        fun atom(): Double {
            if (peek() == '(') {
                pos++; val v = expr()
                if (peek() != ')') throw IllegalArgumentException("Missing )")
                pos++; return v
            }
            val st = pos
            while (peek().isDigit() || peek() == '.') pos++
            if (st == pos) throw IllegalArgumentException("Number expected")
            return s.substring(st, pos).toDouble()
        }
    }
}
