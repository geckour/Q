package com.geckour.q.ui.sheet

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

class QueueListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<QueueListAdapter.ViewHolder>() {

    private var items: List<Song> = emptyList()

    internal fun setItems(items: List<Song>) {
        this.items = items
        notifyDataSetChanged()
    }

    internal fun getItem(index: Int?): Song? =
            when (index) {
                in 0..items.lastIndex -> items[requireNotNull(index)]
                -1 -> items.firstOrNull()
                else -> null
            }

    internal fun setNowPlaying(index: Int?) {
        items = items.map { it.copy(nowPlaying = false) }
        items.mapIndexed { i, song -> i to song.nowPlaying }
                .forEach { notifyItemChanged(it.first) }

        if (index != null && index in 0..items.lastIndex) {
            items = items.mapIndexed { i, song ->
                if (i == index) song.copy(nowPlaying = true)
                else song
            }
            notifyItemChanged(index)
        }
    }

    internal fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items = items.toMutableList().swapped(from, to)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(items[holder.adapterPosition], holder.adapterPosition)
    }

    inner class ViewHolder(private val binding: ItemListSongBinding) :
            RecyclerView.ViewHolder(binding.root) {
        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
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
                    }, PlayerService.OrientedClassType.SONG)
                } ?: return@setOnMenuItemClickListener false

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.queue)
        }

        fun onBind(song: Song, position: Int) {
            binding.data = song
            try {
                Glide.with(binding.thumb)
                        .load(getArtworkUriFromAlbumId(song.albumId))
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }

            binding.root.setOnClickListener { onSongSelected(song, position) }
            binding.root.setOnLongClickListener { onSongLongTapped(song) }
        }

        private fun onSongSelected(song: Song, position: Int) {
            viewModel.onRequestNavigate(song)
            viewModel.requestedPositionInQueue.value = position
        }

        private fun onSongLongTapped(song: Song): Boolean {
            viewModel.onRequestNavigate(song)
            popupMenu.apply {
                show()
            }

            return true
        }

        fun dismissPopupMenu() {
            popupMenu.dismiss()
        }
    }

    private fun <T> MutableList<T>.swapped(from: Int, to: Int): MutableList<T> {
        val tmp = this[to]
        this[to] = this[from]
        this[from] = tmp
        return this
    }
}