package com.geckour.q.ui.library.album

import android.content.Context
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListAlbumBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getSong
import com.geckour.q.util.getTimeString
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.sortedByTrackOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AlbumListAdapter(private val viewModel: MainViewModel) :
        RecyclerView.Adapter<AlbumListAdapter.ViewHolder>() {

    private val items: ArrayList<Album> = ArrayList()

    internal fun setItems(items: List<Album>) {
        this.items.clear()
        upsertItems(items)
        notifyDataSetChanged()
    }

    internal fun getItems(): List<Album> = items

    private fun upsertItem(item: Album) {
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
        val increased = items.map { it.id } - this.items.map { it.id }
        val decreased = this.items.map { it.id } - items.map { it.id }
        increased.forEach { id -> upsertItem(items.first { it.id == id }) }
        decreased.forEach { removeItem(it) }
    }

    private fun removeItem(albumId: Long) {
        val index = items.indexOfFirst { it.id == albumId }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    internal fun onAlbumDeleted(albumId: Long) {
        removeItem(albumId)
    }

    internal fun onNewQueue(
            songs: List<Song>,
            actionType: InsertActionType,
            classType: OrientedClassType = OrientedClassType.ALBUM
    ) {
        viewModel.viewModelScope.launch {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            ItemListAlbumBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
            )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListAlbumBinding) :
            RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(
                        bindTo.context, it.itemId, binding.data
                )
            }
            inflate(R.menu.songs)
        }

        fun bind() {
            val album = items[adapterPosition]
            binding.data = album
            binding.duration.text = album.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(album) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val drawable = Glide.with(binding.thumb.context)
                            .asDrawable()
                            .load(album.thumbUriString ?: R.drawable.ic_empty)
                            .submit()
                            .get()
                    withContext(Dispatchers.Main) {
                        binding.thumb.setImageDrawable(drawable)
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
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

            viewModel.viewModelScope.launch {
                val sortByTrackOrder =
                        id != R.id.menu_insert_all_simple_shuffle_next
                                || id != R.id.menu_insert_all_simple_shuffle_last
                                || id != R.id.menu_override_all_simple_shuffle
                val songs = withContext(Dispatchers.IO) {
                    DB.getInstance(context).let { db ->
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                        viewModel.loading.postValue(true)
                        db.trackDao().findByAlbum(
                                album.id, BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                        ).mapNotNull { getSong(db, it) }
                                .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                                .apply { viewModel.loading.postValue(false) }
                    }
                }

                onNewQueue(songs, actionType, OrientedClassType.SONG)
            }

            return true
        }
    }
}