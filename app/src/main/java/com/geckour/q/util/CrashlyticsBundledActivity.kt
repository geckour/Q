package com.geckour.q.util

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.setCrashlytics

abstract class CrashlyticsBundledActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCrashlytics()
    }
}