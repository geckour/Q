package com.geckour.q.util

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.geckour.q.data.db.DB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun DB.getArtworkUriStringFromId(albumId: Long): String? = withContext(Dispatchers.IO) {
    this@getArtworkUriStringFromId.albumDao().get(albumId)?.artworkUriString
}

fun Resources.Theme.getColor(@AttrRes attrResId: Int): Int =
    TypedValue().apply { this@getColor.resolveAttribute(attrResId, this, true) }.data