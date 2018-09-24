package com.geckour.q.ui.library.genre

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListGenreBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getSong
import com.geckour.q.util.getTrackMediaIds
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class GenreListAdapter(private val viewModel: MainViewModel) : RecyclerView.Adapter<GenreListAdapter.ViewHolder>() {

    private val items: ArrayList<Genre> = ArrayList()

    internal fun setItems(items: List<Genre>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun setItem(item: Genre, position: Int = itemCount) {
        if (items.lastIndex < position) {
            items.add(item)
            notifyItemInserted(items.lastIndex)
        } else {
            this.items[position] = item
            notifyItemChanged(position)
        }
    }

    internal fun getItems(): List<Genre> = items

    internal fun clearItems() {
        this.items.clear()
        notifyDataSetChanged()
    }

    internal fun onNewQueue(songs: List<Song>, actionType: InsertActionType,
                            classType: OrientedClassType = OrientedClassType.GENRE) {
        launch(UI) {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemListGenreBinding.inflate(LayoutInflater.from(parent.context),
                    parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemListGenreBinding)
        : RecyclerView.ViewHolder(binding.root) {

        private val popupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(binding.root.context,
                        it.itemId, binding.data)
            }
            inflate(R.menu.songs)
        }

        fun bind() {
            val genre = items[adapterPosition]
            binding.data = genre
            binding.root.setOnClickListener { viewModel.onRequestNavigate(genre) }
            binding.option.setOnClickListener { popupMenu.show() }
            try {
                Glide.with(binding.thumb).load(genre.thumb).into(binding.thumb)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }

        private fun onOptionSelected(context: Context, id: Int, genre: Genre?): Boolean {
            if (genre == null) return false

            val actionType = when (id) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            launch {
                val songs = genre.getTrackMediaIds(context).mapNotNull {
                    getSong(DB.getInstance(context), it, genreId = genre.id).await()
                }

                onNewQueue(songs, actionType, OrientedClassType.SONG)
            }

            return true
        }
    }
}