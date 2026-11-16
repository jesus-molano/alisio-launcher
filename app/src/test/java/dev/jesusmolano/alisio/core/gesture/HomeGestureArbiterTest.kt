package dev.jesusmolano.alisio.core.gesture

import dev.jesusmolano.alisio.core.draw.StrokePoint
import dev.jesusmolano.alisio.core.draw.recognizeLetter
import kotlin.math.hypot
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Como el emulador de las pruebas: 1 dp son 3 px. Los trazos van en píxeles.
private const val DENSITY = 3f
private val THRESHOLDS = GestureThresholds.of(
    DENSITY,
    touchSlop = 8 * DENSITY,
    longPressMillis = 400,
    doubleTapMillis = 300
)
private val ALL = GestureOptions(drawInitial = true, notifications = true, lock = true, pinch = true)
private val NO_INITIAL = ALL.copy(drawInitial = false)

/** Lo que ha pasado en un gesto: todos los veredictos y el del final. */
private class Run(val verdicts: List<GestureVerdict>) {
    val last get() = verdicts.last()
    val fire get() = last.fire
    val commits get() = verdicts.count { it.haptic == Haptic.COMMIT }
    val releases get() = verdicts.count { it.haptic == Haptic.RELEASE }
}

private fun arbiter(options: GestureOptions = ALL) = HomeGestureArbiter(options, THRESHOLDS) { points ->
    recognizeLetter(points.map { StrokePoint(it.x, it.y) }, THRESHOLDS.letterBox) != null
}

/**
 * Dibuja un trazo de un dedo por [corners], con un evento cada pocos píxeles como da la pantalla, y
 * [millis] por tramo. Antes de cada evento deja pasar el tiempo ([HomeGestureArbiter.tick]).
 */
private fun HomeGestureArbiter.stroke(
    vararg corners: Pair<Float, Float>,
    area: StartArea = StartArea.DRAW,
    startAt: Long = 0L,
    millis: Long = 100L,
    lift: Boolean = true,
    holdFirst: Long = 0L
): Run {
    val verdicts = ArrayList<GestureVerdict>()
    var t = startAt
    verdicts += down(GesturePoint(corners[0].first, corners[0].second, t), area)
    var last = GesturePoint(corners[0].first, corners[0].second, t)
    // El dedo apoyado y quieto antes de empezar el trazo.
    if (holdFirst > 0) {
        t += holdFirst
        verdicts += tick(t)
    }
    for (i in 1 until corners.size) {
        val (ax, ay) = corners[i - 1]
        val (bx, by) = corners[i]
        val steps = max(1, (hypot(bx - ax, by - ay) / 12f).toInt())
        for (s in 1..steps) {
            t += millis / steps
            val f = s.toFloat() / steps
            last = GesturePoint(ax + (bx - ax) * f, ay + (by - ay) * f, t)
            verdicts += tick(t)
            verdicts += move(last)
        }
    }
    if (lift) verdicts += up(last.copy(t = t + 8))
    return Run(verdicts)
}

private fun HomeGestureArbiter.tap(x: Float, y: Float, at: Long, area: StartArea = StartArea.DRAW): Run {
    val point = GesturePoint(x, y, at)
    return Run(listOf(down(point, area), up(point.copy(t = at + 60))))
}

class HomeGestureArbiterTest {
    @Test
    fun `una L estrecha es letra, no notificaciones`() {
        val run = arbiter().stroke(500f to 1150f, 500f to 1700f, 650f to 1700f)
        assertEquals(Candidate.LETTER, run.fire)
        // Mientras baja recto asoma la persiana; al girar, gana la letra y el rastro se ve entero.
        assertTrue(run.verdicts.any { it.leader == Candidate.NOTIFICATIONS && it.progress > 0.5f })
        assertEquals(Candidate.LETTER, run.verdicts[run.verdicts.size - 2].leader)
        assertEquals(1f, run.verdicts[run.verdicts.size - 2].trail)
        assertEquals(0, run.commits)
    }

    @Test
    fun `una N y una P estrechas dibujadas desde abajo son letras, no la barra`() {
        val n = arbiter().stroke(500f to 1800f, 500f to 1350f, 620f to 1800f, 620f to 1350f)
        assertEquals(Candidate.LETTER, n.fire)
        val p = arbiter().stroke(
            500f to 1800f,
            500f to 1350f,
            620f to 1380f,
            640f to 1470f,
            600f to 1550f,
            500f to 1560f
        )
        assertEquals(Candidate.LETTER, p.fire)
    }

    @Test
    fun `un deslizar recto hacia arriba abre la barra al soltar, con una sola vibracion y sin rastro`() {
        val run = arbiter().stroke(540f to 1800f, 540f to 1400f)
        assertEquals(Candidate.SEARCH, run.fire)
        assertEquals(1, run.commits)
        assertTrue(run.verdicts.dropLast(1).all { it.trail == 0f })
        // La píldora sube con el dedo hasta el umbral.
        assertEquals(1f, run.verdicts[run.verdicts.size - 2].progress)
    }

    @Test
    fun `un arco de pulgar hacia arriba abre la barra, no vibra limite`() {
        val run = arbiter().stroke(950f to 1850f, 820f to 1650f, 740f to 1450f, 700f to 1250f)
        assertEquals(Candidate.SEARCH, run.fire)
        assertEquals(1, run.commits)
    }

    @Test
    fun `una diagonal recta no hace nada y no vibra`() {
        val run = arbiter().stroke(300f to 1200f, 500f to 1420f, 700f to 1650f)
        assertNull(run.fire)
        assertEquals(0, run.commits)
    }

    @Test
    fun `una C es letra`() {
        val run = arbiter().stroke(
            700f to 1250f,
            550f to 1200f,
            430f to 1300f,
            400f to 1450f,
            450f to 1600f,
            600f to 1650f,
            720f to 1600f
        )
        assertEquals(Candidate.LETTER, run.fire)
    }

    @Test
    fun `un deslizar recto con un gancho al final sigue siendo deslizar`() {
        val run = arbiter().stroke(540f to 1800f, 540f to 1300f, 500f to 1290f)
        assertEquals(Candidate.SEARCH, run.fire)
        assertEquals(1, run.commits)
        // Lo que se veía antes de soltar es lo que pasa: la píldora seguía arriba.
        assertEquals(Candidate.SEARCH, run.verdicts[run.verdicts.size - 2].leader)
    }

    @Test
    fun `una L de pie corto que no se lee no baja las notificaciones`() {
        val run = HomeGestureArbiter(ALL, THRESHOLDS) { false }.stroke(500f to 1150f, 500f to 1690f, 585f to 1690f)
        assertTrue(run.fire == Candidate.LETTER || run.fire == null)
        // Al soltar se veía la letra, no la persiana.
        assertEquals(Candidate.LETTER, run.verdicts[run.verdicts.size - 2].leader)
        assertEquals(0, run.commits)
    }

    @Test
    fun `un gancho de pie corto deja ver y ejecutar el mismo deslizar`() {
        val run = HomeGestureArbiter(ALL, THRESHOLDS) { false }.stroke(500f to 1150f, 500f to 1690f, 545f to 1690f)
        assertEquals(Candidate.NOTIFICATIONS, run.fire)
        assertEquals(Candidate.NOTIFICATIONS, run.verdicts[run.verdicts.size - 2].leader)
    }

    @Test
    fun `con la inicial, bajar y volver por el mismo camino no hace nada ni vibra`() {
        val run = arbiter().stroke(540f to 1200f, 540f to 1500f, 540f to 1230f)
        assertNull(run.fire)
        assertTrue(run.verdicts.none { it.leader == Candidate.LETTER })
        assertTrue(run.verdicts.dropLast(1).all { it.trail == 0f })
        assertEquals(0, run.commits)
        // Mientras vuelve, la persiana se recoge con el dedo.
        assertTrue(run.verdicts[run.verdicts.size - 2].progress < 0.3f)
    }

    @Test
    fun `con la inicial, volver solo un poco sigue bajando las notificaciones`() {
        val run = arbiter().stroke(540f to 1200f, 540f to 1500f, 540f to 1400f)
        assertEquals(Candidate.NOTIFICATIONS, run.fire)
        assertEquals(1, run.commits)
        assertTrue(run.verdicts.none { it.leader == Candidate.LETTER })
    }

    @Test
    fun `dudar quieto antes de la letra no la pierde`() {
        val run = arbiter().stroke(500f to 1150f, 500f to 1700f, 650f to 1700f, holdFirst = 450)
        assertEquals(Candidate.LETTER, run.fire)
        // Mantener vibró al comprometerse y al deshacerse; la letra no añade nada más.
        assertEquals(1, run.commits)
        assertEquals(1, run.releases)
    }

    @Test
    fun `mantener se insinua antes de comprometerse, sin vibrar`() {
        val held = arbiter()
        held.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        assertEquals(150L, held.nextTick(0))
        assertEquals(GestureVerdict.IDLE, held.tick(100))
        val hint = held.tick(275)
        assertEquals(Candidate.SETTINGS, hint.leader)
        assertTrue(hint.progress > 0f && hint.progress < 1f)
        assertEquals(Haptic.NONE, hint.haptic)
        assertTrue(!hint.committed)
        assertEquals(291L, held.nextTick(275))
        assertEquals(400L, held.nextTick(395))
        // Un temblor dentro de la holgura no apaga la insinuación.
        assertEquals(Candidate.SETTINGS, held.move(GesturePoint(543f, 1401f, 300)).leader)
    }

    @Test
    fun `sin inicial las notificaciones se comprometen a 48 dp y volver las deshace`() {
        val back = arbiter(NO_INITIAL).stroke(540f to 1200f, 540f to 1400f, 540f to 1250f, 540f to 1210f)
        assertNull(back.fire)
        assertEquals(1, back.commits)
        assertEquals(1, back.releases)
        val kept = arbiter(NO_INITIAL).stroke(540f to 1200f, 540f to 1400f)
        assertEquals(Candidate.NOTIFICATIONS, kept.fire)
        assertEquals(1, kept.commits)
        // El compromiso llega con el dedo abajo, antes de soltar.
        assertTrue(kept.verdicts.dropLast(1).any { it.committed && it.leader == Candidate.NOTIFICATIONS })
    }

    @Test
    fun `sin el gesto de notificaciones, deslizar hacia abajo no hace nada`() {
        val run = arbiter(NO_INITIAL.copy(notifications = false)).stroke(540f to 1200f, 540f to 1500f)
        assertNull(run.fire)
        assertEquals(0, run.commits)
    }

    @Test
    fun `un deslizar que empieza en la hora no espera a la letra`() {
        val run = arbiter().stroke(400f to 500f, 560f to 800f, area = StartArea.FREE)
        assertEquals(Candidate.NOTIFICATIONS, run.fire)
        assertTrue(run.verdicts.dropLast(1).any { it.committed })
    }

    @Test
    fun `un arrastre lento en horizontal no abre los ajustes`() {
        val run = arbiter().stroke(200f to 1400f, 900f to 1400f, millis = 900)
        assertNull(run.fire)
        assertTrue(run.verdicts.none { it.leader == Candidate.SETTINGS })
    }

    @Test
    fun `mantener quieto se compromete y abre al soltar, alejarse lo deshace`() {
        val held = arbiter()
        held.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        assertEquals(400L, held.holdDeadline)
        assertEquals(Haptic.NONE, held.tick(399).haptic)
        val commit = held.tick(450)
        assertEquals(Haptic.COMMIT, commit.haptic)
        assertEquals(Candidate.SETTINGS, commit.leader)
        assertEquals(Candidate.SETTINGS, held.up(GesturePoint(545f, 1402f, 600)).fire)

        val escaped = arbiter()
        escaped.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        escaped.tick(450)
        assertEquals(Haptic.RELEASE, escaped.move(GesturePoint(630f, 1400f, 500)).haptic)
        assertEquals(Haptic.NONE, escaped.move(GesturePoint(700f, 1400f, 550)).haptic)
        assertNull(escaped.up(GesturePoint(700f, 1400f, 600)).fire)
    }

    @Test
    fun `mantener sobre un hijo es cosa del hijo`() {
        val run = arbiter()
        run.down(GesturePoint(540f, 2600f, 0), StartArea.TAPPED)
        assertNull(run.holdDeadline)
        assertEquals(Candidate.SEARCH, arbiter().stroke(540f to 2650f, 540f to 2000f, area = StartArea.TAPPED).fire)
    }

    @Test
    fun `un toque y un deslizar lateral rapido no bloquean`() {
        val gestures = arbiter()
        assertNull(gestures.tap(540f, 1400f, at = 0).fire)
        // El segundo dedo cae cerca del primero y a tiempo, pero se mueve: es un deslizar.
        val swipe = gestures.stroke(560f to 1450f, 900f to 1450f, startAt = 200, millis = 80)
        assertTrue(swipe.verdicts.none { it.fire == Candidate.LOCK })
    }

    @Test
    fun `dos toques cerca y seguidos bloquean, lejos o tarde no`() {
        val near = arbiter()
        near.tap(540f, 1400f, at = 0)
        val second = near.tap(600f, 1460f, at = 250)
        assertEquals(Candidate.LOCK, second.fire)
        assertEquals(1, second.commits)

        val far = arbiter()
        far.tap(540f, 1400f, at = 0)
        assertNull(far.tap(900f, 1400f, at = 250).fire)

        val late = arbiter()
        late.tap(540f, 1400f, at = 0)
        assertNull(late.tap(540f, 1400f, at = 500).fire)
    }

    @Test
    fun `sin el gesto de bloquear, dos toques no hacen nada`() {
        val gestures = arbiter(ALL.copy(lock = false))
        gestures.tap(540f, 1400f, at = 0)
        assertNull(gestures.tap(540f, 1400f, at = 200).fire)
    }

    @Test
    fun `un segundo dedo tras comprometerse un deslizar se ignora`() {
        val gestures = arbiter(NO_INITIAL)
        gestures.stroke(540f to 1200f, 540f to 1400f, lift = false)
        val withTwo = gestures.move(GesturePoint(540f, 1410f, 300), spread = 500f)
        assertEquals(Candidate.NOTIFICATIONS, withTwo.leader)
        gestures.move(GesturePoint(540f, 1420f, 320), spread = 300f)
        val end = gestures.up(GesturePoint(540f, 1420f, 340))
        assertEquals(Candidate.NOTIFICATIONS, end.fire)
    }

    @Test
    fun `dos dedos antes de nada son solo pellizco, reversible hasta soltar`() {
        val back = arbiter()
        back.stroke(540f to 1400f, 560f to 1420f, lift = false)
        val pinch = back.move(GesturePoint(560f, 1420f, 200), spread = 600f)
        assertEquals(Candidate.ZONES, pinch.leader)
        assertEquals(0f, pinch.trail)
        assertEquals(Haptic.COMMIT, back.move(GesturePoint(560f, 1420f, 260), spread = 420f).haptic)
        assertEquals(Haptic.RELEASE, back.move(GesturePoint(560f, 1420f, 320), spread = 510f).haptic)
        assertNull(back.up(GesturePoint(560f, 1420f, 380)).fire)

        val kept = arbiter()
        kept.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        kept.move(GesturePoint(540f, 1400f, 20), spread = 600f)
        val half = kept.move(GesturePoint(540f, 1400f, 60), spread = 510f)
        assertTrue(half.progress > 0.4f && half.progress < 0.7f)
        kept.move(GesturePoint(540f, 1400f, 100), spread = 400f)
        // Levantar un dedo no deshace lo comprometido.
        kept.move(GesturePoint(540f, 1400f, 140))
        assertEquals(Candidate.ZONES, kept.up(GesturePoint(540f, 1400f, 160)).fire)
    }

    @Test
    fun `sin el pellizco, dos dedos no hacen nada`() {
        val gestures = arbiter(ALL.copy(pinch = false))
        gestures.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        gestures.move(GesturePoint(540f, 1400f, 20), spread = 600f)
        gestures.move(GesturePoint(540f, 1300f, 60), spread = 300f)
        assertNull(gestures.up(GesturePoint(540f, 1000f, 100)).fire)
    }

    @Test
    fun `un dedo que baja y luego un segundo dedo bajan solo las notificaciones`() {
        val gestures = arbiter(NO_INITIAL)
        gestures.stroke(540f to 1200f, 540f to 1400f, lift = false)
        gestures.move(GesturePoint(540f, 1400f, 300), spread = 600f)
        gestures.move(GesturePoint(540f, 1400f, 340), spread = 300f)
        assertEquals(Candidate.NOTIFICATIONS, gestures.up(GesturePoint(540f, 1400f, 360)).fire)
    }

    @Test
    fun `cancelar en cualquier momento no ejecuta nada`() {
        val swiping = arbiter(NO_INITIAL)
        swiping.stroke(540f to 1800f, 540f to 1300f, lift = false)
        assertEquals(GestureVerdict.IDLE, swiping.cancel())
        assertNull(swiping.up(GesturePoint(540f, 1300f, 500)).fire)

        val drawing = arbiter()
        drawing.stroke(540f to 1700f, 540f to 1500f, 600f to 1300f, 700f to 1250f, lift = false)
        drawing.cancel()
        assertNull(drawing.up(GesturePoint(700f, 1250f, 500)).fire)

        val holding = arbiter()
        holding.down(GesturePoint(540f, 1400f, 0), StartArea.DRAW)
        holding.tick(450)
        holding.cancel()
        assertNull(holding.up(GesturePoint(540f, 1400f, 500)).fire)
    }

    @Test
    fun `una letra no empieza en la franja del gesto atras`() {
        val run = arbiter().stroke(
            30f to 1250f,
            10f to 1300f,
            10f to 1450f,
            60f to 1600f,
            200f to 1650f,
            320f to 1600f,
            area = StartArea.LEFT_EDGE
        )
        // Sin letra posible, lo que baja es deslizar; nunca una letra.
        assertTrue(run.fire != Candidate.LETTER)
        assertTrue(run.verdicts.none { it.leader == Candidate.LETTER })
    }

    @Test
    fun `abajo y arriba manda el sistema`() {
        val run = arbiter().stroke(640f to 2850f, 640f to 2000f, area = StartArea.SYSTEM)
        assertNull(run.fire)
        assertTrue(run.verdicts.none { it.leader != null })
    }

    @Test
    fun `cada gesto que ejecuta algo vibra una sola vez como compromiso`() {
        val routes =
            listOf(
                arbiter().stroke(540f to 1800f, 540f to 1400f),
                arbiter().stroke(540f to 1200f, 540f to 1500f),
                arbiter(NO_INITIAL).stroke(540f to 1800f, 540f to 1400f),
                arbiter(NO_INITIAL).stroke(540f to 1200f, 540f to 1500f),
                arbiter().stroke(400f to 500f, 560f to 800f, area = StartArea.FREE),
                arbiter().stroke(950f to 1850f, 820f to 1650f, 740f to 1450f, 700f to 1250f)
            )
        routes.forEach {
            assertTrue(it.fire in setOf(Candidate.SEARCH, Candidate.NOTIFICATIONS))
            assertEquals(1, it.commits)
        }
    }
}

class GestureMetricsTest {
    private fun points(vararg corners: Pair<Float, Float>) = corners.mapIndexed { i, (x, y) ->
        GesturePoint(
            x,
            y,
            i * 16L
        )
    }

    @Test
    fun `una recta es recta y no gira`() {
        val metrics = GestureMetrics.of(points(0f to 0f, 0f to 100f, 0f to 200f), turnStep = 30f)
        assertEquals(1f, metrics.straightness, 1e-4f)
        assertEquals(0f, metrics.turn, 1e-3f)
        assertTrue(metrics.inCone)
    }

    @Test
    fun `una L gira 90 grados`() {
        val metrics = GestureMetrics.of(points(0f to 0f, 0f to 300f, 120f to 300f), turnStep = 30f)
        assertEquals(90f, metrics.turn, 1f)
        assertTrue(metrics.straightness < 0.85f)
        // Lo que sigue a la esquina se mide desde el vértice, no desde el ancla del tramo que gira.
        assertEquals(120f, metrics.afterCorner, 1f)
    }

    @Test
    fun `sin esquina no hay tramo tras la esquina`() {
        val arc = GestureMetrics.of(points(950f to 1850f, 820f to 1650f, 740f to 1450f, 700f to 1250f), 36f)
        assertEquals(0f, arc.afterCorner, 1e-3f)
    }

    @Test
    fun `el temblor de un pixel no cuenta como giro`() {
        val wobbly = (0..40).map { i -> GesturePoint(if (i % 2 == 0) 0f else 2f, i * 10f, i * 8L) }
        assertTrue(GestureMetrics.of(wobbly, turnStep = 36f).turn < 10f)
    }

    @Test
    fun `el cono deja pasar un arco de pulgar y no una diagonal`() {
        assertTrue(GestureMetrics.of(points(950f to 1850f, 700f to 1250f), 36f).inCone)
        assertTrue(!GestureMetrics.of(points(300f to 1200f, 700f to 1650f), 36f).inCone)
    }
}
