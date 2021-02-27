package com.geckour.q.ui.library.playlist

import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemPlaylistBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Playlist
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

class PlaylistListAdapter(private val viewModel: MainViewModel) :
    RecyclerView.Adapter<PlaylistListAdapter.ViewHolder>() {

    private val items: ArrayList<Playlist> = ArrayList()

    internal fun setItems(items: List<Playlist>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal fun getItems(): List<Playlist> = items

    internal fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType = OrientedClassType.PLAYLIST
    ) {
        viewModel.viewModelScope.launch {
            viewModel.onNewQueue(domainTracks, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(
                    bindTo.context, it.itemId, binding.data
                )
            }
            inflate(R.menu.playlist)
        }

        fun bind() {
            val playlist = items[adapterPosition]
            binding.data = playlist
            binding.duration.text = playlist.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(playlist) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                return@setOnLongClickListener true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            viewModel.viewModelScope.launch {
                try {
                    Glide.with(binding.thumb)
                        .load(playlist.thumb.orDefaultForModel)
                        .applyDefaultSettings()
                        .into(binding.thumb)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }

        private fun Playlist.delete() {
            val deleted = binding.root.context.contentResolver.delete(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Playlists._ID}=?",
                arrayOf(this.id.toString())
            ) == 1
            if (deleted) {
                items.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
            }
        }

        private fun onOptionSelected(context: Context, id: Int, playlist: Playlist?): Boolean {
            if (playlist == null) return false

            if (id == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(context)

                    viewModel.onLoadStateChanged(true)
                    val tracks = playlist.getTrackMediaIds(context)
                        .map { it.first }
                        .let { db.trackDao().getByMediaIds(it) }
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
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                R.id.menu_delete_playlist -> {
                    playlist.delete()
                    return true
                }
                else -> return false
            }

            viewModel.viewModelScope.launch {
                viewModel.onLoadStateChanged(true)
                val tracks = playlist.getTrackMediaIds(context)
                    .sortedBy { it.second }
                    .mapNotNull {
                        getDomainTrack(DB.getInstance(context), it.first, playlistId = playlist.id)
                    }
                viewModel.onLoadStateChanged(false)

                onNewQueue(tracks, actionType, OrientedClassType.TRACK)
            }

            return true
        }
    }
}