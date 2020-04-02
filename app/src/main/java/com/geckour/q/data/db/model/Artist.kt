package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Artist(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var title: String,
    var titleSort: String,
    var playbackCount: Long
)