package com.geckour.q.util

import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.domain.model.UiTrack

enum class InsertActionType {
    NEXT,
    LAST,
    OVERRIDE,
    SHUFFLE_NEXT,
    SHUFFLE_LAST,
    SHUFFLE_OVERRIDE,
    SHUFFLE_SIMPLE_NEXT,
    SHUFFLE_SIMPLE_LAST,
    SHUFFLE_SIMPLE_OVERRIDE
}

enum class ShuffleActionType {
    SHUFFLE_SIMPLE,
    SHUFFLE_ALBUM_ORIENTED,
    SHUFFLE_ARTIST_ORIENTED
}

enum class OrientedClassType {
    ARTIST,
    ALBUM,
    TRACK,
    GENRE
}

data class QueueMetadata(
    val actionType: InsertActionType,
    val classType: OrientedClassType
)

data class QueueInfo(
    val metadata: QueueMetadata,
    val queue: List<JoinedTrack>
)

private val InsertActionType.isSimpleShuffle: Boolean
    get() = this == InsertActionType.SHUFFLE_SIMPLE_NEXT ||
            this == InsertActionType.SHUFFLE_SIMPLE_LAST ||
            this == InsertActionType.SHUFFLE_SIMPLE_OVERRIDE

private val InsertActionType.isOrientedShuffle: Boolean
    get() = this == InsertActionType.SHUFFLE_NEXT ||
            this == InsertActionType.SHUFFLE_LAST ||
            this == InsertActionType.SHUFFLE_OVERRIDE

fun List<JoinedTrack>.orderModified(
    classType: OrientedClassType,
    actionType: InsertActionType
): List<JoinedTrack> {
    if (actionType.isSimpleShuffle) return shuffled()

    return this.groupBy { it.album }
        .map { (album, tracks) ->
            album to tracks.groupBy { it.track.discNum }
                .map { (diskNum, track) ->
                    diskNum to track.sortedBy { it.track.trackNum }
                }
                .sortedBy { it.first }
                .flatMap { it.second }
        }
        .let {
            if (actionType.isOrientedShuffle && classType == OrientedClassType.ALBUM) it.shuffled() else it
        }
        .groupBy { it.first.artistId }
        .toList()
        .let {
            if (actionType.isOrientedShuffle && classType == OrientedClassType.ARTIST) it.shuffled() else it
        }
        .flatMap { (_, albumTrackMap) ->
            albumTrackMap.flatMap { it.second }
        }
}

@JvmName("orderModifiedUiTracks")
fun List<UiTrack>.orderModified(
    classType: OrientedClassType,
    actionType: InsertActionType
): List<UiTrack> {
    if (actionType.isSimpleShuffle) return shuffled()

    return groupBy { if (it.isSpotify) it.album.title else it.album.id }
        .values
        .map { tracks -> tracks.sortedWith(compareBy({ it.discNum }, { it.trackNum })) }
        .let {
            if (actionType.isOrientedShuffle && classType == OrientedClassType.ALBUM) it.shuffled() else it
        }
        .groupBy { tracks ->
            val track = tracks.first()
            if (track.isSpotify) (track.albumArtist ?: track.artist).title
            else track.album.artistId
        }
        .values
        .let {
            if (actionType.isOrientedShuffle && classType == OrientedClassType.ARTIST) it.shuffled() else it
        }
        .flatMap { albums -> albums.flatten() }
}
