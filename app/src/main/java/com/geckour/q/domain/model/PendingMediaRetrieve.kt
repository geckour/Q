package com.geckour.q.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingMediaRetrieve(
    val rootPath: String,
    val needDownloaded: Boolean,
    val generation: Int,
)
