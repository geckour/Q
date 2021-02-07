package com.geckour.q.data.db.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geckour.q.domain.model.MediaItem
import kotlinx.android.parcel.Parcelize

@Entity
@Parcelize
data class Artist(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var title: String,
    var titleSort: String,
    var playbackCount: Long,
    var totalDuration: Long,
    var artworkUriString: String?
) : Parcelable, MediaItem