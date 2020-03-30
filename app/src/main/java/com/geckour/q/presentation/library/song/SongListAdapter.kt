package com.geckour.q.presentation.library.song

import android.content.Context
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.databinding.ItemListSongBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.getArtworkUriStringFromId
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainModel
import com.geckour.q.util.toHiragana
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SongListAdapter(
    private val viewModel: MainViewModel, private val classType: OrientedClassType
) : ListAdapter<Song, SongListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<Song>() {

            override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
                oldItem == newItem
        }
    }

    fun submitList(list: List<Song>?, sortByTrackOrder: Boolean = true) {
        submitList(
            if (sortByTrackOrder) list?.sortedByTrackOrder()
            else list?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                (it.nameSort ?: it.name)?.toHiragana ?: UNKNOWN
            })
        )
    }

    private fun removeItem(songId: Long) {
        submitList(currentList.dropLastWhile { it.id == songId })
    }

    internal fun removeByTrackNum(trackNum: Int) {
        submitList(currentList.dropWhile { it.trackNum == trackNum })
    }

    internal fun clearItems() {
        submitList(null)
    }

    internal fun onNewQueue(context: Context, actionType: InsertActionType) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        viewModel.onNewQueue(
            currentList.let {
                if (sharedPreferences.ignoringEnabled) it.filter { it.ignored != true }
                else it
            }, actionType, OrientedClassType.SONG
        )
    }

    internal fun onSongDeleted(id: Long) {
        removeItem(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemListSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val shortPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedSong?.apply {
                            viewModel.onNewQueue(
                                listOf(this), when (it.itemId) {
                                    R.id.menu_insert_all_next -> InsertActionType.NEXT
                                    R.id.menu_insert_all_last -> InsertActionType.LAST
                                    R.id.menu_override_all -> InsertActionType.OVERRIDE
                                    else -> return@setOnMenuItemClickListener false
                                }, OrientedClassType.SONG
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    else -> return@setOnMenuItemClickListener false
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song)
        }

        private val longPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_transition_to_artist -> {
                        viewModel.viewModelScope.launch {
                            viewModel.selectedArtist.value = withContext((Dispatchers.IO)) {
                                binding.data?.artist?.let {
                                    DB.getInstance(binding.root.context).artistDao().findArtist(it)
                                        .firstOrNull()?.toDomainModel()
                                }
                            }
                        }
                    }
                    R.id.menu_transition_to_album -> {
                        viewModel.viewModelScope.launch {
                            viewModel.selectedAlbum.value = withContext(Dispatchers.IO) {
                                binding.data?.albumId?.let {
                                    DB.getInstance(binding.root.context).albumDao().get(it)
                                        ?.toDomainModel()
                                }
                            }
                        }
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    R.id.menu_delete_song -> deleteSong(viewModel.selectedSong)
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedSong?.apply {
                            viewModel.onNewQueue(
                                listOf(this), when (it.itemId) {
                                    R.id.menu_insert_all_next -> {
                                        InsertActionType.NEXT
                                    }
                                    R.id.menu_insert_all_last -> {
                                        InsertActionType.LAST
                                    }
                                    R.id.menu_override_all -> {
                                        InsertActionType.OVERRIDE
                                    }
                                    else -> return@setOnMenuItemClickListener false
                                }, OrientedClassType.SONG
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song_long)
        }

        fun bind() {
            val song = getItem(adapterPosition)
            binding.data = song
            binding.duration.text = song.durationString
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val uriString = DB.getInstance(binding.root.context)
                        .getArtworkUriStringFromId(song.albumId)
                        .orDefaultForModel
                    withContext(Dispatchers.Main) {
                        Glide.with(binding.thumb)
                            .load(uriString)
                            .applyDefaultSettings()
                            .into(binding.thumb)
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            binding.root.setOnClickListener { onSongSelected(song) }
            binding.root.setOnLongClickListener { onSongLongTapped(song) }
            if (classType == OrientedClassType.PLAYLIST) {
                binding.option.visibility = View.VISIBLE
                binding.option.setOnClickListener {
                    song.trackNum?.apply { removeFromPlaylist(this) }
                }
            }
        }

        private fun deleteSong(song: Song?) {
            if (song == null) return
            viewModel.songToDelete.value = song
        }

        private fun removeFromPlaylist(playOrder: Int) {
            viewModel.onRequestRemoveSongFromPlaylist(playOrder)
        }

        private fun onSongSelected(song: Song) {
            viewModel.onRequestNavigate(song)
            shortPopupMenu.show()
            shortPopupMenu.menu.findItem(R.id.menu_ignore).title =
                binding.root.context.getString(
                    if (binding.data?.ignored == true)
                        R.string.menu_ignore_to_false
                    else R.string.menu_ignore_to_true
                )
        }

        private fun onSongLongTapped(song: Song): Boolean {
            viewModel.onRequestNavigate(song)
            longPopupMenu.show()
            longPopupMenu.menu.findItem(R.id.menu_ignore).title =
                binding.root.context.getString(
                    if (binding.data?.ignored == true)
                        R.string.menu_ignore_to_false
                    else R.string.menu_ignore_to_true
                )

            return true
        }

        private fun toggleIgnored() {
            binding.data?.id?.also { trackId ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    DB.getInstance(binding.root.context).trackDao().apply {
                        val ignored = when (this.get(trackId)?.ignored ?: Bool.FALSE) {
                            Bool.TRUE -> Bool.FALSE
                            Bool.FALSE -> Bool.TRUE
                            Bool.UNDEFINED -> Bool.UNDEFINED
                        }.apply { Timber.d("qgeck saved ignored value: $this") }
                        setIgnored(trackId, ignored)
                        withContext(Dispatchers.Main) {
                            binding.data =
                                binding.data?.let { it.copy(ignored = it.ignored?.not()) }
                        }
                    }
                }
            }
        }
    }
}