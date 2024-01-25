package com.geckour.q.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getThumb
import com.geckour.q.util.getTimeString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Genres(
    navController: NavController,
    isSearchActive: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<ImmutableList<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    onSelectGenre: (item: Genre) -> Unit,
    scrollToTop: Long,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val context = LocalContext.current
    val db = DB.getInstance(context)
    val genreNames by db.trackDao()
        .getAllGenreAsync()
        .collectAsState(initial = emptyList())
    var genres by remember { mutableStateOf<ImmutableList<Genre>>(persistentListOf()) }
    val listState = rememberLazyListState()

    LaunchedEffect(genreNames) {
        launch {
            genres = genreNames.map { genreName ->
                val tracks = db.trackDao().getAllByGenreName(genreName)
                Genre(
                    tracks.getGenreThumb(context),
                    genreName,
                    tracks.sumOf { it.track.duration }
                )
            }.toImmutableList()
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
        items(genres) { genre ->
            Timber.d("qgeck genre: $genre")
            Column(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { navController.navigate(route = "tracks?genreName=${genre.name}") },
                        onLongClick = { onSelectGenre(genre) }
                    )
                    .background(color = QTheme.colors.colorBackground)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                AsyncImage(
                    model = genre.thumb,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(4f),
                    alignment = Alignment.BottomEnd
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = genre.name,
                        fontSize = 20.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Text(
                        text = genre.totalDuration.getTimeString(),
                        fontSize = 12.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onSelectGenre(genre) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_option),
                            contentDescription = null,
                            tint = QTheme.colors.colorTextPrimary
                        )
                    }
                }
                Divider(color = QTheme.colors.colorPrimaryDark)
            }
        }
    }
}

private suspend fun List<JoinedTrack>.getGenreThumb(context: Context): Bitmap? =
    this.distinctBy { it.album.id }
        .take(5)
        .mapNotNull { it.album.artworkUriString }
        .getThumb(context)