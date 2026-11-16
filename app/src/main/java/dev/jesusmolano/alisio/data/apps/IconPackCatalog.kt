package dev.jesusmolano.alisio.data.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** El dibujo que un paquete instalado tiene para una app concreta, ya pintado. */
class PackIcon(val pack: IconPackInfo, val drawable: String, val icon: Bitmap)

/**
 * Los paquetes de iconos instalados, para elegir el icono de una sola app: qué dibujo tiene cada
 * paquete para ella y todos los dibujos de un paquete. Lee y pinta fuera del hilo principal y lo
 * guarda en memoria; al instalar o quitar apps se olvida (ver [forget]).
 */
class IconPackCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val sizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).roundToInt()

    // Un paquete leído es su appfilter entero: se lee una vez por paquete.
    private val packs = ConcurrentHashMap<String, IconPack>()

    // Un paquete puede tener miles de dibujos: solo se guardan los últimos pintados.
    private val rendered = LruCache<String, Bitmap>(RENDERED_CACHE)

    @Volatile private var installed: List<IconPackInfo>? = null

    suspend fun installed(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        installed ?: installedIconPacks(appContext).also { installed = it }
    }

    /** El dibujo de cada paquete instalado que cubre esa actividad, en el orden de los paquetes. */
    suspend fun iconsFor(component: ComponentName): List<PackIcon> = withContext(Dispatchers.IO) {
        installed().mapNotNull { info ->
            val name = pack(info.packageName)?.drawableName(component) ?: return@mapNotNull null
            icon(info.packageName, name)?.let { PackIcon(info, name, it) }
        }
    }

    /** Los nombres de todos los dibujos de un paquete; vacío si no se deja leer. */
    suspend fun names(packageName: String): List<String> = withContext(Dispatchers.IO) {
        pack(packageName)?.names.orEmpty()
    }

    /** Un dibujo de un paquete pintado como icono, o `null` si ya no existe. Se llama fuera del hilo principal. */
    fun icon(packageName: String, drawable: String): Bitmap? {
        val key = "$packageName/$drawable"
        rendered.get(key)?.let { return it }
        val image = pack(packageName)?.drawable(drawable) ?: return null
        return try {
            renderPackIcon(image, sizePx).also { rendered.put(key, it) }
        } catch (e: RuntimeException) {
            // Un dibujo defectuoso del paquete no puede tumbar la hoja ni la carga de apps.
            Log.w(TAG, "Dibujo imposible de pintar en $packageName", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Dibujo demasiado grande en $packageName", e)
            null
        }
    }

    suspend fun iconAsync(packageName: String, drawable: String): Bitmap? =
        withContext(Dispatchers.IO) { icon(packageName, drawable) }

    /** Un paquete instalado, actualizado o quitado cambia lo que hay: se vuelve a leer al pedirlo. */
    fun forget() {
        installed = null
        packs.clear()
        rendered.evictAll()
    }

    private fun pack(packageName: String): IconPack? =
        packs[packageName] ?: IconPack.load(appContext, packageName)?.also { packs[packageName] = it }

    private companion object {
        const val TAG = "IconPackCatalog"
        const val RENDERED_CACHE = 240
    }
}
