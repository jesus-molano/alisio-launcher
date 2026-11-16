package dev.jesusmolano.alisio.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.installedIconPacks
import dev.jesusmolano.alisio.data.settings.WallpaperDim
import dev.jesusmolano.alisio.feature.home.DockSlot
import dev.jesusmolano.alisio.feature.home.DockSlotIcon
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.ui.components.AppIcon
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

private val AccentSwatchSize = 36.dp
private val VeilPreviewHeight = 88.dp

// Con más opciones que estas, las teselas dejan de repartirse el ancho y miden ChoiceTileWidth.
private const val EVEN_CHOICES = 3
private val ChoiceTileWidth = 104.dp

// Una tesela de estilo de iconos: cuatro apps en 2 × 2 (docs/DISENO.md §6.9).
private val StyleIconSize = 40.dp
private val StyleIconGap = 8.dp
private const val STYLE_APPS = 4

// Las teselas de nombres enseñan tres iconos a este tamaño, para que quepan dos teselas por fila.
private const val LABEL_APPS = 3
private val LabelIconSize = 40.dp
private val LabelCellWidth = 52.dp

/**
 * Aspecto: lo que cambia cómo se ve Alisio. Cada opción se elige tocando su resultado dibujado, y
 * el Inicio en miniatura de arriba lo enseña en su sitio en cuanto se toca.
 *
 * @param home dibuja los iconos de muestra en cada estilo.
 * @param onWallpaper abre el selector de fondo de pantalla del sistema.
 */
@Composable
internal fun LookPage(context: SettingsContext, home: HomeViewModel, onWallpaper: () -> Unit) {
    val prefs = context.prefs
    val settings = context.settings
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_wallpaper)),
        leadingIcon = R.drawable.ic_wallpaper,
        titleMark = R.drawable.ic_open_in_new,
        onClick = onWallpaper
    )

    SectionLabel(stringResource(R.string.settings_wallpaper_dim))
    VeilChoices(prefs.wallpaperDim, settings::setWallpaperDim)

    SectionLabel(stringResource(R.string.settings_accent))
    AccentChoices(prefs.accent, settings::setAccent)

    SectionLabel(stringResource(R.string.settings_icons))
    IconStyleChoices(context, home)

    SectionLabel(stringResource(R.string.settings_dock_labels))
    val dockSample = remember(context.dock) { context.dock.filter { it != DockSlot.Empty }.take(LABEL_APPS) }
    LabelChoices(shown = prefs.dockLabels, onToggle = settings::toggleDockLabels) { labels ->
        dockSample.forEach { slot ->
            DockIcon(
                label = slot.name(),
                showLabel = labels,
                size = LabelIconSize,
                modifier = Modifier.width(LabelCellWidth)
            ) { DockSlotIcon(slot, LabelIconSize) }
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

    SectionLabel(stringResource(R.string.settings_font))
    FontChoices(prefs.font, settings::setFont)
}

private fun DockSlot.name(): String = when (this) {
    DockSlot.Empty -> ""
    is DockSlot.App -> app.label
    is DockSlot.Stack -> name
}

/**
 * Una fila de teselas alineada con las filas de la página (docs/DISENO.md §4.5): con hasta tres
 * opciones se reparten el ancho a partes iguales; con más, miden [ChoiceTileWidth] y la fila se
 * desplaza, para que ninguna quede tan estrecha que su muestra no se lea.
 *
 * @param count cuántas teselas lleva la fila.
 * @param content recibe el modificador de ancho que debe llevar cada tesela.
 */
@Composable
private fun ChoiceRow(count: Int, content: @Composable RowScope.(tile: Modifier) -> Unit) {
    val scrolls = count > EVEN_CHOICES
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = AlisioTokens.Outset),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content(if (scrolls) Modifier.width(ChoiceTileWidth) else Modifier.weight(1f)) }
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

/** Los acentos, todos del mismo tamaño: el elegido se distingue por su marca, no por crecer. */
@Composable
private fun AccentChoices(current: Int, onChoose: (Int) -> Unit) {
    // El círculo va centrado en su toque de 48 dp: el margen deja el primero en el filo.
    Row(Modifier.padding(horizontal = 12.dp - (AlisioTokens.MinTouch - AccentSwatchSize) / 2)) {
        AlisioTokens.Accents.forEachIndexed { index, color ->
            val name = stringResource(accentName(index))
            val chosen = index == current
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(AlisioTokens.MinTouch)
                    .clip(CircleShape)
                    .selectable(selected = chosen, role = Role.RadioButton) { onChoose(index) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(AccentSwatchSize).background(color, CircleShape), Alignment.Center) {
                    if (chosen) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = AlisioTokens.Background,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
