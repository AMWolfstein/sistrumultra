package me.misa198.airmedy.library

import kotlinx.coroutines.flow.Flow

/**
 * Reference to an album.
 *
 * `id` must be generated later by hashing `normalizationKey(name)` (see LocalLibraryUtils.kt): a
 * Unicode-normalized form of the name — lowercase, trimmed, internal whitespace collapsed, and
 * NFKD-folded to strip diacritics — so that e.g. "Björk" and "Bjork" produce the same id.
 * Never hash the raw name.
 */
data class LocalAlbumRef(val id: String, val name: String)

/**
 * Reference to an artist (track artist or album artist).
 *
 * `id` must be generated later by hashing `normalizationKey(name)` (see LocalLibraryUtils.kt): a
 * Unicode-normalized form of the name — lowercase, trimmed, internal whitespace collapsed, and
 * NFKD-folded to strip diacritics — so that e.g. "Björk" and "Bjork" produce the same id.
 * Never hash the raw name.
 */
data class LocalArtistRef(val id: String, val name: String)

/**
 * A genre.
 *
 * `id` must be generated later by hashing `normalizationKey(name)` (see LocalLibraryUtils.kt): a
 * Unicode-normalized form of the name — lowercase, trimmed, internal whitespace collapsed, and
 * NFKD-folded to strip diacritics — so that e.g. "Björk" and "Bjork" produce the same id.
 * Never hash the raw name.
 */
data class LocalGenre(val id: String, val name: String)

/**
 * A composer.
 *
 * `id` must be generated later by hashing `normalizationKey(name)` (see LocalLibraryUtils.kt): a
 * Unicode-normalized form of the name — lowercase, trimmed, internal whitespace collapsed, and
 * NFKD-folded to strip diacritics — so that e.g. "Björk" and "Bjork" produce the same id.
 * Never hash the raw name.
 */
data class LocalComposer(val id: String, val name: String)

/** One row per scanned audio file. */
data class LocalTrack(
    /** Populated later as "local:$mediaId" using MediaStore's own row ID, not a computed hash. */
    val id: String,
    val title: String,
    /** Per-track artists (MediaStore ARTIST). Independent of [albumArtists]. */
    val artists: List<LocalArtistRef>,
    /**
     * Album artists, sourced separately from MediaStore's ALBUM_ARTIST column. Must never be
     * derived from, or used to derive, [artists].
     */
    val albumArtists: List<LocalArtistRef>,
    val composers: List<LocalComposer>,
    val genres: List<LocalGenre>,
    val album: LocalAlbumRef,
    val durationMillis: Long,
    val discNumber: Int,
    val trackNumber: Int,
    val year: Int?,
    /**
     * ISO date string. Populated later from full-precision tags (ID3 TDRC, Vorbis DATE) when
     * present, since a bare [year] can't distinguish two releases in the same year.
     */
    val releaseDate: String?,
    val label: String?,
    val isrc: String?,
    val bpm: Int?,
    val copyright: String?,
    /** Container format, e.g. "m4a", "opus", "flac". */
    val format: String,
    /**
     * The actual codec inside the container, e.g. "aac" or "alac" for an m4a file.
     *
     * WARNING: this is conceptually different from [format]. Do NOT compute it the same way as
     * [format] (e.g. from the same MIME type / file extension). Determine it by real codec
     * inspection. A previous implementation made codec always equal format, so ALAC files in
     * m4a containers could never be detected as lossless.
     */
    val codec: String,
    val bitrate: Int,
    val sampleRate: Int,
    val bitDepth: Int?,
    val fileSize: Long,
    /** ISO date string. */
    val addedAt: String,
    val artworkKey: String?,
    val playCount: Int,
    val sortTitle: String,
    val archived: Boolean,
)

/** Placeholder types for the read API; fields to be fleshed out with the storage layer. */
data class LocalAlbum(val ref: LocalAlbumRef, val artists: List<LocalArtistRef>, val trackCount: Int)
data class LocalArtist(val ref: LocalArtistRef, val trackCount: Int)
data class LocalPlaylist(val id: String, val name: String, val trackIds: List<String>)

data class LocalSearchResults(
    val tracks: List<LocalTrack>,
    val albums: List<LocalAlbum>,
    val artists: List<LocalArtist>,
)

/** Scan / availability state of the local library. */
sealed interface LocalLibraryStatus {
    /** Storage permission not granted or media source unavailable. */
    data object Unavailable : LocalLibraryStatus
    data object Idle : LocalLibraryStatus
    data class Scanning(val scanned: Int, val total: Int?) : LocalLibraryStatus
}

/** Read-only access to the on-device library. Reactive data is exposed as [Flow]s. */
interface LocalLibrary {
    val status: Flow<LocalLibraryStatus>
    val tracks: Flow<List<LocalTrack>>
    val albums: Flow<List<LocalAlbum>>
    val artists: Flow<List<LocalArtist>>
    val genres: Flow<List<LocalGenre>>
    val composers: Flow<List<LocalComposer>>
    val playlists: Flow<List<LocalPlaylist>>

    fun search(query: String): Flow<LocalSearchResults>
}
