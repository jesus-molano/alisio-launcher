package dev.jesusmolano.alisio.core.extras

import dev.jesusmolano.alisio.core.text.normalize

/** Lo que hay en el portapapeles, si es algo con lo que la barra sabe hacer algo. */
sealed interface ClipContent {
    /** Texto que se enseña en la fila. */
    val text: String

    /** Una dirección web, ya con su esquema. */
    data class Url(val url: String) : ClipContent {
        override val text get() = url

        /**
         * El sitio al que lleva de verdad, para enseñarlo antes de abrirlo: sin esquema, sin «www.»,
         * sin puerto y sin lo que vaya delante de una «@», que es donde un enlace engañoso esconde
         * un nombre conocido («https://banco.es@otro.example» lleva a «otro.example»).
         */
        val host: String
            get() {
                val authority = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
                val server = authority.substringAfterLast('@')
                // Una dirección IPv6 va entre corchetes y lleva sus propios «:».
                val name =
                    if (server.startsWith('[')) server.substringBefore(']') + "]" else server.substringBefore(':')
                return name.lowercase().removePrefix("www.")
            }
    }

    /** Un teléfono, solo con sus cifras y el «+» si lo lleva. */
    data class Phone(val number: String, override val text: String) : ClipContent

    /** Una dirección postal, para buscarla en el mapa. */
    data class Address(override val text: String) : ClipContent
}

// Más que esto no es una dirección ni un enlace copiado: es un texto, y no se mira.
private const val MAX_LENGTH = 2_000
private const val MAX_ADDRESS_LENGTH = 160
private const val MAX_ADDRESS_LINES = 4

private val WEB_URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

// Sin esquema solo se acepta lo que es claramente un dominio: «www.» o una terminación conocida.
// Si valiera cualquier punto, «informe.pdf» o «v1.2» pasarían por enlaces.
private const val KNOWN_ENDINGS = "com|es|org|net|io|dev|app|eu|info|me|co|ai|cat|gal|eus|uk|de|fr|it|pt|tv|gov|edu"
private val BARE_DOMAIN =
    Regex(
        """^(?:www\.[a-z0-9-]+(?:\.[a-z0-9-]+)+|(?:[a-z0-9-]+\.)+(?:$KNOWN_ENDINGS))(?::\d+)?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE
    )
private const val URL_TRAILING = ".,;:!?)]}»\"'"

private val PHONE = Regex("""^\+?[\d\s().\-]+$""")
private const val MIN_PHONE_DIGITS = 9
private const val MIN_INTERNATIONAL_DIGITS = 8
private const val MAX_PHONE_DIGITS = 15

// Tipos de vía en castellano, catalán y gallego, ya sin tildes. Solo cuentan al empezar una línea,
// que es como se escribe una dirección: «voy de camino, llego en 5» no lo es.
private const val STREET_TYPES =
    """calle|c/|cl\.|avenida|avda\.?|av\.|plaza|pza\.?|paseo|camino|carretera|ctra\.?|ronda|travesia|""" +
        """glorieta|rambla|urbanizacion|poligono|carrer|avinguda|passeig|placa|rua"""

// Tras el tipo de vía va un espacio, salvo en las abreviaturas que acaban en «/» o «.» («c/Mayor 5»):
// sin eso, «plazas libres para el 15» o «placas solares 3 kW» pasarían por direcciones.
private val STREET_FIRST = Regex("""(?:^|\n)\s*(?:$STREET_TYPES)(?:(?<=[/.])|\s)\s*[a-z].*\d""")

// En inglés el número va delante y el tipo de vía detrás: «221B Baker Street».
private const val STREET_SUFFIXES = """street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|square|court|way"""
private val NUMBER_FIRST = Regex("""(?:^|\n)\s*\d+[a-z]?\s+(?:[a-z'.\-]+\s+){1,4}(?:$STREET_SUFFIXES)(?=$|[\s,.])""")

// Código postal español (01000–52999) con letras alrededor: «28013 Madrid».
private val POSTAL_CODE = Regex("""(?:^|\D)(?:0[1-9]|[1-4]\d|5[0-2])\d{3}(?:$|\D)""")

/**
 * Qué hay en [raw], el texto del portapapeles: un enlace (aunque venga dentro de una frase, como al
 * compartir), un teléfono o una dirección postal, por ese orden. `null` si no es nada de eso.
 *
 * La dirección postal es una apuesta: tiene que empezar por un tipo de vía y llevar un número
 * («Calle Mayor 5»), tener la forma inglesa («221B Baker Street») o llevar un código postal entre
 * palabras y comas. Se prefiere no ofrecer nada a ofrecer el mapa para una frase.
 */
fun detectClip(raw: String): ClipContent? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > MAX_LENGTH) return null
    return url(text) ?: phone(text) ?: address(text)
}

private fun url(text: String): ClipContent.Url? {
    WEB_URL.find(text)?.let { match ->
        val url = match.value.trimEnd { it in URL_TRAILING }
        // «https://» a secas no lleva a ningún sitio.
        return if (url.substringAfter("://").isNotEmpty()) ClipContent.Url(url) else null
    }
    return if (BARE_DOMAIN.matches(text)) ClipContent.Url("https://$text") else null
}

private fun phone(text: String): ClipContent.Phone? {
    if (!PHONE.matches(text)) return null
    val digits = text.count(Char::isDigit)
    val international = text.startsWith('+')
    val min = if (international) MIN_INTERNATIONAL_DIGITS else MIN_PHONE_DIGITS
    if (digits !in min..MAX_PHONE_DIGITS) return null
    val number = (if (international) "+" else "") + text.filter(Char::isDigit)
    return ClipContent.Phone(number, text)
}

private fun address(text: String): ClipContent.Address? {
    if (text.length > MAX_ADDRESS_LENGTH || text.lines().size > MAX_ADDRESS_LINES) return null
    val plain = normalize(text)
    val letters = plain.count(Char::isLetter)
    val street = STREET_FIRST.containsMatchIn(plain) || NUMBER_FIRST.containsMatchIn(plain)
    val postal = POSTAL_CODE.containsMatchIn(plain) && letters >= MIN_ADDRESS_LETTERS && ',' in plain
    if (!street && !postal) return null
    // En una línea, que es como la espera el mapa.
    return ClipContent.Address(text.lines().map(String::trim).filter(String::isNotEmpty).joinToString(", "))
}

private const val MIN_ADDRESS_LETTERS = 4
