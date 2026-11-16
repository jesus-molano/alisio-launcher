package dev.jesusmolano.alisio.feature.appmenu

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.core.icons.customLabel
import dev.jesusmolano.alisio.core.icons.drawableTitle
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.IconPackInfo
import dev.jesusmolano.alisio.data.apps.PackIcon
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ChoiceRow
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.components.TitleTextField
import dev.jesusmolano.alisio.ui.components.VectorTileIcon
import dev.jesusmolano.alisio.ui.components.selectedTile
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val GridCell = 64.dp
private val GridIconSize = 48.dp
private val NamePreviewHeight = 104.dp

/**
 * Lo que hace falta para cambiar cómo se ve una app: de dónde salen los iconos que se ofrecen y qué
 * hacer al elegir. Elegir aplica en el acto y quien lo da enseña «Deshacer»; la hoja se cierra para
 * que se vea esa barra y el resultado en su sitio.
 *
 * @param packIcons el dibujo de cada paquete de iconos instalado que cubre esa app.
 * @param drawables todos los dibujos de un paquete, por su nombre.
 * @param icon un dibujo de un paquete, pintado como icono; `null` si no se deja.
 * @param onChoose pone ese icono a la app; `null` vuelve al suyo.
 * @param onPickImage abre el selector de fotos y, si se elige una, la pone como icono de la app y
 *   llama a `onPicked` (la hoja se cierra entonces, no al abrir el selector).
 * @param onRename guarda el nombre escrito; vacío o el original vuelven al suyo.
 * @param onHide oculta la app o la vuelve a enseñar.
 */
class AppLook(
    val packs: suspend () -> List<IconPackInfo>,
    val packIcons: suspend (AppEntry) -> List<PackIcon>,
    val drawables: suspend (String) -> List<String>,
    val icon: suspend (packageName: String, drawable: String) -> Bitmap?,
    val onChoose: (AppEntry, CustomIcon?) -> Unit,
    val onPickImage: (AppEntry, onPicked: () -> Unit) -> Unit,
    val onRename: (AppEntry, String) -> Unit,
    val onHide: (AppEntry, Boolean) -> Unit
)

/**
 * El grupo «Aspecto» del Interior de app: el icono y el nombre (cada uno abre su paso en la misma
 * hoja) y ocultar la app. Un secundario dice lo que no se ve a simple vista: de dónde sale el icono,
 * el nombre original y qué hace ocultarla.
 */
@Composable
internal fun LookRows(app: AppEntry, onIcon: () -> Unit, onName: () -> Unit, onHide: (Boolean) -> Unit) {
    SectionLabel(stringResource(R.string.appmenu_look))
    ItemRow(
        title = AnnotatedString(stringResource(R.string.icon_title)),
        icon = app.icon,
        subtitle =
            stringResource(
                when (app.customIcon) {
                    null -> R.string.icon_source_original
                    is CustomIcon.Pack -> R.string.icon_source_pack
                    is CustomIcon.Image -> R.string.icon_source_image
                }
            ),
        trailing = stringResource(R.string.settings_change),
        onClick = onIcon
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.name_title)),
        leadingIcon = R.drawable.ic_edit,
        // Sin renombrar, el secundario repetiría el nombre de la cabecera.
        subtitle = if (app.renamed) stringResource(R.string.name_original, app.originalLabel) else null,
        trailing = stringResource(R.string.settings_change),
        onClick = onName
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.hide_title)),
        leadingIcon = R.drawable.ic_visibility_off,
        subtitle = stringResource(R.string.hide_hint).takeIf { app.hidden },
        subtitleLines = 2,
        checked = app.hidden,
        onClick = { onHide(!app.hidden) }
    )
}

/**
 * El icono de una app en su propia hoja: lo abre Ajustes → Aspecto, que no pasa por el Interior.
 */
@Composable
fun AppIconSheet(app: AppEntry, look: AppLook, onDismiss: () -> Unit) {
    AlisioSheet(onDismiss = onDismiss) {
        IconStep(app, look, onDone = onDismiss, onBack = null)
    }
}

/**
 * Elige el icono de [app], viendo cada uno antes de tocarlo. Arriba, las teselas con lo más
 * probable: el suyo, el de cada paquete instalado que la cubre y una imagen de la galería. Debajo,
 * cualquier dibujo de un paquete, con buscador al pie como en la barra.
 *
 * @param onDone se ha elegido: quien abre el paso cierra la hoja.
 * @param onBack con valor, el paso va dentro de otra hoja (el Interior) y lleva botón de volver.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.IconStep(app: AppEntry, look: AppLook, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val packIcons by produceState(emptyList<PackIcon>(), app.key) { value = look.packIcons(app) }
    val packs by produceState(emptyList<IconPackInfo>()) { value = look.packs() }
    var browsing by remember { mutableStateOf<String?>(null) }
    val shownPack = browsing ?: (app.customIcon as? CustomIcon.Pack)?.packageName ?: packs.firstOrNull()?.packageName
    var query by remember { mutableStateOf("") }
    val chosen = app.customIcon

    fun choose(icon: CustomIcon?) {
        look.onChoose(app, icon)
        onDone()
    }

    SheetTitle(stringResource(R.string.icon_title), onBack = onBack)
    // Cada tesela dibuja el icono tal como quedará: se elige mirando, no leyendo de dónde sale.
    ChoiceRow(count = packIcons.size + 2) { tile ->
        PreviewChoice(stringResource(R.string.icon_original), chosen == null, { choose(null) }, tile) {
            AppIcon(app.originalIcon, AlisioTokens.DockIconSize)
        }
        packIcons.forEach { option ->
            val icon = CustomIcon.Pack(option.pack.packageName, option.drawable)
            PreviewChoice(option.pack.label, chosen == icon, { choose(icon) }, tile) {
                AppIcon(option.icon, AlisioTokens.DockIconSize)
            }
        }
        // Tocarla siempre abre el selector: también para cambiar la imagen que ya tiene.
        PreviewChoice(stringResource(R.string.icon_image), chosen is CustomIcon.Image, {
            look.onPickImage(app, onDone)
        }, tile) {
            if (chosen is CustomIcon.Image) {
                AppIcon(app.icon, AlisioTokens.DockIconSize)
            } else {
                VectorTileIcon(R.drawable.ic_image)
            }
        }
    }
    if (packs.isEmpty()) {
        Note(
            stringResource(R.string.icon_no_packs),
            Modifier.padding(horizontal = AlisioTokens.Outset, vertical = 12.dp)
        )
        Spacer(Modifier.height(16.dp))
        return
    }
    SectionLabel(stringResource(R.string.icon_any), Modifier.padding(horizontal = AlisioTokens.Outset))
    // Con un solo paquete no hay nada que elegir: sus dibujos salen directamente.
    if (packs.size > 1) {
        FlowRow(
            Modifier.padding(horizontal = AlisioTokens.Outset + 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            packs.forEach { pack ->
                Chip(label = pack.label, selected = pack.packageName == shownPack, onClick = {
                    browsing =
                        pack.packageName
                })
            }
        }
    }
    shownPack?.let { pack ->
        val names by produceState(emptyList<String>(), pack) { value = look.drawables(pack) }
        val found = remember(names, query) { names.filterByName(query, ::drawableTitle) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GridCell),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AlisioTokens.Outset, vertical = 8.dp)
        ) {
            items(found, key = { it }) { name ->
                val icon = CustomIcon.Pack(pack, name)
                PackCell(pack, name, selected = chosen == icon, look = look, onClick = { choose(icon) })
            }
        }
    }
    PillTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.icon_search),
        leadingIcon = R.drawable.ic_search,
        modifier =
            Modifier
                .imePadding()
                .padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
    )
}

/** Un dibujo del paquete en la rejilla; se pinta fuera del hilo principal al aparecer. */
@Composable
private fun PackCell(pack: String, name: String, selected: Boolean, look: AppLook, onClick: () -> Unit) {
    val icon by produceState<Bitmap?>(null, pack, name) { value = look.icon(pack, name) }
    val title = drawableTitle(name)
    val shape = AlisioTokens.DockIconShape
    Box(
        Modifier
            .size(GridCell)
            .clip(shape)
            .selectedTile(selected, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center
    ) { icon?.let { AppIcon(it, GridIconSize) } }
}

/**
 * El nombre propio de [app]. Mientras se escribe, la muestra enseña el icono con ese nombre tal como
 * saldrá en el dock. Guardar el original, o dejarlo vacío, vuelve al de la app.
 *
 * @param onDone se ha guardado: quien abre el paso cierra la hoja.
 */
@Composable
internal fun ColumnScope.NameStep(app: AppEntry, look: AppLook, onDone: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(app.label) }
    val shown = name.trim().ifEmpty { app.originalLabel }
    // Solo se guarda lo que cambia algo: el mismo nombre de ahora no es un cambio.
    val changes = customLabel(name, app.originalLabel) != customLabel(app.label, app.originalLabel)

    fun save(value: String) {
        look.onRename(app, value)
        onDone()
    }

    // Se entra a este paso para escribir: el teclado sale ya, sin otro toque en el campo.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    SheetTitle(stringResource(R.string.name_title), onBack = onBack)
    TitleTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = app.originalLabel,
        onEnter = { if (changes) save(name) },
        modifier = Modifier.focusRequester(focus)
    )
    Spacer(Modifier.height(AlisioTokens.SheetTitleGap - 8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AlisioTokens.Outset)
            .heightIn(min = NamePreviewHeight)
            .clip(AlisioTokens.CardShape)
            .background(AlisioTokens.Fill, AlisioTokens.CardShape),
        contentAlignment = Alignment.Center
    ) {
        DockIcon(label = shown, showLabel = true) { AppIcon(app.icon, AlisioTokens.DockIconSize) }
    }
    // Lo que no se ve en la muestra: el nombre de la app sigue sirviendo para buscarla.
    Note(
        stringResource(R.string.name_search_hint, app.originalLabel),
        Modifier.padding(horizontal = AlisioTokens.Outset, vertical = 8.dp)
    )
    PrimaryButton(
        text = stringResource(R.string.name_save),
        onClick = { save(name) },
        enabled = changes,
        modifier = Modifier.fillMaxWidth().padding(horizontal = AlisioTokens.Outset)
    )
    if (app.renamed) {
        Spacer(Modifier.height(8.dp))
        ItemRow(
            title = AnnotatedString(stringResource(R.string.name_restore)),
            leadingIcon = R.drawable.ic_restart,
            secondary = true,
            onClick = { save(app.originalLabel) },
            modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
        )
    }
    Spacer(Modifier.height(16.dp).imePadding())
}
