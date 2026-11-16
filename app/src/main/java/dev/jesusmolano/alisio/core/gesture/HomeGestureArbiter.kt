package dev.jesusmolano.alisio.core.gesture

import dev.jesusmolano.alisio.core.draw.LETTER_MAX_STRAIGHTNESS
import dev.jesusmolano.alisio.core.zones.PINCH_IN_RATIO
import kotlin.math.abs
import kotlin.math.hypot

/** Los gestos del Inicio que comparten la pantalla y que el árbitro reparte. */
enum class Candidate { SEARCH, NOTIFICATIONS, LETTER, SETTINGS, LOCK, ZONES }

/**
 * Dónde empieza un gesto, que ya descarta algunos.
 *
 * - [DRAW]: el hueco entre la hora y el pie, donde se puede dibujar la inicial.
 * - [FREE]: el resto del Inicio sin gesto propio (la hora, el pie, la holgura del índice).
 * - [TAPPED]: un hijo con toque propio (un icono, la píldora, una nota) se ha quedado el toque: solo
 *   valen los deslizares y el pellizco.
 * - [LEFT_EDGE]: la franja del gesto atrás del borde izquierdo: una letra ahí la robaría el sistema.
 * - [SYSTEM]: arriba la barra de estado y abajo el gesto de inicio: ahí manda el sistema.
 */
enum class StartArea { DRAW, FREE, TAPPED, LEFT_EDGE, SYSTEM }

/** Qué vibración pide el veredicto: [COMMIT] al comprometerse un gesto y [RELEASE] al deshacerlo. */
enum class Haptic { NONE, COMMIT, RELEASE }

/** Qué gestos del Inicio existen ahora, según las preferencias y el servicio de accesibilidad. */
data class GestureOptions(val drawInitial: Boolean, val notifications: Boolean, val lock: Boolean, val pinch: Boolean)

/**
 * Umbrales del árbitro, ya en píxeles y milisegundos. [of] los saca de dp con la densidad de la
 * pantalla y de los tiempos de `ViewConfiguration`, para que el árbitro siga sin Android.
 */
data class GestureThresholds(
    val slop: Float,
    val commit: Float,
    val uncommit: Float,
    val cornerTail: Float,
    val retraceWidth: Float,
    val letterBox: Float,
    val holdEscape: Float,
    val doubleTapDistance: Float,
    val pinchMinDistance: Float,
    val turnStep: Float,
    val holdMillis: Long,
    val doubleTapMillis: Long
) {
    companion object {
        /** [density] son los píxeles de un dp; [touchSlop] ya va en píxeles. */
        fun of(density: Float, touchSlop: Float, longPressMillis: Long, doubleTapMillis: Long) = GestureThresholds(
            slop = touchSlop,
            commit = 48f * density,
            uncommit = 24f * density,
            cornerTail = 24f * density,
            retraceWidth = 16f * density,
            letterBox = 72f * density,
            holdEscape = 24f * density,
            doubleTapDistance = 48f * density,
            pinchMinDistance = 48f * density,
            turnStep = 12f * density,
            holdMillis = longPressMillis,
            doubleTapMillis = doubleTapMillis
        )
    }
}

/**
 * Lo que el Inicio tiene que hacer ahora con un gesto.
 *
 * @property leader el gesto que va ganando, para insinuarlo (la píldora sube, la persiana asoma).
 * @property progress cuánto le falta a [leader], de 0 a 1; 1 es «soltar ya lo hace».
 * @property committed si [leader] ya se ha comprometido: los demás están fuera.
 * @property fire el gesto que hay que ejecutar ahora; solo al soltar.
 * @property trail cuánto se ve el rastro de la letra, de 0 a 1: un deslizar recto no lo enseña.
 * @property claim si el Inicio se queda el gesto: consumirlo cancela la pulsación de los hijos.
 */
data class GestureVerdict(
    val leader: Candidate? = null,
    val progress: Float = 0f,
    val committed: Boolean = false,
    val fire: Candidate? = null,
    val haptic: Haptic = Haptic.NONE,
    val trail: Float = 0f,
    val claim: Boolean = false
) {
    companion object {
        val IDLE = GestureVerdict()
    }
}

// Por encima de esta rectitud, un trazo casi vertical es deslizar aunque se pueda leer como letra:
// abrir una app sin querer cuesta más que abrir la barra. Algo por debajo de la rectitud a partir de
// la cual el reconocedor ya no busca letra, así no queda hueco entre los dos.
private const val SWIPE_STRAIGHTNESS = 0.85f

// Rectitud a partir de la cual el rastro de la letra ya no se ve: un deslizar recto no pinta nada.
private const val TRAIL_HIDDEN_STRAIGHTNESS = 0.95f

// El pellizco comprometido se deshace al volver a separar los dedos hasta aquí (histéresis).
private const val PINCH_RELEASE_RATIO = 0.80f

// «Mantener» empieza a insinuarse pasado este margen (antes sería atenuar con cada toque) y crece
// por fotogramas hasta [HOLD_HINT_MAX]; el compromiso lo lleva a 1, así se nota el salto.
private const val HOLD_HINT_MILLIS = 150L
private const val HOLD_FRAME_MILLIS = 16L
private const val HOLD_HINT_MAX = 0.6f

private val SWIPES = setOf(Candidate.SEARCH, Candidate.NOTIFICATIONS)

/**
 * Árbitro de los gestos del Inicio (docs/PLAN.md §11, «Gestos sin pisarse»): decide por intención
 * (dirección, rectitud, giros, tiempo y distancia) y no por zona, insinúa lo que va ganando, deja
 * deshacer hasta soltar y solo deja un ganador. Es puro: el Inicio le pasa los toques y hace lo que
 * dice el [GestureVerdict].
 *
 * Un gesto es [down], cualquier número de [move] y [tick], y [up] o [cancel]. El último toque se
 * recuerda entre gestos para el doble toque.
 *
 * @param recognize si el trazo es una letra conocida; el Inicio le pasa el reconocedor de `core/draw`.
 */
class HomeGestureArbiter(
    var options: GestureOptions,
    var thresholds: GestureThresholds,
    private val recognize: (List<GesturePoint>) -> Boolean
) {
    private val points = ArrayList<GesturePoint>()
    private val alive = HashSet<Candidate>()
    private var active = false
    private var start = GesturePoint(0f, 0f, 0L)
    private var moved = false
    private var committed: Candidate? = null
    private var pinching = false
    private var pinchStart = 0f
    private var pinchProgress = 0f
    private var lastTap: GesturePoint? = null
    private var secondTap = false

    // Si la letra seguía posible al comprometerse «mantener»: deshacerlo la devuelve.
    private var letterBeforeHold = false

    /** Cuándo se compromete «mantener» si el dedo sigue quieto; `null` si ya no puede pasar. */
    val holdDeadline: Long?
        get() =
            if (active && !moved && !pinching && committed == null && Candidate.SETTINGS in alive) {
                start.t + thresholds.holdMillis
            } else {
                null
            }

    /**
     * Cuándo llamar a [tick] si no llega ningún toque antes, siendo [now] la hora del último: primero
     * al margen de la insinuación, luego cada fotograma hasta el plazo. `null` si no hace falta.
     */
    fun nextTick(now: Long): Long? {
        val deadline = holdDeadline ?: return null
        val hintFrom = start.t + HOLD_HINT_MILLIS
        return if (now < hintFrom) minOf(hintFrom, deadline) else minOf(deadline, now + HOLD_FRAME_MILLIS)
    }

    /** Si el gesto en curso aún puede acabar en letra: mientras tanto se guarda su rastro. */
    val letterPossible: Boolean get() = active && Candidate.LETTER in alive

    /** Baja el primer dedo en [point], que ha caído en [area]. */
    fun down(point: GesturePoint, area: StartArea): GestureVerdict {
        points.clear()
        points += point
        alive.clear()
        active = true
        start = point
        moved = false
        committed = null
        pinching = false
        pinchStart = 0f
        pinchProgress = 0f
        letterBeforeHold = false
        if (area != StartArea.SYSTEM) {
            alive += Candidate.SEARCH
            if (options.notifications) alive += Candidate.NOTIFICATIONS
            if (options.pinch) alive += Candidate.ZONES
            if (options.drawInitial && area == StartArea.DRAW) alive += Candidate.LETTER
            // Sobre un hijo, su toque y su pulsación larga son suyos.
            if (area != StartArea.TAPPED) {
                alive += Candidate.SETTINGS
                if (options.lock) alive += Candidate.LOCK
            }
        }
        val previous = lastTap
        lastTap = null
        secondTap = Candidate.LOCK in alive && previous != null &&
            point.t - previous.t <= thresholds.doubleTapMillis &&
            distance(point, previous) <= thresholds.doubleTapDistance
        return GestureVerdict.IDLE
    }

    /**
     * El dedo primero está en [point]. [spread] es la distancia entre los dos primeros dedos si hay
     * dos o más apoyados, o `null` si solo hay uno.
     */
    fun move(point: GesturePoint, spread: Float? = null): GestureVerdict {
        if (!active) return GestureVerdict.IDLE
        // Con un gesto de un dedo ya comprometido, un segundo dedo no cambia nada.
        val pinchNow = spread != null && (committed == null || committed == Candidate.ZONES)
        if (pinching || pinchNow) return pinch(spread)
        return single(point)
    }

    /**
     * Pasa el tiempo sin moverse: «mantener» se insinúa poco a poco y se compromete al cumplirse su
     * plazo, el único momento en que vibra.
     */
    fun tick(now: Long): GestureVerdict {
        val deadline = holdDeadline ?: return GestureVerdict.IDLE
        if (now < deadline) return holdHint(now)
        letterBeforeHold = Candidate.LETTER in alive
        committed = Candidate.SETTINGS
        alive.retainAll(setOf(Candidate.SETTINGS))
        return GestureVerdict(Candidate.SETTINGS, 1f, committed = true, haptic = Haptic.COMMIT, claim = true)
    }

    /** Lo que se ve de «mantener» antes de su plazo: nada hasta el margen, luego crece con el tiempo. */
    private fun holdHint(now: Long): GestureVerdict {
        val deadline = holdDeadline ?: return GestureVerdict.IDLE
        val hintFrom = start.t + HOLD_HINT_MILLIS
        if (now < hintFrom || deadline <= hintFrom) return GestureVerdict.IDLE
        val fraction = ((now - hintFrom).toFloat() / (deadline - hintFrom)).coerceIn(0f, 1f)
        return GestureVerdict(Candidate.SETTINGS, HOLD_HINT_MAX * fraction)
    }

    /** Se levanta el último dedo en [point]: si algo gana, va en [GestureVerdict.fire]. */
    fun up(point: GesturePoint): GestureVerdict {
        if (!active) return GestureVerdict.IDLE
        if (pinching) {
            active = false
            return GestureVerdict(fire = Candidate.ZONES.takeIf { committed == it })
        }
        val last = single(point)
        active = false
        val haptic = last.haptic
        when (val winner = committed) {
            Candidate.SETTINGS -> return GestureVerdict(fire = winner)

            Candidate.SEARCH, Candidate.NOTIFICATIONS ->
                // La vibración de compromiso ya sonó al pasar el umbral, salvo que pase justo ahora.
                return GestureVerdict(fire = winner, haptic = haptic.takeIf { it == Haptic.COMMIT } ?: Haptic.NONE)

            else -> Unit
        }
        if (!moved) return tap(point)
        // Sin inicial posible, lo que no se comprometió al moverse no hace nada al soltar.
        if (Candidate.LETTER !in alive) return GestureVerdict.IDLE
        return release()
    }

    /** El sistema se ha quedado el gesto (atrás, inicio): nada se ejecuta y nada vibra. */
    fun cancel(): GestureVerdict {
        active = false
        committed = null
        alive.clear()
        lastTap = null
        return GestureVerdict.IDLE
    }

    private fun single(point: GesturePoint): GestureVerdict {
        points += point
        val travelled = distance(point, start)
        if (!moved && travelled > thresholds.slop) {
            moved = true
            // Moverse ya no es un toque ni un mantener, para todo el gesto.
            if (committed != Candidate.SETTINGS) {
                alive.remove(Candidate.SETTINGS)
                alive.remove(Candidate.LOCK)
            }
        }
        if (committed == Candidate.SETTINGS) {
            if (travelled <= thresholds.holdEscape) {
                return GestureVerdict(Candidate.SETTINGS, 1f, committed = true, claim = true)
            }
            // Alejarse después de comprometerse lo deshace. Si se podía dibujar la inicial, el gesto
            // vuelve a poder ser letra (dudar antes de empezarla no la pierde); si no, ya no hace nada.
            committed = null
            alive.clear()
            if (letterBeforeHold) alive += Candidate.LETTER
            return GestureVerdict(haptic = Haptic.RELEASE, claim = true)
        }
        if (!moved) return holdHint(point.t)
        val metrics = GestureMetrics.of(points, thresholds.turnStep)
        val direction = if (metrics.dy < 0f) Candidate.SEARCH else Candidate.NOTIFICATIONS
        return if (Candidate.LETTER in alive) withLetter(metrics, direction) else withoutLetter(metrics, direction)
    }

    /**
     * Con la inicial posible, el deslizar se insinúa mientras vaya recto y en vertical, pero solo se
     * decide al soltar: muchas letras empiezan con un tramo recto largo. Lo que se insinúa aquí es lo
     * que [release] ejecuta: al soltar se aplican las mismas reglas.
     */
    private fun withLetter(metrics: GestureMetrics, direction: Candidate): GestureVerdict {
        // Volver por el mismo camino recoge el deslizar: no es una letra que nadie dibuja.
        retrace(metrics)?.let { back ->
            if (back.direction !in alive) return GestureVerdict(claim = true)
            return GestureVerdict(back.direction, back.along / thresholds.commit, claim = true).clamped()
        }
        if (shaped(metrics)) alive.removeAll(SWIPES)
        val straightTrail = (
            (TRAIL_HIDDEN_STRAIGHTNESS - metrics.straightness) /
                (TRAIL_HIDDEN_STRAIGHTNESS - SWIPE_STRAIGHTNESS)
            ).coerceIn(0f, 1f)
        if (!swipeLeads(metrics, direction)) {
            // El rastro aparece según se va viendo la letra: por lo torcido o por lo que sigue a la esquina.
            val trail = maxOf(straightTrail, (metrics.afterCorner / thresholds.letterBox).coerceIn(0f, 1f))
            return GestureVerdict(Candidate.LETTER, trail = trail, claim = true)
        }
        return GestureVerdict(direction, abs(metrics.dy) / thresholds.commit, trail = straightTrail, claim = true)
            .clamped()
    }

    /**
     * Si el trazo ya es letra para el resto del gesto: una esquina seguida de un tramo que se ve
     * ([GestureThresholds.cornerTail]; un gancho al soltar un deslizar no lo es) o un trazo largo ya
     * claramente torcido.
     */
    private fun shaped(metrics: GestureMetrics) =
        (metrics.turn > CORNER_DEGREES && metrics.afterCorner >= thresholds.cornerTail) ||
            (metrics.straightness < SWIPE_STRAIGHTNESS && metrics.path >= thresholds.letterBox)

    private fun swipeLeads(metrics: GestureMetrics, direction: Candidate) =
        direction in alive && metrics.inCone && metrics.straightness >= SWIPE_STRAIGHTNESS

    /** Un deslizar que vuelve por su eje: hacia dónde fue, cuánto llegó a avanzar y cuánto le queda. */
    private class Retrace(val direction: Candidate, val peak: Float, val along: Float)

    /**
     * Si el dedo ha ido en vertical y está volviendo por el mismo camino (estrecho, primero solo
     * avanza y luego solo retrocede, con la holgura de temblor). Una «N» o una «B» se salen de lo
     * estrecho en cuanto hacen su segundo trazo y vuelven a ser letra.
     */
    private fun retrace(metrics: GestureMetrics): Retrace? {
        if (metrics.width > thresholds.retraceWidth) return null
        val slop = thresholds.slop
        val first = points.firstOrNull { abs(it.y - start.y) > slop } ?: return null
        val sign = if (first.y < start.y) -1f else 1f
        var peak = 0f
        var peakAt = 0
        points.forEachIndexed { i, p ->
            val along = (p.y - start.y) * sign
            if (along > peak) {
                peak = along
                peakAt = i
            }
        }
        var high = 0f
        for (i in 0..peakAt) {
            val along = (points[i].y - start.y) * sign
            if (along < high - slop) return null
            high = maxOf(high, along)
        }
        var low = peak
        for (i in peakAt + 1 until points.size) {
            val along = (points[i].y - start.y) * sign
            if (along > low + slop) return null
            low = minOf(low, along)
        }
        val along = (points.last().y - start.y) * sign
        if (along >= peak) return null
        return Retrace(if (sign < 0f) Candidate.SEARCH else Candidate.NOTIFICATIONS, peak, along)
    }

    /** Sin inicial posible, el deslizar se compromete al pasar el umbral y se deshace al volver. */
    private fun withoutLetter(metrics: GestureMetrics, direction: Candidate): GestureVerdict {
        var haptic = Haptic.NONE
        committed?.let { current ->
            val along = if (current == Candidate.SEARCH) -metrics.dy else metrics.dy
            if (along < thresholds.uncommit) {
                committed = null
                haptic = Haptic.RELEASE
            }
        }
        if (committed == null && direction in alive && metrics.inCone && abs(metrics.dy) >= thresholds.commit) {
            committed = direction
            // Si en el mismo paso se deshizo uno y se comprometió el contrario, suena solo el nuevo.
            haptic = Haptic.COMMIT
        }
        val leader = committed ?: direction.takeIf { it in alive && metrics.inCone }
        val progress = if (leader == null) 0f else abs(metrics.dy) / thresholds.commit
        return GestureVerdict(leader, progress, committed = committed != null, haptic = haptic, claim = true).clamped()
    }

    /**
     * Soltar un trazo que pudo ser letra, con las reglas de [withLetter]: lo que se veía venir es lo
     * que pasa. Un deslizar recogido no hace nada si volvió por debajo de `uncommit`; lo recto que
     * seguía vivo es deslizar; lo demás se lee como letra.
     */
    private fun release(): GestureVerdict {
        val metrics = GestureMetrics.of(points, thresholds.turnStep)
        retrace(metrics)?.let { back ->
            val kept = back.direction in alive && back.peak >= thresholds.commit && back.along >= thresholds.uncommit
            // Sin vibrar al deshacerlo: con la inicial posible nada se había comprometido antes.
            return if (kept) GestureVerdict(fire = back.direction, haptic = Haptic.COMMIT) else GestureVerdict.IDLE
        }
        if (shaped(metrics)) alive.removeAll(SWIPES)
        val direction = if (metrics.dy < 0f) Candidate.SEARCH else Candidate.NOTIFICATIONS
        if (swipeLeads(metrics, direction) && abs(metrics.dy) >= thresholds.commit) {
            return GestureVerdict(fire = direction, haptic = Haptic.COMMIT)
        }
        if (recognize(points)) return GestureVerdict(fire = Candidate.LETTER)
        // Solo un intento claro de letra se dice («límite» lo pone quien la lee); un trazo recto que no
        // es nada se queda en nada, sin vibrar.
        val attempt = metrics.straightness < LETTER_MAX_STRAIGHTNESS && metrics.box >= thresholds.letterBox
        return if (attempt) GestureVerdict(fire = Candidate.LETTER) else GestureVerdict.IDLE
    }

    private fun tap(point: GesturePoint): GestureVerdict {
        if (Candidate.LOCK !in alive || point.t - start.t >= thresholds.holdMillis) return GestureVerdict.IDLE
        if (secondTap) return GestureVerdict(fire = Candidate.LOCK, haptic = Haptic.COMMIT)
        lastTap = point
        return GestureVerdict.IDLE
    }

    private fun pinch(spread: Float?): GestureVerdict {
        if (!pinching) {
            pinching = true
            // Dos dedos antes de comprometer nada: solo queda el pellizco, y el rastro se borra.
            alive.retainAll(setOf(Candidate.ZONES))
            committed = null
            pinchStart = 0f
        }
        if (Candidate.ZONES !in alive) return GestureVerdict(claim = true)
        var haptic = Haptic.NONE
        if (spread == null) {
            // Se ha levantado un dedo: el siguiente par mide desde donde caiga, salvo que ya esté comprometido.
            if (committed == null) pinchStart = 0f
        } else if (pinchStart == 0f) {
            if (committed == null) pinchStart = spread
        } else if (pinchStart >= thresholds.pinchMinDistance) {
            val ratio = spread / pinchStart
            pinchProgress = ((1f - ratio) / (1f - PINCH_IN_RATIO)).coerceIn(0f, 1f)
            if (committed == null && ratio <= PINCH_IN_RATIO) {
                committed = Candidate.ZONES
                haptic = Haptic.COMMIT
            } else if (committed == Candidate.ZONES && ratio >= PINCH_RELEASE_RATIO) {
                committed = null
                haptic = Haptic.RELEASE
            }
        }
        return GestureVerdict(
            Candidate.ZONES,
            pinchProgress,
            committed = committed == Candidate.ZONES,
            haptic = haptic,
            claim = true
        )
    }

    private fun GestureVerdict.clamped() = copy(progress = progress.coerceIn(0f, 1f))

    private fun distance(a: GesturePoint, b: GesturePoint) = hypot(a.x - b.x, a.y - b.y)
}
