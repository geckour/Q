package com.geckour.q.data.db.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geckour.q.domain.model.MediaItem
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

@Entity
@Parcelize
@Serializable
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val titleSort: String,
    val playbackCount: Long,
    val totalDuration: Long,
    val artworkUriString: String?,
    @ColumnInfo(defaultValue = "FALSE") val isFavorite: Boolean = false,
) : Parcelable, MediaItem