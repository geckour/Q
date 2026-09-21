package com.geckour.q.ui.main.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.domain.model.SpotifyRecommendedRoot
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.util.getTimeString
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val SAVED_TRACKS_ID = "tracks"

private const val SAVED_TRACKS_URI = "spotify:collection:tracks"

private const val SECTION_URI_PREFIX = "spotify:section:"

@Composable
fun SpotifySourceMenu(
    endItemMargin: Dp = 0.dp,
    onSelectSource: (source: SpotifyBrowseSource) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(SpotifyBrowseSource.entries, key = { it.name }) { source ->
            SpotifySourceItem(source = source, onClick = { onSelectSource(source) })
        }
        item {
            Spacer(modifier = Modifier.height(endItemMargin))
        }
    }
}

@Composable
fun SpotifyLevel(
    level: SpotifyLevel,
    listState: LazyListState,
    endItemMargin: Dp = 0.dp,
    onOpenContainer: (container: SpotifyContainer) -> Unit,
    onLoadMore: () -> Unit,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val items = level.items
        val showsArtwork = items.any { it.artworkUrl != null }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is SpotifyBrowseItem.Track -> {
                        SpotifyTrackItem(
                            track = item.track,
                            showsArtwork = showsArtwork,
                            onClick = {
                                onDialogEvent(DialogEvent.ShowSpotifyTrackOption(item.track))
                            },
                        )
                    }

                    is SpotifyBrowseItem.Container -> {
                        SpotifyContainerItem(
                            container = item.container,
                            showsArtwork = showsArtwork,
                            onClick = { onOpenContainer(item.container) },
                            onLongClick = {
                                onDialogEvent(
                                    DialogEvent.ShowSpotifyContainerOption(item.container)
                                )
                            },
                        )
                    }
                }
            }
            if (level.nextOffset != null) {
                item(key = "load_more") {
                    LaunchedEffect(level.nextOffset) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator(
                            color = QTheme.colors.colorAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(endItemMargin))
            }
        }

        if (items.isEmpty() && level.hasLoaded.not() && level.hasFailed.not()) {
            CircularWavyProgressIndicator(
                color = QTheme.colors.colorAccent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        val isEmpty = items.isEmpty() && level.hasLoaded && level.hasFailed.not()
        LaunchedEffect(isEmpty) {
            if (isEmpty) onDialogEvent(DialogEvent.NotifySpotifyEmpty)
        }
    }
}

@Composable
private fun SpotifySourceItem(
    source: SpotifyBrowseSource,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = source.icon,
            contentDescription = null,
            tint = QTheme.colors.colorTextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(id = source.labelResId),
            fontSize = 16.sp,
            color = QTheme.colors.colorTextPrimary
        )
    }
}

fun spotifyContainerOptionTarget(container: SpotifyContainer): SpotifyContainer? =
    container.takeIf { it.holdsTracks }

@Composable
fun spotifySourceOptionTarget(
    source: SpotifyBrowseSource,
    isFlattened: Boolean,
): MediaItem? = when (source) {
    SpotifyBrowseSource.RECOMMENDED -> SpotifyRecommendedRoot(isFlattened)

    SpotifyBrowseSource.SAVED -> SpotifyContainer(
        kind = SpotifyContainer.Kind.SAVED,
        id = SAVED_TRACKS_ID,
        uri = SAVED_TRACKS_URI,
        name = stringResource(id = R.string.spotify_tab_saved),
        creatorName = null,
        artworkUrl = null,
        releaseDate = null,
        totalTracks = null,
    )

    SpotifyBrowseSource.PLAYLISTS -> null
}

@Composable
fun spotifyTopBarTitle(section: String?): String {
    section ?: return stringResource(id = R.string.spotify_title)

    return stringResource(id = R.string.spotify_title_with_section, section)
}

@Composable
fun spotifySourceLabel(source: SpotifyBrowseSource): String =
    stringResource(id = source.labelResId)

private val SpotifyContainer.holdsTracks: Boolean
    get() = uri.startsWith(SECTION_URI_PREFIX).not()

private val SpotifyBrowseSource.labelResId: Int
    get() = when (this) {
        SpotifyBrowseSource.SAVED -> R.string.spotify_tab_saved
        SpotifyBrowseSource.PLAYLISTS -> R.string.spotify_tab_playlists
        SpotifyBrowseSource.RECOMMENDED -> R.string.spotify_tab_recommended
    }

private val SpotifyBrowseSource.icon: ImageVector
    get() = when (this) {
        SpotifyBrowseSource.SAVED -> Icons.Default.Star
        SpotifyBrowseSource.PLAYLISTS -> Icons.Default.QueueMusic
        SpotifyBrowseSource.RECOMMENDED -> Icons.Default.AutoAwesome
    }

@Composable
private fun SpotifyTrackItem(
    track: SpotifyTrack,
    showsArtwork: Boolean,
    onClick: () -> Unit,
) {
    SpotifyItemRow(
        artworkUrl = track.artworkUrl,
        showsArtwork = showsArtwork,
        title = track.title,
        subtitle = "${track.artistName} - ${track.albumName}",
        trailing = track.duration.getTimeString(),
        onClick = onClick,
    )
}

@Composable
private fun SpotifyContainerItem(
    container: SpotifyContainer,
    showsArtwork: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kindLabel = when (container.kind) {
        SpotifyContainer.Kind.ALBUM -> stringResource(id = R.string.spotify_item_album)
        SpotifyContainer.Kind.ARTIST -> stringResource(id = R.string.spotify_item_artist)
        SpotifyContainer.Kind.PLAYLIST -> stringResource(id = R.string.spotify_item_playlist)
        SpotifyContainer.Kind.SAVED, SpotifyContainer.Kind.CONTENT -> null
    }

    SpotifyItemRow(
        artworkUrl = container.artworkUrl,
        showsArtwork = showsArtwork,
        title = container.name,
        subtitle = listOfNotNull(kindLabel, container.creatorName).joinToString(" - "),
        trailing = container.totalTracks?.let {
            stringResource(id = R.string.spotify_item_track_count, it)
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotifyItemRow(
    artworkUrl: String?,
    showsArtwork: Boolean,
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val isSingleLine = subtitle.isBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .padding(
                horizontal = if (isSingleLine) 16.dp else 12.dp,
                vertical = if (isSingleLine) 12.dp else 8.dp,
            )
    ) {
        if (showsArtwork) {
            AsyncImage(
                model = artworkUrl ?: R.drawable.ic_empty,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = if (isSingleLine) 18.sp else 16.sp,
                color = QTheme.colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSingleLine.not()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = QTheme.colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = it,
                fontSize = 12.sp,
                color = QTheme.colors.colorTextPrimary
            )
        }
    }
}
