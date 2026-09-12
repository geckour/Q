package com.geckour.q.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

private const val HEADER_ITEM_COUNT = 0

@Composable
fun SavedQueues(
    endItemMargin: Dp,
    onSelectQueue: (queue: List<String>, actionType: InsertActionType) -> Unit,
    initialScrollPosition: Pair<Int, Int>,
    scrollToTop: Long,
    onScrollPositionUpdated: (newIndex: Int, newOffset: Int) -> Unit,
) {
    val context = LocalContext.current
    val db = DB.getInstance(context)
    val listState = rememberLazyListState()
    val pager = remember {
        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = true),
            initialKey = (initialScrollPosition.first - HEADER_ITEM_COUNT).coerceAtLeast(0)
        ) {
            db.savedQueueDao().getAllAsPagingSource()
        }
    }
    val uiSavedQueueFlow = remember(pager) {
        pager.flow.map { pagingData ->
            pagingData.map {
                UiSavedQueue(
                    savedQueueSummary = it,
                    artworkUrlStrings = db.savedQueueDao()
                        .getArtworkUriStrings(
                            savedQueueId = it.savedQueue.id,
                            limit = 30,
                        ),
                    queue = db.savedQueueDao().getTracks(savedQueueId = it.savedQueue.id),
                )
            }
        }
    }
    val lazyPagingItems = uiSavedQueueFlow.collectAsLazyPagingItems()
    var expandedSavedQueueIndex by remember { mutableIntStateOf(-1) }
    var listHeight by remember { mutableIntStateOf(0) }

    ScrollPositionEffect(
        listState = listState,
        initialScrollPosition = initialScrollPosition,
        headerItemCount = HEADER_ITEM_COUNT,
        isItemLoaded = { lazyPagingItems.itemSnapshotList.getOrNull(it) != null },
        onScrollPositionUpdated = onScrollPositionUpdated
    )

    ScrollToTopEffect(listState = listState, scrollToTop = scrollToTop)

    LaunchedEffect(expandedSavedQueueIndex) {
        if (expandedSavedQueueIndex < 0) {
            return@LaunchedEffect
        }

        delay(500.milliseconds)

        listState.animateScrollToItem(index = expandedSavedQueueIndex, scrollOffset = 0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.onSizeChanged { intSize -> listHeight = intSize.height },
    ) {
        items(lazyPagingItems.itemCount) { index ->
            val uiSavedQueue = lazyPagingItems[index] ?: return@items

            Column(
                modifier = Modifier
                    .combinedClickable(
                        onLongClick = {
                            expandedSavedQueueIndex =
                                if (expandedSavedQueueIndex == index) -1 else index
                        },
                        onClick = {
                            if (expandedSavedQueueIndex == index) {
                                expandedSavedQueueIndex = -1
                                return@combinedClickable
                            }

                            onSelectQueue(
                                uiSavedQueue.queue.map { it.track.sourcePath },
                                InsertActionType.OVERRIDE,
                            )
                        }
                    )
                    .padding(vertical = 4.dp)
            ) {
                val queueListState = rememberLazyListState()
                var headerHeight by remember { mutableIntStateOf(0) }
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .onSizeChanged { intSize -> headerHeight = intSize.height }
                ) {
                    Row {
                        Text(
                            uiSavedQueue.savedQueueSummary.savedQueue.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = QTheme.colors.colorButtonNormal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(bottom = 4.dp, end = 16.dp)
                                .weight(1f),
                        )
                        Text(
                            stringResource(
                                R.string.saved_queue_item_metadata,
                                uiSavedQueue.savedQueueSummary.trackCount,
                                uiSavedQueue.savedQueueSummary.totalDuration.getTimeString(),
                            ),
                            fontSize = 12.sp,
                            color = QTheme.colors.colorTextPrimary,
                            maxLines = 1,
                        )
                    }
                    Artworks(uiSavedQueue.artworkUrlStrings)
                }
                AnimatedVisibility(visible = expandedSavedQueueIndex == index) {
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .height(with(LocalDensity.current) { (listHeight - headerHeight).toDp() })
                            .clickable(
                                // FIXME: Show dialog and enable to select insert action type
                                onClick = {
                                    onSelectQueue(
                                        uiSavedQueue.queue.map { it.track.sourcePath },
                                        InsertActionType.OVERRIDE,
                                    )
                                }
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp),
                    ) {
                        items(uiSavedQueue.queue) {
                            QueueItem(it)
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(endItemMargin))
        }
    }
}

@Composable
private fun Artworks(artworkUrlStrings: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        artworkUrlStrings.forEach {
            AsyncImage(it, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun QueueItem(track: JoinedTrack) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        AsyncImage(
            model = track.track.artworkUriString ?: track.album.artworkUriString
            ?: R.drawable.ic_empty,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Text(
            "${track.track.title} - ${track.artist.title} (${track.album.title})",
            fontSize = 16.sp,
            color = QTheme.colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}