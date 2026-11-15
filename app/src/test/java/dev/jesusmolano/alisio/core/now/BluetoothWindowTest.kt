package dev.jesusmolano.alisio.core.now

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothWindowTest {
    private val minute = 60_000L
    private val connectedAt = 1_000_000L
    private val car = ConnectedAudio("Coche", connectedAt)

    @Test
    fun `sin dispositivo conectado no se apunta nada`() {
        assertNull(deviceForOpening(null, connectedAt))
    }

    @Test
    fun `una apertura en los cinco minutos siguientes lleva el dispositivo`() {
        assertEquals("Coche", deviceForOpening(car, connectedAt))
        assertEquals("Coche", deviceForOpening(car, connectedAt + 5 * minute - 1))
    }

    @Test
    fun `pasada la ventana la apertura ya no lleva el dispositivo`() {
        assertNull(deviceForOpening(car, connectedAt + 5 * minute))
        assertNull(deviceForOpening(car, connectedAt + 60 * minute))
    }

    @Test
    fun `una apertura anterior a la conexion no cuenta`() {
        // El reloj del sistema puede retroceder (ajuste de hora) entre la conexión y la apertura.
        assertNull(deviceForOpening(car, connectedAt - 1))
    }

    @Test
    fun `con varios conectados cuenta el ultimo en conectarse`() {
        val watch = ConnectedAudio("Reloj", connectedAt - 60 * minute)
        assertEquals(car, currentAudio(listOf(watch, car)))
        assertNull(currentAudio(emptyList()))
    }

    @Test
    fun `dos conectados a la vez se desempatan siempre igual`() {
        val a = ConnectedAudio("A", connectedAt)
        val b = ConnectedAudio("B", connectedAt)
        assertEquals(a, currentAudio(listOf(a, b)))
        assertEquals(a, currentAudio(listOf(b, a)))
    }

    @Test
    fun `cada conexion cuenta una vez por app`() {
        val day = 24 * 60 * minute
        val openings =
            listOf(
                // Primera conexión: la música se abre dos veces y es un solo caso.
                BluetoothOpening("Coche", "music", connectedAt),
                BluetoothOpening("Coche", "music", connectedAt + 2 * minute),
                BluetoothOpening("Coche", "maps", connectedAt + minute),
                BluetoothOpening("Coche", "music", connectedAt + day),
                BluetoothOpening("Coche", "music", connectedAt + 2 * day),
                BluetoothOpening("Cascos", "music", connectedAt + 3 * day)
            )
        assertEquals(
            mapOf("Coche" to mapOf("music" to 3, "maps" to 1), "Cascos" to mapOf("music" to 1)),
            bluetoothCases(openings)
        )
    }

    @Test
    fun `los casos no dependen del orden de las filas`() {
        val openings =
            listOf(
                BluetoothOpening("Coche", "music", connectedAt + 2 * minute),
                BluetoothOpening("Coche", "music", connectedAt + 20 * minute),
                BluetoothOpening("Coche", "music", connectedAt)
            )
        assertEquals(mapOf("Coche" to mapOf("music" to 2)), bluetoothCases(openings))
    }
}
