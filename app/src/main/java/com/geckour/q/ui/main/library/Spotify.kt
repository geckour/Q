package com.geckour.q.ui.main.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.util.getTimeString
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val SAVED_TRACKS_ID = "tracks"

private const val SAVED_TRACKS_URI = "spotify:collection:tracks"

@Composable
fun Spotify(
    browse: SpotifyBrowseState,
    isConfigured: Boolean,
    hasCredential: Boolean,
    endItemMargin: Dp = 0.dp,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    when {
        isConfigured.not() -> {
            SpotifyMessage(
                message = stringResource(id = R.string.spotify_message_not_configured),
            )
        }

        hasCredential.not() -> {
            SpotifyMessage(
                message = stringResource(id = R.string.spotify_message_auth),
                actionLabelResId = R.string.dialog_ok,
                onAction = { onDialogEvent(DialogEvent.StartSpotifyAuth) },
            )
        }

        else -> {
            SpotifyBrowser(
                browse = browse,
                endItemMargin = endItemMargin,
                onDialogEvent = onDialogEvent,
            )
        }
    }
}

@Composable
private fun SpotifyMessage(
    message: String,
    @StringRes actionLabelResId: Int? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            fontSize = 16.sp,
            color = QTheme.colors.colorTextPrimary,
        )
        if (actionLabelResId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(
                    text = stringResource(id = actionLabelResId),
                    fontSize = 16.sp,
                    color = QTheme.colors.colorAccent,
                )
            }
        }
    }
}

@Composable
private fun SpotifyBrowser(
    browse: SpotifyBrowseState,
    endItemMargin: Dp,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val container = browse.container
    val source = browse.source

    BackHandler(container != null || source != null) {
        if (container != null) onDialogEvent(DialogEvent.CloseSpotifyContainer)
        else onDialogEvent(DialogEvent.ChangeSpotifySource(null))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        browse.errorMessage?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                color = QTheme.colors.colorAccent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val items = if (container == null) browse.items else browse.containerItems
            val showsSourceMenu = source == null
            val hasMore =
                if (container == null) browse.nextOffset != null
                else browse.containerNextOffset != null

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (showsSourceMenu) {
                    items(SpotifyBrowseSource.entries, key = { it.name }) { menuSource ->
                        SpotifySourceItem(
                            source = menuSource,
                            onClick = {
                                onDialogEvent(DialogEvent.ChangeSpotifySource(menuSource))
                            },
                        )
                    }
                }
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is SpotifyBrowseItem.Track -> {
                            SpotifyTrackItem(
                                track = item.track,
                                onClick = {
                                    onDialogEvent(
                                        DialogEvent.ShowSpotifyTrackOption(item.track)
                                    )
                                },
                            )
                        }

                        is SpotifyBrowseItem.Container -> {
                            SpotifyContainerItem(
                                container = item.container,
                                onClick = {
                                    onDialogEvent(
                                        DialogEvent.OpenSpotifyContainer(item.container)
                                    )
                                },
                                onLongClick = {
                                    onDialogEvent(
                                        DialogEvent.ShowSpotifyContainerOption(item.container)
                                    )
                                },
                            )
                        }
                    }
                }
                if (hasMore) {
                    item(key = "load_more") {
                        LaunchedEffect(browse.nextOffset, browse.containerNextOffset) {
                            onDialogEvent(DialogEvent.LoadMoreSpotifyItems)
                        }
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
            when {
                items.isNotEmpty() || showsSourceMenu -> Unit

                browse.isLoading -> {
                    CircularWavyProgressIndicator(
                        color = QTheme.colors.colorAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                browse.errorMessage == null -> {
                    Text(
                        text = stringResource(id = R.string.spotify_message_empty),
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextSecondary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }
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

@Composable
fun spotifyOptionTarget(browse: SpotifyBrowseState): SpotifyContainer? {
    browse.container?.let { return it }
    if (browse.source != SpotifyBrowseSource.SAVED) return null

    return SpotifyContainer(
        kind = SpotifyContainer.Kind.SAVED,
        id = SAVED_TRACKS_ID,
        uri = SAVED_TRACKS_URI,
        name = stringResource(id = R.string.spotify_tab_saved),
        creatorName = null,
        artworkUrl = null,
        releaseDate = null,
        totalTracks = null,
    )
}

@Composable
fun spotifyTopBarTitle(browse: SpotifyBrowseState): String {
    val section = browse.container?.name
        ?: browse.source?.let { stringResource(id = it.labelResId) }
        ?: return stringResource(id = R.string.spotify_title)

    return stringResource(id = R.string.spotify_title_with_section, section)
}

private val SpotifyBrowseSource.labelResId: Int
    get() = when (this) {
        SpotifyBrowseSource.SAVED -> R.string.spotify_tab_saved
        SpotifyBrowseSource.PLAYLISTS -> R.string.spotify_tab_playlists
    }

private val SpotifyBrowseSource.icon: ImageVector
    get() = when (this) {
        SpotifyBrowseSource.SAVED -> Icons.Default.Star
        SpotifyBrowseSource.PLAYLISTS -> Icons.Default.QueueMusic
    }

@Composable
private fun SpotifyTrackItem(
    track: SpotifyTrack,
    onClick: () -> Unit,
) {
    SpotifyItemRow(
        artworkUrl = track.artworkUrl,
        title = track.title,
        subtitle = "${track.artistName} - ${track.albumName}",
        trailing = track.duration.getTimeString(),
        onClick = onClick,
    )
}

@Composable
private fun SpotifyContainerItem(
    container: SpotifyContainer,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kindLabel = when (container.kind) {
        SpotifyContainer.Kind.ALBUM -> stringResource(id = R.string.spotify_item_album)
        SpotifyContainer.Kind.ARTIST -> stringResource(id = R.string.spotify_item_artist)
        SpotifyContainer.Kind.PLAYLIST -> stringResource(id = R.string.spotify_item_playlist)
        SpotifyContainer.Kind.SAVED -> null
    }

    SpotifyItemRow(
        artworkUrl = container.artworkUrl,
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
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = artworkUrl ?: R.drawable.ic_empty,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = QTheme.colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = QTheme.colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
