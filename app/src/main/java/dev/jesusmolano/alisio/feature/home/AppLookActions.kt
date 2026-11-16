package dev.jesusmolano.alisio.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.feature.appmenu.AppLook
import dev.jesusmolano.alisio.ui.components.ImageTarget
import dev.jesusmolano.alisio.ui.components.LocalPickImage
import dev.jesusmolano.alisio.ui.components.LocalUndo

/**
 * Las órdenes para cambiar cómo se ve una app (icono, nombre, ocultarla), con su «Deshacer». Lo
 * usan el Interior de app del Inicio y Ajustes → Aspecto y Zonas, que comparten [viewModel].
 */
@Composable
fun rememberAppLook(viewModel: HomeViewModel): AppLook {
    val undo = LocalUndo.current
    val pickImage = LocalPickImage.current
    val resources = LocalResources.current
    return remember(viewModel, undo, pickImage, resources) {
        fun choose(app: AppEntry, icon: CustomIcon?) {
            undo.show(resources.getString(R.string.undo_icon_changed, app.label), viewModel.setAppIcon(app, icon))
        }
        AppLook(
            packs = viewModel::iconPacks,
            packIcons = viewModel::packIconsFor,
            drawables = viewModel::packDrawables,
            icon = viewModel::packIcon,
            onChoose = ::choose,
            // La actividad pone la imagen y ofrece deshacerlo (MainActivity.onImageChosen).
            onPickImage = { app, onPicked -> pickImage(ImageTarget.App(app.key.toString()), onPicked) },
            onRename = { app, label ->
                val restore = viewModel.setAppLabel(app, label)
                val shown = label.trim().ifEmpty { app.originalLabel }
                undo.show(resources.getString(R.string.undo_renamed, shown), restore)
            },
            onHide = { app, hidden ->
                val restore = viewModel.setAppHidden(app, hidden)
                // Ocultar quita la app de varios sitios a la vez: se puede deshacer. Volver a enseñarla no pierde nada.
                if (hidden) undo.show(resources.getString(R.string.undo_hidden, app.label), restore)
            }
        )
    }
}
