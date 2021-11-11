package com.geckour.q.ui.library.artist

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.databinding.ItemArtistBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getTimeString
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.loadOrDefault
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber

class ArtistListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<Artist, ArtistListAdapter.ViewHolder>(diffCallback),
    KoinComponent {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<Artist>() {

            override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem == newItem
        }
    }

    internal fun onArtistDeleted(artistId: Long) {
        removeItem(artistId)
    }

    private fun removeItem(artistId: Long) {
        submitList(currentList.dropWhile { it.id == artistId })
    }

    internal fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType = OrientedClassType.ARTIST
    ) {
        viewModel.viewModelScope.launch {
            viewModel.onNewQueue(domainTracks, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(
                    bindTo.context, it.itemId, binding.data
                )
            }
            inflate(R.menu.albums)
        }

        fun bind() {
            val artist = getItem(adapterPosition)
            binding.data = artist
            binding.duration.text = artist.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(artist) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            binding.thumb.loadOrDefault(artist.artworkUriString)
        }

        private fun onOptionSelected(context: Context, id: Int, artist: Artist?): Boolean {
            if (artist == null) return false

            if (id == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(context)

                    viewModel.onLoadStateChanged(true)
                    val tracks = db.trackDao()
                        .getAllByArtist(artist.id)
                    viewModel.onLoadStateChanged(false)

                    context.showFileMetadataUpdateDialog(tracks) { binding ->
                        viewModel.viewModelScope.launch {
                            viewModel.onLoadStateChanged(true)
                            binding.updateFileMetadata(context, db, tracks)
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
                R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            viewModel.viewModelScope.launch {
                val sortByTrackOrder = id.let {
                    it != R.id.menu_insert_all_simple_shuffle_next || it != R.id.menu_insert_all_simple_shuffle_last || it != R.id.menu_override_all_simple_shuffle
                }

                viewModel.onLoadStateChanged(true)
                val tracks = DB.getInstance(context).let { db ->
                    val sharedPreferences = get<SharedPreferences>()
                    db.trackDao()
                        .getAllByArtist(
                            artist.id,
                            BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                        )
                        .map { it.toDomainTrack() }
                        .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                }
                viewModel.onLoadStateChanged(false)

                onNewQueue(tracks, actionType, OrientedClassType.ALBUM)
            }

            return true
        }
    }
}