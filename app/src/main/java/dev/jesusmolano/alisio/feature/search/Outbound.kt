package dev.jesusmolano.alisio.feature.search

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

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

private fun Context.open(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    // ActivityNotFoundException o SecurityException: en este teléfono no hay quien lo abra.
    Log.w(TAG, "No se ha podido abrir $intent", e)
    false
}
