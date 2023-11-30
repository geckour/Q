package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.compose.QTheme

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun Controller(
    currentTrack: DomainTrack?,
    progress: Long,
    playbackInfo: Pair<Boolean, Int>,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    resetPlaybackButton: () -> Unit,
    onNewProgress: (newProgress: Long) -> Unit
) {

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
                    .height(100.dp)
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    IconButton(onClick = { /*TODO*/ }) {
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
                }
            }
        }
        Slider(
            value = currentTrack?.let { progress.toDouble() / it.duration }?.toFloat() ?: 0f,
            onValueChange = { newProgress ->
                currentTrack?.let { onNewProgress((newProgress * it.duration).toLong()) }
            },
            colors = SliderDefaults.colors(
                thumbColor = QTheme.colors.colorButtonNormal,
                activeTrackColor = QTheme.colors.colorButtonNormal,
            ),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .weight(1f)
                .fillMaxHeight()
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
            fontSize = 10.sp,
            color = QTheme.colors.colorTextPrimary,
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 2.dp, end = 16.dp)
        )
    }
}