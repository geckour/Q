package com.geckour.q.domain.model

data class SyncProgress(
    val title: String,
    val progressFraction: Float,
    val remainingFiles: Int,
    val skippedFiles: Int,
    val totalFilesSize: Long,
    val processedFilesSize: Long,
    val remainingDuration: Long,
    val paths: List<String>,
)
