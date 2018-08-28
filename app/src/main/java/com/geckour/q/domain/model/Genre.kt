package com.geckour.q.domain.model

import android.graphics.Bitmap

data class Genre(
        val id: Long,
        val thumb: Bitmap?,
        val name: String
)