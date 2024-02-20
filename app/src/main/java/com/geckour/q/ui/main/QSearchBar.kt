package com.geckour.q.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.toUiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QSearchBar(
    isSearchActive: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<ImmutableList<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(query.value) {
        if (query.value.isEmpty()) result.value = persistentListOf()
        else coroutineScope.launch { result.value = search(context, query.value) }
    }
    LaunchedEffect(isSearchActive.value) {
        if (isSearchActive.value.not()) query.value = ""
    }
    BackHandler(isSearchActive.value) {
        isSearchActive.value = false
    }

    DockedSearchBar(
        query = query.value,
        onQueryChange = { q -> query.value = q },
        onSearch = { keyboardController?.hide() },
        active = isSearchActive.value,
        onActiveChange = { newActive -> isSearchActive.value = newActive },
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
        trailingIcon = {
            if (isSearchActive.value) {
                IconButton(onClick = { isSearchActive.value = false }) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null
                    )
                }
            }
        },
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(result.value) { item ->
                when (item.type) {
                    SearchItem.SearchItemType.CATEGORY -> SearchResultSectionHeader(title = item.title)
                    else -> SearchResultItem(
                        item = item,
                        onClick = {
                            onSearchItemClicked(it)
                            when (it.type) {
                                SearchItem.SearchItemType.ALBUM,
                                SearchItem.SearchItemType.ARTIST,
                                SearchItem.SearchItemType.GENRE -> {
                                    isSearchActive.value = false
                                }

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
            is UiTrack -> data.artworkUriString
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

private suspend fun search(context: Context, query: String): ImmutableList<SearchItem> {
    val items = mutableListOf<SearchItem>()
    val db = DB.getInstance(context)
    val tracks = db.searchTrackByFuzzyTitle(query)
        .take(10)
        .map {
            SearchItem(
                it.track.title,
                it.toUiTrack(),
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
        .map { genreName ->
            val totalDuration =
                db.trackDao().getAllByGenreName(genreName).sumOf { it.track.duration }
            SearchItem(
                genreName,
                Genre(null, genreName, totalDuration),
                SearchItem.SearchItemType.GENRE
            )
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

    return items.toImmutableList()
}