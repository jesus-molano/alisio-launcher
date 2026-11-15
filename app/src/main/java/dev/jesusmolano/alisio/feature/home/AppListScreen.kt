package dev.jesusmolano.alisio.feature.home

import android.app.ActivityOptions
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.apps.AppEntry

/**
 * Lista A–Z de la fase 0.
 *
 * @param homePresses sube cada vez que se pulsa «inicio» estando ya en Alisio; devuelve la lista arriba.
 */
@Composable
fun AppListScreen(viewModel: HomeViewModel, homePresses: Int, modifier: Modifier = Modifier) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(homePresses) {
        if (homePresses > 0) listState.animateScrollToItem(0)
    }

    val list = apps ?: return
    if (list.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.apps_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = WindowInsets.systemBars.asPaddingValues()
    ) {
        items(list, key = { it.key.toString() }) { app ->
            AppRow(
                app = app,
                onClick = { iconBounds ->
                    // La animación nace en el icono: límites relativos a la vista para el recorte
                    // y en pantalla para sourceBounds.
                    val options =
                        ActivityOptions.makeClipRevealAnimation(
                            view,
                            iconBounds.left.toInt(),
                            iconBounds.top.toInt(),
                            iconBounds.width.toInt(),
                            iconBounds.height.toInt()
                        )
                    val origin = IntArray(2).also(view::getLocationOnScreen)
                    val sourceBounds =
                        android.graphics.Rect(
                            origin[0] + iconBounds.left.toInt(),
                            origin[1] + iconBounds.top.toInt(),
                            origin[0] + iconBounds.right.toInt(),
                            origin[1] + iconBounds.bottom.toInt()
                        )
                    if (!viewModel.launch(app, sourceBounds, options.toBundle())) {
                        val message = resources.getString(R.string.app_launch_failed, app.label)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: (iconBounds: Rect) -> Unit) {
    var iconBounds by remember { mutableStateOf(Rect.Zero) }
    val icon = remember(app.icon) { app.icon.asImageBitmap() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable { onClick(iconBounds) }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decorativo: el nombre va justo al lado.
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(48.dp)
                    .onGloballyPositioned { iconBounds = it.boundsInWindow() }
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
