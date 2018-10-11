package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Artist(
        @PrimaryKey(autoGenerate = true) var id: Long,
        var mediaId: Long?,
        var title: String?,
        var playbackCount: Long
)