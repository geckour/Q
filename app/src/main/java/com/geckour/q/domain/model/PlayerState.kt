package com.geckour.q.domain.model

data class PlayerState(
        val playWhenReady: Boolean,
        val queue: List<Song>,
        val currentIndex: Int,
        val progress: Long,
        val repeatMode: Int
)