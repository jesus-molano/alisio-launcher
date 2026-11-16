package dev.jesusmolano.alisio.core.gesture

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

/** Un punto de un gesto: posición en píxeles (el eje y crece hacia abajo) y tiempo en milisegundos. */
data class GesturePoint(val x: Float, val y: Float, val t: Long)

/**
 * Lo que dice la forma de un trazo de un dedo: cuánto se ha movido, si va recto y si ha girado.
 * El árbitro del Inicio decide con esto qué gesto va ganando.
 *
 * @property straightness cuerda entre recorrido: 1 es una recta; una letra con curvas o esquinas
 *   baja mucho.
 * @property turn el mayor giro, en grados, entre el primer tramo y cualquiera de los siguientes.
 * @property afterCorner lo recorrido desde la primera esquina (giro mayor que [CORNER_DEGREES]) hasta
 *   el final; 0 si no hay esquina. Un gancho corto al soltar un deslizar no llega a ser letra.
 */
class GestureMetrics(
    val dx: Float,
    val dy: Float,
    val path: Float,
    val chord: Float,
    val width: Float,
    val height: Float,
    val turn: Float,
    val afterCorner: Float = 0f
) {
    val straightness: Float get() = if (path <= 0f) 1f else (chord / path).coerceAtMost(1f)

    /** El lado mayor de la caja del trazo: lo que mide el reconocedor para aceptar una letra. */
    val box: Float get() = max(width, height)

    /** Si va casi en vertical: lo que se desvía a lo ancho no pasa de [CONE_RATIO] de lo que baja o sube. */
    val inCone: Boolean get() = dy != 0f && abs(dx) <= CONE_RATIO * abs(dy)

    companion object {
        /**
         * Mide [points]. Los giros se miden entre tramos de al menos [turnStep] píxeles: con tramos más
         * cortos, el temblor del dedo sobre la pantalla parecería un giro.
         */
        fun of(points: List<GesturePoint>, turnStep: Float): GestureMetrics {
            if (points.isEmpty()) return GestureMetrics(0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val first = points.first()
            val last = points.last()
            // Lo recorrido hasta cada punto, para medir el tramo que sigue a la esquina.
            val travelled = FloatArray(points.size)
            for (i in 1 until points.size) {
                travelled[i] =
                    travelled[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            }
            val path = travelled.last()
            val dx = last.x - first.x
            val dy = last.y - first.y
            val turn = turnOf(points, turnStep)
            return GestureMetrics(
                dx = dx,
                dy = dy,
                path = path,
                chord = hypot(dx, dy),
                width = points.maxOf { it.x } - points.minOf { it.x },
                height = points.maxOf { it.y } - points.minOf { it.y },
                turn = turn.degrees,
                afterCorner = if (turn.cornerAt < 0) 0f else path - travelled[turn.cornerAt]
            )
        }
    }
}

/** Un giro mayor que este respecto al primer tramo es una esquina de letra, no un deslizar. */
const val CORNER_DEGREES = 60f

/**
 * Cuánto se puede desviar a lo ancho un deslizar vertical, en proporción a lo que sube o baja (unos
 * 35°): un pulgar que sube en arco desde abajo a la derecha sigue siendo un deslizar.
 */
const val CONE_RATIO = 0.7f

/** El mayor giro de un trazo y el índice del punto de su primera esquina (-1 si no hay). */
private class Turn(val degrees: Float, val cornerAt: Int)

/**
 * El mayor ángulo entre la dirección del primer tramo y la de cualquier tramo posterior. Una «L»
 * gira 90°; un deslizar en arco de pulgar, unos 20°.
 *
 * La esquina es, dentro del primer tramo que gira más de [CORNER_DEGREES], el punto que más avanza
 * en la dirección inicial: el ancla del tramo puede quedar hasta [step] antes del vértice, y medir
 * desde ahí haría pasar por letra un gancho corto.
 */
private fun turnOf(points: List<GesturePoint>, step: Float): Turn {
    var anchorAt = 0
    var firstX = 0f
    var firstY = 0f
    var hasFirst = false
    var turn = 0f
    var cornerAt = -1
    for (i in points.indices) {
        val sx = points[i].x - points[anchorAt].x
        val sy = points[i].y - points[anchorAt].y
        val length = hypot(sx, sy)
        if (length < step) continue
        if (!hasFirst) {
            firstX = sx / length
            firstY = sy / length
            hasFirst = true
        } else {
            val cosine = ((firstX * sx + firstY * sy) / length).coerceIn(-1f, 1f)
            val degrees = Math.toDegrees(acos(cosine).toDouble()).toFloat()
            turn = max(turn, degrees)
            if (cornerAt < 0 && degrees > CORNER_DEGREES) {
                cornerAt = (anchorAt..i).maxBy { points[it].x * firstX + points[it].y * firstY }
            }
        }
        anchorAt = i
    }
    return Turn(turn, cornerAt)
}
