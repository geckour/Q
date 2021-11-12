package com.geckour.q.ui.sheet

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.service.SleepTimerService
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.showCurrentRemain

class BottomSheetViewModel(sharedPreferences: SharedPreferences) : ViewModel() {

    companion object {
        internal const val PREF_KEY_SHOW_LOCK_TOUCH_QUEUE = "pref_key_lock_touch_queue"
    }

    var playing = MutableLiveData(false)
    internal val toggleSheetState = MutableLiveData<Unit>()
    private val _showCurrentRemain = MutableLiveData<Boolean>()
    internal val showCurrentRemain: LiveData<Boolean> = _showCurrentRemain
    private val _scrollToCurrent = MutableLiveData<Boolean>()
    internal val scrollToCurrent: LiveData<Boolean> = _scrollToCurrent.distinctUntilChanged()

    init {
        _showCurrentRemain.value = sharedPreferences.showCurrentRemain
    }

    fun onClickQueueButton() {
        toggleSheetState.value = Unit
    }

    fun onClickTimeRight() {
        _showCurrentRemain.value = _showCurrentRemain.value?.not()
    }

    fun onClickScrollToCurrentButton() {
        _scrollToCurrent.value = true
    }

    internal fun onNewIndex(
        context: Context,
        currentDomainTrack: DomainTrack?,
        currentPlaybackPosition: Long = 0L
    ) {
        SleepTimerService.notifyTrackChanged(
            context,
            currentDomainTrack ?: return,
            currentPlaybackPosition
        )
    }

    internal fun onTransitionToArtist(
        mainViewModel: MainViewModel,
        currentDomainTrack: DomainTrack?
    ) {
        mainViewModel.selectedArtist.value = currentDomainTrack?.artist
    }

    internal fun onTransitionToAlbum(
        mainViewModel: MainViewModel,
        currentDomainTrack: DomainTrack?
    ) {
        mainViewModel.selectedAlbum.value = currentDomainTrack?.album
    }

    internal fun onScrollToCurrentInvoked() {
        _scrollToCurrent.value = false
    }
}