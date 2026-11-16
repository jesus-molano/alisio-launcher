package dev.jesusmolano.alisio.core.media

/**
 * Spotify y YouTube Music: las del dueño, por si aún no ha elegido ninguna ni ha sonado nada.
 * YouTube Music va también con el paquete de ReVanced, que es el que tiene instalado el S26.
 */
val KNOWN_MUSIC_APPS: List<String> =
    listOf("com.spotify.music", "com.google.android.apps.youtube.music", "app.revanced.android.apps.youtube.music")

/**
 * La app de música por defecto (Música siempre a mano): la elegida en los ajustes; si no, la última
 * que sonó; si tampoco, la primera de [KNOWN_MUSIC_APPS] que esté instalada. Una app desinstalada no
 * vale: la pieza y la sugerencia no tendrían nada que abrir.
 *
 * @param installed los paquetes con icono de inicio.
 */
fun musicAppFor(chosen: String?, lastPlayed: String?, installed: Set<String>): String? =
    chosen?.takeIf { it in installed }
        ?: lastPlayed?.takeIf { it in installed }
        ?: KNOWN_MUSIC_APPS.firstOrNull { it in installed }

/**
 * La clave con la que el historial apunta que una app de música empezó a sonar. Va aparte de la de
 * abrir la app (`TYPE_APP`): poner música desde los auriculares o la notificación no es abrirla, y
 * no debe subir la app en el ranking de la barra.
 */
fun musicKey(packageName: String): String = MUSIC_KEY_PREFIX + packageName

private const val MUSIC_KEY_PREFIX = "music:"

/**
 * Si una sesión que suena cuenta como «la última app que sonó». Solo las que se reconocen como de
 * música ([knownBrowser]: la app publica un servicio de navegación multimedia, como las de música y
 * pódcast para Android Auto) o las de [KNOWN_MUSIC_APPS]. Así un audio de WhatsApp o un vídeo en el
 * navegador no pasan a ser la app de música por defecto.
 */
fun countsAsMusic(packageName: String, knownBrowser: Boolean): Boolean = knownBrowser || packageName in KNOWN_MUSIC_APPS

/**
 * [apps] con las de música ([music]) delante y el resto detrás, cada grupo en su orden de antes. En
 * el selector la lista crece desde abajo: las primeras quedan junto al buscador y al pulgar, y no
 * hay que buscar Spotify entre la calculadora y el navegador.
 */
fun <T> musicAppsFirst(apps: List<T>, music: Set<String>, packageOf: (T) -> String): List<T> =
    apps.sortedBy { packageOf(it) !in music }
