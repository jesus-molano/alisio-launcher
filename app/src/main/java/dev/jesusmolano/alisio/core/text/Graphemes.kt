package dev.jesusmolano.alisio.core.text

import java.text.BreakIterator

/**
 * Los primeros [count] caracteres tal como los ve una persona. Un emoji con bandera, tono de piel o
 * familia ocupa varias unidades UTF-16: cortarlo con `take` lo dejaría roto.
 */
fun String.takeGraphemes(count: Int): String {
    if (count <= 0 || isEmpty()) return ""
    val breaks = BreakIterator.getCharacterInstance().also { it.setText(this) }
    var end = 0
    repeat(count) {
        val next = breaks.next()
        if (next == BreakIterator.DONE) return this
        end = next
    }
    return substring(0, end)
}
