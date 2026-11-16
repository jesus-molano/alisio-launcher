package dev.jesusmolano.alisio.core.command

import dev.jesusmolano.alisio.core.text.normalize

/** Lo que se pide: encender, apagar o, sin decirlo, cambiar al contrario o abrir su ajuste. */
enum class Switch { ON, OFF }

/** Los paneles y ajustes del sistema que la barra abre: Android no deja cambiarlos a otra app. */
enum class PhonePanel { WIFI, BLUETOOTH, MOBILE_DATA, LOCATION, AIRPLANE }

/** Una orden para el teléfono escrita en la barra («linterna», «brillo 40», «wifi»). */
sealed interface PhoneCommand {
    data class Torch(val switch: Switch?) : PhoneCommand

    data class Panel(val panel: PhonePanel, val switch: Switch?) : PhoneCommand

    data class DoNotDisturb(val switch: Switch?) : PhoneCommand

    /** @param percent de 0 a 100; `null` abre el ajuste del brillo. */
    data class Brightness(val percent: Int?) : PhoneCommand

    data class Rotation(val switch: Switch?) : PhoneCommand
}

private enum class Subject { TORCH, WIFI, BLUETOOTH, MOBILE_DATA, LOCATION, AIRPLANE, DND, BRIGHTNESS, ROTATION }

// Las palabras de cada orden en los dos idiomas, ya sin tildes. Solo frases enteras: «wifi
// analyzer» sigue siendo una búsqueda. Nada de palabras sueltas cortas («bt», «data») que taparían apps.
private val SUBJECTS: Map<String, Subject> =
    buildMap {
        listOf("linterna", "flash", "flashlight", "torch").forEach { put(it, Subject.TORCH) }
        listOf("wifi", "wi fi", "wlan").forEach { put(it, Subject.WIFI) }
        put("bluetooth", Subject.BLUETOOTH)
        listOf("datos", "datos moviles", "datos del movil", "mobile data", "cellular data").forEach {
            put(it, Subject.MOBILE_DATA)
        }
        listOf("ubicacion", "localizacion", "gps", "location").forEach { put(it, Subject.LOCATION) }
        listOf("modo avion", "avion", "modo vuelo", "airplane", "airplane mode", "flight mode", "aeroplane mode")
            .forEach { put(it, Subject.AIRPLANE) }
        listOf("no molestar", "modo no molestar", "do not disturb", "dnd").forEach { put(it, Subject.DND) }
        listOf("brillo", "brightness").forEach { put(it, Subject.BRIGHTNESS) }
        listOf(
            "rotacion", "rotar", "girar", "girar pantalla", "auto rotar", "autorrotar", "rotacion automatica",
            "rotation", "rotate", "auto rotate", "autorotate", "screen rotation"
        ).forEach { put(it, Subject.ROTATION) }
    }

private val ON_VERBS =
    listOf("encender", "enciende", "activar", "activa", "pon", "poner", "turn on", "switch on", "enable")
private val OFF_VERBS =
    listOf("apagar", "apaga", "desactivar", "desactiva", "quitar", "quita", "turn off", "switch off", "disable")
private val ON_WORDS = setOf("on", "si", "encendida", "encendido", "activada", "activado")
private val OFF_WORDS = setOf("off", "no", "apagada", "apagado", "desactivada", "desactivado")
private val ARTICLES = listOf("la ", "el ", "los ", "the ")

// «brillo 40», «brillo al 40 %», «brightness to 40%».
private val BRIGHTNESS = Regex("""^(brillo|brightness)(?:\s+(?:al|a|to))?\s+(\d{1,3})\s*%?$""")
private const val MAX_PERCENT = 100

/**
 * Lee una orden para el teléfono: lo escrito entero tiene que ser la orden, con un verbo delante
 * («apagar la linterna», «turn on wifi») o un estado detrás («linterna off»). Devuelve `null` en
 * cualquier otro caso, para no tapar la búsqueda de una app.
 */
fun parsePhoneCommand(input: String): PhoneCommand? {
    var text = normalize(input).replace('-', ' ').replace(Regex("""\s+"""), " ")
    if (text.isEmpty()) return null
    BRIGHTNESS.matchEntire(text)?.let { match ->
        val percent = match.groupValues[2].toInt()
        return if (percent <= MAX_PERCENT) PhoneCommand.Brightness(percent) else null
    }
    var switch: Switch? = null
    ON_VERBS.firstOrNull { text.startsWith("$it ") }?.let {
        switch = Switch.ON
        text = text.removePrefix("$it ")
    } ?: OFF_VERBS.firstOrNull { text.startsWith("$it ") }?.let {
        switch = Switch.OFF
        text = text.removePrefix("$it ")
    }
    ARTICLES.firstOrNull { text.startsWith(it) }?.let { text = text.removePrefix(it) }
    if (switch == null) {
        val last = text.substringAfterLast(' ', "")
        when (last) {
            in ON_WORDS -> switch = Switch.ON
            in OFF_WORDS -> switch = Switch.OFF
        }
        if (switch != null) text = text.removeSuffix(" $last")
    }
    val subject = SUBJECTS[text] ?: return null
    return when (subject) {
        Subject.TORCH -> PhoneCommand.Torch(switch)

        Subject.WIFI -> PhoneCommand.Panel(PhonePanel.WIFI, switch)

        Subject.BLUETOOTH -> PhoneCommand.Panel(PhonePanel.BLUETOOTH, switch)

        Subject.MOBILE_DATA -> PhoneCommand.Panel(PhonePanel.MOBILE_DATA, switch)

        Subject.LOCATION -> PhoneCommand.Panel(PhonePanel.LOCATION, switch)

        Subject.AIRPLANE -> PhoneCommand.Panel(PhonePanel.AIRPLANE, switch)

        Subject.DND -> PhoneCommand.DoNotDisturb(switch)

        // «brillo» solo, o con un estado que no tiene sentido para él, abre su ajuste.
        Subject.BRIGHTNESS -> PhoneCommand.Brightness(null)

        Subject.ROTATION -> PhoneCommand.Rotation(switch)
    }
}

/**
 * Lo que hará la orden con el estado de ahora: lo pedido o, sin pedir nada, lo contrario de lo que
 * hay. `null` si no se sabe cómo está.
 */
fun resolveSwitch(asked: Switch?, currentlyOn: Boolean?): Switch? = asked ?: when (currentlyOn) {
    true -> Switch.OFF
    false -> Switch.ON
    null -> null
}

/** El brillo del sistema (0 a 255) para un porcentaje: 0 % no deja la pantalla a oscuras del todo. */
fun brightnessLevel(percent: Int): Int = (1 + percent.coerceIn(0, MAX_PERCENT) * 254 / MAX_PERCENT)
