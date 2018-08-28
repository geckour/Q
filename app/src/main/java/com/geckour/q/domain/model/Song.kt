package com.geckour.q.domain.model

import android.graphics.Bitmap

data class Song(
        val id: Long,
        val thumb: Bitmap?,
        val name: String?,
        val artist: String
)