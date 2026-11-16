package dev.jesusmolano.alisio.data.system

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dev.jesusmolano.alisio.core.command.PhoneCommand
import dev.jesusmolano.alisio.core.command.PhonePanel
import dev.jesusmolano.alisio.core.command.Switch
import dev.jesusmolano.alisio.core.command.brightnessLevel
import dev.jesusmolano.alisio.core.command.resolveSwitch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PhoneControls"

// Lo que tarda el sistema en decir cómo está la linterna al empezar a escucharla.
private const val TORCH_STATE_MILLIS = 500L

// El ajuste de «No molestar» existe en todos los Android, pero su constante no es pública.
private const val ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"

/** Lo que ha pasado al dar una orden. */
sealed interface PhoneResult {
    /** Hecho sin salir de Alisio; [on] es cómo ha quedado, si es un interruptor. */
    data class Done(val on: Boolean?) : PhoneResult

    /** Se ha abierto el panel o el ajuste del sistema donde se hace. */
    data object Opened : PhoneResult

    data object Failed : PhoneResult
}

/**
 * Las órdenes de la barra para el teléfono. Android no deja a otra app encender el wifi, el
 * Bluetooth, los datos, la ubicación ni el modo avión: para esos se abre su panel (`Settings.Panel`)
 * o su ajuste. La linterna sí se cambia aquí; «No molestar», con el acceso a su política; el brillo
 * y la rotación, con el permiso de cambiar ajustes del sistema. Sin esos permisos se abre el ajuste.
 */
class PhoneControls(context: Context) {
    private val appContext = context.applicationContext
    private val camera = appContext.getSystemService(CameraManager::class.java)
    private val notifications = appContext.getSystemService(NotificationManager::class.java)

    /** Si Alisio puede cambiar el brillo y la rotación (`WRITE_SETTINGS`, que se da en el sistema). */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(appContext)

    /** Si Alisio puede cambiar «No molestar» (acceso a la política de notificaciones). */
    fun dndGranted(): Boolean = notifications.isNotificationPolicyAccessGranted

    suspend fun run(command: PhoneCommand): PhoneResult = when (command) {
        is PhoneCommand.Torch -> torch(command.switch)
        is PhoneCommand.Panel -> panel(command.panel)
        is PhoneCommand.DoNotDisturb -> doNotDisturb(command.switch)
        is PhoneCommand.Brightness -> brightness(command.percent)
        is PhoneCommand.Rotation -> rotation(command.switch)
    }

    private suspend fun torch(asked: Switch?): PhoneResult {
        val id = torchCamera() ?: return PhoneResult.Failed
        val target = resolveSwitch(asked, torchOn(id)) ?: Switch.ON
        return try {
            camera.setTorchMode(id, target == Switch.ON)
            PhoneResult.Done(target == Switch.ON)
        } catch (e: CameraAccessException) {
            // La cámara está en uso (otra app la tiene abierta): no se puede encender.
            Log.w(TAG, "No se ha podido cambiar la linterna", e)
            PhoneResult.Failed
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "La cámara no tiene linterna", e)
            PhoneResult.Failed
        }
    }

    private fun torchCamera(): String? = try {
        camera.cameraIdList.firstOrNull { id ->
            val characteristics = camera.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: CameraAccessException) {
        Log.w(TAG, "No se han podido leer las cámaras", e)
        null
    }

    /**
     * Cómo está la linterna: el sistema solo lo dice al empezar a escucharla. Se escucha un momento
     * y se deja de escuchar; `null` si no contesta a tiempo.
     */
    private suspend fun torchOn(id: String): Boolean? = withTimeoutOrNull(TORCH_STATE_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId != id || !continuation.isActive) return
                        camera.unregisterTorchCallback(this)
                        continuation.resume(enabled)
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId != id || !continuation.isActive) return
                        camera.unregisterTorchCallback(this)
                        continuation.resume(null)
                    }
                }
            camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            continuation.invokeOnCancellation { camera.unregisterTorchCallback(callback) }
        }
    }

    private fun panel(panel: PhonePanel): PhoneResult = when (panel) {
        PhonePanel.WIFI -> open(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        PhonePanel.BLUETOOTH -> open(Settings.ACTION_BLUETOOTH_SETTINGS)
        PhonePanel.MOBILE_DATA -> open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)
        PhonePanel.LOCATION -> open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        PhonePanel.AIRPLANE -> open(Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    }

    private fun doNotDisturb(asked: Switch?): PhoneResult {
        if (!dndGranted()) return open(ZEN_MODE_SETTINGS, Settings.ACTION_SOUND_SETTINGS)
        val on = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val target = resolveSwitch(asked, on) ?: return PhoneResult.Failed
        return try {
            notifications.setInterruptionFilter(
                if (target == Switch.ON) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
            )
            PhoneResult.Done(target == Switch.ON)
        } catch (e: SecurityException) {
            Log.w(TAG, "El acceso a No molestar se ha retirado", e)
            open(ZEN_MODE_SETTINGS, Settings.ACTION_SOUND_SETTINGS)
        }
    }

    private fun brightness(percent: Int?): PhoneResult {
        if (percent == null || !canWriteSettings()) return open(Settings.ACTION_DISPLAY_SETTINGS)
        return try {
            val resolver = appContext.contentResolver
            // Con el brillo automático el sistema pisaría el valor al momento: se pasa a manual.
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessLevel(percent))
            PhoneResult.Done(null)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se ha podido cambiar el brillo", e)
            open(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    private fun rotation(asked: Switch?): PhoneResult {
        if (!canWriteSettings()) return open(Settings.ACTION_DISPLAY_SETTINGS)
        val resolver = appContext.contentResolver
        return try {
            val on = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            val target = resolveSwitch(asked, on) ?: Switch.ON
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (target == Switch.ON) 1 else 0)
            PhoneResult.Done(target == Switch.ON)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se ha podido cambiar la rotación", e)
            open(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    /** Pantalla del sistema donde se da el permiso de cambiar ajustes (brillo y rotación). */
    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${appContext.packageName}".toUri())

    /** Pantalla del sistema donde se da el acceso a «No molestar». */
    fun dndAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** Abre la primera pantalla de [actions] que exista en este sistema. */
    private fun open(vararg actions: String): PhoneResult {
        for (action in actions) {
            try {
                appContext.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return PhoneResult.Opened
            } catch (_: ActivityNotFoundException) {
                // One UI no tiene todos los paneles: se prueba con la siguiente.
            } catch (e: SecurityException) {
                Log.w(TAG, "No se ha podido abrir $action", e)
            }
        }
        return PhoneResult.Failed
    }
}
