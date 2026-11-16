package dev.jesusmolano.alisio.core.media

/** Lo que el reproductor del Inicio enseñaba en un momento dado: de qué app y si sonaba. */
data class PlayerSeen(val source: String, val playing: Boolean)

/**
 * El reproductor apartado a mano sigue oculto hasta que algo empiece a sonar: una app que pasa de
 * pausa a reproducir, u otra app que suena. Mientras tanto, una sesión en pausa que su app deja
 * abierta durante horas no vuelve a ocupar el Inicio.
 *
 * @param hidden lo último que se vio con el reproductor oculto; `null` si está a la vista.
 * @param current lo que hay ahora; `null` sin ninguna sesión.
 * @return lo que hay que recordar para la próxima vez, o `null` si el reproductor vuelve a verse.
 */
fun hiddenAfter(hidden: PlayerSeen?, current: PlayerSeen?): PlayerSeen? = when {
    hidden == null -> null

    // Sin sesión no suena nada: la siguiente que suene, aunque sea de la misma app, lo devuelve.
    current == null -> hidden.copy(playing = false)

    current.playing && (!hidden.playing || current.source != hidden.source) -> null

    else -> current
}
