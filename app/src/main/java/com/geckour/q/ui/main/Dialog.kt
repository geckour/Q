package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.getDropboxCredential
import com.geckour.q.util.setHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.toUiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun TrackOptionDialog(
    uiTrack: UiTrack,
    navController: NavHostController,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onGenerateQueue: (
        track: UiTrack,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onExportLyric: (uiTrack: UiTrack) -> Unit,
    onAttachLyric: (trackId: Long) -> Unit,
    onDetachLyric: (trackId: Long) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit
) {
    Dialog(onDismissRequest = { onSelectTrack(null) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                DialogListItem(
                    onClick = {
                        onNewQueue(
                            persistentListOf(uiTrack.sourcePath),
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
                            persistentListOf(uiTrack.sourcePath),
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
                            persistentListOf(uiTrack.sourcePath),
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
                        onGenerateQueue(
                            uiTrack,
                            InsertActionType.NEXT,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_generated_queue_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onGenerateQueue(
                            uiTrack,
                            InsertActionType.LAST,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_insert_generated_queue_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        onGenerateQueue(
                            uiTrack,
                            InsertActionType.OVERRIDE,
                            OrientedClassType.TRACK
                        )
                        onSelectTrack(null)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_override_generated_queue),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        navController.navigate("albums?artistId=${uiTrack.albumArtist?.id ?: uiTrack.artist.id}")
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
                        navController.navigate("tracks?albumId=${uiTrack.album.id}")
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
                        onExportLyric(uiTrack)
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
                        onAttachLyric(uiTrack.id)
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
                        onDetachLyric(uiTrack.id)
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
                        onDeleteTrack(uiTrack)
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
    isFavoriteOnly: MutableState<Boolean>,
    onSelectAlbum: (album: Album?) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectAlbum(null) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_switch_desc_filter_only_favorite)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    QSwitch(
                        checked = isFavoriteOnly.value,
                        onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.NEXT,
                                OrientedClassType.ALBUM,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.LAST,
                                OrientedClassType.ALBUM,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ALBUM,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ALBUM,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ALBUM,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                        else it.getAllByAlbum(album.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ALBUM,
                                null,
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
                                .let {
                                    if (isFavoriteOnly.value) it.getAllWithFavoriteByAlbum(album.id)
                                    else it.getAllByAlbum(album.id)
                                }
                                .forEach {
                                    onDeleteTrack(it.toUiTrack())
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
    isFavoriteOnly: MutableState<Boolean>,
    onSelectArtist: (artist: Artist?) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectArtist(null) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_switch_desc_filter_only_favorite)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    QSwitch(
                        checked = isFavoriteOnly.value,
                        onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.NEXT,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.LAST,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_NEXT,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_LAST,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ARTIST,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) {
                                            it.getAllWithFavoriteByArtist(artist.id)
                                        } else it.getAllByArtist(artist.id)
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
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
                                .let {
                                    if (isFavoriteOnly.value) {
                                        it.getAllWithFavoriteByArtist(artist.id)
                                    } else it.getAllByArtist(artist.id)
                                }
                                .forEach {
                                    onDeleteTrack(it.toUiTrack())
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
fun AllArtistOptionDialog(
    isFavoriteOnly: MutableState<Boolean>,
    onSelected: (allArtists: AllArtists?) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelected(null) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_switch_desc_filter_only_favorite)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    QSwitch(
                        checked = isFavoriteOnly.value,
                        onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.NEXT,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.LAST,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_NEXT,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_artists_insert_all_shuffle_next),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_LAST,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_artists_insert_all_shuffle_last),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.menu_artists_override_all_shuffle),
                        fontSize = 14.sp,
                        color = QTheme.colors.colorTextPrimary
                    )
                }
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .let {
                                        if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                    }
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.ARTIST,
                                null,
                            )
                            onSelected(null)
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
                                .let {
                                    if (isFavoriteOnly.value) it.getAllWithFavorite() else it.getAll()
                                }
                                .forEach {
                                    onDeleteTrack(it.toUiTrack())
                                    onSelected(null)
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
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onSelectGenre(null) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                DialogListItem(
                    onClick = {
                        coroutineScope.launch {
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.NEXT,
                                OrientedClassType.GENRE,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.LAST,
                                OrientedClassType.GENRE,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.GENRE,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                OrientedClassType.GENRE,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_LAST,
                                OrientedClassType.GENRE,
                                null,
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
                            val trackSourcePaths =
                                DB.getInstance(context).trackDao()
                                    .getAllByGenreName(genre.name)
                                    .map { it.track.sourcePath }
                            onNewQueue(
                                trackSourcePaths,
                                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                OrientedClassType.GENRE,
                                null,
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
                                    onDeleteTrack(it.toUiTrack())
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
    currentDropboxItemList: Triple<String, ImmutableList<FolderMetadata>, ImmutableList<FileMetadata>>,
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
            if (currentDropboxItemList.first.isEmpty() && currentDropboxItemList.second.isEmpty()) {
                onShowDropboxFolderChooser(null)
                return
            }
            var selectedHistory by remember {
                mutableStateOf<ImmutableList<FolderMetadata>>(persistentListOf())
            }
            Dialog(onDismissRequest = hideDropboxDialog) {
                var needDownloaded by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors()
                        .copy(containerColor = QTheme.colors.colorBackground),
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
                            QSwitch(
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
                        if (currentDropboxItemList.second.isEmpty() && currentDropboxItemList.third.isEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.dialog_desc_dropbox_empty_folder
                                ),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorAccent
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                items(currentDropboxItemList.second) {
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                selectedHistory = (selectedHistory.toList() + it)
                                                    .toImmutableList()
                                                onShowDropboxFolderChooser(it)
                                            }
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 12.dp
                                            )
                                            .fillMaxWidth()
                                    ) {
                                        val iconId = "id-icon"
                                        Text(
                                            text = buildAnnotatedString {
                                                appendInlineContent(iconId, "Icon")
                                                append(" ${it.name}")
                                            },
                                            inlineContent = mapOf(
                                                iconId to InlineTextContent(
                                                    Placeholder(
                                                        20.sp,
                                                        20.sp,
                                                        PlaceholderVerticalAlign.Center,
                                                    )
                                                ) {
                                                    Icon(
                                                        contentDescription = stringResource(R.string.content_description_folder),
                                                        tint = QTheme.colors.colorTextPrimary,
                                                        imageVector = Icons.Default.Folder,
                                                    )
                                                },
                                            ),
                                            fontSize = 20.sp,
                                            color = QTheme.colors.colorTextPrimary,
                                        )
                                    }
                                }
                                items(currentDropboxItemList.third) {
                                    Row(
                                        modifier = Modifier
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 12.dp
                                            )
                                            .fillMaxWidth()
                                    ) {
                                        val iconId = "id-icon"
                                        Text(
                                            text = buildAnnotatedString {
                                                appendInlineContent(iconId, "Icon")
                                                append(" ${it.name}")
                                            },
                                            inlineContent = mapOf(
                                                iconId to InlineTextContent(
                                                    Placeholder(
                                                        20.sp,
                                                        20.sp,
                                                        PlaceholderVerticalAlign.Center,
                                                    )
                                                ) {
                                                    Icon(
                                                        contentDescription = stringResource(R.string.content_description_folder),
                                                        tint = QTheme.colors.colorTextSecondary,
                                                        imageVector = Icons.Default.FilePresent,
                                                    )
                                                },
                                            ),
                                            fontSize = 20.sp,
                                            color = QTheme.colors.colorTextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val prev = {
                                selectedHistory =
                                    selectedHistory.dropLast(1).toImmutableList()
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
                                    hideDropboxDialog()
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
            Card(
                colors = CardDefaults.cardColors()
                    .copy(containerColor = QTheme.colors.colorBackground)
            ) {
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
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
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
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
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
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
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
fun EnablePauseOnCurrentTrackEndDialog(
    onCancel: () -> Unit,
    onPositive: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_message_enable_pause_on_current_track_end),
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onPositive) {
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
fun SaveQueueDialog(
    onCancel: () -> Unit,
    onPositive: (title: String) -> Unit,
) {
    var title by remember { mutableStateOf<String?>(null) }
    val currentContext = LocalContext.current
    val savedQueueNextId by remember(currentContext) {
        DB.getInstance(currentContext).savedQueueDao().getNextIdAsFlow()
    }.collectAsState(1L)
    val defaultTitle =
        stringResource(R.string.dialog_title_save_queue, savedQueueNextId)
    Dialog(onDismissRequest = onCancel) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_message_save_queue),
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary,
                )
                TextField(
                    title.orEmpty(),
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            defaultTitle,
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onPositive(title ?: defaultTitle) }) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedQueueOptionDialog(
    uiSavedQueue: UiSavedQueue,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onDelete: (savedQueueId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                DialogListItem(
                    onClick = {
                        onNewQueue(
                            uiSavedQueue.queue.map { it.track.sourcePath },
                            InsertActionType.NEXT,
                            OrientedClassType.TRACK,
                            false,
                        )
                        onDismiss()
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
                            uiSavedQueue.queue.map { it.track.sourcePath },
                            InsertActionType.LAST,
                            OrientedClassType.TRACK,
                            false,
                        )
                        onDismiss()
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
                            uiSavedQueue.queue.map { it.track.sourcePath },
                            InsertActionType.OVERRIDE,
                            OrientedClassType.TRACK,
                            false,
                        )
                        onDismiss()
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
                        onDelete(uiSavedQueue.savedQueueSummary.savedQueue.id)
                        onDismiss()
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
fun SavedQueueModifyDialog(
    uiSavedQueue: UiSavedQueue,
    onModify: (savedQueueId: Long, newTitle: String, newTrackIds: List<Long>) -> Unit,
    currentQueue: List<UiTrack>,
    onDismiss: () -> Unit,
) {
    val newTitle =
        rememberTextFieldState(initialText = uiSavedQueue.savedQueueSummary.savedQueue.title)
    var overrideWithCurrentQueue by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_title_saved_queue_modify),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = QTheme.colors.colorAccent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    state = newTitle,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary,
                    ),
                    label = {
                        Text(
                            stringResource(R.string.dialog_label_saved_queue_modify_title),
                            fontSize = 10.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    placeholder = {
                        Text(
                            newTitle.text.toString(),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                newTitle.setTextAndPlaceCursorAtEnd(
                                    uiSavedQueue.savedQueueSummary.savedQueue.title,
                                )
                            }
                        ) {
                            Text(
                                stringResource(R.string.text_edit_reset),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorPrimary,
                            )
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight,
                        )
                        .fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.dialog_saved_queue_modify_switch_title),
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = overrideWithCurrentQueue,
                        onCheckedChange = { overrideWithCurrentQueue = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onModify(
                                uiSavedQueue.savedQueueSummary.savedQueue.id,
                                newTitle.text.toString()
                                    .ifEmpty { uiSavedQueue.savedQueueSummary.savedQueue.title },
                                if (overrideWithCurrentQueue) currentQueue.map { it.id }
                                else uiSavedQueue.queue.map { it.track.id },
                            )
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.Dialogs(
    selectedTrack: UiTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedGenre: Genre?,
    selectedAllArtists: AllArtists?,
    selectedSavedQueueForOption: UiSavedQueue?,
    selectedSavedQueueForModify: UiSavedQueue?,
    currentQueue: List<UiTrack>,
    navController: NavHostController,
    isSearchActive: MutableState<Boolean>,
    currentDropboxItemList: Triple<String, ImmutableList<FolderMetadata>, ImmutableList<FileMetadata>>,
    downloadTargets: ImmutableList<String>,
    invalidateDownloadedTargets: ImmutableList<String>,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    isFavoriteOnly: MutableState<Boolean>,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAllArtists: (allArtists: AllArtists?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onSelectSavedQueueForOption: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onSelectSavedQueueForModify: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit,
    onExportLyric: (uiTrack: UiTrack) -> Unit,
    onAttachLyric: (trackId: Long) -> Unit,
    onDetachLyric: (trackId: Long) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onGenerateQueue: (
        track: UiTrack,
        actionType: InsertActionType,
        classType: OrientedClassType,
    ) -> Unit,
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
    onCancelEnablePauseOnCurrentTrackEnd: () -> Unit,
    onPositiveEnablePauseOnCurrentTrackEnd: () -> Unit,
    onSaveQueue: (title: String) -> Unit,
    showEnablePauseOnCurrentTrackEndDialog: Boolean,
    showSaveQueueDialog: MutableState<Boolean>,
    onDeleteSavedQueue: (savedQueueId: Long) -> Unit,
    onModifySavedQueue: (savedQueueId: Long, newTitle: String, newTrackIds: List<Long>) -> Unit,
) {
    val innerIsFavoriteOnly = remember { mutableStateOf(isFavoriteOnly.value) }
    LaunchedEffect(isFavoriteOnly.value) {
        innerIsFavoriteOnly.value = isFavoriteOnly.value
    }

    selectedTrack?.let { domainTrack ->
        TrackOptionDialog(
            uiTrack = domainTrack,
            navController = navController,
            onSelectTrack = onSelectTrack,
            onNewQueue = { queue, actionType, classType ->
                isSearchActive.value = false
                onNewQueue(queue, actionType, classType, null)
            },
            onGenerateQueue = { track, actionType, classType ->
                isSearchActive.value = false
                onGenerateQueue(track, actionType, classType)
            },
            onExportLyric = onExportLyric,
            onAttachLyric = onAttachLyric,
            onDetachLyric = onDetachLyric,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedAlbum?.let { album ->
        AlbumOptionDialog(
            album = album,
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelectAlbum = onSelectAlbum,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedArtist?.let { artist ->
        ArtistOptionDialog(
            artist = artist,
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelectArtist = onSelectArtist,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedAllArtists?.let {
        AllArtistOptionDialog(
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelected = onSelectAllArtists,
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
    if (showEnablePauseOnCurrentTrackEndDialog) {
        EnablePauseOnCurrentTrackEndDialog(
            onCancel = onCancelEnablePauseOnCurrentTrackEnd,
            onPositive = onPositiveEnablePauseOnCurrentTrackEnd,
        )
    }
    if (showSaveQueueDialog.value) {
        SaveQueueDialog(
            onCancel = { showSaveQueueDialog.value = false },
            onPositive = {
                showSaveQueueDialog.value = false
                onSaveQueue(it)
            }
        )
    }
    if (selectedSavedQueueForOption != null) {
        SavedQueueOptionDialog(
            uiSavedQueue = selectedSavedQueueForOption,
            onNewQueue = onNewQueue,
            onDelete = onDeleteSavedQueue,
            onDismiss = { onSelectSavedQueueForOption(null) },
        )
    }
    if (selectedSavedQueueForModify != null) {
        SavedQueueModifyDialog(
            uiSavedQueue = selectedSavedQueueForModify,
            currentQueue = currentQueue,
            onModify = onModifySavedQueue,
            onDismiss = { onSelectSavedQueueForModify(null) },
        )
    }
}