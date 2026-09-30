package com.geckour.q.data.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geckour.q.domain.model.MediaItem
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class SpotifyTrack(
    @PrimaryKey val uri: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumArtistName: String?,
    val artworkUrl: String?,
    val duration: Long,
    val trackNum: Int?,
    val trackTotal: Int?,
    val discNum: Int?,
    val releaseDate: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val playbackCount: Long = 0,
    @ColumnInfo(defaultValue = "FALSE") val isFavorite: Boolean = false,
) : MediaItem
