package me.misa198.airmedy.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A durable, idempotent edit made on a mobile playlist.  It is deliberately a
 * delta rather than a playlist snapshot: a mobile library may only contain a
 * subset of a desktop playlist's tracks.
 */
@Serializable
data class PlaylistMutation(
    @SerialName("mutation_id") val mutationId: String,
    @SerialName("playlist_id") val playlistId: String,
    val operation: PlaylistMutationOperation,
    @SerialName("updated_at") val updatedAt: Long,
    val payload: PlaylistMutationPayload = PlaylistMutationPayload(),
)

@Serializable
enum class PlaylistMutationOperation {
    CREATE, UPDATE, DELETE, ADD_TRACK, REMOVE_TRACK, MOVE_TRACK, SET_ARTWORK, REMOVE_ARTWORK, SET_FAVORITE,
}

@Serializable
data class PlaylistMutationPayload(
    val name: String? = null,
    val description: String? = null,
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("previous_track_id") val previousTrackId: String? = null,
    @SerialName("next_track_id") val nextTrackId: String? = null,
    @SerialName("artwork_sha256") val artworkSha256: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean? = null,
)

fun PlaylistMutation.validationError(): String? = when {
    mutationId.isBlank() || playlistId.isBlank() || updatedAt <= 0L -> "Missing mutation identity"
    operation == PlaylistMutationOperation.CREATE && payload.name.isNullOrBlank() -> "A playlist name is required"
    operation in setOf(PlaylistMutationOperation.ADD_TRACK, PlaylistMutationOperation.REMOVE_TRACK, PlaylistMutationOperation.MOVE_TRACK) && payload.trackId.isNullOrBlank() -> "A track ID is required"
    operation == PlaylistMutationOperation.SET_ARTWORK && !Sha256.matches(payload.artworkSha256.orEmpty()) -> "Invalid artwork hash"
    operation == PlaylistMutationOperation.SET_FAVORITE && (playlistId != "favorites" || payload.trackId.isNullOrBlank() || payload.isFavorite == null) -> "Invalid favorite mutation"
    else -> null
}

private val Sha256 = Regex("^[0-9a-f]{64}$")

data class StagedPlaylistArtwork(val sha256: String, val mime: String, val size: Long, val relativePath: String)
