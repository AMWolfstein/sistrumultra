package me.misa198.airmedy.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibraryUtilsTest {
    @Test fun decodeDiscTrack1001() = assertEquals(1, decodeTrackNumber(1001))
    @Test fun decodeDiscTrack1012() = assertEquals(12, decodeTrackNumber(1012))
    @Test fun decodePlain5() = assertEquals(5, decodeTrackNumber(5))
    @Test fun decodeZero() = assertEquals(0, decodeTrackNumber(0))

    @Test fun bjork() = assertEquals(normalizationKey("Bjork"), normalizationKey("Björk"))
    @Test fun whitespace() =
        assertEquals(normalizationKey("Some Artist"), normalizationKey("  Some   Artist  "))
    @Test fun beyonce() = assertEquals(normalizationKey("Beyonce"), normalizationKey("Beyoncé"))
}
