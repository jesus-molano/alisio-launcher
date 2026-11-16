package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.pause.PAUSE_SECONDS
import dev.jesusmolano.alisio.core.pause.pausedFirst
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.AppPickerSheet
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.VectorTileIcon
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val RemoveBadgeSize = 20.dp
private val RemoveBadgeOffset = 5.dp

/**
 * Pausa: qué apps se abren tras unos segundos de espera y cuánto dura. Las apps elegidas están a la
 * vista; tocar una le quita la pausa (se puede deshacer) y la última tesela añade otras.
 *
 * @param onPreviewPause enseña la pausa de esa app tal como saldrá, sin abrirla.
 */
@Composable
internal fun PausePage(context: SettingsContext, onPreviewPause: (AppEntry) -> Unit) {
    val prefs = context.prefs
    val settings = context.settings
    val resources = LocalResources.current
    val undo = LocalUndo.current
    var picking by remember { mutableStateOf(false) }
    // Una app por paquete: la pausa va por paquete, y la copia del perfil de trabajo repetiría tesela.
    val pausable = remember(context.apps) { context.apps.distinctBy { it.component.packageName } }
    val paused = pausable.filter { it.component.packageName in prefs.pausedApps }
    // El orden de la lista se fija al abrir su hoja: si cambiara con cada toque, la fila tocada
    // saltaría de sitio bajo el dedo.
    val candidates =
        remember(pausable, picking) { pausedFirst(pausable, prefs.pausedApps) { it.component.packageName } }

    Note(stringResource(R.string.settings_pause_hint))
    AppGrid(Modifier.padding(top = 8.dp)) {
        paused.forEach { app ->
            AppTile(
                label = app.label,
                description = stringResource(R.string.settings_pause_remove, app.label),
                interaction =
                    Modifier.clickable(role = Role.Button) {
                        val packageName = app.component.packageName
                        settings.togglePauseFor(packageName)
                        undo.show(resources.getString(R.string.undo_pause_removed, app.label)) {
                            settings.addPauseFor(packageName)
                        }
                    }
            ) {
                Box(Modifier.size(AlisioTokens.DockIconSize)) {
                    AppIcon(app.icon, AlisioTokens.DockIconSize)
                    RemoveBadge(Modifier.align(Alignment.TopEnd))
                }
            }
        }
        val add = stringResource(R.string.settings_pause_add)
        AppTile(
            label = add,
            description = stringResource(R.string.settings_pause_apps),
            interaction = Modifier.clickable(role = Role.Button) { picking = true }
        ) { VectorTileIcon(R.drawable.ic_add) }
    }

    if (paused.isNotEmpty()) {
        SectionLabel(stringResource(R.string.settings_pause_duration))
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PAUSE_SECONDS.forEach { seconds ->
                Chip(
                    label = stringResource(R.string.pause_seconds_short, seconds),
                    selected = prefs.pauseSeconds == seconds,
                    onClick = { settings.setPauseSeconds(seconds) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            // Con la duración elegida al lado: se toca y se ve lo que durará.
            RoundIconButton(
                R.drawable.ic_play,
                stringResource(R.string.settings_pause_preview),
                AlisioTokens.Text,
                onClick = { onPreviewPause(paused.first()) },
                container = AlisioTokens.Fill
            )
        }
    }

    if (picking) {
        // Se pueden marcar varias: la hoja no se cierra al tocar una.
        AppPickerSheet(
            title = stringResource(R.string.settings_pause_apps),
            items = candidates,
            key = { it.component.packageName },
            label = { it.label },
            icon = { it.icon },
            chosen = { it.component.packageName in prefs.pausedApps },
            onPick = { settings.togglePauseFor(it.component.packageName) },
            onDismiss = { picking = false },
            multiple = true
        )
    }
}

/**
 * La marca de «quitar» en la esquina de una app con pausa: sin ella, un icono con su nombre parece
 * que abre la app. Decorativa; lo que hace el toque lo dice la descripción de la tesela.
 */
@Composable
private fun RemoveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = RemoveBadgeOffset, y = -RemoveBadgeOffset)
            .size(RemoveBadgeSize)
            .background(AlisioTokens.Surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            tint = AlisioTokens.Text,
            modifier = Modifier.size(12.dp)
        )
    }
}
