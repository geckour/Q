package com.geckour.q.util

import android.icu.util.Calendar
import com.geckour.q.data.db.model.JoinedTrack
import java.text.SimpleDateFormat
import java.util.Locale

val JoinedTrack.dates: Triple<Int?, Int?, Int?>
    get() = track.releaseDate.releaseDates

internal val String?.releaseDates: Triple<Int?, Int?, Int?>
    get() {
        val releaseDateString = this ?: return Triple(null, null, null)

        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                null
            )
        }
        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                null,
                null
            )
        }

        return Triple(null, null, null)
    }
