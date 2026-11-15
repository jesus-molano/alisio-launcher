package dev.jesusmolano.alisio.core.calc

import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_DECIMALS = 8

/**
 * Calcula lo escrito en la barra si es una operación: `+ - * / × ÷ x`, paréntesis y decimales con
 * coma o punto. Devuelve el resultado con coma decimal, o `null` si no es una operación completa
 * (un número suelto no lo es) o no tiene resultado (división entre cero).
 */
fun calculate(input: String): String? {
    val text = input.trim()
    if (text.none { it in OPERATORS } || text.none(Char::isDigit)) return null
    val parser = Parser(text)
    val value = parser.parse() ?: return null
    if (!parser.usedOperator || value.isNaN() || value.isInfinite()) return null
    return BigDecimal(value)
        .setScale(MAX_DECIMALS, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')
}

private const val OPERATORS = "+-*/×÷x"

/** Descenso recursivo: suma → producto → signo → número o paréntesis. */
private class Parser(private val text: String) {
    private var position = 0
    var usedOperator = false
        private set

    fun parse(): Double? {
        val value = sum() ?: return null
        skipSpaces()
        return if (position == text.length) value else null
    }

    private fun sum(): Double? {
        var left = product() ?: return null
        while (true) {
            left =
                when (operator("+-")) {
                    '+' -> left + (product() ?: return null)
                    '-' -> left - (product() ?: return null)
                    else -> return left
                }
        }
    }

    private fun product(): Double? {
        var left = signed() ?: return null
        while (true) {
            left =
                when (operator("*/×÷x")) {
                    '*', '×', 'x' -> left * (signed() ?: return null)
                    '/', '÷' -> left / (signed() ?: return null)
                    else -> return left
                }
        }
    }

    private fun signed(): Double? {
        skipSpaces()
        if (peek() == '-') {
            position++
            return signed()?.let { -it }
        }
        return atom()
    }

    private fun atom(): Double? {
        skipSpaces()
        if (peek() == '(') {
            position++
            val value = sum() ?: return null
            skipSpaces()
            if (peek() != ')') return null
            position++
            return value
        }
        val start = position
        while (peek()?.let { it.isDigit() || it == ',' || it == '.' } == true) position++
        return text.substring(start, position).replace(',', '.').toDoubleOrNull()
    }

    private fun operator(allowed: String): Char? {
        skipSpaces()
        val char = peek() ?: return null
        if (char !in allowed) return null
        position++
        usedOperator = true
        return char
    }

    private fun peek(): Char? = text.getOrNull(position)

    private fun skipSpaces() {
        while (peek() == ' ') position++
    }
}
