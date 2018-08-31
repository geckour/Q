package com.geckour.q.domain.model

data class Song(
        val id: Long,
        val albumId: Long,
        val name: String?,
        val artist: String,
        val duration: Float,
        val trackNum: Int
)