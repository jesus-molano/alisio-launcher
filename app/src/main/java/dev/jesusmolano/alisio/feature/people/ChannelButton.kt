package dev.jesusmolano.alisio.feature.people

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// El icono de WhatsApp ya es una forma rellena: el principal ocupa lo que el círculo de los demás.
private val MainAppIconSize = 40.dp
private val AppIconSize = 28.dp

/** El dibujo de cada canal. */
@DrawableRes
fun channelGlyph(channel: Channel): Int = when (channel) {
    Channel.WHATSAPP_CHAT -> R.drawable.ic_channel_chat
    Channel.WHATSAPP_CALL, Channel.CALL -> R.drawable.ic_channel_call
    Channel.WHATSAPP_VIDEO -> R.drawable.ic_channel_video
    Channel.SMS -> R.drawable.ic_channel_sms
}

/** Lo que TalkBack lee en el botón de un canal, con el nombre de la persona: «Llamar a Laura». */
@StringRes
private fun channelActionFor(channel: Channel): Int = when (channel) {
    Channel.WHATSAPP_CHAT -> R.string.channel_whatsapp_to
    Channel.WHATSAPP_CALL -> R.string.channel_whatsapp_call_to
    Channel.WHATSAPP_VIDEO -> R.string.channel_whatsapp_video_to
    Channel.CALL -> R.string.channel_call_to
    Channel.SMS -> R.string.channel_sms_to
}

/**
 * Botón redondo de un canal en la fila de una persona: toca y contacta, sin pasos intermedios. El
 * chat de WhatsApp enseña el icono de la app si se le pasa; los demás canales de WhatsApp van en
 * su verde.
 *
 * @param personName a quién se contacta: en una lista con varias personas, TalkBack lo dice en cada botón.
 * @param main va relleno: es el canal que usa Enter.
 */
@Composable
fun ChannelButton(
    channel: Channel,
    personName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    whatsappIcon: Bitmap? = null,
    main: Boolean = false
) {
    val label = stringResource(channelActionFor(channel), personName)
    val state = if (main) stringResource(R.string.channel_main) else null
    val whatsapp =
        channel == Channel.WHATSAPP_CHAT || channel == Channel.WHATSAPP_CALL || channel == Channel.WHATSAPP_VIDEO
    if (channel == Channel.WHATSAPP_CHAT && whatsappIcon != null) {
        Box(
            modifier
                .size(AlisioTokens.MinTouch)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    if (state != null) stateDescription = state
                },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(whatsappIcon, if (main) MainAppIconSize else AppIconSize)
        }
    } else {
        RoundIconButton(
            icon = channelGlyph(channel),
            description = label,
            tint =
                when {
                    main -> AlisioTokens.Background
                    whatsapp -> AlisioTokens.WhatsAppGreen
                    else -> AlisioTokens.Text
                },
            onClick = onClick,
            modifier = modifier,
            iconSize = if (main) 20.dp else 24.dp,
            container = if (main) (if (whatsapp) AlisioTokens.WhatsAppGreen else AlisioTokens.Accent) else null,
            state = state
        )
    }
}
