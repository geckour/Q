package com.geckour.q.ui.library.playlist

import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListPlaylistBinding
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType,
                            classType: OrientedClassType = OrientedClassType.PLAYLIST) {
        GlobalScope.launch(Dispatchers.Main) {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListPlaylistBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListPlaylistBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(bindTo.context,
                        it.itemId, binding.data)
            }
            inflate(R.menu.playlist)
        }

        fun bind() {
            val playlist = items[adapterPosition]
            binding.data = playlist
            binding.duration.text = playlist.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(playlist) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                return@setOnLongClickListener true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            try {
                Glide.with(binding.thumb).load(playlist.thumb).into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
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

        private fun onOptionSelected(context: Context, id: Int, playlist: Playlist?): Boolean {
            if (playlist == null) return false

            val actionType = when (id) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                R.id.menu_delete_playlist -> {
                    playlist.delete()
                    return true
                }
                else -> return false
            }

            viewModel.loading.value = true
            GlobalScope.launch {
                val songs = playlist.getTrackMediaIds(context)
                        .sortedBy { it.second }
                        .mapNotNull {
                            getSong(DB.getInstance(context), it.first, playlistId = playlist.id)
                        }.toList()

                onNewQueue(songs, actionType, OrientedClassType.SONG)
            }

            return true
        }
    }
}