package com.geckour.q.ui.sheet

import android.app.AlertDialog
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.SingleLiveEvent
import com.geckour.q.util.fetchPlaylists
import com.geckour.q.util.toDomainModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BottomSheetViewModel(application: Application) : AndroidViewModel(application) {

    internal var sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED
    internal val artworkLongClick: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleSheetState: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val playbackButton: SingleLiveEvent<PlaybackButton> = SingleLiveEvent()
    internal var currentQueue: List<Song> = emptyList()
    internal var currentPosition = -1
    internal val clearQueue: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val newSeekBarProgress: SingleLiveEvent<Float> = SingleLiveEvent()
    internal val shuffle: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val scrollToCurrent: SingleLiveEvent<Unit> = SingleLiveEvent()

    internal val changeRepeatMode: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleCurrentRemain: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val touchLock: SingleLiveEvent<Boolean> = SingleLiveEvent()
    internal val share: SingleLiveEvent<Song> = SingleLiveEvent()

    val currentSong: Song? get() = currentQueue.getOrNull(currentPosition)

    fun onLongClickArtwork(): Boolean {
        if (currentQueue.isNotEmpty()) {
            artworkLongClick.call()
            return true
        }
        return false
    }

    fun onClickQueueButton() {
        toggleSheetState.call()
    }

    fun onClickAddQueueToPlaylistButton(context: Context) {
        viewModelScope.launch {
            val playlists = fetchPlaylists(context)
            val binding = DialogAddQueuePlaylistBinding.inflate(LayoutInflater.from(context))
            val dialog =
                    AlertDialog.Builder(context).setTitle(R.string.dialog_title_add_queue_to_playlist)
                            .setMessage(R.string.dialog_desc_add_queue_to_playlist).setView(binding.root)
                            .setNegativeButton(R.string.dialog_ng) { dialog, _ ->
                                dialog.dismiss()
                            }.setPositiveButton(R.string.dialog_ok) { _, _ -> }
                            .setCancelable(true)
                            .create()
            binding.recyclerView.adapter = QueueAddPlaylistListAdapter(playlists) {
                currentQueue.forEachIndexed { i, song ->
                    context.contentResolver.insert(MediaStore.Audio.Playlists.Members.getContentUri(
                            "external", it.id
                    ), ContentValues().apply {
                        put(
                                MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                                it.memberCount + 1 + i
                        )
                        put(
                                MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId
                        )
                    })
                }
                dialog.dismiss()
            }
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = binding.editText.text?.toString()
                if (title.isNullOrBlank()) {
                    // TODO: エラーメッセージ表示
                } else {
                    createPlaylist(title)
                    dialog.dismiss()
                }
            }
        }
    }

    fun onClickClearQueueButton() {
        clearQueue.call()
    }

    fun onClickShuffleButton() {
        shuffle.call()
    }

    fun onClickScrollToCurrentButton() {
        scrollToCurrent.call()
    }

    fun onClickRepeatButton() {
        changeRepeatMode.call()
    }

    fun onClickToggleCurrentRemainButton() {
        toggleCurrentRemain.call()
    }

    fun onClickTouchOffButton() {
        touchLock.value = touchLock.value?.not() ?: true
    }

    fun onClickShareButton() {
        share.value = currentSong
    }

    fun onPlayOrPause() {
        playbackButton.value = PlaybackButton.PLAY_OR_PAUSE
    }

    fun onNext() {
        playbackButton.value = PlaybackButton.NEXT
    }

    fun onPrev() {
        playbackButton.value = PlaybackButton.PREV
    }

    fun onFF(): Boolean {
        playbackButton.value = PlaybackButton.FF
        return true
    }

    fun onRewind(): Boolean {
        playbackButton.value = PlaybackButton.REWIND
        return true
    }

    internal fun reAttach() {
        touchLock.value = touchLock.value
    }

    internal fun onTransitionToArtist(mainViewModel: MainViewModel) = viewModelScope.launch {
        mainViewModel.selectedArtist.value = withContext((Dispatchers.IO)) {
            currentSong?.artist?.let {
                DB.getInstance(getApplication()).artistDao().findArtist(it)
                        .firstOrNull()?.toDomainModel()
            }
        }
    }

    internal fun onTransitionToAlbum(mainViewModel: MainViewModel) = viewModelScope.launch {
        mainViewModel.selectedAlbum.value = withContext(Dispatchers.IO) {
            currentSong?.albumId?.let {
                DB.getInstance(getApplication()).albumDao().get(it)
                        ?.toDomainModel()
            }
        }
    }

    private fun createPlaylist(title: String) {
        val playlistId = getApplication<App>().contentResolver.insert(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    val now = System.currentTimeMillis()
                    put(MediaStore.Audio.PlaylistsColumns.NAME, title)
                    put(MediaStore.Audio.PlaylistsColumns.DATE_ADDED, now)
                    put(MediaStore.Audio.PlaylistsColumns.DATE_MODIFIED, now)
                }
        )?.let { ContentUris.parseId(it) } ?: run {
            return
        }
        currentQueue.forEachIndexed { i, song ->
            getApplication<App>().contentResolver
                    .insert(MediaStore.Audio.Playlists.Members.getContentUri(
                            "external",
                            playlistId
                    ), ContentValues().apply {
                        put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, i + 1)
                        put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
                    })
        }
    }
}