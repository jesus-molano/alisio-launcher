package dev.jesusmolano.alisio.core.media

/**
 * Por dónde va lo que suena, de 0 a 1, a partir de lo último que dijo la sesión: su posición a
 * [positionAt] (reloj desde el arranque) y su velocidad. Mientras suena, la posición avanza sola.
 * `null` si no se sabe cuánto dura.
 */
fun trackProgress(
    position: Long,
    positionAt: Long,
    speed: Float,
    duration: Long?,
    playing: Boolean,
    now: Long
): Float? {
    if (duration == null || duration <= 0 || position < 0) return null
    val elapsed = if (playing) ((now - positionAt).coerceAtLeast(0) * speed).toLong() else 0L
    return ((position + elapsed).toFloat() / duration).coerceIn(0f, 1f)
}

/**
 * Lo que queda de un temporizador que acaba a [endsAt], como fracción de su hora en curso: el anillo
 * de la esfera da una vuelta por cada hora que falta. `null` si ya ha acabado.
 */
fun timerRemaining(endsAt: Long, now: Long): Float? {
    val left = endsAt - now
    if (left <= 0) return null
    val hour = 3_600_000L
    // Justo una hora o más llena la vuelta; menos, la parte que queda.
    return if (left % hour == 0L) 1f else (left % hour).toFloat() / hour
}
