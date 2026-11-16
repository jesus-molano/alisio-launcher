package dev.jesusmolano.alisio.feature.home

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.glance.CalendarEvent
import dev.jesusmolano.alisio.core.glance.GlanceItem
import dev.jesusmolano.alisio.core.glance.LOW_BATTERY_PERCENT
import dev.jesusmolano.alisio.core.glance.SunEventKind
import dev.jesusmolano.alisio.core.glance.WeatherKind
import dev.jesusmolano.alisio.core.glance.alarmToShow
import dev.jesusmolano.alisio.core.glance.nextEvent
import dev.jesusmolano.alisio.core.glance.nextSunEvent
import dev.jesusmolano.alisio.core.glance.screenTimeToShow
import dev.jesusmolano.alisio.core.glance.showBattery
import dev.jesusmolano.alisio.data.glance.Coordinates
import dev.jesusmolano.alisio.data.glance.GlanceData
import dev.jesusmolano.alisio.data.glance.NextAlarm
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.appmenu.usageText
import dev.jesusmolano.alisio.feature.search.searchWeb
import dev.jesusmolano.alisio.ui.components.formatShortTime
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import java.time.ZoneId
import kotlin.math.roundToInt

private const val TAG = "Glance"

// La fecha y las piezas caben en tres renglones como mucho: un cuarto ya compite con la hora.
private const val GLANCE_MAX_LINES = 3

// Un título largo de evento no se come la línea: se corta y deja sitio a la hora del evento.
private val EventTitleMaxWidth = 180.dp

// Lo que separa una pieza de la siguiente, repartido a sus dos lados: así el toque de cada una
// llega hasta la mitad del hueco y la primera cae donde empieza la fecha. La fecha usa los mismos.
internal val PieceStart = 6.dp
internal val PieceEnd = 10.dp
private val PieceIconGap = 6.dp

// Valores de muestra para la miniatura de los ajustes: enseñan la pieza aunque hoy no diga nada.
private const val SAMPLE_ALARM_HOURS = 8
private const val SAMPLE_EVENT_HOURS = 3
private const val SAMPLE_SUN_HOURS = 2
private const val SAMPLE_BATTERY = 78
private const val SAMPLE_TEMPERATURE = 18
private const val SAMPLE_SCREEN_MILLIS = 85 * 60_000L
private const val HOUR_MILLIS = 3_600_000L

/**
 * Una pieza de la línea lista para pintar: su dibujo, lo que se lee, lo que oye TalkBack y su toque.
 *
 * @param detail lo que sigue a [text] y no se puede cortar (la hora de un evento): [text] se acorta
 *   antes que él.
 */
internal class GlancePiece(
    val item: GlanceItem,
    @param:DrawableRes val icon: Int,
    val text: String,
    val spoken: String,
    val action: String,
    val detail: String? = null,
    val onClick: () -> Unit
) {
    /** Todo lo que se lee, de una vez: para los secundarios de los ajustes. */
    val fullText: String get() = text + detail.orEmpty()
}

/**
 * Las piezas que tienen algo que decir ahora, en el orden de [Prefs.glance]. Cada una se oculta sola
 * cuando no tiene nada (sin alarma en 12 h, sin permiso, sin datos guardados).
 *
 * @param now la hora de este minuto: decide qué alarma, evento y sol cuentan.
 * @param sample para la miniatura de los ajustes: una pieza encendida sin nada que decir se enseña
 *   con un valor de muestra, para ver dónde y cómo queda.
 */
@Composable
internal fun glancePieces(prefs: Prefs, data: GlanceData, now: Long, sample: Boolean = false): List<GlancePiece> {
    val context = LocalContext.current
    return prefs.glance.mapNotNull { item ->
        when (item) {
            GlanceItem.ALARM -> alarmPiece(prefs, data.alarm, now, sample, context)
            GlanceItem.EVENT -> eventPiece(prefs, data.events, now, sample, context)
            GlanceItem.WEATHER -> weatherPiece(prefs, data, sample, context)
            GlanceItem.BATTERY -> batteryPiece(prefs, data, sample, context)
            GlanceItem.SUN -> sunPiece(prefs, data, now, sample, context)
            GlanceItem.SCREEN_TIME -> screenPiece(data, sample, context)
        }
    }
}

@Composable
private fun alarmPiece(prefs: Prefs, alarm: NextAlarm?, now: Long, sample: Boolean, context: Context): GlancePiece? {
    val at = alarmToShow(alarm?.at, now) ?: (now + SAMPLE_ALARM_HOURS * HOUR_MILLIS).takeIf { sample } ?: return null
    val time = formatShortTime(at, prefs.clockFormat)
    return GlancePiece(
        item = GlanceItem.ALARM,
        icon = R.drawable.ic_alarm,
        text = time,
        spoken = stringResource(R.string.glance_alarm_spoken, time),
        action = stringResource(R.string.glance_alarm_action)
    ) { context.openAlarm(alarm) }
}

@Composable
private fun eventPiece(
    prefs: Prefs,
    events: List<CalendarEvent>,
    now: Long,
    sample: Boolean,
    context: Context
): GlancePiece? {
    val event =
        nextEvent(events, now)
            ?: CalendarEvent(
                id = -1,
                title = stringResource(R.string.glance_sample_event),
                begin = now + SAMPLE_EVENT_HOURS * HOUR_MILLIS,
                end = now + (SAMPLE_EVENT_HOURS + 1) * HOUR_MILLIS,
                allDay = false,
                declined = false
            ).takeIf { sample }
            ?: return null
    val title = event.title.ifBlank { stringResource(R.string.glance_event_untitled) }
    val time = formatShortTime(event.begin, prefs.clockFormat)
    return GlancePiece(
        item = GlanceItem.EVENT,
        icon = R.drawable.ic_event,
        text = title,
        spoken = stringResource(R.string.glance_event_spoken, title, time),
        action = stringResource(R.string.glance_event_action),
        detail = stringResource(R.string.glance_event_time, time)
    ) { context.openEvent(event) }
}

@Composable
private fun weatherPiece(prefs: Prefs, data: GlanceData, sample: Boolean, context: Context): GlancePiece? {
    val reading = data.weather
    if (reading == null && !sample) return null
    val temperature = reading?.temperature?.roundToInt() ?: SAMPLE_TEMPERATURE
    val kind = reading?.kind ?: WeatherKind.PARTLY_CLOUDY
    val day = reading?.day ?: true
    val condition = stringResource(kind.label())
    val range =
        if (reading?.max != null && reading.min != null) {
            stringResource(R.string.glance_weather_range, reading.max.roundToInt(), reading.min.roundToInt())
        } else {
            ""
        }
    val query = stringResource(R.string.glance_weather_query, prefs.weatherPlace?.name.orEmpty()).trim()
    return GlancePiece(
        item = GlanceItem.WEATHER,
        icon = kind.icon(day),
        text = stringResource(R.string.glance_temperature, temperature),
        spoken = stringResource(R.string.glance_weather_spoken, temperature, condition, range).trim(),
        action = stringResource(R.string.glance_weather_action)
    ) { context.openWeather(prefs, query) }
}

@Composable
private fun batteryPiece(prefs: Prefs, data: GlanceData, sample: Boolean, context: Context): GlancePiece? {
    val battery = data.battery
    val percent: Int
    val charging: Boolean
    when {
        battery != null && (sample || showBattery(battery.percent, battery.charging, prefs.batteryShow)) -> {
            percent = battery.percent
            charging = battery.charging
        }

        sample -> {
            percent = SAMPLE_BATTERY
            charging = false
        }

        else -> return null
    }
    val text = stringResource(R.string.glance_battery, percent)
    return GlancePiece(
        item = GlanceItem.BATTERY,
        icon =
            when {
                charging -> R.drawable.ic_battery_charging
                percent < LOW_BATTERY_PERCENT -> R.drawable.ic_battery_alert
                else -> R.drawable.ic_battery
            },
        text = text,
        spoken =
            stringResource(
                if (charging) R.string.glance_battery_charging_spoken else R.string.glance_battery_spoken,
                text
            ),
        action = stringResource(R.string.glance_battery_action)
    ) { context.openBatteryUsage() }
}

@Composable
private fun sunPiece(prefs: Prefs, data: GlanceData, now: Long, sample: Boolean, context: Context): GlancePiece? {
    val place = prefs.weatherPlace?.let { Coordinates(it.latitude, it.longitude) } ?: data.location
    val event =
        remember(place, now) { place?.let { nextSunEvent(now, it.latitude, it.longitude, ZoneId.systemDefault()) } }
    val kind = event?.kind ?: SunEventKind.SUNSET.takeIf { sample } ?: return null
    val at = event?.at ?: (now + SAMPLE_SUN_HOURS * HOUR_MILLIS)
    val time = formatShortTime(at, prefs.clockFormat)
    val text = stringResource(
        if (kind ==
            SunEventKind.SUNSET
        ) {
            R.string.glance_sunset
        } else {
            R.string.glance_sunrise
        },
        time
    )
    val query = stringResource(R.string.glance_weather_query, prefs.weatherPlace?.name.orEmpty()).trim()
    return GlancePiece(
        item = GlanceItem.SUN,
        icon = R.drawable.ic_twilight,
        text = text,
        spoken = text,
        action = stringResource(R.string.glance_weather_action)
    ) { context.openWeather(prefs, query) }
}

@Composable
private fun screenPiece(data: GlanceData, sample: Boolean, context: Context): GlancePiece? {
    val millis = screenTimeToShow(data.screenMillis) ?: SAMPLE_SCREEN_MILLIS.takeIf { sample } ?: return null
    val text = usageText(millis)
    return GlancePiece(
        item = GlanceItem.SCREEN_TIME,
        icon = R.drawable.ic_smartphone,
        text = text,
        spoken = stringResource(R.string.glance_screen_spoken, text),
        action = stringResource(R.string.glance_screen_action)
    ) { context.openScreenTime() }
}

/**
 * La línea bajo la hora: la fecha y piezas de texto con su dibujo pequeño, con la letra de la fecha
 * y sin caja (docs/DISENO.md §6.1). Pasa al renglón siguiente si no cabe, hasta tres; lo que no
 * cabe se queda fuera, nunca se encoge.
 *
 * @param interactive en la miniatura de los ajustes es un dibujo: ni toques ni nada que leer.
 * @param leading lo que abre la línea: la fecha en el Inicio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlanceLine(
    pieces: List<GlancePiece>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    leading: @Composable () -> Unit = {}
) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium).onWallpaper()
    val noRipple = remember { MutableInteractionSource() }
    // Cada pieza (y la fecha que abre la línea) lleva su media separación delante: la línea se
    // adelanta eso para que la tinta de la primera caiga en el filo, como la de la hora (ClockText).
    FlowRow(
        modifier.offset(x = -PieceStart),
        horizontalArrangement = Arrangement.Start,
        maxLines = GLANCE_MAX_LINES
    ) {
        leading()
        pieces.forEach { piece ->
            Row(
                Modifier
                    .then(
                        if (interactive) {
                            Modifier
                                .clickable(
                                    interactionSource = noRipple,
                                    indication = null,
                                    onClickLabel = piece.action,
                                    role = Role.Button,
                                    onClick = piece.onClick
                                )
                                // TalkBack lee la frase entera («Alarma a las 7:30»), no el dibujo y la cifra.
                                .clearAndSetSemantics { contentDescription = piece.spoken }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        }
                    )
                    .heightIn(min = AlisioTokens.MinTouch)
                    .padding(start = PieceStart, end = PieceEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(piece.icon),
                    contentDescription = null,
                    tint = AlisioTokens.Text,
                    modifier = Modifier.size(AlisioTokens.GlanceIconSize)
                )
                Spacer(Modifier.width(PieceIconGap))
                Text(
                    text = piece.text,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (piece.detail != null) Modifier.widthIn(max = EventTitleMaxWidth) else Modifier
                )
                // La hora del evento va aparte para que nunca se corte: lo que se acorta es el título.
                piece.detail?.let { Text(text = it, style = style, maxLines = 1) }
            }
        }
    }
}

/** El nombre de cada estado del cielo, para TalkBack y para la tesela de los ajustes. */
internal fun WeatherKind.label(): Int = when (this) {
    WeatherKind.CLEAR -> R.string.weather_clear
    WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly
    WeatherKind.CLOUDY -> R.string.weather_cloudy
    WeatherKind.FOG -> R.string.weather_fog
    WeatherKind.DRIZZLE -> R.string.weather_drizzle
    WeatherKind.RAIN -> R.string.weather_rain
    WeatherKind.SNOW -> R.string.weather_snow
    WeatherKind.STORM -> R.string.weather_storm
}

@DrawableRes
internal fun WeatherKind.icon(day: Boolean): Int = when (this) {
    WeatherKind.CLEAR -> if (day) R.drawable.ic_weather_clear else R.drawable.ic_weather_clear_night
    WeatherKind.PARTLY_CLOUDY -> R.drawable.ic_weather_partly
    WeatherKind.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherKind.FOG -> R.drawable.ic_weather_fog
    WeatherKind.DRIZZLE -> R.drawable.ic_weather_drizzle
    WeatherKind.RAIN -> R.drawable.ic_weather_rain
    WeatherKind.SNOW -> R.drawable.ic_weather_snow
    WeatherKind.STORM -> R.drawable.ic_weather_storm
}

/**
 * Abre la pantalla del reloj que enseña la alarma. Desde Android 14 quien manda un `PendingIntent`
 * tiene que ceder su permiso de abrir actividades: Alisio está a la vista y lo cede. Si el reloj no
 * lo acepta, se abre la app de reloj sin más.
 */
private fun Context.openAlarm(alarm: NextAlarm?) {
    val show = alarm?.show
    if (show != null) {
        try {
            show.send(this, 0, null, null, null, null, pendingIntentOptions())
            return
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "La pantalla de la alarma ya no existe", e)
        }
    }
    openClockApp()
}

@Suppress("DEPRECATION")
internal fun pendingIntentOptions() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    // `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` pasa a obsoleto en API 36 en favor de un modo que no
    // existe antes: este vale en todas las versiones a partir de la 34.
    ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        .toBundle()
} else {
    null
}

/** El evento en el calendario, en su repetición de hoy. Uno de muestra solo abre el calendario. */
private fun Context.openEvent(event: CalendarEvent) {
    val view =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
    // Sin una app que abra el evento suelto (un calendario que solo sincroniza), al menos el calendario.
    if (event.id >= 0 && open(view)) return
    open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
}

/** La app del tiempo elegida o, sin ella, la búsqueda web de siempre con «tiempo» y la ciudad. */
private fun Context.openWeather(prefs: Prefs, query: String) {
    val app = prefs.weatherApp?.let { packageManager.getLaunchIntentForPackage(it) }
    if (app != null && open(app)) return
    searchWeb(prefs.webSearchPackage, query)
}

private fun Context.openBatteryUsage() {
    open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
}

// Bienestar digital no tiene una acción pública: se prueba la pantalla de Samsung, la de Google y,
// si no está ninguna, los ajustes del sistema.
private val WellbeingScreens =
    listOf(
        ComponentName("com.samsung.android.forest", "com.samsung.android.forest.launcher.LauncherActivity"),
        ComponentName(
            "com.google.android.apps.wellbeing",
            "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
        )
    )

private fun Context.openScreenTime() {
    if (WellbeingScreens.any { open(Intent().setComponent(it)) }) return
    open(Intent(android.provider.Settings.ACTION_SETTINGS))
}

private fun Context.open(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    Log.w(TAG, "No se ha podido abrir $intent", e)
    false
}
