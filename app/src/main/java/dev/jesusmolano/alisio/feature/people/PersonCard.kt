package dev.jesusmolano.alisio.feature.people

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.ui.components.Avatar
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.TonalButton
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val CardAvatarSize = 56.dp

/** Nombre de cada canal: lo que se escribe en su botón de la tarjeta. */
@StringRes
fun channelLabel(channel: Channel): Int = when (channel) {
    Channel.WHATSAPP_CHAT -> R.string.channel_whatsapp
    Channel.WHATSAPP_CALL -> R.string.channel_whatsapp_call
    Channel.WHATSAPP_VIDEO -> R.string.channel_whatsapp_video
    Channel.CALL -> R.string.channel_call
    Channel.SMS -> R.string.channel_sms
}

/**
 * La foto de [person] para su avatar, o `null` mientras se lee, si no tiene o si [photos] es `null`
 * (fotos apagadas en los ajustes): quien la pinta enseña entonces las iniciales. La lectura no
 * bloquea; una foto ya leída sale desde el primer fotograma.
 */
@Composable
fun rememberContactPhoto(person: Person, photos: ContactPhotos?): Bitmap? {
    // El estado se rehace con la persona: una fila que pasa a ser de otra no enseña la foto anterior.
    val photo = remember(person, photos) { mutableStateOf(photos?.cached(person)) }
    LaunchedEffect(person, photos) { if (photo.value == null) photo.value = photos?.load(person) }
    return photo.value
}

/**
 * Persona elegida: sustituye a los resultados, sin caja, pegada a las salidas y al campo. El canal
 * principal es el botón relleno, que es también lo que hace Enter; los demás van debajo, cada uno
 * con su nombre. La fila de resultados solo enseña los primeros canales; aquí caben todos.
 *
 * @param channels en orden de uso; el primero es el principal.
 * @param learned el canal principal sale del historial: la tarjeta lo dice.
 * @param onForget olvida el canal aprendido; solo se ofrece cuando [learned].
 * @param onBack vuelve a los resultados sin contactar.
 * @param onOpenContact abre su ficha en la app de Contactos.
 * @param photo su foto para el avatar; sin ella van las iniciales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonCard(
    name: String,
    channels: List<Channel>,
    learned: Boolean,
    onForget: () -> Unit,
    onContact: (Channel) -> Unit,
    onBack: () -> Unit,
    onOpenContact: () -> Unit,
    modifier: Modifier = Modifier,
    photo: Bitmap? = null
) {
    val main = channels.firstOrNull()
    Column(modifier.fillMaxWidth().padding(horizontal = AlisioTokens.Outset)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                R.drawable.ic_arrow_back,
                stringResource(R.string.person_back),
                AlisioTokens.TextSecondary,
                onBack
            )
            Spacer(Modifier.width(4.dp))
            Avatar(name, CardAvatarSize, photo = photo)
            Spacer(Modifier.width(AlisioTokens.IconGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlisioTokens.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (learned && main != null) {
                    // Lo aprendido se corrige donde se ve (docs/IDEAS.md §2.6): «Olvidar» va junto al motivo.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.person_reason, stringResource(channelLabel(main))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlisioTokens.TextSecondary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TextAction(stringResource(R.string.learned_forget), onClick = onForget, secondary = true)
                    }
                }
            }
            RoundIconButton(
                R.drawable.ic_person,
                stringResource(R.string.person_open_contact),
                AlisioTokens.TextSecondary,
                onOpenContact
            )
        }
        if (main != null) {
            Spacer(Modifier.height(AlisioTokens.SheetTitleGap))
            PrimaryButton(
                text = stringResource(channelLabel(main)),
                onClick = { onContact(main) },
                modifier = Modifier.fillMaxWidth(),
                icon = channelGlyph(main),
                // Enter hace lo mismo que este botón.
                trailingIcon = R.drawable.ic_keyboard_return
            )
        }
        val others = channels.drop(1)
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { channel ->
                    TonalButton(
                        text = stringResource(channelLabel(channel)),
                        onClick = { onContact(channel) },
                        icon = channelGlyph(channel)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
