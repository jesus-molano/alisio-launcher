package dev.jesusmolano.alisio.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A quién va la imagen elegida en el selector de fotos. Se guarda como texto con el estado de la
 * actividad: si se rehace con el selector abierto (al girar el teléfono), la instancia nueva sabe
 * qué hacer con la imagen.
 */
sealed interface ImageTarget {
    /** El icono propio de una app, por su clave. */
    data class App(val key: String) : ImageTarget

    /** El icono de la pila de un hueco del dock. */
    data class Stack(val position: Int) : ImageTarget

    fun encode(): String = when (this) {
        is App -> APP_PREFIX + key
        is Stack -> STACK_PREFIX + position
    }

    companion object {
        private const val APP_PREFIX = "app:"
        private const val STACK_PREFIX = "stack:"

        fun decode(text: String?): ImageTarget? = when {
            text == null -> null
            text.startsWith(APP_PREFIX) -> App(text.removePrefix(APP_PREFIX))
            text.startsWith(STACK_PREFIX) -> text.removePrefix(STACK_PREFIX).toIntOrNull()?.let(::Stack)
            else -> null
        }
    }
}

/**
 * Abre el selector de fotos del sistema (sin permiso de almacenamiento) para [ImageTarget]. La
 * actividad guarda la imagen, la pone donde toca y ofrece deshacerlo; `onPicked` solo se llama si
 * ha salido bien y quien la pidió sigue ahí. La pone la actividad: al volver del selector, las
 * pantallas que lo abrieron (los ajustes, Zonas) tienen que seguir abiertas.
 */
val LocalPickImage = staticCompositionLocalOf<(target: ImageTarget, onPicked: () -> Unit) -> Unit> { { _, _ -> } }
