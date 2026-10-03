package com.geckour.q.ui.main.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.navigation.NavBackStackEntry
import com.geckour.q.core.model.MediaItem
import com.geckour.q.domain.model.Nav

@Immutable
data class ScreenMeta(
    val nav: Nav?,
    val title: String,
    val optionTarget: OptionTarget,
)

sealed interface OptionTarget {

    data class Item(val mediaItem: MediaItem?) : OptionTarget

    data class ArtistId(val artistId: Long) : OptionTarget

    data class AlbumId(val albumId: Long) : OptionTarget
}

@Composable
fun RegisterScreenMeta(
    screenMetas: SnapshotStateMap<String, ScreenMeta>,
    backStackEntry: NavBackStackEntry,
    screenMeta: ScreenMeta,
) {
    DisposableEffect(screenMetas, backStackEntry.id, screenMeta) {
        screenMetas[backStackEntry.id] = screenMeta
        onDispose { screenMetas.remove(backStackEntry.id, screenMeta) }
    }
}
