package dev.jesusmolano.alisio.data.apps

/** Clave estable de una app: `paquete/actividad#serie-de-usuario`. */
data class AppKey(val packageName: String, val activityName: String, val userSerial: Long) {
    override fun toString(): String = "$packageName/$activityName#$userSerial"

    companion object {
        fun parse(value: String): AppKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(value.substring(0, slash), value.substring(slash + 1, hash), serial)
        }
    }
}
