package com.geckour.q.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val playWhenReady: Boolean,
    val queue: List<DomainTrack>,
    val currentIndex: Int,
    val progress: Long,
    val repeatMode: Int
)