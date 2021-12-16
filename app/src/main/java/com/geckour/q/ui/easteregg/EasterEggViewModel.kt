package com.geckour.q.ui.easteregg

import androidx.lifecycle.ViewModel
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class EasterEggViewModel(private val db: DB) : ViewModel() {

    val track: Flow<DomainTrack>

    init {
        val seed = Calendar.getInstance(TimeZone.getDefault())
            .let { it.get(Calendar.YEAR) * 1000L + it.get(Calendar.DAY_OF_YEAR) }

        track = db.albumDao().getAllAsync()
            .map { albums ->
                val random = Random(seed)
                db.trackDao().getAllByAlbum(albums[random.nextInt(albums.size)].album.id)
                    .let { it[random.nextInt(it.size)] }.toDomainTrack()
            }
    }
}