package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Artist(
        @PrimaryKey var id: Long,
        var title: String
)