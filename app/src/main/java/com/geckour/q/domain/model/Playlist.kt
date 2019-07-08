package com.geckour.q.domain.model

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Playlist(
        val id: Long,
        val thumb: Bitmap?,
        val name: String,
        val memberCount: Int,
        val totalDuration: Long
) : Parcelable, MediaItem