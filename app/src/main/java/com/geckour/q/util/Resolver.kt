package com.geckour.q.util

import android.content.ContentUris
import android.net.Uri

fun getArtworkUriFromAlbumId(id: Long): Uri =
        ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), id)