package dev.jesusmolano.alisio.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import dev.jesusmolano.alisio.core.icons.CustomIcon
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imágenes de la galería convertidas en icono (de una app o de una pila). Se guardan en el
 * almacenamiento interno de Alisio, ya recortadas a la forma común: la imagen original no se vuelve
 * a leer, así que no hace falta ningún permiso de almacenamiento después de elegirla.
 */
class CustomImages(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIRECTORY)
    private val sizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).roundToInt()

    // Se leen al pintar cada icono: una vez decodificada, cada imagen se queda en memoria.
    private val cache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Lee la imagen que ha dado el selector de fotos del sistema, la recorta y la guarda. Devuelve
     * el nombre con que se guarda, o `null` si no se ha podido leer (formato raro, archivo en la nube
     * sin descargar).
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            val decoded =
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Software: se dibuja en un Canvas propio. Y reducida antes de decodificar: una foto
                    // de 50 Mpx no cabe en memoria entera, y el icono solo necesita su lado corto.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val short = minOf(info.size.width, info.size.height)
                    if (short > sizePx * DECODE_MARGIN) {
                        val scale = sizePx * DECODE_MARGIN.toFloat() / short
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            val icon = renderImageIcon(decoded, sizePx)
            val name = UUID.randomUUID().toString() + EXTENSION
            dir.mkdirs()
            FileOutputStream(File(dir, name)).use { icon.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            cache[name] = icon
            name
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido leer la imagen elegida", e)
            null
        } catch (e: RuntimeException) {
            // SecurityException si el permiso temporal del selector ya caducó, o un decodificador que falla.
            Log.w(TAG, "No se ha podido convertir la imagen elegida", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Imagen elegida demasiado grande", e)
            null
        }
    }

    /** La imagen guardada con ese nombre, o `null` si ya no está. Se llama fuera del hilo principal. */
    fun load(name: String): Bitmap? {
        if (!CustomIcon.isImageName(name)) return null
        cache[name]?.let { return it }
        val file = File(dir, name)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.path)?.also { cache[name] = it }
    }

    /** El PNG guardado con ese nombre, para la copia de la configuración; `null` si ya no está. */
    fun bytes(name: String): ByteArray? {
        if (!CustomIcon.isImageName(name)) return null
        val file = File(dir, name)
        return try {
            if (file.isFile) file.readBytes() else null
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido leer una imagen para la copia", e)
            null
        }
    }

    /**
     * Vuelve a escribir una imagen que trae una copia, con su mismo nombre: así las apps y las pilas
     * que la usan la encuentran. Solo nombres que [CustomIcon] admite, para no salir de la carpeta.
     */
    fun restore(name: String, bytes: ByteArray): Boolean {
        if (!CustomIcon.isImageName(name)) return false
        return try {
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
            cache.remove(name)
            true
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido escribir una imagen de la copia", e)
            false
        }
    }

    /**
     * Borra las imágenes que nadie usa. Solo al arrancar: mientras Alisio corre, una imagen recién
     * sustituida puede volver con «Deshacer».
     */
    suspend fun cleanup(used: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.name !in used }?.forEach { file ->
            if (!file.delete()) Log.w(TAG, "No se ha podido borrar una imagen sin uso")
        }
    }

    private companion object {
        const val TAG = "CustomImages"
        const val DIRECTORY = "custom_icons"
        const val EXTENSION = ".png"
        const val PNG_QUALITY = 100

        // Se decodifica al doble del icono: el recorte y el escalado final quedan nítidos.
        const val DECODE_MARGIN = 2
    }
}
