package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.isDownloaded
import com.geckour.q.util.toDomainTrack

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Tracks(
    albumId: Long = -1,
    genreName: String? = null,
    changeTopBarTitle: (title: String) -> Unit,
    onTrackSelected: (item: DomainTrack) -> Unit,
    onDownload: (item: DomainTrack) -> Unit,
    onInvalidateDownloaded: (item: DomainTrack) -> Unit,
    scrollToTop: Long
) {
    val db = DB.getInstance(LocalContext.current)
    val joinedTracks by (when {
        albumId > 0 -> db.trackDao().getAllByAlbumAsync(albumId)
        genreName != null -> db.trackDao().getAllByGenreNameAsync(genreName)
        else -> db.trackDao().getAllAsync()
    }).collectAsState(initial = emptyList())
    val defaultTabBarTitle = stringResource(id = R.string.nav_track)
    val listState = rememberLazyListState()

    LaunchedEffect(joinedTracks) {
        changeTopBarTitle(
            when {
                albumId > 0 -> db.albumDao().get(albumId)?.album?.title ?: defaultTabBarTitle
                genreName != null -> genreName
                else -> defaultTabBarTitle
            }
        )
    }

    LaunchedEffect(scrollToTop) {
        listState.animateScrollToItem(0)
    }

    LazyColumn(state = listState) {
        items(joinedTracks) { joinedTrack ->
            val domainTrack = joinedTrack.toDomainTrack()
            Card(
                shape = RectangleShape,
                elevation = 0.dp,
                backgroundColor = QTheme.colors.colorBackground,
                onClick = { onTrackSelected(domainTrack) }
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                modifier = Modifier.size(48.dp),
                                model = domainTrack.artworkUriString ?: R.drawable.ic_empty,
                                contentDescription = null
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(IntrinsicSize.Max)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = domainTrack.title,
                                    color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${domainTrack.artist.title} - ${domainTrack.album.title}",
                                    color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.height(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.width(48.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                domainTrack.discNum?.let {
                                    Text(
                                        text = it.toString(),
                                        color = QTheme.colors.colorTextPrimary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "-",
                                        color = QTheme.colors.colorTextSettingNormal,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                                domainTrack.trackNum?.let {
                                    Text(
                                        text = it.toString(),
                                        color = QTheme.colors.colorTextPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = "${domainTrack.codec}・${domainTrack.bitrate}kbps・${domainTrack.sampleRate}kHz",
                                color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .weight(1f)
                                    .width(IntrinsicSize.Max),
                            )
                            Text(
                                text = domainTrack.durationString,
                                color = QTheme.colors.colorTextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                    if (joinedTrack.track.dropboxPath != null) {
                        IconButton(
                            onClick = {
                                if (joinedTrack.track.isDownloaded) onInvalidateDownloaded(
                                    domainTrack
                                )
                                else onDownload(domainTrack)
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (joinedTrack.track.isDownloaded) Icons.Outlined.DownloadForOffline else Icons.Outlined.Download,
                                contentDescription = null,
                                tint = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}