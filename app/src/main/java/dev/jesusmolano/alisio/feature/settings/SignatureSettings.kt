package dev.jesusmolano.alisio.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.command.PhoneCommand
import dev.jesusmolano.alisio.core.command.Switch
import dev.jesusmolano.alisio.core.draw.LETTER_TEMPLATES
import dev.jesusmolano.alisio.core.draw.initialOf
import dev.jesusmolano.alisio.core.people.MAX_FAVORITES
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.core.theme.DAY_MINUTES
import dev.jesusmolano.alisio.core.theme.accentsDiffer
import dev.jesusmolano.alisio.core.theme.dayStrip
import dev.jesusmolano.alisio.core.theme.sunMinutes
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.data.wallpaper.FolderImage
import dev.jesusmolano.alisio.data.wallpaper.WallpaperRotation
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.home.favoritePeople
import dev.jesusmolano.alisio.feature.home.rememberLiveApps
import dev.jesusmolano.alisio.feature.people.rememberContactPhoto
import dev.jesusmolano.alisio.feature.search.phoneHint
import dev.jesusmolano.alisio.feature.search.phoneIcon
import dev.jesusmolano.alisio.feature.search.phoneTitle
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.AppPickerSheet
import dev.jesusmolano.alisio.ui.components.Avatar
import dev.jesusmolano.alisio.ui.components.CalendarLiveIcon
import dev.jesusmolano.alisio.ui.components.ChoiceRow
import dev.jesusmolano.alisio.ui.components.ClockLiveIcon
import dev.jesusmolano.alisio.ui.components.DrawTrail
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.ProgressOutline
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.formatShortTime
import dev.jesusmolano.alisio.ui.components.liveLookFor
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.accentColor
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle

private val SampleIconSize = 40.dp
private val SampleGap = 12.dp
private val DrawSampleSize = 72.dp
private val StripHeight = 28.dp
private val StripMarkWidth = 2.dp
private val WallpaperThumbWidth = 72.dp
private val WallpaperThumbHeight = 128.dp
private val FavoriteTileSize = 44.dp

// Un tramo de la franja del color del día cada diez minutos: el paso se ve suave, no a saltos.
private const val STRIP_STEP_MINUTES = 10

// Fondos que se enseñan por delante: el de hoy y los dos siguientes.
private const val WALLPAPER_PREVIEW = 3

// Muestra de lo que suena para la tesela de iconos vivos: un poco más de la mitad.
private const val SAMPLE_PROGRESS = 0.6f

/**
 * Lo que la página Aspecto necesita para los fondos que rotan: elegir la carpeta (lo lanza la
 * actividad, que tiene el selector), pasar al siguiente y leer la carpeta para la vista previa.
 */
class WallpaperActions(val rotation: WallpaperRotation, val pickFolder: () -> Unit, val next: () -> Unit)

/**
 * Notas en el Inicio: un interruptor, porque su resultado ya se ve en la miniatura de arriba (la
 * nota de muestra bajo la hora). El secundario dice cómo se crean, que no se ve en ningún sitio.
 */
@Composable
internal fun NotesOption(context: SettingsContext) {
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_notes)),
        leadingIcon = R.drawable.ic_note,
        subtitle = stringResource(R.string.settings_notes_hint),
        subtitleLines = 2,
        checked = context.prefs.homeNotes,
        onClick = { context.settings.toggleHomeNotes() }
    )
}

/**
 * Personas en la barra: las caras elegidas tal como salen (un toque quita una, con deshacer) y la
 * fila que abre el selector de personas. Sin acceso a la agenda, la fila que lo pide.
 */
@Composable
internal fun FavoritesOptions(context: SettingsContext, home: HomeViewModel) {
    val people by home.people.collectAsStateWithLifecycle()
    val granted = context.access.contacts
    val chosen = remember(context.prefs.favoritePeople, people) { favoritePeople(context.prefs.favoritePeople, people) }
    val photos = home.photos.takeIf { context.prefs.contactPhotos }
    val undo = LocalUndo.current
    val view = LocalView.current
    var picking by remember { mutableStateOf(false) }
    val removed = stringResource(R.string.undo_favorite_removed)
    if (!granted) {
        // Se concede con un diálogo sobre Alisio: sin la marca de salir a una pantalla del sistema.
        ItemRow(
            title = AnnotatedString(stringResource(R.string.settings_needs_contacts)),
            leadingIcon = R.drawable.ic_contacts,
            trailing = stringResource(R.string.settings_allow),
            onClick = context.access.onContacts
        )
        return
    }
    if (chosen.isNotEmpty()) {
        AppGrid {
            chosen.forEach { person ->
                val photo = rememberContactPhoto(person, photos)
                AppTile(
                    label = person.name.substringBefore(' '),
                    description = stringResource(R.string.settings_favorite_remove, person.name),
                    interaction =
                        Modifier.clickable(role = Role.Button) {
                            val key = personKey(person.lookupKey)
                            context.settings.toggleFavorite(key)
                            undo.show(removed) { context.settings.toggleFavorite(key) }
                        }
                ) { Avatar(person.name, FavoriteTileSize, photo = photo) }
            }
        }
    }
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_favorites_pick)),
        leadingIcon = R.drawable.ic_person,
        subtitle = stringResource(R.string.settings_favorites_count, chosen.size, MAX_FAVORITES),
        trailing = stringResource(R.string.settings_choose),
        onClick = { picking = true }
    )
    if (picking) {
        val keys = context.prefs.favoritePeople
        AppPickerSheet(
            title = stringResource(R.string.settings_favorites_pick),
            items = people,
            key = { personKey(it.lookupKey) },
            label = Person::name,
            // Solo las fotos ya leídas: la lista no espera a la agenda para pintarse.
            icon = { photos?.cached(it) },
            avatar = true,
            searchHint = stringResource(R.string.picker_search_people),
            chosen = { personKey(it.lookupKey) in keys },
            onPick = { person ->
                // Ya hay cinco: no cabe otra hasta quitar una. Se dice al dedo, sin cerrar la hoja.
                if (!context.settings.toggleFavorite(personKey(person.lookupKey))) AlisioHaptics.limit(view)
            },
            onDismiss = { picking = false },
            multiple = true
        )
    }
}

/**
 * Dibujar la inicial: un interruptor (es un gesto) con su muestra al lado, hecha con lo real: la
 * letra de la app más usada del dock dibujada como se dibuja, y esa app.
 */
@Composable
internal fun DrawInitialOption(context: SettingsContext) {
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_draw_initial)),
        leadingIcon = R.drawable.ic_gesture,
        subtitle = stringResource(R.string.settings_draw_initial_hint),
        subtitleLines = 2,
        checked = context.prefs.drawInitial,
        onClick = { context.settings.toggleDrawInitial() }
    )
    if (!context.prefs.drawInitial) return
    // Las apps del dock primero: son las que más se usan y la muestra se reconoce de un vistazo.
    val sample =
        remember(context.apps, context.dock) {
            (context.dock.dockApps() + context.apps).filterNot { it.hidden }.firstOrNull { app ->
                val letter = initialOf(app.label)
                LETTER_TEMPLATES.any { it.first == letter }
            }
        } ?: return
    val letter = initialOf(sample.label) ?: return
    val template = remember(letter) { LETTER_TEMPLATES.first { it.first == letter }.second }
    val density = LocalDensity.current
    val side = with(density) { DrawSampleSize.toPx() }
    // La plantilla a escala de la muestra, con un margen para que el trazo no toque el borde.
    val points = remember(template, side) {
        template.map {
            Offset(
                side * (0.15f + it.x * 0.7f),
                side * (0.1f + it.y * 0.8f)
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = AlisioTokens.Edge - AlisioTokens.Outset, top = 4.dp, bottom = 8.dp)
            .clearAndSetSemantics { contentDescription = sample.label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(DrawSampleSize).background(AlisioTokens.Fill, AlisioTokens.CardShape)) {
            DrawTrail(points, Modifier.size(DrawSampleSize))
        }
        Spacer(Modifier.width(SampleGap))
        AppIcon(sample.icon, SampleIconSize)
        Spacer(Modifier.width(SampleGap))
        Text(
            sample.label,
            style = MaterialTheme.typography.bodyMedium,
            color = AlisioTokens.TextSecondary,
            maxLines = 1
        )
    }
}

/**
 * Iconos vivos: dos teselas con el calendario y el reloj del dock como quedarían (con el día de hoy
 * y la hora de ahora, en el estilo de iconos elegido) y como son.
 */
@Composable
internal fun LiveIconChoices(context: SettingsContext) {
    val prefs = context.prefs
    val live = rememberLiveApps(context.apps, enabled = true)
    val look = liveLookFor(prefs.iconStyle)
    val calendarApp = remember(context.apps, live) {
        context.apps.firstOrNull {
            it.component.packageName in
                live.calendar
        }
    }
    val clockApp = remember(context.apps, live) { context.apps.firstOrNull { it.component.packageName in live.clock } }
    val now = rememberNow()
    val locale = LocalConfiguration.current.locales[0]
    val weekday =
        remember(now.dayOfWeek, locale) {
            now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar {
                it.titlecase(locale)
            }
        }
    ChoiceRow(2) { tile ->
        listOf(true, false).forEach { on ->
            PreviewChoice(
                label = stringResource(if (on) R.string.settings_live_icons_on else R.string.settings_live_icons_off),
                selected = prefs.liveIcons == on,
                onClick = { context.settings.setLiveIcons(on) },
                modifier = tile
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(SampleGap)) {
                    if (on || calendarApp == null) {
                        CalendarLiveIcon(now.dayOfMonth, weekday, SampleIconSize, look)
                    } else {
                        AppIcon(calendarApp.icon, SampleIconSize)
                    }
                    if (on || clockApp == null) {
                        ClockLiveIcon(now.hour, now.minute, SampleIconSize, look)
                    } else {
                        AppIcon(clockApp.icon, SampleIconSize)
                    }
                    // Lo que suena, con su anillo: la primera app del dock hace de muestra.
                    (context.dock.dockApps().firstOrNull() ?: context.apps.firstOrNull())?.let { app ->
                        Box(Modifier.size(SampleIconSize)) {
                            AppIcon(app.icon, SampleIconSize)
                            if (on) ProgressOutline(SAMPLE_PROGRESS, SampleIconSize)
                        }
                    }
                }
            }
        }
    }
}

/**
 * El color del día: el interruptor y, encendido, el tono de la noche (los cuatro de la paleta) y la
 * franja de las 24 horas con el acento de cada media hora, la hora de ahora marcada y la salida y la
 * puesta del sol de este sitio debajo.
 */
@Composable
internal fun DayAccentOptions(context: SettingsContext, home: HomeViewModel) {
    val prefs = context.prefs
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_day_accent)),
        leadingIcon = R.drawable.ic_twilight,
        subtitle = stringResource(R.string.settings_day_accent_hint),
        subtitleLines = 2,
        checked = prefs.dayAccent,
        onClick = { context.settings.toggleDayAccent() }
    )
    if (!prefs.dayAccent) return
    val glance by home.glance.collectAsStateWithLifecycle()
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val latitude = prefs.weatherPlace?.latitude ?: glance.location?.latitude
    val longitude = prefs.weatherPlace?.longitude ?: glance.location?.longitude
    val sun =
        remember(now.toLocalDate(), latitude, longitude) { sunMinutes(now.toLocalDate(), zone, latitude, longitude) }
    // El acento de día es el elegido arriba, sin el paso del día encima.
    val day = accentColorOf(prefs)
    val strip =
        remember(day, prefs.nightAccent, sun) {
            val night = AlisioTokens.Accents.getOrElse(prefs.nightAccent) { AlisioTokens.Accents.first() }
            dayStrip(day.toArgb(), night.toArgb(), sun, AlisioTokens.Background.toArgb(), step = STRIP_STEP_MINUTES)
        }
    Note(stringResource(R.string.settings_day_accent_night))
    Row(Modifier.padding(horizontal = 12.dp - (AlisioTokens.MinTouch - AccentSwatchSize) / 2)) {
        AlisioTokens.Accents.forEachIndexed { index, color ->
            val distinct = accentsDiffer(color.toArgb(), day.toArgb())
            AccentSwatch(
                name = stringResource(accentName(index)),
                chosen = index == prefs.nightAccent,
                onClick = { if (distinct) context.settings.setNightAccent(index) }
            ) { Box(Modifier.size(AccentSwatchSize).background(color, CircleShape)) }
        }
    }
    val minute = now.hour * 60 + now.minute
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AlisioTokens.Edge - AlisioTokens.Outset, vertical = 8.dp)
            .height(StripHeight)
            .clip(AlisioTokens.RowShape)
            .clearAndSetSemantics {}
    ) {
        val step = size.width / strip.size
        strip.forEachIndexed { index, color ->
            drawRect(Color(color), topLeft = Offset(index * step, 0f), size = Size(step + 1f, size.height))
        }
        val x = size.width * minute / DAY_MINUTES
        drawRect(
            AlisioTokens.Background,
            topLeft = Offset(x - StripMarkWidth.toPx() / 2, 0f),
            size = Size(StripMarkWidth.toPx(), size.height)
        )
    }
    val today = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val sunrise = formatShortTime(today + sun.sunrise * 60_000L, prefs.clockFormat)
    val sunset = formatShortTime(today + sun.sunset * 60_000L, prefs.clockFormat)
    Note(stringResource(R.string.settings_day_accent_sun, sunrise, sunset))
}

/** El acento elegido en la paleta o el propio, sin el color del día: es el tono de día. */
private fun accentColorOf(prefs: Prefs): Color = accentColor(prefs.accent, prefs.customAccent)

/**
 * Fondos que rotan: la carpeta (elegirla enciende el cambio diario), el interruptor y la vista
 * previa con el fondo de hoy y los dos siguientes, tal como van a salir. «Pasar al siguiente» lo
 * cambia ya.
 */
@Composable
internal fun WallpaperRotationOptions(context: SettingsContext, wallpaper: WallpaperActions) {
    val prefs = context.prefs
    val folder = prefs.wallpaperFolder?.takeIf { wallpaper.rotation.hasAccess(it) }
    val name by produceState<String?>(null, folder) { value = folder?.let { wallpaper.rotation.folderName(it) } }
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_wallpaper_folder)),
        leadingIcon = R.drawable.ic_folder,
        subtitle = if (folder == null) stringResource(R.string.settings_wallpaper_folder_none) else name,
        titleMark = R.drawable.ic_open_in_new,
        trailing = stringResource(if (folder == null) R.string.settings_choose else R.string.settings_change),
        onClick = wallpaper.pickFolder
    )
    if (folder == null) return
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_wallpaper_rotate)),
        // El reloj, y no el mismo dibujo de «Fondo de pantalla» que va justo encima: esta fila es cuándo cambia.
        leadingIcon = R.drawable.ic_schedule,
        checked = prefs.wallpaperRotation,
        onClick = { context.settings.toggleWallpaperRotation() }
    )
    if (!prefs.wallpaperRotation) return
    // La miniatura cambia al pasar al siguiente: se vuelve a leer cuando cambia el fondo puesto.
    var shownAt by remember { mutableIntStateOf(0) }
    val images by produceState(emptyList<FolderImage>(), folder) { value = wallpaper.rotation.images(folder) }
    val current by produceState<Int?>(null, folder, shownAt) { value = wallpaper.rotation.currentIndex() }
    val density = LocalDensity.current
    val thumbPx = with(density) { WallpaperThumbHeight.roundToPx() }
    if (images.isEmpty()) {
        Note(stringResource(R.string.settings_wallpaper_empty))
        return
    }
    val start = current ?: 0
    val upcoming = (0 until minOf(WALLPAPER_PREVIEW, images.size)).map { images[(start + it) % images.size] }
    Row(
        Modifier.padding(horizontal = AlisioTokens.Edge - AlisioTokens.Outset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(SampleGap)
    ) {
        upcoming.forEachIndexed { index, image ->
            val thumb by produceState<Bitmap?>(null, image.uri) {
                value =
                    wallpaper.rotation.thumbnail(image.uri, thumbPx)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(WallpaperThumbWidth, WallpaperThumbHeight)
                        .clip(AlisioTokens.CardShape)
                        .background(AlisioTokens.Fill)
                        .semantics { contentDescription = image.name }
                ) {
                    thumb?.let {
                        Image(
                            bitmap = remember(it) { it.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(WallpaperThumbWidth, WallpaperThumbHeight)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        stringResource(
                            when (index) {
                                0 -> R.string.settings_wallpaper_today
                                1 -> R.string.settings_wallpaper_tomorrow
                                else -> R.string.settings_wallpaper_after
                            }
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == 0) AlisioTokens.Text else AlisioTokens.TextSecondary
                )
            }
        }
    }
    TextAction(
        text = stringResource(R.string.settings_wallpaper_next),
        onClick = {
            wallpaper.next()
            shownAt++
        },
        modifier = Modifier.padding(start = AlisioTokens.Edge - AlisioTokens.Outset)
    )
}

/**
 * Órdenes del teléfono en la barra: un interruptor y, debajo, la fila tal como saldría al escribir
 * «encender linterna», con su dibujo y su marca de Enter.
 */
@Composable
internal fun PhoneCommandsOption(context: SettingsContext) {
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_phone_commands)),
        leadingIcon = R.drawable.ic_flashlight,
        subtitle = stringResource(R.string.settings_phone_commands_hint),
        subtitleLines = 2,
        checked = context.prefs.phoneCommands,
        onClick = { context.settings.togglePhoneCommands() }
    )
    if (!context.prefs.phoneCommands) return
    val sample = PhoneCommand.Torch(Switch.ON)
    val resources = LocalResources.current
    Box(Modifier.clearAndSetSemantics {}) {
        ItemRow(
            title = AnnotatedString(resources.phoneTitle(sample)),
            leadingIcon = phoneIcon(sample),
            subtitle = resources.phoneHint(sample),
            enterTarget = true,
            onClick = {}
        )
    }
}
