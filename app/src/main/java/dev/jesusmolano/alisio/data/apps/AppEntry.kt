package dev.jesusmolano.alisio.data.apps

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.UserHandle
import dev.jesusmolano.alisio.core.icons.CustomIcon

/**
 * Una app con icono de inicio, tal como se enseña en todas partes. [label] e [icon] ya llevan lo que
 * el dueño haya cambiado; [originalLabel] y [originalIcon] son los de la app (el icono, en el estilo
 * elegido).
 *
 * @param updatedAt cuándo se instaló o actualizó por última vez; 0 si no se ha podido saber.
 * @param hidden no sale en Zonas, Trazo, Recientes ni Ahora; solo al buscar su nombre entero.
 * @param customIcon el icono elegido para ella, si no es el suyo.
 * @param suspended en pausa por Bienestar digital (o un Modo de concentración): su icono ya viene
 *   atenuado y al abrirla el sistema enseña su propio aviso.
 * @param hideFromSuggestions el sistema pide no sugerirla (`shouldHideFromSuggestions`): Ahora y
 *   Recientes no la proponen, aunque sigue en todas partes donde se la busca.
 */
class AppEntry(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: Bitmap,
    val updatedAt: Long,
    val originalLabel: String = label,
    val originalIcon: Bitmap = icon,
    val hidden: Boolean = false,
    val customIcon: CustomIcon? = null,
    val suspended: Boolean = false,
    val hideFromSuggestions: Boolean = false
) {
    val renamed: Boolean get() = label != originalLabel
}
