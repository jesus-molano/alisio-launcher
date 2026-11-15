package dev.jesusmolano.alisio.feature.people

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.initials
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** Nombre de cada canal: lo que lee TalkBack en su botón y lo que se escribe debajo en la tarjeta. */
@StringRes
fun channelLabel(channel: Channel): Int = when (channel) {
    Channel.WHATSAPP_CHAT -> R.string.channel_whatsapp
    Channel.WHATSAPP_CALL -> R.string.channel_whatsapp_call
    Channel.WHATSAPP_VIDEO -> R.string.channel_whatsapp_video
    Channel.CALL -> R.string.channel_call
    Channel.SMS -> R.string.channel_sms
}

/** Avatar con las iniciales de la persona. Decorativo: el nombre va siempre al lado. */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(AlisioTokens.AccentSoft, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = AlisioTokens.Accent
        )
    }
}

/**
 * Tarjeta de persona: todos sus canales, cada uno con su icono y su nombre debajo. La fila de
 * resultados solo enseña los primeros; aquí caben todos.
 *
 * @param channels en orden de uso; el primero es el principal y va marcado con el acento.
 * @param learned el canal principal sale del historial: la tarjeta lo dice.
 * @param onOpenContact abre su ficha en la app de Contactos.
 */
@Composable
fun PersonCard(
    name: String,
    channels: List<Channel>,
    learned: Boolean,
    whatsappIcon: Bitmap?,
    onContact: (Channel) -> Unit,
    onOpenContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AlisioTokens.CardShape
    Column(
        modifier
            .fillMaxWidth()
            .background(AlisioTokens.Surface, shape)
            .border(1.dp, AlisioTokens.CardBorder, shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = AlisioTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val main = channels.firstOrNull()
                if (learned && main != null) {
                    Text(
                        text = stringResource(R.string.person_reason, stringResource(channelLabel(main))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlisioTokens.TextSecondary
                    )
                }
            }
            val openContact = stringResource(R.string.person_open_contact)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenContact)
                    .semantics { contentDescription = openContact },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    // El nombre de la acción ya está en la semántica del botón.
                    contentDescription = null,
                    tint = AlisioTokens.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            channels.forEachIndexed { index, channel ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChannelButton(
                        channel = channel,
                        onClick = { onContact(channel) },
                        whatsappIcon = whatsappIcon,
                        main = index == 0
                    )
                    Spacer(Modifier.height(4.dp))
                    // El botón ya lleva este nombre para TalkBack.
                    Text(
                        text = stringResource(channelLabel(channel)),
                        style = AlisioTokens.DockLabel,
                        color = if (index == 0) AlisioTokens.Accent else AlisioTokens.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }
            }
        }
    }
}
