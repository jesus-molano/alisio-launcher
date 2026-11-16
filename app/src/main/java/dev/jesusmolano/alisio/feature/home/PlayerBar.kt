package dev.jesusmolano.alisio.feature.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlin.math.abs
import kotlinx.coroutines.launch

private val PlayerMinHeight = 64.dp
private val CoverSize = AlisioTokens.IconSlot
private val PlayerIconSize = 26.dp

// Recorrido en horizontal a partir del cual soltar aparta el reproductor.
private val HideDrag = 96.dp

// Lo de lado que tiene que ser el arrastre, frente a lo vertical, para que la placa se lo quede.
private const val SIDEWAYS_RATIO = 2f

/**
 * Reproductor del Inicio: una placa con la carátula, el título, la app y dos botones. Tocar fuera de
 * los botones abre la app que suena. Mismo material y mismo ancho que Ahora y la píldora.
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
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AlisioTokens.PillShape
    val hideLabel = stringResource(R.string.player_hide)
    // Lo que la placa se ha movido tras el dedo; al soltar vuelve a su sitio o se va.
    val slide = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hide by rememberUpdatedState(onHide)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlayerMinHeight)
                // Deslizarla a un lado la aparta: una sesión en pausa puede quedarse horas en el Inicio.
                .pointerInput(Unit) {
                    val hideAt = HideDrag.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Solo un arrastre claramente de lado es suyo: un pulgar que sube en diagonal
                        // desde la placa quiere la barra, y ese gesto es del Inicio.
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed || change.isConsumed) return@awaitEachGesture
                            val moved = change.position - down.position
                            if (moved.getDistance() <= viewConfiguration.touchSlop) continue
                            if (abs(moved.x) < SIDEWAYS_RATIO * abs(moved.y)) return@awaitEachGesture
                            change.consume()
                            break
                        }
                        // `horizontalDrag` da `false` si el gesto se cancela: entonces no se aparta.
                        val finished =
                            horizontalDrag(down.id) { change ->
                                scope.launch { slide.snapTo(slide.value + change.positionChange().x) }
                                change.consume()
                            }
                        if (finished && abs(slide.value) >= hideAt) hide()
                        scope.launch { slide.animateTo(0f, AlisioMotion.follow()) }
                    }
                }
                .graphicsLayer {
                    translationX = slide.value
                    // Se desvanece según se aleja: al pasar el umbral ya casi no está.
                    alpha = (1f - abs(slide.value) / (2 * HideDrag.toPx())).coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(AlisioTokens.Plate, shape)
                .clickable(onClickLabel = stringResource(R.string.player_open, player.appLabel), onClick = onOpen)
                // El gesto no existe para TalkBack: apartar el reproductor va como acción con nombre.
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(hideLabel) {
                                hide()
                                true
                            }
                        )
                }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(player.art, appIcon)
        Spacer(Modifier.width(AlisioTokens.IconGap))
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
        RoundIconButton(
            icon = if (player.playing) R.drawable.ic_pause else R.drawable.ic_play,
            description = stringResource(if (player.playing) R.string.player_pause else R.string.player_play),
            tint = AlisioTokens.Text,
            onClick = onToggle,
            iconSize = PlayerIconSize
        )
        if (player.canSkip) {
            RoundIconButton(
                icon = R.drawable.ic_skip_next,
                description = stringResource(R.string.player_next),
                tint = AlisioTokens.TextSecondary,
                onClick = onNext,
                iconSize = PlayerIconSize
            )
        }
    }
}

@Composable
private fun Cover(art: Bitmap?, appIcon: Bitmap?) {
    val shape = AlisioTokens.DockIconShape
    when {
        art != null ->
            Image(
                bitmap = remember(art) { art.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(CoverSize).clip(shape)
            )

        appIcon != null -> AppIcon(appIcon, CoverSize)

        else ->
            Box(Modifier.size(CoverSize).background(AlisioTokens.Fill, shape), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = AlisioTokens.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
    }
}
