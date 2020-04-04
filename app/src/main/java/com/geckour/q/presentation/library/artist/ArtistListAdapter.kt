package com.geckour.q.presentation.library.artist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ItemListArtistBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.getSong
import com.geckour.q.util.getTimeString
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.sortedByTrackOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ArtistListAdapter(private val viewModel: MainViewModel) :
    ListAdapter<Artist, ArtistListAdapter.ViewHolder>(diffCallback) {

    companion object {

        val diffCallback = object : DiffUtil.ItemCallback<Artist>() {

            override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem == newItem
        }
    }

    override fun submitList(list: List<Artist>?) {
        super.submitList(
            list?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nameSort })
        )
    }

    internal fun onArtistDeleted(artistId: Long) {
        removeItem(artistId)
    }

    private fun removeItem(artistId: Long) {
        submitList(currentList.dropWhile { it.id == artistId })
    }

    internal fun onNewQueue(
        songs: List<Song>,
        actionType: InsertActionType,
        classType: OrientedClassType = OrientedClassType.ARTIST
    ) {
        viewModel.viewModelScope.launch {
            viewModel.onNewQueue(songs, actionType, classType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemListArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }


    inner class ViewHolder(private val binding: ItemListArtistBinding) :
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
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Glide.with(binding.thumb)
                            .load(artist.thumbUriString.orDefaultForModel)
                            .applyDefaultSettings()
                            .into(binding.thumb)
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

            viewModel.viewModelScope.launch {
                val sortByTrackOrder = id.let {
                    it != R.id.menu_insert_all_simple_shuffle_next || it != R.id.menu_insert_all_simple_shuffle_last || it != R.id.menu_override_all_simple_shuffle
                }
                val songs = withContext(Dispatchers.IO) {
                    DB.getInstance(context).let { db ->
                        val sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(context)
                        viewModel.loading.postValue(true)
                        db.albumDao().findByArtistId(artist.id).map {
                            db.trackDao().findByAlbum(
                                it.id,
                                BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                            ).mapNotNull { getSong(db, it) }.let {
                                if (sortByTrackOrder) it.sortedByTrackOrder()
                                else it
                            }
                        }.apply {
                            viewModel.loading.postValue(false)
                        }.flatten()
                    }
                }

                onNewQueue(songs, actionType, OrientedClassType.ALBUM)
            }

            return true
        }
    }
}