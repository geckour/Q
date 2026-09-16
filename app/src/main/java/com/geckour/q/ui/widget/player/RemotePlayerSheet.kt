package com.geckour.q.ui.widget.player

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.rememberRemoteScrollState
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.verticalScroll
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import com.geckour.q.R
import com.geckour.q.ui.widget.WidgetHostAction
import com.geckour.q.util.getTimeString

/**
 * RemoteCompose rendition of [com.geckour.q.ui.main.PlayerSheet].
 *
 * The interactive Compose sheet is turned into a serialisable document here: state arrives as an
 * immutable [PlayerWidgetState] instead of flows, callbacks become click actions carrying the id
 * the launcher reports back (see [WidgetHostAction]), and every icon is an already rasterised
 * bitmap because the document cannot reach back into the app's resources.
 *
 * How much of the sheet is drawn depends on how tall the launcher made the widget; see
 * [PlayerWidgetLayout].
 */
@Composable
@RemoteComposable
internal fun RemotePlayerSheet(
    state: PlayerWidgetState,
    colors: PlayerWidgetColors,
    activeIcons: PlayerWidgetIcons,
    inactiveIcons: PlayerWidgetIcons,
    layout: PlayerWidgetLayout,
) {
    RemoteColumn(
        modifier = RemoteModifier
            .fillMaxSize()
            .background(colors.background.rc)
            .widgetClickable(PlayerWidgetAction.OpenApp.actionId)
    ) {
        RemoteColumn(
            modifier = RemoteModifier
                .fillMaxWidth()
                .padding(layout.contentPaddingDp.rdp)
        ) {
            RemoteTrackInfo(
                state = state,
                colors = colors,
                artworkSizeDp = layout.artworkSizeDp,
            )
            if (layout.showControls) {
                RemoteBox(modifier = RemoteModifier.height(8.rdp))
                RemoteControls(
                    state = state,
                    icons = if (state.hasQueue) activeIcons else inactiveIcons,
                )
            }
        }
        if (layout.showQueue) {
            RemoteQueueSummary(
                state = state,
                colors = colors,
                layout = layout,
            )
            RemoteQueue(
                modifier = RemoteModifier.weight(1f.rf),
                state = state,
                colors = colors,
            )
        }
    }
}

@Composable
@RemoteComposable
private fun RemoteTrackInfo(
    state: PlayerWidgetState,
    colors: PlayerWidgetColors,
    artworkSizeDp: Int,
) {
    val currentTrack = state.currentTrack

    RemoteBox(
        modifier = RemoteModifier.fillMaxWidth(),
        contentAlignment = RemoteAlignment.BottomEnd,
    ) {
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
            RemoteArtwork(
                artwork = state.artwork,
                colors = colors,
                sizeDp = artworkSizeDp,
            )
            RemoteBox(modifier = RemoteModifier.width(12.rdp))
            RemoteColumn(modifier = RemoteModifier.weight(1f.rf)) {
                RemoteText(
                    text = (currentTrack?.title.orEmpty()).rs,
                    color = colors.textPrimary.rc,
                    fontSize = 16.rsp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = RemoteModifier.fillMaxWidth(),
                )
                RemoteBox(modifier = RemoteModifier.height(2.rdp))
                RemoteText(
                    text = currentTrack
                        ?.let { "${it.artist} - ${it.album}" }
                        .orEmpty()
                        .rs,
                    color = colors.textSecondary.rc,
                    fontSize = 12.rsp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = RemoteModifier.fillMaxWidth(),
                )
                RemoteBox(modifier = RemoteModifier.height(4.rdp))
            }
        }
        RemoteText(
            text = currentTrack?.duration?.getTimeString().orEmpty().rs,
            color = colors.textSecondary.rc,
            fontSize = 10.rsp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
@RemoteComposable
private fun RemoteArtwork(
    artwork: ImageBitmap?,
    colors: PlayerWidgetColors,
    sizeDp: Int,
) {
    RemoteBox(
        modifier = RemoteModifier
            .size(sizeDp.rdp)
            .background(colors.inactive.rc)
    ) {
        if (artwork != null) {
            RemoteImage(
                remoteBitmap = RemoteImageBitmap(artwork),
                contentDescription = null,
                modifier = RemoteModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
@RemoteComposable
private fun RemoteControls(
    state: PlayerWidgetState,
    icons: PlayerWidgetIcons,
) {
    RemoteColumn(
        modifier = RemoteModifier.fillMaxWidth(),
        verticalArrangement = RemoteArrangement.Center,
    ) {
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            horizontalArrangement = RemoteArrangement.End,
            verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
            RemoteIconButton(
                icon = icons.repeat(state.repeatMode),
                action = PlayerWidgetAction.RotateRepeatMode,
                size = 24,
            )
            RemoteIconButton(
                icon = icons.shuffle,
                action = PlayerWidgetAction.Shuffle,
                size = 24,
            )
        }
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            horizontalArrangement = RemoteArrangement.Center,
            verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
            RemoteIconButton(
                icon = icons.prev,
                action = PlayerWidgetAction.Prev,
                size = 24,
            )
            RemoteIconButton(
                icon =
                    if (state.playing && state.playbackState == Player.STATE_READY) icons.pause
                    else icons.play,
                action = PlayerWidgetAction.TogglePlayPause,
                size = 24,
                modifier = RemoteModifier.padding(horizontal = 20.rdp),
            )
            RemoteIconButton(
                icon = icons.next,
                action = PlayerWidgetAction.Next,
                size = 24,
            )
        }
    }
}

/** Mirrors the total duration [com.geckour.q.ui.main.Controller] shows above the queue. */
@Composable
@RemoteComposable
private fun RemoteQueueSummary(
    state: PlayerWidgetState,
    colors: PlayerWidgetColors,
    layout: PlayerWidgetLayout,
) {
    if (!state.hasQueue) return

    RemoteText(
        text = stringResource(
            R.string.bottom_sheet_time_total,
            state.queueTotalDuration.getTimeString(),
        ).rs,
        color = colors.textSecondary.rc,
        fontSize = 10.rsp,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = RemoteModifier
            .fillMaxWidth()
            .padding(
                vertical = 6.rdp,
                horizontal = layout.contentPaddingDp.rdp,
            ),
    )
}

@Composable
@RemoteComposable
private fun RemoteQueue(
    modifier: RemoteModifier,
    state: PlayerWidgetState,
    colors: PlayerWidgetColors,
) {
    if (!state.hasQueue) return

    val scrollState = rememberRemoteScrollState()

    RemoteColumn(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        state.queue.forEachIndexed { index, track ->
            RemoteQueueItem(
                track = track,
                nowPlaying = index == state.currentIndex,
                colors = colors,
            )
        }
    }
}

@Composable
@RemoteComposable
private fun RemoteQueueItem(
    track: PlayerWidgetTrack,
    nowPlaying: Boolean,
    colors: PlayerWidgetColors,
) {
    RemoteRow(
        modifier = RemoteModifier
            .fillMaxWidth()
            .background((if (nowPlaying) colors.nowPlaying else colors.background).rc)
            .padding(vertical = 8.rdp, horizontal = 12.rdp),
        verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
        RemoteColumn(modifier = RemoteModifier.weight(1f.rf)) {
            RemoteText(
                text = track.title.rs,
                color = colors.textPrimary.rc,
                fontSize = 12.rsp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = RemoteModifier.fillMaxWidth(),
            )
            RemoteText(
                text = "${track.artist} - ${track.album}".rs,
                color = colors.textSecondary.rc,
                fontSize = 10.rsp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = RemoteModifier.fillMaxWidth(),
            )
        }
        RemoteBox(modifier = RemoteModifier.width(8.rdp))
        RemoteText(
            text = track.duration.getTimeString().rs,
            color = colors.textSecondary.rc,
            fontSize = 10.rsp,
            maxLines = 1,
        )
    }
}

@Composable
@RemoteComposable
private fun RemoteIconButton(
    modifier: RemoteModifier = RemoteModifier,
    icon: ImageBitmap,
    action: PlayerWidgetAction,
    size: Int,
    enabled: Boolean = true,
) {
    RemoteBox(
        modifier = modifier
            .size((size + 16).rdp)
            .clip(RemoteRoundedCornerShape(50))
            .widgetClickable(action.actionId, enabled),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteImage(
            remoteBitmap = RemoteImageBitmap(icon),
            contentDescription = null,
            modifier = RemoteModifier.size(size.rdp),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Makes the element report [actionId] back to [PlayerSheetWidgetProvider] when tapped. The provider
 * registers the matching [android.app.PendingIntent] under the same id on the published
 * [android.widget.RemoteViews].
 */
private fun RemoteModifier.widgetClickable(
    actionId: Int,
    enabled: Boolean = true,
): RemoteModifier =
    clickable(action = WidgetHostAction(actionId), enabled = enabled)
