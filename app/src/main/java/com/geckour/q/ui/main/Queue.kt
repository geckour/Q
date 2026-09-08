package com.geckour.q.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString
import com.geckour.q.util.moved
import com.geckour.q.util.nonUpScaleSp
import com.geckour.q.util.removedAt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


@Composable
fun Queue(
    endItemMargin: Dp = 0.dp,
    uiTracks: ImmutableList<UiTrack>,
    isPlaying: Boolean,
    showLyric: Boolean,
    isInLyricEditMode: Boolean,
    currentPlaybackPosition: Long,
    forceScrollToCurrent: Long,
    isLyricScrolledByUser: MutableState<Boolean>,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onTrackSelected: (track: UiTrack) -> Unit,
    onNewProgress: (newProgress: Long) -> Unit,
    onChangeIndexRequested: (index: Int) -> Unit,
    onRemoveTrackFromQueue: (index: Int) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val currentContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = DB.getInstance(currentContext)
    val currentDensity = LocalDensity.current
    val nowPlayingTrackId = uiTracks.firstOrNull { it.nowPlaying }?.id ?: -1
    val lyric by remember(nowPlayingTrackId) {
        db.lyricDao().getLyricFlowByTrackId(nowPlayingTrackId)
    }.collectAsState(initial = null)
    var contentHeight by remember { mutableIntStateOf(0) }

    if (showLyric) {
        val lazyListState = rememberLazyListState()
        val lyricLinesForShowing = lyric.lyricLinesForShowing
        var currentIndex by remember { mutableIntStateOf(-1) }
        val isSyncedLyric = lyricLinesForShowing.size > 1 &&
                lyricLinesForShowing.any { it.lyricLine.timing != 0L }
        val navigationBottomInset = WindowInsets.navigationBars.getBottom(currentDensity)
        var currentIndexLyricHeight by remember { mutableIntStateOf(0) }
        val scrollToCurrent: suspend () -> Unit = {
            if (isInLyricEditMode.not() && currentIndex > -1) {
                lazyListState.animateScrollToItem(
                    currentIndex,
                    -(contentHeight - currentIndexLyricHeight - navigationBottomInset) / 2
                )
            }
        }
        val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()

        LaunchedEffect(currentPlaybackPosition) {
            currentIndex =
                if (lyricLinesForShowing.all { it.lyricLine.timing == 0L }) -1
                else lyricLinesForShowing.indexOfLast {
                    it.lyricLine.timing <= currentPlaybackPosition
                }
        }
        LaunchedEffect(currentIndex) {
            if (isLyricScrolledByUser.value.not()) {
                scrollToCurrent()
            }
        }
        LaunchedEffect(forceScrollToCurrent) {
            isLyricScrolledByUser.value = false
            scrollToCurrent()
        }
        LaunchedEffect(lyric?.id) {
            if (isInLyricEditMode.not() && currentIndex < 0) {
                lazyListState.scrollToItem(0)
            }
        }
        LaunchedEffect(isDragged) {
            isLyricScrolledByUser.value = true
        }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { contentHeight = it.height }
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                ) {
                    if (lyric?.lines.isNullOrEmpty()) {
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
                        if (isInLyricEditMode) {
                            EditableLrcItem(
                                line = indexedLyricLine,
                                currentPlaybackPosition = currentPlaybackPosition,
                                onNewLine = { _, _ -> },
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
                                },
                            )
                        } else {
                            LrcItem(
                                lyric = indexedLyricLine.lyricLine.sentence,
                                focused = indexedLyricLine.index == currentIndex,
                                onClick = if (isSyncedLyric) {
                                    { onNewProgress(indexedLyricLine.lyricLine.timing) }
                                } else null,
                                modifier = Modifier.onSizeChanged {
                                    if (indexedLyricLine.index == currentIndex) {
                                        currentIndexLyricHeight = it.height
                                    }
                                }
                            )
                        }
                    }
                    if (isInLyricEditMode.not()) {
                        item {
                            Spacer(modifier = Modifier.height(36.dp + endItemMargin))
                        }
                    }
                }
                if (isInLyricEditMode) {
                    EditableLrcItem(
                        line = null,
                        currentPlaybackPosition = currentPlaybackPosition,
                        onNewLine = { timing, sentence ->
                            lyric?.let {
                                coroutineScope.launch {
                                    db.lyricDao()
                                        .upsertLyric(
                                            it.copy(
                                                lines = it.lines.toMutableList().apply {
                                                    add(LyricLine(timing, sentence))
                                                }
                                            )
                                        )
                                }
                            }
                        },
                        onUpdateLine = { _, _ -> },
                        onDeleteLine = { _ -> },
                    )
                    Spacer(modifier = Modifier.height(endItemMargin))
                }
            }
    } else {
        var items by remember { mutableStateOf(uiTracks) }
        val lazyListState = rememberLazyListState()
        var from by remember { mutableIntStateOf(-1) }
        var to by remember { mutableIntStateOf(-1) }
        val reorderableState = rememberReorderableLazyListState(lazyListState) { f, t ->
            items = items.moved(f.index, t.index).toImmutableList()
            from = f.index
            to = t.index
        }
        val lottieComposition by rememberLottieComposition(
            spec = LottieCompositionSpec.RawRes(resId = R.raw.emoji_u1f425_anim)
        )

        LaunchedEffect(uiTracks) {
            items = uiTracks
        }

        LaunchedEffect(forceScrollToCurrent) {
            lazyListState.animateScrollToItem(
                uiTracks.indexOfFirst { it.nowPlaying }.coerceAtLeast(0),
                -contentHeight / 2 + with(currentDensity) { 44.dp.roundToPx() }
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { contentHeight = it.height }
        ) {
            itemsIndexed(items, { _, item -> item.key }) { index, domainTrack ->
                ReorderableItem(
                    state = reorderableState,
                    key = domainTrack.key,
                ) { isDragging ->
                    QueueItem(
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStopped = {
                                onQueueMove(from, to)
                            }
                        ),
                        isPlaying = isPlaying,
                        uiTrack = domainTrack,
                        index = index,
                        isDragging = isDragging,
                        lottieComposition = lottieComposition,
                        onTrackSelected = onTrackSelected,
                        onChangeIndexRequested = onChangeIndexRequested,
                        onRemoveTrackFromQueue = onRemoveTrackFromQueue,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(endItemMargin))
            }
        }
    }
}

@Composable
fun QueueItem(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    uiTrack: UiTrack,
    index: Int,
    isDragging: Boolean,
    lottieComposition: LottieComposition?,
    onTrackSelected: (track: UiTrack) -> Unit,
    onChangeIndexRequested: (index: Int) -> Unit,
    onRemoveTrackFromQueue: (index: Int) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val elevation by animateDpAsState(targetValue = if (isDragging) 16.dp else 0.dp, label = "")

    Surface(
        shadowElevation = elevation,
        color = if (uiTrack.nowPlaying) QTheme.colors.colorWeekAccent else QTheme.colors.colorBackgroundBottomSheet,
        onClick = { onChangeIndexRequested(index) },
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiTrack.nowPlaying) {
                if (isPlaying) {
                    LottieAnimation(
                        composition = lottieComposition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(4.dp)
                    )
                } else {
                    AsyncImage(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(4.dp),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(R.raw.emoji_u1f425)
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = "This track is selected"
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onTrackSelected(uiTrack) },
                        model = uiTrack.artworkUriString ?: R.drawable.ic_empty,
                        contentDescription = null
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .width(IntrinsicSize.Max)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = uiTrack.title,
                            color = if (uiTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 24.nonUpScaleSp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiTrack.artist.title} - ${uiTrack.album.title}",
                            color = if (uiTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.nonUpScaleSp
                        )
                    }
                    Icon(
                        imageVector = if (uiTrack.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = QTheme.colors.colorTextPrimary,
                        modifier = Modifier
                            .clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onToggleFavorite(uiTrack) }
                            .padding(8.dp)
                            .size(20.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.RemoveCircleOutline,
                        contentDescription = null,
                        tint = QTheme.colors.colorButtonNormal,
                        modifier = Modifier
                            .clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onRemoveTrackFromQueue(index) }
                            .padding(8.dp)
                            .size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Row(
                    modifier = Modifier.height(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = QTheme.colors.colorTextPrimary,
                        fontSize = 10.nonUpScaleSp,
                        lineHeight = 25.nonUpScaleSp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = "${uiTrack.codec}・${uiTrack.bitrate}kbps・${uiTrack.sampleRate}kHz",
                        color = if (uiTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                        fontSize = 10.nonUpScaleSp,
                        lineHeight = 25.nonUpScaleSp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = uiTrack.durationString,
                        color = QTheme.colors.colorTextPrimary,
                        fontSize = 12.nonUpScaleSp,
                        lineHeight = 18.nonUpScaleSp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditableLrcItem(
    line: IndexedLyricLine?,
    currentPlaybackPosition: Long,
    onNewLine: (timing: Long, sentence: String) -> Unit,
    onUpdateLine: (index: Int, newLine: LyricLine) -> Unit,
    onDeleteLine: (index: Int) -> Unit,
) {
    var text by remember { mutableStateOf(line?.lyricLine?.sentence ?: "") }

    LaunchedEffect(line?.index, line) {
        text = line?.lyricLine?.sentence ?: ""
    }

    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = {
                if (line != null) {
                    onUpdateLine(
                        line.index,
                        line.lyricLine.copy(timing = currentPlaybackPosition)
                    )
                }
            },
            enabled = line != null,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = (line?.lyricLine?.timing
                    ?: currentPlaybackPosition).getTimeString(withMillis = true),
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
                if (line != null) {
                    onUpdateLine(
                        line.index,
                        line.lyricLine.copy(sentence = text)
                    )
                }
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = QTheme.colors.colorTextPrimary),
            cursorBrush = SolidColor(QTheme.colors.colorTextSecondary),
            decorationBox = { innerTextField ->
                Column {
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        innerTextField()
                    }
                    HorizontalDivider(color = QTheme.colors.colorTextSecondary)
                }
            }
        )
        IconButton(
            onClick = {
                if (line == null) {
                    onNewLine(currentPlaybackPosition, text)
                } else {
                    onDeleteLine(line.index)
                }
            }
        ) {
            Icon(
                imageVector = if (line == null) Icons.Default.Add else Icons.Default.Delete,
                contentDescription = "削除",
                tint = QTheme.colors.colorButtonNormal
            )
        }
    }
}

@Composable
fun LrcItem(
    modifier: Modifier = Modifier,
    lyric: String,
    focused: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(onClick = onClick)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
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