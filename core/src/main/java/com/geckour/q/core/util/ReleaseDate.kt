package com.geckour.q.core.util

import android.icu.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

private val releaseDateFormats = listOf("yyyy-MM-dd", "yyyy-MM", "yyyy")

val String?.releaseDates: Triple<Int?, Int?, Int?>
    get() {
        val releaseDateString = this ?: return Triple(null, null, null)

        releaseDateFormats.forEach { format ->
            val date = runCatching {
                SimpleDateFormat(format, Locale.JAPAN).parse(releaseDateString)
            }.getOrNull() ?: return@forEach
            val calendar = Calendar.getInstance().apply { time = date }
            return Triple(
                calendar.get(Calendar.YEAR),
                if (format.contains("MM")) calendar.get(Calendar.MONTH) + 1 else null,
                if (format.contains("dd")) calendar.get(Calendar.DAY_OF_MONTH) else null
            )
        }

        return Triple(null, null, null)
    }
