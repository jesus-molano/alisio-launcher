package dev.jesusmolano.alisio.core.extras

import dev.jesusmolano.alisio.core.extras.ClockCommand.Alarm
import dev.jesusmolano.alisio.core.extras.ClockCommand.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockCommandTest {
    @Test
    fun `alarma con hora y minutos`() {
        assertEquals(Alarm(7, 30), parseClockCommand("alarma 7:30"))
        assertEquals(Alarm(7, 30), parseClockCommand("alarm 7.30"))
        assertEquals(Alarm(19, 5), parseClockCommand("Alarma 19:05"))
        assertEquals(Alarm(7, 30), parseClockCommand("despertador 7h30"))
        assertEquals(Alarm(0, 0), parseClockCommand("alarma 0:00"))
    }

    @Test
    fun `alarma en punto`() {
        assertEquals(Alarm(7, 0), parseClockCommand("alarma 7"))
        assertEquals(Alarm(23, 0), parseClockCommand("alarma 23h"))
        assertEquals(Alarm(7, 0), parseClockCommand("alarma a las 7"))
        assertEquals(Alarm(7, 15), parseClockCommand("alarm at 7:15"))
    }

    @Test
    fun `alarma con am y pm`() {
        assertEquals(Alarm(19, 30), parseClockCommand("alarm 7:30 pm"))
        assertEquals(Alarm(7, 0), parseClockCommand("alarm 7am"))
        assertEquals(Alarm(0, 15), parseClockCommand("alarm 12:15 am"))
        assertEquals(Alarm(12, 0), parseClockCommand("alarm 12 pm"))
        assertEquals(Alarm(21, 0), parseClockCommand("alarma 9 p.m."))
        assertNull(parseClockCommand("alarm 13 pm"))
        assertNull(parseClockCommand("alarm 0 am"))
    }

    @Test
    fun `horas que no existen`() {
        assertNull(parseClockCommand("alarma 24:00"))
        assertNull(parseClockCommand("alarma 7:60"))
        assertNull(parseClockCommand("alarma 7:3"))
        assertNull(parseClockCommand("alarma 730"))
        assertNull(parseClockCommand("alarma mañana"))
    }

    @Test
    fun `temporizador en minutos por defecto`() {
        assertEquals(Timer(600), parseClockCommand("temporizador 10"))
        assertEquals(Timer(600), parseClockCommand("timer 10"))
        assertEquals(Timer(600), parseClockCommand("Temporizador de 10 minutos"))
        assertEquals(Timer(300), parseClockCommand("timer for 5 min"))
    }

    @Test
    fun `temporizador con unidades`() {
        assertEquals(Timer(600), parseClockCommand("timer 10m"))
        assertEquals(Timer(90), parseClockCommand("timer 90s"))
        assertEquals(Timer(3600), parseClockCommand("temporizador 1h"))
        assertEquals(Timer(5400), parseClockCommand("timer 1h 30m"))
        assertEquals(Timer(5400), parseClockCommand("timer 1h30"))
        assertEquals(Timer(150), parseClockCommand("timer 2m30"))
        assertEquals(Timer(3690), parseClockCommand("temporizador 1 hora y 1 minuto 30 segundos"))
    }

    @Test
    fun `temporizador con forma de reloj`() {
        assertEquals(Timer(90), parseClockCommand("timer 1:30"))
        assertEquals(Timer(3900), parseClockCommand("timer 1:05:00"))
        assertNull(parseClockCommand("timer 1:75"))
    }

    @Test
    fun `duraciones que no valen`() {
        assertNull(parseClockCommand("timer 0"))
        // El reloj no acepta más de un día.
        assertNull(parseClockCommand("timer 25h"))
        assertNull(parseClockCommand("timer 99999999999999999999"))
        assertNull(parseClockCommand("timer 10 30"))
        assertNull(parseClockCommand("timer 30m 1h"))
        assertNull(parseClockCommand("timer 10 patatas"))
        assertNull(parseClockCommand("timer pizza"))
    }

    @Test
    fun `la palabra sola o cualquier otra cosa es una busqueda normal`() {
        assertNull(parseClockCommand("alarma"))
        assertNull(parseClockCommand("timer"))
        assertNull(parseClockCommand("temporizador "))
        assertNull(parseClockCommand("7:30"))
        assertNull(parseClockCommand("alarmas 7:30"))
        assertNull(parseClockCommand(""))
    }
}
