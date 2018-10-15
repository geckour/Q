package com.geckour.q.ui.library.song

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListSongBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class SongListAdapter(private val viewModel: MainViewModel,
                      private val classType: OrientedClassType)
    : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

    private val items: ArrayList<Song> = ArrayList()

    internal fun setItems(items: List<Song>) {
        this.items.clear()
        upsertItems(items)
        notifyDataSetChanged()
    }

    internal fun setItem(item: Song, position: Int = itemCount) {
        if (items.lastIndex < position) {
            items.add(item)
            notifyItemInserted(items.lastIndex)
        } else {
            this.items[position] = item
            notifyItemChanged(position)
        }
    }

    internal fun upsertItem(item: Song, sortByTrackOrder: Boolean = true) {
        var index = items.indexOfFirst { it.id == item.id }
        if (index < 0) {
            val tempList = (items + item).let {
                if (sortByTrackOrder) it.sortedByTrackOrder()
                else it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                    it.name ?: UNKNOWN
                })
            }

            index = tempList.indexOf(item)
            items.add(index, item)
            notifyItemInserted(index)
        } else {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    internal fun upsertItems(items: List<Song>, sortByTrackOrder: Boolean = true) {
        val increased = items.map { it.id } - this.items.map { it.id }
        val decreased = this.items.map { it.id } - items.map { it.id }
        increased.forEach { id -> upsertItem(items.first { it.id == id }, sortByTrackOrder) }
        decreased.forEach { removeItem(it) }
    }

    private fun removeItem(item: Song) {
        removeItem(item.id)
    }

    private fun removeItem(songId: Long) {
        items.asSequence()
                .mapIndexed { i, s -> i to s }
                .filter { it.second.id == songId }.toList()
                .forEach {
                    items.removeAt(it.first)
                    notifyItemRemoved(it.first)
                }
    }

    internal fun addItems(items: List<Song>) {
        val size = itemCount
        this.items.addAll(items)
        notifyItemRangeInserted(size, items.size)
    }

    internal fun removeByTrackNum(trackNum: Int) {
        val index = this.items.indexOfFirst { it.trackNum == trackNum }
        if (index !in this.items.indices) return
        this.items.removeAt(index)
        notifyItemRemoved(index)
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(actionType: InsertActionType) {
        viewModel.onNewQueue(items, actionType, OrientedClassType.SONG)
    }

    internal fun onSongDeleted(id: Long) {
        removeItem(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListSongBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListSongBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private val shortPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                viewModel.selectedSong?.apply {
                    viewModel.onNewQueue(listOf(this), when (it.itemId) {
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
                } ?: return@setOnMenuItemClickListener false

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song)
        }

        private val longPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                if (it.itemId == R.id.menu_delete_song) {
                    deleteSong(viewModel.selectedSong)
                } else {
                    viewModel.selectedSong?.apply {
                        viewModel.onNewQueue(listOf(this), when (it.itemId) {
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
                    } ?: return@setOnMenuItemClickListener false
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song_long)
        }

        fun bind() {
            val song = items[adapterPosition]
            binding.data = song
            binding.duration.text = song.durationString
            try {
                GlobalScope.launch(Dispatchers.Main) {
                    Glide.with(binding.thumb)
                            .load(DB.getInstance(binding.root.context)
                                    .getArtworkUriStringFromId(song.albumId)
                                    ?: R.drawable.ic_empty)
                            .into(binding.thumb)
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { onSongSelected(song) }
            binding.root.setOnLongClickListener { onSongLongTapped(song) }
            if (classType == OrientedClassType.PLAYLIST) {
                binding.option.visibility = View.VISIBLE
                binding.option.setOnClickListener {
                    song.trackNum?.apply { removeFromPlaylist(this) }
                }
            }
        }

        private fun deleteSong(song: Song?) {
            if (song == null) return
            viewModel.songToDelete.value = song
        }

        private fun removeFromPlaylist(playOrder: Int) {
            viewModel.onRequestRemoveSongFromPlaylist(playOrder)
        }

        private fun onSongSelected(song: Song) {
            viewModel.onRequestNavigate(song)
            shortPopupMenu.show()
        }

        private fun onSongLongTapped(song: Song): Boolean {
            viewModel.onRequestNavigate(song)
            longPopupMenu.show()

            return true
        }
    }
}