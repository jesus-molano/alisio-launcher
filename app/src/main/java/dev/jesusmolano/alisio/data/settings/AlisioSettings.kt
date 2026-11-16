package dev.jesusmolano.alisio.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jesusmolano.alisio.core.backup.PrefValue
import dev.jesusmolano.alisio.core.backup.acceptedPrefs
import dev.jesusmolano.alisio.core.dock.DEFAULT_DOCK_SLOTS
import dev.jesusmolano.alisio.core.dock.dockLabelsShown
import dev.jesusmolano.alisio.core.dock.normalizeDockSlots
import dev.jesusmolano.alisio.core.glance.BatteryShow
import dev.jesusmolano.alisio.core.glance.DEFAULT_GLANCE
import dev.jesusmolano.alisio.core.glance.GlanceItem
import dev.jesusmolano.alisio.core.glance.Place
import dev.jesusmolano.alisio.core.glance.formatGlance
import dev.jesusmolano.alisio.core.glance.parseGlance
import dev.jesusmolano.alisio.core.glance.raiseGlance as raisedGlance
import dev.jesusmolano.alisio.core.glance.toggleGlance as toggledGlance
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.pause.DEFAULT_PAUSE_SECONDS
import dev.jesusmolano.alisio.core.pause.normalizePauseSeconds
import dev.jesusmolano.alisio.core.people.formatFavorites
import dev.jesusmolano.alisio.core.people.parseFavorites
import dev.jesusmolano.alisio.core.people.toggleFavorite as toggledFavorites
import dev.jesusmolano.alisio.core.theme.CUSTOM_ACCENT
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.core.time.ClockFormat
import dev.jesusmolano.alisio.core.time.ClockSize
import dev.jesusmolano.alisio.core.trace.TraceRail
import java.io.IOException
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Lo que se elige en los ajustes de Alisio. Cada función nueva añade aquí sus opciones.
 *
 * @param accent índice del color de acento en la paleta del tema, o [CUSTOM_ACCENT] para [customAccent].
 * @param customAccent el color propio (ARGB) elegido a mano; se recuerda aunque se vuelva a la paleta.
 * @param font familias de letra de toda la interfaz.
 * @param assistantPackage app de IA a la que la barra manda las preguntas.
 * @param webSearchPackage app que hace la búsqueda web; `null` es la que elija el sistema.
 * @param nowEnabled si el Inicio enseña la sugerencia de Ahora.
 * @param playerEnabled si el Inicio enseña el reproductor cuando hay algo sonando o en pausa.
 * @param unreadDots si el dock marca con un punto las apps con notificaciones sin leer.
 * @param unreadMuted paquetes cuyo punto está apagado.
 * @param dockGestures si deslizar hacia arriba sobre un icono del dock abre su destino secundario.
 * @param zonesPinch si pellizcar el Inicio abre Zonas; apagado, se llega desde la barra.
 * @param zonesLabels si Zonas enseña el nombre debajo de cada icono.
 * @param clipboardActions si la barra mira el portapapeles al abrirse para ofrecer su acción.
 * @param lockDoubleTap si dos toques en una zona vacía del Inicio bloquean la pantalla. Solo tiene
 *   efecto con el servicio de accesibilidad activado.
 * @param notificationsSwipe si deslizar hacia abajo en una zona vacía del Inicio baja las
 *   notificaciones. Solo tiene efecto con el servicio de accesibilidad activado.
 * @param pausedApps paquetes que Alisio abre tras una pausa; vacío, la pausa está apagada.
 * @param pauseSeconds cuánto dura esa pausa.
 * @param contactPhotos si el avatar de una persona es su foto de la agenda; apagado, o si no tiene
 *   foto, son sus iniciales.
 * @param traceRail cuándo se ve el índice de Trazo; con [TraceRail.NEVER] no hay índice.
 * @param dockSlots cuántos huecos enseña el dock (0 a 8). Los que quedan fuera no se borran.
 * @param dockLabelsChoice lo elegido para los nombres del dock; `null` si nunca se eligió, y
 *   entonces [dockLabels] depende del número de huecos.
 * @param clockFormat 12 o 24 h, o lo que diga el sistema; [clockSize] el tamaño de la hora.
 * @param glance las piezas de la línea bajo la hora, en su orden.
 * @param batteryShow cuándo tiene algo que decir la batería de esa línea.
 * @param weatherPlace la ciudad elegida para el tiempo; `null` es la ubicación aproximada.
 * @param weatherApp app que abre tocar el tiempo; `null` lo busca en la web.
 * @param onboardingSkipped la bienvenida se descartó con «Ahora no»: Alisio se usa sin ser la app de inicio.
 * @param liveNow si Ahora enseña lo que está en curso (un viaje, un temporizador) antes que su
 *   sugerencia (docs/IDEAS.md §2.7). Apagado de serie: no se ha probado en el S26.
 * @param pauseStats si la pausa dice cuántas veces se ha abierto la app hoy y cuánto tiempo
 *   (docs/IDEAS.md §2.4). Necesita el acceso a uso.
 * @param homeNotes si las notas («+ texto» en la barra o compartidas desde otra app) salen bajo la hora.
 * @param phoneCommands si la barra entiende órdenes para el teléfono («linterna», «brillo 40»).
 * @param drawInitial si dibujar una letra en una zona vacía del Inicio abre la app o la persona más
 *   usada con esa inicial.
 * @param liveIcons si el calendario, el reloj y la app que suena enseñan en el dock su dato vivo.
 *   Apagado de serie: cambia el icono de marca solo en el dock, y la app debe reconocerse igual en
 *   Zonas, Trazo y la barra; se enciende viéndolo en su tesela.
 * @param dayAccent si el acento pasa del elegido de día a [nightAccent] de noche, con el sol.
 * @param nightAccent el tono de la noche, un índice de la paleta.
 * @param wallpaperRotation si el fondo cambia cada día con una imagen de [wallpaperFolder].
 * @param wallpaperFolder la carpeta elegida (URI de árbol con permiso persistente); `null` sin elegir.
 * @param favoritePeople las personas sobre el dock, por su clave de persona y en su orden.
 */
data class Prefs(
    val iconStyle: IconStyle = IconStyle.Color,
    val accent: Int = 0,
    val customAccent: Int? = null,
    val font: FontChoice = FontChoice.PAIR,
    val wallpaperDim: WallpaperDim = WallpaperDim.MEDIUM,
    val assistantPackage: String = DEFAULT_ASSISTANT,
    val webSearchPackage: String? = null,
    val nowEnabled: Boolean = true,
    val playerEnabled: Boolean = true,
    val unreadDots: Boolean = true,
    val unreadMuted: Set<String> = DEFAULT_UNREAD_MUTED,
    val dockGestures: Boolean = true,
    val zonesPinch: Boolean = true,
    val zonesLabels: Boolean = true,
    val clipboardActions: Boolean = true,
    val lockDoubleTap: Boolean = true,
    val notificationsSwipe: Boolean = true,
    val pausedApps: Set<String> = DEFAULT_PAUSED_APPS,
    val pauseSeconds: Int = DEFAULT_PAUSE_SECONDS,
    val contactPhotos: Boolean = true,
    val traceRail: TraceRail = TraceRail.ALWAYS,
    val dockSlots: Int = DEFAULT_DOCK_SLOTS,
    val dockLabelsChoice: Boolean? = null,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val clockSize: ClockSize = ClockSize.LARGE,
    val glance: List<GlanceItem> = DEFAULT_GLANCE,
    val batteryShow: BatteryShow = BatteryShow.LOW,
    val weatherPlace: Place? = null,
    val weatherApp: String? = null,
    val onboardingSkipped: Boolean = false,
    val liveNow: Boolean = false,
    val pauseStats: Boolean = true,
    val homeNotes: Boolean = true,
    val phoneCommands: Boolean = true,
    // Apagado de serie: reconocer una letra abre una app al momento, y un arco o un deslizar curvo
    // podría tomarse por letra (IDEAS §0, que no se abra nada sin querer).
    val drawInitial: Boolean = false,
    val liveIcons: Boolean = false,
    val dayAccent: Boolean = false,
    val nightAccent: Int = DEFAULT_NIGHT_ACCENT,
    val wallpaperRotation: Boolean = false,
    val wallpaperFolder: String? = null,
    val favoritePeople: List<String> = emptyList()
) {
    /** Si el dock enseña los nombres bajo los iconos ([dockLabelsShown]). */
    val dockLabels: Boolean get() = dockLabelsShown(dockLabelsChoice, dockSlots)

    /** El tiempo o el sol están encendidos sin una ciudad fijada: dependen de la ubicación aproximada. */
    fun usesDeviceLocation(): Boolean =
        weatherPlace == null && (GlanceItem.WEATHER in glance || GlanceItem.SUN in glance)
}

/** X y YouTube llevan pausa de entrada (§3 «Opcional»); si no están instaladas, no pintan nada. */
val DEFAULT_PAUSED_APPS: Set<String> = setOf("com.twitter.android", "com.google.android.youtube")

/** X viene con el punto apagado (§5.5): lo suyo rara vez es algo pendiente de leer. */
val DEFAULT_UNREAD_MUTED: Set<String> = setOf("com.twitter.android")

/** Ámbar: un tono cálido para la noche frente al turquesa de día que viene de serie. */
const val DEFAULT_NIGHT_ACCENT = 2

/** Claude, el asistente por defecto (§5.3). */
const val DEFAULT_ASSISTANT = "com.anthropic.claude"

/** Cuánto se oscurece el fondo de pantalla en el Inicio: [factor] multiplica la opacidad del velo. */
enum class WallpaperDim(val factor: Float) {
    LIGHT(0.45f),
    MEDIUM(1f),
    STRONG(1.6f)
}

/** Preferencias simples de Alisio (DataStore, §4 del plan). */
class AlisioSettings(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    /** `null` hasta que se lee del disco, para no pintar el Inicio con opciones que no son las elegidas. */
    val prefs: StateFlow<Prefs?> =
        store.data
            // Un archivo de preferencias ilegible no puede dejar el Inicio sin iconos: se usan los valores por defecto.
            .catch { emit(emptyPreferences()) }
            .map { stored ->
                try {
                    read(stored)
                } catch (e: ClassCastException) {
                    // Un valor con otro tipo del que se lee (de un archivo de otra versión) cerraría
                    // Alisio en cada arranque: se borra y, mientras, valen los valores por defecto.
                    Log.w(TAG, "Hay preferencias con un tipo que no es el suyo", e)
                    dropMistyped()
                    read(emptyPreferences())
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun read(stored: Preferences): Prefs = Prefs(
        iconStyle = IconStyle.parse(stored[ICON_STYLE]),
        accent = stored[ACCENT] ?: 0,
        customAccent = stored[CUSTOM_ACCENT_COLOR],
        font = FontChoice.parse(stored[FONT]),
        dockLabelsChoice = stored[DOCK_LABELS],
        wallpaperDim =
            WallpaperDim.entries.getOrElse(stored[WALLPAPER_DIM] ?: WallpaperDim.MEDIUM.ordinal) {
                WallpaperDim.MEDIUM
            },
        assistantPackage = stored[ASSISTANT] ?: DEFAULT_ASSISTANT,
        webSearchPackage = stored[WEB_SEARCH],
        nowEnabled = stored[NOW_ENABLED] ?: true,
        playerEnabled = stored[PLAYER_ENABLED] ?: true,
        unreadDots = stored[UNREAD_DOTS] ?: true,
        unreadMuted = stored[UNREAD_MUTED] ?: DEFAULT_UNREAD_MUTED,
        dockGestures = stored[DOCK_GESTURES] ?: true,
        zonesPinch = stored[ZONES_PINCH] ?: true,
        zonesLabels = stored[ZONES_LABELS] ?: true,
        clipboardActions = stored[CLIPBOARD_ACTIONS] ?: true,
        lockDoubleTap = stored[LOCK_DOUBLE_TAP] ?: true,
        notificationsSwipe = stored[NOTIFICATIONS_SWIPE] ?: true,
        pausedApps = stored[PAUSED_APPS] ?: DEFAULT_PAUSED_APPS,
        pauseSeconds = normalizePauseSeconds(stored[PAUSE_SECONDS_KEY]),
        contactPhotos = stored[CONTACT_PHOTOS] ?: true,
        traceRail = TraceRail.parse(stored[TRACE_RAIL]),
        dockSlots = normalizeDockSlots(stored[DOCK_SLOTS]),
        clockFormat = ClockFormat.parse(stored[CLOCK_FORMAT]),
        clockSize = ClockSize.parse(stored[CLOCK_SIZE]),
        glance = parseGlance(stored[GLANCE]),
        batteryShow = BatteryShow.parse(stored[BATTERY_SHOW]),
        weatherPlace = storedPlace(stored),
        weatherApp = stored[WEATHER_APP],
        onboardingSkipped = stored[ONBOARDING_SKIPPED] ?: false,
        liveNow = stored[LIVE_NOW] ?: false,
        pauseStats = stored[PAUSE_STATS] ?: true,
        homeNotes = stored[HOME_NOTES] ?: true,
        phoneCommands = stored[PHONE_COMMANDS] ?: true,
        drawInitial = stored[DRAW_INITIAL] ?: false,
        liveIcons = stored[LIVE_ICONS] ?: false,
        dayAccent = stored[DAY_ACCENT] ?: false,
        nightAccent = stored[NIGHT_ACCENT] ?: DEFAULT_NIGHT_ACCENT,
        wallpaperRotation = stored[WALLPAPER_ROTATION] ?: false,
        wallpaperFolder = stored[WALLPAPER_FOLDER],
        favoritePeople = parseFavorites(stored[FAVORITE_PEOPLE])
    )

    /** Borra las claves guardadas con un tipo distinto del que Alisio espera para ellas. */
    private fun dropMistyped() = write { prefs ->
        prefs.asMap()
            .filter { (key, value) -> !hasExpectedType(key.name, value.toPrefValue()) }
            .keys
            .forEach { prefs.remove(it) }
    }

    /** Aparte de [prefs] para que los iconos solo se vuelvan a dibujar cuando cambia su estilo. */
    val iconStyle: StateFlow<IconStyle?> =
        prefs.map { it?.iconStyle }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Aparte de [prefs] para el dock: cuántos huecos se enseñan. Hasta leer el disco, los de por
     * defecto; el Inicio no se pinta antes de tener las preferencias.
     */
    val dockSlots: StateFlow<Int> =
        prefs.map { it?.dockSlots ?: DEFAULT_DOCK_SLOTS }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_DOCK_SLOTS)

    /** Aparte de [prefs] para quien solo necesita saber si hay reproductor; `false` hasta leer el disco. */
    val playerEnabled: StateFlow<Boolean> =
        prefs.map { it?.playerEnabled == true }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setIconStyle(style: IconStyle) = write { it[ICON_STYLE] = style.id }

    fun setAccent(index: Int) {
        accentDraft.value = null
        write { it[ACCENT] = index }
    }

    /**
     * El color que se está eligiendo a mano y aún no se ha soltado: el tema lo pinta en el acto (la
     * miniatura del Inicio y la página cambian con el dedo) sin escribir en el disco a cada paso.
     * Solo en memoria; `null` es «lo guardado».
     */
    val accentDraft = MutableStateFlow<Int?>(null)

    /** Guarda el color propio y lo elige como acento. */
    fun setCustomAccent(argb: Int) = write {
        it[CUSTOM_ACCENT_COLOR] = argb
        it[ACCENT] = CUSTOM_ACCENT
    }

    fun setFont(font: FontChoice) = write { it[FONT] = font.id }

    fun setTraceRail(rail: TraceRail) = write { it[TRACE_RAIL] = rail.id }

    /** Vuelve a poner la pausa a una app, al deshacer haberla quitado. */
    fun addPauseFor(packageName: String) = write {
        it[PAUSED_APPS] =
            (it[PAUSED_APPS] ?: DEFAULT_PAUSED_APPS) + packageName
    }

    /** Desde aquí los nombres del dock no dependen del número de huecos: los ha elegido el dueño. */
    fun setDockLabels(shown: Boolean) = write { it[DOCK_LABELS] = shown }

    fun setDockSlots(count: Int) = write { it[DOCK_SLOTS] = normalizeDockSlots(count) }

    fun setClockFormat(format: ClockFormat) = write { it[CLOCK_FORMAT] = format.id }

    fun setClockSize(size: ClockSize) = write { it[CLOCK_SIZE] = size.id }

    /** El orden nuevo se decide dentro de la escritura: dos toques seguidos no pisan el mismo valor. */
    fun toggleGlance(item: GlanceItem) = write {
        it[GLANCE] = formatGlance(toggledGlance(parseGlance(it[GLANCE]), item))
    }

    fun raiseGlance(item: GlanceItem) = write { it[GLANCE] = formatGlance(raisedGlance(parseGlance(it[GLANCE]), item)) }

    fun setBatteryShow(show: BatteryShow) = write { it[BATTERY_SHOW] = show.id }

    /** `null` vuelve a la ubicación aproximada. */
    fun setWeatherPlace(place: Place?) = write {
        if (place == null) {
            it.remove(WEATHER_CITY)
            it.remove(WEATHER_REGION)
            it.remove(WEATHER_LATITUDE)
            it.remove(WEATHER_LONGITUDE)
        } else {
            it[WEATHER_CITY] = place.name
            if (place.region == null) it.remove(WEATHER_REGION) else it[WEATHER_REGION] = place.region
            it[WEATHER_LATITUDE] = place.latitude
            it[WEATHER_LONGITUDE] = place.longitude
        }
    }

    fun setWeatherApp(packageName: String?) = write {
        if (packageName == null) it.remove(WEATHER_APP) else it[WEATHER_APP] = packageName
    }

    fun toggleDockGestures() = write { it[DOCK_GESTURES] = !(it[DOCK_GESTURES] ?: true) }

    fun toggleZonesPinch() = write { it[ZONES_PINCH] = !(it[ZONES_PINCH] ?: true) }

    fun toggleZonesLabels() = write { it[ZONES_LABELS] = !(it[ZONES_LABELS] ?: true) }

    fun toggleClipboardActions() = write { it[CLIPBOARD_ACTIONS] = !(it[CLIPBOARD_ACTIONS] ?: true) }

    fun setWallpaperDim(dim: WallpaperDim) = write { it[WALLPAPER_DIM] = dim.ordinal }

    fun toggleNow() = write { it[NOW_ENABLED] = !(it[NOW_ENABLED] ?: true) }

    fun togglePlayer() = write { it[PLAYER_ENABLED] = !(it[PLAYER_ENABLED] ?: true) }

    fun toggleUnreadDots() = write { it[UNREAD_DOTS] = !(it[UNREAD_DOTS] ?: true) }

    /** Enciende o apaga el punto de una app. Mientras no se toque ninguno valen los apagados por defecto. */
    fun toggleUnreadFor(packageName: String) = write {
        val muted = it[UNREAD_MUTED] ?: DEFAULT_UNREAD_MUTED
        it[UNREAD_MUTED] = if (packageName in muted) muted - packageName else muted + packageName
    }

    fun toggleLockDoubleTap() = write { it[LOCK_DOUBLE_TAP] = !(it[LOCK_DOUBLE_TAP] ?: true) }

    fun toggleNotificationsSwipe() = write { it[NOTIFICATIONS_SWIPE] = !(it[NOTIFICATIONS_SWIPE] ?: true) }

    /** Pone o quita la pausa de una app. Mientras no se toque ninguna valen las de por defecto. */
    fun togglePauseFor(packageName: String) = write {
        val paused = it[PAUSED_APPS] ?: DEFAULT_PAUSED_APPS
        it[PAUSED_APPS] = if (packageName in paused) paused - packageName else paused + packageName
    }

    fun setPauseSeconds(seconds: Int) = write { it[PAUSE_SECONDS_KEY] = seconds }

    fun toggleContactPhotos() = write { it[CONTACT_PHOTOS] = !(it[CONTACT_PHOTOS] ?: true) }

    fun toggleLiveNow() = write { it[LIVE_NOW] = !(it[LIVE_NOW] ?: false) }

    fun setPauseStats(shown: Boolean) = write { it[PAUSE_STATS] = shown }

    fun toggleHomeNotes() = write { it[HOME_NOTES] = !(it[HOME_NOTES] ?: true) }

    fun togglePhoneCommands() = write { it[PHONE_COMMANDS] = !(it[PHONE_COMMANDS] ?: true) }

    fun toggleDrawInitial() = write { it[DRAW_INITIAL] = !(it[DRAW_INITIAL] ?: true) }

    fun setLiveIcons(on: Boolean) = write { it[LIVE_ICONS] = on }

    fun toggleDayAccent() = write { it[DAY_ACCENT] = !(it[DAY_ACCENT] ?: false) }

    fun setNightAccent(index: Int) = write { it[NIGHT_ACCENT] = index }

    fun toggleWallpaperRotation() = write { it[WALLPAPER_ROTATION] = !(it[WALLPAPER_ROTATION] ?: false) }

    /** Fija la carpeta de fondos y enciende el cambio diario: elegir una carpeta es pedirlo. */
    fun setWallpaperFolder(uri: String) = write {
        it[WALLPAPER_FOLDER] = uri
        it[WALLPAPER_ROTATION] = true
    }

    /**
     * Pone o quita una persona de las favoritas. Devuelve `false` si ya hay cinco y se pide otra; la
     * comprobación se hace con lo que hay ahora, y la escritura vuelve a mirarlo por si cambió.
     */
    fun toggleFavorite(key: String): Boolean {
        val now = prefs.value?.favoritePeople.orEmpty()
        if (toggledFavorites(now, key) == null) return false
        write { stored ->
            toggledFavorites(parseFavorites(stored[FAVORITE_PEOPLE]), key)?.let {
                stored[FAVORITE_PEOPLE] = formatFavorites(it)
            }
        }
        return true
    }

    /**
     * Todas las preferencias guardadas, con su tipo, para la copia de la configuración. Solo lo que
     * está escrito: lo que nunca se tocó sigue con su valor por defecto también al cargarla.
     */
    suspend fun snapshot(): Map<String, PrefValue> = try {
        store.data.first().asMap()
            // Una clave que ya nadie lee (la del gasto rápido, borrado) no se lleva a otro teléfono.
            .filterKeys { it.name !in RETIRED_KEYS }
            .mapNotNull { (key, value) -> value.toPrefValue()?.let { key.name to it } }
            .toMap()
    } catch (e: IOException) {
        Log.w(TAG, "No se han podido leer las preferencias para la copia", e)
        emptyMap()
    }

    /**
     * Sustituye las preferencias por las de una copia, en una sola escritura: lo que no venga en ella
     * vuelve a su valor por defecto. Devuelve `false` si no se ha podido escribir.
     */
    suspend fun restore(values: Map<String, PrefValue>): Boolean = try {
        val accepted = acceptedPrefs(values, ::expectedType)
        (values.keys - accepted.keys).forEach { Log.w(TAG, "Preferencia de la copia descartada: $it") }
        store.edit { prefs ->
            prefs.clear()
            accepted.forEach { (name, value) -> prefs.put(name, value) }
        }
        accentDraft.value = null
        true
    } catch (e: IOException) {
        Log.w(TAG, "No se han podido cargar las preferencias de la copia", e)
        false
    }

    private fun Any.toPrefValue(): PrefValue? = when (this) {
        is Boolean -> PrefValue.Bool(this)
        is Int -> PrefValue.Whole(this)
        is Long -> PrefValue.Big(this)
        is Double -> PrefValue.Real(this)
        is Float -> PrefValue.Small(this)
        is String -> PrefValue.Text(this)
        is Set<*> -> PrefValue.Texts(filterIsInstance<String>().toSet())
        else -> null
    }

    private fun MutablePreferences.put(name: String, value: PrefValue) {
        when (value) {
            is PrefValue.Bool -> this[booleanPreferencesKey(name)] = value.value
            is PrefValue.Whole -> this[intPreferencesKey(name)] = value.value
            is PrefValue.Big -> this[longPreferencesKey(name)] = value.value
            is PrefValue.Real -> this[doublePreferencesKey(name)] = value.value
            is PrefValue.Small -> this[floatPreferencesKey(name)] = value.value
            is PrefValue.Text -> this[stringPreferencesKey(name)] = value.value
            is PrefValue.Texts -> this[stringSetPreferencesKey(name)] = value.value
        }
    }

    /** La bienvenida no vuelve a salir; ser la app de inicio queda como un permiso más de los ajustes. */
    fun skipOnboarding() = write { it[ONBOARDING_SKIPPED] = true }

    fun setAssistant(packageName: String) = write { it[ASSISTANT] = packageName }

    fun setWebSearch(packageName: String?) = write {
        if (packageName == null) it.remove(WEB_SEARCH) else it[WEB_SEARCH] = packageName
    }

    private fun write(change: (MutablePreferences) -> Unit) {
        scope.launch {
            try {
                store.edit { change(it) }
            } catch (e: IOException) {
                // Disco lleno o archivo dañado: la opción no se guarda, pero Alisio no se cierra.
                Log.w(TAG, "No se ha podido guardar una preferencia", e)
            }
        }
    }

    private companion object {
        const val TAG = "AlisioSettings"

        /** La ciudad elegida, si están las tres piezas que la forman. */
        fun storedPlace(stored: Preferences): Place? {
            val name = stored[WEATHER_CITY] ?: return null
            val latitude = stored[WEATHER_LATITUDE] ?: return null
            val longitude = stored[WEATHER_LONGITUDE] ?: return null
            return Place(name, stored[WEATHER_REGION], latitude, longitude)
        }

        // El tipo que Alisio lee en cada clave. `Preferences.Key` no lo guarda (compara solo el
        // nombre), así que cada clave lo apunta aquí al declararse: no hay lista aparte que olvidar.
        private val expected = HashMap<String, KClass<out PrefValue>>()

        /** El tipo con el que Alisio lee la clave [name]; `null` si no la conoce. */
        fun expectedType(name: String): KClass<out PrefValue>? = expected[name]

        fun hasExpectedType(name: String, value: PrefValue?): Boolean = expected[name]?.isInstance(value) == true

        private fun text(name: String) = stringPreferencesKey(name).also { expected[name] = PrefValue.Text::class }

        private fun whole(name: String) = intPreferencesKey(name).also { expected[name] = PrefValue.Whole::class }

        private fun bool(name: String) = booleanPreferencesKey(name).also { expected[name] = PrefValue.Bool::class }

        private fun real(name: String) = doublePreferencesKey(name).also { expected[name] = PrefValue.Real::class }

        private fun texts(name: String) = stringSetPreferencesKey(name).also { expected[name] = PrefValue.Texts::class }

        val ICON_STYLE = text("icon_style")
        val ACCENT = whole("accent")
        val CUSTOM_ACCENT_COLOR = whole("custom_accent")
        val FONT = text("font")
        val DOCK_LABELS = bool("dock_labels")
        val WALLPAPER_DIM = whole("wallpaper_dim")
        val ASSISTANT = text("assistant")
        val WEB_SEARCH = text("web_search")
        val NOW_ENABLED = bool("now_enabled")
        val PLAYER_ENABLED = bool("player_enabled")
        val UNREAD_DOTS = bool("unread_dots")
        val UNREAD_MUTED = texts("unread_muted")
        val DOCK_GESTURES = bool("dock_gestures")
        val ZONES_PINCH = bool("zones_pinch")
        val ZONES_LABELS = bool("zones_labels")
        val CLIPBOARD_ACTIONS = bool("clipboard_actions")
        val LOCK_DOUBLE_TAP = bool("lock_double_tap")
        val NOTIFICATIONS_SWIPE = bool("notifications_swipe")
        val PAUSED_APPS = texts("paused_apps")
        val PAUSE_SECONDS_KEY = whole("pause_seconds")
        val CONTACT_PHOTOS = bool("contact_photos")
        val TRACE_RAIL = text("trace_rail")
        val ONBOARDING_SKIPPED = bool("onboarding_skipped")
        val RETIRED_KEYS = setOf("expense_template")
        val LIVE_NOW = bool("live_now")
        val PAUSE_STATS = bool("pause_stats")
        val DOCK_SLOTS = whole("dock_slots")
        val CLOCK_FORMAT = text("clock_format")
        val CLOCK_SIZE = text("clock_size")
        val GLANCE = text("glance")
        val BATTERY_SHOW = text("battery_show")
        val WEATHER_CITY = text("weather_city")
        val WEATHER_REGION = text("weather_region")
        val WEATHER_LATITUDE = real("weather_latitude")
        val WEATHER_LONGITUDE = real("weather_longitude")
        val WEATHER_APP = text("weather_app")
        val HOME_NOTES = bool("home_notes")
        val PHONE_COMMANDS = bool("phone_commands")
        val DRAW_INITIAL = bool("draw_initial")
        val LIVE_ICONS = bool("live_icons")
        val DAY_ACCENT = bool("day_accent")
        val NIGHT_ACCENT = whole("night_accent")
        val WALLPAPER_ROTATION = bool("wallpaper_rotation")
        val WALLPAPER_FOLDER = text("wallpaper_folder")
        val FAVORITE_PEOPLE = text("favorite_people")
    }
}
