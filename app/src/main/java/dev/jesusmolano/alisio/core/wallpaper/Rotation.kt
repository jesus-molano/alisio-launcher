package dev.jesusmolano.alisio.core.wallpaper

import java.util.Locale

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp")

/** Si un archivo de la carpeta sirve de fondo: por su tipo o, si no lo dice, por su extensión. */
fun isWallpaperImage(name: String?, mime: String?): Boolean {
    if (mime != null && mime.startsWith("image/")) return true
    val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return false
    return extension in IMAGE_EXTENSIONS
}

/**
 * El fondo que toca hoy, como posición en la lista de la carpeta ya ordenada por nombre; `null` si
 * hoy ya se cambió o no hay imágenes. Se avanza uno por día, sin saltar los días en que no se miró
 * el Inicio: así se ven todas en orden.
 *
 * @param lastDay el día (desde 1970) en que se puso el último; `null` si nunca.
 * @param lastIndex su posición entonces.
 */
fun nextWallpaper(today: Long, lastDay: Long?, lastIndex: Int, count: Int): Int? {
    if (count <= 0) return null
    if (lastDay != null && lastDay >= today) return null
    if (lastDay == null) return 0
    return (lastIndex + 1).mod(count)
}

/** Orden estable de la carpeta: por nombre, sin distinguir mayúsculas, como lo enseña el sistema. */
fun <T> wallpaperOrder(items: List<T>, name: (T) -> String): List<T> =
    items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
