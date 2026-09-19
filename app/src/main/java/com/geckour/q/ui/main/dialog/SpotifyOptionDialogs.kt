package com.geckour.q.ui.main.dialog

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.geckour.q.R
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.ui.component.QOption
import com.geckour.q.ui.component.QOptionDialog
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.isSpotifyInstalled

@Composable
fun SpotifyTrackOptionDialog(
    track: SpotifyTrack,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    QOptionDialog(
        options = insertOptions(
            labelResIds = InsertLabelResIds.Single,
            onSelect = { actionType ->
                onDialogEvent(DialogEvent.AddSpotifyTrack(track, actionType))
                onDialogEvent(DialogEvent.Dismiss)
            },
        ) + spotifyLinkOption(track.uri, onDialogEvent),
        onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
    )
}

@Composable
fun SpotifyContainerOptionDialog(
    container: SpotifyContainer,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val onSelect: (actionType: InsertActionType, classType: OrientedClassType) -> Unit =
        { actionType, classType ->
            onDialogEvent(DialogEvent.AddSpotifyContainer(container, actionType, classType))
            onDialogEvent(DialogEvent.Dismiss)
        }
    val onSelectTrackOriented: (actionType: InsertActionType) -> Unit = { actionType ->
        onSelect(actionType, OrientedClassType.TRACK)
    }

    QOptionDialog(
        options = insertOptions(InsertLabelResIds.All, onSelectTrackOriented) +
                orientedShuffleOptions(container.kind, onSelect) +
                simpleShuffleOptions(onSelectTrackOriented) +
                spotifyLinkOption(container.uri, onDialogEvent),
        onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
    )
}

private class InsertLabelResIds(
    @StringRes val next: Int,
    @StringRes val last: Int,
    @StringRes val override: Int,
) {

    companion object {

        val Single = InsertLabelResIds(
            next = R.string.menu_insert_next,
            last = R.string.menu_insert_last,
            override = R.string.menu_override,
        )

        val All = InsertLabelResIds(
            next = R.string.menu_insert_all_next,
            last = R.string.menu_insert_all_last,
            override = R.string.menu_override_all,
        )
    }
}

private fun insertOptions(
    labelResIds: InsertLabelResIds,
    onSelect: (actionType: InsertActionType) -> Unit,
): List<QOption> = listOf(
    QOption(labelResIds.next) { onSelect(InsertActionType.NEXT) },
    QOption(labelResIds.last) { onSelect(InsertActionType.LAST) },
    QOption(labelResIds.override) { onSelect(InsertActionType.OVERRIDE) },
)

private fun orientedShuffleOptions(
    kind: SpotifyContainer.Kind,
    onSelect: (actionType: InsertActionType, classType: OrientedClassType) -> Unit,
): List<QOption> {
    val albumOriented = listOf(
        QOption(R.string.menu_albums_insert_all_shuffle_next) {
            onSelect(InsertActionType.SHUFFLE_NEXT, OrientedClassType.ALBUM)
        },
        QOption(R.string.menu_albums_insert_all_shuffle_last) {
            onSelect(InsertActionType.SHUFFLE_LAST, OrientedClassType.ALBUM)
        },
        QOption(R.string.menu_albums_override_all_shuffle) {
            onSelect(InsertActionType.SHUFFLE_OVERRIDE, OrientedClassType.ALBUM)
        },
    )
    val artistOriented = listOf(
        QOption(R.string.menu_artists_insert_all_shuffle_next) {
            onSelect(InsertActionType.SHUFFLE_NEXT, OrientedClassType.ARTIST)
        },
        QOption(R.string.menu_artists_insert_all_shuffle_last) {
            onSelect(InsertActionType.SHUFFLE_LAST, OrientedClassType.ARTIST)
        },
        QOption(R.string.menu_artists_override_all_shuffle) {
            onSelect(InsertActionType.SHUFFLE_OVERRIDE, OrientedClassType.ARTIST)
        },
    )

    return when (kind) {
        SpotifyContainer.Kind.ALBUM -> emptyList()
        SpotifyContainer.Kind.ARTIST -> albumOriented
        SpotifyContainer.Kind.PLAYLIST, SpotifyContainer.Kind.SAVED -> albumOriented + artistOriented
    }
}

private fun simpleShuffleOptions(
    onSelect: (actionType: InsertActionType) -> Unit,
): List<QOption> = listOf(
    QOption(R.string.menu_insert_all_simple_shuffle_next) {
        onSelect(InsertActionType.SHUFFLE_SIMPLE_NEXT)
    },
    QOption(R.string.menu_insert_all_simple_shuffle_last) {
        onSelect(InsertActionType.SHUFFLE_SIMPLE_LAST)
    },
    QOption(R.string.menu_override_all_simple_shuffle) {
        onSelect(InsertActionType.SHUFFLE_SIMPLE_OVERRIDE)
    },
)

@Composable
private fun spotifyLinkOption(
    uri: String,
    onDialogEvent: (event: DialogEvent) -> Unit,
): QOption {
    val isInstalled = isSpotifyInstalled(LocalContext.current)

    return QOption(
        if (isInstalled) R.string.spotify_menu_open else R.string.spotify_menu_get_free
    ) {
        onDialogEvent(DialogEvent.OpenInSpotify(uri))
        onDialogEvent(DialogEvent.Dismiss)
    }
}
