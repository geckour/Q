package com.geckour.q.data.db.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geckour.q.domain.model.MediaItem
import kotlinx.android.parcel.Parcelize

@Entity
@Parcelize
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val titleSort: String,
    val playbackCount: Long,
    val totalDuration: Long,
    val artworkUriString: String?
) : Parcelable, MediaItem