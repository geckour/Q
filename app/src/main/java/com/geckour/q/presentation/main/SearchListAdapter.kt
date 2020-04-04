package com.geckour.q.presentation.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListSearchCategoryBinding
import com.geckour.q.databinding.ItemListSearchItemBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.Song
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.orDefaultForModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchListAdapter(private val viewModel: MainViewModel) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SearchItem>()

    private fun addItems(items: List<SearchItem>) {
        val size = itemCount
        this.items.addAll(items)
        notifyItemRangeInserted(size, items.size)
    }

    private fun clearItems() {
        items.clear()
        notifyDataSetChanged()
    }

    internal fun replaceItems(items: List<SearchItem>) {
        clearItems()
        addItems(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                SearchItem.SearchItemType.CATEGORY.ordinal -> {
                    CategoryViewHolder(
                            ItemListSearchCategoryBinding.inflate(
                                    LayoutInflater.from(parent.context), parent, false
                            )
                    )
                }
                else -> {
                    ItemViewHolder(
                            ItemListSearchItemBinding.inflate(
                                    LayoutInflater.from(parent.context), parent, false
                            )
                    )
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

    class CategoryViewHolder(private val binding: ItemListSearchCategoryBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: SearchItem) {
            binding.title = item.title
        }
    }

    inner class ItemViewHolder(private val binding: ItemListSearchItemBinding) :
            RecyclerView.ViewHolder(binding.root) {

        private val trackPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                val song = (binding.data?.data as? Song)
                        ?: return@setOnMenuItemClickListener true
                viewModel.onNewQueue(
                        listOf(song),
                        when (it.itemId) {
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
                        OrientedClassType.SONG
                )

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song)
        }

        fun onBind(item: SearchItem) {
            binding.data = item
            binding.root.setOnClickListener { item.onClick() }
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val db = DB.getInstance(binding.root.context)
                val artworkUriString = when (item.type) {
                    SearchItem.SearchItemType.ARTIST -> {
                        (item.data as? Artist)?.id?.let {
                            db.albumDao().getAllByArtistId(it)
                                    .firstOrNull { it.artworkUriString != null }
                                    ?.artworkUriString
                        }
                    }
                    SearchItem.SearchItemType.ALBUM -> {
                        (item.data as? Album)?.thumbUriString
                    }
                    SearchItem.SearchItemType.TRACK -> {
                        (item.data as? Song)?.albumId?.let {
                            db.albumDao().get(it)?.artworkUriString
                        }
                    }
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    Glide.with(binding.thumb)
                            .load(artworkUriString.orDefaultForModel)
                            .applyDefaultSettings()
                            .into(binding.thumb)
                }
            }
        }

        fun SearchItem.onClick() {
            when (type) {
                SearchItem.SearchItemType.ARTIST -> {
                    viewModel.selectedArtist.value = data as? Artist
                }
                SearchItem.SearchItemType.ALBUM -> {
                    viewModel.selectedAlbum.value = data as Album
                }
                SearchItem.SearchItemType.TRACK -> trackPopupMenu.show()
                SearchItem.SearchItemType.PLAYLIST -> {
                    viewModel.selectedPlaylist.value = data as? Playlist
                }
                SearchItem.SearchItemType.GENRE -> {
                    viewModel.selectedGenre.value = data as? Genre
                }
                else -> Unit
            }
        }
    }
}