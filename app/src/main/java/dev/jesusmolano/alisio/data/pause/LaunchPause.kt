package dev.jesusmolano.alisio.data.pause

import android.graphics.Bitmap
import dev.jesusmolano.alisio.core.pause.pauseBeforeOpening
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.settings.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Una apertura que espera a que pase la pausa.
 *
 * @param packageName de qué app es: la pausa dice cuánto se ha usado hoy.
 * @param icon el de la app; `null` si no tiene icono de inicio.
 * @param open abre de verdad y apunta la apertura en el historial; devuelve `false` si no ha podido.
 */
class PendingLaunch(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val seconds: Int,
    private val open: () -> Boolean
) {
    internal fun run(): Boolean = open()
}

/**
 * La pausa antes de abrir las apps elegidas en los ajustes (§3 «Opcional»). Todo lo que abre una app
 * desde Alisio pasa por [open]: si la app lleva pausa, la apertura se queda en [pending] hasta que la
 * pantalla de pausa la confirma o la descarta. Solo frena lo que se abre desde Alisio; lo que se
 * abre desde recientes, una notificación u otra app no pasa por aquí.
 */
class LaunchPause(private val prefs: StateFlow<Prefs?>) {
    private val _pending = MutableStateFlow<PendingLaunch?>(null)

    /** La apertura en pausa, o `null`. Solo hay una: una nueva sustituye a la anterior. */
    val pending: StateFlow<PendingLaunch?> = _pending.asStateFlow()

    /**
     * Abre con [action] o, si [packageName] lleva pausa, lo deja pendiente. Devuelve `false` solo si
     * se ha intentado abrir y no se ha podido: una apertura en pausa cuenta como atendida, para que
     * quien llama no avise de un fallo ni la apunte en el historial antes de tiempo.
     */
    fun open(packageName: String, label: String, icon: Bitmap?, action: () -> Boolean): Boolean {
        // Sin preferencias leídas aún no se frena nada: no se sabe qué ha elegido el usuario.
        val current = prefs.value ?: return action()
        val seconds = pauseBeforeOpening(packageName, current.pausedApps, current.pauseSeconds) ?: return action()
        _pending.value = PendingLaunch(packageName, label, icon, seconds, action)
        return true
    }

    fun open(app: AppEntry, action: () -> Boolean): Boolean =
        open(app.component.packageName, app.label, app.icon, action)

    /** Un acceso directo abre su app: lleva la pausa de ella y se anuncia con su nombre. */
    fun open(shortcut: ShortcutEntry, action: () -> Boolean): Boolean =
        open(shortcut.info.`package`, shortcut.app.label, shortcut.app.icon, action)

    /** Enseña la pausa de [app] tal como saldría, sin abrir nada al final: para verla desde los ajustes. */
    fun preview(app: AppEntry, seconds: Int) {
        _pending.value = PendingLaunch(app.component.packageName, app.label, app.icon, seconds) { true }
    }

    /** «Abrir igualmente». Devuelve `false` si la app no se ha podido abrir. */
    fun confirm(): Boolean {
        val launch = _pending.value ?: return true
        _pending.value = null
        return launch.run()
    }

    /** «No abrir», atrás, «inicio» o salir de Alisio: la apertura se olvida. */
    fun cancel() {
        _pending.value = null
    }
}
