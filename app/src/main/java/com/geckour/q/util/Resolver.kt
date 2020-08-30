package com.geckour.q.util

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.geckour.q.data.db.DB

suspend fun DB.getArtworkUriStringFromId(albumId: Long): String? =
    this@getArtworkUriStringFromId.albumDao().get(albumId)?.artworkUriString

fun Resources.Theme.getColor(@AttrRes attrResId: Int): Int =
    TypedValue().apply { this@getColor.resolveAttribute(attrResId, this, true) }.data