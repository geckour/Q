package com.geckour.q.ui.library.playlist

import android.provider.MediaStore
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.databinding.ItemListPlaylistBinding
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
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

    internal fun getItems(): List<Playlist> = items

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType) {
        launch(UI) {
            viewModel.onNewQueue(songs, actionType, OrientedClassType.PLAYLIST)
        }
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
        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                binding.data?.apply {
                    when (it.itemId) {
                        R.id.menu_delete_playlist -> this.delete()
                        else -> return@setOnMenuItemClickListener false
                    }
                } ?: return@setOnMenuItemClickListener false

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.playlist_long)
        }

        fun bind(playlist: Playlist) {
            binding.data = playlist
            try {
                Glide.with(binding.thumb).load(playlist.thumb).into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { viewModel.onRequestNavigate(playlist) }
            binding.root.setOnLongClickListener {
                popupMenu.show()
                return@setOnLongClickListener true
            }
        }

        private fun Playlist.delete() {
            val deleted = binding.root.context.contentResolver
                    .delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Audio.Playlists._ID}=?",
                            arrayOf(this.id.toString())) == 1
            if (deleted) {
                items.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
            }
        }
    }
}