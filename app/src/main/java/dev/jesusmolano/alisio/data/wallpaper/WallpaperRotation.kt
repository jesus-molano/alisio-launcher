package dev.jesusmolano.alisio.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jesusmolano.alisio.core.wallpaper.isWallpaperImage
import dev.jesusmolano.alisio.core.wallpaper.nextWallpaper
import dev.jesusmolano.alisio.core.wallpaper.wallpaperOrder
import dev.jesusmolano.alisio.data.settings.Prefs
import java.io.IOException
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.wallpaperStore by preferencesDataStore("wallpaper")

private const val TAG = "WallpaperRotation"
private val LAST_DAY = longPreferencesKey("last_day")
private val LAST_INDEX = intPreferencesKey("last_index")

// Una imagen de la carpeta se reduce a algo más que la pantalla: el sistema la recorta a su gusto.
private const val DECODE_MARGIN = 1.25f

/** Una imagen de la carpeta de fondos. */
class FolderImage(val uri: Uri, val name: String)

/**
 * Fondos que rotan (docs/IDEAS.md, bloque «Seña propia»): una imagen nueva de la carpeta elegida
 * cada día, puesta al salir del Inicio. No hay trabajo en segundo plano: si un día no se mira el
 * Inicio, ese día no cambia. Lo puesto y cuándo se guarda aparte de las preferencias, porque no es
 * una opción y no debe viajar en la copia de la configuración.
 */
class WallpaperRotation(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.wallpaperStore

    // Volver al Inicio dos veces seguidas no pone dos fondos.
    private val changing = Mutex()

    /** Si la carpeta sigue concedida: el permiso se pierde al borrarla o al quitarlo en el sistema. */
    fun hasAccess(folder: String): Boolean =
        appContext.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder && it.isReadPermission }

    /** Guarda el permiso de la carpeta recién elegida para que dure más allá de este arranque. */
    fun keepAccess(folder: Uri): Boolean = try {
        appContext.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "El sistema no deja guardar el permiso de la carpeta", e)
        false
    }

    /** El nombre de la carpeta, como lo enseña el selector del sistema; `null` si ya no se puede leer. */
    suspend fun folderName(folder: String): String? = withContext(Dispatchers.IO) {
        try {
            val tree = folder.toUri()
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            appContext.contentResolver.query(
                document,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido leer el nombre de la carpeta", e)
            null
        }
    }

    /** Las imágenes de la carpeta (sin entrar en subcarpetas), en su orden. Vacía si no se puede leer. */
    suspend fun images(folder: String): List<FolderImage> = withContext(Dispatchers.IO) {
        try {
            val tree = folder.toUri()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree)
            )
            val found =
                appContext.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(1)
                            if (!isWallpaperImage(name, cursor.getString(2))) continue
                            add(
                                FolderImage(
                                    DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                                    name
                                )
                            )
                        }
                    }
                }.orEmpty()
            wallpaperOrder(found) { it.name }
        } catch (e: RuntimeException) {
            // Carpeta borrada, permiso retirado o proveedor que ya no está.
            Log.w(TAG, "No se ha podido leer la carpeta de fondos", e)
            emptyList()
        }
    }

    /** La posición de la imagen puesta ahora, o `null` si aún no se ha puesto ninguna. */
    suspend fun currentIndex(): Int? {
        val stored = store.data.catch { emit(emptyPreferences()) }.first()
        return if (stored[LAST_DAY] == null) null else stored[LAST_INDEX]
    }

    /**
     * Al salir del Inicio: si está encendido y hoy aún no se ha cambiado, pone la siguiente imagen.
     * Con [force] la pone aunque ya se haya cambiado hoy («Pasar a la siguiente» de los ajustes).
     * Devuelve `true` si ha cambiado el fondo.
     */
    suspend fun rotateIfDue(prefs: Prefs, force: Boolean = false): Boolean {
        val folder = prefs.wallpaperFolder ?: return false
        if (!prefs.wallpaperRotation) return false
        return changing.withLock {
            val stored = store.data.catch { emit(emptyPreferences()) }.first()
            val today = LocalDate.now().toEpochDay()
            val lastDay = stored[LAST_DAY].takeUnless { force }
            // Ya cambiado hoy: ni se pregunta al proveedor de la carpeta, que es lo que tarda.
            if (lastDay != null && lastDay >= today) return@withLock false
            // Los permisos guardados se piden al sistema: fuera del hilo principal.
            if (!withContext(Dispatchers.IO) { hasAccess(folder) }) return@withLock false
            val images = images(folder)
            val index =
                if (force) {
                    nextWallpaper(today, lastDay = today - 1, lastIndex = stored[LAST_INDEX] ?: -1, count = images.size)
                } else {
                    nextWallpaper(today, lastDay, stored[LAST_INDEX] ?: 0, images.size)
                }
                    ?: return@withLock false
            if (!apply(images[index].uri)) return@withLock false
            try {
                store.edit {
                    it[LAST_DAY] = today
                    it[LAST_INDEX] = index
                }
            } catch (e: IOException) {
                Log.w(TAG, "No se ha podido apuntar el fondo puesto", e)
            }
            true
        }
    }

    /** Una miniatura de la imagen para la vista previa de los ajustes; `null` si no se puede leer. */
    suspend fun thumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido leer la miniatura de un fondo", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido leer la miniatura de un fondo", e)
            null
        }
    }

    /**
     * Pone la imagen como fondo del Inicio (no de la pantalla de bloqueo). Se decodifica reducida a
     * la pantalla: una foto de 50 Mpx no cabe entera en memoria. El velo del Inicio se ajusta solo,
     * porque escucha el cambio de colores del fondo (`rememberLightWallpaper`).
     */
    private suspend fun apply(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val metrics = appContext.resources.displayMetrics
            val longest = maxOf(metrics.widthPixels, metrics.heightPixels) * DECODE_MARGIN
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) {
                        decoder,
                        info,
                        _
                    ->
                    val side = maxOf(info.size.width, info.size.height)
                    if (side > longest) {
                        val scale = longest / side
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            WallpaperManager.getInstance(appContext).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            true
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido poner el fondo", e)
            false
        } catch (e: RuntimeException) {
            // Imagen dañada, en la nube sin descargar o demasiado grande incluso reducida.
            Log.w(TAG, "No se ha podido poner el fondo", e)
            false
        }
    }
}
