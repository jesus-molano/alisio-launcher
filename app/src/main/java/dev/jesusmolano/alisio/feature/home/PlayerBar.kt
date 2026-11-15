package dev.jesusmolano.alisio.feature.home

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** Alto fijo: el índice de Trazo se acorta justo ese tanto cuando el reproductor sale. */
internal val PlayerBarHeight = 64.dp

private val CoverSize = 44.dp
private val BarPadding = 10.dp

// Radio de la píldora menos el margen: la esquina de la carátula corre paralela a la del reproductor.
private val CoverShape = RoundedCornerShape(18.dp)

/**
 * Reproductor del Inicio (§6): una sola pieza con la carátula, el título, la app y dos botones.
 * Tocar fuera de los botones abre la app que suena.
 *
 * @param appIcon icono de la app, para cuando la sesión no trae carátula.
 */
@Composable
internal fun PlayerBar(
    player: NowPlaying,
    appIcon: Bitmap?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AlisioTokens.PillShape
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlayerBarHeight)
                .clip(shape)
                // Más translúcido que la tarjeta de Ahora y sin borde: acompaña, no pide atención.
                .background(AlisioTokens.Surface.copy(alpha = 0.72f), shape)
                .clickable(onClickLabel = stringResource(R.string.player_open, player.appLabel), onClick = onOpen)
                .padding(start = BarPadding, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(player.art, appIcon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = player.title,
                style = MaterialTheme.typography.titleMedium,
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    player.artist?.let { stringResource(R.string.player_subtitle, it, player.appLabel) }
                        ?: player.appLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = AlisioTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (player.playing) {
            PlayerButton(R.drawable.ic_pause, stringResource(R.string.player_pause), AlisioTokens.Text, onToggle)
        } else {
            PlayerButton(R.drawable.ic_play, stringResource(R.string.player_play), AlisioTokens.Text, onToggle)
        }
        if (player.canSkip) {
            PlayerButton(
                R.drawable.ic_skip_next,
                stringResource(R.string.player_next),
                AlisioTokens.TextSecondary,
                onNext
            )
        }
    }
}

@Composable
private fun Cover(art: Bitmap?, appIcon: Bitmap?) {
    when {
        art != null ->
            Image(
                bitmap = remember(art) { art.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(CoverSize).clip(CoverShape)
            )

        appIcon != null -> AppIcon(appIcon, CoverSize)

        else ->
            Box(
                Modifier.size(CoverSize).background(AlisioTokens.SurfaceElevated, CoverShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = AlisioTokens.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
    }
}

@Composable
private fun PlayerButton(@DrawableRes icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}
