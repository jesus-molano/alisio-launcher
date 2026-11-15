package dev.jesusmolano.alisio.core.icons

/** Estilo de los iconos de app, elegible en los ajustes de Alisio. [id] es lo que se guarda. */
sealed interface IconStyle {
    val id: String

    /** A color, todos con la misma forma (cuadrado redondeado). */
    data object Color : IconStyle {
        override val id = "color"
    }

    /** Monocromo: la capa temática de la app si la trae; si no, su icono en escala de grises. */
    data object Monochrome : IconStyle {
        override val id = "mono"
    }

    /** Tal como los dibuja el sistema, con la forma de One UI. */
    data object System : IconStyle {
        override val id = "system"
    }

    /** Paquete de iconos instalado; las apps que no cubre usan [Color]. */
    data class Pack(val packageName: String) : IconStyle {
        override val id = PACK_PREFIX + packageName
    }

    companion object {
        private const val PACK_PREFIX = "pack:"

        /** Lo guardado puede faltar o venir de una versión anterior: lo que no se reconoce es [Color]. */
        fun parse(id: String?): IconStyle = when {
            id == Monochrome.id -> Monochrome

            id == System.id -> System

            id != null && id.length > PACK_PREFIX.length && id.startsWith(PACK_PREFIX) ->
                Pack(id.removePrefix(PACK_PREFIX))

            else -> Color
        }
    }
}

private const val COMPONENT_PREFIX = "ComponentInfo{"

/**
 * Saca `paquete/actividad` del atributo `component` de un `appfilter.xml`
 * (`ComponentInfo{paquete/actividad}`); `null` si no tiene esa forma.
 */
fun appFilterComponent(value: String): String? {
    if (!value.startsWith(COMPONENT_PREFIX) || !value.endsWith("}")) return null
    return value.substring(COMPONENT_PREFIX.length, value.length - 1).takeIf { '/' in it }
}
