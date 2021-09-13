package com.geckour.q.ui.library.genre

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemGenreBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.getDomainTrack
import com.geckour.q.util.getTimeString
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import timber.log.Timber

class GenreListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<Genre, GenreListAdapter.ViewHolder>(
        object : DiffUtil.ItemCallback<Genre>() {
            override fun areItemsTheSame(oldItem: Genre, newItem: Genre): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Genre, newItem: Genre): Boolean =
                oldItem == newItem
        }
    ) {

    private val items: ArrayList<Genre> = ArrayList()

    internal fun getItems(): List<Genre> = items

    internal fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType = OrientedClassType.GENRE
    ) {
        viewModel.viewModelScope.launch {
            viewModel.onNewQueue(domainTracks, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemGenreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun getItemCount(): Int = items.size

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
            inflate(R.menu.tracks)
        }

        fun bind() {
            val genre = items[adapterPosition]
            binding.data = genre
            binding.duration.text = genre.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(genre) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            viewModel.viewModelScope.launch {
                try {
                    Glide.with(binding.thumb)
                        .load(genre.thumb.orDefaultForModel)
                        .applyDefaultSettings()
                        .into(binding.thumb)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }

        private fun onOptionSelected(context: Context, id: Int, genre: Genre?): Boolean {
            if (genre == null) return false

            if (id == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(binding.root.context)

                    viewModel.onLoadStateChanged(true)
                    val tracks = genre.getTrackMediaIds(context)
                        .mapNotNull { db.trackDao().getByMediaId(it) }
                    viewModel.onLoadStateChanged(false)

                    binding.root.context.showFileMetadataUpdateDialog(tracks) { binding ->
                        viewModel.viewModelScope.launch {
                            viewModel.onLoadStateChanged(true)
                            binding.updateFileMetadata(binding.root.context, db, tracks)
                            viewModel.onLoadStateChanged(false)
                        }
                    }
                }
                return true
            }

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
                viewModel.onLoadStateChanged(true)
                val tracks = genre.getTrackMediaIds(context)
                    .mapNotNull {
                        getDomainTrack(DB.getInstance(context), it, genreId = genre.id)
                    }
                viewModel.onLoadStateChanged(false)

                onNewQueue(tracks, actionType, OrientedClassType.TRACK)
            }

            return true
        }
    }
}