package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Track(
        @PrimaryKey(autoGenerate = true) var id: Long,
        var mediaId: Long,
        var title: String?,
        var albumId: Long,
        var artistId: Long,
        var albumArtistId: Long?,
        var duration: Long,
        var trackNum: Int?,
        var discNum: Int?,
        var sourcePath: String,
        var playbackCount: Long,
        var ignoreOnMultipleAdd: Boolean
)