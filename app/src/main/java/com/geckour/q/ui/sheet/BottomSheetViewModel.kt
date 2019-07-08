package com.geckour.q.ui.sheet

import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geckour.q.R
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.util.SingleLiveEvent
import com.geckour.q.util.fetchPlaylists
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BottomSheetViewModel : ViewModel() {

    internal var sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED
    internal val artworkLongClick: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleSheetState: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val playbackButton: SingleLiveEvent<PlaybackButton> = SingleLiveEvent()
    internal val currentQueue: SingleLiveEvent<List<Song>> = SingleLiveEvent()
    internal val currentPosition: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val clearQueue: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val newSeekBarProgress: SingleLiveEvent<Float> = SingleLiveEvent()
    internal val shuffle: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val changedQueue: SingleLiveEvent<List<Song>> = SingleLiveEvent()
    internal val changedPosition: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val scrollToCurrent: SingleLiveEvent<Unit> = SingleLiveEvent()

    internal val playing: SingleLiveEvent<Boolean> = SingleLiveEvent()
    internal val playbackRatio: SingleLiveEvent<Float> = SingleLiveEvent()

    internal val repeatMode: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val changeRepeatMode: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleCurrentRemain: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val touchLock: SingleLiveEvent<Boolean> = SingleLiveEvent()
    internal val share: SingleLiveEvent<Song> = SingleLiveEvent()

    var currentSong: Song? = null

    fun onLongClickArtwork(): Boolean {
        if (currentQueue.value?.isNotEmpty() == true) {
            artworkLongClick.call()
            return true
        }
        return false
    }

    fun onClickQueueButton() {
        toggleSheetState.call()
    }

    fun onClickAddQueueToPlaylistButton(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val queue = currentQueue.value ?: return@launch
            val playlists = fetchPlaylists(context)
            val binding = DialogAddQueuePlaylistBinding.inflate(LayoutInflater.from(context))
            val dialog = AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_title_add_queue_to_playlist)
                    .setMessage(R.string.dialog_desc_add_queue_to_playlist)
                    .setView(binding.root)
                    .setNegativeButton(R.string.dialog_ng) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.dialog_ok) { _, _ -> }
                    .setCancelable(true)
                    .create()
            binding.recyclerView.adapter = QueueAddPlaylistListAdapter(playlists) {
                queue.forEachIndexed { i, song ->
                    context.contentResolver.insert(
                            MediaStore.Audio.Playlists.Members
                                    .getContentUri("external", it.id),
                            ContentValues().apply {
                                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, it.memberCount + 1 + i)
                                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
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
                    val playlistId = context.contentResolver.insert(
                            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            ContentValues().apply {
                                val now = System.currentTimeMillis()
                                put(MediaStore.Audio.PlaylistsColumns.NAME, title)
                                put(MediaStore.Audio.PlaylistsColumns.DATE_ADDED, now)
                                put(MediaStore.Audio.PlaylistsColumns.DATE_MODIFIED, now)
                            })?.let { ContentUris.parseId(it) } ?: kotlin.run {
                        dialog.dismiss()
                        return@setOnClickListener
                    }
                    queue.forEachIndexed { i, song ->
                        context.contentResolver.insert(
                                MediaStore.Audio.Playlists.Members
                                        .getContentUri("external", playlistId),
                                ContentValues().apply {
                                    put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, i + 1)
                                    put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
                                })
                    }
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
        share.value = currentQueue.value?.getOrNull(currentPosition.value ?: return)
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

    internal fun reAttatch() {
        currentQueue.value = currentQueue.value
        currentPosition.value = currentPosition.value
        playing.value = playing.value
        playbackRatio.value = playbackRatio.value
        repeatMode.value = repeatMode.value
        touchLock.value = touchLock.value
    }
}