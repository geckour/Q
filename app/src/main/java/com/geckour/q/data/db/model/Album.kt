package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Album(
        @PrimaryKey var id: Long,
        var title: String,
        var artistId: Long,
        var artworkUriString: String?
)