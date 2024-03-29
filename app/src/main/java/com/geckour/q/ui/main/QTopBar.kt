package com.geckour.q.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.BottomSheetScaffoldState
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun QTopBar(
    title: String,
    scaffoldState: BottomSheetScaffoldState,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(emptyList<SearchItem>()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TopAppBar(
            modifier = Modifier.clickable(onClick = onTapBar),
            navigationIcon = {
                IconButton(onClick = {
                    if (scaffoldState.drawerState.currentValue == DrawerValue.Closed) {
                        coroutineScope.launch { scaffoldState.drawerState.open() }
                    } else {
                        coroutineScope.launch { scaffoldState.drawerState.close() }
                    }
                }) {
                    Icon(Icons.Default.Menu, contentDescription = null)
                }
            },
            title = {
                AnimatedVisibility(visible = active.not()) { Text(text = title) }
            },
            actions = {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_daynight),
                        contentDescription = null
                    )
                }
            },
            backgroundColor = QTheme.colors.colorPrimary,
            contentColor = QTheme.colors.colorTextPrimary
        )
        SearchBar(
            query = query,
            onQueryChange = { q ->
                query = q
                if (q.isEmpty()) result = emptyList()
                else coroutineScope.launch { result = search(context, q) }
            },
            onSearch = { keyboardController?.hide() },
            active = active,
            onActiveChange = { newActive -> active = newActive },
            colors = SearchBarDefaults.colors(
                containerColor = QTheme.colors.colorBackgroundSearch,
                dividerColor = Color.Transparent,
                inputFieldColors = TextFieldDefaults.colors(
                    cursorColor = QTheme.colors.colorTextPrimary,
                    focusedTextColor = QTheme.colors.colorTextPrimary,
                    unfocusedTextColor = QTheme.colors.colorTextPrimary
                )
            ),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(bottom = 144.dp)
                    .fillMaxSize()
            ) {
                items(result) { item ->
                    when (item.type) {
                        SearchItem.SearchItemType.CATEGORY -> SearchResultSectionHeader(title = item.title)
                        else -> SearchResultItem(
                            item = item,
                            onClick = {
                                onSearchItemClicked(it)
                                when (it.type) {
                                    SearchItem.SearchItemType.ALBUM,
                                    SearchItem.SearchItemType.ARTIST,
                                    SearchItem.SearchItemType.GENRE -> active = false

                                    else -> Unit
                                }
                            },
                            onLongClick = onSearchItemLongClicked
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultSectionHeader(title: String) {
    Row(modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)) {
        Text(text = title, color = QTheme.colors.colorAccent, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    item: SearchItem,
    onClick: (item: SearchItem) -> Unit,
    onLongClick: (item: SearchItem) -> Unit
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        val artworkUriString = when (val data = item.data) {
            is DomainTrack -> data.artworkUriString
            is Album -> data.artworkUriString
            is Artist -> data.artworkUriString
            else -> null
        }
        AsyncImage(
            model = artworkUriString,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = item.title,
            color = QTheme.colors.colorTextPrimary,
            fontSize = 18.sp
        )
    }
}

suspend fun search(context: Context, query: String): List<SearchItem> {
    val items = mutableListOf<SearchItem>()
    val db = DB.getInstance(context)
    val tracks = db.searchTrackByFuzzyTitle(query)
        .take(10)
        .map {
            SearchItem(
                it.track.title,
                it.toDomainTrack(),
                SearchItem.SearchItemType.TRACK
            )
        }
    if (tracks.isNotEmpty()) {
        items.add(
            SearchItem(
                context.getString(R.string.search_category_track),
                SearchCategory(),
                SearchItem.SearchItemType.CATEGORY
            )
        )
        items.addAll(tracks)
    }

    val albums = db.searchAlbumByFuzzyTitle(query)
        .take(10)
        .map { SearchItem(it.album.title, it.album, SearchItem.SearchItemType.ALBUM) }
    if (albums.isNotEmpty()) {
        items.add(
            SearchItem(
                context.getString(R.string.search_category_album),
                SearchCategory(),
                SearchItem.SearchItemType.CATEGORY
            )
        )
        items.addAll(albums)
    }

    val artists = db.searchArtistByFuzzyTitle(query)
        .take(10)
        .map { SearchItem(it.title, it, SearchItem.SearchItemType.ARTIST) }
    if (artists.isNotEmpty()) {
        items.add(
            SearchItem(
                context.getString(R.string.search_category_artist),
                SearchCategory(),
                SearchItem.SearchItemType.CATEGORY
            )
        )
        items.addAll(artists)
    }

    val genres = db.trackDao().getAllGenreByName(query)
        .take(10)
        .map {
            val totalDuration =
                db.trackDao().getAllByGenreName(it).sumOf { it.track.duration }
            SearchItem(it, Genre(null, it, totalDuration), SearchItem.SearchItemType.GENRE)
        }
    if (genres.isNotEmpty()) {
        items.add(
            SearchItem(
                context.getString(R.string.search_category_genre),
                SearchCategory(),
                SearchItem.SearchItemType.CATEGORY
            )
        )
        items.addAll(genres)
    }

    return items
}