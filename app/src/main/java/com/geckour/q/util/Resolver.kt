package com.geckour.q.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri

fun getArtworkUriFromAlbumId(context: Context, id: Long): Uri =
        ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), id)