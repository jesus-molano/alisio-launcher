package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val TrackHeight = 12.dp
private val ThumbSize = 28.dp
private val ThumbInset = 4.dp

/**
 * Un deslizador de una cualidad del color (tono, saturación o brillo). La pista es el propio color
 * en todo su recorrido: se ve qué hay a cada lado antes de mover el dedo. Es el único sitio de la app
 * con degradado, porque aquí el degradado es lo que se elige, no un adorno (docs/DISENO.md §5).
 *
 * @param track el recorrido del color, de un extremo al otro.
 * @param thumb el color que hay ahora bajo el pulgar; va dentro de un anillo claro que se ve sobre
 *   cualquier parte de la pista.
 * @param valueText lo que TalkBack dice del valor («210°», «80 %»).
 * @param onValueChangeFinished al soltar: es cuando se guarda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    track: Brush,
    thumb: Color,
    valueText: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AlisioTokens.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            interactionSource = interaction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AlisioTokens.MinTouch)
                    .semantics {
                        contentDescription = label
                        stateDescription = valueText
                    },
            thumb = {
                Box(
                    Modifier.size(ThumbSize).background(AlisioTokens.Text, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(ThumbSize - ThumbInset * 2).background(thumb, CircleShape))
                }
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TrackHeight)
                        .clip(AlisioTokens.PillShape)
                        .background(track)
                )
            }
        )
    }
}
