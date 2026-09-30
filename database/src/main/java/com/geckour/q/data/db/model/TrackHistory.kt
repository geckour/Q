package com.geckour.q.data.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(indices = [Index("createdAt"), Index("trackId"), Index("spotifyUri")])
data class TrackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val trackId: Long,
    val createdAt: Long,
    val spotifyUri: String? = null,
)

data class JoinedTrackHistory(
    @Embedded val trackHistory: TrackHistory,
    @Relation(parentColumn = "trackId", entityColumn = "id", entity = Track::class)
    val joinedTrack: JoinedTrack?,
    @Relation(parentColumn = "spotifyUri", entityColumn = "uri")
    val spotifyTrack: SpotifyTrack?,
)
