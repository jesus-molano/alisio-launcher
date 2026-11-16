package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.feature.zones.ZoneEdit
import dev.jesusmolano.alisio.feature.zones.ZoneEditSheet
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Zonas: verlas y llevar las islas propias. Tocar una isla abre su hoja, la misma que en Zonas,
 * donde se renombra, se llena y se borra. Enseñar o no los nombres de las apps está en Aspecto.
 */
@Composable
internal fun ZonesPage(zones: ZoneSettings) {
    // Isla propia que se está creando o cambiando en su hoja.
    var editing by remember { mutableStateOf<ZoneEdit?>(null) }
    val editLabel = stringResource(R.string.settings_island_edit)

    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_zones_open)),
        leadingIcon = R.drawable.ic_zones,
        onClick = zones.onOpen
    )

    SectionLabel(stringResource(R.string.settings_islands))
    zones.islands.forEach { island ->
        val name = island.name.orEmpty()
        ItemRow(
            title = AnnotatedString(name),
            // Su primera app la hace reconocible; vacía, el dibujo de Zonas.
            icon = island.apps.firstOrNull()?.icon,
            leadingIcon = R.drawable.ic_zones,
            subtitle = pluralStringResource(R.plurals.zone_apps, island.apps.size, island.apps.size),
            onClickLabel = editLabel,
            onClick = { (island.id as? ZoneId.Custom)?.let { editing = ZoneEdit.Existing(it.id, name) } },
            actions = {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = AlisioTokens.TextSecondary,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(20.dp)
                )
            }
        )
    }
    ItemRow(
        title = AnnotatedString(stringResource(R.string.zone_new)),
        leadingIcon = R.drawable.ic_add,
        onClick = { editing = ZoneEdit.New }
    )

    editing?.let { edit ->
        ZoneEditSheet(edit = edit, viewModel = zones.editor, onDismiss = { editing = null })
    }
}
