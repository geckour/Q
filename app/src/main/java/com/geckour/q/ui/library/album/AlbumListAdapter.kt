package com.geckour.q.ui.library.album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.databinding.ItemAlbumBinding
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getTimeString
import com.geckour.q.util.loadOrDefault

class AlbumListAdapter(
    private val onClickAlbum: (album: Album) -> Unit,
    private val onNewQueue: (actionType: InsertActionType, album: Album) -> Unit,
    private val onEditMetadata: (album: Album) -> Unit,
    private val onDeleteAlbum: (album: Album) -> Unit
) : ListAdapter<JoinedAlbum, AlbumListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<JoinedAlbum>() {

            override fun areItemsTheSame(oldItem: JoinedAlbum, newItem: JoinedAlbum): Boolean =
                oldItem.album.id == newItem.album.id

            override fun areContentsTheSame(oldItem: JoinedAlbum, newItem: JoinedAlbum): Boolean =
                oldItem == newItem
        }
    }

    override fun submitList(list: List<JoinedAlbum>?) {
        super.submitList(
            list?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album.titleSort })
        )
    }

    private fun removeItem(albumId: Long) {
        submitList(currentList.dropWhile { it.album.id == albumId })
    }

    internal fun onAlbumDeleted(albumId: Long) {
        removeItem(albumId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                if (it.itemId == R.id.menu_edit_metadata) {
                    onEditMetadata(binding.data?.album ?: return@setOnMenuItemClickListener false)
                    return@setOnMenuItemClickListener true
                }
                if (it.itemId == R.id.menu_delete_album) {
                    onDeleteAlbum(binding.data?.album ?: return@setOnMenuItemClickListener false)
                    return@setOnMenuItemClickListener true
                }

                val actionType = when (it.itemId) {
                    R.id.menu_insert_all_next -> InsertActionType.NEXT
                    R.id.menu_insert_all_last -> InsertActionType.LAST
                    R.id.menu_override_all -> InsertActionType.OVERRIDE
                    R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                    R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                    R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                    else -> null
                } ?: return@setOnMenuItemClickListener false
                onNewQueue(
                    actionType,
                    binding.data?.album ?: return@setOnMenuItemClickListener false
                )

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.album)
        }

        fun bind() {
            val joinedAlbum = getItem(adapterPosition)
            binding.data = joinedAlbum
            binding.duration.text = joinedAlbum.album.totalDuration.getTimeString()
            binding.root.setOnClickListener { onClickAlbum(joinedAlbum.album) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                return@setOnLongClickListener true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            binding.thumb.loadOrDefault(joinedAlbum.album.artworkUriString)
        }
    }
}