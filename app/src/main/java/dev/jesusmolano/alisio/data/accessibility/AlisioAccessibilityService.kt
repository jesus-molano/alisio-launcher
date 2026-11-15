package dev.jesusmolano.alisio.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.jesusmolano.alisio.AlisioApp

/**
 * Servicio de accesibilidad mínimo y opcional (§5.8). Existe solo porque bloquear la pantalla y bajar
 * las notificaciones son órdenes que Android reserva a estos servicios (`performGlobalAction`).
 *
 * No mira la pantalla: su configuración (`res/xml/accessibility_service.xml`) no pide ningún tipo de
 * evento ni el contenido de las ventanas, así que el sistema no le entrega nada. Lo activa y lo
 * enlaza el sistema desde sus ajustes de accesibilidad; Alisio no lo arranca.
 */
class AlisioAccessibilityService : AccessibilityService() {
    private val actions: GlobalActions
        get() = (application as AlisioApp).container.globalActions

    override fun onServiceConnected() {
        super.onServiceConnected()
        actions.onServiceConnected(this)
    }

    // Sin tipos de evento pedidos no llega ninguno; y si llegara, no se lee.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        actions.onServiceGone(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        actions.onServiceGone(this)
        super.onDestroy()
    }
}
