package com.geckour.q.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import kotlinx.collections.immutable.persistentListOf

@Composable
fun TrackOptionDialog(
    uiTrack: UiTrack,
    navController: NavHostController,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    fun option(@StringRes labelResId: Int, action: () -> Unit) = QOption(labelResId) {
        action()
        onDialogEvent(DialogEvent.Dismiss)
    }

    fun newQueue(actionType: InsertActionType) = onDialogEvent(
        DialogEvent.NewQueue(
            persistentListOf(uiTrack.sourcePath),
            actionType,
            OrientedClassType.TRACK
        )
    )

    fun generateQueue(actionType: InsertActionType) = onDialogEvent(
        DialogEvent.GenerateQueue(uiTrack, actionType, OrientedClassType.TRACK)
    )

    QOptionDialog(
        options = listOf(
            option(R.string.menu_insert_next) { newQueue(InsertActionType.NEXT) },
            option(R.string.menu_insert_last) { newQueue(InsertActionType.LAST) },
            option(R.string.menu_override) { newQueue(InsertActionType.OVERRIDE) },
            option(R.string.menu_insert_generated_queue_next) {
                generateQueue(InsertActionType.NEXT)
            },
            option(R.string.menu_insert_generated_queue_last) {
                generateQueue(InsertActionType.LAST)
            },
            option(R.string.menu_override_generated_queue) {
                generateQueue(InsertActionType.OVERRIDE)
            },
            option(R.string.menu_transition_to_artist) {
                navController.navigate("albums?artistId=${uiTrack.albumArtist?.id ?: uiTrack.artist.id}")
            },
            option(R.string.menu_transition_to_album) {
                navController.navigate("tracks?albumId=${uiTrack.album.id}")
            },
            option(R.string.menu_export_lyric) {
                onDialogEvent(DialogEvent.ExportLyric(uiTrack))
            },
            option(R.string.menu_attach_lyric) {
                onDialogEvent(DialogEvent.AttachLyric(uiTrack.id))
            },
            option(R.string.menu_detach_lyric) {
                onDialogEvent(DialogEvent.DetachLyric(uiTrack.id))
            },
            option(R.string.menu_delete_from_device) {
                onDialogEvent(DialogEvent.DeleteTrack(uiTrack))
            },
        ),
        onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
    )
}

@Composable
fun AlbumOptionDialog(
    album: Album,
    isFavoriteOnly: MutableState<Boolean>,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    TrackSourceOptionDialog(
        source = TrackSource.OfAlbum(album.id),
        classType = OrientedClassType.ALBUM,
        orientedShuffleLabelResIds = null,
        isFavoriteOnly = isFavoriteOnly,
        onDialogEvent = onDialogEvent,
    )
}

@Composable
fun ArtistOptionDialog(
    artist: Artist,
    isFavoriteOnly: MutableState<Boolean>,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    TrackSourceOptionDialog(
        source = TrackSource.OfArtist(artist.id),
        classType = OrientedClassType.ARTIST,
        orientedShuffleLabelResIds = OrientedShuffleLabelResIds(
            next = R.string.menu_albums_insert_all_shuffle_next,
            last = R.string.menu_albums_insert_all_shuffle_last,
            override = R.string.menu_albums_override_all_shuffle,
        ),
        isFavoriteOnly = isFavoriteOnly,
        onDialogEvent = onDialogEvent,
    )
}

@Composable
fun AllArtistOptionDialog(
    isFavoriteOnly: MutableState<Boolean>,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    TrackSourceOptionDialog(
        source = TrackSource.All,
        classType = OrientedClassType.ARTIST,
        orientedShuffleLabelResIds = OrientedShuffleLabelResIds(
            next = R.string.menu_artists_insert_all_shuffle_next,
            last = R.string.menu_artists_insert_all_shuffle_last,
            override = R.string.menu_artists_override_all_shuffle,
        ),
        isFavoriteOnly = isFavoriteOnly,
        onDialogEvent = onDialogEvent,
    )
}

@Composable
fun GenreOptionDialog(
    genre: Genre,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    TrackSourceOptionDialog(
        source = TrackSource.OfGenre(genre.name),
        classType = OrientedClassType.GENRE,
        orientedShuffleLabelResIds = null,
        isFavoriteOnly = null,
        onDialogEvent = onDialogEvent,
    )
}

private data class OrientedShuffleLabelResIds(
    @StringRes val next: Int,
    @StringRes val last: Int,
    @StringRes val override: Int,
)

@Composable
private fun TrackSourceOptionDialog(
    source: TrackSource,
    classType: OrientedClassType,
    orientedShuffleLabelResIds: OrientedShuffleLabelResIds?,
    isFavoriteOnly: MutableState<Boolean>?,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    fun option(@StringRes labelResId: Int, event: (favoriteOnly: Boolean) -> DialogEvent) =
        QOption(labelResId) {
            onDialogEvent(event(isFavoriteOnly?.value == true))
            onDialogEvent(DialogEvent.Dismiss)
        }

    fun newQueue(@StringRes labelResId: Int, actionType: InsertActionType) =
        option(labelResId) { favoriteOnly ->
            DialogEvent.NewQueueFromSource(source, favoriteOnly, actionType, classType)
        }

    QOptionDialog(
        options = listOfNotNull(
            newQueue(R.string.menu_insert_all_next, InsertActionType.NEXT),
            newQueue(R.string.menu_insert_all_last, InsertActionType.LAST),
            newQueue(R.string.menu_override_all, InsertActionType.OVERRIDE),
            orientedShuffleLabelResIds?.let {
                newQueue(it.next, InsertActionType.SHUFFLE_NEXT)
            },
            orientedShuffleLabelResIds?.let {
                newQueue(it.last, InsertActionType.SHUFFLE_LAST)
            },
            orientedShuffleLabelResIds?.let {
                newQueue(it.override, InsertActionType.SHUFFLE_OVERRIDE)
            },
            newQueue(
                R.string.menu_insert_all_simple_shuffle_next,
                InsertActionType.SHUFFLE_SIMPLE_NEXT
            ),
            newQueue(
                R.string.menu_insert_all_simple_shuffle_last,
                InsertActionType.SHUFFLE_SIMPLE_LAST
            ),
            newQueue(
                R.string.menu_override_all_simple_shuffle,
                InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
            ),
            option(R.string.menu_delete_from_device) { favoriteOnly ->
                DialogEvent.DeleteTracksFromSource(source, favoriteOnly)
            },
        ),
        onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
        isFavoriteOnly = isFavoriteOnly,
    )
}
