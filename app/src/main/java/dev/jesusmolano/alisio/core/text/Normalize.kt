package dev.jesusmolano.alisio.core.text

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = Regex("\\p{Mn}+")

/** Minúsculas y sin diacríticos: «Cámara» → «camara». */
fun normalize(text: String): String =
    DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
