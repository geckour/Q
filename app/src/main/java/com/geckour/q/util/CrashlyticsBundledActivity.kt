package com.geckour.q.util

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import com.geckour.q.setCrashlytics

abstract class CrashlyticsBundledActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCrashlytics()
    }
}

fun Menu.setIconTint(@ColorInt tintColor: Int = Color.WHITE) {
    (0 until this.size()).forEach {
        getItem(it).icon?.apply {
            DrawableCompat.setTint(this, tintColor)
        }
    }
}