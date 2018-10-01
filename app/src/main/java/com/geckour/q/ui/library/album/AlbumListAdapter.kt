package com.geckour.q.ui.library.album

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListAlbumBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class AlbumListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<AlbumListAdapter.ViewHolder>() {

    private val items: ArrayList<Album> = ArrayList()

    internal fun setItems(items: List<Album>) {
        this.items.clear()
        upsertItems(items)
        notifyDataSetChanged()
    }

    internal fun getItems(): List<Album> = items

    internal fun upsertItem(item: Album) {
        var index = items.indexOfFirst { it.id == item.id }
        if (index < 0) {
            val tempList = ArrayList(items).apply { add(item) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                        it.name ?: UNKNOWN
                    })
            index = tempList.indexOf(item)
            items.add(index, item)
            notifyItemInserted(index)
        } else {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    internal fun upsertItems(items: List<Album>) {
        val increased = items - this.items
        val decreased = this.items - items
        increased.forEach { upsertItem(it) }
        decreased.forEach { removeItem(it) }
    }

    private fun removeItem(item: Album) {
        removeItem(item.id)
    }

    private fun removeItem(albumId: Long) {
        val index = items.indexOfFirst { it.id == albumId }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onAlbumDeleted(albumId: Long) {
        removeItem(albumId)
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType,
                            classType: OrientedClassType = OrientedClassType.ALBUM) {
        GlobalScope.launch(Dispatchers.Main) {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListAlbumBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListAlbumBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(binding.root.context,
                        it.itemId, binding.data)
            }
            inflate(R.menu.songs)
        }

        fun bind() {
            val album = items[adapterPosition]
            binding.data = album
            binding.duration.text = album.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(album) }
            binding.option.setOnClickListener { popupMenu.show() }
            try {
                Glide.with(binding.thumb)
                        .load(album.thumbUriString ?: R.drawable.ic_empty)
                        .into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }

        private fun onOptionSelected(context: Context, id: Int, album: Album?): Boolean {
            if (album == null) return false

            val actionType = when (id) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            viewModel.loading.value = true
            GlobalScope.launch {
                val sortByTrackOrder = id.let {
                    it != R.id.menu_insert_all_simple_shuffle_next
                            || it != R.id.menu_insert_all_simple_shuffle_last
                            || it != R.id.menu_override_all_simple_shuffle
                }
                val songs = DB.getInstance(context).let { db ->
                    db.trackDao().findByAlbum(album.id)
                            .mapNotNull { getSong(db, it).await() }
                            .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                }

                onNewQueue(songs, actionType, OrientedClassType.SONG)
            }

            return true
        }
    }
}