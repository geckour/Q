package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["parentUri", "uri"],
    indices = [Index("title"), Index("parentUri")],
)
data class SpotifyContentEntry(
    val parentUri: String,
    val uri: String,
    val contentId: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val position: Int,
    val updatedAt: Long,
)
