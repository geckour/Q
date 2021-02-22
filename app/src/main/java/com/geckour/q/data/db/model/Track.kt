package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val mediaId: Long,
    val lastModified: Long,
    val codec: String,
    val bitrate: Long,
    val sampleRate: Int,
    val albumId: Long,
    val artistId: Long,
    val albumArtistId: Long?,
    val sourcePath: String,
    val dropboxPath: String?,
    val dropboxExpiredAt: Long?,
    val title: String,
    val titleSort: String,
    val composer: String,
    val composerSort: String,
    val duration: Long,
    val trackNum: Int?,
    val discNum: Int?,
    val playbackCount: Long,
    val artworkUriString: String?,
    val ignored: Bool = Bool.FALSE
)

data class JoinedTrack(
    @Embedded val track: Track,
    @Relation(parentColumn = "albumId", entityColumn = "id") val album: Album,
    @Relation(parentColumn = "artistId", entityColumn = "id") val artist: Artist,
    @Relation(parentColumn = "albumArtistId", entityColumn = "id") val albumArtist: Artist?,
)

enum class Bool {
    TRUE, FALSE, UNDEFINED;
}