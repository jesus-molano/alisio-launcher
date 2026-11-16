package dev.jesusmolano.alisio.data.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Algo en curso para la placa de Ahora: un viaje, un temporizador, un pedido.
 *
 * @param countdownTo fin de la cuenta atrás (hora del reloj), si la notificación cuenta hacia abajo.
 * @param open lo que abre la notificación al tocarla; `null` si no abre nada.
 */
class LiveNow(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String?,
    val countdownTo: Long?,
    val open: PendingIntent?
)

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

    private val _live = MutableStateFlow<LiveNow?>(null)

    /**
     * Lo que está en curso ahora (docs/IDEAS.md §2.7), o `null`. Solo se lee con «En curso» encendido
     * en los ajustes; apagado, el servicio no mira el texto de ninguna notificación.
     */
    val live: StateFlow<LiveNow?> = _live.asStateFlow()

    // Lo pone el servicio mientras está enlazado: vuelve a mirar las notificaciones activas.
    internal var liveScanner: (() -> Unit)? = null

    internal fun setLive(value: LiveNow?) {
        _live.value = value
    }

    private val _timers = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Cuándo acaba cada cuenta atrás en curso, por paquete (el temporizador del reloj), para el icono
     * vivo del dock. De la notificación solo se mira que cuente hacia abajo y su hora de fin.
     */
    val timers: StateFlow<Map<String, Long>> = _timers.asStateFlow()

    internal fun setTimers(value: Map<String, Long>) {
        _timers.value = value
    }

    /** Al encender «En curso» o al volver al Inicio: lo que ya estaba en curso sale sin esperar a otro aviso. */
    fun refreshLive() {
        liveScanner?.invoke()
    }

    internal fun onListenerDisconnected() {
        _unread.value = emptyMap()
        _live.value = null
        _timers.value = emptyMap()
        liveScanner = null
        refresh()
    }

    private fun isGranted(): Boolean = notificationManager.isNotificationListenerAccessGranted(component)
}
