package dev.jesusmolano.alisio.feature.search

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import androidx.core.net.toUri
import dev.jesusmolano.alisio.core.extras.ClipContent
import dev.jesusmolano.alisio.core.extras.ClockCommand
import dev.jesusmolano.alisio.core.extras.detectClip

private const val TAG = "Outbound"

/** Nombre de una app instalada, o `null` si no lo está. Sirve para saber si el asistente elegido existe. */
internal fun Context.appLabel(packageName: String): String? = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/**
 * Comparte el texto con el asistente de IA elegido en los ajustes; con el texto vacío solo abre la
 * app. Como todas las salidas de la barra (§5.3), devuelve `false` si nadie atiende el intent.
 */
internal fun Context.askAssistant(packageName: String, text: String): Boolean {
    val launch = packageManager.getLaunchIntentForPackage(packageName)
    if (text.isBlank()) return launch != null && open(launch)
    val share =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(packageName)
            .putExtra(Intent.EXTRA_TEXT, text)
    // Si la app no acepta texto compartido, al menos se abre.
    return open(share) || (launch != null && open(launch))
}

/**
 * Busca en la web con la app elegida en los ajustes, o con la del sistema si [packageName] es `null`.
 * Un navegador que no atiende `ACTION_WEB_SEARCH` recibe la búsqueda como dirección de Google.
 */
internal fun Context.searchWeb(packageName: String?, text: String): Boolean {
    if (packageName == null) return open(webSearch(text))
    return open(webSearch(text).setPackage(packageName)) ||
        open(Intent(Intent.ACTION_VIEW, googleSearch(text)).setPackage(packageName)) ||
        // La app elegida ya no está: se usa la del sistema.
        open(webSearch(text))
}

private fun webSearch(text: String) = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)

private fun googleSearch(text: String): Uri = "https://www.google.com/search?q=${Uri.encode(text)}".toUri()

/** Busca en Play Store; si no hay quien abra `market://`, en la web de Play. */
internal fun Context.searchPlayStore(text: String): Boolean {
    val query = Uri.encode(text)
    return open(Intent(Intent.ACTION_VIEW, "market://search?q=$query&c=apps".toUri())) ||
        open(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=$query&c=apps".toUri()))
}

internal fun Context.openSetting(action: String): Boolean = open(Intent(action))

internal fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
}

/** App que puede hacer la búsqueda web: su paquete y su nombre. */
internal class WebSearchApp(val packageName: String, val label: String)

/** Las que atienden `ACTION_WEB_SEARCH` y los navegadores. Necesita esos intents en `<queries>`. */
internal fun Context.webSearchApps(): List<WebSearchApp> {
    val intents =
        listOf(
            Intent(Intent.ACTION_WEB_SEARCH),
            Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        )
    return intents
        .flatMap { packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL) }
        .map { it.activityInfo.packageName }
        .distinct()
        .mapNotNull { packageName -> appLabel(packageName)?.let { WebSearchApp(packageName, it) } }
        .sortedBy { it.label.lowercase() }
}

/**
 * Le pide al reloj una alarma o un temporizador, sin pasar por su pantalla. Necesita el permiso
 * `SET_ALARM`; sin él o sin una app de reloj que lo atienda, devuelve `false`.
 */
internal fun Context.setClock(command: ClockCommand): Boolean {
    val intent =
        when (command) {
            is ClockCommand.Alarm ->
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)

            is ClockCommand.Timer ->
                Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, command.seconds)
        }
    return open(intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true))
}

/** La acción de lo que había en el portapapeles: abrir el enlace, marcar el teléfono o ver el mapa. */
internal fun Context.openClip(content: ClipContent): Boolean {
    val intent =
        when (content) {
            is ClipContent.Url -> Intent(Intent.ACTION_VIEW, content.url.toUri())

            // El marcador y no la llamada: un número copiado se revisa antes de llamar.
            is ClipContent.Phone -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", content.number, null))

            is ClipContent.Address -> Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(content.text)}".toUri())
        }
    return open(intent, secret = true)
}

// Las apps que guardan contraseñas marcan así lo que copian (constante de Android 13,
// `ClipDescription.EXTRA_IS_SENSITIVE`; en Android 12 nadie la pone y no estorba).
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Lo que hay en el portapapeles, si es un enlace, un teléfono o una dirección. Android avisa al
 * usuario en cada lectura, así que solo se llama al abrir la barra; y antes mira la descripción,
 * que no avisa, para no leer imágenes ni contraseñas. El texto no se guarda ni se registra: solo
 * sale de aquí lo que [detectClip] reconoce.
 */
internal fun Context.clipboardContent(): ClipContent? = try {
    val clipboard = getSystemService(ClipboardManager::class.java)
    val description = clipboard.primaryClipDescription
    val readable =
        description != null &&
            description.hasMimeType("text/*") &&
            description.extras?.getBoolean(EXTRA_IS_SENSITIVE) != true
    val text = if (readable) clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text else null
    text?.toString()?.let(::detectClip)
} catch (_: RuntimeException) {
    // Si el sistema niega la lectura (SecurityException), la barra se abre sin esa acción.
    null
}

/** @param secret el intent lleva algo del usuario (lo copiado): no se escribe en el registro. */
private fun Context.open(intent: Intent, secret: Boolean = false): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    // ActivityNotFoundException o SecurityException: en este teléfono no hay quien lo abra.
    if (secret) {
        // El mensaje de la excepción también lleva el intent entero: solo se apunta su tipo.
        Log.w(TAG, "No se ha podido abrir ${intent.action}: ${e.javaClass.simpleName}")
    } else {
        Log.w(TAG, "No se ha podido abrir $intent", e)
    }
    false
}
