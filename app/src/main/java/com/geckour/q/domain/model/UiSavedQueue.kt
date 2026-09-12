package com.geckour.q.domain.model

import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.SavedQueueSummary

data class UiSavedQueue(
    val savedQueueSummary: SavedQueueSummary,
    val artworkUrlStrings: List<String>,
    val queue: List<JoinedTrack>,
)