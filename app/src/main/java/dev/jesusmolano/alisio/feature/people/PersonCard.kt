package dev.jesusmolano.alisio.feature.people

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.MainChannel
import dev.jesusmolano.alisio.core.people.initials
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** Nombre de cada canal tal como se enseña en botones y chips. */
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
 * Tarjeta de persona de §6: quién es, por qué se propone ese canal y un botón principal con el
 * canal aprendido. Los demás canales van debajo, como chips.
 *
 * @param main canal del botón principal; si sale del historial, la tarjeta lo dice.
 */
@Composable
fun PersonCard(
    name: String,
    channels: Set<Channel>,
    main: MainChannel,
    onContact: (Channel) -> Unit,
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
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = AlisioTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (main.learned) {
                    Text(
                        text = stringResource(R.string.person_reason, stringResource(channelLabel(main.channel))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlisioTokens.TextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val pill = AlisioTokens.PillShape
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(pill)
                .background(AlisioTokens.Accent, pill)
                .clickable(role = Role.Button) { onContact(main.channel) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(channelLabel(main.channel)),
                style = MaterialTheme.typography.titleMedium,
                color = AlisioTokens.Background
            )
        }
        val others = Channel.entries.filter { it in channels && it != main.channel }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                others.forEach { channel -> Chip(stringResource(channelLabel(channel))) { onContact(channel) } }
            }
        }
    }
}
