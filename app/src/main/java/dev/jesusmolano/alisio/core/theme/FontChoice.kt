package dev.jesusmolano.alisio.core.theme

/**
 * Tipografía de toda la interfaz. Solo combina las dos familias que Alisio ya lleva dentro y la del
 * sistema: no hay una tercera familia que cargar.
 *
 * @param id lo que se guarda en las preferencias; no cambia aunque cambie el nombre del valor.
 */
enum class FontChoice(val id: String) {
    /** Space Grotesk para lo que se mira (hora, títulos, cifras) e Inter para lo que se lee. */
    PAIR("pair"),

    /** Todo en Inter. */
    INTER("inter"),

    /** Todo en Space Grotesk. */
    GROTESK("grotesk"),

    /** La fuente que el usuario tiene elegida en el sistema. */
    SYSTEM("system");

    companion object {
        /** Un valor que no se reconoce (preferencia de otra versión, archivo dañado) cae en [PAIR]. */
        fun parse(id: String?): FontChoice = entries.firstOrNull { it.id == id } ?: PAIR
    }
}
