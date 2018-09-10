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
            if (index in 0..items.lastIndex) items[requireNotNull(index)] else null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(items[holder.adapterPosition])
    }

    inner class ViewHolder(private val binding: ItemListSongBinding) :
            RecyclerView.ViewHolder(binding.root) {
        fun onBind(song: Song) {
            binding.data = song
            try {
                Glide.with(binding.thumb)
                        .load(getArtworkUriFromAlbumId(song.albumId))
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }

            binding.root.setOnClickListener { onSongSelected(song) }
            binding.root.setOnLongClickListener { onSongLongTapped(song) }
        }

        private fun onSongSelected(song: Song) {
            viewModel.onRequestNavigate(song)
            viewModel.requestedSongInQueue.value = song
        }

        private fun onSongLongTapped(song: Song): Boolean {
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
                inflate(R.menu.queue)
                show()
            }

            return true
        }
    }
}