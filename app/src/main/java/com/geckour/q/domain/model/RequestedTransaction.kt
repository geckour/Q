package com.geckour.q.domain.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class RequestedTransaction(
        val tag: String,
        val artist: Artist? = null,
        val album: Album? = null,
        val genre: Genre? = null,
        val playlist: Playlist? = null
) : Parcelable