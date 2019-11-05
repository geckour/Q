package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Track(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var albumId: Long,
    var artistId: Long,
    var albumArtistId: Long?,
    var mediaId: Long,
    var sourcePath: String,
    var title: String?,
    var composer: String?,
    var duration: Long,
    var trackNum: Int?,
    var discNum: Int?,
    var playbackCount: Long,
    var ignored: Bool = Bool.FALSE
)

enum class Bool {
    TRUE,
    FALSE,
    UNDEFINED;
}