package com.geckour.q.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getShouldShowCurrentRemain
import com.geckour.q.util.getTimeString
import com.geckour.q.util.setShouldShowCurrentRemain
import kotlinx.coroutines.launch

@Composable
fun Controller(
    currentTrack: DomainTrack?,
    progress: Long,
    queueTotalDuration: Long,
    queueRemainingDuration: Long,
    playbackInfo: Pair<Boolean, Int>,
    repeatMode: Int,
    isLoading: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    resetPlaybackButton: () -> Unit,
    onNewProgress: (newProgress: Long) -> Unit,
    rotateRepeatMode: () -> Unit,
    shuffleQueue: () -> Unit,
    moveToCurrentIndex: () -> Unit,
    clearQueue: () -> Unit,
) {
    Column {
        Column(modifier = Modifier.height(144.dp)) {
            Row {
                AsyncImage(
                    model = currentTrack?.artworkUriString,
                    contentDescription = null,
                    contentScale = ContentScale.Inside,
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(100.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = currentTrack?.title.orEmpty(),
                                fontSize = 12.sp,
                                color = QTheme.colors.colorTextPrimary,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack?.let { "${it.artist.title} - ${it.album.title}" }
                                    .orEmpty(),
                                fontSize = 10.sp,
                                color = QTheme.colors.colorTextPrimary,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier
                                .width(84.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dropbox),
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(if (currentTrack?.dropboxPath != null) 1f else 0f)
                            )
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
                            Icon(
                                painter = painterResource(id = R.drawable.ic_empty),
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(if (isLoading) 1f else 0f)
                                    .graphicsLayer {
                                        rotationZ = degree
                                    }
                            )
                        }
                    }
                    Row {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rotation = remember { Animatable(360f) }
                            LaunchedEffect(playbackInfo.second) {
                                if (playbackInfo.second == Player.STATE_BUFFERING) {
                                    rotation.animateTo(
                                        0f,
                                        animationSpec = infiniteRepeatable(tween(1000)),
                                    )
                                }
                            }
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
                            )
                            IconButton(
                                onClick = {
                                    onTogglePlayPause()
                                    resetPlaybackButton()
                                },
                                modifier = Modifier.graphicsLayer {
                                    rotationZ =
                                        if (playbackInfo.second == Player.STATE_BUFFERING) rotation.value else 0f
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
                            )
                        }
                        Row(
                            modifier = Modifier.width(84.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = rotateRepeatMode) {
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
                                    tint = QTheme.colors.colorButtonNormal
                                )
                            }
                            IconButton(onClick = shuffleQueue) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shuffle),
                                    contentDescription = null,
                                    tint = QTheme.colors.colorButtonNormal
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val shouldShowCurrentRemain by context.getShouldShowCurrentRemain()
                    .collectAsState(initial = false)
                Text(
                    text = currentTrack?.let { progress.getTimeString() }.orEmpty(),
                    fontSize = 10.sp,
                    color = QTheme.colors.colorTextPrimary,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = currentTrack?.let { progress.toDouble() / it.duration }?.toFloat()
                        ?: 0f,
                    onValueChange = { newProgress ->
                        currentTrack?.let { onNewProgress((newProgress * it.duration).toLong()) }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = QTheme.colors.colorButtonNormal,
                        activeTrackColor = QTheme.colors.colorButtonNormal,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Text(
                    text = currentTrack?.let {
                        if (shouldShowCurrentRemain) "-${(currentTrack.duration - progress).getTimeString()}"
                        else currentTrack.duration.getTimeString()
                    }.orEmpty(),
                    fontSize = 10.sp,
                    color = QTheme.colors.colorTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable {
                            coroutineScope.launch {
                                context.setShouldShowCurrentRemain(shouldShowCurrentRemain.not())
                            }
                        }
                )
            }
            Text(
                text = currentTrack?.let {
                    stringResource(
                        id = R.string.track_file_info,
                        it.codec,
                        it.bitrate,
                        it.sampleRate
                    )
                }.orEmpty(),
                fontSize = 10.sp,
                color = QTheme.colors.colorTextPrimary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 2.dp, end = 16.dp)
            )
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
                onClick = clearQueue,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_remove),
                    contentDescription = null,
                    tint = QTheme.colors.colorButtonNormal
                )
            }
        }
    }
}