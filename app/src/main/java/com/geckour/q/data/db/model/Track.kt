package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity
data class Track(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var lastModified: Long,
    var albumId: Long,
    var artistId: Long,
    var albumArtistId: Long?,
    var mediaId: Long,
    var sourcePath: String,
    var title: String,
    var titleSort: String,
    var composer: String,
    var composerSort: String,
    var duration: Long,
    var trackNum: Int?,
    var discNum: Int?,
    var playbackCount: Long,
    var ignored: Bool = Bool.FALSE
)

data class JoinedTrack(
    @Embedded val track: Track,
    @Relation(parentColumn = "albumId", entityColumn = "id") var album: Album,
    @Relation(parentColumn = "artistId", entityColumn = "id") var artist: Artist,
    @Relation(parentColumn = "albumArtistId", entityColumn = "id") var albumArtist: Artist?,
)

enum class Bool {
    TRUE, FALSE, UNDEFINED;
}