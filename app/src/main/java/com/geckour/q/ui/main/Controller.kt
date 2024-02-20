package com.geckour.q.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.geckour.q.R
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.ui.DoubleTrackSlider
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.getShouldShowCurrentRemain
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isDownloaded
import com.geckour.q.util.nonUpScaleSp
import com.geckour.q.util.setShouldShowCurrentRemain
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Controller(
    sheetProgress: Float,
    currentTrack: UiTrack?,
    progress: Long,
    bufferProgress: Long,
    queueTotalDuration: Long,
    queueRemainingDuration: Long,
    playbackInfo: Pair<Boolean, Int>,
    repeatMode: Int,
    isLoading: Boolean,
    routeInfo: QAudioDeviceInfo?,
    showLyric: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    resetPlaybackButton: () -> Unit,
    onNewProgress: (newProgress: Long) -> Unit,
    rotateRepeatMode: () -> Unit,
    shuffleQueue: (actionType: ShuffleActionType?) -> Unit,
    resetShuffleQueue: () -> Unit,
    moveToCurrentIndex: () -> Unit,
    clearQueue: () -> Unit,
    onTrackSelected: (item: UiTrack) -> Unit,
    cancelLoad: () -> Unit,
    onToggleShowLyrics: () -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val yProgress = sin(sheetProgress * PI.toFloat() / 2)
    var textAreaHeight by remember { mutableIntStateOf(0) }
    Column {
        Box(modifier = Modifier.height(144.dp + 142.dp * yProgress)) {
            AsyncImage(
                model = currentTrack?.artworkUriString?.let {
                    ImageRequest.Builder(LocalContext.current).data(it).size(
                        Size(width = 384, height = 384)
                    ).build()
                } ?: R.drawable.ic_empty,
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier = Modifier
                    .size(84.dp + 42.dp * sheetProgress)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { currentTrack?.let { onTrackSelected(it) } }
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 126.dp * yProgress)
            ) {
                Spacer(
                    modifier = Modifier.width(
                        8.dp +
                                (if (sheetProgress < 0.8f) 84.dp + 42.dp * sheetProgress
                                else 588.dp * (1f - sheetProgress))
                    )
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            textAreaHeight = it.height
                        }
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack?.title.orEmpty(),
                        fontSize = (16 * (1 + 0.2f * sheetProgress)).nonUpScaleSp,
                        lineHeight = (24 * (1 + 0.2f * sheetProgress)).nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            spacing = MarqueeSpacing(24.dp),
                            iterations = Int.MAX_VALUE,
                            velocity = 45.dp,
                            delayMillis = 600
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack?.let { "${it.artist.title} - ${it.album.title}" }
                            .orEmpty(),
                        fontSize = (14 * (1 + 0.2f * sheetProgress)).nonUpScaleSp,
                        lineHeight = (21 * (1 + 0.2f * sheetProgress)).nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            spacing = MarqueeSpacing(24.dp),
                            iterations = Int.MAX_VALUE,
                            velocity = 45.dp
                        )
                    )
                }
                Spacer(
                    modifier = Modifier.width(
                        8.dp + 120.dp * (1 - sheetProgress)
                    )
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            y = with(LocalDensity.current) { textAreaHeight.toDp() } / 2 - 20.dp + 4.dp
                        )
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val degree by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                1000,
                                easing = LinearEasing
                            )
                        ),
                        label = ""
                    )
                    val queueStateIndicatorAlpha by animateFloatAsState(
                        targetValue = if (isLoading) 1f else 0f,
                        animationSpec = tween(200),
                        label = ""
                    )
                    val dropboxIndicatorAlpha by animateFloatAsState(
                        targetValue = if (currentTrack?.dropboxPath != null) 1f else 0f,
                        animationSpec = tween(200),
                        label = ""
                    )
                    currentTrack?.isFavorite?.let {
                        Icon(
                            imageVector = if (it) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary,
                            modifier = Modifier
                                .clickable(
                                    onClick = { onToggleFavorite(currentTrack) },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = false)
                                )
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                    if (currentTrack?.isDownloaded == true) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadForOffline,
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                                .alpha(dropboxIndicatorAlpha)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dropbox),
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                                .alpha(dropboxIndicatorAlpha)
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_empty),
                        contentDescription = null,
                        tint = QTheme.colors.colorTextPrimary,
                        modifier = Modifier
                            .clickable(
                                onClick = cancelLoad,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            )
                            .padding(8.dp)
                            .size(24.dp)
                            .alpha(queueStateIndicatorAlpha)
                            .graphicsLayer {
                                rotationZ = degree
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = with(LocalDensity.current) { textAreaHeight.toDp() })
                ) {
                    Icon(
                        painter = painterResource(
                            id = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> R.drawable.ic_repeat_off
                                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                                else -> throw IllegalStateException()
                            }
                        ),
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .clickable(
                                onClick = rotateRepeatMode,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            )
                            .padding(8.dp)
                            .size(24.dp)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shuffle),
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { shuffleQueue(null) },
                                onLongClick = resetShuffleQueue,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            )
                            .padding(8.dp)
                            .size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 84.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val degree by infiniteTransition.animateFloat(
                        initialValue = 360f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000)
                        ),
                        label = ""
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_backward),
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onRewind() },
                                    onTap = { onPrev() },
                                    onPress = {
                                        awaitRelease()
                                        resetPlaybackButton()
                                    }
                                )
                            }
                            .padding(8.dp)
                            .size(24.dp)
                    )
                    IconButton(
                        onClick = {
                            onTogglePlayPause()
                            resetPlaybackButton()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .graphicsLayer {
                                rotationZ =
                                    if (playbackInfo.second == Player.STATE_BUFFERING) degree else 0f
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (playbackInfo.first && playbackInfo.second == Player.STATE_READY) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = null,
                            tint = QTheme.colors.colorButtonNormal
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward),
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { onFastForward() },
                                    onTap = { onNext() },
                                    onPress = {
                                        awaitRelease()
                                        resetPlaybackButton()
                                    }
                                )
                            }
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    val shouldShowCurrentRemain by context.getShouldShowCurrentRemain()
                        .collectAsState(initial = false)
                    Text(
                        text = currentTrack?.let { progress.getTimeString() }.orEmpty(),
                        fontSize = 12.nonUpScaleSp,
                        lineHeight = 18.nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(60.dp)
                    )
                    DoubleTrackSlider(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        key = currentTrack,
                        primaryProgressFraction = currentTrack?.duration
                            ?.let { progress.toFloat() / it }
                            ?: 0f,
                        secondaryProgressFraction = currentTrack?.duration?.let { bufferProgress.toFloat() / it },
                        thumbColor = QTheme.colors.colorButtonNormal,
                        primaryTrackColor = QTheme.colors.colorButtonNormal,
                        onSeek = { newProgressFraction ->
                            currentTrack?.let {
                                onNewProgress((newProgressFraction * it.duration).toLong())
                            }
                        },
                    )
                    Text(
                        text = currentTrack?.let {
                            if (shouldShowCurrentRemain) "-${(currentTrack.duration - progress).getTimeString()}"
                            else currentTrack.duration.getTimeString()
                        }.orEmpty(),
                        fontSize = 12.nonUpScaleSp,
                        lineHeight = 18.nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                coroutineScope.launch {
                                    context.setShouldShowCurrentRemain(shouldShowCurrentRemain.not())
                                }
                            }
                            .width(60.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.padding(bottom = 2.dp, start = 16.dp, end = 16.dp)) {
                    Text(
                        text = routeInfo?.audioDeviceName
                            ?: stringResource(id = R.string.default_device_name),
                        fontSize = 10.nonUpScaleSp,
                        lineHeight = 15.nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = currentTrack?.let {
                            stringResource(
                                id = R.string.track_file_info,
                                it.codec,
                                it.bitrate,
                                it.sampleRate
                            )
                        }.orEmpty(),
                        fontSize = 10.nonUpScaleSp,
                        lineHeight = 15.nonUpScaleSp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f)
                    .width(IntrinsicSize.Max)
                    .height(IntrinsicSize.Min)
            ) {
                IconButton(
                    onClick = moveToCurrentIndex,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_current),
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        id = R.string.bottom_sheet_time_total,
                        queueTotalDuration.getTimeString()
                    ),
                    fontSize = 12.sp,
                    color = QTheme.colors.colorTextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        id = R.string.bottom_sheet_time_remain,
                        queueRemainingDuration.getTimeString()
                    ),
                    fontSize = 12.sp,
                    color = QTheme.colors.colorTextPrimary
                )
            }
            IconButton(
                onClick = onToggleShowLyrics,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = if (showLyric) QTheme.colors.colorButtonNormal else QTheme.colors.colorInactive
                )
            }
            IconButton(
                onClick = clearQueue,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_remove),
                    contentDescription = null,
                    tint = QTheme.colors.colorButtonNormal
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}