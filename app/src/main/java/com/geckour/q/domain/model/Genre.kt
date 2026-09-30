package com.geckour.q.domain.model

import android.graphics.Bitmap
import android.os.Parcelable
import com.geckour.q.core.model.MediaItem
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Genre(
        val thumb: Bitmap?,
        val name: String,
        val totalDuration: Long
) : Parcelable, MediaItem