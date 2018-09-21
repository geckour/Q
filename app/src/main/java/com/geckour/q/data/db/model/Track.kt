package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Track(
        @PrimaryKey var id: Long,
        var title: String,
        var albumId: Long,
        var artistId: Long,
        var albumArtistId: Long?,
        var duration: Long,
        var trackNum: Int?,
        var trackTotal: Int?,
        var discNum: Int?,
        var discTotal: Int?,
        var sourcePath: String
)