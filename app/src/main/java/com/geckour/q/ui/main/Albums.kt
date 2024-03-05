package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isDownloaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Albums(
    navController: NavController,
    artistId: Long = -1,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<ImmutableList<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    changeTopBarTitle: (title: String) -> Unit,
    onSelectAlbum: (item: JoinedAlbum) -> Unit,
    onDownload: (dropboxPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (albumId: Long) -> Unit,
    resumeScrollToIndex: Int,
    resumeScrollToOffset: Int,
    scrollToTop: Long,
    onScrollPositionUpdated: (newIndex: Int, newOffset: Int) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val db = DB.getInstance(LocalContext.current)
    val pager = remember {
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = true)) {
            if (artistId < 1) db.albumDao().getAllAsPagingSource()
            else db.albumDao().getAllByArtistIdAsPagingSource(artistId)
        }
    }
    val lazyPagingItems =
        (if (isFavoriteOnly.value) {
            pager.flow.map { pagingData -> pagingData.filter { (album, _) -> album.isFavorite } }
        } else pager.flow)
            .collectAsLazyPagingItems()
    val defaultTabBarTitle = stringResource(id = R.string.nav_album)
    val listState = rememberLazyListState()

    LaunchedEffect(artistId) {
        changeTopBarTitle(
            if (artistId > 0) db.artistDao().get(artistId)?.title ?: defaultTabBarTitle
            else defaultTabBarTitle
        )
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress.not()) {
            onScrollPositionUpdated(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(resumeScrollToIndex) {
        coroutineScope.launch {
            listState.scrollToItem(
                index = resumeScrollToIndex,
                scrollOffset = resumeScrollToOffset
            )
        }
    }

    LaunchedEffect(scrollToTop) {
        listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            QSearchBar(
                isSearchActive = isSearchActive,
                query = query,
                result = result,
                keyboardController = keyboardController,
                onSearchItemClicked = onSearchItemClicked,
                onSearchItemLongClicked = onSearchItemLongClicked
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_switch_desc_filter_only_favorite),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                QSwitch(
                    checked = isFavoriteOnly.value,
                    onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                )
            }
        }
        items(lazyPagingItems.itemCount) { index ->
            val joinedAlbum = lazyPagingItems[index] ?: return@items
            val tracks by db.trackDao().getAllByAlbumAsFlow(joinedAlbum.album.id)
                .collectAsState(initial = emptyList())
            val containDropboxContent = tracks.any { it.dropboxPath != null }
            val downloadableDropboxPaths = tracks.mapNotNull {
                if (it.isDownloaded) null else it.dropboxPath
            }
            Surface(
                color = QTheme.colors.colorBackground,
                shadowElevation = 0.dp,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate(route = "tracks?albumId=${joinedAlbum.album.id}") },
                    onLongClick = { onSelectAlbum(joinedAlbum) }
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = joinedAlbum.album.artworkUriString ?: R.drawable.ic_empty,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .weight(1f)
                            .width(IntrinsicSize.Max)
                    ) {
                        Text(
                            text = joinedAlbum.album.title,
                            fontSize = 20.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                        Text(
                            text = joinedAlbum.artist.title,
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                    Text(
                        text = joinedAlbum.album.totalDuration.getTimeString(),
                        fontSize = 12.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (containDropboxContent) {
                        IconButton(
                            onClick = {
                                if (downloadableDropboxPaths.isEmpty()) onInvalidateDownloaded(
                                    joinedAlbum.album.id
                                )
                                else onDownload(downloadableDropboxPaths)
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadableDropboxPaths.isNotEmpty()) Icons.Outlined.Download else Icons.Outlined.DownloadForOffline,
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleFavorite(joinedAlbum.album) },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (joinedAlbum.album.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary
                        )
                    }
                    IconButton(
                        onClick = { onSelectAlbum(joinedAlbum) },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_option),
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary
                        )
                    }
                }
            }
        }
        if (lazyPagingItems.loadState.append == LoadState.Loading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}