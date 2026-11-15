package dev.jesusmolano.alisio.core.extras

import java.net.URLEncoder

/**
 * Un gasto escrito en la barra.
 *
 * @param amount importe con punto decimal («12.50»), la forma que cualquier app web sabe leer.
 * @param concept lo que se ha escrito detrás, tal cual.
 */
data class Expense(val amount: String, val concept: String)

// La expresión de §5.8: importe con hasta dos decimales, «€» opcional y, tras un espacio, el concepto.
private val EXPENSE = Regex("""^(\d+(?:[.,]\d{1,2})?)\s*€?\s+(.+)$""")

/** Lee «12 café» o «12,50€ comida». Devuelve `null` si lo escrito no tiene esa forma. */
fun parseExpense(input: String): Expense? {
    val match = EXPENSE.matchEntire(input.trim()) ?: return null
    val concept = match.groupValues[2].trim()
    // Un concepto sin letras («12 3», «12 + 4») es una cuenta a medias, no un gasto.
    if (concept.none(Char::isLetter)) return null
    return Expense(match.groupValues[1].replace(',', '.'), concept)
}

private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*:.+""")
private val AMOUNT_MARKS = listOf("{importe}", "{amount}")
private val CONCEPT_MARKS = listOf("{concepto}", "{concept}")

/**
 * Si [template] sirve para apuntar gastos: es una dirección con esquema (`https:`, o el de una app)
 * y tiene al menos una de las dos marcas. Sin eso, el gasto rápido queda apagado.
 */
fun isExpenseTemplate(template: String): Boolean {
    val text = template.trim()
    return SCHEME.matches(text) && (AMOUNT_MARKS + CONCEPT_MARKS).any { it in text }
}

/**
 * La dirección que apunta [expense]: [template] con `{importe}` y `{concepto}` (o `{amount}` y
 * `{concept}`) sustituidos y codificados para URL. `null` si la plantilla no vale.
 */
fun expenseUrl(template: String, expense: Expense): String? {
    if (!isExpenseTemplate(template)) return null
    var url = template.trim()
    AMOUNT_MARKS.forEach { url = url.replace(it, encode(expense.amount)) }
    CONCEPT_MARKS.forEach { url = url.replace(it, encode(expense.concept)) }
    return url
}

// URLEncoder codifica formularios y escribe el espacio como «+», que solo vale en la consulta; «%20»
// vale también si la marca está en la ruta. Se usa la variante con el nombre del juego de caracteres
// porque la que recibe un Charset no existe en Android 12.
private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
