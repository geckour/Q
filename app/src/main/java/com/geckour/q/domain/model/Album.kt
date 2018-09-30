package com.geckour.q.domain.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Album(
        val id: Long,
        val mediaId: Long,
        val name: String?,
        val artist: String?,
        val thumbUriString: String?,
        val totalDuration: Long
) : Parcelable