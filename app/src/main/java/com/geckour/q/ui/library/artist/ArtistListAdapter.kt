package com.geckour.q.ui.library.artist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.databinding.ItemListArtistBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getArtworkUriFromAlbumId
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class ArtistListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<ArtistListAdapter.ViewHolder>() {

    private val items: ArrayList<Artist> = ArrayList()

    internal fun setItems(items: List<Artist>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun setItem(item: Artist, position: Int = itemCount) {
        if (items.lastIndex < position) {
            items.add(item)
            notifyItemInserted(items.lastIndex)
        } else {
            this.items[position] = item
            notifyItemChanged(position)
        }
    }

    internal fun getItems(): List<Artist> = items

    internal fun upsertItem(item: Artist) {
        var index = items.indexOfFirst { it.id == item.id }
        if (index < 0) {
            val tempList = ArrayList(items).apply { add(item) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            index = tempList.indexOf(item)
            items.add(index, item)
            notifyItemInserted(index)
        } else {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    internal fun upsertItems(items: List<Artist>) {
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
            viewModel.onNewQueue(songs, actionType, OrientedClassType.ARTIST)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListArtistBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListArtistBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val artist = items[adapterPosition]
            binding.data = artist
            try {
                Glide.with(binding.thumb)
                        .load(getArtworkUriFromAlbumId(artist.albumId))
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { viewModel.onRequestNavigate(artist) }
        }
    }
}