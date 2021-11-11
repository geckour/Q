package com.geckour.q.ui.sheet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemTrackBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.loadOrDefault
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import timber.log.Timber

class QueueListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<DomainTrack, QueueListAdapter.ViewHolder>(diffUtil) {

    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<DomainTrack>() {

            override fun areItemsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem == newItem
        }
    }

    internal val currentItem: DomainTrack? get() = currentList.firstOrNull { it.nowPlaying }
    internal val currentIndex: Int get() = currentList.indexOfFirst { it.nowPlaying }

    internal fun getItemsAfter(start: Int): List<DomainTrack> =
        if (start in currentList.indices) currentList.subList(start, currentList.size)
        else emptyList()

    private fun remove(index: Int) {
        if (index !in currentList.indices) return
        viewModel.onQueueRemove(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_transition_to_artist -> {
                        viewModel.selectedArtist.value = binding.data?.artist
                    }
                    R.id.menu_transition_to_album -> {
                        viewModel.selectedAlbum.value = binding.data?.album
                    }
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedDomainTrack?.apply {
                            viewModel.onNewQueue(
                                listOf(this), when (menuItem.itemId) {
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
                                }, OrientedClassType.TRACK
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                    R.id.menu_edit_metadata -> {
                        viewModel.viewModelScope.launch {
                            val db = DB.getInstance(binding.root.context)

                            viewModel.onLoadStateChanged(true)
                            val tracks = currentList.mapNotNull { db.trackDao().get(it.id) }
                            viewModel.onLoadStateChanged(false)

                            binding.root.context.showFileMetadataUpdateDialog(tracks) { binding ->
                                viewModel.viewModelScope.launch {
                                    viewModel.onLoadStateChanged(true)
                                    binding.updateFileMetadata(binding.root.context, db, tracks)
                                    viewModel.onLoadStateChanged(false)
                                }
                            }
                        }
                    }
                    R.id.menu_delete_track -> {
                        remove(adapterPosition)
                        deleteTrack(binding.data)
                    }
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.queue)
        }

        fun bind() {
            val track = currentList[adapterPosition].copy(
                discNum = null,
                trackNum = adapterPosition + 1
            )
            binding.data = track
            binding.nowPlaying = track.nowPlaying
            binding.duration.text = track.durationString
            binding.thumb.loadOrDefault(track.album.artworkUriString)

            binding.option.apply {
                visibility = View.VISIBLE
                setOnClickListener { remove(adapterPosition) }
            }

            binding.root.setOnClickListener { onTrackSelected(track, adapterPosition) }
            binding.root.setOnLongClickListener { onTrackLongTapped(track) }
        }

        private fun onTrackSelected(domainTrack: DomainTrack, position: Int) {
            viewModel.onRequestNavigate(domainTrack)
            viewModel.onChangeRequestedPositionInQueue(position)
        }

        private fun onTrackLongTapped(domainTrack: DomainTrack): Boolean {
            viewModel.onRequestNavigate(domainTrack)
            popupMenu.apply {
                show()
            }

            return true
        }

        private fun deleteTrack(domainTrack: DomainTrack?) {
            domainTrack ?: return
            viewModel.deleteTrack(domainTrack)
        }

        fun dismissPopupMenu() {
            popupMenu.dismiss()
        }
    }
}