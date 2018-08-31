package com.geckour.q.data.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class Album(
        @PrimaryKey var id: Long,
        var title: String,
        var artworkUriString: String?,
        var albumArtistId: Long?
)