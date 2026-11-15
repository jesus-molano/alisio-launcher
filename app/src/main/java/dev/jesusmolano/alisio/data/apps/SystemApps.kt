package dev.jesusmolano.alisio.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log

/**
 * Los paquetes de la isla Sistema de Zonas (§5.7): Ajustes y las apps predeterminadas de cámara,
 * galería, reloj, teléfono y mensajes. Se resuelven por intents, no por nombre de paquete, para que
 * valgan con las apps de Samsung, las de Google o las que yo elija.
 */
class SystemApps(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    /** Hace varias consultas al sistema: se llama fuera del hilo principal. */
    fun packages(): Set<String> = buildSet {
        listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_DIAL)
        ).forEach { intent -> handlerOf(intent)?.let(::add) }
        // Teléfono y mensajes tienen además un rol: si hay app elegida, es esa y no hay que adivinar.
        attempt { appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }?.let(::add)
        attempt { Telephony.Sms.getDefaultSmsPackage(appContext) }?.let(::add)
    }

    /**
     * La app que atiende [intent]. Con varias candidatas y ninguna predeterminada, el sistema
     * responde con su selector (paquete «android»): entonces vale la primera que venga con el
     * teléfono, que es la «del sistema» aunque no se haya elegido.
     */
    private fun handlerOf(intent: Intent): String? = attempt {
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (resolved != null && resolved.packageName != CHOOSER_PACKAGE) {
            resolved.packageName
        } else {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }
                .firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                ?.packageName
        }
    }

    // Una consulta que falla (un perfil bloqueado, un sistema sin telefonía) deja esa app fuera de
    // Sistema; no puede dejar Zonas sin abrir.
    private fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido resolver una app del sistema", e)
        null
    }

    private companion object {
        const val TAG = "SystemApps"
        const val CHOOSER_PACKAGE = "android"
    }
}
