package com.geckour.q.domain.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Artist(
    val id: Long,
    val name: String,
    val nameSort: String,
    val thumbUriString: String?,
    val totalDuration: Long
) : Parcelable, MediaItem