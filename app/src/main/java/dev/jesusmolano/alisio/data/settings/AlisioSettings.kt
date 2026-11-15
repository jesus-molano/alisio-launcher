package dev.jesusmolano.alisio.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jesusmolano.alisio.core.icons.IconStyle
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Lo que se elige en los ajustes de Alisio. Cada función nueva añade aquí sus opciones.
 *
 * @param accent índice del color de acento en la paleta del tema.
 * @param assistantPackage app de IA a la que la barra manda las preguntas.
 * @param webSearchPackage app que hace la búsqueda web; `null` es la que elija el sistema.
 * @param nowEnabled si el Inicio enseña la sugerencia de Ahora.
 * @param playerEnabled si el Inicio enseña el reproductor cuando hay algo sonando o en pausa.
 * @param unreadDots si el dock marca con un punto las apps con notificaciones sin leer.
 * @param unreadMuted paquetes cuyo punto está apagado.
 * @param dockGestures si deslizar hacia arriba sobre un icono del dock abre su destino secundario.
 */
data class Prefs(
    val iconStyle: IconStyle = IconStyle.Color,
    val accent: Int = 0,
    val dockLabels: Boolean = true,
    val wallpaperDim: WallpaperDim = WallpaperDim.MEDIUM,
    val assistantPackage: String = DEFAULT_ASSISTANT,
    val webSearchPackage: String? = null,
    val nowEnabled: Boolean = true,
    val playerEnabled: Boolean = true,
    val unreadDots: Boolean = true,
    val unreadMuted: Set<String> = DEFAULT_UNREAD_MUTED,
    val dockGestures: Boolean = true
)

/** X viene con el punto apagado (§5.5): lo suyo rara vez es algo pendiente de leer. */
val DEFAULT_UNREAD_MUTED: Set<String> = setOf("com.twitter.android")

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
                Prefs(
                    iconStyle = IconStyle.parse(stored[ICON_STYLE]),
                    accent = stored[ACCENT] ?: 0,
                    dockLabels = stored[DOCK_LABELS] ?: true,
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
                    dockGestures = stored[DOCK_GESTURES] ?: true
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Aparte de [prefs] para que los iconos solo se vuelvan a dibujar cuando cambia su estilo. */
    val iconStyle: StateFlow<IconStyle?> =
        prefs.map { it?.iconStyle }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Aparte de [prefs] para quien solo necesita saber si hay reproductor; `false` hasta leer el disco. */
    val playerEnabled: StateFlow<Boolean> =
        prefs.map { it?.playerEnabled == true }.stateIn(scope, SharingStarted.Eagerly, false)

    fun setIconStyle(style: IconStyle) = write { it[ICON_STYLE] = style.id }

    fun setAccent(index: Int) = write { it[ACCENT] = index }

    /** El valor nuevo se decide dentro de la escritura: dos toques seguidos no pisan el mismo valor. */
    fun toggleDockLabels() = write { it[DOCK_LABELS] = !(it[DOCK_LABELS] ?: true) }

    fun toggleDockGestures() = write { it[DOCK_GESTURES] = !(it[DOCK_GESTURES] ?: true) }

    fun setWallpaperDim(dim: WallpaperDim) = write { it[WALLPAPER_DIM] = dim.ordinal }

    fun toggleNow() = write { it[NOW_ENABLED] = !(it[NOW_ENABLED] ?: true) }

    fun togglePlayer() = write { it[PLAYER_ENABLED] = !(it[PLAYER_ENABLED] ?: true) }

    fun toggleUnreadDots() = write { it[UNREAD_DOTS] = !(it[UNREAD_DOTS] ?: true) }

    /** Enciende o apaga el punto de una app. Mientras no se toque ninguno valen los apagados por defecto. */
    fun toggleUnreadFor(packageName: String) = write {
        val muted = it[UNREAD_MUTED] ?: DEFAULT_UNREAD_MUTED
        it[UNREAD_MUTED] = if (packageName in muted) muted - packageName else muted + packageName
    }

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
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val ACCENT = intPreferencesKey("accent")
        val DOCK_LABELS = booleanPreferencesKey("dock_labels")
        val WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val ASSISTANT = stringPreferencesKey("assistant")
        val WEB_SEARCH = stringPreferencesKey("web_search")
        val NOW_ENABLED = booleanPreferencesKey("now_enabled")
        val PLAYER_ENABLED = booleanPreferencesKey("player_enabled")
        val UNREAD_DOTS = booleanPreferencesKey("unread_dots")
        val UNREAD_MUTED = stringSetPreferencesKey("unread_muted")
        val DOCK_GESTURES = booleanPreferencesKey("dock_gestures")
    }
}
