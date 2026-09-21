package me.misa198.airmedy.library

import java.text.Normalizer

internal actual fun nfkd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
