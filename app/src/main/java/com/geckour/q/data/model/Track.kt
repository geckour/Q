package com.geckour.q.data.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

@Entity(foreignKeys = [
    ForeignKey(entity = Artist::class, parentColumns = ["id"], childColumns = ["artistId"]),
    ForeignKey(entity = Album::class, parentColumns = ["id"], childColumns = ["albumId"])
])
data class Track(
        @PrimaryKey var id: Long,
        var title: String,
        var albumId: Long,
        var artistId: Long,
        var duration: Long,
        var sourcePath: String
)