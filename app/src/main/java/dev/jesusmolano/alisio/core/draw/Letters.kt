package dev.jesusmolano.alisio.core.draw

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Un punto del trazo, en píxeles o en unidades normalizadas; el eje y crece hacia abajo. */
data class StrokePoint(val x: Float, val y: Float)

// Puntos de cada trazo tras repartirlo a distancias iguales: bastan para distinguir 25 letras.
private const val SAMPLES = 64

// Distancia media (en lados de la caja) por encima de la cual el trazo no se parece a ninguna letra.
private const val MAX_DISTANCE = 0.17f

// Ventaja mínima de la mejor letra sobre la segunda: si dos se parecen igual, no se adivina.
private const val MIN_MARGIN = 0.012f

// Un trazo casi recto es deslizar, no dibujar: es el gesto que ya tenían la barra y las notificaciones.
private const val MAX_STRAIGHTNESS = 0.9f

/**
 * Reconoce la letra mayúscula que dibuja un trazo de un solo dedo, comparándolo con plantillas de
 * un trazo ([LETTER_TEMPLATES], al estilo del reconocedor $1 de Wobbrock, sin girar): se reparte en
 * [SAMPLES] puntos, se lleva a una caja de lado 1 sin deformarlo y se mide la distancia media punto
 * a punto. Devuelve `null` si el trazo es pequeño, casi recto, o no se parece claramente a una.
 *
 * @param minSize lado mínimo de la caja del trazo, en las unidades de [points]: un garabato pequeño
 *   es un toque que se ha movido, no una letra.
 */
fun recognizeLetter(points: List<StrokePoint>, minSize: Float): Char? {
    if (points.size < 2) return null
    val width = points.maxOf { it.x } - points.minOf { it.x }
    val height = points.maxOf { it.y } - points.minOf { it.y }
    if (maxOf(width, height) < minSize) return null
    val length = pathLength(points)
    if (length <= 0f) return null
    val first = points.first()
    val last = points.last()
    if (hypot(last.x - first.x, last.y - first.y) / length > MAX_STRAIGHTNESS) return null
    val ranked = letterDistances(points)
    val (best, bestDistance) = ranked.first()
    val second = ranked.getOrNull(1)?.second ?: Float.MAX_VALUE
    if (bestDistance > MAX_DISTANCE || second - bestDistance < MIN_MARGIN) return null
    return best
}

/**
 * Plantillas de un trazo: cada letra con las formas más habituales de dibujarla de una vez, en una
 * caja de 0 a 1. Falta la «I»: un trazo recto es deslizar.
 */
val LETTER_TEMPLATES: List<Pair<Char, List<StrokePoint>>> =
    listOf(
        'A' to line(0f to 1f, 0.5f to 0f, 1f to 1f),
        'A' to line(0f to 1f, 0.5f to 0f, 1f to 1f, 0.75f to 0.55f, 0.25f to 0.55f),
        'B' to
            line(0f to 1f, 0f to 0f) + arc(0.3f, 0.25f, 0.4f, 0.25f, -90f, 90f) +
            arc(0.35f, 0.75f, 0.45f, 0.25f, -90f, 90f),
        'B' to line(0f to 0f, 0f to 1f, 0f to 0f) + arc(0.3f, 0.25f, 0.4f, 0.25f, -90f, 90f) +
            arc(0.35f, 0.75f, 0.45f, 0.25f, -90f, 90f),
        'C' to arc(0.5f, 0.5f, 0.5f, 0.5f, -45f, -315f),
        'D' to line(0f to 1f, 0f to 0f) + arc(0f, 0.5f, 0.8f, 0.5f, -90f, 90f),
        'D' to line(0f to 0f, 0f to 1f, 0f to 0f) + arc(0f, 0.5f, 0.8f, 0.5f, -90f, 90f),
        'E' to arc(0.55f, 0.27f, 0.4f, 0.25f, -30f, -270f) + arc(0.55f, 0.75f, 0.45f, 0.25f, -90f, -330f),
        'E' to line(1f to 0f, 0f to 0f, 0f to 0.5f, 0.7f to 0.5f, 0f to 0.5f, 0f to 1f, 1f to 1f),
        'F' to line(1f to 0f, 0f to 0f, 0f to 1f),
        'F' to line(1f to 0f, 0f to 0f, 0f to 0.5f, 0.7f to 0.5f, 0f to 0.5f, 0f to 1f),
        'G' to arc(0.5f, 0.5f, 0.5f, 0.5f, -45f, -360f) + line(1f to 0.5f, 0.55f to 0.5f),
        'H' to line(0f to 0f, 0f to 1f, 0f to 0.6f, 0.2f to 0.42f, 0.55f to 0.4f, 0.8f to 0.55f, 0.8f to 1f),
        'J' to line(0.8f to 0f, 0.8f to 0.7f) + arc(0.45f, 0.7f, 0.35f, 0.3f, 0f, 180f),
        'K' to line(0f to 0f, 0f to 1f, 0f to 0.6f, 0.85f to 0f, 0.25f to 0.45f, 0.9f to 1f),
        'L' to line(0f to 0f, 0f to 1f, 0.7f to 1f),
        'M' to line(0f to 1f, 0f to 0f, 0.5f to 0.65f, 1f to 0f, 1f to 1f),
        'N' to line(0f to 1f, 0f to 0f, 1f to 1f, 1f to 0f),
        'O' to arc(0.5f, 0.5f, 0.5f, 0.5f, -90f, -450f),
        'O' to arc(0.5f, 0.5f, 0.5f, 0.5f, -90f, 270f),
        'P' to line(0f to 1f, 0f to 0f) + arc(0f, 0.27f, 0.7f, 0.27f, -90f, 90f),
        'Q' to arc(0.5f, 0.5f, 0.5f, 0.5f, -90f, -450f) + line(0.5f to 0f, 0.6f to 0.6f, 1f to 1f),
        'R' to line(0f to 1f, 0f to 0f) + arc(0f, 0.27f, 0.7f, 0.27f, -90f, 90f) + line(0f to 0.54f, 0.8f to 1f),
        'S' to arc(0.5f, 0.25f, 0.45f, 0.25f, -20f, -270f) + arc(0.5f, 0.75f, 0.45f, 0.25f, -90f, 160f),
        'T' to line(0f to 0f, 1f to 0f, 0.5f to 0f, 0.5f to 1f),
        'U' to line(0f to 0f, 0f to 0.55f) + arc(0.5f, 0.55f, 0.5f, 0.45f, 180f, 0f) + line(1f to 0.55f, 1f to 0f),
        'V' to line(0f to 0f, 0.5f to 1f, 1f to 0f),
        'W' to line(0f to 0f, 0.25f to 1f, 0.5f to 0.35f, 0.75f to 1f, 1f to 0f),
        'X' to line(0f to 0f, 1f to 1f, 1f to 0f, 0f to 1f),
        'X' to line(1f to 0f, 0f to 1f, 0f to 0f, 1f to 1f),
        'Y' to line(0f to 0f, 0.5f to 0.5f, 1f to 0f, 0.25f to 1f),
        'Z' to line(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
    )

/** Cada letra con la distancia de [points] a su plantilla más cercana, de la más parecida a la menos. */
internal fun letterDistances(points: List<StrokePoint>): List<Pair<Char, Float>> {
    val stroke = normalized(smoothed(points))
    return TEMPLATES.map { (letter, template) -> letter to distance(stroke, template) }
        .groupBy({ it.first }, { it.second })
        .map { (letter, distances) -> letter to distances.min() }
        .sortedBy { it.second }
}

private val TEMPLATES: List<Pair<Char, List<StrokePoint>>> = LETTER_TEMPLATES.map { (letter, points) ->
    letter to
        normalized(points)
}

private fun line(vararg corners: Pair<Float, Float>): List<StrokePoint> = corners.map {
    StrokePoint(it.first, it.second)
}

/** Un arco de elipse de [from] a [to] grados (0 a la derecha, 90 abajo), en el sentido que marquen. */
private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float): List<StrokePoint> {
    val steps = 16
    return (0..steps).map { step ->
        val angle = Math.toRadians((from + (to - from) * step / steps).toDouble())
        StrokePoint(cx + rx * cos(angle).toFloat(), cy + ry * sin(angle).toFloat())
    }
}

/**
 * Media de cada punto con sus vecinos: la pantalla da muchos puntos con temblor de un píxel, y ese
 * zigzag alarga el trazo y descoloca el reparto de [resample]. Los extremos se quedan donde están.
 */
private fun smoothed(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 3) return points
    return points.indices.map { i ->
        if (i == 0 || i == points.lastIndex) {
            points[i]
        } else {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            StrokePoint((a.x + 2 * b.x + c.x) / 4, (a.y + 2 * b.y + c.y) / 4)
        }
    }
}

private fun pathLength(points: List<StrokePoint>): Float =
    points.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()

/** Repartido a distancias iguales y llevado a una caja de lado 1 centrada en 0, sin deformarlo. */
private fun normalized(points: List<StrokePoint>): List<StrokePoint> {
    val even = resample(points, SAMPLES)
    val minX = even.minOf { it.x }
    val minY = even.minOf { it.y }
    val side = maxOf(even.maxOf { it.x } - minX, even.maxOf { it.y } - minY).coerceAtLeast(1e-6f)
    val centerX = (minX + even.maxOf { it.x }) / 2
    val centerY = (minY + even.maxOf { it.y }) / 2
    return even.map { StrokePoint((it.x - centerX) / side, (it.y - centerY) / side) }
}

private fun resample(points: List<StrokePoint>, count: Int): List<StrokePoint> {
    val step = pathLength(points) / (count - 1)
    if (step <= 0f) return List(count) { points.first() }
    val result = ArrayList<StrokePoint>(count)
    result += points.first()
    var carried = 0f
    var previous = points.first()
    var index = 1
    while (index < points.size && result.size < count) {
        val next = points[index]
        val segment = hypot(next.x - previous.x, next.y - previous.y)
        if (carried + segment >= step && segment > 0f) {
            val t = (step - carried) / segment
            val point = StrokePoint(previous.x + t * (next.x - previous.x), previous.y + t * (next.y - previous.y))
            result += point
            previous = point
            carried = 0f
        } else {
            carried += segment
            previous = next
            index++
        }
    }
    while (result.size < count) result += points.last()
    return result
}

private fun distance(a: List<StrokePoint>, b: List<StrokePoint>): Float =
    a.indices.sumOf { hypot(a[it].x - b[it].x, a[it].y - b[it].y).toDouble() }.toFloat() / a.size
