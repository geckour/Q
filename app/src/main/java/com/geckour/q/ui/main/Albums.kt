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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Albums(
    navController: NavController,
    artistId: Long = -1,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<List<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    changeTopBarTitle: (title: String) -> Unit,
    onSelectAlbum: (item: JoinedAlbum) -> Unit,
    onDownload: (dropboxPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (albumId: Long) -> Unit,
    scrollToTop: Long,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val db = DB.getInstance(LocalContext.current)
    val joinedAlbums by (
            if (artistId < 1) db.albumDao().getAllAsync()
            else db.albumDao().getAllByArtistIdAsync(artistId)
            ).collectAsState(initial = emptyList())
    val defaultTabBarTitle = stringResource(id = R.string.nav_album)
    val listState = rememberLazyListState()

    LaunchedEffect(joinedAlbums) {
        changeTopBarTitle(
            if (artistId > 0) db.artistDao().get(artistId)?.title ?: defaultTabBarTitle
            else defaultTabBarTitle
        )
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
                Switch(
                    checked = isFavoriteOnly.value,
                    onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                )
            }
        }
        items(joinedAlbums.let { joinedAlbums ->
            if (isFavoriteOnly.value) joinedAlbums.filter { it.album.isFavorite } else joinedAlbums
        }) { joinedAlbum ->
            val containDropboxContent by db.albumDao()
                .containDropboxContent(joinedAlbum.album.id)
                .collectAsState(initial = false)
            val downloadableDropboxPaths by db.albumDao()
                .downloadableDropboxPaths(joinedAlbum.album.id)
                .collectAsState(emptyList())
            Surface(
                color = QTheme.colors.colorBackground,
                elevation = 0.dp,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate(route = "tracks?albumId=${joinedAlbum.album.id}") },
                    onLongClick = { onSelectAlbum(joinedAlbum) }
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = joinedAlbum.album.artworkUriString ?: R.drawable.ic_empty,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(80.dp)
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
    }
}