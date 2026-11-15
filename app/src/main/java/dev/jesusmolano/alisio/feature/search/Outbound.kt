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
private const val CLAUDE_PACKAGE = "com.anthropic.claude"

/** Salidas de la barra hacia otras apps (§5.3). Todas devuelven `false` si nadie atiende el intent. */
internal fun Context.isClaudeInstalled(): Boolean = try {
    packageManager.getPackageInfo(CLAUDE_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** Comparte el texto con Claude; con el texto vacío solo abre la app. */
internal fun Context.askClaude(text: String): Boolean {
    val intent =
        if (text.isBlank()) {
            packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE) ?: return false
        } else {
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setPackage(CLAUDE_PACKAGE)
                .putExtra(Intent.EXTRA_TEXT, text)
        }
    return open(intent)
}

internal fun Context.searchWeb(text: String): Boolean =
    open(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))

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

private fun Context.open(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    // ActivityNotFoundException o SecurityException: en este teléfono no hay quien lo abra.
    Log.w(TAG, "No se ha podido abrir $intent", e)
    false
}
