package com.geckour.q.ui.easteregg

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.launch
import java.util.*

class EasterEggViewModel(application: Application) : AndroidViewModel(application) {

    val song = MutableLiveData<DomainTrack>()

    init {
        viewModelScope.launch { pickupSong() }
    }

    private suspend fun pickupSong() {
        val seed = Calendar.getInstance(TimeZone.getDefault())
            .let { it.get(Calendar.YEAR) * 1000L + it.get(Calendar.DAY_OF_YEAR) }
        val track = DB.getInstance(getApplication()).trackDao()
            .getAll()
            .let { it[Random(seed).nextInt(it.size)] }
        song.value = track.toDomainTrack()
    }
}