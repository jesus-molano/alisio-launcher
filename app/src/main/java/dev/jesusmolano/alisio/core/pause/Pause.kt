package dev.jesusmolano.alisio.core.pause

/** Duraciones de la pausa que se ofrecen en los ajustes, en segundos. */
val PAUSE_SECONDS: List<Int> = listOf(3, 5, 10)

const val DEFAULT_PAUSE_SECONDS = 5

private const val MILLIS_PER_SECOND = 1000L

/**
 * Lo guardado puede venir de otra versión con otras duraciones: se queda la ofrecida más cercana,
 * para que en los ajustes siempre haya una marcada. En empate, la más corta.
 */
fun normalizePauseSeconds(stored: Int?): Int {
    if (stored == null) return DEFAULT_PAUSE_SECONDS
    return PAUSE_SECONDS.minBy { kotlin.math.abs(it - stored) }
}

/**
 * Segundos de pausa antes de abrir [packageName], o `null` si se abre sin más. La pausa va por
 * paquete: vale igual para la app, para sus accesos directos y para su copia en otro perfil.
 *
 * @param paused paquetes que el usuario ha elegido frenar.
 */
fun pauseBeforeOpening(packageName: String, paused: Set<String>, seconds: Int): Int? =
    if (packageName in paused && seconds > 0) seconds else null

/**
 * Segundos que quedan por enseñar en la cuenta atrás. Redondea hacia arriba: el número baja a 0
 * justo cuando la pausa termina, no un segundo antes.
 */
fun pauseSecondsLeft(totalSeconds: Int, elapsedMillis: Long): Int {
    val leftMillis = totalSeconds * MILLIS_PER_SECOND - elapsedMillis.coerceAtLeast(0)
    if (leftMillis <= 0) return 0
    return ((leftMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

/**
 * Las apps de la lista de pausa en los ajustes: primero las que ya tienen pausa y después el resto,
 * cada grupo en el orden en que venían. Así lo elegido queda a la vista sin buscarlo.
 */
fun <T> pausedFirst(apps: List<T>, paused: Set<String>, packageOf: (T) -> String): List<T> {
    val (chosen, rest) = apps.partition { packageOf(it) in paused }
    return chosen + rest
}
