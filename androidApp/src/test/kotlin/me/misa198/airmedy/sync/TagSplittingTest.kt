package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scanner builds each track's artists, album artists and composers with
 * localArtistsOf/localComposersOf. They used to split on ";" only; they now use the
 * configured tag separators.
 */
class TagSplittingTest {

    private val defaults = TagSeparatorSettings()

    @Test fun `default separators split on semicolon, slash and the Arabic comma`() {
        assertEquals(
            listOf("A", "B", "C", "عمرو دياب", "تامر حسني"),
            localArtistsOf("A; B/C، عمرو دياب، تامر حسني", defaults).map { it.name },
        )
        assertEquals(listOf("Amr Diab, R3HAB"), localArtistsOf("Amr Diab, R3HAB", defaults).map { it.name })
    }

    @Test fun `configured delimiters change what the scanner extracts`() {
        val withComma = TagSeparatorSettings(delimiters = listOf(";", ","))
        assertEquals(listOf("Amr Diab", "R3HAB"), localArtistsOf("Amr Diab, R3HAB", withComma).map { it.name })

        val featured = TagSeparatorSettings(delimiters = listOf("feat."))
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), localArtistsOf("Daft Punk feat. Pharrell Williams", featured).map { it.name })
    }

    @Test fun `turning separation off keeps the whole tag as one name`() {
        val off = TagSeparatorSettings(enabled = false)
        assertEquals(listOf("A; B/C"), localArtistsOf("A; B/C", off).map { it.name })
        assertEquals(listOf("A & B; C"), localComposersOf("A & B; C", off).map { it.name })
    }

    @Test fun `composers share the artist separators`() {
        assertEquals(listOf("Mostafa Elassal & Amr Tayam", "Ahmed"), localComposersOf("Mostafa Elassal & Amr Tayam; Ahmed", defaults).map { it.name })
        val withAmpersand = TagSeparatorSettings(delimiters = listOf(";", "&"))
        assertEquals(listOf("Mostafa Elassal", "Amr Tayam", "Ahmed"), localComposersOf("Mostafa Elassal & Amr Tayam; Ahmed", withAmpersand).map { it.name })
    }

    @Test fun `ids are built per name after splitting, with diacritics folded`() {
        val refs = localArtistsOf("Björk/Bjork", defaults)
        assertEquals(listOf("Björk", "Bjork"), refs.map { it.name })
        assertEquals(artistId("Bjork"), refs[0].id)
        assertEquals(refs[0].id, refs[1].id)
        assertEquals(composerId("Hans Zimmer"), localComposersOf("Hans Zimmer; Lisa Gerrard", defaults).first().id)
    }

    @Test fun `escaped delimiters survive the scan`() {
        assertEquals(listOf("AC/DC", "Brian Johnson"), localArtistsOf("AC\\/DC; Brian Johnson", defaults).map { it.name })
    }

    @Test fun `a blank artist tag still yields one placeholder artist, a blank composer none`() {
        assertEquals(listOf(""), localArtistsOf("", defaults).map { it.name })
        assertEquals(emptyList(), localComposersOf("", defaults))
    }

    @Test fun `the settings signature changes with the delimiters and the switch`() {
        val base = TagSeparatorSettings()
        assertEquals(base.signature, TagSeparatorSettings(delimiters = listOf(";", "/", "،")).signature)
        assert(base.signature != TagSeparatorSettings(delimiters = listOf(";")).signature)
        assert(base.signature != TagSeparatorSettings(enabled = false).signature)
    }
}
