package com.geckour.q.ui.sheet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.databinding.ItemTrackBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.orDefaultForModel
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

    internal fun getItemIds(): List<Long> = currentList.map { it.id }

    internal fun getItemsAfter(start: Int): List<DomainTrack> =
        if (start in currentList.indices) currentList.subList(start, currentList.size)
        else emptyList()

    internal fun submitNewQueue(currentIndex: Int?, items: List<DomainTrack>? = null) {
        submitList(
            (items ?: currentList).mapIndexed { i, track ->
                track.copy(
                    nowPlaying = i == currentIndex,
                    discNum = null,
                    trackNum = i + 1
                )
            }
        )
    }

    private fun remove(index: Int) {
        if (index !in currentList.indices) return
        submitList(currentList.toMutableList().apply { removeAt(index) })
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
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_transition_to_artist -> {
                        viewModel.selectedArtist.value = binding.data?.artist
                    }
                    R.id.menu_transition_to_album -> {
                        viewModel.selectedAlbum.value = binding.data?.album
                    }
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
                                }, OrientedClassType.TRACK
                            )
                        } ?: return@setOnMenuItemClickListener false
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
            val track = currentList[adapterPosition]
            binding.data = track
            binding.duration.text = track.durationString
            try {
                Glide.with(binding.thumb)
                    .load(track.album.artworkUriString.orDefaultForModel)
                    .applyDefaultSettings()
                    .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }

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