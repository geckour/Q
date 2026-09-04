package com.geckour.q.domain.model

import com.geckour.q.data.db.model.TrackHistory

data class UiTrackHistory(
    val trackHistory: TrackHistory,
    val uiTrack: UiTrack,
)