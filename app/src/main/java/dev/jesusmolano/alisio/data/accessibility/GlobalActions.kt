package dev.jesusmolano.alisio.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las dos órdenes que Alisio solo puede dar a través de su servicio de accesibilidad (§5.8): bloquear
 * la pantalla y bajar las notificaciones. El servicio se activa y se desactiva en los ajustes del
 * sistema; sin él las órdenes no hacen nada y devuelven `false`.
 */
class GlobalActions(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AccessibilityManager::class.java)
    private val component = ComponentName(appContext, AlisioAccessibilityService::class.java)

    // El servicio enlazado. Solo él puede ejecutar las órdenes; lo fija y lo retira el propio servicio.
    private var service: AccessibilityService? = null

    private val _enabled = MutableStateFlow(isEnabled())

    /** Si el servicio está activado en los ajustes del sistema. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** El servicio se activa y se desactiva fuera de Alisio: se vuelve a mirar al volver al Inicio. */
    fun refresh() {
        _enabled.value = isEnabled()
    }

    fun lockScreen(): Boolean = perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    fun showNotifications(): Boolean = perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    internal fun onServiceConnected(connected: AccessibilityService) {
        service = connected
        _enabled.value = true
    }

    internal fun onServiceGone(gone: AccessibilityService) {
        // Si el sistema ya ha enlazado otra instancia, la que se va no debe llevársela por delante.
        if (service === gone) service = null
        refresh()
    }

    private fun perform(action: Int): Boolean {
        val current = service ?: return false
        return try {
            current.performGlobalAction(action)
        } catch (e: RuntimeException) {
            // El servicio puede desactivarse entre el gesto y la orden.
            Log.w(TAG, "El sistema no ha aceptado la orden $action", e)
            false
        }
    }

    private fun isEnabled(): Boolean = service != null ||
        try {
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty().any {
                val info = it.resolveInfo?.serviceInfo
                info != null && info.packageName == component.packageName && info.name == component.className
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido saber si el servicio de accesibilidad está activo", e)
            false
        }

    private companion object {
        const val TAG = "GlobalActions"
    }
}
