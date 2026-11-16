package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.glance.GlanceItem
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.settings.Access
import dev.jesusmolano.alisio.core.settings.AccessUse
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.core.settings.blockingAccess
import dev.jesusmolano.alisio.core.theme.CUSTOM_ACCENT
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.data.apps.installedIconPacks
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.ui.components.ItemRow

// Los nombres de los acentos, en el orden de `AlisioTokens.Accents`.
private val AccentNames =
    listOf(R.string.accent_teal, R.string.accent_blue, R.string.accent_amber, R.string.accent_lilac)

/** El nombre de un acento, para decirlo en voz alta y para el resumen de Aspecto. */
@StringRes
internal fun accentName(index: Int): Int =
    if (index == CUSTOM_ACCENT) R.string.accent_custom else AccentNames.getOrElse(index) { AccentNames.first() }

@StringRes
internal fun FontChoice.label(): Int = when (this) {
    FontChoice.PAIR -> R.string.font_pair
    FontChoice.INTER -> R.string.font_inter
    FontChoice.GROTESK -> R.string.font_grotesk
    FontChoice.SYSTEM -> R.string.font_system
}

/**
 * La portada: una fila por sección, con lo elegido en ella como resumen. Permisos va la última, salvo
 * que falte un acceso que algo encendido necesita: entonces es lo primero que hay que ver.
 */
@Composable
internal fun CoverPage(
    context: SettingsContext,
    zones: ZoneSettings,
    backup: BackupActions,
    onNavigate: (SettingsDestination) -> Unit
) {
    val blocked = context.blockingAccess().isNotEmpty()
    val sections =
        listOf(
            SettingsDestination.LOOK,
            SettingsDestination.ICONS,
            SettingsDestination.HOME,
            SettingsDestination.GESTURES,
            SettingsDestination.SEARCH,
            SettingsDestination.ZONES,
            SettingsDestination.PAUSE
        )
    val pages =
        if (blocked) {
            listOf(SettingsDestination.PERMISSIONS) + sections
        } else {
            sections + SettingsDestination.PERMISSIONS
        }
    pages.forEach { page ->
        ItemRow(
            title = AnnotatedString(stringResource(page.title())),
            leadingIcon = page.icon(),
            subtitle = summary(page, context, zones),
            onClick = { onNavigate(page) }
        )
    }
    BackupRows(backup)
}

/** Los accesos que faltan y que algo encendido necesita (ver [blockingAccess]). */
private fun SettingsContext.blockingAccess(): Set<Access> = blockingAccess(
    access.granted,
    AccessUse(
        player = prefs.playerEnabled,
        unreadDots = prefs.unreadDots,
        lockDoubleTap = prefs.lockDoubleTap,
        notificationsSwipe = prefs.notificationsSwipe,
        contactPhotos = prefs.contactPhotos,
        nextEvent = GlanceItem.EVENT in prefs.glance,
        deviceLocation = prefs.usesDeviceLocation(),
        screenTime = GlanceItem.SCREEN_TIME in prefs.glance,
        favoritePeople = prefs.favoritePeople.isNotEmpty()
    )
)

// Lo que cabe en el resumen de una fila antes de que se corte: las primeras cosas encendidas.
private const val SUMMARY_ITEMS = 4

/** El vector de cada sección: el mismo en la portada y en los resultados de la barra. */
@DrawableRes
internal fun SettingsDestination.icon(): Int = when (this) {
    SettingsDestination.COVER -> R.drawable.ic_settings
    SettingsDestination.LOOK -> R.drawable.ic_palette
    SettingsDestination.ICONS -> R.drawable.ic_apps
    SettingsDestination.HOME -> R.drawable.ic_home
    SettingsDestination.GESTURES -> R.drawable.ic_gesture
    SettingsDestination.SEARCH -> R.drawable.ic_search
    SettingsDestination.ZONES -> R.drawable.ic_zones
    SettingsDestination.PAUSE -> R.drawable.ic_hourglass
    SettingsDestination.PERMISSIONS -> R.drawable.ic_lock
}

/** Lo elegido en una sección, en una línea: se sabe cómo está sin entrar. */
@Composable
private fun summary(page: SettingsDestination, context: SettingsContext, zones: ZoneSettings): String {
    val prefs = context.prefs
    val android = LocalContext.current
    val separator = stringResource(R.string.list_separator)
    return when (page) {
        SettingsDestination.COVER -> ""

        SettingsDestination.LOOK ->
            listOfNotNull(
                stringResource(if (prefs.dayAccent) R.string.settings_day_accent else accentName(prefs.accent)),
                stringResource(prefs.font.label()),
                stringResource(R.string.settings_wallpaper_rotation).takeIf {
                    prefs.wallpaperRotation && prefs.wallpaperFolder != null
                }
            ).joinToString(separator)

        SettingsDestination.ICONS -> {
            val style =
                when (val style = prefs.iconStyle) {
                    IconStyle.Color -> stringResource(R.string.icons_color)

                    IconStyle.Monochrome -> stringResource(R.string.icons_mono)

                    IconStyle.System -> stringResource(R.string.icons_system)

                    is IconStyle.Pack ->
                        remember(style) {
                            installedIconPacks(android).firstOrNull { it.packageName == style.packageName }
                        }
                            ?.label
                            ?: stringResource(R.string.icons_color)
                }
            val custom = context.apps.count { it.customIcon != null }
            listOfNotNull(
                style,
                stringResource(R.string.settings_live_icons).takeIf { prefs.liveIcons },
                pluralStringResource(R.plurals.settings_summary_custom_icons, custom, custom).takeIf { custom > 0 }
            ).joinToString(separator)
        }

        SettingsDestination.HOME -> {
            // Sin acceso a notificaciones no hay reproductor ni puntos, estén como estén sus opciones.
            val notified = context.access.notifications
            val dock =
                if (prefs.dockSlots == 0) {
                    stringResource(R.string.settings_summary_no_dock)
                } else {
                    pluralStringResource(R.plurals.settings_summary_dock, prefs.dockSlots, prefs.dockSlots)
                }
            (
                listOf(dock) +
                    listOfNotNull(
                        stringResource(R.string.settings_now).takeIf { prefs.nowEnabled },
                        stringResource(R.string.settings_section_notes).takeIf { prefs.homeNotes },
                        stringResource(R.string.settings_player).takeIf { prefs.playerEnabled && notified },
                        stringResource(R.string.settings_unread).takeIf { prefs.unreadDots && notified }
                    )
                ).take(SUMMARY_ITEMS).joinToString(separator)
        }

        SettingsDestination.GESTURES -> {
            // Los dos gestos del servicio no hacen nada sin él: no se cuentan.
            val served = context.access.accessibility
            listOfNotNull(
                stringResource(R.string.settings_draw_initial).takeIf { prefs.drawInitial },
                stringResource(R.string.settings_summary_pinch).takeIf { prefs.zonesPinch },
                stringResource(R.string.settings_dock_gestures).takeIf { prefs.dockGestures },
                stringResource(R.string.settings_summary_double_tap).takeIf { prefs.lockDoubleTap && served },
                stringResource(R.string.settings_notifications_swipe).takeIf { prefs.notificationsSwipe && served }
            ).take(SUMMARY_ITEMS)
                .ifEmpty { listOf(stringResource(R.string.settings_summary_gestures_none)) }
                .joinToString(separator)
        }

        SettingsDestination.SEARCH -> {
            val assistant = remember(prefs.assistantPackage, context.apps) { android.appLabel(prefs.assistantPackage) }
            listOfNotNull(
                assistant,
                stringResource(R.string.settings_clipboard).takeIf { prefs.clipboardActions },
                stringResource(R.string.settings_section_people).takeIf { context.access.contacts },
                stringResource(R.string.settings_section_commands).takeIf { prefs.phoneCommands }
            ).ifEmpty { listOf(stringResource(R.string.settings_web_search)) }.joinToString(separator)
        }

        SettingsDestination.ZONES ->
            if (zones.islands.isEmpty()) {
                stringResource(R.string.settings_summary_no_islands)
            } else {
                pluralStringResource(R.plurals.settings_summary_islands, zones.islands.size, zones.islands.size)
            }

        SettingsDestination.PAUSE -> {
            val paused = context.apps.map { it.component.packageName }.distinct().count { it in prefs.pausedApps }
            if (paused == 0) {
                stringResource(R.string.settings_pause_none)
            } else {
                pluralStringResource(R.plurals.settings_summary_pause, paused, paused, prefs.pauseSeconds)
            }
        }

        // Solo cuenta como «falta» lo que algo encendido necesita: los accesos opcionales (el brillo,
        // no molestar, el audio Bluetooth…) sin conceder no son un aviso.
        SettingsDestination.PERMISSIONS -> {
            val missing = context.blockingAccess().size
            when {
                missing > 0 -> pluralStringResource(R.plurals.settings_summary_missing, missing, missing)

                context.access.granted.size == Access.entries.size -> stringResource(
                    R.string.settings_summary_all_granted
                )

                else -> stringResource(R.string.settings_summary_needed_granted)
            }
        }
    }
}
