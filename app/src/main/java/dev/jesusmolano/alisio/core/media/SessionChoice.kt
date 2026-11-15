package dev.jesusmolano.alisio.core.media

/** Lo que hace falta saber de una sesión multimedia para decidir cuál enseña el reproductor. */
data class SessionFacts(val playing: Boolean, val hasTitle: Boolean)

/**
 * La sesión que enseña el reproductor del Inicio (§5.5): la que suena y, si ninguna suena, la última
 * que se enseñó. Si esa ya no existe, la primera de la lista, que el sistema da por prioridad.
 *
 * Una sesión que ni suena ni tiene título no cuenta: algunas apps dejan abierta una sesión vacía y el
 * reproductor saldría en blanco.
 *
 * @param sessions las sesiones activas, en el orden del sistema.
 * @param last la última que se enseñó, si sigue en [sessions].
 */
fun <T> chooseSession(sessions: List<T>, last: T?, facts: (T) -> SessionFacts): T? {
    val candidates = sessions.filter { facts(it).run { playing || hasTitle } }
    return candidates.firstOrNull { facts(it).playing }
        ?: last?.takeIf { it in candidates }
        ?: candidates.firstOrNull()
}
