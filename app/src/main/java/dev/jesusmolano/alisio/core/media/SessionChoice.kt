package dev.jesusmolano.alisio.core.media

/**
 * Lo que hace falta saber de una sesión multimedia para decidir cuál enseña el reproductor.
 *
 * @param playingFor cuánto lleva sonando, en milisegundos; por defecto, lo bastante para contar.
 * @param shown es la que el reproductor enseña ahora.
 */
data class SessionFacts(
    val playing: Boolean,
    val hasTitle: Boolean,
    val playingFor: Long = Long.MAX_VALUE,
    val shown: Boolean = false
) {
    /**
     * Si el reproductor puede enseñarla. Sin título solo si lleva [UNTITLED_GRACE_MILLIS] sonando: una
     * app que arranca en frío (Spotify, YouTube Music) empieza a sonar antes de mandar la canción, y el
     * reproductor saldría un momento vacío, con el nombre de la app, y cambiaría enseguida. Pasado el
     * margen cuenta igual: hay audios que nunca mandan título. La que ya se enseña no se va por quedarse
     * un instante sin título al cambiar de canción.
     */
    val showable: Boolean get() = hasTitle || (playing && (shown || playingFor >= UNTITLED_GRACE_MILLIS))
}

/** Lo que se espera a que una sesión que suena mande su título antes de enseñarla sin él. */
const val UNTITLED_GRACE_MILLIS = 2_000L

/**
 * Desde cuándo suena una sesión tras un aviso suyo (reloj desde el arranque); `null` si no suena.
 * [KNOWN_PLAYING] marca una que ya sonaba cuando se empezó a escuchar: no está arrancando y cuenta
 * como si llevara mucho sonando.
 *
 * @param previous lo que se sabía antes de este aviso.
 * @param known la sesión ya existía al empezar a escuchar.
 */
fun playingSinceAfter(previous: Long?, playing: Boolean, known: Boolean, now: Long): Long? = when {
    !playing -> null
    previous != null -> previous
    known -> KNOWN_PLAYING
    else -> now
}

/** Marca de [playingSinceAfter] para lo que ya sonaba antes de escuchar. */
const val KNOWN_PLAYING = 0L

/**
 * Cuándo volver a elegir sesión para que salga una que suena sin título al acabar su margen; `null` si
 * no hay ninguna esperando.
 *
 * @param untitledPlayingFor cuánto lleva sonando cada sesión que suena sin título.
 */
fun untitledRecheckDelay(untitledPlayingFor: List<Long>): Long? =
    untitledPlayingFor.minOfOrNull { UNTITLED_GRACE_MILLIS - it }?.takeIf { it > 0 }

/**
 * La sesión que enseña el reproductor del Inicio (§5.5): la que suena y, si ninguna suena, la última
 * que se enseñó. Si esa ya no existe, la primera de la lista, que el sistema da por prioridad.
 *
 * Una sesión sin título no cuenta salvo que lleve un rato sonando ([SessionFacts.showable]): algunas
 * apps dejan abierta una sesión vacía, y otras empiezan a sonar antes de decir qué.
 *
 * La app de música elegida ([preferred]) gana a otros audios que suenan a la vez (un audio de
 * WhatsApp, un vídeo): el sistema pone primero la sesión que más ha cambiado y no la que el dueño
 * escucha. En pausa no se adelanta a la última enseñada: lo último que se tocó sigue a mano.
 *
 * @param sessions las sesiones activas, en el orden del sistema.
 * @param last la última que se enseñó, si sigue en [sessions].
 * @param preferred dice si una sesión es de la app de música elegida.
 */
fun <T> chooseSession(
    sessions: List<T>,
    last: T?,
    preferred: (T) -> Boolean = { false },
    facts: (T) -> SessionFacts
): T? {
    val candidates = sessions.filter { facts(it).showable }
    return candidates.firstOrNull { facts(it).playing && preferred(it) }
        ?: candidates.firstOrNull { facts(it).playing }
        ?: last?.takeIf { it in candidates }
        ?: candidates.firstOrNull()
}
