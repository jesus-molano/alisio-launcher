package dev.jesusmolano.alisio.feature.home

import android.util.Log
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.core.gesture.Candidate
import dev.jesusmolano.alisio.core.gesture.GesturePoint
import dev.jesusmolano.alisio.core.gesture.GestureVerdict
import dev.jesusmolano.alisio.core.gesture.Haptic
import dev.jesusmolano.alisio.core.gesture.HomeGestureArbiter
import dev.jesusmolano.alisio.core.gesture.StartArea
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Etiqueta de logcat de los gestos del Inicio (`adb logcat -s AlisioGesture`). */
internal const val GESTURE_TAG = "AlisioGesture"
private const val TAG = GESTURE_TAG

// Cuánto se atenúa el Inicio con un pellizco o un mantener ya comprometidos.
private const val HOME_DIM = 0.3f

// La persiana propia: alto y alfa máxima del degradado que asoma desde arriba.
private val ShadeHeight = 160.dp
private const val SHADE_ALPHA = 0.22f

/**
 * La persiana que asoma desde arriba mientras deslizar hacia abajo va ganando: la de verdad es del
 * sistema y no se puede enseñar a medias. Se dibuja en la fase de dibujo con [progress].
 */
@Composable
internal fun BoxScope.NotificationsShade(progress: () -> Float) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(ShadeHeight)
            .drawBehind {
                val shown = progress()
                if (shown > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(AlisioTokens.Text.copy(alpha = SHADE_ALPHA * shown), Color.Transparent)
                        )
                    )
                }
            }
    )
}

/**
 * El último `ACTION_CANCEL` que ha recibido la actividad (`MainActivity.dispatchTouchEvent`), en
 * milisegundos de `uptimeMillis`. Compose convierte ese cancelar en un «soltar» sin pulsar, igual
 * que levantar el dedo: solo con esta hora sabe el Inicio que el gesto se lo ha quedado el sistema
 * (atrás, inicio, recientes) y que no debe ejecutar nada. Solo se usa en el hilo principal.
 */
internal object SystemCancel {
    var at = Long.MIN_VALUE
        private set

    fun mark(eventTime: Long) {
        at = eventTime
    }
}

/**
 * Lo que el gesto en curso insinúa, para dibujarlo: lo leen las capas en la fase de dibujo, así
 * seguir al dedo no recompone el Inicio. Cada gesto lleva su propio avance: el que va ganando sigue
 * al dedo y los demás se recogen con `Follow`, también cuando cambia el ganador a mitad de gesto
 * (una «N» empieza subiendo la píldora): nada desaparece de golpe.
 */
@Stable
internal class GestureFeedback(private val scope: CoroutineScope) {
    private val shown = Candidate.entries.associateWith { mutableFloatStateOf(0f) }

    /** Una animación en curso de un gesto y adónde va: 0 es recogerse. */
    private class Motion(val job: Job, val target: Float)

    private val motions = HashMap<Candidate, Motion>()
    var trail by mutableFloatStateOf(0f)
        private set
    private var leader: Candidate? = null
    private var committed = false

    /** Cuánto se ve ahora [candidate], de 0 a 1. */
    fun of(candidate: Candidate): Float = shown.getValue(candidate).floatValue

    /**
     * Alfa del contenido del Inicio: pellizcar o mantener lo atenúan, porque lo que va a salir
     * (Zonas, los ajustes) lo tapa entero. DISENO §2 solo deja animar alfa y 12 dp.
     */
    fun homeAlpha(): Float = 1f - HOME_DIM * maxOf(of(Candidate.ZONES), of(Candidate.SETTINGS))

    fun show(verdict: GestureVerdict) {
        trail = verdict.trail
        val next = verdict.leader
        Candidate.entries.forEach { if (it != next) retire(it) }
        if (next == null) {
            leader = null
            committed = false
            return
        }
        val committing = verdict.committed && (!committed || next != leader)
        leader = next
        committed = verdict.committed
        val running = motions[next]?.takeIf { it.job.isActive }
        if (committing && verdict.progress - of(next) > COMMIT_JUMP) {
            // Un compromiso de golpe (mantener) se anima; seguir al dedo, no.
            run(next, verdict.progress, AlisioMotion.fast())
        } else if (running == null || running.target == 0f) {
            // Mientras se anima un compromiso no se pisa; volver a ganar sí corta la recogida.
            running?.job?.cancel()
            motions.remove(next)
            shown.getValue(next).floatValue = verdict.progress
        }
    }

    /** Vuelve a reposo: lo insinuado se recoge con `Follow` en vez de desaparecer de golpe. */
    fun settle() {
        trail = 0f
        leader = null
        committed = false
        Candidate.entries.forEach(::retire)
    }

    private fun retire(candidate: Candidate) {
        val running = motions[candidate]?.takeIf { it.job.isActive }
        // En reposo y sin nada en marcha no hay nada que recoger. Un compromiso que aún no ha empezado
        // a subir sí se recoge: si no, acabaría en 1 sin ganador.
        if (running == null && of(candidate) == 0f) return
        // Ya va de vuelta: empezar otra vez en cada toque la frenaría.
        if (running != null && running.target == 0f) return
        run(candidate, 0f, AlisioMotion.follow())
    }

    private fun run(candidate: Candidate, target: Float, spec: AnimationSpec<Float>) {
        motions[candidate]?.job?.cancel()
        val state = shown.getValue(candidate)
        val job = scope.launch {
            animate(state.floatValue, target, animationSpec = spec) { v, _ -> state.floatValue = v }
        }
        motions[candidate] = Motion(job, target)
    }
}

// Un compromiso que salta más que esto respecto a lo insinuado se anima en vez de saltar.
private const val COMMIT_JUMP = 0.2f

/**
 * Cuántos dedos hay apoyados en el Inicio, contados antes que los hijos (pasada Initial). Un hijo
 * solo ve los dedos que caen sobre él: Trazo lo consulta para no quedarse un segundo dedo que es
 * de un pellizco. [claimed] dice si el Inicio se ha quedado el evento actual por ser un pellizco.
 */
internal class HomeFingers {
    var pressed = 0
        internal set
    var claimed = false
        internal set
}

/**
 * Cuenta los dedos en [fingers] y, con dos o más y [claim] cierto, se queda el evento antes que los
 * hijos: así el dock, el reproductor o Trazo no se llevan un pellizco que empezó o acabó sobre ellos
 * (sus detectores se retiran al ver el cambio consumido). Va en el Inicio y no se quita nunca.
 */
internal fun Modifier.homeFingers(fingers: HomeFingers, claim: () -> Boolean): Modifier = pointerInput(fingers) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            fingers.pressed = event.changes.count { it.pressed }
            fingers.claimed = fingers.pressed >= 2 && claim()
            if (fingers.claimed) event.changes.forEach(PointerInputChange::consume)
        }
    }
}

/**
 * Todos los gestos de un dedo y el pellizco del Inicio en un solo detector, repartidos por
 * [arbiter] (`core/gesture`). Mira la pasada Final, después de los hijos: si uno se queda el toque
 * (un icono, la píldora), el gesto empieza como [StartArea.TAPPED]; si uno se queda el movimiento
 * (Trazo, el deslizar del dock, el reproductor), el Inicio lo suelta sin hacer nada. Cuando el
 * árbitro reclama el gesto, consume los cambios: los hijos ven el toque consumido y no lo cuentan
 * como pulsación.
 *
 * El modificador no cambia nunca a mitad de gesto: lo que puede cambiar (si está en pausa, qué
 * gestos existen) se pregunta en cada toque. Quitarlo cancelaría también los gestos de los hijos.
 *
 * @param fingers el recuento de [homeFingers], que va antes en la misma cadena.
 * @param paused si ahora no se escucha (algo encima del Inicio, un Trazo, la elección de la inicial).
 * @param areaOf dónde cae un dedo que ningún hijo ha tomado.
 * @param onHaptic la vibración que pide el árbitro, solo al comprometerse o deshacerse un gesto.
 * @param onFire ejecuta el gesto ganador al soltar; con [Candidate.LETTER] recibe el trazo.
 */
internal fun Modifier.homeGestures(
    arbiter: HomeGestureArbiter,
    feedback: GestureFeedback,
    fingers: HomeFingers,
    trail: SnapshotStateList<Offset>,
    paused: () -> Boolean,
    areaOf: (Offset) -> StartArea,
    onHaptic: (Haptic) -> Unit,
    onFire: (Candidate, List<Offset>) -> Unit
): Modifier = pointerInput(arbiter) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (paused()) return@awaitEachGesture
        val geometric = areaOf(down.position)
        val area =
            if (down.isConsumed && geometric != StartArea.SYSTEM) StartArea.TAPPED else geometric
        val downTime = down.uptimeMillis
        val stroke = ArrayList<Offset>()
        var last = GesturePoint(down.position.x, down.position.y, downTime)
        var lastTime = downTime
        var previous = GestureVerdict.IDLE

        fun apply(verdict: GestureVerdict) {
            if (verdict.haptic != Haptic.NONE) onHaptic(verdict.haptic)
            if (verdict.leader != previous.leader || verdict.committed != previous.committed ||
                verdict.fire != null || verdict.haptic != Haptic.NONE
            ) {
                Log.d(
                    TAG,
                    "area=$area leader=${verdict.leader} committed=${verdict.committed} " +
                        "fire=${verdict.fire} haptic=${verdict.haptic}"
                )
            }
            previous = verdict
            feedback.show(verdict)
            // El rastro se guarda mientras pueda ser letra y se ve según lo que diga el árbitro.
            if (arbiter.letterPossible) {
                if (trail.size < stroke.size) trail.addAll(stroke.subList(trail.size, stroke.size))
            } else if (trail.isNotEmpty()) {
                trail.clear()
            }
        }

        apply(arbiter.down(last, area))
        stroke += down.position
        try {
            while (true) {
                // Con el dedo quieto, «mantener» se insinúa por fotogramas hasta su plazo.
                val wake = arbiter.nextTick(lastTime)
                val event =
                    if (wake == null) {
                        awaitPointerEvent(PointerEventPass.Final)
                    } else {
                        withTimeoutOrNull((wake - lastTime).coerceAtLeast(0L)) {
                            awaitPointerEvent(PointerEventPass.Final)
                        }
                    }
                if (event == null) {
                    lastTime = wake ?: lastTime
                    apply(arbiter.tick(lastTime))
                    continue
                }
                lastTime = event.changes.maxOf { it.uptimeMillis }
                // Un hijo se ha quedado el movimiento, o algo se ha puesto encima: el Inicio lo suelta.
                // Lo que consumió el propio Inicio por ser un pellizco (homeFingers) no cuenta.
                val childClaimed =
                    !fingers.claimed && event.changes.any { it.previousPressed && it.pressed && it.isConsumed }
                if (childClaimed || paused()) {
                    apply(arbiter.cancel())
                    break
                }
                val primary = event.changes.firstOrNull { it.id == down.id }
                if (primary != null && primary.pressed) {
                    last = GesturePoint(primary.position.x, primary.position.y, primary.uptimeMillis)
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    // Levantar y cancelar llegan igual; la hora del cancelar de la actividad los separa.
                    val cancelled = SystemCancel.at >= downTime
                    val end = last.copy(t = lastTime)
                    val verdict = if (cancelled) arbiter.cancel() else arbiter.up(end)
                    if (previous.claim) event.changes.forEach(PointerInputChange::consume)
                    apply(verdict)
                    if (cancelled) Log.d(TAG, "cancelado por el sistema")
                    verdict.fire?.let { onFire(it, if (it == Candidate.LETTER) stroke.toList() else emptyList()) }
                    break
                }
                val spread =
                    if (pressed.size >= 2) (pressed[0].position - pressed[1].position).getDistance() else null
                if (spread == null && primary != null && primary.pressed) stroke += primary.position
                val verdict = arbiter.move(last, spread)
                if (verdict.claim) event.changes.forEach(PointerInputChange::consume)
                apply(verdict)
            }
        } finally {
            // También si el gesto se corta a medias (el nodo se va o cambia su clave): sin restos.
            trail.clear()
            feedback.settle()
        }
    }
}
