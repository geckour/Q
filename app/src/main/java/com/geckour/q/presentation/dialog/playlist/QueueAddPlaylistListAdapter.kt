package com.geckour.q.presentation.dialog.playlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.databinding.ItemSimplePlaylistBinding
import com.geckour.q.domain.model.Playlist

class QueueAddPlaylistListAdapter(
        private val items: List<Playlist>, private val onSelectPlaylist: (Playlist) -> Unit
) : RecyclerView.Adapter<QueueAddPlaylistListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            ItemSimplePlaylistBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
            )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[holder.adapterPosition])
    }

    inner class ViewHolder(private val binding: ItemSimplePlaylistBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.data = playlist
            binding.root.setOnClickListener { onSelectPlaylist(playlist) }
        }
    }
}