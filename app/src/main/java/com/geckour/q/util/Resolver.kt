package com.geckour.q.util

import android.content.ContentUris
import android.content.res.Resources
import android.net.Uri
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.geckour.q.data.db.DB
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import com.geckour.q.data.db.model.Album as DBAlbum
import com.geckour.q.domain.model.Album as DomainAlbum

fun DB.getArtworkUriStringFromId(albumId: Long, parentJob: Job? = null): Deferred<String?> =
        if (parentJob != null) GlobalScope.async(parentJob) {
            this@getArtworkUriStringFromId.albumDao().get(albumId)?.artworkUriString
        } else GlobalScope.async {
            this@getArtworkUriStringFromId.albumDao().get(albumId)?.artworkUriString
        }

fun Long.getArtworkUriFromMediaId(): Uri =
        ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), this)

fun Resources.Theme.getColor(@AttrRes attrResId: Int): Int =
        TypedValue().apply { this@getColor.resolveAttribute(attrResId, this, true) }.data