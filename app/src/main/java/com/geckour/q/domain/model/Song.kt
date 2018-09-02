package com.geckour.q.domain.model

data class Song(
        val id: Long,
        val albumId: Long,
        val name: String?,
        val artist: String,
        val duration: Long,
        val trackNum: Int?,
        val trackTotal: Int?,
        val discNum: Int?,
        val discTotal: Int?
)