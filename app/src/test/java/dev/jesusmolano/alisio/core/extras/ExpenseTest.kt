package dev.jesusmolano.alisio.core.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseTest {
    private val template = "https://gastos.example/nuevo?importe={importe}&concepto={concepto}"

    @Test
    fun `importe y concepto`() {
        assertEquals(Expense("12", "café"), parseExpense("12 café"))
        assertEquals(Expense("12.50", "comida"), parseExpense("12,50€ comida"))
        assertEquals(Expense("12.5", "comida con Ana"), parseExpense("  12.5 €  comida con Ana "))
        assertEquals(Expense("3", "en raya"), parseExpense("3 en raya"))
    }

    @Test
    fun `lo que no es un gasto`() {
        assertNull(parseExpense("12"))
        assertNull(parseExpense("12€"))
        assertNull(parseExpense("café 12"))
        // Sin espacio no hay concepto: «12café» no cumple la expresión de §5.8.
        assertNull(parseExpense("12café"))
        assertNull(parseExpense("12,505 comida"))
        // Una cuenta a medias no es un gasto.
        assertNull(parseExpense("12 + 3"))
        assertNull(parseExpense("12 3"))
        assertNull(parseExpense(""))
    }

    @Test
    fun `la plantilla se rellena codificada para URL`() {
        assertEquals(
            "https://gastos.example/nuevo?importe=12&concepto=caf%C3%A9",
            expenseUrl(template, Expense("12", "café"))
        )
        assertEquals(
            "https://gastos.example/nuevo?importe=12.50&concepto=pan%20%26%20leche%3F",
            expenseUrl(template, Expense("12.50", "pan & leche?"))
        )
    }

    @Test
    fun `marcas en ingles, en la ruta y repetidas`() {
        assertEquals(
            "gastos://add/7/taxi%20a%20casa?c=taxi%20a%20casa",
            expenseUrl(" gastos://add/{amount}/{concept}?c={concepto} ", Expense("7", "taxi a casa"))
        )
    }

    @Test
    fun `sin plantilla valida no hay direccion`() {
        val expense = Expense("12", "café")
        assertNull(expenseUrl("", expense))
        assertNull(expenseUrl("   ", expense))
        // Sin esquema no se puede abrir.
        assertNull(expenseUrl("gastos.example/nuevo?importe={importe}", expense))
        // Sin marcas abriría siempre lo mismo.
        assertNull(expenseUrl("https://gastos.example/nuevo", expense))
        assertFalse(isExpenseTemplate("https://"))
        assertTrue(isExpenseTemplate("https://gastos.example/?q={concepto}"))
    }
}
