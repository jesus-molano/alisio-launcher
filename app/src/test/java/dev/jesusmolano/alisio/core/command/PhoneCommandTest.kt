package dev.jesusmolano.alisio.core.command

import dev.jesusmolano.alisio.core.command.PhoneCommand.Brightness
import dev.jesusmolano.alisio.core.command.PhoneCommand.DoNotDisturb
import dev.jesusmolano.alisio.core.command.PhoneCommand.Panel
import dev.jesusmolano.alisio.core.command.PhoneCommand.Rotation
import dev.jesusmolano.alisio.core.command.PhoneCommand.Torch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCommandTest {
    @Test
    fun `linterna en los dos idiomas`() {
        assertEquals(Torch(null), parsePhoneCommand("linterna"))
        assertEquals(Torch(null), parsePhoneCommand("Flashlight"))
        assertEquals(Torch(Switch.ON), parsePhoneCommand("encender la linterna"))
        assertEquals(Torch(Switch.OFF), parsePhoneCommand("apaga linterna"))
        assertEquals(Torch(Switch.ON), parsePhoneCommand("turn on the torch"))
        assertEquals(Torch(Switch.OFF), parsePhoneCommand("flashlight off"))
    }

    @Test
    fun `paneles del sistema`() {
        assertEquals(Panel(PhonePanel.WIFI, null), parsePhoneCommand("wifi"))
        assertEquals(Panel(PhonePanel.WIFI, null), parsePhoneCommand("Wi-Fi"))
        assertEquals(Panel(PhonePanel.BLUETOOTH, Switch.OFF), parsePhoneCommand("desactivar bluetooth"))
        assertEquals(Panel(PhonePanel.MOBILE_DATA, null), parsePhoneCommand("datos móviles"))
        assertEquals(Panel(PhonePanel.LOCATION, Switch.ON), parsePhoneCommand("activar la ubicación"))
        assertEquals(Panel(PhonePanel.AIRPLANE, null), parsePhoneCommand("modo avión"))
        assertEquals(Panel(PhonePanel.AIRPLANE, Switch.ON), parsePhoneCommand("airplane mode on"))
    }

    @Test
    fun `no molestar y rotacion`() {
        assertEquals(DoNotDisturb(null), parsePhoneCommand("no molestar"))
        assertEquals(DoNotDisturb(Switch.ON), parsePhoneCommand("turn on do not disturb"))
        assertEquals(Rotation(null), parsePhoneCommand("rotación"))
        assertEquals(Rotation(Switch.OFF), parsePhoneCommand("auto-rotate off"))
    }

    @Test
    fun `brillo con y sin cifra`() {
        assertEquals(Brightness(40), parsePhoneCommand("brillo 40"))
        assertEquals(Brightness(40), parsePhoneCommand("brillo al 40 %"))
        assertEquals(Brightness(100), parsePhoneCommand("brightness to 100%"))
        assertEquals(Brightness(null), parsePhoneCommand("brillo"))
        assertNull(parsePhoneCommand("brillo 150"))
    }

    @Test
    fun `lo que no es una orden entera sigue siendo una busqueda`() {
        assertNull(parsePhoneCommand("wifi analyzer"))
        assertNull(parsePhoneCommand("linternas"))
        assertNull(parsePhoneCommand("bt"))
        assertNull(parsePhoneCommand("encender"))
        assertNull(parsePhoneCommand(""))
        assertNull(parsePhoneCommand("spotify"))
    }

    @Test
    fun `sin pedir nada se cambia al contrario`() {
        assertEquals(Switch.OFF, resolveSwitch(null, currentlyOn = true))
        assertEquals(Switch.ON, resolveSwitch(null, currentlyOn = false))
        assertEquals(Switch.ON, resolveSwitch(Switch.ON, currentlyOn = true))
        assertNull(resolveSwitch(null, currentlyOn = null))
    }

    @Test
    fun `el brillo nunca deja la pantalla negra`() {
        assertEquals(1, brightnessLevel(0))
        assertEquals(255, brightnessLevel(100))
        assertEquals(128, brightnessLevel(50))
    }
}
