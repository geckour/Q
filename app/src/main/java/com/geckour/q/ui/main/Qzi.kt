package com.geckour.q.ui.main

import android.icu.util.Calendar
import android.icu.util.TimeZone
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.ui.compose.QTheme
import kotlin.random.Random

@Composable
fun Qzi(onClick: (item: JoinedTrack) -> Unit) {
    val calendar = Calendar.getInstance(TimeZone.getDefault())
    val random = Random(calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR))
    val db = DB.getInstance(LocalContext.current)
    val tracks by db.trackDao().getAllAsync().collectAsState(initial = emptyList())
    val trackCount by db.trackDao().countAsync().collectAsState(initial = 0)
    val track = if (trackCount > 0) tracks.getOrNull(random.nextInt(trackCount)) else null

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.easter_egg_title),
                fontSize = 20.sp,
                color = QTheme.colors.colorTextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            val contentModifier = remember { Modifier.padding(horizontal = 20.dp) }
            Column(
                modifier = (if (track != null) contentModifier.clickable(
                    onClick = { onClick(track) },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false)
                ) else contentModifier)
            ) {
                val rotation = remember { Animatable(360f) }
                LaunchedEffect(track) {
                    if (track == null) {
                        rotation.animateTo(
                            0f,
                            animationSpec = infiniteRepeatable(tween(1000)),
                        )
                    }
                }
                AsyncImage(
                    model = track?.track?.artworkUriString ?: R.drawable.ic_empty,
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.CenterHorizontally)
                        .graphicsLayer {
                            rotationZ =
                                if (track == null) rotation.value else 0f
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = track?.track?.title.orEmpty(),
                    fontSize = 20.sp,
                    color = QTheme.colors.colorTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track?.let { "${it.artist.title} - ${it.album.title}" }.orEmpty(),
                    fontSize = 16.sp,
                    color = QTheme.colors.colorTextPrimary
                )
            }
        }
    }
}