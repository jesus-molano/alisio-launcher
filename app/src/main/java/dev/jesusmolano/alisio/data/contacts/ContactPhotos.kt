package dev.jesusmolano.alisio.data.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotos pequeñas de los contactos, ya recortadas en círculo como el avatar de iniciales al que
 * sustituyen. Se leen una a una cuando una fila las pide, no al indexar la agenda: la mayoría de
 * las personas nunca llega a pintarse. Solo viven en memoria.
 */
class ContactPhotos(context: Context) {
    private val appContext = context.applicationContext
    private val cache = LruCache<String, Bitmap>(CACHE_SIZE)

    /** La foto si ya se leyó; sirve para pintarla desde el primer fotograma, sin pasar por las iniciales. */
    fun cached(person: Person): Bitmap? = person.photoUri?.let(cache::get)

    /** La foto de [person], o `null` si no tiene o no se deja leer. */
    suspend fun load(person: Person): Bitmap? {
        val uri = person.photoUri ?: return null
        cache.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) { read(uri)?.also { cache.put(uri, it) } }
    }

    /** La dirección de la foto no cambia cuando el contacto cambia de foto: hay que soltar lo leído. */
    fun clear() = cache.evictAll()

    private fun read(uri: String): Bitmap? = try {
        appContext.contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream)?.let(::circle)
    } catch (e: IOException) {
        // La foto se ha borrado entre que se leyó la agenda y se pidió.
        Log.w(TAG, "Foto de contacto imposible de leer", e)
        null
    } catch (e: RuntimeException) {
        // SecurityException si se ha retirado el permiso, o un proveedor que responde mal.
        Log.w(TAG, "Foto de contacto imposible de leer", e)
        null
    }

    private fun circle(photo: Bitmap): Bitmap {
        val size = minOf(photo.width, photo.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val result = createBitmap(size, size)
        val canvas = Canvas(result)
        // Una foto no cuadrada se centra: se pierde lo que sobra por los lados.
        canvas.translate((size - photo.width) / 2f, (size - photo.height) / 2f)
        canvas.drawCircle(photo.width / 2f, photo.height / 2f, size / 2f, paint)
        return result
    }

    private companion object {
        const val TAG = "ContactPhotos"

        // Son miniaturas (96 px de lado en Android de serie, unos 37 kB cada una): con 64 hay de
        // sobra para lo que la barra enseña a la vez.
        const val CACHE_SIZE = 64
    }
}
