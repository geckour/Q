package com.geckour.q.ui.library.track

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.databinding.ItemTrackBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber

class TrackListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<DomainTrack, TrackListAdapter.ViewHolder>(diffCallback),
    KoinComponent {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<DomainTrack>() {

            override fun areItemsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DomainTrack, newItem: DomainTrack): Boolean =
                oldItem == newItem
        }
    }

    private fun removeItem(trackId: Long) {
        submitList(currentList.dropLastWhile { it.id == trackId })
    }

    internal fun removeByTrackNum(trackNum: Int) {
        submitList(currentList.dropWhile { it.trackNum == trackNum })
    }

    internal fun clearItems() {
        submitList(null)
    }

    internal fun onNewQueue(actionType: InsertActionType) {
        val sharedPreferences = get<SharedPreferences>()
        viewModel.onNewQueue(
            currentList.let {
                if (sharedPreferences.ignoringEnabled) it.filter { it.ignored != true }
                else it
            }, actionType, OrientedClassType.TRACK
        )
    }

    internal fun onTrackDeleted(id: Long) {
        removeItem(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val shortPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedDomainTrack?.apply {
                            viewModel.onNewQueue(
                                listOf(this), when (menuItem.itemId) {
                                    R.id.menu_insert_all_next -> InsertActionType.NEXT
                                    R.id.menu_insert_all_last -> InsertActionType.LAST
                                    R.id.menu_override_all -> InsertActionType.OVERRIDE
                                    else -> return@setOnMenuItemClickListener false
                                }, OrientedClassType.TRACK
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                    R.id.menu_edit_metadata -> {
                        viewModel.selectedDomainTrack?.id?.let { trackId ->
                            viewModel.viewModelScope.launch {
                                val db = DB.getInstance(binding.root.context)

                                viewModel.onLoadStateChanged(true)
                                val tracks =
                                    db.trackDao().get(trackId)?.let { listOf(it) }.orEmpty()
                                viewModel.onLoadStateChanged(false)

                                binding.root.context.showFileMetadataUpdateDialog(tracks) { binding ->
                                    viewModel.viewModelScope.launch {
                                        viewModel.onLoadStateChanged(true)
                                        binding.updateFileMetadata(binding.root.context, db, tracks)
                                        viewModel.onLoadStateChanged(false)
                                    }
                                }
                            }
                        }
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    else -> return@setOnMenuItemClickListener false
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.track)
        }

        private val longPopupMenu = PopupMenu(binding.root.context, binding.root).apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_transition_to_artist -> {
                        viewModel.selectedArtist.value = binding.data?.artist
                    }
                    R.id.menu_transition_to_album -> {
                        viewModel.selectedAlbum.value = binding.data?.album
                    }
                    R.id.menu_ignore -> toggleIgnored()
                    R.id.menu_edit_metadata -> {
                        viewModel.selectedDomainTrack?.id?.let { trackId ->
                            viewModel.viewModelScope.launch {
                                val db = DB.getInstance(binding.root.context)

                                viewModel.onLoadStateChanged(true)
                                val tracks =
                                    db.trackDao().get(trackId)?.let { listOf(it) }.orEmpty()
                                viewModel.onLoadStateChanged(false)

                                binding.root.context.showFileMetadataUpdateDialog(tracks) { binding ->
                                    viewModel.viewModelScope.launch {
                                        viewModel.onLoadStateChanged(true)
                                        binding.updateFileMetadata(binding.root.context, db, tracks)
                                        viewModel.onLoadStateChanged(false)
                                    }
                                }
                            }
                        }
                    }
                    R.id.menu_delete_track -> {
                        viewModel.selectedDomainTrack?.let { viewModel.deleteTrack(it) }
                    }
                    R.id.menu_insert_all_next, R.id.menu_insert_all_last, R.id.menu_override_all -> {
                        viewModel.selectedDomainTrack?.apply {
                            viewModel.onNewQueue(
                                listOf(this), when (menuItem.itemId) {
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
                                }, OrientedClassType.TRACK
                            )
                        } ?: return@setOnMenuItemClickListener false
                    }
                }

                return@setOnMenuItemClickListener true
            }
            inflate(R.menu.track_long)
        }

        fun bind() {
            val track = getItem(adapterPosition)
            binding.data = track
            binding.duration.text = track.durationString
            viewModel.viewModelScope.launch {
                try {
                    Glide.with(binding.thumb)
                        .load(track.artworkUriString.orDefaultForModel)
                        .applyDefaultSettings()
                        .into(binding.thumb)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            binding.root.setOnClickListener { onTrackSelected(track) }
            binding.root.setOnLongClickListener { onTrackLongTapped(track) }
        }

        private fun onTrackSelected(domainTrack: DomainTrack) {
            viewModel.onRequestNavigate(domainTrack)
            shortPopupMenu.show()
            shortPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.getString(
                if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                else R.string.menu_ignore_to_true
            )
        }

        private fun onTrackLongTapped(domainTrack: DomainTrack): Boolean {
            viewModel.onRequestNavigate(domainTrack)
            longPopupMenu.show()
            longPopupMenu.menu.findItem(R.id.menu_ignore).title = binding.root.context.getString(
                if (binding.data?.ignored == true) R.string.menu_ignore_to_false
                else R.string.menu_ignore_to_true
            )

            return true
        }

        private fun toggleIgnored() {
            binding.data?.id?.also { trackId ->
                viewModel.viewModelScope.launch {
                    DB.getInstance(binding.root.context).trackDao().apply {
                        val ignored = when (this.get(trackId)?.track?.ignored ?: Bool.FALSE) {
                            Bool.TRUE -> Bool.FALSE
                            Bool.FALSE -> Bool.TRUE
                            Bool.UNDEFINED -> Bool.UNDEFINED
                        }
                        setIgnored(trackId, ignored)

                        binding.data = binding.data?.let { it.copy(ignored = it.ignored?.not()) }
                    }
                }
            }
        }
    }
}