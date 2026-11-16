package dev.jesusmolano.alisio.core.media

/**
 * Las formas de pedir a una app de música que suene sin abrirla, de la más fiable a la menos
 * (docs/PLAN.md §11, «Música siempre a mano»).
 */
enum class ResumeStep {
    /** `MediaController.play()` sobre la sesión en pausa de la app. */
    SESSION,

    /** La tecla «reproducir» al sistema, que la entrega a la última sesión con botones multimedia. */
    MEDIA_KEY,

    /** `ACTION_MEDIA_BUTTON` dirigido al receptor de botones de la app. */
    MEDIA_BUTTON
}

/**
 * Qué pasos probar, en orden, para reanudar [packageName]. Lista vacía: no hay forma de saber si
 * suena, y quien llama abre la app directamente.
 *
 * - [ResumeStep.MEDIA_KEY] solo si [lastStarted] es esa app: el sistema da la tecla a la última
 *   sesión que sonó, sea de la app que sea, y un audio de WhatsApp o un vídeo posteriores se
 *   reanudarían en su lugar. Sin escuchar las sesiones ([watching]) no se sabe cuál fue la última.
 * - Sin escuchar las sesiones, la única prueba de que suena es que el sistema tenga audio activo. Si
 *   ya lo había antes de la orden ([audioActive]), no prueba nada.
 *
 * @param hasSession si la app tiene una sesión abierta (en pausa).
 * @param lastStarted la última app que se vio empezar a sonar, de cualquier tipo; `null` si no se sabe.
 */
fun resumeSteps(
    packageName: String,
    hasSession: Boolean,
    lastStarted: String?,
    watching: Boolean,
    audioActive: Boolean
): List<ResumeStep> {
    if (!watching && audioActive) return emptyList()
    return buildList {
        if (watching && hasSession) add(ResumeStep.SESSION)
        if (watching && lastStarted == packageName) add(ResumeStep.MEDIA_KEY)
        add(ResumeStep.MEDIA_BUTTON)
    }
}

/**
 * Lo que se deduce de un cambio en las apps que suenan.
 *
 * @param started las apps que acaban de empezar a sonar (de cualquier tipo; quien llama filtra las de música).
 * @param played si hay que apuntar ahora como «la última vez que sonó algo».
 * @param lastStarted la última app que se ha visto empezar a sonar; `null` si no se sabe.
 */
data class PlayingChange(val started: Set<String>, val played: Boolean, val lastStarted: String?)

/**
 * Compara dos lecturas de las apps que suenan ([before] y [now]) teniendo en cuenta si se escuchaban
 * las sesiones en cada una ([wasWatching] y [watching]).
 *
 * - Al empezar a escuchar (arranque del proceso, acceso concedido, pieza encendida) lo que ya sonaba
 *   no acaba de empezar: no se apunta como inicio, que falsearía la costumbre horaria de Ahora.
 * - Al dejar de escuchar, el vaciado no es que la música haya parado: no cambia nada y se olvida
 *   [lastStarted], que dejaría de estar al día.
 * - Si empiezan varias a la vez no se sabe cuál es la última.
 */
fun playingChange(
    before: Set<String>,
    now: Set<String>,
    wasWatching: Boolean,
    watching: Boolean,
    lastStarted: String?
): PlayingChange {
    if (!watching) return PlayingChange(emptySet(), played = false, lastStarted = null)
    if (!wasWatching) return PlayingChange(emptySet(), played = now.isNotEmpty(), lastStarted = now.singleOrNull())
    val started = now - before
    val last =
        when (started.size) {
            0 -> lastStarted
            1 -> started.single()
            else -> null
        }
    // Al dejar de sonar también se apunta: es el momento que importa para «acaba de sonar».
    return PlayingChange(started, played = now.isNotEmpty() || before.isNotEmpty(), lastStarted = last)
}
