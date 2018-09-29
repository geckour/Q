package com.geckour.q.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.ItemListSearchCategoryBinding
import com.geckour.q.databinding.ItemListSearchItemBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.UNKNOWN
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import com.geckour.q.data.db.model.Album as DBAlbum
import com.geckour.q.data.db.model.Artist as DBArtist

class SearchListAdapter(private val viewModel: MainViewModel)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: ArrayList<SearchItem> = ArrayList()

    internal fun addItem(item: SearchItem) {
        items.add(item)
        notifyItemInserted(itemCount)
    }

    internal fun addItems(items: List<SearchItem>) {
        val size = itemCount
        this.items.addAll(items)
        notifyItemRangeInserted(size, items.size)
    }

    internal fun clearItems() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                SearchItem.SearchItemType.CATEGORY.ordinal -> {
                    CategoryViewHolder(ItemListSearchCategoryBinding
                            .inflate(LayoutInflater.from(parent.context), parent, false))
                }
                else -> {
                    ItemViewHolder(ItemListSearchItemBinding
                            .inflate(LayoutInflater.from(parent.context), parent, false))
                }
            }

    override fun getItemViewType(position: Int): Int = items[position].type.ordinal

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryViewHolder -> holder.onBind(items[position])
            is ItemViewHolder -> holder.onBind(items[position])
            else -> throw IllegalArgumentException()
        }
    }

    class CategoryViewHolder(private val binding: ItemListSearchCategoryBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: SearchItem) {
            binding.title = item.title
        }
    }

    inner class ItemViewHolder(private val binding: ItemListSearchItemBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private val trackPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                val song = (binding.data?.data as? Track)?.let {
                    Song(it.id, it.mediaId, it.albumId, it.title, UNKNOWN, null, it.duration,
                            it.trackNum, it.discNum, null, null, it.sourcePath)
                } ?: return@setOnMenuItemClickListener false
                viewModel.onNewQueue(listOf(song), when (it.itemId) {
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
                }, OrientedClassType.SONG)

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song)
        }

        fun onBind(item: SearchItem) {
            binding.data = item
            binding.root.setOnClickListener { item.onClick() }
            launch {
                val db = DB.getInstance(binding.root.context)
                val artwork = when (item.type) {
                    SearchItem.SearchItemType.ARTIST -> (item.data as? DBArtist)?.id?.let {
                        db.albumDao().findByArtistId(it)
                                .firstOrNull { it.artworkUriString != null }
                                ?.artworkUriString
                    }
                    SearchItem.SearchItemType.ALBUM -> (item.data as? DBAlbum)?.artworkUriString
                    SearchItem.SearchItemType.TRACK -> (item.data as? Track)?.albumId?.let {
                        db.albumDao().get(it)?.artworkUriString
                    }
                    else -> null
                }
                launch(UI) {
                    Glide.with(binding.thumb)
                            .load(artwork ?: R.drawable.ic_empty)
                            .into(binding.thumb)
                }
            }
        }

        fun SearchItem.onClick() {
            when (type) {
                SearchItem.SearchItemType.ARTIST -> {
                    viewModel.selectedArtist.value = (data as? DBArtist)?.let {
                        Artist(it.id, it.title ?: UNKNOWN, null, 0)
                    }
                }
                SearchItem.SearchItemType.ALBUM -> {
                    viewModel.selectedAlbum.value = (data as? DBAlbum)?.let {
                        Album(it.id, it.mediaId, it.title, null, it.artworkUriString, 0)
                    }
                }
                SearchItem.SearchItemType.TRACK -> trackPopupMenu.show()
                else -> Unit
            }
        }
    }
}