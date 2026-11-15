package dev.jesusmolano.alisio.feature.people

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Verde de WhatsApp: sus canales se reconocen por el color aunque el dibujo sea el mismo que el de la llamada normal.
private val WhatsAppGreen = Color(0xFF25D366)

@DrawableRes
private fun channelGlyph(channel: Channel): Int = when (channel) {
    Channel.WHATSAPP_CHAT -> R.drawable.ic_channel_chat
    Channel.WHATSAPP_CALL, Channel.CALL -> R.drawable.ic_channel_call
    Channel.WHATSAPP_VIDEO -> R.drawable.ic_channel_video
    Channel.SMS -> R.drawable.ic_channel_sms
}

/**
 * Botón redondo de un canal: toca y contacta, sin pasos intermedios. El chat de WhatsApp enseña el
 * icono de la app si se le pasa; los demás canales de WhatsApp van en su verde.
 *
 * @param main lo marca con el acento: es el canal que usa Enter.
 */
@Composable
fun ChannelButton(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    whatsappIcon: Bitmap? = null,
    main: Boolean = false
) {
    val label = stringResource(channelLabel(channel))
    val whatsapp =
        channel == Channel.WHATSAPP_CHAT || channel == Channel.WHATSAPP_CALL || channel == Channel.WHATSAPP_VIDEO
    // 48 dp de toque con un círculo de 40 dp dentro.
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        val ring = if (main) Modifier.border(1.5.dp, AlisioTokens.Accent, CircleShape) else Modifier
        if (channel == Channel.WHATSAPP_CHAT && whatsappIcon != null) {
            Box(Modifier.size(40.dp).then(ring), contentAlignment = Alignment.Center) {
                AppIcon(whatsappIcon, 30.dp)
            }
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (whatsapp) WhatsAppGreen.copy(alpha = 0.16f) else AlisioTokens.SurfaceElevated,
                        CircleShape
                    )
                    .then(ring),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(channelGlyph(channel)),
                    // El nombre del canal ya está en la semántica del botón.
                    contentDescription = null,
                    tint = if (whatsapp) WhatsAppGreen else AlisioTokens.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
