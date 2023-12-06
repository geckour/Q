package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Albums(
    navController: NavController,
    artistId: Long = -1,
    changeTopBarTitle: (title: String) -> Unit,
    onSelectAlbum: (item: JoinedAlbum) -> Unit,
    onDownload: (dropboxPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (albumId: Long) -> Unit,
    scrollToTop: Long
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

    LazyColumn(state = listState) {
        items(joinedAlbums) { joinedAlbum ->
            val containDropboxContent by db.albumDao()
                .containDropboxContent(joinedAlbum.album.id)
                .collectAsState(initial = false)
            val downloadableDropboxPaths by db.albumDao()
                .downloadableDropboxPaths(joinedAlbum.album.id)
                .collectAsState(emptyList())
            Card(
                shape = RectangleShape,
                backgroundColor = QTheme.colors.colorBackground,
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
                            Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadableDropboxPaths.isNotEmpty()) Icons.Outlined.Download else Icons.Outlined.DownloadForOffline,
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSelectAlbum(joinedAlbum) },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
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