package com.geckour.q.domain.model

import android.os.Parcelable
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import kotlinx.android.parcel.Parcelize

@Parcelize
data class RequestedTransaction(
    val tag: Tag,
    val artist: Artist? = null,
    val album: Album? = null,
    val genre: Genre? = null
) : Parcelable {

    enum class Tag {
        ARTIST,
        ALBUM,
        GENRE,
        EASTER_EGG
    }
}