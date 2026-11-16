package dev.jesusmolano.alisio.feature.settings

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import dev.jesusmolano.alisio.feature.home.openNoteLink
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

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
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_replay_welcome)),
        leadingIcon = R.drawable.ic_restart,
        // Al dejar de estar vista, MainActivity enseña la bienvenida en lugar de todo lo demás.
        onClick = { context.settings.replayWelcome() }
    )
    Credits()
}

/**
 * Al final de todo y en pequeño: quién hace Alisio, la versión y dónde invitarle a un café. El enlace
 * se abre en el navegador; Alisio no usa la red para esto.
 */
@Composable
private fun Credits() {
    val android = LocalContext.current
    val version = remember { android.packageManager.getPackageInfo(android.packageName, 0).versionName.orEmpty() }
    val failed = stringResource(R.string.app_launch_failed, KOFI_NAME)
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_kofi)),
        leadingIcon = R.drawable.ic_kofi,
        // Sale de Alisio al navegador: lo avisa la marca junto al título, como en Permisos.
        titleMark = R.drawable.ic_open_in_new,
        subtitle = stringResource(R.string.settings_kofi_hint),
        secondary = true,
        // Sin navegador no se abre nada: se dice, como al fallar una app en el Inicio.
        onClick = {
            if (!android.openNoteLink(KOFI_URL)) Toast.makeText(android, failed, Toast.LENGTH_SHORT).show()
        }
    )
    Text(
        text = stringResource(R.string.settings_credits, version),
        style = MaterialTheme.typography.bodySmall,
        color = AlisioTokens.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
}

private const val KOFI_URL = "https://ko-fi.com/jesusmolano"

// Nombre propio: igual en todos los idiomas.
private const val KOFI_NAME = "Ko-fi"

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
