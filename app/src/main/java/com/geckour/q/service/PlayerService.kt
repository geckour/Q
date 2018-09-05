package com.geckour.q.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.geckour.q.domain.model.Song

class PlayerService : Service() {

    enum class InsertActionType {
        NEXT,
        LAST,
        OVERRIDE,
        SHUFFLE_NEXT,
        SHUFFLE_LAST,
        SHUFFLE_OVERRIDE
    }

    enum class OrientedClassType {
        ARTIST,
        ALBUM,
        SONG,
        GENRE,
        PLAYLIST
    }

    data class QueueMetadata(
            val actionType: InsertActionType,
            val classType: OrientedClassType
    )

    data class InsertQueue(
            val metadata: QueueMetadata,
            val queue: List<Song>
    )

    override fun onBind(intent: Intent?): IBinder? = null
}