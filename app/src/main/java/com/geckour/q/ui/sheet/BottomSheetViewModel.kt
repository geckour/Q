package com.geckour.q.ui.sheet

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.geckour.q.R
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.formatPattern
import com.geckour.q.util.getTempArtworkUriString
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

    fun onClickShareButton(context: Context, domainTrack: DomainTrack?) {
        domainTrack ?: return
        val subject = domainTrack.toTrackInfo().getSharingSubject(context.formatPattern)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, subject)
            domainTrack.getTempArtworkUriString(context)
                ?.toUri()
                ?.let {
                    putExtra(Intent.EXTRA_STREAM, it)
                    setType("image/png")
                } ?: run { setType("text/plain") }
        }
        PendingIntent.getActivity(
            context,
            0,
            Intent.createChooser(intent, context.getString(R.string.share_chooser_title)),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).send()
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