package com.geckour.q.ui.widget.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.await
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.service.PlayerService
import com.geckour.q.spotify.isSpotifySourcePath
import com.geckour.q.ui.compose.ColorBackgroundBottomSheet
import com.geckour.q.ui.compose.ColorBackgroundBottomSheetInverse
import com.geckour.q.ui.compose.ColorInactive
import com.geckour.q.ui.compose.ColorInactiveInverse
import com.geckour.q.ui.compose.ColorPrimary
import com.geckour.q.ui.compose.ColorStrong
import com.geckour.q.ui.compose.ColorTextPrimary
import com.geckour.q.ui.compose.ColorTextPrimaryInverse
import com.geckour.q.ui.compose.ColorTextSecondary
import com.geckour.q.ui.compose.ColorTextSecondaryInverse
import com.geckour.q.ui.compose.ColorWeakAccent
import com.geckour.q.ui.compose.ColorWeakAccentInverse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * The controls the widget offers, mirroring the subset of
 * [com.geckour.q.ui.main.PlayerSheet]'s callbacks that make sense on a home screen.
 */
internal enum class PlayerWidgetAction {
    OpenApp,
    RotateRepeatMode,
    Prev,
    TogglePlayPause,
    Next,
    Shuffle;

    /**
     * Id the document reports back on a tap, and under which the provider registers the matching
     * [android.app.PendingIntent]. Ids only have to be unique within a single widget's document,
     * and start at [Int.MIN_VALUE] like `androidx.glance.appwidget`'s do.
     */
    val actionId: Int get() = Int.MIN_VALUE + ordinal
}

/**
 * A single queue entry, reduced to what the widget is able to render.
 */
internal data class PlayerWidgetTrack(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
)

/**
 * Snapshot of the player state the widget renders. This is the widget counterpart of the arguments
 * [com.geckour.q.ui.main.PlayerSheet] receives, flattened into an immutable value so that it can be
 * captured into a RemoteCompose document off the main thread.
 */
internal data class PlayerWidgetState(
    val queue: ImmutableList<PlayerWidgetTrack>,
    val currentIndex: Int,
    val artwork: ImageBitmap?,
    val playing: Boolean,
    val playbackState: Int,
    val repeatMode: Int,
) {

    val currentTrack: PlayerWidgetTrack? get() = queue.getOrNull(currentIndex)

    val hasQueue: Boolean get() = queue.isNotEmpty()

    companion object {

        val Empty = PlayerWidgetState(
            queue = persistentListOf(),
            currentIndex = 0,
            artwork = null,
            playing = false,
            playbackState = Player.STATE_IDLE,
            repeatMode = Player.REPEAT_MODE_OFF,
        )
    }
}

/**
 * What fits in the space the launcher gives the widget.
 *
 * The track info is always drawn; the transport controls need two home screen rows and the queue
 * three. Launchers only report a size in dp, never a row count, so the thresholds sit halfway
 * between the heights a row of the widget is actually given (roughly 96, 203 and 311 dp on a
 * 420dpi phone).
 */
internal data class PlayerWidgetLayout(
    val contentPaddingDp: Int,
    val artworkSizeDp: Int,
    val heightStep: Int,
) {

    companion object {

        private const val STEP_1_HEIGHT_DP = 95
        private const val STEP_2_HEIGHT_DP = 190
        private const val MIN_ARTWORK_SIZE_DP = 36
        private const val MAX_ARTWORK_SIZE_DP = 64
        private const val ARTWORK_SCALE = 0.8f
        private const val CONTENT_PADDING_DP = 12

        fun of(heightDp: Int): PlayerWidgetLayout = PlayerWidgetLayout(
            contentPaddingDp = CONTENT_PADDING_DP,
            artworkSizeDp =
                (heightDp.coerceAtLeast(MIN_ARTWORK_SIZE_DP) * ARTWORK_SCALE)
                    .roundToInt()
                    .coerceAtMost(MAX_ARTWORK_SIZE_DP),
            heightStep = when (heightDp) {
                in 0 until STEP_1_HEIGHT_DP -> 0
                in STEP_1_HEIGHT_DP until STEP_2_HEIGHT_DP -> 1
                else -> 2
            }
        )
    }
}

/**
 * The subset of [com.geckour.q.ui.compose.QColors] the widget needs. The widget is rendered outside
 * of the app process, so the palette is resolved eagerly instead of through a composition local.
 */
internal data class PlayerWidgetColors(
    val background: Color,
    val nowPlaying: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val button: Color,
    val inactive: Color,
) {

    companion object {

        fun of(isInNightMode: Boolean): PlayerWidgetColors {
            return if (isInNightMode) {
                PlayerWidgetColors(
                    background = ColorBackgroundBottomSheetInverse,
                    nowPlaying = ColorWeakAccentInverse,
                    textPrimary = ColorTextPrimaryInverse,
                    textSecondary = ColorTextSecondaryInverse,
                    button = ColorStrong,
                    inactive = ColorInactiveInverse,
                )
            } else {
                PlayerWidgetColors(
                    background = ColorBackgroundBottomSheet,
                    nowPlaying = ColorWeakAccent,
                    textPrimary = ColorTextPrimary,
                    textSecondary = ColorTextSecondary,
                    button = ColorPrimary,
                    inactive = ColorInactive,
                )
            }
        }
    }
}

/**
 * RemoteCompose documents cannot reference app resources, so every icon is rasterised into the
 * document at capture time.
 */
internal data class PlayerWidgetIcons(
    val prev: ImageBitmap,
    val play: ImageBitmap,
    val pause: ImageBitmap,
    val next: ImageBitmap,
    val repeatOff: ImageBitmap,
    val repeatAll: ImageBitmap,
    val repeatOne: ImageBitmap,
    val shuffle: ImageBitmap,
) {

    fun repeat(repeatMode: Int): ImageBitmap = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> repeatAll
        Player.REPEAT_MODE_ONE -> repeatOne
        else -> repeatOff
    }

    companion object {

        /** Covers the largest icon the sheet draws, in dp. */
        private const val ICON_SIZE_DP = 32

        fun of(context: Context, tint: Color): PlayerWidgetIcons {
            val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()

            return PlayerWidgetIcons(
                prev = context.tintedBitmap(R.drawable.ic_backward, tint, sizePx),
                play = context.tintedBitmap(R.drawable.ic_play, tint, sizePx),
                pause = context.tintedBitmap(R.drawable.ic_pause, tint, sizePx),
                next = context.tintedBitmap(R.drawable.ic_forward, tint, sizePx),
                repeatOff = context.tintedBitmap(R.drawable.ic_repeat_off, tint, sizePx),
                repeatAll = context.tintedBitmap(R.drawable.ic_repeat, tint, sizePx),
                repeatOne = context.tintedBitmap(R.drawable.ic_repeat_one, tint, sizePx),
                shuffle = context.tintedBitmap(R.drawable.ic_shuffle, tint, sizePx),
            )
        }

        private fun Context.tintedBitmap(
            @DrawableRes resId: Int,
            tint: Color,
            sizePx: Int,
        ): ImageBitmap {
            val drawable = requireNotNull(AppCompatResources.getDrawable(this, resId)).mutate()
            DrawableCompat.setTint(drawable, tint.toArgb())

            return drawable.toBitmap(sizePx, sizePx).asImageBitmap()
        }
    }
}

/**
 * Reads the current player state through a short lived [MediaController] bound to [PlayerService],
 * completing it with the track metadata held in the local database.
 *
 * The controller has to be created and read on the main thread; the database and artwork lookups are
 * pushed to the IO dispatcher.
 */
internal suspend fun loadPlayerWidgetState(
    context: Context,
    artworkSizePx: Int,
): PlayerWidgetState {
    val sessionToken = SessionToken(
        context,
        ComponentName(context, PlayerService::class.java)
    )
    val playback = withContext(Dispatchers.Main) {
        val controller = runCatching {
            MediaController.Builder(context, sessionToken).buildAsync().await()
        }.onFailure { Timber.w(it, "The widget could not connect to the player session") }
            .getOrNull() ?: return@withContext null

        try {
            PlaybackSnapshot(
                sourcePaths = List(controller.mediaItemCount) {
                    controller.getMediaItemAt(it).mediaId
                }.filter { it.isNotBlank() },
                currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0),
                playing = controller.playWhenReady,
                playbackState = controller.playbackState,
                repeatMode = controller.repeatMode,
            )
        } finally {
            controller.release()
        }
    } ?: return PlayerWidgetState.Empty

    if (playback.sourcePaths.isEmpty()) return PlayerWidgetState.Empty

    return withContext(Dispatchers.IO) {
        val db = DB.getInstance(context)
        val (spotifyUris, localSourcePaths) =
            playback.sourcePaths.partition { it.isSpotifySourcePath }
        val joinedTracks = runCatching {
            db.trackDao().getAllBySourcePaths(localSourcePaths)
        }.getOrDefault(emptyList()).associateBy { it.track.sourcePath }
        val spotifyTracks = runCatching {
            db.spotifyTrackDao().getAllByUris(spotifyUris)
        }.getOrDefault(emptyList()).associateBy { it.uri }
        // Kept index aligned with the session's queue: tapping a row asks the session to play
        // that very index, so a track the database no longer knows about must not shift the ones
        // after it.
        val queue = playback.sourcePaths
            .map { sourcePath ->
                spotifyTracks[sourcePath]?.toPlayerWidgetTrack()
                    ?: joinedTracks[sourcePath].toPlayerWidgetTrack(sourcePath = sourcePath)
            }
            .toImmutableList()
        val currentSourcePath = playback.sourcePaths.getOrNull(playback.currentIndex)
        val currentJoinedTrack = currentSourcePath?.let { joinedTracks[it] }
        val artwork = (currentSourcePath?.let { spotifyTracks[it]?.artworkUrl }
            ?: currentJoinedTrack?.track?.artworkUriString
            ?: currentJoinedTrack?.album?.artworkUriString)
            ?.let { context.loadArtwork(it, artworkSizePx) }

        PlayerWidgetState(
            queue = queue,
            currentIndex = playback.currentIndex,
            artwork = artwork,
            playing = playback.playing,
            playbackState = playback.playbackState,
            repeatMode = playback.repeatMode,
        )
    }
}

private data class PlaybackSnapshot(
    val sourcePaths: List<String>,
    val currentIndex: Int,
    val playing: Boolean,
    val playbackState: Int,
    val repeatMode: Int,
)

/** Falls back to the file name so that a track missing from the database still holds its slot. */
private fun JoinedTrack?.toPlayerWidgetTrack(sourcePath: String): PlayerWidgetTrack =
    if (this == null) {
        PlayerWidgetTrack(
            title = sourcePath.toUri().lastPathSegment.orEmpty(),
            artist = "",
            album = "",
            duration = 0L,
        )
    } else {
        PlayerWidgetTrack(
            title = track.title,
            artist = artist.title,
            album = album.title,
            duration = track.duration,
        )
    }

private fun SpotifyTrack.toPlayerWidgetTrack(): PlayerWidgetTrack =
    PlayerWidgetTrack(
        title = title,
        artist = artistName,
        album = albumName,
        duration = duration,
    )

private fun Context.loadArtwork(uriString: String, sizePx: Int): ImageBitmap? = runCatching {
    imageLoader.executeBlocking(
        ImageRequest.Builder(this)
            .data(uriString)
            .size(sizePx)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
    ).image?.toBitmap()?.asImageBitmap()
}.getOrNull()
