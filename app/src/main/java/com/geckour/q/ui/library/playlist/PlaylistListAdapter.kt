package com.geckour.q.ui.library.playlist

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.geckour.q.databinding.ItemListPlaylistBinding
import com.geckour.q.domain.model.Playlist
import com.geckour.q.ui.MainViewModel
import timber.log.Timber

class PlaylistListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<PlaylistListAdapter.ViewHolder>() {

    private val items: ArrayList<Playlist> = ArrayList()

    internal fun setItems(items: List<Playlist>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun setItem(item: Playlist, position: Int = itemCount) {
        if (items.lastIndex < position) {
            items.add(item)
            notifyItemInserted(items.lastIndex)
        } else {
            this.items[position] = item
            notifyItemChanged(position)
        }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListPlaylistBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[holder.adapterPosition])
    }


    inner class ViewHolder(private val binding: ItemListPlaylistBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            binding.data = playlist
            try {
                Glide.with(binding.thumb).load(playlist.thumb).into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { viewModel.onRequestNavigate(playlist) }
        }
    }
}