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
import com.geckour.q.databinding.ItemSongBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.swapped
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

    internal fun setNowPlayingPosition(index: Int?, items: List<DomainTrack>? = null) {
        submitList(
            (items ?: currentList).mapIndexed { i, song -> song.copy(nowPlaying = i == index) })
    }

    internal fun move(from: Int, to: Int) {
        if (from !in currentList.indices || to !in currentList.indices) return
        submitList(currentList.toMutableList().swapped(from, to))
    }

    private fun remove(index: Int) {
        if (index !in currentList.indices) return
        submitList(currentList.toMutableList().apply { removeAt(index) })
        viewModel.onQueueRemove(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemSongBinding) :
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
                                }, OrientedClassType.SONG
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                    R.id.menu_delete_song -> {
                        remove(adapterPosition)
                        deleteSong(binding.data)
                    }
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.queue)
        }

        fun bind() {
            val song = currentList[adapterPosition]
            binding.data = song
            binding.duration.text = song.durationString
            try {
                Glide.with(binding.thumb)
                    .load(song.album.artworkUriString)
                    .applyDefaultSettings()
                    .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }

            binding.option.apply {
                visibility = View.VISIBLE
                setOnClickListener { remove(adapterPosition) }
            }

            binding.root.setOnClickListener { onSongSelected(song, adapterPosition) }
            binding.root.setOnLongClickListener { onSongLongTapped(song) }
        }

        private fun onSongSelected(domainTrack: DomainTrack, position: Int) {
            viewModel.onRequestNavigate(domainTrack)
            viewModel.onChangeRequestedPositionInQueue(position)
        }

        private fun onSongLongTapped(domainTrack: DomainTrack): Boolean {
            viewModel.onRequestNavigate(domainTrack)
            popupMenu.apply {
                show()
            }

            return true
        }

        private fun deleteSong(domainTrack: DomainTrack?) {
            domainTrack ?: return
            viewModel.onRequestDeleteSong(domainTrack)
        }

        fun dismissPopupMenu() {
            popupMenu.dismiss()
        }
    }
}