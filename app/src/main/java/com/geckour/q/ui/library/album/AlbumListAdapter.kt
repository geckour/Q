package com.geckour.q.ui.library.album

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.geckour.q.databinding.ItemListAlbumBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getArtworkUriFromAlbumId
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

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListAlbumBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[holder.adapterPosition])
    }


    inner class ViewHolder(private val binding: ItemListAlbumBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) {
            binding.data = album
            try {
                Glide.with(binding.thumb)
                        .load(getArtworkUriFromAlbumId(binding.thumb.context, album.id))
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.option.setOnClickListener { viewModel.onRequestNavigate(album) }
        }
    }
}