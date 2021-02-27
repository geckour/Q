package com.geckour.q.ui.sheet

import android.app.AlertDialog
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.service.SleepTimerService
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.share.SharingActivity
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.fetchPlaylists
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

    fun onClickAddQueueToPlaylistButton(context: Context) {
        viewModelScope.launch {
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
                currentQueue.forEachIndexed { i, track ->
                    context.contentResolver.insert(MediaStore.Audio.Playlists.Members.getContentUri(
                        "external", it.id
                    ), ContentValues().apply {
                        put(
                            MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                            it.memberCount + 1 + i
                        )
                        put(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID, track.mediaId
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

    private fun createPlaylist(title: String) {
        val playlistId = getApplication<App>().contentResolver.insert(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                val now = System.currentTimeMillis()
                put(
                    MediaStore.Audio.PlaylistsColumns.NAME, title
                )
                put(
                    MediaStore.Audio.PlaylistsColumns.DATE_ADDED, now
                )
                put(
                    MediaStore.Audio.PlaylistsColumns.DATE_MODIFIED, now
                )
            })?.let { ContentUris.parseId(it) } ?: run {
            return
        }
        currentQueue.forEachIndexed { i, track ->
            getApplication<App>().contentResolver.insert(MediaStore.Audio.Playlists.Members.getContentUri(
                "external", playlistId
            ), ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, i + 1)
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, track.mediaId)
            })
        }
    }
}