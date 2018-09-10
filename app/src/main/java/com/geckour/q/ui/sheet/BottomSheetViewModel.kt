package com.geckour.q.ui.sheet

import android.arch.lifecycle.ViewModel
import android.support.design.widget.BottomSheetBehavior
import com.geckour.q.domain.model.Song
import com.geckour.q.util.SingleLifeEvent

class BottomSheetViewModel : ViewModel() {

    enum class PlaybackButton {
        PLAY_OR_PAUSE,
        NEXT,
        PREV,
        FF,
        REWIND
    }

    internal val sheetState: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val playbackButton: SingleLifeEvent<PlaybackButton> = SingleLifeEvent()
    internal val currentQueue: SingleLifeEvent<List<Song>> = SingleLifeEvent()
    internal val currentPosition: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val newSeekBarProgress: SingleLifeEvent<Float> = SingleLifeEvent()
    internal val shuffle: SingleLifeEvent<Unit> = SingleLifeEvent()
    internal val changedQueue: SingleLifeEvent<List<Song>> = SingleLifeEvent()
    internal val changedPosition: SingleLifeEvent<Int> = SingleLifeEvent()

    internal var playing: SingleLifeEvent<Boolean> = SingleLifeEvent()
    internal var playbackRatio: SingleLifeEvent<Float> = SingleLifeEvent()

    init {
        sheetState.value = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun onClickQueueButton() {
        sheetState.value = when (sheetState.value) {
            BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
            else -> BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun onClickClearQueueButton() {
        val newQueue =
                if (playing.value == true) {
                    currentPosition.value?.let {
                        currentQueue.value?.get(it)?.let { listOf(it) }
                    } ?: emptyList()
                } else emptyList()

        currentPosition.value = 0
        currentQueue.value = newQueue

        changedPosition.value = 0
        changedQueue.value = newQueue
    }

    fun onClickShuffleButton() {
        shuffle.call()
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