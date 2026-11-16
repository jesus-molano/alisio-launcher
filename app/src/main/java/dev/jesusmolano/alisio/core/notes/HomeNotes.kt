package dev.jesusmolano.alisio.core.notes

/** Una nota no ocupa más que una línea del Inicio: lo que pase de aquí se corta al guardarla ([clampNote]). */
const val MAX_NOTE_LENGTH = 280

/** Notas que se ven a la vez bajo la hora; las demás se cuentan con «+N». */
const val VISIBLE_NOTES = 2

private val SPACES = Regex("""\s+""")

// Un enlace empieza por su esquema o por «www.»; acaba en el primer espacio.
private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

// Lo que suele quedar pegado a un enlace dentro de una frase y no forma parte de él.
private const val LINK_TRAILING = ".,;:!?)]»\"'"

// Lo que queda solo al quitar el enlace de «Título · enlace»: no es texto que leer.
private const val SEPARATORS = "·-–—:|•"

/**
 * Lee «+ comprar pan» (o «+comprar pan») como una nota para el Inicio. Devuelve su texto, o `null`
 * si lo escrito no empieza por «+» o lo que sigue no lleva ninguna letra: «+5» es una cuenta.
 */
fun parseNoteCommand(input: String): String? {
    val text = input.trim()
    if (!text.startsWith("+")) return null
    val body = tidy(text.drop(1))
    if (body.none { it.isLetter() }) return null
    return clampNote(body)
}

/**
 * Corta la nota a [MAX_NOTE_LENGTH] sin partir su primer enlace: una dirección cortada sigue
 * pareciendo buena y abre una página que no existe, y la nota solo enseña su texto, así que el corte
 * no se vería. Se recorta el texto de alrededor (primero lo de después); un enlace que ya pasa del
 * límite se guarda entero.
 */
fun clampNote(text: String): String {
    if (text.length <= MAX_NOTE_LENGTH) return text
    val link = LINK.find(text) ?: return text.take(MAX_NOTE_LENGTH).trimEnd()
    val before = text.substring(0, link.range.first)
    val after = text.substring(link.range.last + 1)
    val room = (MAX_NOTE_LENGTH - link.value.length).coerceAtLeast(0)
    // En «Título · enlace», lo que separa se queda y lo que se acorta es el título.
    val head = before.trimEnd { it.isWhitespace() || it in SEPARATORS }
    val tail = before.substring(head.length)
    val keptBefore =
        if (before.length <= room) {
            before
        } else {
            head.take((room - tail.length).coerceAtLeast(0)).trimEnd()
                .takeIf { kept -> kept.any { it.isLetterOrDigit() } }
                ?.let { it + tail }
                .orEmpty()
        }
    val keptAfter = after.take((room - keptBefore.length).coerceAtLeast(0)).trimEnd()
    return keptBefore + link.value + keptAfter
}

/**
 * La nota que crea un texto compartido desde otra app. Un enlace de un navegador llega con su
 * título en [subject] y la dirección en [text]: se guardan los dos, así la nota se lee por su título
 * y un toque abre la dirección. `null` si no llega nada que guardar.
 */
fun sharedNote(text: String?, subject: String?): String? {
    val body = tidy(text.orEmpty())
    val title = tidy(subject.orEmpty())
    val joined =
        when {
            body.isEmpty() -> title
            title.isEmpty() || body.contains(title, ignoreCase = true) -> body
            else -> "$title · $body"
        }
    return joined.takeIf { it.isNotEmpty() }?.let(::clampNote)
}

/** El primer enlace de la nota, listo para abrir («www.» pasa a «https://www.»); `null` si no lleva. */
fun noteLink(text: String): String? {
    val found = LINK.find(text)?.value?.trimEnd { it in LINK_TRAILING } ?: return null
    if (found.length <= "www.".length) return null
    return if (found.startsWith("www.", ignoreCase = true)) "https://$found" else found
}

/**
 * Lo que se lee de una nota en el Inicio. Con enlace y más texto, solo el texto: la dirección no se
 * lee bien en una línea y un toque ya la abre. Con el enlace solo, su sitio («elpais.com»).
 */
fun noteDisplay(text: String): String {
    val link = LINK.find(text) ?: return tidy(text)
    val rest = tidy(text.removeRange(link.range)).trim { it.isWhitespace() || it in SEPARATORS }
    if (rest.any { it.isLetterOrDigit() }) return rest
    return linkHost(link.value) ?: tidy(text)
}

/** El sitio de un enlace sin «www.», o `null` si no se puede sacar. */
fun linkHost(link: String): String? {
    val afterScheme = link.substringAfter("://", link)
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    return host.removePrefix("www.").removePrefix("WWW.").takeIf { '.' in it }
}

/** Las notas que caben bajo la hora y cuántas quedan fuera. */
class VisibleNotes<T>(val shown: List<T>, val more: Int)

/** Las [max] primeras de [notes] (ya en orden, la más reciente primero) y cuántas más hay. */
fun <T> visibleNotes(notes: List<T>, max: Int = VISIBLE_NOTES): VisibleNotes<T> =
    VisibleNotes(notes.take(max), (notes.size - max).coerceAtLeast(0))

private fun tidy(text: String): String = SPACES.replace(text, " ").trim()
