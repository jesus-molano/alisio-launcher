package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.settings.Access
import dev.jesusmolano.alisio.core.settings.accessOrder
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Cómo se enseña un acceso.
 *
 * @param pending para qué hace falta, mientras no está concedido.
 * @param granted lo que da, una vez concedido.
 * @param leavesAlisio se concede en una pantalla del sistema, no en un diálogo sobre Alisio.
 */
private class AccessRow(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @StringRes val pending: Int,
    @StringRes val granted: Int,
    val leavesAlisio: Boolean
)

private fun Access.row(): AccessRow = when (this) {
    Access.HOME_ROLE ->
        AccessRow(
            R.string.settings_home_role,
            R.drawable.ic_home,
            R.string.settings_home_role_hint,
            R.string.settings_home_role_on,
            leavesAlisio = false
        )

    Access.CONTACTS ->
        AccessRow(
            R.string.settings_contacts,
            R.drawable.ic_contacts,
            R.string.people_allow_hint,
            R.string.settings_contacts_on,
            leavesAlisio = false
        )

    Access.BLUETOOTH ->
        AccessRow(
            R.string.settings_bluetooth,
            R.drawable.ic_bluetooth,
            R.string.settings_bluetooth_hint,
            R.string.settings_bluetooth_on,
            leavesAlisio = false
        )

    Access.NOTIFICATIONS ->
        AccessRow(
            R.string.settings_notification_access,
            R.drawable.ic_notifications,
            R.string.settings_notification_access_hint,
            R.string.settings_notification_access_on,
            leavesAlisio = true
        )

    Access.USAGE ->
        AccessRow(
            R.string.settings_usage,
            R.drawable.ic_schedule,
            R.string.settings_usage_hint,
            R.string.settings_usage_on,
            leavesAlisio = true
        )

    Access.ACCESSIBILITY ->
        AccessRow(
            R.string.settings_accessibility,
            R.drawable.ic_accessibility,
            R.string.settings_accessibility_hint,
            R.string.settings_accessibility_on,
            leavesAlisio = true
        )
}

/**
 * Permisos: todo lo que Alisio le pide al sistema, junto y separado de las preferencias. Lo que
 * falta va primero, con su acción «Permitir»; lo concedido lleva una marca discreta y dice lo que
 * da. Tocar una fila concedida abre la pantalla del sistema donde se retira.
 */
@Composable
internal fun PermissionsPage(access: SettingsAccess) {
    accessOrder(access.granted).forEach { item ->
        val row = item.row()
        val held = item in access.granted
        ItemRow(
            title = AnnotatedString(stringResource(row.title)),
            leadingIcon = row.icon,
            titleMark = R.drawable.ic_open_in_new.takeIf { row.leavesAlisio && !held },
            subtitle = stringResource(if (held) row.granted else row.pending),
            subtitleLines = 2,
            trailing = if (held) null else stringResource(R.string.settings_allow),
            onClick = access.request(item),
            actions =
                if (held) {
                    {
                        // Sin acento: concedido es lo normal, no algo elegido.
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.settings_granted),
                            tint = AlisioTokens.TextSecondary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(20.dp)
                        )
                    }
                } else {
                    null
                }
        )
    }
}

private fun SettingsAccess.request(access: Access): () -> Unit = when (access) {
    Access.HOME_ROLE -> onHomeRole
    Access.CONTACTS -> onContacts
    Access.BLUETOOTH -> onBluetooth
    Access.NOTIFICATIONS -> onNotifications
    Access.USAGE -> onUsage
    Access.ACCESSIBILITY -> onAccessibility
}
