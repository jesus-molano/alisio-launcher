package dev.jesusmolano.alisio.feature.search

import android.content.res.Resources
import androidx.annotation.DrawableRes
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.command.PhoneCommand
import dev.jesusmolano.alisio.core.command.PhonePanel
import dev.jesusmolano.alisio.core.command.Switch

/** El dibujo de cada orden en su fila de la barra. */
@DrawableRes
internal fun phoneIcon(command: PhoneCommand): Int = when (command) {
    is PhoneCommand.Torch -> R.drawable.ic_flashlight

    is PhoneCommand.Panel ->
        when (command.panel) {
            PhonePanel.WIFI -> R.drawable.ic_wifi
            PhonePanel.BLUETOOTH -> R.drawable.ic_bluetooth
            PhonePanel.MOBILE_DATA -> R.drawable.ic_mobile_data
            PhonePanel.LOCATION -> R.drawable.ic_place
            PhonePanel.AIRPLANE -> R.drawable.ic_airplane
        }

    is PhoneCommand.DoNotDisturb -> R.drawable.ic_do_not_disturb

    is PhoneCommand.Brightness -> R.drawable.ic_brightness

    is PhoneCommand.Rotation -> R.drawable.ic_screen_rotation
}

/** Lo que hará la orden, dicho como el título de su fila («Encender la linterna»). */
internal fun Resources.phoneTitle(command: PhoneCommand): String = when (command) {
    is PhoneCommand.Torch -> getString(
        switched(command.switch, R.string.phone_torch, R.string.phone_torch_on, R.string.phone_torch_off)
    )

    is PhoneCommand.Panel -> getString(panelName(command.panel))

    is PhoneCommand.DoNotDisturb -> getString(
        switched(command.switch, R.string.phone_dnd, R.string.phone_dnd_on, R.string.phone_dnd_off)
    )

    is PhoneCommand.Brightness -> command.percent?.let { getString(R.string.phone_brightness_to, it) }
        ?: getString(R.string.phone_brightness)

    is PhoneCommand.Rotation ->
        getString(
            switched(command.switch, R.string.phone_rotation, R.string.phone_rotation_on, R.string.phone_rotation_off)
        )
}

/**
 * La línea de debajo: solo cuando la orden no se hace en el sitio. Android no deja a otra app
 * encender el wifi o la ubicación, así que la fila dice que se abre su panel, y no promete otra cosa.
 */
internal fun Resources.phoneHint(command: PhoneCommand): String? = when (command) {
    is PhoneCommand.Panel -> getString(R.string.phone_opens_panel)
    is PhoneCommand.Brightness -> if (command.percent == null) getString(R.string.phone_opens_display) else null
    else -> null
}

/** Cómo ha quedado tras hacerla sin salir de Alisio, para el aviso corto. */
internal fun Resources.phoneDone(command: PhoneCommand, on: Boolean?): String {
    val lit = on == true
    return when (command) {
        is PhoneCommand.Torch -> getString(onOff(lit, R.string.phone_torch_done_on, R.string.phone_torch_done_off))

        is PhoneCommand.DoNotDisturb -> getString(onOff(lit, R.string.phone_dnd_done_on, R.string.phone_dnd_done_off))

        is PhoneCommand.Rotation -> getString(
            onOff(lit, R.string.phone_rotation_done_on, R.string.phone_rotation_done_off)
        )

        else -> phoneTitle(command)
    }
}

private fun onOff(on: Boolean, whenOn: Int, whenOff: Int): Int = if (on) whenOn else whenOff

private fun switched(switch: Switch?, neutral: Int, on: Int, off: Int): Int = when (switch) {
    null -> neutral
    Switch.ON -> on
    Switch.OFF -> off
}

private fun panelName(panel: PhonePanel): Int = when (panel) {
    PhonePanel.WIFI -> R.string.phone_wifi
    PhonePanel.BLUETOOTH -> R.string.phone_bluetooth
    PhonePanel.MOBILE_DATA -> R.string.phone_mobile_data
    PhonePanel.LOCATION -> R.string.phone_location
    PhonePanel.AIRPLANE -> R.string.phone_airplane
}
