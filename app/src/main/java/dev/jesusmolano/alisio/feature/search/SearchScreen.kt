package dev.jesusmolano.alisio.feature.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val ScreenMargin = 16.dp
private val CloseDrag = 64.dp

/**
 * Barra de §5.3: resultados apilados desde abajo, junto al teclado, con el mejor pegado a la barra.
 * Enter abre ese resultado; si no hay ninguno, pregunta a Claude (o busca en la web si no está).
 *
 * @param onClose se llama tras abrir algo; quien la muestra decide cómo cerrarla.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    // El texto vive aquí, no en un flujo: si volviera con retraso, el campo perdería letras y el cursor.
    var query by remember { mutableStateOf("") }
    val state = remember(query, source) { viewModel.search(query, source) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }
    val claudeInstalled = remember { context.isClaudeInstalled() }
    val copiedMessage = stringResource(R.string.search_copied)

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        focusRequester.requestFocus()
    }
    // Al cambiar los resultados la lista vuelve al que está pegado a la barra, que es el que abre Enter;
    // si no, conservaría la posición del elemento que estaba primero antes.
    LaunchedEffect(state) { listState.scrollToItem(0) }

    fun finish(opened: Boolean, failureLabel: String) {
        if (opened) {
            onClose()
        } else {
            Toast.makeText(context, resources.getString(R.string.app_launch_failed, failureLabel), Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun open(target: Target) = when (target) {
        is Target.App -> finish(viewModel.launch(target), target.app.label)

        is Target.Shortcut -> finish(viewModel.launch(target), target.shortcut.label)

        is Target.Setting -> {
            val opened = context.openSetting(target.setting.action)
            if (opened) viewModel.record(target)
            finish(opened, target.label)
        }
    }

    fun fallback(query: String) {
        val label = resources.getString(if (claudeInstalled) R.string.search_chip_claude else R.string.search_chip_web)
        finish(if (claudeInstalled) context.askClaude(query) else context.searchWeb(query), label)
    }

    fun copyCalculation(result: String) {
        context.copyToClipboard(result)
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        onClose()
    }

    fun onEnter() {
        val calculation = state.calculation
        val best = state.best
        when {
            calculation != null -> copyCalculation(calculation)
            best != null -> open(best.target)
            state.query.isNotBlank() -> fallback(state.query)
        }
    }

    val closeDragPx = with(LocalDensity.current) { CloseDrag.toPx() }
    // Deslizar hacia abajo cierra la barra, igual que deslizar hacia arriba la abre. Sobre la lista
    // solo cuenta lo que ella ya no puede desplazar; el gesto termina en cada fling.
    val closeOnPullDown =
        remember(onClose, closeDragPx) {
            object : NestedScrollConnection {
                var pulled = 0f

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0) {
                        pulled += available.y
                        if (pulled > closeDragPx) {
                            pulled = 0f
                            onClose()
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    pulled = 0f
                    return Velocity.Zero
                }
            }
        }

    Column(
        modifier
            .fillMaxSize()
            .background(AlisioTokens.Background)
            .nestedScroll(closeOnPullDown)
            // Fuera de la lista. De paso, la barra se queda los toques: no llegan al Inicio de debajo.
            .pointerInput(onClose) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = { if (dragged > closeDragPx) onClose() }
                )
            }
            .systemBarsPadding()
            .imePadding()
    ) {
        Text(
            text = formatClock(rememberNow()),
            style = MaterialTheme.typography.headlineLarge,
            color = AlisioTokens.TextTertiary.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 24.dp, top = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = 8.dp)
        ) {
            // Con reverseLayout el primer elemento es el más cercano a la barra.
            when {
                state.query.isBlank() -> recents(state, onOpen = ::open)

                else -> results(
                    state,
                    claudeInstalled,
                    onOpen = ::open,
                    onCopy = ::copyCalculation,
                    onFallback = ::fallback
                )
            }
        }
        Row(
            Modifier.padding(horizontal = ScreenMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (claudeInstalled) {
                Chip(stringResource(R.string.search_chip_claude)) {
                    finish(context.askClaude(state.query), resources.getString(R.string.search_chip_claude))
                }
            }
            Chip(stringResource(R.string.search_chip_web)) {
                finish(context.searchWeb(state.query), resources.getString(R.string.search_chip_web))
            }
            Chip(stringResource(R.string.search_chip_play)) {
                finish(context.searchPlayStore(state.query), resources.getString(R.string.search_chip_play))
            }
        }
        PillTextField(
            value = state.query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_hint),
            onEnter = ::onEnter,
            modifier =
                Modifier
                    .padding(start = ScreenMargin, end = ScreenMargin, bottom = 12.dp)
                    .focusRequester(focusRequester)
        )
    }
}

private fun LazyListScope.recents(state: SearchState, onOpen: (Target) -> Unit) {
    if (state.recents.isEmpty()) return
    items(state.recents.size, key = { state.recents[it].key.toString() }) { index ->
        val app = state.recents[index]
        ItemRow(title = AnnotatedString(app.label), icon = app.icon, onClick = { onOpen(Target.App(app)) })
    }
    item { SectionLabel(stringResource(R.string.search_section_recents)) }
}

private fun LazyListScope.results(
    state: SearchState,
    claudeInstalled: Boolean,
    onOpen: (Target) -> Unit,
    onCopy: (String) -> Unit,
    onFallback: (String) -> Unit
) {
    state.calculation?.let { result ->
        item(key = "calc") {
            ItemRow(
                title = AnnotatedString(result),
                leading = "=",
                subtitle = stringResource(R.string.search_calc_hint),
                trailing = stringResource(R.string.search_enter),
                highlighted = true,
                onClick = { onCopy(result) }
            )
        }
        item { SectionLabel(stringResource(R.string.search_section_calc)) }
    }
    if (state.calculation == null && state.sections.isEmpty()) {
        item(key = "fallback") {
            val title =
                stringResource(
                    if (claudeInstalled) R.string.search_ask_claude else R.string.search_ask_web,
                    state.query.trim()
                )
            ItemRow(
                title = AnnotatedString(title),
                leading = "?",
                trailing = stringResource(R.string.search_enter),
                highlighted = true,
                onClick = { onFallback(state.query) }
            )
        }
    }
    state.sections.forEach { (section, results) ->
        items(results.size, key = { "${section.name}:$it" }) { index ->
            val result = results[index]
            val best = result === state.best
            val target = result.target
            ItemRow(
                title = highlight(target.title, result.positions),
                icon = target.icon,
                leading = "⚙",
                subtitle =
                    when (target) {
                        is Target.App -> null
                        is Target.Shortcut -> target.shortcut.app.label
                        is Target.Setting -> stringResource(R.string.search_setting_subtitle)
                    },
                trailing = if (best) stringResource(R.string.search_enter) else null,
                highlighted = best,
                onClick = { onOpen(target) }
            )
        }
        item {
            SectionLabel(
                stringResource(
                    when (section) {
                        Section.APPS -> R.string.search_section_apps
                        Section.SHORTCUTS -> R.string.search_section_shortcuts
                        Section.SETTINGS -> R.string.search_section_settings
                    }
                )
            )
        }
    }
}

private val Target.title: String
    get() =
        when (this) {
            is Target.App -> app.label
            is Target.Shortcut -> shortcut.label
            is Target.Setting -> label
        }

private val Target.icon
    get() =
        when (this) {
            is Target.App -> app.icon
            is Target.Shortcut -> shortcut.app.icon
            is Target.Setting -> null
        }

/** Pinta en acento las letras del título que coinciden con lo escrito. */
private fun highlight(title: String, positions: IntArray): AnnotatedString = buildAnnotatedString {
    append(title)
    positions.forEach { position ->
        // Las posiciones son del nombre normalizado; solo valen si el título tiene esa letra.
        if (position < title.length) addStyle(SpanStyle(color = AlisioTokens.Accent), position, position + 1)
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val shape = AlisioTokens.PillShape
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(AlisioTokens.Surface, shape)
            .border(1.dp, AlisioTokens.ControlBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AlisioTokens.Text)
    }
}
