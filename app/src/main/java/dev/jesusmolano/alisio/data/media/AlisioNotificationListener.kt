package dev.jesusmolano.alisio.data.media

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.jesusmolano.alisio.AlisioApp
import dev.jesusmolano.alisio.core.notify.NotificationFacts
import dev.jesusmolano.alisio.core.notify.unreadByPackage
import dev.jesusmolano.alisio.core.now.LiveSignals
import dev.jesusmolano.alisio.core.now.chooseLive

/**
 * La única pieza de Alisio que el sistema mantiene viva (§5): la enlaza él mientras el usuario tenga
 * concedido el acceso a notificaciones. Sirve para dos cosas: contar las notificaciones sin leer de
 * cada app y dar derecho a leer las sesiones multimedia ([MediaSessions]).
 *
 * Privacidad: de cada notificación solo se guarda, en memoria, de qué paquete es y de qué clase
 * ([NotificationFacts]) y, si cuenta hacia abajo, a qué hora acaba (el icono vivo del reloj). Su
 * contenido no se lee, no se guarda y no se escribe en el registro. La
 * única excepción es «En curso» (apagado de serie): con él encendido se lee, en memoria y solo de lo
 * que está en curso, el título y el texto corto que la placa de Ahora enseña.
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
        countdowns.clear()
        try {
            activeNotifications?.forEach {
                active[it.key] = it.facts()
                it.countdownEnd()?.let { end -> countdowns[it.key] = it.packageName to end }
            }
        } catch (e: RuntimeException) {
            // El acceso puede retirarse entre el enlace y esta lectura: se empieza sin recuento.
            Log.w(TAG, "No se han podido leer las notificaciones activas", e)
        }
        access.liveScanner = ::scanLive
        publish()
        scanLive()
    }

    // «En curso» encendido en los ajustes; apagado no se mira el texto de ninguna notificación.
    private val liveEnabled: Boolean
        get() = (application as AlisioApp).container.settings.prefs.value?.liveNow == true

    /**
     * Busca algo en curso entre las notificaciones activas (docs/IDEAS.md §2.7). Solo se recorre la
     * lista cuando llega o se va un aviso que podría serlo, no con cada mensaje de chat.
     */
    private fun scanLive() {
        if (!liveEnabled) {
            access.setLive(null)
            return
        }
        val notifications =
            try {
                activeNotifications.orEmpty().filter { it.packageName != packageName }
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se han podido leer las notificaciones en curso", e)
                return
            }
        access.setLive(chooseLive(notifications) { it.liveSignals() }?.toLive())
    }

    private fun StatusBarNotification.liveSignals(): LiveSignals {
        val extras = notification.extras
        val promoted =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
        return LiveSignals(
            key = key,
            packageName = packageName,
            ongoing = isOngoing,
            promoted = promoted,
            category = notification.category,
            hasProgress =
                (extras?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0) > 0 ||
                    extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) == true,
            usesChronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true,
            isMedia = facts().media,
            postedAt = postTime
        )
    }

    private fun StatusBarNotification.toLive(): LiveNow? {
        val extras = notification.extras ?: return null
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: return null).toString()
        val countdown =
            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) &&
                extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        return LiveNow(
            key = key,
            packageName = packageName,
            title = title,
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            countdownTo = notification.`when`.takeIf { countdown },
            open = notification.contentIntent
        )
    }

    // Lo que podría ser algo en curso: así un chat que escribe cada segundo no recorre la lista entera.
    private fun StatusBarNotification.mayBeLive(): Boolean = isOngoing || access.live.value?.key == key

    override fun onListenerDisconnected() {
        active.clear()
        access.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        active[sbn.key] = sbn.facts()
        val end = sbn.countdownEnd()
        if (end != null) countdowns[sbn.key] = sbn.packageName to end else countdowns.remove(sbn.key)
        publish()
        if (sbn.mayBeLive()) scanLive()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        active.remove(sbn.key)
        countdowns.remove(sbn.key)
        publish()
        if (sbn.mayBeLive()) scanLive()
    }

    // Por clave de notificación: el paquete y la hora a la que acaba su cuenta atrás.
    private val countdowns = HashMap<String, Pair<String, Long>>()

    private fun publish() {
        access.setUnread(unreadByPackage(active.values))
        // La que acabe antes de cada app: es la que el icono del reloj enseña.
        access.setTimers(
            countdowns.values.groupBy({ it.first }, { it.second }).mapValues { (_, ends) -> ends.min() }
        )
    }

    /**
     * Cuándo acaba la cuenta atrás que enseña la notificación, si cuenta hacia abajo y aún no ha
     * acabado. No lee ningún texto: solo dos marcas y su hora.
     */
    private fun StatusBarNotification.countdownEnd(): Long? {
        val extras = notification.extras ?: return null
        val countdown =
            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) &&
                extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        return notification.`when`.takeIf { countdown && it > System.currentTimeMillis() }
    }

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
