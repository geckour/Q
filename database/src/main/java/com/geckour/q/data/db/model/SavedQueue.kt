package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(indices = [Index("updatedAt")])
data class SavedQueue(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    indices = [
        Index("savedQueueId", "sortIndex"),
        Index("trackId"),
        Index("spotifyUri"),
    ]
)
data class SavedQueueTrack(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val savedQueueId: Long,
    val trackId: Long,
    val sortIndex: Int,
    val spotifyUri: String? = null,
)

data class JoinedSavedQueueTrack(
    @Embedded val savedQueueTrack: SavedQueueTrack,
    @Relation(parentColumn = "trackId", entityColumn = "id", entity = Track::class)
    val joinedTrack: JoinedTrack?,
    @Relation(parentColumn = "spotifyUri", entityColumn = "uri")
    val spotifyTrack: SpotifyTrack?,
)

data class SavedQueueSummary(
    @Embedded val savedQueue: SavedQueue,
    val trackCount: Int,
    val totalDuration: Long,
)
