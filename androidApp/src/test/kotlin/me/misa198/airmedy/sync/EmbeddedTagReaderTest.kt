package me.misa198.airmedy.sync

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddedTagReaderTest {

    private val lrc = "[00:01.00]Hello world\n[00:02.50]Second line"
    private val image = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
    )

    @Test fun `flac vorbis comment lyrics`() {
        val file = flac(picture = null, comments = listOf("LYRICS=$lrc"))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac prefers lyrics over unsynced`() {
        val file = flac(picture = null, comments = listOf("UNSYNCEDLYRICS=plain", "LYRICS=$lrc"))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac vorbis lyrics decode as utf8`() {
        val korean = "[00:01.00]안녕하세요\n[00:02.00]세계"
        val file = flac(picture = null, comments = listOf("LYRICS=$korean"))
        assertEquals(korean, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `flac picture block artwork`() {
        val file = flac(picture = image, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac prefers a later front cover over an earlier back cover`() {
        val back = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val file = flac(picture = back, pictureType = 4, secondPicture = image, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac falls back to a non-front picture when no front cover exists`() {
        val file = flac(picture = image, pictureType = 4, comments = emptyList())
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `flac without picture returns null artwork`() {
        val file = flac(picture = null, comments = listOf("LYRICS=$lrc"))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    @Test fun `id3 v2_3 uslt lyrics`() {
        val file = temp("id3-uslt", id3v23(frames = listOf(uslt("eng", lrc))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Second line")
    }

    @Test fun `id3 v2_3 apic artwork`() {
        val file = temp("id3-apic", id3v23(frames = listOf(apic(image))))
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `id3 v2_2 ult and pic`() {
        val file = temp("id3-v22", id3v22(frames = listOf(ult("eng", lrc), pic(image))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Hello world")
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `id3 without tags returns null`() {
        val file = temp("no-tags", byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00))
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    @Test fun `ogg vorbis lyrics`() {
        val file = temp("ogg", oggVorbis(vorbisComment(comments = listOf("LYRICS=$lrc"))))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `ogg metadata block picture artwork`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(pictureBlock(image))
        val file = temp("ogg-picture", oggVorbis(vorbisComment(comments = listOf("METADATA_BLOCK_PICTURE=$b64"))))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    /**
     * Mirrors the page layout of a real affected SpotiFLAC/libopusenc file: same vendor,
     * tag keys and value lengths, a 6531-byte LYRICS value straddling a 4080-byte page
     * boundary, two covers (75 KB, then 418 KB) and trailing padding, so the ~680 KB
     * comment packet spans ~170 pages and ends past the old 512 KB read prefix.
     */
    @Test fun `opus comment header spanning many pages keeps lyrics artwork and tags`() {
        val lyrics = lrcOfLength(6531 - "LYRICS=".length)
        val front = imageOfSize(75_247, seed = 1)
        val back = imageOfSize(418_458, seed = 2)
        val comments = listOf(
            filler("COMPATIBLE_BRANDS", 38), filler("COPYRIGHT", 27), "DATE=2024-06-12", filler("DISCNUMBER", 12),
            filler("ENCODER", 35), filler("ENCODER", 20), filler("ENCODER_OPTIONS", 29), "ISRC=EGA012400123",
            "LYRICS=$lyrics", filler("MAJOR_BRAND", 16), filler("MINOR_VERSION", 15), filler("ORGANIZATION", 25),
            filler("R128_ALBUM_GAIN", 20), filler("R128_TRACK_GAIN", 20), filler("TRACKNUMBER", 16),
            "UNSYNCEDLYRICS=" + "u".repeat(6539 - "UNSYNCEDLYRICS=".length), "YEAR=2024",
            "METADATA_BLOCK_PICTURE=" + base64Picture(front, pictureType = 3),
            "METADATA_BLOCK_PICTURE=" + base64Picture(back, pictureType = 4),
            filler("TITLE", 16), filler("ARTIST", 24), filler("ALBUMARTIST", 29), filler("ALBUM", 25), filler("GENRE", 14),
        )
        val bytes = oggOpus(vorbisComment(comments, vendor = "libopus 1.6, libopusenc 0.3"), padding = 10_412)
        // Sanity-check the fixture reproduces the failure shape: the lyrics are split by a
        // page header, and the comment packet extends past the old 512 KB read prefix.
        assertEquals(-1, bytes.indexOf(lyrics.toByteArray(Charsets.UTF_8)))
        assertTrue(bytes.size > 600 * 1024)
        val file = temp("opus-multipage", bytes)

        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(front.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
        val tags = assertNotNull(EmbeddedTagReader.embeddedTrackTags(file.path))
        assertEquals("EGA012400123", tags.isrc)
        assertEquals("2024-06-12", tags.releaseDate)
    }

    @Test fun `opus artwork beyond the first 512 KB is read`() {
        val cover = imageOfSize(600_000, seed = 3)
        val comment = vorbisComment(listOf("METADATA_BLOCK_PICTURE=" + base64Picture(cover)), vendor = "libopus 1.6, libopusenc 0.3")
        val file = temp("opus-large-cover", oggOpus(comment))
        assertEquals(cover.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    /** libvorbis/libogg layout: comment and setup packets share pages of up to 255 segments. */
    @Test fun `vorbis comment header spanning pages keeps lyrics and artwork`() {
        val lyrics = lrcOfLength(70_000)
        val cover = imageOfSize(150_000, seed = 4)
        val comment = vorbisComment(
            listOf("TITLE=Vorbis", "LYRICS=$lyrics", "METADATA_BLOCK_PICTURE=" + base64Picture(cover)),
            vendor = "Xiph.Org libVorbis I 20200704 (Reducing Environment)",
        )
        val bytes = oggVorbis(comment)
        assertEquals(-1, bytes.indexOf(lyrics.toByteArray(Charsets.UTF_8)))
        val file = temp("vorbis-multipage", bytes)
        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
        assertEquals(cover.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `ogg pages from another multiplexed stream are skipped`() {
        val lyrics = lrcOfLength(10_000)
        val pages = oggPages(
            listOf(opusHead(), "OpusTags".toByteArray(Charsets.US_ASCII) + vorbisComment(listOf("LYRICS=$lyrics"))),
            maxSegmentsPerPage = 16,
        )
        val foreign = oggPages(listOf(ByteArray(300) { 7 }), serial = 0x0BADF00D, maxSegmentsPerPage = 16).single()
        val bytes = (pages.take(2) + foreign + pages.drop(2)).fold(ByteArray(0)) { acc, page -> acc + page }
        val file = temp("opus-multiplexed", bytes)
        assertEquals(lyrics, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `truncated ogg comment header returns null`() {
        val comment = vorbisComment(listOf("LYRICS=" + lrcOfLength(20_000)))
        val bytes = oggOpus(comment)
        val file = temp("opus-truncated", bytes.copyOf(10_000))
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a covr artwork`() {
        val file = m4a(ilst = covr(image))
        val bytes = EmbeddedTagReader.embeddedArtworkBytes(file.path)
        assertNotNull(bytes)
        assertEquals(image.toList(), bytes.toList())
    }

    @Test fun `m4a copyright lyric atom lyrics`() {
        val file = m4a(ilst = lyricAtom(lrc))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a itunes freeform lyrics`() {
        val file = m4a(ilst = itunesLyrics(lrc))
        assertEquals(lrc, EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    // Before the itunesLyrics() fixture gained its mean/name version/flags header this passed
    // only because the freeform atom was unreadable, not because the precedence rule ran.
    @Test fun `m4a prefers copyright lyric over itunes freeform`() {
        val file = m4a(ilst = lyricAtom("plain lyrics") + itunesLyrics(lrc))
        assertEquals("plain lyrics", EmbeddedTagReader.embeddedLyricsText(file.path))
    }

    @Test fun `m4a without ilst returns null`() {
        val file = m4a(ilst = byteArrayOf())
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    @Test fun `wav id3 chunk artwork and lyrics`() {
        val file = wav(id3v23(frames = listOf(uslt("eng", lrc), apic(image))))
        assertContains(EmbeddedTagReader.embeddedLyricsText(file.path)!!, "Hello world")
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `aiff id3 chunk artwork`() {
        val file = aiff(id3v23(frames = listOf(apic(image))))
        assertEquals(image.toList(), EmbeddedTagReader.embeddedArtworkBytes(file.path)!!.toList())
    }

    @Test fun `wav without id3 chunk returns null`() {
        val fmt = riffChunk("fmt ", byteArrayOf(0x01, 0x00), bigEndian = false)
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.ISO_8859_1))
            write(fmt)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.writeIntLE(4 + body.size)
        out.write(body)
        val file = temp("wav-no-id3", out.toByteArray())
        assertNull(EmbeddedTagReader.embeddedLyricsText(file.path))
        assertNull(EmbeddedTagReader.embeddedArtworkBytes(file.path))
    }

    private fun temp(name: String, bytes: ByteArray): File =
        File.createTempFile(name, ".test").apply { writeBytes(bytes) }

    private fun flac(picture: ByteArray?, comments: List<String>, pictureType: Int = 3, secondPicture: ByteArray? = null): File {
        val blocks = mutableListOf<Pair<Int, ByteArray>>()
        picture?.let { blocks += FLAC_PICTURE to pictureBlock(it, pictureType) }
        secondPicture?.let { blocks += FLAC_PICTURE to pictureBlock(it, 3) }
        blocks += FLAC_VORBIS_COMMENT to vorbisComment(comments)
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        blocks.forEachIndexed { index, (type, payload) ->
            out.write((if (index == blocks.lastIndex) 0x80 else 0x00) or type)
            out.write(byteArrayOf((payload.size shr 16 and 0xFF).toByte(), (payload.size shr 8 and 0xFF).toByte(), (payload.size and 0xFF).toByte()))
            out.write(payload)
        }
        return temp("flac", out.toByteArray())
    }

    private fun pictureBlock(imageBytes: ByteArray, pictureType: Int = 3): ByteArray {
        val mime = "image/png".toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream()
        out.writeIntBE(pictureType)
        out.writeIntBE(mime.size); out.write(mime)
        out.writeIntBE(0)
        out.writeIntBE(0); out.writeIntBE(0); out.writeIntBE(24); out.writeIntBE(0)
        out.writeIntBE(imageBytes.size); out.write(imageBytes)
        return out.toByteArray()
    }

    private fun vorbisComment(comments: List<String>, vendor: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        out.writeIntLE(vendorBytes.size)
        out.write(vendorBytes)
        out.writeIntLE(comments.size)
        comments.forEach { entry ->
            val bytes = entry.toByteArray(Charsets.UTF_8)
            out.writeIntLE(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- OGG fixtures

    /** "KEY=value" padded to exactly [length] bytes, matching a real tag's size. */
    private fun filler(key: String, length: Int): String = "$key=" + "x".repeat(length - key.length - 1)

    /** LRC-shaped UTF-8 text (multi-byte Arabic included) of exactly [length] bytes. */
    private fun lrcOfLength(length: Int): String {
        val sb = StringBuilder()
        var line = 0
        while (sb.toString().toByteArray(Charsets.UTF_8).size < length) {
            sb.append("[%02d:%02d.00]سطر رقم %d line\n".format(line / 60, line % 60, line))
            line++
        }
        var text = sb.toString()
        while (text.toByteArray(Charsets.UTF_8).size > length) text = text.dropLast(1)
        return text + "a".repeat(length - text.toByteArray(Charsets.UTF_8).size)
    }

    private fun imageOfSize(size: Int, seed: Int): ByteArray =
        kotlin.random.Random(seed).nextBytes(size).also { image.copyInto(it, endIndex = 8) }

    private fun base64Picture(imageBytes: ByteArray, pictureType: Int = 3): String =
        java.util.Base64.getEncoder().encodeToString(pictureBlock(imageBytes, pictureType))

    private fun opusHead(): ByteArray = ByteArrayOutputStream().apply {
        write("OpusHead".toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(1, 2, 0x38, 0x01, 0x44, 0xAC.toByte(), 0, 0, 0, 0, 0))
    }.toByteArray()

    /**
     * libopusenc layout: OpusHead alone on the BOS page, then the OpusTags packet (plus
     * [padding] zero bytes, as libopusenc reserves) on pages of 16 lacing values (4080
     * bytes), then audio pages.
     */
    private fun oggOpus(comment: ByteArray, padding: Int = 0): ByteArray {
        val tags = "OpusTags".toByteArray(Charsets.US_ASCII) + comment + ByteArray(padding)
        val audio = List(40) { ByteArray(160) { i -> (i * 31 + it).toByte() } }
        return oggPages(listOf(opusHead(), tags) + audio, maxSegmentsPerPage = 16, pageStarts = setOf(1, 2))
            .fold(ByteArray(0)) { acc, page -> acc + page }
    }

    /** libvorbis layout: id header alone on the BOS page; comment and setup headers share pages. */
    private fun oggVorbis(comment: ByteArray): ByteArray {
        val id = byteArrayOf(0x01) + "vorbis".toByteArray(Charsets.US_ASCII) + ByteArray(23) { 1 }
        val commentPacket = byteArrayOf(0x03) + "vorbis".toByteArray(Charsets.US_ASCII) + comment + byteArrayOf(0x01)
        val setup = byteArrayOf(0x05) + "vorbis".toByteArray(Charsets.US_ASCII) + ByteArray(3_000) { 5 }
        val audio = List(20) { ByteArray(400) { i -> (i + it).toByte() } }
        return oggPages(listOf(id, commentPacket, setup) + audio, maxSegmentsPerPage = 255, pageStarts = setOf(1, 3))
            .fold(ByteArray(0)) { acc, page -> acc + page }
    }

    /**
     * Lays [packets] out as spec-conformant Ogg pages: lacing values in each page's
     * segment table, packets continued across pages (header type 0x01), BOS on the
     * first page, EOS on the last, and a valid page CRC. A page is flushed once it
     * holds [maxSegmentsPerPage] lacing values, and before each packet in [pageStarts].
     */
    private fun oggPages(
        packets: List<ByteArray>,
        serial: Int = 0x16F3F5BA,
        maxSegmentsPerPage: Int,
        pageStarts: Set<Int> = emptySet(),
    ): List<ByteArray> {
        val pages = ArrayList<ByteArray>()
        val lacing = ArrayList<Int>()
        val body = ByteArrayOutputStream()
        var continued = false
        var nextPageContinued = false
        fun flush(eos: Boolean) {
            if (lacing.isEmpty()) return
            val flags = (if (continued) 0x01 else 0) or (if (pages.isEmpty()) 0x02 else 0) or (if (eos) 0x04 else 0)
            pages += oggPage(flags, serial, pages.size, lacing, body.toByteArray())
            lacing.clear(); body.reset()
            continued = nextPageContinued
        }
        packets.forEachIndexed { index, packet ->
            if (index in pageStarts) flush(eos = false)
            var offset = 0
            while (true) {
                val length = minOf(255, packet.size - offset)
                lacing += length
                body.write(packet, offset, length)
                offset += length
                val packetDone = length < 255
                nextPageContinued = !packetDone
                if (lacing.size == maxSegmentsPerPage) flush(eos = false)
                if (packetDone) break
            }
            nextPageContinued = false
        }
        flush(eos = true)
        return pages
    }

    private fun oggPage(flags: Int, serial: Int, sequence: Int, lacing: List<Int>, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OggS".toByteArray(Charsets.US_ASCII))
        out.write(0) // stream structure version
        out.write(flags)
        repeat(8) { out.write(0) } // granule position (unused by the tag reader)
        out.writeIntLE(serial)
        out.writeIntLE(sequence)
        out.writeIntLE(0) // CRC placeholder
        out.write(lacing.size)
        lacing.forEach { out.write(it) }
        out.write(body)
        val page = out.toByteArray()
        val crc = oggCrc(page)
        for (i in 0 until 4) page[22 + i] = (crc ushr (8 * i)).toByte()
        return page
    }

    /** Ogg's CRC-32 (polynomial 0x04C11DB7, zero init, unreflected) over the page with a zeroed CRC field. */
    private fun oggCrc(page: ByteArray): Int {
        var crc = 0
        for (byte in page) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        }
        return crc
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (start in 0..size - needle.size) {
            for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
            return start
        }
        return -1
    }

    private fun id3v23(frames: List<ByteArray>): ByteArray = id3(3, 0, frames)
    private fun id3v22(frames: List<ByteArray>): ByteArray = id3(2, 0, frames)

    private fun id3(major: Int, revision: Int, frames: List<ByteArray>): ByteArray {
        val body = ByteArrayOutputStream().apply { frames.forEach { write(it) } }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(major); out.write(revision); out.write(0)
        out.writeSyncSafe(body.size)
        out.write(body)
        return out.toByteArray()
    }

    private fun uslt(lang: String, text: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(3)
        content.write(lang.toByteArray(Charsets.ISO_8859_1))
        content.write(0)
        content.write(text.toByteArray(Charsets.UTF_8))
        return frame32(code = "USLT", content.toByteArray(), syncsafe = true)
    }

    private fun apic(imageBytes: ByteArray): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(0)
        content.write("image/png".toByteArray(Charsets.ISO_8859_1)); content.write(0)
        content.write(3)
        content.write(0)
        content.write(imageBytes)
        return frame32(code = "APIC", content.toByteArray(), syncsafe = true)
    }

    private fun ult(lang: String, text: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(3)
        content.write(lang.toByteArray(Charsets.ISO_8859_1))
        content.write(0)
        content.write(text.toByteArray(Charsets.UTF_8))
        return frame22(code = "ULT", content.toByteArray())
    }

    private fun pic(imageBytes: ByteArray): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(0)
        content.write("PNG".toByteArray(Charsets.ISO_8859_1))
        content.write(3)
        content.write(0)
        content.write(imageBytes)
        return frame22(code = "PIC", content.toByteArray())
    }

    /** ID3v2.2 frames carry a 3-byte code and a 24-bit big-endian size (no flags). */
    private fun frame22(code: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(code.toByteArray(Charsets.ISO_8859_1))
        out.write(data.size shr 16 and 0xFF)
        out.write(data.size shr 8 and 0xFF)
        out.write(data.size and 0xFF)
        out.write(data)
        return out.toByteArray()
    }

    private fun frame32(code: String, data: ByteArray, syncsafe: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(code.toByteArray(Charsets.ISO_8859_1))
        if (syncsafe) out.writeSyncSafe(data.size) else out.writeIntBE(data.size)
        out.write(0); out.write(0)
        out.write(data)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntBE(value: Int) {
        write(value shr 24 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 8 and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun ByteArrayOutputStream.writeSyncSafe(value: Int) {
        write(value shr 21 and 0x7F)
        write(value shr 14 and 0x7F)
        write(value shr 7 and 0x7F)
        write(value and 0x7F)
    }

    // --------------------------------------------------- MP4 / RIFF / AIFF fixtures

    /** Builds an MP4 container: 4-byte big-endian size + type + payload. */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeIntBE(8 + payload.size)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun m4a(ilst: ByteArray): File {
        val ilstBox = box("ilst", ilst)
        val meta = box("meta", (ByteArray(4) + box("hdlr", ByteArray(8))).let { it + ilstBox })
        val udta = box("udta", meta)
        val moov = box("moov", udta)
        val ftyp = box("ftyp", ("M4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(8)))
        return temp("m4a", ftyp + moov)
    }

    private fun covr(imageBytes: ByteArray): ByteArray {
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(imageBytes)
        return box("covr", box("data", dataPayload.toByteArray()))
    }

    private fun lyricAtom(text: String): ByteArray {
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(text.toByteArray(Charsets.UTF_8))
        return box("\u00A9lyr", box("data", dataPayload.toByteArray()))
    }

    /** `mean`/`name` carry a 4-byte version/flags header before the text, as written by
     *  real taggers (verified against mutagen's `----:com.apple.iTunes:LYRICS` output). */
    private fun itunesLyrics(text: String): ByteArray {
        val mean = box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray(Charsets.UTF_8))
        val name = box("name", ByteArray(4) + "LYRICS".toByteArray(Charsets.UTF_8))
        val dataPayload = ByteArrayOutputStream()
        dataPayload.write(ByteArray(8))
        dataPayload.write(text.toByteArray(Charsets.UTF_8))
        return box("----", mean + name + box("data", dataPayload.toByteArray()))
    }

    /** RIFF/AIFF chunk: id + size (chosen endianness) + payload + pad to even bounds. */
    private fun riffChunk(id: String, payload: ByteArray, bigEndian: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        if (bigEndian) out.writeIntBE(payload.size) else out.writeIntLE(payload.size)
        out.write(payload)
        if (payload.size and 1 != 0) out.write(0)
        return out.toByteArray()
    }

    private fun wav(id3Tag: ByteArray): File {
        val fmt = riffChunk("fmt ", byteArrayOf(0x01, 0x00), bigEndian = false)
        val id3 = riffChunk("id3 ", id3Tag, bigEndian = false)
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.ISO_8859_1))
            write(fmt)
            write(id3)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.writeIntLE(4 + body.size)
        out.write(body)
        return temp("wav", out.toByteArray())
    }

    private fun aiff(id3Tag: ByteArray): File {
        val comm = riffChunk("COMM", ByteArray(18), bigEndian = true)
        val id3 = riffChunk("ID3 ", id3Tag, bigEndian = true)
        val body = ByteArrayOutputStream().apply {
            write("AIFF".toByteArray(Charsets.ISO_8859_1))
            write(comm)
            write(id3)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("FORM".toByteArray(Charsets.ISO_8859_1))
        out.writeIntBE(4 + body.size)
        out.write(body)
        return temp("aiff", out.toByteArray())
    }

    private companion object {
        const val FLAC_PICTURE = 6
        const val FLAC_VORBIS_COMMENT = 4
    }
}