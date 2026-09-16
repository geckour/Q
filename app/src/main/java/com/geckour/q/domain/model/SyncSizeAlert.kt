package com.geckour.q.domain.model

sealed interface SyncSizeAlert {

    val downloadSize: Long
    val availableSize: Long

    data class Confirmation(
        override val downloadSize: Long,
        override val availableSize: Long,
    ) : SyncSizeAlert

    data class Exceeded(
        override val downloadSize: Long,
        override val availableSize: Long,
    ) : SyncSizeAlert
}
