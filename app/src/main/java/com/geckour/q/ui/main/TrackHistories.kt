package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import coil.compose.AsyncImage
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.UiTrackHistory
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getDateTimeString
import com.geckour.q.util.toUiTrack
import kotlinx.coroutines.flow.map

private const val HEADER_ITEM_COUNT = 0

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackHistories(
    endItemMargin: Dp,
    onSelectHistory: (item: UiTrackHistory) -> Unit,
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
            db.trackHistoryDao().getAllAsPagingSource()
        }
    }
    val uiTrackHistoryFlow = remember(pager) {
        pager.flow.map { pagingData ->
            pagingData.map {
                UiTrackHistory(
                    trackHistory = it.trackHistory,
                    uiTrack = it.joinedTrack.toUiTrack(),
                )
            }
        }
    }
    val lazyPagingItems = uiTrackHistoryFlow.collectAsLazyPagingItems()

    ScrollPositionEffect(
        listState = listState,
        initialScrollPosition = initialScrollPosition,
        headerItemCount = HEADER_ITEM_COUNT,
        isItemLoaded = { lazyPagingItems.itemSnapshotList.getOrNull(it) != null },
        onScrollPositionUpdated = onScrollPositionUpdated
    )

    ScrollToTopEffect(listState = listState, scrollToTop = scrollToTop)

    LazyColumn(state = listState) {
        items(lazyPagingItems.itemCount) { index ->
            val uiTrackHistory = lazyPagingItems[index] ?: return@items

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = { onSelectHistory(uiTrackHistory) })
                        .background(color = QTheme.colors.colorBackground)
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = uiTrackHistory.uiTrack.artworkUriString,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = "${uiTrackHistory.uiTrack.title} - ${uiTrackHistory.uiTrack.artist.title}",
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = uiTrackHistory.trackHistory.createdAt.getDateTimeString(),
                            fontSize = 12.sp,
                            color = QTheme.colors.colorTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = QTheme.colors.colorPrimaryDark)
            }
        }
        item {
            Spacer(modifier = Modifier.height(endItemMargin))
        }
    }
}