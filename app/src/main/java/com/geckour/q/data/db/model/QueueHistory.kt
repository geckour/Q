package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("createdAt")])
data class QueueHistory(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val createdAt: Long,
)

@Entity(
    indices = [
        Index("queueHistoryId", "trackId", unique = true),
        Index("trackId"),
    ]
)
data class QueueHistoryTrack(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val queueHistoryId: Long,
    val trackId: Long,
)

data class CoQueuedTrack(
    @Embedded val joinedTrack: JoinedTrack,
    val coQueuedCount: Int,
)
