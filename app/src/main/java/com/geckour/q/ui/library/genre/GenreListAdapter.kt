package com.geckour.q.ui.library.genre

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.databinding.ItemGenreBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getTimeString
import com.geckour.q.util.loadOrDefault

class GenreListAdapter(
    private val onNewQueue: (genre: Genre, actionType: InsertActionType) -> Unit,
    private val onClickGenre: (genre: Genre) -> Unit,
    private val onEditMetadata: (genre: Genre) -> Unit,
) :
    ListAdapter<Genre, GenreListAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<Genre>() {
            override fun areItemsTheSame(oldItem: Genre, newItem: Genre): Boolean =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: Genre, newItem: Genre): Boolean =
                oldItem == newItem
        }
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemGenreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemGenreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(
                    bindTo.context, it.itemId, binding.data
                )
            }
            inflate(R.menu.genre)
        }

        fun bind() {
            val genre = currentList[adapterPosition]
            binding.data = genre
            binding.duration.text = genre.totalDuration.getTimeString()
            binding.root.setOnClickListener { onClickGenre(genre) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            binding.thumb.loadOrDefault(genre.thumb)
        }

        private fun onOptionSelected(context: Context, id: Int, genre: Genre?): Boolean {
            if (genre == null) return false

            if (id == R.id.menu_edit_metadata) {
                onEditMetadata(genre)
                return true
            }
            onNewQueue(
                genre,
                when (id) {
                    R.id.menu_insert_all_next -> InsertActionType.NEXT
                    R.id.menu_insert_all_last -> InsertActionType.LAST
                    R.id.menu_override_all -> InsertActionType.OVERRIDE
                    R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                    R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                    R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                    else -> return false
                }
            )

            return true
        }
    }
}