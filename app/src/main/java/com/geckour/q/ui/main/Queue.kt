package com.geckour.q.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString
import com.geckour.q.util.moved
import com.geckour.q.util.removedAt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable


@Composable
fun ColumnScope.Queue(
    domainTracks: ImmutableList<DomainTrack>,
    forceScrollToCurrent: Long,
    showLyric: Boolean,
    currentPlaybackPosition: Long,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (domainTrack: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (domainTrack: DomainTrack) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = DB.getInstance(context)
    var items by remember { mutableStateOf(domainTracks) }
    val lyric by db.lyricDao()
        .getLyricFlowByTrackId(domainTracks.firstOrNull { it.nowPlaying }?.id ?: -1)
        .collectAsState(initial = null)
    val lyricLinesForShowing = lyric.lyricLinesForShowing
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to -> items = items.moved(from.index, to.index).toImmutableList() },
        onDragEnd = { from, to -> onQueueMove(from, to) }
    )
    var lyricListHeight by remember { mutableIntStateOf(0) }

    var isInEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(domainTracks) {
        items = domainTracks
    }
    LaunchedEffect(forceScrollToCurrent) {
        reorderableState.listState.animateScrollToItem(domainTracks.indexOfFirst { it.nowPlaying }
            .coerceAtLeast(0))
    }

    if (showLyric) {
        var currentIndex by remember { mutableIntStateOf(-1) }
        val listState = rememberLazyListState()
        val density = LocalDensity.current

        LaunchedEffect(currentPlaybackPosition) {
            currentIndex =
                if (lyricLinesForShowing.all { it.lyricLine.timing == 0L }) -1
                else lyricLinesForShowing.indexOfLast {
                    it.lyricLine.timing < currentPlaybackPosition
                }
        }
        LaunchedEffect(currentIndex, lyricListHeight) {
            if (isInEditMode.not() && currentIndex > -1) {
                listState.animateScrollToItem(
                    currentIndex + 1,
                    -lyricListHeight / 2 + with(density) { 22.dp.roundToPx() }
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { lyricListHeight = it.height }
        ) {
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { isInEditMode = isInEditMode.not() }) {
                        Text(
                            text = if (isInEditMode) "完了" else "編集",
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                }
            }
            if (isInEditMode) {
                item {
                    NewLyricLineInputBox(
                        onSubmit = { newSentence ->
                            coroutineScope.launch {
                                (lyric ?: domainTracks.firstOrNull { it.nowPlaying }
                                    ?.id
                                    ?.let {
                                        val newLyric =
                                            Lyric(id = 0, trackId = it, lines = emptyList())
                                        val id = db.lyricDao().upsertLyric(newLyric)
                                        newLyric.copy(id = id)
                                    })?.let {
                                    db.lyricDao().upsertLyric(
                                        it.copy(
                                            lines = lyricLinesForShowing.map { it.lyricLine } +
                                                    LyricLine(
                                                        currentPlaybackPosition,
                                                        newSentence
                                                    )
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            } else if (lyric?.lines.isNullOrEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "歌詞が設定されていないか読み込めませんでした",
                            fontSize = 20.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                }
            }
            items(lyricLinesForShowing) { indexedLyricLine ->
                if (isInEditMode) EditableLrcItem(
                    line = indexedLyricLine,
                    currentPlaybackPosition = currentPlaybackPosition,
                    onUpdateLine = { index, newLine ->
                        lyric?.let {
                            coroutineScope.launch {
                                db.lyricDao()
                                    .upsertLyric(
                                        it.copy(
                                            lines = lyricLinesForShowing.toMutableList()
                                                .apply {
                                                    set(
                                                        index,
                                                        IndexedLyricLine(index, newLine)
                                                    )
                                                }
                                                .map { it.lyricLine }
                                        )
                                    )
                            }
                        }
                    },
                    onDeleteLine = { index ->
                        lyric?.let {
                            coroutineScope.launch {
                                db.lyricDao()
                                    .upsertLyric(
                                        it.copy(
                                            lines = lyricLinesForShowing.removedAt(index)
                                                .map { it.lyricLine }
                                        )
                                    )
                            }
                        }
                    })
                else LrcItem(
                    lyric = indexedLyricLine.lyricLine.sentence,
                    indexedLyricLine.index == currentIndex
                )
            }
        }
    } else {
        LazyColumn(
            state = reorderableState.listState,
            modifier = Modifier
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState)
                .weight(1f)
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
                        onRemoveTrackFromQueue = onRemoveTrackFromQueue,
                        onToggleFavorite = onToggleFavorite,
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
    onRemoveTrackFromQueue: (domainTrack: DomainTrack) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
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
            Column(modifier = Modifier.padding(top = 20.dp)) {
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
                        onClick = { onToggleFavorite(domainTrack) },
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (domainTrack.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary
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
                    modifier = Modifier.height(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = QTheme.colors.colorTextPrimary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = "${domainTrack.codec}・${domainTrack.bitrate}kbps・${domainTrack.sampleRate}kHz",
                        color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
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
fun NewLyricLineInputBox(onSubmit: (newLine: String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
            value = text,
            onValueChange = { text = it },
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubmit(text)
                    text = ""
                },
                onGo = {
                    onSubmit(text)
                    text = ""
                },
                onNext = {
                    onSubmit(text)
                    text = ""
                },
                onSend = {
                    onSubmit(text)
                    text = ""
                },
            ),
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = QTheme.colors.colorTextPrimary),
            cursorBrush = SolidColor(QTheme.colors.colorTextSecondary),
            decorationBox = { innerTextField ->
                Column {
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        innerTextField()
                    }
                    Divider(color = QTheme.colors.colorTextSecondary)
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = {
            onSubmit(text)
            text = ""
        }) {
            Text(
                text = "挿入",
                color = QTheme.colors.colorTextPrimary
            )
        }
    }
}

@Composable
fun EditableLrcItem(
    line: IndexedLyricLine,
    currentPlaybackPosition: Long,
    onUpdateLine: (index: Int, newLine: LyricLine) -> Unit,
    onDeleteLine: (index: Int) -> Unit,
) {
    var text by remember { mutableStateOf(line.lyricLine.sentence) }

    LaunchedEffect(line.index, line) {
        text = line.lyricLine.sentence
    }

    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = {
            onUpdateLine(
                line.index,
                line.lyricLine.copy(timing = currentPlaybackPosition)
            )
        }) {
            Text(
                text = line.lyricLine.timing.getTimeString(),
                color = QTheme.colors.colorTextPrimary
            )
        }
        BasicTextField(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .weight(1f)
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
            value = text,
            onValueChange = {
                text = it
                onUpdateLine(
                    line.index,
                    line.lyricLine.copy(sentence = text)
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = QTheme.colors.colorTextPrimary),
            cursorBrush = SolidColor(QTheme.colors.colorTextSecondary),
            decorationBox = { innerTextField ->
                Column {
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        innerTextField()
                    }
                    Divider(color = QTheme.colors.colorTextSecondary)
                }
            }
        )
        IconButton(onClick = { onDeleteLine(line.index) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "削除",
                tint = QTheme.colors.colorButtonNormal
            )
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

data class IndexedLyricLine(
    val index: Int,
    val lyricLine: LyricLine,
)

val Lyric?.lyricLinesForShowing
    get() = this?.lines.orEmpty()
        .let { lines ->
            lines.filter { it.timing == 0L } +
                    lines.filter { it.timing != 0L }.sortedBy { it.timing }
        }
        .mapIndexed { index, lyricLine ->
            IndexedLyricLine(index, lyricLine)
        }