package dev.jesusmolano.alisio.data.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El acceso a notificaciones (§7) y lo que da para los puntos de no leído: cuántas notificaciones sin
 * leer tiene cada paquete. Solo el recuento, y solo en memoria; lo rellena [AlisioNotificationListener].
 *
 * Sin el acceso, que se concede en los ajustes del sistema, no hay recuento ni reproductor.
 */
class NotificationAccess(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    /** El servicio de escucha: también identifica a Alisio al pedir las sesiones multimedia. */
    val component = ComponentName(appContext, AlisioNotificationListener::class.java)

    private val _granted = MutableStateFlow(isGranted())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Notificaciones sin leer por paquete. Vacío sin acceso o mientras el sistema no enlaza el servicio. */
    val unread: StateFlow<Map<String, Int>> = _unread.asStateFlow()

    /** El acceso se da y se quita fuera de Alisio: se vuelve a mirar al volver al Inicio. */
    fun refresh() {
        val granted = isGranted()
        _granted.value = granted
        // Al retirar el acceso el servicio no siempre avisa: los puntos no pueden quedarse puestos.
        if (!granted) _unread.value = emptyMap()
    }

    internal fun setUnread(counts: Map<String, Int>) {
        _unread.value = counts
    }

    internal fun onListenerDisconnected() {
        _unread.value = emptyMap()
        refresh()
    }

    private fun isGranted(): Boolean = notificationManager.isNotificationListenerAccessGranted(component)
}
