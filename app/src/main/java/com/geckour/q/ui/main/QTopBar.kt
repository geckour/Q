package com.geckour.q.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QTopBar(
    title: String,
    appBarOptionMediaItem: MediaItem?,
    drawerState: DrawerState,
    isSearchActive: Boolean,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAllArtists: (allArtists: AllArtists?) -> Unit,
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
                if (appBarOptionMediaItem != null) {
                    val isFavorite = when (appBarOptionMediaItem) {
                        is Artist -> appBarOptionMediaItem.isFavorite
                        is Album -> appBarOptionMediaItem.isFavorite
                        is UiTrack -> appBarOptionMediaItem.isFavorite
                        else -> null
                    }
                    if (isFavorite != null) {
                        IconButton(onClick = {
                            val newMediaItem = onToggleFavorite(appBarOptionMediaItem)
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
                if (appBarOptionMediaItem != null) {
                    IconButton(
                        onClick = {
                            when (appBarOptionMediaItem) {
                                is AllArtists -> {
                                    onSelectAllArtists(AllArtists)
                                }

                                is Artist -> {
                                    onSelectArtist(appBarOptionMediaItem)
                                }

                                is Album -> {
                                    onSelectAlbum(appBarOptionMediaItem)
                                }

                                is UiTrack -> {
                                    onSelectTrack(appBarOptionMediaItem)
                                }

                                else -> Unit
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors().copy(
                containerColor = QTheme.colors.colorPrimary,
                navigationIconContentColor = QTheme.colors.colorTextPrimary,
                titleContentColor = QTheme.colors.colorTextPrimary,
                actionIconContentColor = QTheme.colors.colorTextPrimary
            ),
        )
    }
}