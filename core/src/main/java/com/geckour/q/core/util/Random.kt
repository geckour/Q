package com.geckour.q.core.util

import android.icu.util.Calendar
import android.icu.util.TimeZone
import kotlin.random.Random

val dailyRandom: Random
    get() = Calendar.getInstance(TimeZone.getDefault()).let {
        Random(it.get(Calendar.YEAR) * 1000L + it.get(Calendar.DAY_OF_YEAR))
    }
