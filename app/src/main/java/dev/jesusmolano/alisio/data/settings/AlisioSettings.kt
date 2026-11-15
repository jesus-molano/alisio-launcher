package dev.jesusmolano.alisio.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 */
data class Prefs(
    val iconStyle: IconStyle = IconStyle.Color,
    val accent: Int = 0,
    val dockLabels: Boolean = true,
    val wallpaperDim: WallpaperDim = WallpaperDim.MEDIUM,
    val assistantPackage: String = DEFAULT_ASSISTANT,
    val webSearchPackage: String? = null
)

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
                    webSearchPackage = stored[WEB_SEARCH]
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Aparte de [prefs] para que los iconos solo se vuelvan a dibujar cuando cambia su estilo. */
    val iconStyle: StateFlow<IconStyle?> =
        prefs.map { it?.iconStyle }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setIconStyle(style: IconStyle) = write { it[ICON_STYLE] = style.id }

    fun setAccent(index: Int) = write { it[ACCENT] = index }

    /** El valor nuevo se decide dentro de la escritura: dos toques seguidos no pisan el mismo valor. */
    fun toggleDockLabels() = write { it[DOCK_LABELS] = !(it[DOCK_LABELS] ?: true) }

    fun setWallpaperDim(dim: WallpaperDim) = write { it[WALLPAPER_DIM] = dim.ordinal }

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
    }
}
