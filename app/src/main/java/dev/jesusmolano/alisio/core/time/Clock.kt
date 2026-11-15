package dev.jesusmolano.alisio.core.time

/** Hora actual en milisegundos; inyectable para que los tests no dependan del reloj real. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
