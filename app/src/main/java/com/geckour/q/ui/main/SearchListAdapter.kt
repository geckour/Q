package com.geckour.q.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.databinding.ItemSearchCategoryBinding
import com.geckour.q.databinding.ItemSearchItemBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.loadOrDefault

class SearchListAdapter(
    private val onNewQueue: (actionType: InsertActionType, track: DomainTrack) -> Unit,
    private val onEditMetadata: (track: DomainTrack) -> Unit,
    private val onClickArtist: (artist: Artist) -> Unit,
    private val onClickAlbum: (album: Album) -> Unit,
    private val onClickGenre: (genre: Genre) -> Unit
) : ListAdapter<SearchItem, RecyclerView.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<SearchItem>() {

            override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
                oldItem.type == newItem.type && oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            SearchItem.SearchItemType.CATEGORY.ordinal -> {
                CategoryViewHolder(
                    ItemSearchCategoryBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }
            else -> {
                ItemViewHolder(
                    ItemSearchItemBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }
        }

    override fun getItemViewType(position: Int): Int = currentList[position].type.ordinal

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryViewHolder -> holder.onBind(currentList[position])
            is ItemViewHolder -> holder.onBind(currentList[position])
            else -> throw IllegalArgumentException()
        }
    }

    class CategoryViewHolder(private val binding: ItemSearchCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: SearchItem) {
            binding.title = item.title
        }
    }

    inner class ItemViewHolder(
        private val binding: ItemSearchItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val trackPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener { menuItem ->
                val track = (binding.data?.data as? DomainTrack)
                    ?: return@setOnMenuItemClickListener true

                if (menuItem.itemId == R.id.menu_edit_metadata) {
                    onEditMetadata(track)
                    return@setOnMenuItemClickListener true
                }

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
                        else -> return@setOnMenuItemClickListener true
                    },
                    track
                )

                return@setOnMenuItemClickListener true
            }
//            inflate(R.menu.track)
        }

        fun onBind(item: SearchItem) {
            binding.data = item
            binding.root.setOnClickListener { item.onClick() }
            val artworkUriString = when (item.type) {
                SearchItem.SearchItemType.ARTIST -> {
                    (item.data as? Artist)?.artworkUriString
                }
                SearchItem.SearchItemType.ALBUM -> {
                    (item.data as? Album)?.artworkUriString
                }
                SearchItem.SearchItemType.TRACK -> {
                    (item.data as? DomainTrack)?.album?.artworkUriString
                }
                else -> null
            }

            binding.thumb.loadOrDefault(artworkUriString)
        }

        fun SearchItem.onClick() {
            when (type) {
                SearchItem.SearchItemType.ARTIST -> {
                    onClickArtist(data as? Artist ?: return)
                }
                SearchItem.SearchItemType.ALBUM -> {
                    onClickAlbum(data as? Album ?: return)
                }
                SearchItem.SearchItemType.TRACK -> trackPopupMenu.show()
                SearchItem.SearchItemType.GENRE -> {
                    onClickGenre(data as? Genre ?: return)
                }
                else -> Unit
            }
        }
    }
}