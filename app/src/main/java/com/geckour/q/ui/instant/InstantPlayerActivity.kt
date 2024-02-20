package com.geckour.q.ui.instant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.DoubleTrackSlider
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getIsInNightMode
import com.geckour.q.util.getTimeString
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class InstantPlayerActivity : AppCompatActivity() {

    private val viewModel by viewModel<InstantPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        viewModel.initializeMediaController(this, uri)

        setContent {
            val isInNightMode by LocalContext.current.getIsInNightMode()
                .collectAsState(initial = isSystemInDarkTheme())

            QTheme(darkTheme = isInNightMode) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = colorResource(id = R.color.colorBackgroundInstantPlayer)
                        )
                        .clickable(onClick = ::finish),
                ) {
                    Controller(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        viewModel.releaseMediaController()

        super.onDestroy()
    }
}

@Composable
private fun Controller(viewModel: InstantPlayerViewModel) {
    val duration by viewModel.duration.collectAsState()
    val current by viewModel.current.collectAsState()
    val currentBuffer by viewModel.currentBuffer.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Card(
        shape = RoundedCornerShape(size = 8.dp),
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .clickable(enabled = false, onClick = {})
            .wrapContentSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App icon",
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterStart)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.REWIND
                                        )
                                    },
                                    onTap = {
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.PREV
                                        )
                                    },
                                    onPress = {
                                        awaitRelease()
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.UNDEFINED
                                        )
                                    }
                                )
                            }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .clickable {
                                viewModel.onPlaybackButtonPressed(
                                    if (isPlaying) PlaybackButton.PAUSE
                                    else PlaybackButton.PLAY
                                )
                            }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "FastForward",
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.FF
                                        )
                                    },
                                    onTap = {
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.NEXT
                                        )
                                    },
                                    onPress = {
                                        awaitRelease()
                                        viewModel.onPlaybackButtonPressed(
                                            PlaybackButton.UNDEFINED
                                        )
                                    }
                                )
                            }
                            .padding(8.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = current.getTimeString(),
                    color = QTheme.colors.colorTextPrimary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                DoubleTrackSlider(
                    modifier = Modifier.weight(1f),
                    primaryProgressFraction = if (duration > 0) current.toFloat() / duration else 0f,
                    secondaryProgressFraction = if (duration > 0) currentBuffer.toFloat() / duration else 0f,
                    onSeekEnded = viewModel::onSeekBarProgressChanged
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = duration.getTimeString(),
                    color = QTheme.colors.colorTextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}