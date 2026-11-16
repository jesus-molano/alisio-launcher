package dev.jesusmolano.alisio.core.theme

/**
 * Cuánto tapan el fondo de pantalla las placas del pie del Inicio (píldora, Ahora, En curso y
 * reproductor). Los alfas de cada nivel viven en los tokens (`AlisioTokens.homePlate`): [SOLID] es la
 * placa de siempre. Es una opción del dueño frente a DISENO §2.4, que deja la placa a 0,86.
 */
enum class PlateOpacity {
    SOLID,
    MEDIUM,
    LOW
}
