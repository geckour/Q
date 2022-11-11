package com.geckour.q.ui.sheet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.databinding.ItemTrackBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.loadOrDefault

class QueueListAdapter(
    private val onNewQueue: (actionType: InsertActionType, track: DomainTrack) -> Unit,
    private val onEditMetadata: (tracks: List<DomainTrack>) -> Unit,
    private val onQueueRemove: (position: Int) -> Unit,
    private val onClickArtist: (artist: Artist) -> Unit,
    private val onClickAlbum: (album: Album) -> Unit,
    private val onClickTrack: (track: DomainTrack) -> Unit,
    private val onChangeCurrentPosition: (position: Int) -> Unit,
    private val onDeleteTrack: (track: DomainTrack) -> Unit
) : ListAdapter<DomainTrack, QueueListAdapter.ViewHolder>(diffUtil) {

    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<DomainTrack>() {

            override fun areItemsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean {
                val dummyAlbum = Album(0, 0, "", "", null, false, 0, 0)
                val dummyArtist = Artist(0, "", "", 0, 0, null)
                val oldMockedItem = oldItem.copy(album = dummyAlbum, artist = dummyArtist)
                val newMockedItem = newItem.copy(album = dummyAlbum, artist = dummyArtist)
                return oldMockedItem == newMockedItem
            }
        }
    }

    internal val currentItem: DomainTrack? get() = currentList.firstOrNull { it.nowPlaying }
    internal val currentIndex: Int get() = currentList.indexOfFirst { it.nowPlaying }

    internal fun getItemsAfter(start: Int): List<DomainTrack> =
        if (start in currentList.indices) currentList.subList(start, currentList.size)
        else emptyList()

    private fun remove(index: Int) {
        if (index !in currentList.indices) return
        onQueueRemove(index)
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
                        onClickArtist(
                            binding.data?.artist ?: return@setOnMenuItemClickListener false
                        )
                    }
                    R.id.menu_transition_to_album -> {
                        onClickAlbum(
                            binding.data?.album ?: return@setOnMenuItemClickListener false
                        )
                    }
                    R.id.menu_insert_all_next,
                    R.id.menu_insert_all_last,
                    R.id.menu_override_all -> {
                        onNewQueue(
                            when (menuItem.itemId) {
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
                            },
                            binding.data ?: return@setOnMenuItemClickListener false
                        )
                    }
                    R.id.menu_edit_metadata -> {
                        onEditMetadata(currentList)
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
            onClickTrack(domainTrack)
            onChangeCurrentPosition(position)
        }

        private fun onTrackLongTapped(domainTrack: DomainTrack): Boolean {
            onClickTrack(domainTrack)
            popupMenu.apply {
                show()
            }

            return true
        }

        private fun deleteTrack(domainTrack: DomainTrack?) {
            domainTrack ?: return
            onDeleteTrack(domainTrack)
        }

        fun dismissPopupMenu() {
            popupMenu.dismiss()
        }
    }
}