package com.geckour.q.domain.model

data class PlayerState(
        val queue: List<Song>,
        val currentPosition: Int,
        val progress: Long,
        val playWhenReady: Boolean
)