package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Album(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var artistId: Long,
    var title: String,
    var titleSort: String,
    var artworkUriString: String?,
    var hasAlbumArtist: Boolean,
    var playbackCount: Long
)