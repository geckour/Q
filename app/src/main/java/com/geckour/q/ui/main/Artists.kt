package com.geckour.q.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getTimeString
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Artists(
    navController: NavController,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<ImmutableList<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    onSelectArtist: (item: Artist) -> Unit,
    onDownload: (dropboxPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (artistId: Long) -> Unit,
    scrollToTop: Long,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val db = DB.getInstance(LocalContext.current)
    val artists by db.artistDao().getAllOrientedAlbumAsync().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

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
        items(artists.let {
                artists -> if (isFavoriteOnly.value) artists.filter { it.isFavorite } else artists
        }) { artist ->
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
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadableDropboxPaths.isEmpty()) Icons.Outlined.DownloadForOffline else Icons.Outlined.Download,
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleFavorite(artist) },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (artist.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary
                        )
                    }
                    IconButton(
                        onClick = { onSelectArtist(artist) },
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