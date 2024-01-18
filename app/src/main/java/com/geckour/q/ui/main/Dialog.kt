package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.getDropboxCredential
import com.geckour.q.util.setHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun TrackOptionDialog(
    domainTrack: DomainTrack,
    navController: NavHostController,
    onSelectTrack: (track: DomainTrack?) -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onExportLyric: (domainTrack: DomainTrack) -> Unit,
    onAttachLyric: (trackId: Long) -> Unit,
    onDetachLyric: (trackId: Long) -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit
) {
    Dialog(onDismissRequest = { onSelectTrack(null) }) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column {
                DialogListItem(
                    onClick = {
                        onNewQueue(
                            listOf(domainTrack),
                            InsertActionType.NEXT,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onNewQueue(
                            listOf(domainTrack),
                            InsertActionType.LAST,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onNewQueue(
                            listOf(domainTrack),
                            InsertActionType.OVERRIDE,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        navController.navigate("albums?artistId=${domainTrack.artist.id}")
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_transition_to_artist),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        navController.navigate("tracks?albumId=${domainTrack.album.id}")
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_transition_to_album),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onExportLyric(domainTrack)
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_export_lyric),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onAttachLyric(domainTrack.id)
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_attach_lyric),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onDetachLyric(domainTrack.id)
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_detach_lyric),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onDeleteTrack(domainTrack)
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_delete_from_device),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumOptionDialog(
    album: Album,
    onSelectAlbum: (album: Album?) -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectAlbum(null) }) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column {
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.NEXT,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.LAST,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByAlbum(album.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ALBUM
                            )
                            onSelectAlbum(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all_simple_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            DB.getInstance(context).trackDao()
                                .getAllByAlbum(album.id)
                                .forEach {
                                    onDeleteTrack(it.toDomainTrack())
                                    onSelectAlbum(null)
                                }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_delete_from_device),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistOptionDialog(
    artist: Artist,
    onSelectArtist: (artist: Artist?) -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectArtist(null) }) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column {
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.NEXT,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.LAST,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_NEXT,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_albums_insert_all_shuffle_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_LAST,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_albums_insert_all_shuffle_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_OVERRIDE,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_albums_override_all_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByArtist(artist.id)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ARTIST
                            )
                            onSelectArtist(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all_simple_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            DB.getInstance(context).trackDao()
                                .getAllByArtist(artist.id)
                                .forEach {
                                    onDeleteTrack(it.toDomainTrack())
                                    onSelectArtist(null)
                                }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_delete_from_device),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun GenreOptionDialog(
    genre: Genre,
    onSelectGenre: (genre: Genre?) -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectGenre(null) }) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column {
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.NEXT,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.LAST,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                            onNewQueue(
                                tracks,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val tracks =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.toDomainTrack() }
                                    .shuffled()
                            onNewQueue(
                                tracks,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ALBUM
                            )
                            onSelectGenre(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_all_simple_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            DB.getInstance(context).trackDao()
                                .getAllByGenreName(genre.name)
                                .forEach {
                                    onDeleteTrack(it.toDomainTrack())
                                    onSelectGenre(null)
                                }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_delete_from_device),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun DropboxDialog(
    hasAlreadyShownDropboxSyncAlert: Boolean,
    currentDropboxItemList: Pair<String, List<FolderMetadata>>,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (selectedFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (targetFolderPath: String?, needDownloaded: Boolean) -> Unit
) {
    val context = LocalContext.current
    val credential = runBlocking {
        context.getDropboxCredential().firstOrNull()
    }
    if (hasAlreadyShownDropboxSyncAlert) {
        if (credential.isNullOrBlank()) {
            onStartAuthDropbox()
        } else {
            if (currentDropboxItemList.second.isEmpty()) {
                onShowDropboxFolderChooser(null)
            }
            var selectedHistory by remember { mutableStateOf(emptyList<FolderMetadata>()) }
            Dialog(onDismissRequest = hideDropboxDialog) {
                var needDownloaded by remember { mutableStateOf(false) }
                Card(
                    backgroundColor = QTheme.colors.colorBackground,
                    modifier = Modifier.heightIn(max = 800.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.dialog_title_dropbox_choose_folder),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = QTheme.colors.colorAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.dialog_desc_dropbox_choose_folder),
                            fontSize = 18.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.dialog_switch_need_downloaded),
                                fontSize = 18.sp,
                                color = QTheme.colors.colorTextPrimary,
                            )
                            Switch(
                                checked = needDownloaded,
                                onCheckedChange = {
                                    needDownloaded =
                                        needDownloaded.not()
                                })
                        }
                        Text(
                            text = currentDropboxItemList.first,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = QTheme.colors.colorAccent
                        )
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            items(currentDropboxItemList.second) {
                                Text(
                                    text = it.name,
                                    fontSize = 20.sp,
                                    color = QTheme.colors.colorTextPrimary,
                                    modifier = Modifier
                                        .clickable {
                                            selectedHistory += it
                                            onShowDropboxFolderChooser(it)
                                        }
                                        .padding(
                                            horizontal = 8.dp,
                                            vertical = 12.dp
                                        )
                                        .fillMaxWidth()
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val prev = {
                                selectedHistory =
                                    selectedHistory.dropLast(1)
                                onShowDropboxFolderChooser(selectedHistory.lastOrNull())
                            }
                            BackHandler(selectedHistory.isNotEmpty()) {
                                prev()
                            }
                            if (selectedHistory.isNotEmpty()) {
                                TextButton(onClick = prev) {
                                    Text(
                                        text = stringResource(R.string.dialog_prev),
                                        fontSize = 16.sp,
                                        color = QTheme.colors.colorTextPrimary
                                    )
                                }
                            }
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            TextButton(onClick = hideDropboxDialog) {
                                Text(
                                    text = stringResource(R.string.dialog_ng),
                                    fontSize = 16.sp,
                                    color = QTheme.colors.colorTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    startDropboxSync(
                                        selectedHistory.lastOrNull()?.pathLower,
                                        needDownloaded
                                    )
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.dialog_ok),
                                    fontSize = 16.sp,
                                    color = QTheme.colors.colorAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        Dialog(onDismissRequest = hideDropboxDialog) {
            Card(backgroundColor = QTheme.colors.colorBackground) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_dropbox_sync_caution),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = QTheme.colors.colorAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.dialog_desc_dropbox_sync_caution),
                        fontSize = 18.sp,
                        color = QTheme.colors.colorTextPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = hideDropboxDialog) {
                            Text(
                                text = stringResource(R.string.dialog_ng),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    context.setHasAlreadyShownDropboxSyncAlert(
                                        true
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_ok),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShuffleResetOptionDialog(
    hideResetShuffleDialog: () -> Unit,
    onShuffle: (actionType: ShuffleActionType) -> Unit,
    onResetShuffle: () -> Unit
) {
    Dialog(onDismissRequest = hideResetShuffleDialog) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column {
                DialogListItem(
                    onClick = {
                        onResetShuffle()
                        hideResetShuffleDialog()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_choice_reset_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onShuffle(ShuffleActionType.SHUFFLE_ALBUM_ORIENTED)
                        hideResetShuffleDialog()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_choice_album_oriented_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onShuffle(ShuffleActionType.SHUFFLE_ARTIST_ORIENTED)
                        hideResetShuffleDialog()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_choice_artist_oriented_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDownloadDialog(onCancelDownload: () -> Unit, onStartDownloader: () -> Unit) {
    Dialog(onDismissRequest = onCancelDownload) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_title_dropbox_download),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = QTheme.colors.colorAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.dialog_desc_dropbox_download),
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelDownload) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onStartDownloader) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmInvalidateDownloadedDialog(
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit
) {
    Dialog(onDismissRequest = onCancelInvalidateDownloaded) {
        Card(backgroundColor = QTheme.colors.colorBackground) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_title_dropbox_invalidate_downloaded),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = QTheme.colors.colorAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.dialog_desc_dropbox_invalidate_downloaded),
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelInvalidateDownloaded) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onStartInvalidateDownloaded) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.Dialogs(
    selectedTrack: DomainTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedGenre: Genre?,
    navController: NavHostController,
    currentDropboxItemList: Pair<String, List<FolderMetadata>>,
    downloadTargets: List<String>,
    invalidateDownloadedTargets: List<Long>,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    onSelectTrack: (track: DomainTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit,
    onExportLyric: (domainTrack: DomainTrack) -> Unit,
    onAttachLyric: (trackId: Long) -> Unit,
    onDetachLyric: (trackId: Long) -> Unit,
    onNewQueue: (queue: List<DomainTrack>, actionType: InsertActionType, classType: OrientedClassType) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (selectedFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (targetFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onShuffle: (actionType: ShuffleActionType) -> Unit,
    onResetShuffle: () -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
) {
    selectedTrack?.let { domainTrack ->
        TrackOptionDialog(
            domainTrack = domainTrack,
            navController = navController,
            onSelectTrack = onSelectTrack,
            onNewQueue = onNewQueue,
            onExportLyric = onExportLyric,
            onAttachLyric = onAttachLyric,
            onDetachLyric = onDetachLyric,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedAlbum?.let { album ->
        AlbumOptionDialog(
            album = album,
            onSelectAlbum = onSelectAlbum,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedArtist?.let { artist ->
        ArtistOptionDialog(
            artist = artist,
            onSelectArtist = onSelectArtist,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedGenre?.let { genre ->
        GenreOptionDialog(
            genre = genre,
            onSelectGenre = onSelectGenre,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    if (showDropboxDialog) {
        DropboxDialog(
            hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
            currentDropboxItemList = currentDropboxItemList,
            onStartAuthDropbox = onStartAuthDropbox,
            onShowDropboxFolderChooser = onShowDropboxFolderChooser,
            hideDropboxDialog = hideDropboxDialog,
            startDropboxSync = startDropboxSync
        )
    }
    if (showResetShuffleDialog) {
        ShuffleResetOptionDialog(
            hideResetShuffleDialog = hideResetShuffleDialog,
            onShuffle = onShuffle,
            onResetShuffle = onResetShuffle
        )
    }
    if (downloadTargets.isNotEmpty()) {
        ConfirmDownloadDialog(
            onCancelDownload = onCancelDownload,
            onStartDownloader = onStartDownloader
        )
    }
    if (invalidateDownloadedTargets.isNotEmpty()) {
        ConfirmInvalidateDownloadedDialog(
            onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
            onStartInvalidateDownloaded = onStartInvalidateDownloaded
        )
    }
}