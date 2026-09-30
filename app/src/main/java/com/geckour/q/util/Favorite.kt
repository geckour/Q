package com.geckour.q.util

import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.UiTrack

fun com.geckour.q.core.model.MediaItem?.isFavoriteToggled(): com.geckour.q.core.model.MediaItem? =
    when (this) {
        is UiTrack -> {
            copy(isFavorite = isFavorite.not())
        }

        is Album -> {
            copy(isFavorite = isFavorite.not())
        }

        is Artist -> {
            copy(isFavorite = isFavorite.not())
        }

        else -> null
    }
