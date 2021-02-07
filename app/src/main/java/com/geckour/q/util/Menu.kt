package com.geckour.q.util

import android.graphics.Color
import android.view.Menu
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat

fun Menu.setIconTint(@ColorInt tintColor: Int = Color.WHITE) {
    (0 until this.size()).forEach {
        getItem(it).icon?.apply {
            DrawableCompat.setTint(this, tintColor)
        }
    }
}