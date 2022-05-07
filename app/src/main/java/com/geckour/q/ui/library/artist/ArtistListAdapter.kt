package com.geckour.q.ui.library.artist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.model.Artist
import com.geckour.q.databinding.ItemArtistBinding
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getTimeString
import com.geckour.q.util.loadOrDefault

class ArtistListAdapter(
    private val onClickArtist: (artist: Artist) -> Unit,
    private val onNewQueue: (actionType: InsertActionType, artist: Artist) -> Unit,
    private val onEditMetadata: (artist: Artist) -> Unit,
    private val onDeleteArtist: (artist: Artist) -> Unit
) : ListAdapter<Artist, ArtistListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<Artist>() {

            override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem == newItem
        }
    }

    internal fun onArtistDeleted(artistId: Long) {
        removeItem(artistId)
    }

    private fun removeItem(artistId: Long) {
        submitList(currentList.dropWhile { it.id == artistId })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                if (it.itemId == R.id.menu_edit_metadata) {
                    onEditMetadata(binding.data ?: return@setOnMenuItemClickListener false)
                    return@setOnMenuItemClickListener true
                }
                if (it.itemId == R.id.menu_delete_artist) {
                    onDeleteArtist(binding.data ?: return@setOnMenuItemClickListener false)
                    return@setOnMenuItemClickListener true
                }

                val actionType = when (it.itemId) {
                    R.id.menu_insert_all_next -> InsertActionType.NEXT
                    R.id.menu_insert_all_last -> InsertActionType.LAST
                    R.id.menu_override_all -> InsertActionType.OVERRIDE
                    R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                    R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                    R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                    R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                    R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                    R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                    else -> null
                } ?: return@setOnMenuItemClickListener false

                onNewQueue(actionType, binding.data ?: return@setOnMenuItemClickListener false)

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.artist)
        }

        fun bind() {
            val artist = getItem(adapterPosition)
            binding.data = artist
            binding.duration.text = artist.totalDuration.getTimeString()
            binding.root.setOnClickListener { onClickArtist(artist) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            binding.thumb.loadOrDefault(artist.artworkUriString)
        }
    }
}