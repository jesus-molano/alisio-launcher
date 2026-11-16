package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.feature.appmenu.SwipeTargetSheet
import dev.jesusmolano.alisio.feature.home.DockSlot
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// El destino de un icono va en su esquina, pequeño: es a dónde lleva, no lo que es.
private val TargetBadgeSize = 24.dp
private val TargetBadgeOffset = 6.dp

/**
 * Inicio: lo que hay en la pantalla de inicio además del reloj y el dock, y los gestos sobre ella.
 * Lo que tiene resultado visible (sugerencia, reproductor, puntos, marcas de deslizar) se ve
 * aparecer y desaparecer en la miniatura de arriba; los gestos no tienen nada que enseñar.
 *
 * @param home de dónde salen las sugerencias silenciadas y los destinos al deslizar, y quien los cambia.
 */
@Composable
internal fun HomePage(context: SettingsContext, home: HomeViewModel) {
    val prefs = context.prefs
    val settings = context.settings
    val access = context.access
    val silenced by home.silencedSuggestions.collectAsStateWithLifecycle()

    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_now)),
        leadingIcon = R.drawable.ic_auto_awesome,
        checked = prefs.nowEnabled,
        onClick = { settings.toggleNow() }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_bluetooth)),
        leadingIcon = R.drawable.ic_bluetooth,
        // Con permiso, la fila enseña lo que la regla ve ahora mismo en vez de explicarla.
        subtitle =
            when {
                !access.bluetooth -> stringResource(R.string.settings_bluetooth_hint)
                access.bluetoothDevice != null -> stringResource(R.string.now_reason_bluetooth, access.bluetoothDevice)
                else -> stringResource(R.string.settings_bluetooth_none)
            },
        subtitleLines = 2,
        trailing = if (access.bluetooth) null else stringResource(R.string.settings_allow),
        onClick = access.onBluetooth
    )
    // Una sugerencia descartada tres veces no vuelve sola: aquí se le da otra oportunidad.
    if (silenced.isNotEmpty()) SilencedApps(silenced, home::restoreSuggestion)

    SectionLabel(stringResource(R.string.settings_notifications))
    if (!access.notifications) RequirementRow(R.string.settings_needs_notifications, access.onNotifications)
    DependentSwitch(
        title = R.string.settings_player,
        icon = R.drawable.ic_play,
        on = prefs.playerEnabled,
        met = access.notifications,
        onToggle = { settings.togglePlayer() }
    )
    DependentSwitch(
        title = R.string.settings_unread,
        icon = R.drawable.ic_dot,
        on = prefs.unreadDots,
        met = access.notifications,
        onToggle = { settings.toggleUnreadDots() }
    )
    if (prefs.unreadDots && access.notifications) {
        UnreadApps(context.dock.dockApps(), prefs.unreadMuted, settings::toggleUnreadFor)
    }

    SectionLabel(stringResource(R.string.settings_dock))
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_dock_gestures)),
        leadingIcon = R.drawable.ic_swipe_up,
        checked = prefs.dockGestures,
        onClick = { settings.toggleDockGestures() }
    )
    if (prefs.dockGestures) SwipeTargets(context, home)

    SectionLabel(stringResource(R.string.settings_gestures))
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_zones_pinch)),
        leadingIcon = R.drawable.ic_pinch,
        checked = prefs.zonesPinch,
        onClick = { settings.toggleZonesPinch() }
    )
    if (!access.accessibility) RequirementRow(R.string.settings_needs_accessibility, access.onAccessibility)
    DependentSwitch(
        title = R.string.settings_lock_double_tap,
        icon = R.drawable.ic_lock,
        on = prefs.lockDoubleTap,
        met = access.accessibility,
        onToggle = { settings.toggleLockDoubleTap() },
        subtitle = stringResource(R.string.settings_lock_double_tap_hint)
    )
    DependentSwitch(
        title = R.string.settings_notifications_swipe,
        icon = R.drawable.ic_swipe_down,
        on = prefs.notificationsSwipe,
        met = access.accessibility,
        onToggle = { settings.toggleNotificationsSwipe() },
        subtitle = stringResource(R.string.settings_notifications_swipe_hint)
    )
}

/** Las apps que Ahora ya no sugiere: un toque en una la devuelve. */
@Composable
private fun SilencedApps(apps: List<AppEntry>, onRestore: (AppEntry) -> Unit) {
    Note(stringResource(R.string.settings_now_silenced))
    AppGrid {
        apps.forEach { app ->
            AppTile(
                label = app.label,
                description = stringResource(R.string.settings_now_restore, app.label),
                interaction = Modifier.clickable(role = Role.Button) { onRestore(app) }
            ) { AppIcon(app.icon, AlisioTokens.DockIconSize) }
        }
    }
}

/**
 * Las apps del dock tal como se ven en él: con su punto si está encendido y atenuadas sin él si está
 * apagado. Tocar una lo cambia. Se enseñan con el punto puesto aunque ahora no tengan nada sin leer:
 * aquí se elige cómo quedará, no se mira el estado.
 */
@Composable
private fun UnreadApps(apps: List<AppEntry>, muted: Set<String>, onToggle: (String) -> Unit) {
    Note(
        stringResource(if (apps.isEmpty()) R.string.settings_unread_apps_empty else R.string.settings_unread_apps)
    )
    AppGrid {
        apps.forEach { app ->
            val packageName = app.component.packageName
            val on = packageName !in muted
            AppTile(
                label = app.label,
                description = stringResource(R.string.settings_unread_app, app.label),
                interaction =
                    Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(packageName) }),
                dimmed = !on,
                unread = on
            ) { AppIcon(app.icon, AlisioTokens.DockIconSize) }
        }
    }
}

/**
 * A dónde lleva deslizar hacia arriba sobre cada icono suelto del dock: el destino va en pequeño en
 * su esquina, y un toque abre el selector. Las apps de una pila no tienen este gesto.
 */
@Composable
private fun SwipeTargets(context: SettingsContext, home: HomeViewModel) {
    val targets by home.swipeTargets.collectAsStateWithLifecycle()
    val shortcuts by home.shortcuts.collectAsStateWithLifecycle()
    val loose = remember(context.dock) { context.dock.filterIsInstance<DockSlot.App>().map { it.app } }
    // App cuyo destino se está eligiendo en su hoja.
    var picking by remember { mutableStateOf<AppEntry?>(null) }

    Note(
        stringResource(
            if (loose.isEmpty()) R.string.settings_swipe_targets_empty else R.string.settings_swipe_targets
        )
    )
    AppGrid {
        loose.forEach { app ->
            val target = targets[app.key.toString()]
            AppTile(
                label = app.label,
                description =
                    if (target == null) {
                        stringResource(R.string.settings_swipe_target_none, app.label)
                    } else {
                        stringResource(R.string.settings_swipe_target, app.label, target.label)
                    },
                interaction = Modifier.clickable(role = Role.Button) { picking = app }
            ) {
                Box(Modifier.size(AlisioTokens.DockIconSize)) {
                    AppIcon(app.icon, AlisioTokens.DockIconSize)
                    if (target != null) {
                        AppIcon(
                            target.icon(),
                            TargetBadgeSize,
                            Modifier.align(Alignment.BottomEnd).offset(x = TargetBadgeOffset, y = TargetBadgeOffset)
                        )
                    }
                }
            }
        }
    }

    picking?.let { app ->
        SwipeTargetSheet(
            app = app,
            apps = context.apps,
            shortcuts = shortcuts,
            target = targets[app.key.toString()],
            shortcutIcon = home::shortcutIcon,
            onSetTarget = { home.setSwipeTarget(app, it) },
            onDismiss = { picking = null }
        )
    }
}

private fun SwipeTarget.icon() = when (this) {
    is SwipeTarget.App -> app.icon
    is SwipeTarget.Shortcut -> shortcut.app.icon
}
