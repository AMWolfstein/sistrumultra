package me.misa198.airmedy.library

/**
 * Decodes a raw MediaStore TRACK column value. MediaStore may return
 * `discNumber * 1000 + trackNumber` instead of the plain track number.
 */
fun decodeTrackNumber(raw: Int): Int = if (raw > 1000) raw % 1000 else raw

/**
 * Key used to generate ids for artists, genres, composers, album artists and albums:
 * lowercase, trim, collapse internal whitespace to single spaces, NFKD-decompose, then strip
 * combining marks (diacritics). "Björk" and "Bjork" yield the same key.
 */
fun normalizationKey(name: String): String {
    val collapsed = name.trim().lowercase().replace(Regex("\\s+"), " ")
    return nfkd(collapsed).filterNot { isCombiningMark(it) }
}

private fun isCombiningMark(c: Char): Boolean =
    c.code in 0x0300..0x036F || // Combining Diacritical Marks
        c.code in 0x1AB0..0x1AFF || // Extended
        c.code in 0x1DC0..0x1DFF || // Supplement
        c.code in 0x20D0..0x20FF || // For Symbols
        c.code in 0xFE20..0xFE2F // Half Marks

/** Platform Unicode NFKD decomposition. */
internal expect fun nfkd(text: String): String
