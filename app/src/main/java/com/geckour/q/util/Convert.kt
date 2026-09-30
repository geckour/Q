package com.geckour.q.util

import androidx.appcompat.app.AppCompatDelegate

val Boolean.toNightModeInt: Int
    get() = if (this) AppCompatDelegate.MODE_NIGHT_YES
    else AppCompatDelegate.MODE_NIGHT_NO
