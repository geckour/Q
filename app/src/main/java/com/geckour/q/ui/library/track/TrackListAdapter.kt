package com.geckour.q.ui.library.track

import android.view.LayoutInflater
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

class TrackListAdapter(
    private val onNewQueue: (actionType: InsertActionType, track: DomainTrack) -> Unit,
    private val onEditMetadata: (track: DomainTrack) -> Unit,
    private val onTransitToArtist: (artist: Artist) -> Unit,
    private val onTransitToAlbum: (album: Album) -> Unit,
    private val onDeleteTrack: (track: DomainTrack) -> Unit,
    private val onClickTrack: (track: DomainTrack) -> Unit,
    private val onToggleIgnored: (track: DomainTrack) -> Unit
) : ListAdapter<DomainTrack, TrackListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<DomainTrack>() {

            override fun areItemsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem == newItem
        }
    }

    internal fun clearItems() {
        submitList(null)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_transition_to_artist -> {
                        onTransitToArtist(
                            binding.data?.artist ?: return@setOnMenuItemClickListener false
                        )
                    }
                    R.id.menu_transition_to_album -> {
                        onTransitToAlbum(
                            binding.data?.album ?: return@setOnMenuItemClickListener false
                        )
                    }
                    R.id.menu_ignore -> {
                        onToggleIgnored(binding.data ?: return@setOnMenuItemClickListener false)
                        binding.data = binding.data?.let { it.copy(ignored = it.ignored?.not()) }
                    }
                    R.id.menu_edit_metadata -> {
                        onEditMetadata(binding.data ?: return@setOnMenuItemClickListener false)
                    }
                    R.id.menu_delete_track -> {
                        onDeleteTrack(binding.data ?: return@setOnMenuItemClickListener false)
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
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.track)
        }

        fun bind() {
            val track = getItem(adapterPosition)
            binding.data = track
            binding.duration.text = track.durationString
            binding.thumb.loadOrDefault(track.artworkUriString)
            binding.root.setOnClickListener { onTrackSelected(track) }
            binding.root.setOnLongClickListener {
                onTrackSelected(track)
                return@setOnLongClickListener true
            }
        }

        private fun onTrackSelected(domainTrack: DomainTrack) {
            onClickTrack(domainTrack)
            popupMenu.show()
            popupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.getString(
                if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                else R.string.menu_ignore_to_true
            )
        }
    }
}