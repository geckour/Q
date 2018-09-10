package com.geckour.q.ui.library.song

import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.databinding.ItemListSongBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getArtworkUriFromAlbumId
import timber.log.Timber

class SongListAdapter(private val viewModel: MainViewModel)
    : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

    private val items: ArrayList<Song> = ArrayList()

    internal fun setItems(items: List<Song>) {
        this.items.clear()
        this.items.addAll(items)
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
                if (sortByTrackOrder) {
                    it.groupBy { it.discNum }
                            .map { it.key to it.value.sortedBy { it.trackNum } }
                            .sortedBy { it.first }
                            .flatMap { it.second }
                } else {
                    it.sortedBy { it.name }
                }
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
        val changed = items - this.items
        changed.forEach {
            upsertItem(it, sortByTrackOrder)
        }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(actionType: PlayerService.InsertActionType) {
        viewModel.onNewQueue(items, actionType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListSongBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[holder.adapterPosition])
    }

    inner class ViewHolder(private val binding: ItemListSongBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.data = song
            try {
                Glide.with(binding.thumb)
                        .load(getArtworkUriFromAlbumId(song.albumId))
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            binding.root.setOnClickListener { onSongSelected(song) }
        }

        private fun onSongSelected(song: Song) {
            viewModel.onRequestNavigate(song)
            PopupMenu(binding.root.context, binding.root).apply {
                setOnMenuItemClickListener {
                    viewModel.selectedSong?.apply {
                        viewModel.onNewQueue(listOf(this), when (it.itemId) {
                            R.id.menu_insert_all_next -> {
                                PlayerService.InsertActionType.NEXT
                            }
                            R.id.menu_insert_all_last -> {
                                PlayerService.InsertActionType.LAST
                            }
                            R.id.menu_override_all -> {
                                PlayerService.InsertActionType.OVERRIDE
                            }
                            else -> return@setOnMenuItemClickListener false
                        })
                    } ?: return@setOnMenuItemClickListener false

                    return@setOnMenuItemClickListener true
                }
                inflate(R.menu.song)
                show()
            }
        }
    }
}