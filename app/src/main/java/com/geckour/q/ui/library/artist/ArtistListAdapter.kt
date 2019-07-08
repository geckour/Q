package com.geckour.q.ui.library.artist

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
import com.geckour.q.databinding.ItemListArtistBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getSong
import com.geckour.q.util.getTimeString
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.sortedByTrackOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private fun removeItem(item: Artist) {
        removeItem(item.id)
    }

    private fun removeItem(artistId: Long) {
        val index = items.indexOfFirst { it.id == artistId }
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
        val increased = items.map { it.id } - this.items.map { it.id }
        val decreased = this.items.map { it.id } - items.map { it.id }
        increased.forEach { id -> upsertItem(items.first { it.id == id }) }
        decreased.forEach { removeItem(it) }
    }

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onArtistDeleted(artistId: Long) {
        removeItem(artistId)
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType,
                            classType: OrientedClassType = OrientedClassType.ARTIST) {
        GlobalScope.launch(Dispatchers.Main) {
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

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(bindTo.context,
                        it.itemId, binding.data)
            }
            inflate(R.menu.albums)
        }

        fun bind() {
            val artist = items[adapterPosition]
            binding.data = artist
            binding.duration.text = artist.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(artist) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val drawable = Glide.with(binding.thumb.context)
                            .asDrawable()
                            .load(artist.thumbUriString ?: R.drawable.ic_empty)
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

            GlobalScope.launch {
                val sortByTrackOrder = id.let {
                    it != R.id.menu_insert_all_simple_shuffle_next
                            || it != R.id.menu_insert_all_simple_shuffle_last
                            || it != R.id.menu_override_all_simple_shuffle
                }
                val songs = DB.getInstance(context).let { db ->
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                    viewModel.loading.postValue(true)
                    db.albumDao().findByArtistId(artist.id).map {
                        db.trackDao().findByAlbum(it.id, BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled))
                                .mapNotNull { getSong(db, it) }
                                .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                    }.apply {
                        viewModel.loading.postValue(false)
                    }.flatten()
                }

                onNewQueue(songs, actionType, OrientedClassType.ALBUM)
            }

            return true
        }
    }
}