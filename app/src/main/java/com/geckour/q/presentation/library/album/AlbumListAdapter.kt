package com.geckour.q.presentation.library.album

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
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.databinding.ItemAlbumBinding
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.getTimeString
import com.geckour.q.util.orDefaultForModel
import kotlinx.coroutines.launch
import timber.log.Timber

class AlbumListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<JoinedAlbum, AlbumListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<JoinedAlbum>() {

            override fun areItemsTheSame(oldItem: JoinedAlbum, newItem: JoinedAlbum): Boolean =
                oldItem.album.id == newItem.album.id

            override fun areContentsTheSame(oldItem: JoinedAlbum, newItem: JoinedAlbum): Boolean =
                oldItem == newItem
        }
    }

    override fun submitList(list: List<JoinedAlbum>?) {
        super.submitList(
            list?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album.titleSort })
        )
    }

    private fun removeItem(albumId: Long) {
        submitList(currentList.dropWhile { it.album.id == albumId })
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
        ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun getPopupMenu(bindTo: View) = PopupMenu(bindTo.context, bindTo).apply {
            setOnMenuItemClickListener {
                return@setOnMenuItemClickListener onOptionSelected(it.itemId, binding.data)
            }
            inflate(R.menu.songs)
        }

        fun bind() {
            val joinedAlbum = getItem(adapterPosition)
            binding.data = joinedAlbum
            binding.duration.text = joinedAlbum.album.totalDuration.getTimeString()
            binding.root.setOnClickListener { viewModel.onRequestNavigate(joinedAlbum.album) }
            binding.root.setOnLongClickListener {
                getPopupMenu(it).show()
                true
            }
            binding.option.setOnClickListener { getPopupMenu(it).show() }
            viewModel.viewModelScope.launch {
                try {
                    Glide.with(binding.thumb)
                        .load(joinedAlbum.album.artworkUriString.orDefaultForModel)
                        .applyDefaultSettings()
                        .into(binding.thumb)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }

        private fun onOptionSelected(id: Int, joinedAlbum: JoinedAlbum?): Boolean {
            if (joinedAlbum == null) return false

            val actionType = when (id) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            val sortByTrackOrder = id !in listOf(
                R.id.menu_insert_all_simple_shuffle_next,
                R.id.menu_insert_all_simple_shuffle_last,
                R.id.menu_override_all_simple_shuffle
            )

            viewModel.onSongMenuAction(actionType, joinedAlbum.album, sortByTrackOrder)

            return true
        }
    }
}