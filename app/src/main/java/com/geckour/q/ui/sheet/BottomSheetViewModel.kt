package com.geckour.q.ui.sheet

import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.Song
import com.geckour.q.util.PlaybackButton
import com.geckour.q.util.SingleLifeEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior

class BottomSheetViewModel : ViewModel() {

    internal val sheetState: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val playbackButton: SingleLifeEvent<PlaybackButton> = SingleLifeEvent()
    internal val currentQueue: SingleLifeEvent<List<Song>> = SingleLifeEvent()
    internal val currentPosition: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val addQueueToPlaylist: SingleLifeEvent<List<Song>> = SingleLifeEvent()
    internal val clearQueue: SingleLifeEvent<Unit> = SingleLifeEvent()
    internal val newSeekBarProgress: SingleLifeEvent<Float> = SingleLifeEvent()
    internal val shuffle: SingleLifeEvent<Unit> = SingleLifeEvent()
    internal val changedQueue: SingleLifeEvent<List<Song>> = SingleLifeEvent()
    internal val changedPosition: SingleLifeEvent<Int> = SingleLifeEvent()

    internal val playing: SingleLifeEvent<Boolean> = SingleLifeEvent()
    internal val playbackRatio: SingleLifeEvent<Float> = SingleLifeEvent()

    internal val repeatMode: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val changeRepeatMode: SingleLifeEvent<Unit> = SingleLifeEvent()

    init {
        sheetState.value = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun onClickQueueButton() {
        sheetState.value = when (sheetState.value) {
            BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
            else -> BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun onClickAddQueueToPlaylistButton() {
        addQueueToPlaylist.value = currentQueue.value
    }

    fun onClickClearQueueButton() {
        clearQueue.call()
    }

    fun onClickShuffleButton() {
        shuffle.call()
    }

    fun onClickRepeatButton() {
        changeRepeatMode.call()
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

    internal fun restoreState() {
        sheetState.value = sheetState.value
        currentQueue.value = currentQueue.value
        currentPosition.value = currentPosition.value
    }
}