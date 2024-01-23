package com.geckour.q.data.db.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.geckour.q.domain.model.MediaItem
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

@Entity
@Parcelize
@Serializable
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val artistId: Long,
    val title: String,
    val titleSort: String,
    val artworkUriString: String?,
    val hasAlbumArtist: Boolean,
    val playbackCount: Long,
    val totalDuration: Long,
    @ColumnInfo(defaultValue = "FALSE") val isFavorite: Boolean = false,
) : Parcelable, MediaItem

@Parcelize
data class JoinedAlbum(
    @Embedded val album: Album,
    @Relation(parentColumn = "artistId", entityColumn = "id") var artist: Artist,
) : Parcelable