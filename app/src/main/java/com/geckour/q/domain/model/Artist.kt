package com.geckour.q.domain.model

import android.graphics.Bitmap

data class Artist(
        val id: Long,
        val thumb: Bitmap?,
        val name: String?
)