package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.databinding.DialogEditMetadataBinding

fun Context.showFileMetadataUpdateDialog(
    target: List<JoinedTrack>,
    onUpdate: (DialogEditMetadataBinding) -> Unit
) {
    val binding = DialogEditMetadataBinding.inflate(LayoutInflater.from(this), null, false).apply {
        initialTrackName =
            target.map { it.track.title }.distinct().let { if (it.size == 1) it[0] else null }
        initialTrackNameSort =
            target.map { it.track.titleSort }.distinct().let { if (it.size == 1) it[0] else null }
        initialAlbumName =
            target.map { it.album.title }.distinct().let { if (it.size == 1) it[0] else null }
        initialAlbumNameSort =
            target.map { it.album.titleSort }.distinct().let { if (it.size == 1) it[0] else null }
        initialArtistName =
            target.map { it.artist.title }.distinct().let { if (it.size == 1) it[0] else null }
        initialArtistNameSort =
            target.map { it.artist.titleSort }.distinct().let { if (it.size == 1) it[0] else null }
        initialComposerName =
            target.map { it.track.composer }.distinct().let { if (it.size == 1) it[0] else null }
        initialComposerNameSort =
            target.map { it.track.composerSort }.distinct()
                .let { if (it.size == 1) it[0] else null }
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.metadata_edit_dialog_title)
        .setMessage(R.string.metadata_edit_dialog_message)
        .setView(binding.root)
        .setPositiveButton(R.string.button_apply) { dialog, _ ->
            onUpdate(binding)
            dialog.dismiss()
        }
        .setNegativeButton(R.string.button_cancel) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}