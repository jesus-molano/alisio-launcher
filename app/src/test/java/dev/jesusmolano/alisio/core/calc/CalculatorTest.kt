package dev.jesusmolano.alisio.core.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test
    fun `operaciones basicas`() {
        assertEquals("4", calculate("2+2"))
        assertEquals("7", calculate("10 - 3"))
        assertEquals("42", calculate("6*7"))
        assertEquals("2,5", calculate("5/2"))
    }

    @Test
    fun `precedencia y parentesis`() {
        assertEquals("14", calculate("2+3*4"))
        assertEquals("20", calculate("(2+3)*4"))
        assertEquals("-6", calculate("-2*3"))
        assertEquals("1", calculate("3 - -2 - 4"))
    }

    @Test
    fun `decimales con coma o punto y simbolos de teclado`() {
        assertEquals("3,75", calculate("1,5 × 2.5"))
        assertEquals("4", calculate("12 ÷ 3"))
        assertEquals("6", calculate("2x3"))
        assertEquals("0,3", calculate("0,1+0,2"))
        assertEquals("0,33333333", calculate("1/3"))
    }

    @Test
    fun `lo que no es una operacion completa devuelve null`() {
        assertNull(calculate("12"))
        assertNull(calculate("-5"))
        assertNull(calculate("12 café"))
        assertNull(calculate("2+"))
        assertNull(calculate("(2+3"))
        assertNull(calculate("wi-fi 6"))
        assertNull(calculate("xbox 360"))
        assertNull(calculate("1/0"))
        assertNull(calculate(""))
    }
}
