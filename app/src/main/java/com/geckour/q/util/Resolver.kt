package com.geckour.q.util

import android.content.ContentUris
import android.net.Uri
import com.geckour.q.data.db.DB
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import com.geckour.q.data.db.model.Album as DBAlbum
import com.geckour.q.domain.model.Album as DomainAlbum

fun DB.getArtworkUriFromId(albumId: Long, parentJob: Job? = null): Deferred<Uri?> =
        if (parentJob != null) async(parentJob) {
            this@getArtworkUriFromId.albumDao().get(albumId)?.getArtworkUri()
        } else async { this@getArtworkUriFromId.albumDao().get(albumId)?.getArtworkUri() }

fun DomainAlbum.getArtworkUri(): Uri = mediaId.getArtworkUriFromMediaId()

fun DBAlbum.getArtworkUri(): Uri = mediaId.getArtworkUriFromMediaId()

fun Long.getArtworkUriFromMediaId(): Uri =
        ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), this)