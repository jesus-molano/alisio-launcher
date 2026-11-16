package dev.jesusmolano.alisio.feature.onboarding

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import java.io.IOException

/**
 * Una grabación real de Alisio, muda y en bucle, con las esquinas redondeadas. Se usa TextureView
 * porque, a diferencia de SurfaceView, Compose sí puede recortarla. Solo avanza con [playing]; sin
 * animaciones del sistema se queda en el primer fotograma.
 *
 * @param aspect ancho entre alto del vídeo, para reservar su sitio antes de que cargue.
 */
@Composable
internal fun WelcomeVideo(@RawRes video: Int, aspect: Float, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = AlisioMotion.animationsOn()
    // Con la pantalla apagada o en otra app (Ajustes del sistema al dar un permiso, el selector del
    // fondo), la TextureView sigue en la ventana y no pierde la superficie: se pausa a mano.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val loop = remember(video) { LoopPlayer(context, video) }
    DisposableEffect(loop) { onDispose { loop.release() } }
    LaunchedEffect(loop, playing, animate, resumed) { loop.play(playing && animate && resumed) }
    // Una TextureView por vídeo: si cambiara, la vista nueva daría su superficie al reproductor nuevo.
    // Con la misma vista, el reproductor nuevo no la recibiría nunca (ya estaba disponible).
    key(video) {
        AndroidView(
            factory = { TextureView(it).apply { surfaceTextureListener = loop } },
            modifier =
                modifier
                    .aspectRatio(aspect)
                    .clip(AlisioTokens.CardShape)
                    .border(1.dp, AlisioTokens.Text.copy(alpha = FRAME_ALPHA), AlisioTokens.CardShape)
        )
    }
}

/**
 * El MediaPlayer de un vídeo: nace cuando la TextureView tiene superficie y se libera cuando la pierde
 * o el vídeo sale de la composición. Los vídeos no tienen pista de audio, así que no piden el foco de
 * audio ni paran la música que esté sonando.
 */
private class LoopPlayer(context: Context, @RawRes private val video: Int) : TextureView.SurfaceTextureListener {
    private val app = context.applicationContext
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var wanted = false

    fun play(on: Boolean) {
        wanted = on
        sync()
    }

    // Solo se puede arrancar o pausar un reproductor ya preparado; hasta entonces se recuerda lo pedido.
    // Un estado inesperado del reproductor deja el vídeo quieto: es un adorno y no puede cerrar el Inicio.
    private fun sync() {
        val current = player?.takeIf { prepared } ?: return
        try {
            if (wanted && !current.isPlaying) current.start()
            if (!wanted && current.isPlaying) current.pause()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "El vídeo de la bienvenida no responde", e)
            release()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val target = Surface(texture).also { surface = it }
        val created = MediaPlayer()
        player = created
        try {
            app.resources.openRawResourceFd(video).use {
                created.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            created.setSurface(target)
            created.isLooping = true
            created.setVolume(0f, 0f)
            created.setOnPreparedListener {
                prepared = true
                // Pinta el primer fotograma aunque no vaya a avanzar.
                it.seekTo(0)
                sync()
            }
            // Tras un error el reproductor ya no sirve (start() lanzaría): se suelta y el hueco queda quieto.
            created.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Error del vídeo de la bienvenida: $what/$extra")
                release()
                true
            }
            created.prepareAsync()
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido abrir el vídeo de la bienvenida", e)
            release()
        } catch (e: RuntimeException) {
            // IllegalStateException del reproductor o Resources.NotFoundException del recurso.
            Log.w(TAG, "No se ha podido preparar el vídeo de la bienvenida", e)
            release()
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        prepared = false
        surface?.release()
        surface = null
    }
}

// El borde fino que separa la grabación del fondo de color.
private const val FRAME_ALPHA = 0.16f

private const val TAG = "WelcomeVideo"
