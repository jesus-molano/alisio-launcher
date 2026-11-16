package dev.jesusmolano.alisio.data.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.scale
import dev.jesusmolano.alisio.core.media.SessionFacts
import dev.jesusmolano.alisio.core.media.chooseSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Lo que enseña el reproductor del Inicio.
 *
 * @param art carátula ya reducida; `null` si la sesión no trae ninguna.
 * @param canSkip si la app admite pasar a la pista siguiente.
 */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val canSkip: Boolean,
    // Por dónde va (core/media trackProgress): la posición dicha por la sesión, cuándo la dijo (reloj
    // desde el arranque), a qué velocidad avanza y cuánto dura; sin duración no hay anillo en el dock.
    val positionMillis: Long = -1,
    val positionAt: Long = 0,
    val speed: Float = 1f,
    val durationMillis: Long? = null
)

/**
 * Las sesiones multimedia activas y la que enseña el reproductor del Inicio (§5.5): la que suena o,
 * si ninguna suena, la última. Leerlas exige el acceso a notificaciones; sin él no hay reproductor.
 *
 * No hay servicio propio: los avisos de sesiones solo llegan mientras el proceso vive. Todo ocurre en
 * el hilo principal.
 *
 * @param enabled si alguien usa las sesiones (el reproductor o la pieza «Música»). Apagado no se
 *   escucha nada: el servicio de notificaciones mantiene vivo el proceso y los avisos seguirían
 *   llegando sin uso.
 * @param preferred la app de música elegida en los ajustes: si suena, el reproductor la enseña a ella.
 */
class MediaSessions(
    context: Context,
    private val access: NotificationAccess,
    enabled: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val preferred: StateFlow<String?> = MutableStateFlow(null)
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _playing = MutableStateFlow<NowPlaying?>(null)

    /** `null` sin sesiones (o sin acceso): el reproductor no ocupa sitio. */
    val playing: StateFlow<NowPlaying?> = _playing.asStateFlow()

    private val _playingApps = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Los paquetes con alguna sesión que suena, aunque el reproductor enseñe otra: con esto se sabe
     * si «Música» ha conseguido reanudar su app.
     */
    val playingApps: StateFlow<Set<String>> = _playingApps.asStateFlow()

    private val _watching = MutableStateFlow(false)

    /**
     * Si se están leyendo las sesiones; sin ello [playingApps] no dice nada. Al empezar cambia
     * después de publicar las sesiones y al parar antes de vaciarlas: así quien combine los dos ve el
     * vaciado ya sin escucha y no lo toma por música que se ha parado (core/media `playingChange`).
     */
    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    // En el orden del sistema, que pone primero la sesión con más prioridad.
    private var watches = emptyList<Watch>()
    private var shown: Watch? = null
    private var lastShown: MediaSession.Token? = null
    private var wanted = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> setControllers(controllers.orEmpty()) }

    init {
        scope.launch {
            combine(access.granted, enabled) { granted, on -> granted && on }.collect {
                wanted = it
                watch()
            }
        }
        // Elegir otra app de música cambia qué se enseña aunque ninguna sesión haya dicho nada.
        scope.launch { preferred.collect { publish() } }
    }

    /**
     * Pide reproducir a una sesión de [packageName] que siga abierta (en pausa), sin abrir la app.
     * Devuelve `false` si no hay ninguna o no ha aceptado la orden.
     */
    fun play(packageName: String): Boolean {
        val controller = sessionOf(packageName) ?: return false
        return try {
            controller.transportControls.play()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "La sesión de $packageName no ha aceptado reproducir", e)
            false
        }
    }

    /** Si [packageName] tiene una sesión abierta, aunque esté en pausa. */
    fun hasSession(packageName: String): Boolean = sessionOf(packageName) != null

    private fun sessionOf(packageName: String): MediaController? =
        watches.firstOrNull { it.controller.packageName == packageName }?.controller

    /**
     * El Inicio vuelve a verse. Si el sistema rechazó la escucha (el acceso puede constar como
     * concedido antes de que el componente esté habilitado), aquí se reintenta: el estado no ha
     * cambiado y por sí solo no volvería a avisar.
     */
    fun refresh() {
        if (wanted && !_watching.value) watch()
    }

    /** Pausa lo que suena o reanuda lo que está en pausa. */
    fun toggle() {
        val playing = shown?.state.isPlaying()
        control { if (playing) pause() else play() }
    }

    fun skipToNext() = control { skipToNext() }

    /** Abre la app de la sesión que se enseña. Devuelve `false` si no se ha podido. */
    fun openApp(): Boolean {
        val packageName = shown?.controller?.packageName ?: return false
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido abrir la app del reproductor", e)
            false
        }
    }

    private fun control(action: MediaController.TransportControls.() -> Unit) {
        val controller = shown?.controller ?: return
        try {
            controller.transportControls.action()
        } catch (e: RuntimeException) {
            // La sesión puede haber muerto entre el dibujo y el toque.
            Log.w(TAG, "La sesión no ha aceptado la orden", e)
        }
    }

    private fun watch() {
        if (!wanted) {
            if (_watching.value) {
                manager.removeOnActiveSessionsChangedListener(sessionsListener)
                // Antes de vaciar las sesiones: ver [watching].
                _watching.value = false
            }
            setControllers(emptyList())
            return
        }
        if (_watching.value) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, access.component, mainHandler)
            setControllers(manager.getActiveSessions(access.component))
            _watching.value = true
        } catch (e: SecurityException) {
            // El acceso se ha retirado entre la comprobación y la llamada, o el sistema aún no lo
            // aplica: sin reproductor. Se quita la escucha a medias para que [refresh] reintente entera.
            Log.w(TAG, "Sin acceso a las sesiones multimedia", e)
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            setControllers(emptyList())
        }
    }

    private fun setControllers(controllers: List<MediaController>) {
        val previous = watches.associateBy { it.controller.sessionToken }
        // Cada aviso trae controladores nuevos para las mismas sesiones: se conserva el que ya se
        // escuchaba, que tiene leída su carátula.
        watches = controllers.map { previous[it.sessionToken] ?: Watch(it).also(Watch::start) }
        val kept = watches.toSet()
        previous.values.filterNot { it in kept }.forEach(Watch::stop)
        publish()
    }

    private fun publish() {
        val last = watches.firstOrNull { it.controller.sessionToken == lastShown }
        val wantedApp = preferred.value
        val chosen =
            chooseSession(watches, last, preferred = { it.controller.packageName == wantedApp }) {
                SessionFacts(it.state.isPlaying(), it.track.title != null)
            }
        _playingApps.value = watches.filter { it.state.isPlaying() }.mapTo(HashSet()) { it.controller.packageName }
        shown = chosen
        if (chosen != null) lastShown = chosen.controller.sessionToken
        _playing.value =
            chosen?.run {
                NowPlaying(
                    packageName = controller.packageName,
                    appLabel = appLabel,
                    title = track.title ?: appLabel,
                    artist = track.artist,
                    art = track.art,
                    playing = state.isPlaying(),
                    canSkip = (state?.actions ?: 0L) and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    positionMillis = state?.position ?: -1,
                    positionAt = state?.lastPositionUpdateTime ?: 0,
                    speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                    durationMillis = track.duration
                )
            }
    }

    private class Track(val title: String?, val artist: String?, val art: Bitmap?, val duration: Long? = null)

    /** Una sesión y lo último que ha dicho de sí misma. */
    private inner class Watch(val controller: MediaController) : MediaController.Callback() {
        var track = Track(null, null, null)
        var state: PlaybackState? = null
        val appLabel: String = labelOf(controller.packageName)

        fun start() {
            try {
                track = controller.metadata.toTrack()
                state = controller.playbackState
                controller.registerCallback(this, mainHandler)
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se ha podido leer una sesión multimedia", e)
            }
        }

        fun stop() = controller.unregisterCallback(this)

        // La carátula solo se lee aquí: hay apps que publican su estado cada segundo, y pedirla en
        // cada aviso traería una imagen nueva del otro proceso cada vez.
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            track = metadata.toTrack()
            publish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            publish()
        }
    }

    private fun MediaMetadata?.toTrack(): Track {
        if (this == null) return Track(null, null, null)
        val title = text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: text(MediaMetadata.METADATA_KEY_TITLE)
        val artist = text(MediaMetadata.METADATA_KEY_ARTIST) ?: text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val art =
            getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
        return Track(title, artist, art?.let(::shrink), duration)
    }

    private fun MediaMetadata.text(key: String): String? = getString(key)?.trim()?.ifEmpty { null }

    /** La carátula se dibuja pequeña: no hace falta retener la imagen a tamaño completo. */
    private fun shrink(art: Bitmap): Bitmap? {
        val longest = maxOf(art.width, art.height)
        if (longest <= ART_SIZE_PX) return art
        val factor = ART_SIZE_PX.toFloat() / longest
        return try {
            art.scale((art.width * factor).toInt().coerceAtLeast(1), (art.height * factor).toInt().coerceAtLeast(1))
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se ha podido reducir la carátula", e)
            null
        }
    }

    private fun labelOf(packageName: String): String = try {
        val packageManager = appContext.packageManager
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    // Pasar de canción o conectar con otro dispositivo es seguir sonando: si no, entre canciones la
    // pieza «Música» parpadearía y cada canción se apuntaría como un inicio nuevo en el historial.
    private fun PlaybackState?.isPlaying(): Boolean = this?.state in PLAYING_STATES

    private companion object {
        const val TAG = "MediaSessions"
        const val ART_SIZE_PX = 256
        val PLAYING_STATES =
            setOf(
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_SKIPPING_TO_NEXT,
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
                PlaybackState.STATE_CONNECTING
            )
    }
}
