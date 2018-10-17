package com.geckour.q.ui.sheet

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
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import timber.log.Timber

class QueueListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<QueueListAdapter.ViewHolder>() {

    private val items: MutableList<Song> = mutableListOf()

    internal fun setItems(items: List<Song>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun getItem(index: Int?): Song? =
            when (index) {
                in 0..items.lastIndex -> items[requireNotNull(index)]
                -1 -> items.firstOrNull()
                else -> null
            }

    internal fun getItemIds(): List<Long> = items.map { it.id }

    internal fun getItemsAfter(start: Int): List<Song> =
            items.subList(start, items.size)

    internal fun setNowPlayingPosition(index: Int?) {

        if (index != null) {
            if (index in 0 until items.size) {
                val changed: MutableList<Int> = mutableListOf()
                items.mapIndexed { i, song ->
                    val matched = i == index
                    if (song.nowPlaying != matched) changed.add(i)
                }
                changed.forEach {
                    items[it] = items[it].let { it.copy(nowPlaying = it.nowPlaying.not()) }
                    notifyItemChanged(it)
                }
            }
        } else {
            val changed: MutableList<Int> = mutableListOf()
            items.mapIndexed { i, song -> if (song.nowPlaying) changed.add(i) }
            changed.forEach {
                items[it] = items[it].copy(nowPlaying = false)
                notifyItemChanged(it)
            }
        }
    }

    internal fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.swap(from, to)
        notifyItemMoved(from, to)
    }

    private fun remove(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        notifyItemRemoved(index)
        viewModel.onQueueRemove(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListSongBinding) :
            RecyclerView.ViewHolder(binding.root) {
        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_transition_to_artist -> {
                        GlobalScope.launch(Dispatchers.Main) {
                            viewModel.selectedArtist.value =
                                    withContext((Dispatchers.IO)) {
                                        binding.data?.artist?.let {
                                            DB.getInstance(binding.root.context).artistDao()
                                                    .findArtist(it).firstOrNull()
                                                    ?.toDomainModel()
                                        }
                                    }
                        }
                    }
                    R.id.menu_transition_to_album -> {
                        GlobalScope.launch(Dispatchers.Main) {
                            viewModel.selectedAlbum.value =
                                    withContext(Dispatchers.IO) {
                                        binding.data?.albumId?.let {
                                            DB.getInstance(binding.root.context).albumDao()
                                                    .get(it)
                                                    ?.toDomainModel()
                                        }
                                    }
                        }
                    }
                    R.id.menu_insert_all_next,
                    R.id.menu_insert_all_last,
                    R.id.menu_override_all -> {
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
                    R.id.menu_delete_song -> {
                        remove(adapterPosition)
                        deleteSong(binding.data)
                    }
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.queue)
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

            binding.option.apply {
                visibility = View.VISIBLE
                setOnClickListener { remove(adapterPosition) }
            }

            binding.root.setOnClickListener { onSongSelected(song, adapterPosition) }
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

        private fun deleteSong(song: Song?) {
            if (song == null) return
            viewModel.songToDelete.value = song
        }

        fun dismissPopupMenu() {
            popupMenu.dismiss()
        }
    }
}