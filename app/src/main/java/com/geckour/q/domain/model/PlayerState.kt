package com.geckour.q.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val playWhenReady: Boolean,
    val sourcePaths: List<String>,
    val currentIndex: Int,
    val duration: Long,
    val progress: Long,
    val repeatMode: Int
)