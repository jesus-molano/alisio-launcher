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
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.settings.Access
import dev.jesusmolano.alisio.core.settings.AccessUse
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.core.settings.blockingAccess
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.data.apps.installedIconPacks
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.ui.components.ItemRow

// Los nombres de los acentos, en el orden de `AlisioTokens.Accents`.
private val AccentNames =
    listOf(R.string.accent_teal, R.string.accent_blue, R.string.accent_amber, R.string.accent_lilac)

/** El nombre de un acento, para decirlo en voz alta y para el resumen de Aspecto. */
@StringRes
internal fun accentName(index: Int): Int = AccentNames.getOrElse(index) { AccentNames.first() }

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
internal fun CoverPage(context: SettingsContext, zones: ZoneSettings, onNavigate: (SettingsDestination) -> Unit) {
    val prefs = context.prefs
    val blocked =
        blockingAccess(
            context.access.granted,
            AccessUse(
                player = prefs.playerEnabled,
                unreadDots = prefs.unreadDots,
                lockDoubleTap = prefs.lockDoubleTap,
                notificationsSwipe = prefs.notificationsSwipe,
                contactPhotos = prefs.contactPhotos
            )
        ).isNotEmpty()
    val sections =
        listOf(
            SettingsDestination.LOOK,
            SettingsDestination.HOME,
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
}

/** El vector de cada sección: el mismo en la portada y en los resultados de la barra. */
@DrawableRes
internal fun SettingsDestination.icon(): Int = when (this) {
    SettingsDestination.COVER -> R.drawable.ic_settings
    SettingsDestination.LOOK -> R.drawable.ic_palette
    SettingsDestination.HOME -> R.drawable.ic_home
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

        SettingsDestination.LOOK -> {
            val icons =
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
            listOf(stringResource(accentName(prefs.accent)), icons, stringResource(prefs.font.label()))
                .joinToString(separator)
        }

        SettingsDestination.HOME -> {
            // Sin acceso a notificaciones no hay reproductor ni puntos, estén como estén sus opciones.
            val notified = context.access.notifications
            listOfNotNull(
                stringResource(R.string.settings_now).takeIf { prefs.nowEnabled },
                stringResource(R.string.settings_player).takeIf { prefs.playerEnabled && notified },
                stringResource(R.string.settings_unread).takeIf { prefs.unreadDots && notified }
            ).ifEmpty { listOf(stringResource(R.string.settings_summary_home_none)) }.joinToString(separator)
        }

        SettingsDestination.SEARCH -> {
            val assistant = remember(prefs.assistantPackage, context.apps) { android.appLabel(prefs.assistantPackage) }
            listOfNotNull(
                assistant,
                stringResource(R.string.settings_clipboard).takeIf { prefs.clipboardActions },
                stringResource(R.string.settings_section_people).takeIf { context.access.contacts }
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

        SettingsDestination.PERMISSIONS -> {
            val missing = Access.entries.size - context.access.granted.size
            if (missing == 0) {
                stringResource(R.string.settings_summary_all_granted)
            } else {
                pluralStringResource(R.plurals.settings_summary_missing, missing, missing)
            }
        }
    }
}
