package com.geckour.q.ui.library.song

import android.content.Context
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.databinding.ItemListSongBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getArtworkUriStringFromId
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SongListAdapter(
        private val viewModel: MainViewModel, private val classType: OrientedClassType
) : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

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
        items.asSequence().mapIndexed { i, s -> i to s }.filter { it.second.id == songId }.toList()
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

    internal fun onNewQueue(context: Context, actionType: InsertActionType) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        viewModel.onNewQueue(
                items.let {
                    if (sharedPreferences.ignoringEnabled) it.filter { it.ignored != true }
                    else it
                }, actionType, OrientedClassType.SONG
        )
    }

    internal fun onSongDeleted(id: Long) {
        removeItem(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            ItemListSongBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
            )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListSongBinding) :
            RecyclerView.ViewHolder(binding.root) {

        private val shortPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedSong?.apply {
                            viewModel.onNewQueue(
                                    listOf(this), when (it.itemId) {
                                R.id.menu_insert_all_next -> InsertActionType.NEXT
                                R.id.menu_insert_all_last -> InsertActionType.LAST
                                R.id.menu_override_all -> InsertActionType.OVERRIDE
                                else -> return@setOnMenuItemClickListener false
                            }, OrientedClassType.SONG
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    else -> return@setOnMenuItemClickListener false
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.song)
        }

        private val longPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_transition_to_artist -> {
                        GlobalScope.launch(Dispatchers.Main) {
                            viewModel.selectedArtist.value = withContext((Dispatchers.IO)) {
                                binding.data?.artist?.let {
                                    DB.getInstance(binding.root.context).artistDao().findArtist(it)
                                            .firstOrNull()?.toDomainModel()
                                }
                            }
                        }
                    }
                    R.id.menu_transition_to_album -> {
                        GlobalScope.launch(Dispatchers.Main) {
                            viewModel.selectedAlbum.value = withContext(Dispatchers.IO) {
                                binding.data?.albumId?.let {
                                    DB.getInstance(binding.root.context).albumDao().get(it)
                                            ?.toDomainModel()
                                }
                            }
                        }
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    R.id.menu_delete_song -> deleteSong(viewModel.selectedSong)
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedSong?.apply {
                            viewModel.onNewQueue(
                                    listOf(this), when (it.itemId) {
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
                            }, OrientedClassType.SONG
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
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
                    Glide.with(binding.thumb).load(
                            DB.getInstance(binding.root.context).getArtworkUriStringFromId(song.albumId)
                                    ?: R.drawable.ic_empty
                    ).into(binding.thumb)
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
            shortPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.let {
                it.getString(
                        if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                        else R.string.menu_ignore_to_true
                )
            }
        }

        private fun onSongLongTapped(song: Song): Boolean {
            viewModel.onRequestNavigate(song)
            longPopupMenu.show()
            longPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.let {
                it.getString(
                        if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                        else R.string.menu_ignore_to_true
                )
            }

            return true
        }

        private fun toggleIgnored() {
            binding.data?.id?.also { trackId ->
                GlobalScope.launch(Dispatchers.IO) {
                    DB.getInstance(binding.root.context).trackDao().apply {
                        val ignored = when (this.get(trackId)?.ignored ?: Bool.FALSE) {
                            Bool.TRUE -> Bool.FALSE
                            Bool.FALSE -> Bool.TRUE
                            Bool.UNDEFINED -> Bool.UNDEFINED
                        }.apply { Timber.d("qgeck saved ignored value: $this") }
                        setIgnored(trackId, ignored)
                        withContext(Dispatchers.Main) {
                            binding.data =
                                    binding.data?.let { it.copy(ignored = it.ignored?.not()) }
                        }
                    }
                }
            }
        }
    }
}