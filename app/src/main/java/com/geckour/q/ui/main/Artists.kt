package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Artists(
    navController: NavController,
    onSelectArtist: (item: Artist) -> Unit,
    onDownload: (dropboxPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (artistId: Long) -> Unit,
    scrollToTop: Long
) {
    val db = DB.getInstance(LocalContext.current)
    val artists by db.artistDao().getAllOrientedAlbumAsync().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTop) {
        listState.animateScrollToItem(0)
    }

    LazyColumn(state = listState) {
        items(artists) { artist ->
            val containDropboxContent by db.artistDao()
                .containDropboxContent(artist.id)
                .collectAsState(initial = false)
            val downloadableDropboxPaths by db.artistDao()
                .downloadableDropboxPaths(artist.id)
                .collectAsState(initial = emptyList())
            Surface(
                color = QTheme.colors.colorBackground,
                elevation = 0.dp,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate(route = "albums?artistId=${artist.id}") },
                    onLongClick = { onSelectArtist(artist) }),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = artist.artworkUriString ?: R.drawable.ic_empty,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                    )
                    Text(
                        text = artist.title,
                        fontSize = 20.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                            .width(IntrinsicSize.Max)
                    )
                    Text(
                        text = artist.totalDuration.getTimeString(),
                        fontSize = 12.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (containDropboxContent) {
                        IconButton(
                            onClick = {
                                if (downloadableDropboxPaths.isEmpty()) onInvalidateDownloaded(
                                    artist.id
                                )
                                else onDownload(downloadableDropboxPaths)
                            },
                            Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadableDropboxPaths.isEmpty()) Icons.Outlined.DownloadForOffline else Icons.Outlined.Download,
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSelectArtist(artist) },
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