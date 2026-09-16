package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.toUiTrack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun TrackOptionDialog(
    uiTrack: UiTrack,
    navController: NavHostController,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                DialogListItem(
                    onClick = {
                        onDialogEvent(
                            DialogEvent.NewQueue(
                                persistentListOf(uiTrack.sourcePath),
                                InsertActionType.NEXT,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(
                            DialogEvent.NewQueue(
                                persistentListOf(uiTrack.sourcePath),
                                InsertActionType.LAST,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(
                            DialogEvent.NewQueue(
                                persistentListOf(uiTrack.sourcePath),
                                InsertActionType.OVERRIDE,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(
                            DialogEvent.GenerateQueue(
                                uiTrack,
                                InsertActionType.NEXT,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(
                            DialogEvent.GenerateQueue(
                                uiTrack,
                                InsertActionType.LAST,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(
                            DialogEvent.GenerateQueue(
                                uiTrack,
                                InsertActionType.OVERRIDE,
                                OrientedClassType.TRACK
                            )
                        )
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.ExportLyric(uiTrack))
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.AttachLyric(uiTrack.id))
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.DetachLyric(uiTrack.id))
                        onDialogEvent(DialogEvent.Dismiss)
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
                        onDialogEvent(DialogEvent.DeleteTrack(uiTrack))
                        onDialogEvent(DialogEvent.Dismiss)
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
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.NEXT,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.LAST,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.OVERRIDE,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_LAST,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                    OrientedClassType.ALBUM,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                                    onDialogEvent(DialogEvent.DeleteTrack(it.toUiTrack()))
                                    onDialogEvent(DialogEvent.Dismiss)
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
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                                    onDialogEvent(DialogEvent.DeleteTrack(it.toUiTrack()))
                                    onDialogEvent(DialogEvent.Dismiss)
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
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_LAST,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                    OrientedClassType.ARTIST,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                                    onDialogEvent(DialogEvent.DeleteTrack(it.toUiTrack()))
                                    onDialogEvent(DialogEvent.Dismiss)
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
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.NEXT,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.LAST,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.OVERRIDE,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_LAST,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                            onDialogEvent(
                                DialogEvent.NewQueue(
                                    trackSourcePaths,
                                    InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                    OrientedClassType.GENRE,
                                    null,
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
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
                                    onDialogEvent(DialogEvent.DeleteTrack(it.toUiTrack()))
                                    onDialogEvent(DialogEvent.Dismiss)
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
