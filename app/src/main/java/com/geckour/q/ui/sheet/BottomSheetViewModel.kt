package com.geckour.q.ui.sheet

import android.app.Application
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.service.SleepTimerService
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.share.SharingActivity
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.showCurrentRemain
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BottomSheetViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        internal const val PREF_KEY_SHOW_LOCK_TOUCH_QUEUE = "pref_key_lock_touch_queue"
    }

    var playing = MutableLiveData(false)
    internal var sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED
    private val _artworkLongClick = MutableLiveData<Boolean>()
    internal val artworkLongClick: LiveData<Boolean> = _artworkLongClick.distinctUntilChanged()
    internal val toggleSheetState = MutableLiveData<Unit>()
    internal var currentQueue: List<DomainTrack> = emptyList()
        set(value) {
            currentDomainTrack.value = value.getOrNull(currentIndex)
            playerActive.value = value.isNotEmpty()
            field = value
        }
    internal var currentIndex: Int = -1
        set(value) {
            currentDomainTrack.value = currentQueue.getOrNull(value)
            field = value
        }
    internal var playbackPosition: Long = 0
    private val _showCurrentRemain = MutableLiveData<Boolean>()
    internal val showCurrentRemain: LiveData<Boolean> = _showCurrentRemain
    private val _scrollToCurrent = MutableLiveData<Boolean>()
    internal val scrollToCurrent: LiveData<Boolean> = _scrollToCurrent.distinctUntilChanged()
    private val _touchLock = MutableLiveData<Boolean>()
    val touchLock: LiveData<Boolean> = _touchLock.distinctUntilChanged()
    val playerActive = MutableLiveData(false)

    val currentDomainTrack = MutableLiveData<DomainTrack>()

    private var updateArtworkJob: Job = Job()

    init {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        _touchLock.value = sharedPreferences.getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)
        _showCurrentRemain.value = sharedPreferences.showCurrentRemain
    }

    fun onLongClickArtwork(): Boolean {
        if (currentQueue.isNotEmpty()) {
            _artworkLongClick.value = true
            return true
        }
        return false
    }

    fun onClickQueueButton() {
        toggleSheetState.value = Unit
    }

    fun onClickTimeRight() {
        _showCurrentRemain.value = _showCurrentRemain.value?.not()
    }

    fun onClickShareButton() {
        getApplication<Application>().startActivity(
            SharingActivity.getIntent(getApplication(), currentDomainTrack.value ?: return)
        )
    }

    fun onClickScrollToCurrentButton() {
        _scrollToCurrent.value = true
    }

    fun onClickTouchLockButton() {
        _touchLock.value = _touchLock.value?.not() ?: true
    }

    internal fun onNewIndex(index: Int) {
        currentIndex = index
        SleepTimerService.notifyTrackChanged(
            getApplication(),
            currentDomainTrack.value ?: return,
            playbackPosition
        )
    }

    internal fun reAttach() {
        _touchLock.value = touchLock.value
    }

    internal fun onTransitionToArtist(mainViewModel: MainViewModel) {
        mainViewModel.selectedArtist.value = currentDomainTrack.value?.artist
    }

    internal fun onTransitionToAlbum(mainViewModel: MainViewModel) {
        mainViewModel.selectedAlbum.value = currentDomainTrack.value?.album
    }

    internal fun setArtwork(imageView: ImageView) {
        updateArtworkJob.cancel()
        updateArtworkJob = viewModelScope.launch {
            val drawable = when (val track = currentDomainTrack.value) {
                null -> null
                else -> {
                    withContext(Dispatchers.IO) {
                        Glide.with(imageView)
                            .asDrawable()
                            .load(track.thumbUriString.orDefaultForModel)
                            .applyDefaultSettings()
                            .submit()
                            .get()
                    }
                }
            }

            imageView.setImageDrawable(drawable)
        }
    }

    internal fun onArtworkDialogShown() {
        _artworkLongClick.value = false
    }

    internal fun onScrollToCurrentInvoked() {
        _scrollToCurrent.value = false
    }
}