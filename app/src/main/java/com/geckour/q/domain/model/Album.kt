package com.geckour.q.domain.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Album(
        val id: Long,
        val name: String,
        val nameSort: String,
        val artist: String,
        val artistSort: String,
        val thumbUriString: String?,
        val totalDuration: Long
) : Parcelable, MediaItem