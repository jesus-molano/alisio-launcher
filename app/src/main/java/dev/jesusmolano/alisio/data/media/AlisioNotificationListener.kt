package dev.jesusmolano.alisio.data.media

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.jesusmolano.alisio.AlisioApp
import dev.jesusmolano.alisio.core.notify.NotificationFacts
import dev.jesusmolano.alisio.core.notify.unreadByPackage

/**
 * La única pieza de Alisio que el sistema mantiene viva (§5): la enlaza él mientras el usuario tenga
 * concedido el acceso a notificaciones. Sirve para dos cosas: contar las notificaciones sin leer de
 * cada app y dar derecho a leer las sesiones multimedia ([MediaSessions]).
 *
 * Privacidad: de cada notificación solo se guarda, en memoria, de qué paquete es y de qué clase
 * ([NotificationFacts]). Su contenido no se lee, no se guarda y no se escribe en el registro.
 */
class AlisioNotificationListener : NotificationListenerService() {
    // Por clave de notificación: así cada aviso cuesta una entrada del mapa, en vez de pedirle al
    // sistema la lista entera (con sus textos e imágenes) cada vez que llega o se va una.
    private val active = HashMap<String, NotificationFacts>()

    private val access: NotificationAccess
        get() = (application as AlisioApp).container.notificationAccess

    override fun onListenerConnected() {
        access.refresh()
        active.clear()
        try {
            activeNotifications?.forEach { active[it.key] = it.facts() }
        } catch (e: RuntimeException) {
            // El acceso puede retirarse entre el enlace y esta lectura: se empieza sin recuento.
            Log.w(TAG, "No se han podido leer las notificaciones activas", e)
        }
        publish()
    }

    override fun onListenerDisconnected() {
        active.clear()
        access.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        active[sbn.key] = sbn.facts()
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        active.remove(sbn.key)
        publish()
    }

    private fun publish() = access.setUnread(unreadByPackage(active.values))

    private fun StatusBarNotification.facts(): NotificationFacts {
        val flags = notification.flags
        return NotificationFacts(
            packageName = packageName,
            ongoing = isOngoing,
            foregroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            media =
                notification.category == Notification.CATEGORY_TRANSPORT ||
                    notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        )
    }

    private companion object {
        const val TAG = "NotificationListener"
    }
}
