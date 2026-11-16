package dev.jesusmolano.alisio.core.icons

/**
 * El icono que el dueño ha elegido para una app en lugar del suyo. [id] es lo que se guarda.
 */
sealed interface CustomIcon {
    val id: String

    /** Un dibujo concreto de un paquete de iconos instalado, por su nombre en el paquete. */
    data class Pack(val packageName: String, val drawable: String) : CustomIcon {
        override val id = PACK_PREFIX + packageName + SEPARATOR + drawable
    }

    /** Una imagen de la galería, ya recortada y guardada en el almacenamiento interno con este nombre. */
    data class Image(val file: String) : CustomIcon {
        override val id = IMAGE_PREFIX + file
    }

    companion object {
        private const val PACK_PREFIX = "pack:"
        private const val IMAGE_PREFIX = "image:"
        private const val SEPARATOR = "/"

        /** `null` para lo que falta o no se reconoce: la app vuelve a su icono. */
        fun parse(id: String?): CustomIcon? = when {
            id == null -> null

            id.startsWith(PACK_PREFIX) -> {
                val rest = id.removePrefix(PACK_PREFIX)
                val slash = rest.indexOf(SEPARATOR)
                if (slash <= 0 || slash == rest.lastIndex) null else Pack(rest.take(slash), rest.substring(slash + 1))
            }

            id.startsWith(IMAGE_PREFIX) -> id.removePrefix(IMAGE_PREFIX).takeIf(::isImageName)?.let(::Image)

            else -> null
        }

        /**
         * Un nombre de imagen guardada: sin rutas, para que lo leído de la base no pueda señalar un
         * archivo fuera de la carpeta de iconos.
         */
        fun isImageName(name: String): Boolean =
            name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
                !name.startsWith(".")
    }
}

/**
 * El nombre propio de una app tal como se guarda: sin espacios sobrantes y `null` si queda vacío o
 * es el mismo que el original, que entonces vuelve a mandar.
 */
fun customLabel(input: String, original: String): String? = input.trim().takeIf { it.isNotEmpty() && it != original }

/** El cuadrado mayor centrado en una imagen de [width] × [height]: lo que queda al recortarla a icono. */
data class CenterSquare(val left: Int, val top: Int, val size: Int)

fun centerSquare(width: Int, height: Int): CenterSquare {
    val size = minOf(width, height)
    return CenterSquare((width - size) / 2, (height - size) / 2, size)
}

/**
 * Cómo se lee el nombre de un dibujo de un paquete («google_maps» es «google maps»), para buscarlo
 * por lo que dice y no por cómo lo ha escrito el autor del paquete.
 */
fun drawableTitle(name: String): String = name.replace('_', ' ').replace('-', ' ').trim()
