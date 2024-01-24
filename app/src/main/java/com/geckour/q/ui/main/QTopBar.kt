package com.geckour.q.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DrawerState
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.launch

@Composable
fun QTopBar(
    title: String,
    optionMediaItem: MediaItem?,
    drawerState: DrawerState,
    isSearchActive: Boolean,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onShowOptions: () -> Unit,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TopAppBar(
            modifier = Modifier.clickable(onClick = onTapBar),
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (drawerState.currentValue == DrawerValue.Closed) {
                            coroutineScope.launch { drawerState.open() }
                        } else {
                            coroutineScope.launch { drawerState.close() }
                        }
                    }
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null)
                }
            },
            title = {
                AnimatedVisibility(visible = isSearchActive.not()) {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            actions = {
                if (optionMediaItem != null) {
                    val isFavorite = when (optionMediaItem) {
                        is Artist -> optionMediaItem.isFavorite
                        is Album -> optionMediaItem.isFavorite
                        is DomainTrack -> optionMediaItem.isFavorite
                        else -> null
                    }
                    if (isFavorite != null) {
                        IconButton(onClick = {
                            val newMediaItem = onToggleFavorite(optionMediaItem)
                            onSetOptionMediaItem(newMediaItem)
                        }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_daynight),
                        contentDescription = null
                    )
                }
                if (optionMediaItem != null) {
                    IconButton(onClick = onShowOptions) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null
                        )
                    }
                }
            },
            backgroundColor = QTheme.colors.colorPrimary,
            contentColor = QTheme.colors.colorTextPrimary
        )
    }
}