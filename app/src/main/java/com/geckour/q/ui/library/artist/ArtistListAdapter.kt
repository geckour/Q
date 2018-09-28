package com.geckour.q.ui.library.artist

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListArtistBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class ArtistListAdapter(private val viewModel: MainViewModel)
    : RecyclerView.Adapter<ArtistListAdapter.ViewHolder>() {

    private val items: ArrayList<Artist> = ArrayList()

    internal fun setItems(items: List<Artist>) {
        this.items.clear()
        upsertItems(items)
        notifyDataSetChanged()
    }

    internal fun getItems(): List<Artist> = items

    internal fun removeItem(item: Artist) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    internal fun upsertItem(item: Artist) {
        var index = items.indexOfFirst { it.id == item.id }
        if (index < 0) {
            val tempList = ArrayList(items).apply { add(item) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            index = tempList.indexOf(item)
            items.add(index, item)
            notifyItemInserted(index)
        } else {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    internal fun upsertItems(items: List<Artist>) {
        val increased = items - this.items
        val decreased = this.items - items
        increased.forEach { upsertItem(it) }
        decreased.forEach { removeItem(it) }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType,
                            classType: OrientedClassType = OrientedClassType.ARTIST) {
        launch(UI) {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListArtistBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListArtistBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(binding.root.context,
                        it.itemId, binding.data)
            }
            inflate(R.menu.albums)
        }

        fun bind() {
            val artist = items[adapterPosition]
            binding.data = artist
            binding.duration.text = artist.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(artist) }
            binding.option.setOnClickListener { popupMenu.show() }
            try {
                launch(UI) {
                    Glide.with(binding.thumb)
                            .load(artist.thumbUriString ?: R.drawable.ic_empty)
                            .into(binding.thumb)
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }

        private fun onOptionSelected(context: Context, id: Int, artist: Artist?): Boolean {
            if (artist == null) return false

            val actionType = when (id) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            launch {
                val sortByTrackOrder = id.let {
                    it != R.id.menu_insert_all_simple_shuffle_next
                            || it != R.id.menu_insert_all_simple_shuffle_last
                            || it != R.id.menu_override_all_simple_shuffle
                }
                val songs = DB.getInstance(context).let { db ->
                    db.albumDao().findByArtistId(artist.id).map {
                        db.trackDao().findByAlbum(it.id)
                                .mapNotNull { getSong(db, it).await() }
                                .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                    }.flatten()
                }

                onNewQueue(songs, actionType, OrientedClassType.ALBUM)
            }

            return true
        }
    }
}