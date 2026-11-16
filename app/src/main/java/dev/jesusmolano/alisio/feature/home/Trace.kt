package dev.jesusmolano.alisio.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.trace.RestRail
import dev.jesusmolano.alisio.core.trace.TraceBand
import dev.jesusmolano.alisio.core.trace.TraceGrid
import dev.jesusmolano.alisio.core.trace.TraceRail
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppSection
import dev.jesusmolano.alisio.data.apps.OTHER_SECTION
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.IconLabel
import dev.jesusmolano.alisio.ui.components.selectedTile
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Ancho de la franja del índice, pegada al borde derecho: un toque mínimo de 48 dp. */
val TraceRailWidth = AlisioTokens.MinTouch

// Índice en reposo: pegado al pie y, mientras quepa, por debajo de la línea de alcance del pulgar
// (fracción del alto de la pantalla). Con más letras se aprietan hasta RestMinPitch; por debajo de
// LabelMinPitch se escribe una de cada pocas y las demás son un punto.
private const val REACH_FRACTION = 0.34f
private val RestMaxPitch = 20.dp
internal val RestMinPitch = 9.dp
private val LabelMinPitch = 14.dp

// Franja que se abre desde el dedo: hasta esta separación, sin tocar la barra de estado ni la zona
// del gesto de inicio del sistema.
private val BandMaxPitch = 30.dp
private val BandTopGap = 16.dp
private val BandBottomGap = 40.dp

// Encima y debajo del índice, una franja que no hace nada: ni empieza un trazo ni deja pasar el
// gesto atrás del sistema. Separa los dos gestos para que un toque en el límite no haga ninguno.
private val DeadZone = 20.dp

// El dedo tiene que salir del índice esto antes de que la letra quede fijada y se elija app.
private val LockDistance = 16.dp

// Una app solo se abre si lleva este tiempo elegida al soltar: pasar por encima y levantar el dedo
// de un tirón no abre la que quedó debajo.
private const val ARM_MILLIS = 70L

private val CellWidth = 80.dp
private val CellHeight = 100.dp
private val CellIconTop = 12.dp
private val HeaderHeight = 112.dp
private val GridGap = 8.dp
private val GridMargin = 12.dp

// Lupa: la letra bajo el dedo crece hasta 1 + LENS_SCALE y sale LensShift hacia la izquierda para
// que el pulgar no la tape; sus vecinas, menos, hasta LENS_REACH letras de distancia.
private const val LENS_SCALE = 0.9f
private const val LENS_REACH = 2.5f
private val LensShift = 28.dp
private val LetterBox = 24.dp

// Entre el índice y la rejilla: justo lo que asoma la letra de la lupa fuera del índice, que sale
// hacia las apps, y un poco de aire. Con menos, la letra en acento tocaba el anillo de la app elegida.
private val RailGridGap = LensShift + LetterBox * (1 + LENS_SCALE) / 2 - TraceRailWidth / 2 + 4.dp
private val DotSize = 3.dp

/** Pasa dp a píxeles con [density] fuera de un ámbito que la tenga. */
private class PxOf(private val density: Density) {
    fun of(dp: Dp): Float = with(density) { dp.toPx() }
}

/** Un trazo en curso: la franja abierta, la letra, el dedo, su rejilla y la app bajo el dedo. */
private data class TraceState(
    val band: TraceBand,
    val letter: Int,
    val fingerY: Float,
    val grid: TraceGrid,
    val locked: Boolean,
    val selected: Int?
)

/**
 * Trazo: índice de letras en el borde derecho. Tocar una letra, deslizar a la izquierda sobre sus
 * apps y soltar encima de una la abre, todo en un gesto. Al tocar, las letras se abren desde el
 * dedo y ocupan toda la altura libre (el Inicio se oculta), así que hasta las del extremo quedan
 * separadas y cerca. Soltar sobre el índice, sobre un hueco o lejos de la rejilla cancela; volver
 * al borde suelta la app elegida y deja elegir otra letra.
 *
 * Ocupa todo el [BoxScope] para poder tapar lo que hay debajo mientras dura el trazo.
 *
 * @param mode con [TraceRail.EDGE] las letras solo se ven durante el trazo; con [TraceRail.NEVER] no hay índice.
 * @param railTop dónde puede empezar el índice en reposo, medido desde arriba.
 * @param railBottom dónde acaba el índice en reposo, medido desde abajo (el pie del Inicio).
 * @param railTopLimit hasta dónde puede subir si sus letras no caben entre [railTop] y el pie.
 * @param onOpenZones en el Inicio, el camino de TalkBack a todas las apps; `null` dentro de Zonas.
 * @param pressedFingers cuántos dedos hay en toda la pantalla de quien lo contiene; Trazo solo ve los
 *   que caen sobre él, y un segundo dedo fuera del índice es un pellizco, no un trazo.
 * @param onTracing avisa cuando empieza y cuando acaba un trazo.
 */
@Composable
fun BoxScope.Trace(
    sections: List<AppSection<AppEntry>>,
    mode: TraceRail,
    railTop: Dp,
    railBottom: Dp,
    onLaunch: (AppEntry) -> Unit,
    railTopLimit: Dp = railTop,
    onOpenZones: (() -> Unit)? = null,
    pressedFingers: () -> Int = { 0 },
    onTracing: (Boolean) -> Unit = {}
) {
    if (sections.isEmpty() || mode == TraceRail.NEVER) return
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val launch by rememberUpdatedState(onLaunch)
    // Si la lista de apps cambia a mitad de un trazo, el trazo se descarta: sus índices ya no valen.
    var trace by remember(sections) { mutableStateOf<TraceState?>(null) }
    // El último trazo sigue pintado mientras se desvanece, aunque ya haya acabado.
    var lastTrace by remember(sections) { mutableStateOf<TraceState?>(null) }
    val tracing = trace != null
    // Quien pinta debajo retira su contenido mientras dura el trazo: el velo no lo tapa del todo.
    LaunchedEffect(tracing) { onTracing(tracing) }
    // 0 en reposo, 1 con la franja abierta: mueve las letras entre las dos posiciones.
    val spread = remember { Animatable(0f) }
    LaunchedEffect(tracing) {
        spread.animateTo(if (tracing) 1f else 0f, if (tracing) AlisioMotion.standard() else AlisioMotion.exit())
    }

    BoxWithConstraints(Modifier.matchParentSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()
        val px = PxOf(density)
        val statusTop = insets.getTop(density).toFloat()
        val navBottom = insets.getBottom(density).toFloat()
        val railLeft = width - px.of(TraceRailWidth)
        val rest =
            remember(sections.size, height, railTop, railBottom, railTopLimit) {
                RestRail.place(
                    count = sections.size,
                    softTop = px.of(railTop),
                    hardTop = px.of(railTopLimit),
                    bottom = height - px.of(railBottom),
                    reachTop = height * REACH_FRACTION,
                    maxPitch = px.of(RestMaxPitch),
                    minPitch = px.of(RestMinPitch)
                )
            }
        val labelStep = rest.labelStep(px.of(LabelMinPitch))

        AnimatedVisibility(
            visible = tracing,
            enter = fadeIn(AlisioMotion.fast()),
            exit = fadeOut(AlisioMotion.exit())
        ) {
            lastTrace?.let { current ->
                sections.getOrNull(current.letter)?.let { TraceOverlay(current, it, railLeft) }
            }
        }

        RailLetters(sections, mode, rest, labelStep, lastTrace, trace?.letter, { spread.value }, railLeft)

        val dead = px.of(DeadZone)
        val zoneTop = rest.top - dead
        val railDescription = stringResource(R.string.trace_rail_description)
        val openZones = stringResource(R.string.trace_open_zones)
        Box(
            Modifier
                .offset { IntOffset(railLeft.roundToInt(), zoneTop.roundToInt()) }
                .size(TraceRailWidth, with(density) { (rest.bottom - rest.top + 2 * dead).toDp() })
                // El índice pisa la franja del gesto atrás del borde derecho: sin esto, One UI se queda
                // el trazo. Solo el índice y su zona muerta: por encima y por debajo, atrás sigue
                // funcionando (en Zonas cierra la pantalla).
                .systemGestureExclusion()
                // TalkBack se queda los gestos y el trazo no se puede hacer con él: el camino
                // equivalente es Zonas, que tiene todas las apps y su propio índice.
                .semantics {
                    contentDescription = railDescription
                    if (onOpenZones != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(openZones) {
                                    onOpenZones()
                                    true
                                }
                            )
                    }
                }
                .pointerInput(sections, rest, height, width, statusTop, navBottom) {
                    val bandMinTop = statusTop + BandTopGap.toPx()
                    // Nunca por encima del índice en reposo: sus últimas letras se apretarían más que en él.
                    val bandMaxBottom = max(height - navBottom - BandBottomGap.toPx(), rest.bottom)
                    val gridRight = railLeft - RailGridGap.toPx()
                    val gridMinTop = statusTop + HeaderHeight.toPx()
                    val gridMaxBottom = height - navBottom - GridGap.toPx() * 2
                    val cellWidth = CellWidth.toPx()
                    val cellHeight = CellHeight.toPx()
                    val lock = LockDistance.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Un dedo que llega consumido (el Inicio ya lo cuenta como pellizco) o que
                        // cae con otro ya apoyado fuera del índice no empieza un trazo.
                        if (down.isConsumed || pressedFingers() > 1) return@awaitEachGesture
                        down.consume()
                        val startY = down.position.y + zoneTop
                        if (startY < rest.top || startY > rest.bottom) {
                            // Zona muerta: se queda el gesto entero para que tampoco lo tome el Inicio.
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        val anchor = rest.letterAt(startY)
                        val band =
                            TraceBand(sections.size, anchor, startY, bandMinTop, bandMaxBottom, BandMaxPitch.toPx())

                        fun gridFor(letter: Int) = TraceGrid(
                            count = sections[letter].items.size,
                            maxColumns = ((gridRight - AlisioTokens.Edge.toPx()) / cellWidth).toInt(),
                            maxRows = ((gridMaxBottom - gridMinTop) / cellHeight).toInt(),
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            right = gridRight,
                            centerY = band.centerOf(letter),
                            minTop = gridMinTop,
                            maxBottom = gridMaxBottom,
                            margin = GridMargin.toPx()
                        )

                        fun publish(state: TraceState) {
                            trace = state
                            lastTrace = state
                        }

                        var current = TraceState(band, anchor, startY, gridFor(anchor), locked = false, selected = null)
                        var selectedAt = 0L
                        var open = false
                        publish(current)
                        // La primera vibración llega cuando el dedo se mueve o cambia de letra, no al
                        // apoyarlo: un toque suelto en el borde no ha decidido aún nada.
                        var felt = false
                        val slop = viewConfiguration.touchSlop
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Un segundo dedo (pellizco), aunque caiga fuera del índice, o alguien
                                // que se queda el gesto: el trazo se cancela sin abrir nada.
                                val fingers = maxOf(event.changes.count { it.pressed }, pressedFingers())
                                if (fingers > 1 || change.isConsumed) break
                                if (!change.pressed) {
                                    open = current.locked && current.selected != null &&
                                        change.uptimeMillis - selectedAt >= ARM_MILLIS
                                    break
                                }
                                change.consume()
                                val x = change.position.x + railLeft
                                val y = change.position.y + zoneTop
                                val next =
                                    when {
                                        x >= railLeft -> {
                                            // En el borde se elige letra. Si venía de la rejilla, la
                                            // app elegida se suelta: soltar aquí cancela.
                                            val letter = band.letterAt(y, current.letter)
                                            if (letter != current.letter && felt) AlisioHaptics.step(view)
                                            current.copy(
                                                letter = letter,
                                                fingerY = y,
                                                grid = if (letter != current.letter) gridFor(letter) else current.grid,
                                                locked = false,
                                                selected = null
                                            )
                                        }

                                        // Entre el índice y la rejilla la letra no cambia: el dedo que
                                        // sale hacia las apps suele torcerse un poco.
                                        !current.locked && x > railLeft - lock -> current.copy(fingerY = y)

                                        else -> {
                                            val selected = current.grid.indexAt(x, y, current.selected)
                                            if (selected != current.selected) {
                                                selectedAt = change.uptimeMillis
                                                if (selected != null && felt) AlisioHaptics.step(view)
                                            }
                                            current.copy(fingerY = y, locked = true, selected = selected)
                                        }
                                    }
                                val moved = (change.position - down.position).getDistance() > slop
                                if (!felt && (moved || next.letter != current.letter)) {
                                    felt = true
                                    AlisioHaptics.step(view)
                                }
                                current = next
                                publish(next)
                            }
                        } finally {
                            // También si el sistema cancela el gesto a medias.
                            trace = null
                        }
                        val selected = current.selected
                        if (open && selected != null) {
                            AlisioHaptics.done(view)
                            sections.getOrNull(current.letter)?.items?.getOrNull(selected)?.let(launch)
                        }
                    }
                }
        )
    }
}

/**
 * Las letras del índice. En reposo están en [rest] (con [TraceRail.EDGE], invisibles); durante un
 * trazo pasan a la franja abierta y la letra bajo el dedo crece y se aparta del pulgar. Las
 * posiciones se leen en la fase de dibujo: seguir al dedo no recompone nada.
 */
@Composable
private fun RailLetters(
    sections: List<AppSection<AppEntry>>,
    mode: TraceRail,
    rest: RestRail,
    labelStep: Int,
    trace: TraceState?,
    activeLetter: Int?,
    spread: () -> Float,
    railLeft: Float
) {
    val density = LocalDensity.current
    val box = with(density) { LetterBox.toPx() }
    val dot = with(density) { DotSize.toPx() }
    val shift = with(density) { LensShift.toPx() }
    val center = railLeft + with(density) { TraceRailWidth.toPx() } / 2
    val style = AlisioTokens.RailLetter.onWallpaper()
    val always = mode == TraceRail.ALWAYS
    // La primera letra de verdad: con «#» delante, la «A» (o la que haya) se escribe siempre.
    val first = sections.indexOfFirst { it.letter != OTHER_SECTION }.coerceAtLeast(0)
    val labels = remember(rest, labelStep, first) { rest.labels(labelStep, first) }
    sections.forEachIndexed { index, section ->
        val labeled = index in labels

        fun y(): Float {
            val band = trace?.band ?: return rest.centerOf(index)
            return lerp(rest.centerOf(index), band.centerOf(index), spread())
        }

        // Cuánto le toca de lupa: nada en reposo, todo bajo el dedo.
        fun lens(): Float {
            val current = trace ?: return 0f
            val focus = if (current.locked) current.letter.toFloat() else current.band.positionAt(current.fingerY)
            return max(0f, 1f - abs(index - focus) / LENS_REACH) * spread()
        }
        if (always && !labeled) {
            Box(
                Modifier
                    .offset { IntOffset((center - dot / 2).roundToInt(), (y() - dot / 2).roundToInt()) }
                    .size(DotSize)
                    .graphicsLayer { alpha = 1f - spread() }
                    .background(AlisioTokens.Text, CircleShape)
            )
        }
        val active = activeLetter == index
        Box(
            Modifier
                .offset { IntOffset((railLeft + 0f).roundToInt(), (y() - box / 2).roundToInt()) }
                .size(TraceRailWidth, LetterBox)
                .graphicsLayer {
                    val lens = lens()
                    translationX = -shift * lens
                    scaleX = 1f + LENS_SCALE * lens
                    scaleY = scaleX
                    alpha = if (always && labeled) 1f else spread()
                },
            contentAlignment = Alignment.Center
        ) {
            // La letra bajo el dedo es una señal viva: va en acento.
            Text(text = section.letter, style = if (active) style.copy(color = AlisioTokens.Accent) else style)
        }
    }
}

/**
 * El borde derecho del Inicio con cada [mode], para elegirlo mirando: con [TraceRail.ALWAYS], las
 * letras en reposo; con [TraceRail.EDGE], como salen al tocar (abiertas desde el dedo, con la lupa);
 * con [TraceRail.NEVER], nada. Dibuja con las mismas letras y estilos que el índice de verdad.
 *
 * @param letters las primeras letras del índice real.
 */
@Composable
internal fun TraceRailSample(letters: List<String>, mode: TraceRail, modifier: Modifier = Modifier) {
    val style = AlisioTokens.RailLetter.onWallpaper()
    val middle = letters.size / 2
    Column(
        modifier.size(TraceRailWidth, LetterBox * letters.size),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { index, letter ->
            val lens = if (mode == TraceRail.EDGE) max(0f, 1f - abs(index - middle) / LENS_REACH) else 0f
            Box(
                Modifier
                    .size(TraceRailWidth, LetterBox)
                    .graphicsLayer {
                        translationX = -LensShift.toPx() * lens
                        scaleX = 1f + LENS_SCALE * lens
                        scaleY = scaleX
                        alpha =
                            when (mode) {
                                TraceRail.ALWAYS -> 1f

                                // Las letras salen alrededor del dedo; las lejanas apenas se ven aún.
                                TraceRail.EDGE -> max(0.3f, 1f - abs(index - middle) / letters.size.toFloat() * 2)

                                TraceRail.NEVER -> 0f
                            }
                    },
                contentAlignment = Alignment.Center
            ) {
                val active = mode == TraceRail.EDGE && index == middle
                Text(text = letter, style = if (active) style.copy(color = AlisioTokens.Accent) else style)
            }
        }
    }
}

/** Lo que se ve durante un trazo: el Inicio tapado, la letra en grande y la rejilla de sus apps. */
@Composable
private fun TraceOverlay(trace: TraceState, section: AppSection<AppEntry>, railLeft: Float) {
    val grid = trace.grid
    val density = LocalDensity.current
    val selectedApp = trace.selected?.let(section.items::getOrNull)
    // La rejilla acompaña a la letra mientras el dedo está en el índice; fijada, ya no se mueve.
    val top by animateFloatAsState(grid.top, AlisioMotion.follow(), label = "trace-grid")
    val headerEnd = with(density) { (railLeft - grid.right).toDp() } + TraceRailWidth
    Box(Modifier.fillMaxSize().background(AlisioTokens.Scrim)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (top - HeaderHeight.toPx()).roundToInt()) }
                    .height(HeaderHeight)
                    .padding(start = AlisioTokens.Edge, end = headerEnd, bottom = GridGap),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(text = section.letter, style = AlisioTokens.Numeral, color = AlisioTokens.Accent)
            // Solo la pista mientras no hay app elegida: su nombre ya va bajo su icono, marcado. El
            // renglón sigue ocupando su sitio para que la letra no baje al elegir.
            Text(
                text = stringResource(if (trace.locked) R.string.trace_hint_cancel else R.string.trace_hint_slide),
                style = MaterialTheme.typography.titleMedium,
                color = AlisioTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (selectedApp != null) 0f else 1f)
            )
        }
        // Cada letra trae su rejilla nueva, que entra subiendo un poco; la de antes desaparece sin más.
        key(section.letter) { GridCells(trace, section, top) }
    }
}

@Composable
private fun GridCells(trace: TraceState, section: AppSection<AppEntry>, top: Float) {
    val grid = trace.grid
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, AlisioMotion.fast()) }
    val rise = with(density) { AlisioMotion.Rise.toPx() }
    val cellWidth = with(density) { grid.cellWidth.toDp() }
    val cellHeight = with(density) { grid.cellHeight.toDp() }
    section.items.take(grid.shown).forEachIndexed { index, app ->
        val selected = trace.selected == index
        Column(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            grid.cellLeft(index).roundToInt(),
                            (grid.cellTop(index) - grid.top + top).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        alpha = appear.value
                        translationY = (1f - appear.value) * rise
                    }
                    .size(cellWidth, cellHeight)
                    .padding(2.dp)
                    .selectedTile(selected, AlisioTokens.RowShape)
                    .padding(top = CellIconTop - 2.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app.icon, AlisioTokens.DockIconSize)
            Spacer(Modifier.height(6.dp))
            IconLabel(app.label, color = if (selected) AlisioTokens.Text else AlisioTokens.TextSecondary)
        }
    }
}
