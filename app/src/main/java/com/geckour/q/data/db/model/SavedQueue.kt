package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("updatedAt")])
data class SavedQueue(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    indices = [
        Index("savedQueueId", "sortIndex"),
        Index("trackId"),
    ]
)
data class SavedQueueTrack(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val savedQueueId: Long,
    val trackId: Long,
    val sortIndex: Int,
)

data class SavedQueueSummary(
    @Embedded val savedQueue: SavedQueue,
    val trackCount: Int,
    val totalDuration: Long,
)
