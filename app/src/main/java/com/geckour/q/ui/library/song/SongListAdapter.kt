package com.geckour.q.ui.library.song

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.databinding.ItemSongBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.orDefaultForModel
import kotlinx.coroutines.launch
import timber.log.Timber

class SongListAdapter(
    private val viewModel: MainViewModel, private val classType: OrientedClassType
) : ListAdapter<DomainTrack, SongListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<DomainTrack>() {

            override fun areItemsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem == newItem
        }
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
        ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val shortPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedDomainTrack?.apply {
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
                        viewModel.selectedArtist.value = binding.data?.artist
                    }
                    R.id.menu_transition_to_album -> {
                        viewModel.selectedAlbum.value = binding.data?.album
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    R.id.menu_delete_song -> deleteSong(viewModel.selectedDomainTrack)
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedDomainTrack?.apply {
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
            viewModel.viewModelScope.launch {
                try {
                    Glide.with(binding.thumb)
                        .load(song.artworkUriString.orDefaultForModel)
                        .applyDefaultSettings()
                        .into(binding.thumb)
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

        private fun deleteSong(domainTrack: DomainTrack?) {
            domainTrack ?: return
            viewModel.onRequestDeleteSong(domainTrack)
        }

        private fun removeFromPlaylist(playOrder: Int) {
            viewModel.onRequestRemoveSongFromPlaylist(playOrder)
        }

        private fun onSongSelected(domainTrack: DomainTrack) {
            viewModel.onRequestNavigate(domainTrack)
            shortPopupMenu.show()
            shortPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.getString(
                if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                else R.string.menu_ignore_to_true
            )
        }

        private fun onSongLongTapped(domainTrack: DomainTrack): Boolean {
            viewModel.onRequestNavigate(domainTrack)
            longPopupMenu.show()
            longPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.getString(
                if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                else R.string.menu_ignore_to_true
            )

            return true
        }

        private fun toggleIgnored() {
            binding.data?.id?.also { trackId ->
                viewModel.viewModelScope.launch {
                    DB.getInstance(binding.root.context).trackDao().apply {
                        val ignored = when (this.get(trackId)?.track?.ignored ?: Bool.FALSE) {
                            Bool.TRUE -> Bool.FALSE
                            Bool.FALSE -> Bool.TRUE
                            Bool.UNDEFINED -> Bool.UNDEFINED
                        }.apply { Timber.d("qgeck saved ignored value: $this") }
                        setIgnored(trackId, ignored)

                        binding.data = binding.data?.let { it.copy(ignored = it.ignored?.not()) }
                    }
                }
            }
        }
    }
}