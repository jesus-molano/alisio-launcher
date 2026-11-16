package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.dock.MAX_DOCK_SLOTS
import dev.jesusmolano.alisio.core.glance.BatteryShow
import dev.jesusmolano.alisio.core.glance.GlanceItem
import dev.jesusmolano.alisio.core.glance.Place
import dev.jesusmolano.alisio.core.time.ClockFormat
import dev.jesusmolano.alisio.core.time.ClockSize
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.feature.home.DockSlot
import dev.jesusmolano.alisio.feature.home.DockSlotIcon
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.glancePieces
import dev.jesusmolano.alisio.feature.home.scaled
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppPickerSheet
import dev.jesusmolano.alisio.ui.components.ChoiceRow
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

// Las teselas de formato enseñan una hora de tarde: a las 9:41 «12 h» y «24 h» se verían iguales.
private const val FORMAT_SAMPLE_HOUR = 17
private const val FORMAT_SAMPLE_MINUTE = 30

// La tesela de cada tamaño enseña la hora a esta fracción de la del Inicio: cabe entera en 104 dp.
private const val SIZE_TILE_RATIO = 0.42f

// Lo que se espera tras la última tecla antes de buscar la ciudad: no se pide un resultado por letra.
private const val SEARCH_DEBOUNCE_MILLIS = 400L

// Los iconos de la tesela de cada número de huecos: tantos como caben en una tesela de 104 dp.
private val SlotTileGap = 2.dp
private val SlotTileSpan = 80.dp
private val SlotTileMaxIcon = 18.dp

/** El orden en que se ofrecen las piezas apagadas: el de siempre, no el alfabético. */
private val GlanceOrder = GlanceItem.entries

/**
 * La hora del Inicio: su tamaño y su formato, cada uno con tres teselas que la dibujan así. La
 * miniatura de arriba la enseña en su sitio.
 */
@Composable
internal fun ClockChoices(context: SettingsContext) {
    val prefs = context.prefs
    val now = rememberNow()
    val clock = AlisioTokens.Clock
    Note(stringResource(R.string.settings_clock_size))
    ChoiceRow(ClockSize.entries.size) { tile ->
        ClockSize.entries.forEach { size ->
            PreviewChoice(
                label = stringResource(size.label()),
                selected = prefs.clockSize == size,
                onClick = { context.settings.setClockSize(size) },
                modifier = tile
            ) {
                Text(
                    text = formatClock(now, prefs.clockFormat),
                    style = clock.scaled(SIZE_TILE_RATIO * AlisioTokens.clockScale(size)),
                    color = AlisioTokens.Text,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
    // Aire entre las dos filas de teselas: la nota es del grupo de abajo, no del de arriba.
    Spacer(Modifier.height(AlisioTokens.LabelGap * 2))
    Note(stringResource(R.string.settings_clock_format))
    val afternoon = remember { ZonedDateTime.now().withHour(FORMAT_SAMPLE_HOUR).withMinute(FORMAT_SAMPLE_MINUTE) }
    ChoiceRow(ClockFormat.entries.size) { tile ->
        ClockFormat.entries.forEach { format ->
            PreviewChoice(
                label = stringResource(format.label()),
                selected = prefs.clockFormat == format,
                onClick = { context.settings.setClockFormat(format) },
                modifier = tile
            ) {
                Text(
                    text = formatClock(afternoon, format),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = clock.fontFamily),
                    color = AlisioTokens.Text,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@StringRes
private fun ClockSize.label(): Int = when (this) {
    ClockSize.SMALL -> R.string.settings_clock_small
    ClockSize.MEDIUM -> R.string.settings_clock_medium
    ClockSize.LARGE -> R.string.settings_clock_large
}

@StringRes
private fun ClockFormat.label(): Int = when (this) {
    ClockFormat.SYSTEM -> R.string.settings_clock_system
    ClockFormat.H12 -> R.string.settings_clock_12
    ClockFormat.H24 -> R.string.settings_clock_24
}

/**
 * Cuántos huecos tiene el dock, de 0 a 8: cada tesela dibuja ese dock con lo que tendrá, también
 * los huecos que ahora están ocultos y volverían. La nota lo dice: reducir no borra nada.
 *
 * @param all los ocho huecos con su contenido guardado.
 */
@Composable
internal fun DockSlotChoices(context: SettingsContext, all: List<DockSlot>) {
    // Se abre con la elegida y la anterior a la vista: se ve que se puede ir a menos y a más.
    ChoiceRow(MAX_DOCK_SLOTS + 1, shown = (context.prefs.dockSlots - 1).coerceAtLeast(0)) { tile ->
        (0..MAX_DOCK_SLOTS).forEach { count ->
            PreviewChoice(
                label =
                    if (count == 0) {
                        stringResource(R.string.settings_dock_none)
                    } else {
                        pluralStringResource(R.plurals.settings_dock_slots, count, count)
                    },
                selected = context.prefs.dockSlots == count,
                onClick = { context.settings.setDockSlots(count) },
                modifier = tile
            ) {
                if (count > 0) {
                    val icon = minOf(SlotTileMaxIcon, (SlotTileSpan - SlotTileGap * (count - 1)) / count)
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(SlotTileGap)) {
                        repeat(count) { position -> DockSlotIcon(all.getOrElse(position) { DockSlot.Empty }, icon) }
                    }
                }
            }
        }
    }
    Note(stringResource(R.string.settings_dock_slots_note))
}

/** Cómo se presenta cada pieza en los ajustes: su nombre y su dibujo. */
private class GlanceInfo(@param:StringRes val title: Int, @param:DrawableRes val icon: Int)

private fun GlanceItem.info(): GlanceInfo = when (this) {
    GlanceItem.ALARM -> GlanceInfo(R.string.settings_glance_alarm, R.drawable.ic_alarm)
    GlanceItem.EVENT -> GlanceInfo(R.string.settings_glance_event, R.drawable.ic_event)
    GlanceItem.WEATHER -> GlanceInfo(R.string.settings_glance_weather, R.drawable.ic_weather_partly)
    GlanceItem.BATTERY -> GlanceInfo(R.string.settings_glance_battery, R.drawable.ic_battery)
    GlanceItem.SUN -> GlanceInfo(R.string.settings_glance_sun, R.drawable.ic_twilight)
    GlanceItem.SCREEN_TIME -> GlanceInfo(R.string.settings_glance_screen, R.drawable.ic_smartphone)
}

/**
 * Lo que va junto a la hora: las piezas encendidas primero, en su orden, con un botón para subir
 * cada una; las apagadas después. Cada fila dice lo que su pieza enseña ahora mismo, y la miniatura
 * de arriba la pone en su sitio al encenderla. Si a una pieza encendida le falta un permiso, la
 * primera fila del grupo lo pide.
 */
@Composable
internal fun GlanceChoices(context: SettingsContext, home: HomeViewModel) {
    val prefs = context.prefs
    val access = context.access
    val data by home.glance.collectAsStateWithLifecycle()
    val now = rememberNow().toInstant().toEpochMilli()
    // Lo que diría cada pieza hoy, sin muestras: es el secundario de su fila.
    val live = glancePieces(prefs, data, now).associateBy { it.item }

    if (GlanceItem.EVENT in prefs.glance && !access.calendar) {
        PermissionRow(R.string.settings_needs_calendar, access.onCalendar)
    }
    if (prefs.usesDeviceLocation() && !access.location) {
        PermissionRow(R.string.settings_needs_location, access.onLocation)
    }
    if (GlanceItem.SCREEN_TIME in prefs.glance && !access.usage) {
        RequirementRow(R.string.settings_needs_usage, access.onUsage)
    }

    val ordered = prefs.glance + GlanceOrder.filter { it !in prefs.glance }
    ordered.forEach { item ->
        val info = item.info()
        val on = item in prefs.glance
        val title = stringResource(info.title)
        val position = prefs.glance.indexOf(item)
        ItemRow(
            title = AnnotatedString(title),
            leadingIcon = info.icon,
            subtitle = glanceStatus(item, on, live[item]?.fullText, access.calendar, data.battery?.percent),
            subtitleLines = 2,
            checked = on,
            onClick = { context.settings.toggleGlance(item) },
            actions =
                if (position > 0) {
                    {
                        RoundIconButton(
                            R.drawable.ic_arrow_upward,
                            stringResource(R.string.settings_glance_raise, title),
                            AlisioTokens.TextSecondary,
                            onClick = { context.settings.raiseGlance(item) }
                        )
                    }
                } else {
                    null
                }
        )
    }

    if (GlanceItem.BATTERY in prefs.glance) {
        SectionLabel(stringResource(R.string.settings_glance_battery))
        BatteryChoices(context)
    }
    if (GlanceItem.WEATHER in prefs.glance || GlanceItem.SUN in prefs.glance) {
        SectionLabel(stringResource(R.string.settings_glance_weather))
        WeatherOptions(context, home)
    }
}

/**
 * Lo que dice la fila de cada pieza: lo que enseña ahora y, si no enseña nada, por qué. Del tiempo,
 * apagado, que usa la red: es lo único de la línea que sale del teléfono.
 */
@Composable
private fun glanceStatus(
    item: GlanceItem,
    on: Boolean,
    shown: String?,
    calendarGranted: Boolean,
    batteryPercent: Int?
): String? {
    if (!on) return if (item == GlanceItem.WEATHER) stringResource(R.string.settings_glance_weather_hint) else null
    if (shown != null) return shown
    return when (item) {
        GlanceItem.ALARM -> stringResource(R.string.settings_glance_alarm_none)

        // Sin el permiso, lo que falta ya lo dice la primera fila del grupo.
        GlanceItem.EVENT -> stringResource(R.string.settings_glance_event_none).takeIf { calendarGranted }

        // Oculta por el umbral: se dice la cifra de ahora y cuándo saldrá. Sin lectura aún, nada.
        GlanceItem.BATTERY ->
            batteryPercent?.let {
                stringResource(R.string.settings_glance_battery_hidden, stringResource(R.string.glance_battery, it))
            }

        else -> stringResource(R.string.settings_glance_waiting)
    }
}

/** Un permiso que se concede con un diálogo sobre Alisio: sin la marca de salir a otra pantalla. */
@Composable
private fun PermissionRow(@StringRes title: Int, onGrant: () -> Unit) {
    ItemRow(
        title = AnnotatedString(stringResource(title)),
        leadingIcon = R.drawable.ic_info,
        trailing = stringResource(R.string.settings_allow),
        onClick = onGrant
    )
}

/** Cuándo sale la batería: tres teselas con la pieza tal como se ve en cada caso. */
@Composable
private fun BatteryChoices(context: SettingsContext) {
    ChoiceRow(BatteryShow.entries.size) { tile ->
        BatteryShow.entries.forEach { show ->
            val (label, icon, percent) =
                when (show) {
                    BatteryShow.ALWAYS -> Triple(R.string.settings_battery_always, R.drawable.ic_battery, 78)

                    BatteryShow.LOW -> Triple(R.string.settings_battery_low, R.drawable.ic_battery_alert, 22)

                    BatteryShow.CHARGING -> Triple(
                        R.string.settings_battery_charging,
                        R.drawable.ic_battery_charging,
                        64
                    )
                }
            PreviewChoice(
                label = stringResource(label),
                selected = context.prefs.batteryShow == show,
                onClick = { context.settings.setBatteryShow(show) },
                modifier = tile
            ) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = AlisioTokens.Text,
                        modifier = Modifier.size(AlisioTokens.GlanceIconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.glance_battery, percent),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = AlisioTokens.Text
                    )
                }
            }
        }
    }
}

/** De dónde sale el tiempo (y el sol) y qué abre tocarlo. */
@Composable
private fun WeatherOptions(context: SettingsContext, home: HomeViewModel) {
    val prefs = context.prefs
    var choosing by remember { mutableStateOf<WeatherChoice?>(null) }
    val place = prefs.weatherPlace
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_weather_place)),
        leadingIcon = R.drawable.ic_place,
        subtitle = place?.name ?: stringResource(R.string.settings_weather_location),
        trailing = stringResource(R.string.settings_change),
        onClick = { choosing = WeatherChoice.PLACE }
    )
    val app = remember(prefs.weatherApp, context.apps) {
        context.apps.firstOrNull { it.component.packageName == prefs.weatherApp }
    }
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_weather_open)),
        icon = app?.icon,
        leadingIcon = R.drawable.ic_search.takeIf { app == null },
        subtitle = app?.label ?: stringResource(R.string.settings_weather_web),
        trailing = stringResource(R.string.settings_change),
        onClick = { choosing = WeatherChoice.APP }
    )

    when (choosing) {
        WeatherChoice.PLACE ->
            PlaceSheet(
                current = place,
                search = home::searchPlaces,
                onPick = {
                    context.settings.setWeatherPlace(it)
                    choosing = null
                },
                onDismiss = { choosing = null }
            )

        WeatherChoice.APP ->
            AppPickerSheet(
                title = stringResource(R.string.settings_weather_app_title),
                items = context.apps,
                key = { it.key.toString() },
                label = AppEntry::label,
                icon = { it.icon },
                chosen = { it.component.packageName == prefs.weatherApp },
                onPick = {
                    context.settings.setWeatherApp(it.component.packageName)
                    choosing = null
                },
                onDismiss = { choosing = null }
            ) {
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.settings_weather_web)),
                    leadingIcon = R.drawable.ic_search,
                    selected = prefs.weatherApp == null,
                    onClick = {
                        context.settings.setWeatherApp(null)
                        choosing = null
                    }
                )
            }

        null -> Unit
    }
}

private enum class WeatherChoice { PLACE, APP }

/** Lo que dice la búsqueda de una ciudad mientras se escribe. */
private sealed interface PlaceSearch {
    data object Idle : PlaceSearch

    data object Offline : PlaceSearch

    class Found(val places: List<Place>) : PlaceSearch
}

/**
 * Dónde se mira el tiempo: la ubicación aproximada o una ciudad buscada por su nombre. Como el
 * selector de apps, la lista crece desde el buscador, que va abajo junto al teclado. Buscar usa la
 * red: sin ella lo dice, y lo elegido antes se queda.
 */
@Composable
private fun PlaceSheet(
    current: Place?,
    search: suspend (String, String) -> List<Place>?,
    onPick: (Place?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PlaceSearch>(PlaceSearch.Idle) }
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(query, language) {
        if (query.isBlank()) {
            result = PlaceSearch.Idle
            return@LaunchedEffect
        }
        // Cada tecla cancela la búsqueda anterior: solo se pide lo escrito al parar.
        delay(SEARCH_DEBOUNCE_MILLIS)
        result = search(query, language)?.let(PlaceSearch::Found) ?: PlaceSearch.Offline
    }
    AlisioSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.settings_weather_place_title))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = AlisioTokens.Outset, vertical = 8.dp)
        ) {
            when (val found = result) {
                PlaceSearch.Idle -> Unit

                PlaceSearch.Offline -> item { Note(stringResource(R.string.settings_weather_offline)) }

                is PlaceSearch.Found -> {
                    if (found.places.isEmpty()) item { Note(stringResource(R.string.settings_weather_no_results)) }
                    items(found.places, key = { "${it.latitude},${it.longitude}" }) { place ->
                        ItemRow(
                            title = AnnotatedString(place.name),
                            leadingIcon = R.drawable.ic_place,
                            subtitle = place.region,
                            selected = current == place,
                            onClick = { onPick(place) }
                        )
                    }
                }
            }
            // Arriba de lo buscado, la opción de siempre: volver a la ubicación aproximada.
            if (query.isBlank()) {
                item {
                    ItemRow(
                        title = AnnotatedString(stringResource(R.string.settings_weather_location)),
                        leadingIcon = R.drawable.ic_place,
                        selected = current == null,
                        onClick = { onPick(null) }
                    )
                }
                current?.let { chosen ->
                    item {
                        ItemRow(
                            title = AnnotatedString(chosen.name),
                            leadingIcon = R.drawable.ic_place,
                            subtitle = chosen.region,
                            selected = true,
                            onClick = { onPick(chosen) }
                        )
                    }
                }
            }
        }
        PillTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.settings_weather_search),
            leadingIcon = R.drawable.ic_search,
            modifier =
                Modifier
                    .imePadding()
                    .padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
        )
    }
}
