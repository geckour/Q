package com.geckour.q.domain.model

import android.graphics.Bitmap

data class Playlist(
        val id: Long,
        val thumb: Bitmap?,
        val name: String
)