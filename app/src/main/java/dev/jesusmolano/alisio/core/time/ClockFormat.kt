package dev.jesusmolano.alisio.core.time

/** Cómo se escribe la hora del Inicio. [id] es lo que se guarda. */
enum class ClockFormat(val id: String) {
    /** Lo que diga el sistema (Ajustes → Fecha y hora). */
    SYSTEM("system"),
    H12("12"),
    H24("24");

    companion object {
        fun parse(id: String?): ClockFormat = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** El patrón de la hora, sin «a. m.» ni «p. m.»: la hora grande no lleva marca. */
fun clockPattern(format: ClockFormat, system24: Boolean): String {
    val use24 =
        when (format) {
            ClockFormat.SYSTEM -> system24
            ClockFormat.H12 -> false
            ClockFormat.H24 -> true
        }
    return if (use24) "H:mm" else "h:mm"
}

/** Tamaño de la hora del Inicio. El tamaño de cada uno está en `AlisioTokens.clockScale`. */
enum class ClockSize(val id: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    companion object {
        fun parse(id: String?): ClockSize = entries.firstOrNull { it.id == id } ?: LARGE
    }
}
