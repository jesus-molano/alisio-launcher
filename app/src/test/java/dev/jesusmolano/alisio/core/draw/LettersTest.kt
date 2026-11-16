package dev.jesusmolano.alisio.core.draw

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LettersTest {
    private val minSize = 60f

    /** La plantilla a tamaño de dedo, con temblor y algo estirada, como la dibujaría una mano. */
    private fun drawn(points: List<StrokePoint>, size: Float, stretchX: Float, seed: Int): List<StrokePoint> {
        val random = Random(seed)
        // La mano no acierta cada esquina: cada punto de la plantilla se mueve hasta 4 px.
        val shaky =
            points.map {
                StrokePoint(
                    300f + it.x * size * stretchX + random.nextFloat() * 8f - 4f,
                    500f + it.y * size + random.nextFloat() * 8f - 4f
                )
            }
        // Más puntos por tramo, como los que da la pantalla al mover el dedo, con medio píxel de ruido.
        return shaky.zipWithNext().flatMap { (a, b) ->
            (0 until 6).map { i ->
                val t = i / 6f
                StrokePoint(
                    a.x + (b.x - a.x) * t + random.nextFloat() - 0.5f,
                    a.y + (b.y - a.y) * t + random.nextFloat() - 0.5f
                )
            }
        } + shaky.last()
    }

    @Test
    fun `cada plantilla dibujada a mano se reconoce`() {
        // Se juntan todos los fallos para ver de una vez qué letras se confunden con cuáles.
        val wrong =
            LETTER_TEMPLATES.flatMapIndexed { index, (letter, points) ->
                listOf(0.85f, 1f, 1.15f).mapNotNull { stretch ->
                    val stroke = drawn(points, size = 220f, stretchX = stretch, seed = index)
                    val found = recognizeLetter(stroke, minSize)
                    val near = letterDistances(stroke).take(2).joinToString { "%s %.3f".format(it.first, it.second) }
                    "$letter×$stretch→$found ($near)".takeIf { found != letter }
                }
            }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `una o dibujada como un poligono`() {
        val circle =
            (0..24).map { step ->
                val angle = -PI / 2 - 2 * PI * step / 24
                StrokePoint(200f + 90f * cos(angle).toFloat(), 400f + 100f * sin(angle).toFloat())
            }
        assertEquals('O', recognizeLetter(circle, minSize))
    }

    @Test
    fun `un trazo recto es deslizar, no una letra`() {
        val down = (0..20).map { StrokePoint(100f + it * 0.5f, 100f + it * 15f) }
        assertNull(recognizeLetter(down, minSize))
        val diagonal = (0..20).map { StrokePoint(100f + it * 10f, 100f + it * 10f) }
        assertNull(recognizeLetter(diagonal, minSize))
    }

    @Test
    fun `un garabato pequeno no es una letra`() {
        val tiny = drawn(LETTER_TEMPLATES.first { it.first == 'S' }.second, size = 30f, stretchX = 1f, seed = 1)
        assertNull(recognizeLetter(tiny, minSize))
        assertNull(recognizeLetter(listOf(StrokePoint(0f, 0f)), minSize))
    }

    @Test
    fun `un garabato sin forma no se adivina`() {
        val random = Random(7)
        val scribble = (0..60).map { StrokePoint(200f + random.nextFloat() * 200f, 300f + random.nextFloat() * 200f) }
        assertNull(recognizeLetter(scribble, minSize))
    }

    @Test
    fun `todas las letras menos la i tienen plantilla`() {
        val letters = LETTER_TEMPLATES.map { it.first }.toSet()
        assertTrue(('A'..'Z').all { it == 'I' || it in letters })
    }
}
