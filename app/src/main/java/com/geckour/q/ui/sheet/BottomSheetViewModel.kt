package com.geckour.q.ui.sheet

import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.Song
import com.geckour.q.util.SingleLiveEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior

class BottomSheetViewModel : ViewModel() {

    internal var sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED
    internal val toggleSheetState: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val playbackButton: SingleLiveEvent<PlaybackButton> = SingleLiveEvent()
    internal val currentQueue: SingleLiveEvent<List<Song>> = SingleLiveEvent()
    internal val currentPosition: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val addQueueToPlaylist: SingleLiveEvent<List<Song>> = SingleLiveEvent()
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
    internal val toggleCurrentRmeain: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val touchLock: SingleLiveEvent<Boolean> = SingleLiveEvent()

    fun onClickQueueButton() {
        toggleSheetState.call()
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

    fun onClickScrollToCurrentButton() {
        scrollToCurrent.call()
    }

    fun onClickRepeatButton() {
        changeRepeatMode.call()
    }

    fun onClickToggleCurrentRemainButton() {
        toggleCurrentRmeain.call()
    }

    fun onClickTouchOffButton() {
        touchLock.value = touchLock.value?.not() ?: true
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
}