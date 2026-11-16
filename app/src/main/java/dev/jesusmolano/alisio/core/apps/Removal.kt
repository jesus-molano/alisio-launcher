package dev.jesusmolano.alisio.core.apps

/** Qué se puede quitar de una app desde el Interior de app. */
enum class Removal {
    /** Nada: no se ofrece. */
    NONE,

    /** La app entera. */
    UNINSTALL,

    /** Solo sus actualizaciones: es del sistema, y el diálogo vuelve a la versión de fábrica. */
    UPDATES
}

/**
 * Lo que el Interior de app ofrece quitar. Una app del sistema que nunca se ha actualizado no se puede
 * quitar. Las de otro perfil (trabajo, Carpeta segura) no se ofrecen por alcance: el diálogo del
 * sistema podría con ellas, pero no se ha probado en esos perfiles.
 *
 * @param system la app viene con el teléfono (`FLAG_SYSTEM`).
 * @param updated es del sistema y tiene actualizaciones instaladas (`FLAG_UPDATED_SYSTEM_APP`).
 * @param ownProfile es del perfil en el que corre Alisio.
 */
fun removalFor(system: Boolean, updated: Boolean, ownProfile: Boolean): Removal = when {
    !ownProfile -> Removal.NONE
    !system -> Removal.UNINSTALL
    updated -> Removal.UPDATES
    else -> Removal.NONE
}
