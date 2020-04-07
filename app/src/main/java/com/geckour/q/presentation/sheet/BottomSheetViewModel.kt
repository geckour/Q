package com.geckour.q.presentation.sheet

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
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.presentation.share.SharingActivity
import com.geckour.q.service.SleepTimerService
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.fetchPlaylists
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.toDomainModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BottomSheetViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        internal const val PREF_KEY_SHOW_LOCK_TOUCH_QUEUE = "pref_key_lock_touch_queue"
    }

    var playing = MutableLiveData<Boolean>(false)
    internal var sheetState: Int = BottomSheetBehavior.STATE_COLLAPSED
    private val _artworkLongClick = MutableLiveData<Boolean>()
    internal val artworkLongClick: LiveData<Boolean> = _artworkLongClick.distinctUntilChanged()
    internal val toggleSheetState = MutableLiveData<Unit>()
    internal var currentQueue: List<Song> = emptyList()
    internal var currentPosition: Int = -1
    internal var playbackRatio: Float = 0f
    private val _toggleCurrentRemain = MutableLiveData<Boolean>()
    internal val toggleCurrentRemain: LiveData<Boolean> =
        _toggleCurrentRemain.distinctUntilChanged()
    private val _scrollToCurrent = MutableLiveData<Boolean>()
    internal val scrollToCurrent: LiveData<Boolean> = _scrollToCurrent.distinctUntilChanged()
    private val _touchLock = MutableLiveData<Boolean>()
    val touchLock: LiveData<Boolean> = _touchLock.distinctUntilChanged()
    val isQueueNotEmpty: Boolean get() = currentQueue.isEmpty().not()

    val currentSong: Song? get() = currentQueue.getOrNull(currentPosition)

    private var updateArtworkJob: Job = Job()

    init {
        _touchLock.value = PreferenceManager.getDefaultSharedPreferences(application)
            .getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)
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

    fun onClickTimeRight() {
        _toggleCurrentRemain.value = true
    }

    fun onClickShareButton() {
        getApplication<Application>().startActivity(
            SharingActivity.getIntent(getApplication(), currentSong ?: return)
        )
    }

    fun onClickScrollToCurrentButton() {
        _scrollToCurrent.value = true
    }

    fun onClickTouchLockButton() {
        _touchLock.value = _touchLock.value?.not() ?: true
    }

    internal fun onNewPosition(position: Int) {
        currentPosition = position
        SleepTimerService.notifyTrackChanged(getApplication(), currentSong ?: return, playbackRatio)
    }

    internal fun reAttach() {
        _touchLock.value = touchLock.value
    }

    internal fun onTransitionToArtist(mainViewModel: MainViewModel) = viewModelScope.launch {
        mainViewModel.selectedArtist.value = withContext((Dispatchers.IO)) {
            currentSong?.artist?.let {
                DB.getInstance(getApplication())
                    .artistDao()
                    .getAllByTitle(it)
                    .firstOrNull()
                    ?.toDomainModel()
            }
        }
    }

    internal fun onTransitionToAlbum(mainViewModel: MainViewModel) = viewModelScope.launch {
        mainViewModel.selectedAlbum.value = withContext(Dispatchers.IO) {
            currentSong?.albumId?.let {
                DB.getInstance(getApplication()).albumDao().get(it)?.toDomainModel()
            }
        }
    }

    internal fun setArtwork(imageView: ImageView) {
        updateArtworkJob.cancel()
        updateArtworkJob = viewModelScope.launch(Dispatchers.IO) {
            val drawable = currentSong.let {
                when (it) {
                    null -> null
                    else -> {
                        Glide.with(imageView)
                            .asDrawable()
                            .load(it.thumbUriString.orDefaultForModel)
                            .applyDefaultSettings()
                            .submit()
                            .get()
                    }
                }
            }
            withContext(Dispatchers.Main) { imageView.setImageDrawable(drawable) }
        }
    }

    internal fun onArtworkDialogShown() {
        _artworkLongClick.value = false
    }

    internal fun onCurrentRemainToggled() {
        _toggleCurrentRemain.value = false
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
        currentQueue.forEachIndexed { i, song ->
            getApplication<App>().contentResolver.insert(MediaStore.Audio.Playlists.Members.getContentUri(
                "external", playlistId
            ), ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, i + 1)
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
            })
        }
    }
}