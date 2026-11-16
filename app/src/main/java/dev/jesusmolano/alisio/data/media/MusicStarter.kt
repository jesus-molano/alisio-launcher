package dev.jesusmolano.alisio.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Process
import android.service.media.MediaBrowserService
import android.util.Log
import android.view.KeyEvent
import androidx.core.util.AtomicFile
import dev.jesusmolano.alisio.core.media.KNOWN_MUSIC_APPS
import dev.jesusmolano.alisio.core.media.ResumeStep
import dev.jesusmolano.alisio.core.media.countsAsMusic
import dev.jesusmolano.alisio.core.media.musicAppFor
import dev.jesusmolano.alisio.core.media.musicKey
import dev.jesusmolano.alisio.core.media.playingChange
import dev.jesusmolano.alisio.core.media.resumeSteps
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Música siempre a mano: reanuda la app de música por defecto sin abrirla y recuerda lo que hace
 * falta para proponerla (la última app que sonó, cuándo empieza a sonar y su última carátula).
 *
 * Cómo se reanuda sin abrir la app, de lo más fiable a lo menos (docs/PLAN.md §11, «Música siempre
 * a mano»; qué pasos se prueban lo decide `resumeSteps`):
 * 1. Si la app aún tiene su sesión abierta (en pausa), `MediaController.play()` sobre ella.
 * 2. Si es la última app que se vio empezar a sonar, la tecla «reproducir» por
 *    `AudioManager.dispatchMediaKeyEvent`. Es lo que hace el botón de unos auriculares: el sistema la
 *    entrega a la última sesión que sonó, aunque su app ya no esté en marcha, y le da permiso para
 *    arrancar su servicio en primer plano. Un `ACTION_MEDIA_BUTTON` enviado por Alisio no lleva ese
 *    permiso, y desde Android 12 la app no podría arrancar su servicio.
 * 3. Si no, ese `ACTION_MEDIA_BUTTON` dirigido a su receptor de botones, que vale cuando la app aún
 *    está en marcha.
 *
 * Quien llama abre la app si [resume] devuelve `false`: nada ha empezado a sonar en [RESUME_WAIT_MILLIS].
 */
class MusicStarter(
    context: Context,
    private val sessions: MediaSessions,
    private val settings: AlisioSettings,
    apps: StateFlow<List<AppEntry>?>,
    private val history: LaunchHistory,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    /**
     * La app de música por defecto ([musicAppFor]) en el perfil principal; `null` si no hay ninguna.
     * La usan la pieza «Música», la sugerencia «Reanudar» y la barra.
     */
    val app: StateFlow<AppEntry?> =
        combine(apps, settings.prefs) { all, prefs ->
            val main = all.orEmpty().filter { it.user == Process.myUserHandle() }
            val installed = main.mapTo(HashSet()) { it.component.packageName }
            musicAppFor(prefs?.musicApp, prefs?.lastMusicApp, installed)?.let { packageName ->
                main.firstOrNull { it.component.packageName == packageName }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val audio = appContext.getSystemService(AudioManager::class.java)

    private val _lastArt = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    /**
     * La última carátula de cada app de música, por paquete. Va por app para que «Reanudar Spotify»
     * no lleve la portada de lo último que sonó en YouTube Music.
     */
    val lastArt: StateFlow<Map<String, Bitmap>> = _lastArt.asStateFlow()

    private val _lastPlayedAt = MutableStateFlow<Long?>(null)

    /**
     * La última vez que se vio sonar algo, solo en memoria: la regla de Ahora no propone reanudar
     * justo después de una pausa.
     */
    val lastPlayedAt: StateFlow<Long?> = _lastPlayedAt.asStateFlow()

    // Por paquete, si publica un servicio de navegación multimedia: no cambia mientras siga instalada.
    private val browsers = HashMap<String, Boolean>()

    // La última app que se vio empezar a sonar, sea de música o no; `null` si no se sabe. Solo en
    // memoria y solo mientras se escuchan las sesiones: decide si vale la tecla del paso (2).
    private var lastStarted: String? = null

    // La última carátula comprobada: las apps publican su estado cada segundo con la misma imagen, y
    // así no se comparan píxeles en cada aviso.
    private var checkedArt: Bitmap? = null

    // Las carátulas por guardar. Conflated: si las canciones pasan rápido, solo cuenta la última.
    private val artWrites = Channel<Pair<String, Bitmap>>(Channel.CONFLATED)

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { loadArts() }
            // Lo visto sonar mientras se leía el disco es más nuevo que lo guardado: gana.
            _lastArt.update { loaded + it }
        }
        scope.launch {
            var before = emptySet<String>()
            var wasWatching = false
            // Se lee el estado actual de los dos en cada aviso, no el que trae el aviso: MediaSessions
            // los cambia en un orden fijo (ver MediaSessions.watching) y así no se ve una mezcla.
            merge(sessions.watching.map { }, sessions.playingApps.map { }).collect {
                val watching = sessions.watching.value
                val now = sessions.playingApps.value
                val change = playingChange(before, now, wasWatching, watching, lastStarted)
                change.started.filter(::isMusic).forEach(::started)
                if (change.played) _lastPlayedAt.value = clock.now()
                lastStarted = change.lastStarted
                before = now
                wasWatching = watching
            }
        }
        scope.launch { sessions.playing.collect(::keepArt) }
        scope.launch(Dispatchers.IO) {
            for ((packageName, art) in artWrites) saveArt(packageName, art)
        }
    }

    /**
     * Reanuda [packageName] sin abrirla y espera a que suene. Devuelve `false` si no ha sonado: quien
     * llama abre entonces la app, que es lo único seguro que queda.
     */
    suspend fun resume(packageName: String): Boolean {
        val watching = sessions.watching.value
        val steps =
            resumeSteps(
                packageName = packageName,
                hasSession = sessions.hasSession(packageName),
                lastStarted = lastStarted,
                watching = watching,
                audioActive = audio.isMusicActive
            )
        val sent =
            steps.any { step ->
                when (step) {
                    ResumeStep.SESSION -> sessions.play(packageName)
                    ResumeStep.MEDIA_KEY -> pressPlay()
                    ResumeStep.MEDIA_BUTTON -> sendMediaButton(packageName)
                }
            }
        return sent && awaitPlaying(packageName, watching)
    }

    /**
     * Los paquetes que cuentan como apps de música ([countsAsMusic]) de una sola consulta: los que
     * publican un servicio de navegación multimedia, más los conocidos. El selector de la app de
     * música los pone primero; preguntar app por app al abrirlo tardaría.
     */
    fun musicPackages(): Set<String> = appContext.packageManager
        .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
        .mapNotNullTo(HashSet()) { it.serviceInfo?.packageName }
        .apply { addAll(KNOWN_MUSIC_APPS) }
        .filterTo(HashSet()) { countsAsMusic(it, knownBrowser = true) }

    private fun started(packageName: String) {
        settings.setLastMusicApp(packageName)
        history.record(LaunchHistory.TYPE_MUSIC, musicKey(packageName))
    }

    private fun isMusic(packageName: String): Boolean {
        val browser =
            browsers.getOrPut(packageName) {
                val intent = Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName)
                appContext.packageManager.queryIntentServices(intent, 0).isNotEmpty()
            }
        return countsAsMusic(packageName, browser)
    }

    /**
     * Guarda la carátula de lo que suena, por app. Se compara la imagen y no el título: hay apps que
     * mandan primero una imagen provisional y luego la buena con el mismo título.
     */
    private fun keepArt(playing: NowPlaying?) {
        val art = playing?.art ?: return
        if (!playing.playing || art === checkedArt || !isMusic(playing.packageName)) return
        checkedArt = art
        val packageName = playing.packageName
        if (_lastArt.value[packageName]?.sameAs(art) == true) return
        _lastArt.update { it + (packageName to art) }
        artWrites.trySend(packageName to art)
    }

    private fun artFile(packageName: String) = AtomicFile(
        File(
            appContext.filesDir,
            ART_PREFIX + packageName + ART_SUFFIX
        )
    )

    // AtomicFile escribe aparte y sustituye al terminar: un proceso que muere a mitad no deja un PNG roto.
    private fun saveArt(packageName: String, art: Bitmap) {
        val file = artFile(packageName)
        val out =
            try {
                file.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "No se ha podido guardar la carátula", e)
                return
            }
        try {
            art.compress(Bitmap.CompressFormat.PNG, 100, out)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.w(TAG, "No se ha podido guardar la carátula", e)
        }
    }

    private fun loadArts(): Map<String, Bitmap> = appContext.filesDir
        .listFiles { file -> file.name.startsWith(ART_PREFIX) && file.name.endsWith(ART_SUFFIX) }
        .orEmpty()
        .mapNotNull { file ->
            val packageName = file.name.removePrefix(ART_PREFIX).removeSuffix(ART_SUFFIX)
            try {
                artFile(packageName).openRead().use { BitmapFactory.decodeStream(it) }?.let { packageName to it }
            } catch (e: IOException) {
                Log.w(TAG, "No se ha podido leer la carátula de $packageName", e)
                null
            }
        }
        .toMap()

    private fun pressPlay(): Boolean = try {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "El sistema no ha aceptado la tecla de reproducir", e)
        false
    }

    private fun sendMediaButton(packageName: String): Boolean {
        val receiver =
            appContext.packageManager
                .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), 0)
                .firstOrNull()
                ?.activityInfo
                ?: return false
        val component = ComponentName(receiver.packageName, receiver.name)
        return try {
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                appContext.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON)
                        .setComponent(component)
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
                )
            }
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido mandar el botón de reproducir a $packageName", e)
            false
        }
    }

    /**
     * Si [packageName] suena antes de [RESUME_WAIT_MILLIS]. Sin escuchar las sesiones no se sabe qué
     * app suena: basta con que suene algo, porque `resumeSteps` ya ha descartado el caso de que
     * sonara antes de la orden.
     */
    private suspend fun awaitPlaying(packageName: String, watching: Boolean): Boolean =
        withTimeoutOrNull(RESUME_WAIT_MILLIS) {
            if (watching) {
                sessions.playingApps.first { packageName in it }
            } else {
                while (!audio.isMusicActive) delay(POLL_MILLIS)
            }
            true
        } ?: false

    private companion object {
        const val TAG = "MusicStarter"

        // files/music_art_<paquete>.png, una por app de música (docs/PLAN.md §11).
        const val ART_PREFIX = "music_art_"
        const val ART_SUFFIX = ".png"
        const val RESUME_WAIT_MILLIS = 1_500L
        const val POLL_MILLIS = 100L
    }
}
