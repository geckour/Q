package com.geckour.q.ui.library.album

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.databinding.ItemListAlbumBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class AlbumListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<AlbumListAdapter.ViewHolder>() {

    private val items: ArrayList<Album> = ArrayList()

    internal fun setItems(items: List<Album>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun setItem(item: Album, position: Int = itemCount) {
        if (items.lastIndex < position) {
            items.add(item)
            notifyItemInserted(items.lastIndex)
        } else {
            this.items[position] = item
            notifyItemInserted(position)
        }
    }

    internal fun getItems(): List<Album> = items

    internal fun upsertItem(item: Album) {
        var index = items.indexOfFirst { it.id == item.id }
        if (index < 0) {
            val tempList = ArrayList(items).apply { add(item) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                        it.name ?: UNKNOWN
                    })
            index = tempList.indexOf(item)
            items.add(index, item)
            notifyItemInserted(index)
        } else {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    internal fun upsertItems(items: List<Album>) {
        val changed = items - this.items
        changed.forEach {
            upsertItem(it)
        }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType) {
        launch(UI) {
            viewModel.onNewQueue(songs, actionType, OrientedClassType.ALBUM)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListAlbumBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListAlbumBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val album = items[adapterPosition]
            binding.data = album
            try {
                Glide.with(binding.thumb)
                        .load(album.thumbUriString)
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { viewModel.onRequestNavigate(album) }
        }
    }
}