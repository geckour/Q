package com.geckour.q.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val playWhenReady: Boolean,
    val trackIds: List<Long>,
    val currentIndex: Int,
    val progress: Long,
    val repeatMode: Int
)