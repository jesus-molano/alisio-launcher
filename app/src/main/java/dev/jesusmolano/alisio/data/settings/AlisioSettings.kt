package dev.jesusmolano.alisio.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jesusmolano.alisio.core.icons.IconStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("settings")

/** Preferencias simples de Alisio (DataStore, §4 del plan). */
class AlisioSettings(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    /** `null` hasta que se lee del disco, para no pintar los iconos con un estilo que no es el elegido. */
    val iconStyle: StateFlow<IconStyle?> =
        store.data
            // Un archivo de preferencias ilegible no puede dejar el Inicio sin iconos: se usa el valor por defecto.
            .catch { emit(emptyPreferences()) }
            .map { IconStyle.parse(it[ICON_STYLE]) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun setIconStyle(style: IconStyle) {
        scope.launch { store.edit { it[ICON_STYLE] = style.id } }
    }

    private companion object {
        val ICON_STYLE = stringPreferencesKey("icon_style")
    }
}
