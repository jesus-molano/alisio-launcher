package dev.jesusmolano.alisio.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.settings.KNOWN_ASSISTANTS
import dev.jesusmolano.alisio.feature.settings.SettingsAccess
import dev.jesusmolano.alisio.feature.settings.accentName
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.TonalButton
import dev.jesusmolano.alisio.ui.components.stableSystemBars
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlinx.coroutines.launch

/** Los pasos de la bienvenida, en orden: primero lo que se ve, luego lo que se hace y al final lo que se pide. */
private enum class WelcomeStep { HELLO, STYLE, SEARCH, TRACE, INITIAL, ZONES, MUSIC, ACCESS, HOME }

/**
 * La bienvenida: un recorrido corto por lo que hace a Alisio distinto (la barra, Trazo, la inicial
 * dibujada y Zonas), con una demostración animada en cada paso, y lo que conviene elegir o permitir
 * al empezar. Todo se puede saltar y se repite desde los ajustes.
 *
 * @param apps las apps instaladas: de ellas salen los asistentes de IA y las apps de música.
 * @param musicPackages las apps que cuentan como de música (`MusicStarter.musicPackages`).
 * @param onWallpaper abre el selector del fondo de pantalla del sistema.
 * @param onFinish acaba la bienvenida; con `true`, además pide ser la app de inicio.
 */
@Composable
fun WelcomeScreen(
    prefs: Prefs,
    settings: AlisioSettings,
    access: SettingsAccess,
    apps: List<AppEntry>,
    musicPackages: Set<String>,
    onWallpaper: () -> Unit,
    onFinish: (askHomeRole: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = WelcomeStep.entries
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val accent = AlisioTokens.Accent
    val step = steps[pager.currentPage]
    fun next() {
        if (pager.currentPage < steps.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    WelcomeBackground(accent, modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(stableSystemBars())
                .padding(horizontal = AlisioTokens.Edge)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.welcome_step, pager.currentPage + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    // Texto pleno y no secundario: arriba pasa la mancha del acento y el gris no se leería.
                    color = AlisioTokens.Text,
                    modifier = Modifier.weight(1f)
                )
                if (step != WelcomeStep.HOME) {
                    TextAction(stringResource(R.string.welcome_skip), onClick = { onFinish(false) }, plain = true)
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                StepPage(
                    step = steps[page],
                    // Solo avanza el vídeo de la página que se ve; las de al lado se quedan quietas.
                    // settledPage y no currentPage: al saltar varios pasos (desde el repaso), los de en
                    // medio no arrancan su vídeo al pasar.
                    current = page == pager.settledPage,
                    prefs = prefs,
                    settings = settings,
                    access = access,
                    apps = apps,
                    musicPackages = musicPackages,
                    accent = accent,
                    onWallpaper = onWallpaper,
                    onGoTo = { target -> scope.launch { pager.animateScrollToPage(target.ordinal) } }
                )
            }
            Dots(steps.size, pager.currentPage, accent)
            Spacer(Modifier.height(16.dp))
            when (step) {
                WelcomeStep.HOME -> {
                    PrimaryButton(
                        text =
                            stringResource(
                                if (access.homeRole) R.string.welcome_home_ready else R.string.welcome_make_home
                            ),
                        onClick = { onFinish(!access.homeRole) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!access.homeRole) {
                        TextAction(
                            stringResource(R.string.welcome_later),
                            onClick = { onFinish(false) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                else ->
                    PrimaryButton(
                        text =
                            stringResource(
                                if (step == WelcomeStep.HELLO) R.string.welcome_start else R.string.welcome_next
                            ),
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth()
                    )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Un paso: la demostración arriba, el texto debajo y, si el paso lo tiene, lo que se elige. */
@Composable
private fun StepPage(
    step: WelcomeStep,
    current: Boolean,
    prefs: Prefs,
    settings: AlisioSettings,
    access: SettingsAccess,
    apps: List<AppEntry>,
    musicPackages: Set<String>,
    accent: Color,
    onWallpaper: () -> Unit,
    onGoTo: (WelcomeStep) -> Unit
) {
    val musicApps = apps.filter { it.component.packageName in musicPackages }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        when (step) {
            WelcomeStep.HELLO -> BigWord(stringResource(R.string.welcome_hello_big), accent)

            WelcomeStep.SEARCH ->
                WelcomeVideo(R.raw.welcome_search, SEARCH_ASPECT, current, Modifier.fillMaxWidth())

            WelcomeStep.TRACE -> PhoneVideo(R.raw.welcome_trace, current)

            WelcomeStep.INITIAL -> PhoneVideo(R.raw.welcome_initial, current)

            WelcomeStep.ZONES -> PhoneVideo(R.raw.welcome_zones, current)

            WelcomeStep.HOME ->
                BigWord(
                    stringResource(if (access.homeRole) R.string.welcome_home_big_ready else R.string.welcome_home_big),
                    accent
                )

            // Estilo y música enseñan lo que se elige (el color cambia el fondo al momento); permisos, sus filas.
            WelcomeStep.STYLE, WelcomeStep.MUSIC, WelcomeStep.ACCESS -> Unit
        }
        Spacer(Modifier.height(24.dp))
        val (title, body) = step.texts(access.homeRole)
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = AlisioTokens.Text,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = AlisioTokens.Text)
        Spacer(Modifier.height(16.dp))
        when (step) {
            WelcomeStep.STYLE -> {
                AccentChoices(prefs, accent, settings)
                Spacer(Modifier.height(12.dp))
                TonalButton(
                    stringResource(R.string.welcome_change_wallpaper),
                    onWallpaper,
                    icon = R.drawable.ic_wallpaper
                )
            }

            WelcomeStep.SEARCH -> {
                val assistants = apps.filter { it.component.packageName in KNOWN_ASSISTANTS }.take(MAX_CHOICES)
                assistants.forEach { app ->
                    ItemRow(
                        title = AnnotatedString(app.label),
                        icon = app.icon,
                        selected = app.component.packageName == prefs.assistantPackage,
                        onClick = { settings.setAssistant(app.component.packageName) }
                    )
                }
            }

            WelcomeStep.INITIAL ->
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.welcome_initial_toggle)),
                    leadingIcon = R.drawable.ic_gesture,
                    checked = prefs.drawInitial,
                    onClick = settings::toggleDrawInitial
                )

            WelcomeStep.MUSIC ->
                if (musicApps.isEmpty()) {
                    Text(
                        stringResource(R.string.welcome_music_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlisioTokens.TextSecondary
                    )
                } else {
                    // Todas: son pocas, la página se desplaza, y cortar dejaría fuera justo la que se usa.
                    musicApps.forEach { app ->
                        ItemRow(
                            title = AnnotatedString(app.label),
                            icon = app.icon,
                            selected = app.component.packageName == prefs.musicApp,
                            onClick = { settings.setMusicApp(app.component.packageName) }
                        )
                    }
                }

            WelcomeStep.ACCESS -> AccessRows(access)

            WelcomeStep.HOME -> Recap(prefs, apps, onGoTo)

            else -> Unit
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Una grabación de pantalla completa del teléfono, centrada y con la altura que deja sitio al texto. */
@Composable
private fun PhoneVideo(@RawRes video: Int, playing: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WelcomeVideo(video, PHONE_ASPECT, playing, Modifier.height(PhoneVideoHeight))
    }
}

/** Una palabra muy grande pintada con el acento y otro color: lo primero que se ve del paso. */
@Composable
private fun BigWord(text: String, accent: Color) {
    val second = remember(accent) { AlisioTokens.Accents.firstOrNull { it != accent } ?: accent }
    // Alto de línea en em: al encoger la palabra, el renglón encoge con ella y no deja hueco encima.
    val style = AlisioTokens.Clock.copy(brush = Brush.linearGradient(listOf(accent, second)), lineHeight = 1.em)
    Text(
        text,
        style = style,
        maxLines = 1,
        // «Ya casi está.» no cabe al tamaño de la hora: encoge hasta caber en una línea, sin cortarse.
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = style.fontSize * BIG_WORD_MIN_SCALE,
                maxFontSize = style.fontSize,
                stepSize = style.fontSize * BIG_WORD_STEP_SCALE
            ),
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
    )
}

/**
 * Lo elegido en la bienvenida, una fila por elección. Tocar una vuelve a su paso para cambiarla: el
 * último paso sirve de repaso y no solo de despedida.
 */
@Composable
private fun Recap(prefs: Prefs, apps: List<AppEntry>, onGoTo: (WelcomeStep) -> Unit) {
    val none = stringResource(R.string.welcome_recap_none)
    fun appOf(packageName: String?) = apps.firstOrNull { it.component.packageName == packageName }
    val assistant = appOf(prefs.assistantPackage)
    val music = appOf(prefs.musicApp)
    ItemRow(
        title = AnnotatedString(stringResource(R.string.welcome_recap_accent)),
        leadingIcon = R.drawable.ic_palette,
        subtitle = stringResource(if (prefs.dayAccent) R.string.settings_day_accent else accentName(prefs.accent)),
        onClick = { onGoTo(WelcomeStep.STYLE) }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.welcome_recap_assistant)),
        icon = assistant?.icon,
        leadingIcon = R.drawable.ic_search.takeIf { assistant == null },
        subtitle = assistant?.label ?: none,
        onClick = { onGoTo(WelcomeStep.SEARCH) }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.welcome_initial_toggle)),
        leadingIcon = R.drawable.ic_gesture,
        subtitle = stringResource(if (prefs.drawInitial) R.string.welcome_recap_on else R.string.welcome_recap_off),
        onClick = { onGoTo(WelcomeStep.INITIAL) }
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.welcome_recap_music)),
        icon = music?.icon,
        leadingIcon = R.drawable.ic_music_note.takeIf { music == null },
        subtitle = music?.label ?: none,
        onClick = { onGoTo(WelcomeStep.MUSIC) }
    )
}

/** El título y el texto de cada paso; el último cambia si Alisio ya es la app de inicio. */
private fun WelcomeStep.texts(isHome: Boolean): Pair<Int, Int> = when (this) {
    WelcomeStep.HELLO -> R.string.welcome_hello_title to R.string.welcome_hello_body

    WelcomeStep.STYLE -> R.string.welcome_style_title to R.string.welcome_style_body

    WelcomeStep.SEARCH -> R.string.welcome_search_title to R.string.welcome_search_body

    WelcomeStep.TRACE -> R.string.welcome_trace_title to R.string.welcome_trace_body

    WelcomeStep.INITIAL -> R.string.welcome_initial_title to R.string.welcome_initial_body

    WelcomeStep.ZONES -> R.string.welcome_zones_title to R.string.welcome_zones_body

    WelcomeStep.MUSIC -> R.string.welcome_music_title to R.string.welcome_music_body

    WelcomeStep.ACCESS -> R.string.welcome_access_title to R.string.welcome_access_body

    WelcomeStep.HOME ->
        if (isHome) {
            R.string.welcome_home_ready_title to R.string.welcome_home_ready_body
        } else {
            R.string.welcome_home_title to R.string.welcome_home_body
        }
}

/**
 * Los acentos, como círculos que se eligen tocando: el fondo cambia al momento. Con el color del día
 * o uno propio no hay ninguno marcado; elegir uno apaga el del día, que si no seguiría mandando.
 */
@Composable
private fun AccentChoices(prefs: Prefs, current: Color, settings: AlisioSettings) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AlisioTokens.Accents.forEachIndexed { index, color ->
            val chosen = !prefs.dayAccent && color == current
            val name = stringResource(accentName(index))
            Box(
                Modifier
                    .size(AlisioTokens.MinTouch)
                    .clip(CircleShape)
                    // Todo el círculo de 48 dp se toca, también el aro que rodea al color.
                    .selectable(selected = chosen, role = Role.RadioButton) { settings.chooseFixedAccent(index) }
                    .semantics { contentDescription = name }
                    .then(if (chosen) Modifier.border(3.dp, AlisioTokens.Text, CircleShape) else Modifier)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Cada permiso con lo que enciende; tocar lo pide (o lleva a donde se concede). */
@Composable
private fun AccessRows(access: SettingsAccess) {
    val allow = stringResource(R.string.welcome_allow)
    val done = stringResource(R.string.welcome_granted)
    val separator = stringResource(R.string.list_separator)
    val rows =
        listOf(
            Triple(
                R.string.welcome_access_notifications,
                R.string.welcome_access_notifications_why,
                R.drawable.ic_notifications
            ) to
                (access.notifications to access.onNotifications),
            Triple(R.string.welcome_access_contacts, R.string.welcome_access_contacts_why, R.drawable.ic_contacts) to
                (access.contacts to access.onContacts),
            Triple(R.string.welcome_access_calendar, R.string.welcome_access_calendar_why, R.drawable.ic_event) to
                (access.calendar to access.onCalendar),
            Triple(R.string.welcome_access_location, R.string.welcome_access_location_why, R.drawable.ic_place) to
                (access.location to access.onLocation),
            Triple(R.string.welcome_access_usage, R.string.welcome_access_usage_why, R.drawable.ic_hourglass) to
                (access.usage to access.onUsage)
        )
    rows.forEach { (texts, state) ->
        val (title, why, icon) = texts
        val (granted, onAsk) = state
        ItemRow(
            title = AnnotatedString(stringResource(title)),
            leadingIcon = icon,
            // Como en Permisos: lo concedido se dice en el subtítulo y la acción solo sale si falta.
            // Tocar una concedida lleva a donde se retira, igual que en los ajustes.
            subtitle = if (granted) done + separator + stringResource(why) else stringResource(why),
            subtitleLines = 2,
            trailing = if (granted) null else allow,
            onClick = onAsk
        )
    }
}

/** El punto de cada paso; el actual, más ancho y del acento. */
@Composable
private fun Dots(count: Int, current: Int, accent: Color) {
    // Solo de adorno: el contador «1 de 9» ya dice el paso a TalkBack.
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 20.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) accent else AlisioTokens.TextSecondary)
            )
        }
    }
}

// Más opciones que estas no caben bajo el texto sin desplazar: el resto se elige en los ajustes.
private const val MAX_CHOICES = 4

// Las grabaciones del teléfono son de 540 × 1170; la de la barra, una franja de 540 × 250.
private const val PHONE_ASPECT = 540f / 1170f
private const val SEARCH_ASPECT = 540f / 250f

// Lo que puede encoger la palabra grande, y de cuánto en cuánto, para caber en una línea.
private const val BIG_WORD_MIN_SCALE = 0.4f
private const val BIG_WORD_STEP_SCALE = 0.05f

// Deja sitio debajo para el título y dos o tres líneas de texto sin desplazar.
private val PhoneVideoHeight = 340.dp
