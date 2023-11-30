package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.toDomainTrack

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Tracks(albumId: Long = -1, genreName: String? = null) {
    val db = DB.getInstance(LocalContext.current)
    val joinedTracks by (when {
        albumId > 0 -> db.trackDao().getAllByAlbumAsync(albumId)
        genreName != null -> db.trackDao().getAllByGenreNameAsync(genreName)
        else -> db.trackDao().getAllAsync()
    }).collectAsState(initial = emptyList())

    LazyColumn {
        items(joinedTracks) { joinedTrack ->
            val domainTrack = joinedTrack.toDomainTrack()
//        val popupMenu = PopupMenu(LocalContext.current, LocalView.current).apply {
//            setOnMenuItemClickListener { menuItem ->
//                when (menuItem.itemId) {
//                    R.id.menu_transition_to_artist -> {
//                        mainViewModel.onRequestNavigate(domainTrack.artist)
//                    }
//
//                    R.id.menu_transition_to_album -> {
//                        mainViewModel.onRequestNavigate(domainTrack.album)
//                    }
//
//                    R.id.menu_insert_all_next,
//                    R.id.menu_insert_all_last,
//                    R.id.menu_override_all -> {
//                        mainViewModel.onNewQueue(
//                            listOf(domainTrack),
//                            when (menuItem.itemId) {
//                                R.id.menu_insert_all_next -> {
//                                    InsertActionType.NEXT
//                                }
//
//                                R.id.menu_insert_all_last -> {
//                                    InsertActionType.LAST
//                                }
//
//                                R.id.menu_override_all -> {
//                                    InsertActionType.OVERRIDE
//                                }
//
//                                else -> return@setOnMenuItemClickListener false
//                            },
//                            OrientedClassType.TRACK
//                        )
//                    }
//
//                    R.id.menu_edit_metadata -> {
//                        lifecycleScope.launch {
//                            val db = DB.getInstance(requireContext())
//                            val tracks = mainViewModel.currentQueueFlow.value.mapNotNull {
//                                db.trackDao().get(it.id)
//                            }
//                            requireContext().showFileMetadataUpdateDialog(
//                                tracks,
//                                onUpdate = { binding ->
//                                    lifecycleScope.launch {
//                                        binding.updateFileMetadata(requireContext(), db, tracks)
//                                    }
//                                }
//                            )
//                        }
//                    }
//
//                    R.id.menu_delete_track -> {
//                        mainViewModel.deleteTrack(domainTrack)
//                    }
//                }
//
//                return@setOnMenuItemClickListener true
//            }
//            inflate(R.menu.queue)
//        }
            Card(
                shape = RectangleShape,
                elevation = 0.dp,
                backgroundColor = QTheme.colors.colorBackground,
                onClick = { /* TODO */ }
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
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
            }
        }
    }
}