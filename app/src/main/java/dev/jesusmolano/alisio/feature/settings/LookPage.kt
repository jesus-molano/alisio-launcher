package dev.jesusmolano.alisio.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.dock.DOCK_ITEM_SPARE_DP
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppKey
import dev.jesusmolano.alisio.data.apps.installedIconPacks
import dev.jesusmolano.alisio.data.settings.WallpaperDim
import dev.jesusmolano.alisio.feature.appmenu.AppIconSheet
import dev.jesusmolano.alisio.feature.home.DockSlot
import dev.jesusmolano.alisio.feature.home.DockSlotIcon
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.home.rememberAppLook
import dev.jesusmolano.alisio.feature.home.rememberDockIconSize
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.AppPickerSheet
import dev.jesusmolano.alisio.ui.components.ChoiceRow
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberLightWallpaper
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.wallpaperVeil
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.fontsFor

private val VeilPreviewHeight = 88.dp

// Una tesela de estilo de iconos: cuatro apps en 2 × 2 (docs/DISENO.md §6.9).
private val StyleIconSize = 40.dp
private val StyleIconGap = 8.dp
private const val STYLE_APPS = 4

// Las teselas de nombres enseñan tres iconos a este tamaño, para que quepan dos teselas por fila.
private const val LABEL_APPS = 3
private val LabelIconSize = 40.dp
private val LabelCellWidth = 52.dp

/**
 * Aspecto: el fondo, el color y la letra de Alisio. Cada opción se elige tocando su resultado
 * dibujado, y el Inicio en miniatura de arriba lo enseña en su sitio en cuanto se toca.
 *
 * @param home calcula el color del día de muestra.
 * @param onWallpaper abre el selector de fondo de pantalla del sistema.
 */
@Composable
internal fun LookPage(
    context: SettingsContext,
    home: HomeViewModel,
    onWallpaper: () -> Unit,
    wallpaper: WallpaperActions
) {
    val prefs = context.prefs
    val settings = context.settings
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_wallpaper)),
        leadingIcon = R.drawable.ic_wallpaper,
        titleMark = R.drawable.ic_open_in_new,
        onClick = onWallpaper
    )
    // Se ve el efecto al momento en la propia página: la barra de arriba se va o vuelve.
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_hide_status_bar)),
        subtitle = stringResource(R.string.settings_hide_status_bar_hint),
        leadingIcon = R.drawable.ic_smartphone,
        checked = prefs.hideStatusBar,
        onClick = { settings.toggleHideStatusBar() }
    )

    SectionLabel(stringResource(R.string.settings_wallpaper_rotation))
    WallpaperRotationOptions(context, wallpaper)

    SectionLabel(stringResource(R.string.settings_wallpaper_dim))
    VeilChoices(prefs.wallpaperDim, settings::setWallpaperDim)

    SectionLabel(stringResource(R.string.settings_accent))
    AccentChoices(context)
    DayAccentOptions(context, home)

    SectionLabel(stringResource(R.string.settings_font))
    FontChoices(prefs.font, settings::setFont)
}

/**
 * Iconos: cómo se dibujan las apps en el dock, en Zonas y en Trazo. Va aparte de Aspecto para que
 * ninguna de las dos páginas sea un listado largo; el dock de la miniatura enseña cada cambio.
 *
 * @param home dibuja los iconos de muestra en cada estilo y cambia el icono de una app.
 */
@Composable
internal fun IconsPage(context: SettingsContext, home: HomeViewModel) {
    val prefs = context.prefs
    val settings = context.settings
    // La primera opción de la página va sin aire encima, como la primera fila de las demás.
    SectionLabel(stringResource(R.string.settings_icon_style), first = true)
    IconStyleChoices(context, home)

    SectionLabel(stringResource(R.string.settings_live_icons))
    LiveIconChoices(context)

    SectionLabel(stringResource(R.string.settings_app_icons))
    AppIconChoices(context, home)

    SectionLabel(stringResource(R.string.settings_dock_labels))
    val dockSample = remember(context.dock) { context.dock.filter { it != DockSlot.Empty }.take(LABEL_APPS) }
    // Con muchos huecos, cada uno es más estrecho en el Inicio y los nombres se cortan: la muestra usa
    // ese ancho real (nunca más del que cabe en la tesela), para que el corte se vea antes de elegir.
    val dockIcon = rememberDockIconSize(context.dock.size)
    val iconSize = minOf(dockIcon, LabelIconSize)
    val cellWidth = minOf(dockIcon + DOCK_ITEM_SPARE_DP.dp, LabelCellWidth)
    LabelChoices(shown = prefs.dockLabels, onToggle = { settings.setDockLabels(!prefs.dockLabels) }) { labels ->
        dockSample.forEach { slot ->
            DockIcon(
                label = slot.name(),
                showLabel = labels,
                size = iconSize,
                modifier = Modifier.width(cellWidth)
            ) { DockSlotIcon(slot, iconSize) }
        }
    }

    SectionLabel(stringResource(R.string.settings_zones_labels))
    // En Zonas están las apps que no caben en el dock: se enseñan esas. Las de nombre más corto, para
    // que la muestra «con nombres» se lea entera en celdas tan estrechas.
    val zoneSample =
        remember(context.apps, context.dock) {
            val inDock = context.dock.dockApps().mapTo(HashSet()) { it.key }
            val outside = context.apps.filter { it.key !in inDock }.sortedBy { it.label.length }
            (outside + context.apps).distinctBy { it.key }.take(LABEL_APPS)
        }
    LabelChoices(shown = prefs.zonesLabels, onToggle = settings::toggleZonesLabels) { labels ->
        zoneSample.forEach { app ->
            DockIcon(
                label = app.label,
                showLabel = labels,
                size = LabelIconSize,
                modifier = Modifier.width(LabelCellWidth)
            ) { AppIcon(app.icon, LabelIconSize) }
        }
    }
}

private fun DockSlot.name(): String = when (this) {
    DockSlot.Empty -> ""
    is DockSlot.App -> app.label
    is DockSlot.Stack -> name
}

/**
 * Las apps con un icono elegido a mano, cada una con su hoja, y la fila que elige otra app para
 * cambiar el suyo. Es la misma hoja «Icono» del Interior de app.
 */
@Composable
private fun AppIconChoices(context: SettingsContext, home: HomeViewModel) {
    val look = rememberAppLook(home)
    var picking by remember { mutableStateOf(false) }
    // Por clave: la hoja sigue a la app aunque cambie su icono (y con él, su entrada) mientras está abierta.
    var editing by remember { mutableStateOf<AppKey?>(null) }
    context.apps.filter { it.customIcon != null }.forEach { app ->
        ItemRow(
            title = AnnotatedString(app.label),
            icon = app.icon,
            subtitle =
                stringResource(
                    if (app.customIcon is CustomIcon.Image) R.string.icon_source_image else R.string.icon_source_pack
                ),
            trailing = stringResource(R.string.settings_change),
            onClick = { editing = app.key }
        )
    }
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_app_icon_pick)),
        leadingIcon = R.drawable.ic_image,
        onClick = { picking = true }
    )
    if (picking) {
        AppPickerSheet(
            title = stringResource(R.string.settings_app_icon_pick),
            items = context.apps,
            key = { it.key.toString() },
            label = { it.label },
            icon = { it.icon },
            chosen = { false },
            onPick = {
                picking = false
                editing = it.key
            },
            onDismiss = { picking = false }
        )
    }
    editing?.let { key ->
        val app = context.apps.firstOrNull { it.key == key }
        if (app == null) {
            editing = null
        } else {
            AppIconSheet(app, look, onDismiss = { editing = null })
        }
    }
}

/**
 * Cuánto se oscurece el fondo. Las teselas no pueden enseñar el fondo real (leerlo pide un permiso
 * que Alisio no tiene): pintan el mismo velo del Inicio sobre un gris claro de muestra, y el fondo
 * de verdad se ve cambiar en la miniatura de arriba.
 */
@Composable
private fun VeilChoices(current: WallpaperDim, onChoose: (WallpaperDim) -> Unit) {
    val time = formatClock(rememberNow())
    val lightWallpaper = rememberLightWallpaper()
    ChoiceRow(WallpaperDim.entries.size) { tile ->
        WallpaperDim.entries.forEach { dim ->
            PreviewChoice(
                label =
                    stringResource(
                        when (dim) {
                            WallpaperDim.LIGHT -> R.string.settings_dim_light
                            WallpaperDim.MEDIUM -> R.string.settings_dim_medium
                            WallpaperDim.STRONG -> R.string.settings_dim_strong
                        }
                    ),
                selected = dim == current,
                onClick = { onChoose(dim) },
                modifier = tile,
                previewHeight = VeilPreviewHeight,
                backdrop =
                    Modifier
                        .background(AlisioTokens.TextSecondary)
                        .wallpaperVeil(dim.factor, lightWallpaper)
            ) {
                Text(text = time, style = AlisioTokens.Numeral, color = AlisioTokens.Text, maxLines = 1)
            }
        }
    }
}

/**
 * Los estilos de iconos y los paquetes instalados, cada uno con cuatro apps del dock dibujadas en
 * ese estilo. Con paquetes instalados las opciones pasan de tres y la fila se desplaza.
 */
@Composable
private fun IconStyleChoices(context: SettingsContext, home: HomeViewModel) {
    val android = LocalContext.current
    val packs = remember { installedIconPacks(android) }
    // Las apps que el usuario ve a diario; si el dock tiene menos de cuatro, se completa con otras.
    val sample =
        remember(context.apps, context.dock) {
            (context.dock.dockApps() + context.apps).distinctBy { it.key }.take(STYLE_APPS)
        }
    ChoiceRow(BUILT_IN_ICON_STYLES + packs.size) { tile ->
        IconStyleChoice(stringResource(R.string.icons_color), IconStyle.Color, sample, context, home, tile)
        IconStyleChoice(stringResource(R.string.icons_mono), IconStyle.Monochrome, sample, context, home, tile)
        IconStyleChoice(stringResource(R.string.icons_system), IconStyle.System, sample, context, home, tile)
        packs.forEach { pack ->
            IconStyleChoice(pack.label, IconStyle.Pack(pack.packageName), sample, context, home, tile)
        }
    }
}

// Color, monocromo y los del sistema; los paquetes instalados se suman a estos.
private const val BUILT_IN_ICON_STYLES = 3

@Composable
private fun IconStyleChoice(
    label: String,
    style: IconStyle,
    sample: List<AppEntry>,
    context: SettingsContext,
    home: HomeViewModel,
    modifier: Modifier = Modifier
) {
    // Mientras se dibujan (fuera del hilo principal) la tesela guarda su sitio vacía.
    val icons by produceState(emptyList<Bitmap>(), style, sample) { value = home.previewIcons(style, sample) }
    PreviewChoice(
        label = label,
        selected = style == context.prefs.iconStyle,
        onClick = { context.settings.setIconStyle(style) },
        modifier = modifier,
        previewHeight = StyleIconSize * 2 + StyleIconGap
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StyleIconGap)) {
            icons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(StyleIconGap)) {
                    row.forEach { AppIcon(it, StyleIconSize) }
                }
            }
        }
    }
}

/**
 * Enseñar o no el nombre bajo cada icono: dos teselas con los mismos iconos, con nombre y sin él.
 *
 * @param icons dibuja los iconos de muestra; recibe si llevan nombre.
 */
@Composable
private fun LabelChoices(shown: Boolean, onToggle: () -> Unit, icons: @Composable RowScope.(Boolean) -> Unit) {
    ChoiceRow(2) { tile ->
        listOf(true, false).forEach { labels ->
            PreviewChoice(
                label = stringResource(if (labels) R.string.settings_labels_on else R.string.settings_labels_off),
                selected = shown == labels,
                // Son dos valores de una misma opción: tocar el que no está elegido la cambia.
                onClick = { if (shown != labels) onToggle() },
                modifier = tile
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { icons(labels) }
            }
        }
    }
}

/**
 * La tipografía se elige mirando: cada tesela enseña la hora y una palabra de la interfaz con sus
 * familias, tal como quedarán.
 */
@Composable
private fun FontChoices(current: FontChoice, onChoose: (FontChoice) -> Unit) {
    val time = formatClock(rememberNow())
    ChoiceRow(FontChoice.entries.size) { tile ->
        FontChoice.entries.forEach { choice ->
            val fonts = remember(choice) { fontsFor(choice) }
            PreviewChoice(
                label = stringResource(choice.label()),
                selected = choice == current,
                onClick = { onChoose(choice) },
                modifier = tile
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = fonts.display),
                        color = AlisioTokens.Text,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.search_hint_plain),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fonts.text),
                        color = AlisioTokens.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
