package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.feature.people.rememberContactPhoto
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.webSearchApps
import dev.jesusmolano.alisio.ui.components.AppPickerSheet
import dev.jesusmolano.alisio.ui.components.Avatar
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val PhotoPreviewSize = 56.dp

/** Las hojas que abre la página para elegir una app. */
private enum class SearchChoice { ASSISTANT, WEB_SEARCH }

// Asistentes de IA conocidos: si están instalados salen los primeros al elegir.
private val KNOWN_ASSISTANTS =
    setOf(
        "com.anthropic.claude",
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "ai.x.grok",
        "ai.perplexity.app.android",
        "com.microsoft.copilot",
        "com.deepseek.chat",
        "ai.mistral.chat"
    )

/**
 * Búsqueda: a quién pregunta la barra, con qué busca en la web, qué mira del portapapeles y cómo
 * enseña a las personas.
 *
 * @param contacts la agenda: de ella sale la foto real de la tesela «Foto».
 */
@Composable
internal fun SearchPage(context: SettingsContext, contacts: ContactsRepository) {
    val android = LocalContext.current
    val prefs = context.prefs
    val settings = context.settings
    val access = context.access
    val apps = context.apps
    val webApps = remember { android.webSearchApps() }
    val iconByPackage = remember(apps) { apps.associate { it.component.packageName to it.icon } }
    var picking by remember { mutableStateOf<SearchChoice?>(null) }

    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_assistant)),
        icon = iconByPackage[prefs.assistantPackage],
        leadingIcon = R.drawable.ic_help,
        subtitle = android.appLabel(prefs.assistantPackage) ?: stringResource(R.string.settings_assistant_missing),
        subtitleLines = 2,
        trailing = stringResource(R.string.settings_change),
        onClick = { picking = SearchChoice.ASSISTANT }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_web_search)),
        icon = prefs.webSearchPackage?.let(iconByPackage::get),
        leadingIcon = R.drawable.ic_search,
        subtitle =
            prefs.webSearchPackage?.let { android.appLabel(it) }
                ?: stringResource(R.string.settings_web_search_system),
        trailing = stringResource(R.string.settings_change),
        onClick = { picking = SearchChoice.WEB_SEARCH }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.settings_clipboard)),
        leadingIcon = R.drawable.ic_paste,
        // Encendido, lo que importa saber es el aviso del sistema, que es por lo que se apagaría.
        subtitle =
            stringResource(
                if (prefs.clipboardActions) R.string.settings_clipboard_on else R.string.settings_clipboard_hint
            ),
        subtitleLines = 2,
        checked = prefs.clipboardActions,
        onClick = { settings.toggleClipboardActions() }
    )

    SectionLabel(stringResource(R.string.settings_section_people))
    if (!access.contacts) {
        ItemRow(
            title = AnnotatedString(stringResource(R.string.settings_contacts)),
            leadingIcon = R.drawable.ic_contacts,
            subtitle = stringResource(R.string.people_allow_hint),
            subtitleLines = 2,
            trailing = stringResource(R.string.settings_allow),
            onClick = access.onContacts
        )
    }
    ContactPhotoChoices(
        photos = prefs.contactPhotos,
        enabled = access.contacts,
        contacts = contacts,
        onToggle = { settings.toggleContactPhotos() }
    )

    SectionLabel(stringResource(R.string.settings_section_commands))
    PhoneCommandsOption(context)

    when (picking) {
        SearchChoice.ASSISTANT -> {
            // Primero los asistentes conocidos que estén instalados; después, cualquier otra app.
            val candidates =
                remember(apps) {
                    (apps.filter { it.component.packageName in KNOWN_ASSISTANTS } + apps)
                        .distinctBy { it.component.packageName }
                }
            AppPickerSheet(
                title = stringResource(R.string.settings_assistant),
                items = candidates,
                key = { it.component.packageName },
                label = { it.label },
                icon = { it.icon },
                chosen = { it.component.packageName == prefs.assistantPackage },
                onPick = {
                    settings.setAssistant(it.component.packageName)
                    picking = null
                },
                onDismiss = { picking = null }
            )
        }

        SearchChoice.WEB_SEARCH ->
            AppPickerSheet(
                title = stringResource(R.string.settings_web_search),
                items = webApps,
                key = { it.packageName },
                label = { it.label },
                icon = { iconByPackage[it.packageName] },
                chosen = { it.packageName == prefs.webSearchPackage },
                onPick = {
                    settings.setWebSearch(it.packageName)
                    picking = null
                },
                onDismiss = { picking = null }
            ) {
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.settings_web_search_system)),
                    leadingIcon = R.drawable.ic_search,
                    selected = prefs.webSearchPackage == null,
                    onClick = {
                        settings.setWebSearch(null)
                        picking = null
                    }
                )
            }

        null -> Unit
    }
}

/**
 * El avatar de una persona: su foto de la agenda o sus iniciales. La tesela «Foto» enseña la de la
 * primera persona que tenga una; si nadie la tiene, el dibujo de una persona en su lugar. Sin el
 * permiso de contactos no hay fotos que leer y las dos teselas se atenúan.
 */
@Composable
private fun ContactPhotoChoices(photos: Boolean, enabled: Boolean, contacts: ContactsRepository, onToggle: () -> Unit) {
    val people by contacts.people.collectAsStateWithLifecycle()
    val sample = remember(people) { people.firstOrNull { it.photoUri != null } ?: people.firstOrNull() }
    val name = sample?.name ?: stringResource(R.string.settings_photos_sample)
    val photo = sample?.let { rememberContactPhoto(it, contacts.photos) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PreviewChoice(
            label = stringResource(R.string.settings_photos_on),
            selected = photos,
            onClick = { if (!photos) onToggle() },
            modifier = Modifier.weight(1f),
            enabled = enabled
        ) {
            if (photo != null) {
                Avatar(name, PhotoPreviewSize, photo = photo)
            } else {
                Box(Modifier.size(PhotoPreviewSize).background(AlisioTokens.Fill, CircleShape), Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        tint = AlisioTokens.TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        PreviewChoice(
            label = stringResource(R.string.settings_photos_off),
            selected = !photos,
            onClick = { if (photos) onToggle() },
            modifier = Modifier.weight(1f),
            enabled = enabled
        ) { Avatar(name, PhotoPreviewSize) }
    }
}
