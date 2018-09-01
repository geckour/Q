package com.geckour.q.data.db.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class Artist(
        @PrimaryKey var id: Long,
        var title: String
)