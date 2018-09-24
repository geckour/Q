package com.geckour.q.domain.model

data class PlayerState(
        val queue: List<Song>,
        val currentPosition: Int,
        val progress: Float,
        val playWhenReady: Boolean
)