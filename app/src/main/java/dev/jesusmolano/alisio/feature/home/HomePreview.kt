package dev.jesusmolano.alisio.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.dock.DOCK_ITEM_SPARE_DP
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.db.HomeNote
import dev.jesusmolano.alisio.data.glance.GlanceData
import dev.jesusmolano.alisio.data.media.LiveNow
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.searchHint
import dev.jesusmolano.alisio.ui.components.ClockText
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberLightWallpaper
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.wallpaperVeil
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper

// Cuánto se reduce el Inicio: con esto caben a la vez la hora, Ahora, el reproductor, el dock y la
// píldora en el alto de la ventana de los ajustes.
private const val PREVIEW_SCALE = 0.66f

// La hora de la miniatura, como fracción de AlisioTokens.Clock en sus unidades ya reducidas: con
// 112 sp sale a 64, unos 42 sp reales (la guía pedía 48, que ya pisaba la sugerencia). A su tamaño
// del Inicio se comería media ventana.
private const val PREVIEW_CLOCK_RATIO = 64f / 112f

// El aire de la miniatura bajo la barra de estado, sobre su borde de abajo y entre sus dos columnas.
private val PreviewTopGap = 8.dp
private val PreviewBottomGap = 20.dp
private val PreviewMiddleGap = 16.dp

// Lo que le queda al temporizador de muestra de «En curso».
private const val SAMPLE_LIVE_MILLIS = 12 * 60_000L

/**
 * El Inicio en miniatura, para los ajustes: las mismas piezas del Inicio ([NowCard], [PlayerBar],
 * [DockRow], [SearchPill]) dibujadas a escala sobre el fondo de pantalla real, con las opciones de
 * [prefs]. Cambia en cuanto cambia una opción. No se puede tocar y TalkBack solo lee su nombre.
 *
 * Va sin fondo propio en lo alto de la pantalla: debajo se ve el Inicio real, que con los ajustes
 * abiertos solo conserva el fondo y su velo.
 *
 * @param swipeOwners claves de las apps del dock con destino al deslizar: llevan su marca.
 * @param sampleApp la app que hace de sugerencia de Ahora; sin ninguna no hay sugerencia que enseñar.
 * @param notificationsGranted sin el acceso a notificaciones el Inicio real no tiene reproductor ni
 *   puntos, y la miniatura tampoco los enseña aunque estén encendidos.
 * @param onContentHeight recibe el alto que necesita la miniatura para que nada se pise, medido;
 *   quien la pone le da ese alto (más la barra de estado).
 * @param musicLabel el nombre de la app de música por defecto, para la pieza «Música».
 */
@Composable
fun HomePreview(
    prefs: Prefs,
    dock: List<DockSlot>,
    swipeOwners: Set<String>,
    sampleApp: AppEntry?,
    notificationsGranted: Boolean,
    glance: GlanceData,
    modifier: Modifier = Modifier,
    notes: List<HomeNote> = emptyList(),
    onContentHeight: ((Dp) -> Unit)? = null,
    musicLabel: String? = null
) {
    val context = LocalContext.current
    val description = stringResource(R.string.settings_preview)
    val density = LocalDensity.current
    // Todo lo de dentro mide con esta densidad: cada pieza sale reducida sin tocar su código.
    val miniature = remember(density) { Density(density.density * PREVIEW_SCALE, density.fontScale) }
    // El mismo ancho que la pantalla, reducido: las piezas guardan entre sí las proporciones del Inicio.
    val width = with(density) { (LocalWindowInfo.current.containerSize.width * PREVIEW_SCALE).toDp() }
    val assistant = remember(prefs.assistantPackage) { context.appLabel(prefs.assistantPackage) }
    // Un solo punto de muestra, en la primera app que lo tenga encendido: enseña cómo es, no cuántos hay.
    val dotted =
        remember(dock, prefs.unreadDots, prefs.unreadMuted, notificationsGranted) {
            if (!prefs.unreadDots || !notificationsGranted) {
                -1
            } else {
                dock.indexOfFirst { slot -> slot.packages().any { it !in prefs.unreadMuted } }
            }
        }
    // Lo que miden de verdad la columna de arriba y la de abajo, en píxeles de pantalla.
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    if (onContentHeight != null && topPx > 0 && bottomPx > 0) {
        val content = with(density) { (topPx + bottomPx).toDp() } + PreviewTopGap + PreviewBottomGap + PreviewMiddleGap
        LaunchedEffect(content) { onContentHeight(content) }
    }
    Box(
        modifier
            .wallpaperVeil(prefs.wallpaperDim.factor, rememberLightWallpaper(), overHome = true)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = PreviewTopGap, bottom = PreviewBottomGap)
                .width(width)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalDensity provides miniature) {
                val clock = AlisioTokens.Clock
                val clockSize = clock.fontSize * PREVIEW_CLOCK_RATIO * AlisioTokens.clockScale(prefs.clockSize)
                val now = rememberNow()
                // Cada columna se mide entera aunque no quepa (`unbounded`): la ventana crece con ella y
                // nunca se pisan, sea cual sea el tamaño de la hora, de la letra o de cada pieza.
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { topPx = it.height }
                        .padding(horizontal = AlisioTokens.Edge)
                ) {
                    ClockText(
                        text = formatClock(now, prefs.clockFormat),
                        style = clock.copy(fontSize = clockSize, lineHeight = clockSize).onWallpaper()
                    )
                    // La línea bajo la hora, con muestras para lo encendido que hoy no tiene nada que
                    // decir: se ve dónde y cómo queda cada pieza al elegirla.
                    if (prefs.glance.isNotEmpty()) {
                        val pieces =
                            glancePieces(
                                prefs,
                                glance,
                                now.toInstant().toEpochMilli(),
                                sample = true,
                                music = musicLabel?.let { MusicGlance(it) {} }
                            )
                        val date = formatHomeDate(now)
                        val style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ).onWallpaper()
                        Text(
                            text = date,
                            style = style,
                            modifier = Modifier.heightIn(min = AlisioTokens.MinTouch).wrapContentHeight()
                        )
                        GlanceLine(pieces, interactive = false)
                    }
                    // Las notas, o una de muestra si aún no hay ninguna: se ve dónde salen al encenderlas.
                    if (prefs.homeNotes) {
                        val sample = stringResource(R.string.settings_preview_note)
                        val shown = notes.ifEmpty { listOf(HomeNote(id = -1, text = sample, createdAt = 0)) }
                        NotesLine(shown, onStrike = {}, onOpenLink = {}, interactive = false)
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                        .onSizeChanged { bottomPx = it.height }
                ) {
                    // Con «En curso» encendido, el Inicio pone en el sitio de Ahora lo que está pasando
                    // (HomeScreen hace la misma cuenta): aquí, un temporizador de muestra.
                    if (prefs.nowEnabled && prefs.liveNow && notificationsGranted) {
                        LiveCard(
                            live = sampleLive(now.toInstant().toEpochMilli()),
                            appLabel = stringResource(R.string.settings_preview_live_app),
                            icon = null,
                            onOpen = {},
                            modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                        )
                        Spacer(Modifier.height(AlisioTokens.PlateGap))
                    } else if (prefs.nowEnabled && sampleApp != null) {
                        NowCard(
                            suggestion = NowSuggestion(NowRule.HABIT, sampleApp.key.toString()),
                            app = sampleApp,
                            onOpen = {},
                            onDismiss = {},
                            modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                        )
                        Spacer(Modifier.height(AlisioTokens.PlateGap))
                    }
                    if (prefs.playerEnabled && notificationsGranted) {
                        PlayerBar(
                            player = samplePlayer(),
                            appIcon = null,
                            onOpen = {},
                            onToggle = {},
                            onNext = {},
                            onHide = {},
                            modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                        )
                        Spacer(Modifier.height(AlisioTokens.PlateGap))
                    }
                    val iconSize = rememberDockIconSize(dock.size)
                    if (dock.isNotEmpty()) {
                        DockRow {
                            dock.forEachIndexed { position, slot ->
                                DockIcon(
                                    label = slot.label(),
                                    showLabel = prefs.dockLabels && slot != DockSlot.Empty,
                                    size = iconSize,
                                    unread = position == dotted,
                                    swipeMark = prefs.dockGestures && slot.hasTarget(swipeOwners),
                                    onWallpaper = true,
                                    modifier = Modifier.width(iconSize + DOCK_ITEM_SPARE_DP.dp).padding(vertical = 4.dp)
                                ) { DockSlotIcon(slot, iconSize) }
                            }
                        }
                        Spacer(Modifier.height(AlisioTokens.PlateGap))
                    }
                    SearchPill(searchHint(assistant), onClick = {}, Modifier.padding(horizontal = AlisioTokens.Outset))
                }
            }
        }
        // Encima de todo y sin hacer nada: los toques no llegan a las piezas, que aquí son un dibujo.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
    }
}

/** Algo en curso de ejemplo: un temporizador al que le quedan unos minutos. */
@Composable
private fun sampleLive(nowMillis: Long) = LiveNow(
    key = "",
    packageName = "",
    title = stringResource(R.string.settings_preview_live_title),
    text = null,
    countdownTo = nowMillis + SAMPLE_LIVE_MILLIS,
    open = null
)

/** Un tema de ejemplo: el reproductor real solo existe mientras suena algo. */
@Composable
private fun samplePlayer() = NowPlaying(
    packageName = "",
    appLabel = stringResource(R.string.settings_preview_artist),
    title = stringResource(R.string.settings_preview_track),
    artist = null,
    art = null,
    playing = true,
    canSkip = true
)

private fun DockSlot.label(): String = when (this) {
    DockSlot.Empty -> ""
    is DockSlot.App -> app.label
    is DockSlot.Stack -> name
}

/** Solo un icono suelto tiene destino al deslizar; una pila y un hueco vacío, nunca. */
private fun DockSlot.hasTarget(owners: Set<String>): Boolean = this is DockSlot.App && app.key.toString() in owners

private fun DockSlot.packages(): List<String> = when (this) {
    DockSlot.Empty -> emptyList()
    is DockSlot.App -> listOf(app.component.packageName)
    is DockSlot.Stack -> apps.map { it.component.packageName }
}
