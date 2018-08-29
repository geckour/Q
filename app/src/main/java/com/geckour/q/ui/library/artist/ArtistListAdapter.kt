package com.geckour.q.ui.library.artist

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.geckour.q.databinding.ItemListArtistBinding
import com.geckour.q.domain.model.Artist

class ArtistListAdapter : RecyclerView.Adapter<ArtistListAdapter.ViewHolder>() {

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
            notifyItemInserted(position)
        }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListArtistBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[holder.adapterPosition])
    }


    class ViewHolder(private val binding: ItemListArtistBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(artist: Artist) {
            binding.data = artist
        }
    }
}