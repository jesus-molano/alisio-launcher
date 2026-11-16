package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable

/**
 * Las barras del sistema contadas como si se vieran, para `windowInsetsPadding`. Con «Ocultar la
 * barra de estado» (`Prefs.hideStatusBar`) la barra está oculta y `systemBarsPadding` daría 0 arriba:
 * el contenido subiría hasta la cámara y saltaría cada vez que la barra asoma (docs/PLAN.md §11).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableSystemBars(): WindowInsets = WindowInsets.systemBarsIgnoringVisibility

/** Como [stableSystemBars], solo la barra de estado: para lo que solo deja hueco arriba. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableStatusBars(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility
