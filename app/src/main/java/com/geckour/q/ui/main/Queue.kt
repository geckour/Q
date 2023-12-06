package com.geckour.q.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.moved
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable


@Composable
fun Queue(
    domainTracks: List<DomainTrack>,
    forceScrollToCurrent: Long,
    showLyric: Boolean,
    currentPlaybackPosition: Long,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (domainTrack: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (domainTrack: DomainTrack) -> Unit
) {
    var items by remember { mutableStateOf(domainTracks) }
    val lyric by DB.getInstance(LocalContext.current)
        .lyricDao()
        .getLyricByTrackId(domainTracks.firstOrNull { it.nowPlaying }?.id ?: -1)
        .collectAsState(initial = null)
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to -> items = items.moved(from.index, to.index) },
        onDragEnd = { from, to -> onQueueMove(from, to) }
    )
    var lyricListHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(domainTracks) {
        items = domainTracks
    }
    LaunchedEffect(forceScrollToCurrent) {
        reorderableState.listState.animateScrollToItem(domainTracks.indexOfFirst { it.nowPlaying }
            .coerceAtLeast(0))
    }

    if (showLyric) {
        if (lyric?.lines.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "歌詞が設定されていないか読み込めませんでした",
                    fontSize = 20.sp,
                    color = QTheme.colors.colorTextPrimary
                )
            }
        } else {
            var currentIndex by remember { mutableIntStateOf(-1) }
            val listState = rememberLazyListState()
            val density = LocalDensity.current

            LaunchedEffect(currentPlaybackPosition) {
                currentIndex = checkNotNull(lyric?.lines.orEmpty()).indexOfLast {
                    it.timing < currentPlaybackPosition
                }
            }
            LaunchedEffect(currentIndex, lyricListHeight) {
                if (currentIndex > -1) {
                    listState.animateScrollToItem(
                        currentIndex,
                        -lyricListHeight / 2 + with(density) { 22.dp.roundToPx() }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .onGloballyPositioned { lyricListHeight = it.size.height }
            ) {
                itemsIndexed(checkNotNull(lyric?.lines.orEmpty())) { index, lyricLine ->
                    LrcItem(lyric = lyricLine.sentence, index == currentIndex)
                }
            }
        }
    } else {
        LazyColumn(
            state = reorderableState.listState,
            modifier = Modifier
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState)
                .fillMaxHeight()
        ) {
            itemsIndexed(items, { _, item -> item.key }) { index, domainTrack ->
                ReorderableItem(
                    reorderableState = reorderableState,
                    key = domainTrack.key
                ) { isDragging ->
                    QueueItem(
                        domainTrack = domainTrack,
                        index = index,
                        isDragging = isDragging,
                        onChangeRequestedTrackInQueue = onChangeRequestedTrackInQueue,
                        onRemoveTrackFromQueue = onRemoveTrackFromQueue
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun QueueItem(
    modifier: Modifier = Modifier,
    domainTrack: DomainTrack,
    index: Int,
    isDragging: Boolean,
    onChangeRequestedTrackInQueue: (domainTrack: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (domainTrack: DomainTrack) -> Unit
) {
    val elevation by animateDpAsState(targetValue = if (isDragging) 16.dp else 0.dp, label = "")

    Surface(
        elevation = elevation,
        color = if (domainTrack.nowPlaying) QTheme.colors.colorWeekAccent else QTheme.colors.colorBackgroundBottomSheet,
        onClick = { onChangeRequestedTrackInQueue(domainTrack) },
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (domainTrack.nowPlaying) {
                Image(
                    painter = painterResource(id = R.drawable.ic_spectrum),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color = QTheme.colors.colorAccent),
                    modifier = Modifier
                        .size(16.dp)
                        .padding(2.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        modifier = Modifier.size(48.dp),
                        model = domainTrack.artworkUriString ?: R.drawable.ic_empty,
                        contentDescription = null
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .width(IntrinsicSize.Max)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = domainTrack.title,
                            color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${domainTrack.artist.title} - ${domainTrack.album.title}",
                            color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { onRemoveTrackFromQueue(domainTrack) },
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_remove),
                            contentDescription = null,
                            tint = QTheme.colors.colorButtonNormal
                        )
                    }
                }
                Row(
                    modifier = Modifier.height(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.width(48.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = QTheme.colors.colorTextPrimary,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "${domainTrack.codec}・${domainTrack.bitrate}kbps・${domainTrack.sampleRate}kHz",
                        color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .width(IntrinsicSize.Max),
                    )
                    Text(
                        text = domainTrack.durationString,
                        color = QTheme.colors.colorTextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LrcItem(lyric: String, focused: Boolean) {
    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            text = lyric,
            fontSize = 20.sp,
            color = if (focused) QTheme.colors.colorButtonNormal else QTheme.colors.colorTextPrimary,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}