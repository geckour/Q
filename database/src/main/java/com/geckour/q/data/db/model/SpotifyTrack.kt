package com.geckour.q.data.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.geckour.q.core.model.MediaItem
import kotlinx.serialization.Serializable

@Entity(indices = [Index("matchKey")])
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
    /** Whether the track is left out of generated queues, as [Track.ignored] for a local track. */
    @ColumnInfo(defaultValue = "FALSE") val ignored: Boolean = false,
    /** The key to link local tracks of the same song, made by [spotifyMatchKeyOf]. */
    val matchKey: String? = null,
) : MediaItem
