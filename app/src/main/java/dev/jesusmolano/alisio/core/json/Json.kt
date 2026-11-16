package dev.jesusmolano.alisio.core.json

/**
 * Lector de JSON mínimo, sin dependencias, para las respuestas de Open-Meteo. `org.json` es de
 * Android y no corre en los tests de JVM; una biblioteca entera para dos respuestas pequeñas sería
 * una dependencia de más (docs/PLAN.md §10).
 *
 * Devuelve `Map<String, Any?>`, `List<Any?>`, `String`, `Double`, `Boolean` o `null`. Un texto mal
 * formado lanza [JsonException].
 */
fun parseJson(text: String): Any? {
    val reader = JsonReader(text)
    val value = reader.value()
    reader.skipSpace()
    if (!reader.atEnd()) throw JsonException("Sobra texto en la posición ${reader.position}")
    return value
}

class JsonException(message: String) : Exception(message)

/**
 * Escribe [value] como JSON con sangría de dos espacios, para que el archivo de la copia se pueda
 * leer a mano. Acepta lo mismo que devuelve [parseJson] y además `Int` y `Long`; un número no finito
 * o cualquier otro tipo lanza [JsonException].
 */
fun toJson(value: Any?): String = StringBuilder().also { it.writeJson(value, 0) }.toString()

private const val INDENT = "  "

private fun StringBuilder.writeJson(value: Any?, depth: Int) {
    when (value) {
        null -> append("null")

        is Boolean -> append(value)

        is Int, is Long -> append(value)

        is Double -> {
            if (!value.isFinite()) throw JsonException("Número no finito")
            // Las cifras enteras se escriben sin «.0»: así un contador vuelve a leerse igual.
            if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) append(value.toLong()) else append(value)
        }

        is String -> writeString(value)

        is Map<*, *> -> writeBlock('{', '}', value.entries, depth) { entry ->
            writeString(entry.key as? String ?: throw JsonException("Clave que no es texto"))
            append(": ")
            writeJson(entry.value, depth + 1)
        }

        is Collection<*> -> writeBlock('[', ']', value, depth) { writeJson(it, depth + 1) }

        else -> throw JsonException("Tipo sin JSON: ${value::class.simpleName}")
    }
}

private fun <T> StringBuilder.writeBlock(
    open: Char,
    close: Char,
    items: Collection<T>,
    depth: Int,
    write: StringBuilder.(T) -> Unit
) {
    append(open)
    if (items.isEmpty()) {
        append(close)
        return
    }
    items.forEachIndexed { index, item ->
        if (index > 0) append(',')
        append('\n').append(INDENT.repeat(depth + 1))
        write(item)
    }
    append('\n').append(INDENT.repeat(depth)).append(close)
}

private fun StringBuilder.writeString(text: String) {
    append('"')
    for (c in text) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

/** El objeto que hay en [key], o `null` si falta o es de otro tipo. */
fun Map<*, *>.obj(key: String): Map<*, *>? = this[key] as? Map<*, *>

fun Map<*, *>.list(key: String): List<*>? = this[key] as? List<*>

fun Map<*, *>.number(key: String): Double? = this[key] as? Double

fun Map<*, *>.text(key: String): String? = this[key] as? String

private const val HEX_RADIX = 16
private const val UNICODE_DIGITS = 4

private class JsonReader(private val text: String) {
    var position = 0
        private set

    fun atEnd() = position >= text.length

    fun skipSpace() {
        while (!atEnd() && text[position].isWhitespace()) position++
    }

    fun value(): Any? {
        skipSpace()
        if (atEnd()) throw JsonException("Falta un valor al final")
        return when (val c = text[position]) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) number() else throw JsonException("Carácter «$c» en $position")
        }
    }

    private fun literal(word: String, value: Any?): Any? {
        if (!text.startsWith(word, position)) throw JsonException("Se esperaba $word en $position")
        position += word.length
        return value
    }

    private fun obj(): Map<String, Any?> {
        position++
        val result = LinkedHashMap<String, Any?>()
        skipSpace()
        if (peek() == '}') {
            position++
            return result
        }
        while (true) {
            skipSpace()
            if (peek() != '"') throw JsonException("Se esperaba una clave en $position")
            val key = string()
            skipSpace()
            expect(':')
            result[key] = value()
            skipSpace()
            when (peek()) {
                ',' -> position++

                '}' -> {
                    position++
                    return result
                }

                else -> throw JsonException("Se esperaba «,» o «}» en $position")
            }
        }
    }

    private fun array(): List<Any?> {
        position++
        val result = ArrayList<Any?>()
        skipSpace()
        if (peek() == ']') {
            position++
            return result
        }
        while (true) {
            result += value()
            skipSpace()
            when (peek()) {
                ',' -> position++

                ']' -> {
                    position++
                    return result
                }

                else -> throw JsonException("Se esperaba «,» o «]» en $position")
            }
        }
    }

    private fun string(): String {
        position++
        val out = StringBuilder()
        while (true) {
            if (atEnd()) throw JsonException("Texto sin cerrar")
            when (val c = text[position++]) {
                '"' -> return out.toString()
                '\\' -> out.append(escape())
                else -> out.append(c)
            }
        }
    }

    private fun escape(): Char {
        if (atEnd()) throw JsonException("Escape sin cerrar")
        return when (val c = text[position++]) {
            '"', '\\', '/' -> c

            'b' -> '\b'

            'f' -> '\u000C'

            'n' -> '\n'

            'r' -> '\r'

            't' -> '\t'

            'u' -> {
                if (position + UNICODE_DIGITS > text.length) throw JsonException("Escape \\u incompleto")
                val code = text.substring(position, position + UNICODE_DIGITS).toIntOrNull(HEX_RADIX)
                    ?: throw JsonException("Escape \\u no válido en $position")
                position += UNICODE_DIGITS
                code.toChar()
            }

            else -> throw JsonException("Escape «\\$c» no válido")
        }
    }

    private fun number(): Double {
        val start = position
        while (!atEnd() && (text[position].isDigit() || text[position] in "+-.eE")) position++
        return text.substring(start, position).toDoubleOrNull() ?: throw JsonException("Número no válido en $start")
    }

    private fun peek(): Char = if (atEnd()) throw JsonException("Fin inesperado") else text[position]

    private fun expect(c: Char) {
        if (peek() != c) throw JsonException("Se esperaba «$c» en $position")
        position++
    }
}
