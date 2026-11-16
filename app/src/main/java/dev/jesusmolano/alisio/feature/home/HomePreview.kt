package dev.jesusmolano.alisio.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.searchHint
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
 */
@Composable
fun HomePreview(
    prefs: Prefs,
    dock: List<DockSlot>,
    swipeOwners: Set<String>,
    sampleApp: AppEntry?,
    notificationsGranted: Boolean,
    modifier: Modifier = Modifier
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
    Box(
        modifier
            .wallpaperVeil(prefs.wallpaperDim.factor, rememberLightWallpaper(), overHome = true)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 20.dp)
                .width(width)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalDensity provides miniature) {
                val clock = AlisioTokens.Clock
                val clockSize = clock.fontSize * PREVIEW_CLOCK_RATIO
                Text(
                    text = formatClock(rememberNow()),
                    style = clock.copy(fontSize = clockSize, lineHeight = clockSize).onWallpaper(),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(horizontal = AlisioTokens.Edge)
                )
                Column(Modifier.align(Alignment.BottomCenter)) {
                    if (prefs.nowEnabled && sampleApp != null) {
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
                    DockRow {
                        dock.forEachIndexed { position, slot ->
                            DockIcon(
                                label = slot.label(),
                                showLabel = prefs.dockLabels && slot != DockSlot.Empty,
                                unread = position == dotted,
                                swipeMark = prefs.dockGestures && slot.hasTarget(swipeOwners),
                                onWallpaper = true,
                                modifier = Modifier.width(DockItemWidth).padding(vertical = 4.dp)
                            ) { DockSlotIcon(slot, AlisioTokens.DockIconSize) }
                        }
                    }
                    Spacer(Modifier.height(AlisioTokens.PlateGap))
                    SearchPill(searchHint(assistant), onClick = {}, Modifier.padding(horizontal = AlisioTokens.Outset))
                }
            }
        }
        // Encima de todo y sin hacer nada: los toques no llegan a las piezas, que aquí son un dibujo.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
    }
}

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
