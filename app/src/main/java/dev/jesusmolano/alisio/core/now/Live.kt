package dev.jesusmolano.alisio.core.now

/**
 * Lo que interesa de una notificación para saber si es algo en curso (docs/IDEAS.md §2.7): un viaje,
 * un temporizador, un pedido. Solo banderas y tiempos; el texto lo guarda quien la lee, en memoria.
 *
 * @param promoted lleva la marca de Live Update del sistema (`FLAG_PROMOTED_ONGOING`).
 * @param isMedia es el aviso de un reproductor (con sesión multimedia).
 */
class LiveSignals(
    val key: String,
    val packageName: String,
    val ongoing: Boolean,
    val promoted: Boolean,
    val category: String?,
    val hasProgress: Boolean,
    val usesChronometer: Boolean,
    val isMedia: Boolean,
    val postedAt: Long
)

// Categorías de Android para cosas en curso. One UI publica muchas sin la marca de Live Update, así
// que estas se aceptan también si llevan progreso o cronómetro.
private val LIVE_CATEGORIES = setOf("navigation", "progress", "transport", "stopwatch")

/**
 * Si la notificación cuenta como algo en curso. Un reproductor nunca: ya tiene su placa. Lo marcado
 * como Live Update vale siempre; lo demás, solo si es permanente, de una categoría de cosas en curso
 * y con algo que avanza (progreso o cronómetro): así no entra cualquier aviso fijo.
 */
fun isLive(signals: LiveSignals): Boolean = !signals.isMedia &&
    (
        signals.promoted ||
            (signals.ongoing && signals.category in LIVE_CATEGORIES && (signals.hasProgress || signals.usesChronometer))
        )

/** De todo lo que está en curso, lo último en publicarse; `null` si no hay nada. */
fun <T> chooseLive(items: List<T>, signals: (T) -> LiveSignals): T? =
    items.filter { isLive(signals(it)) }.maxByOrNull { signals(it).postedAt }
