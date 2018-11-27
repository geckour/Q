package com.geckour.q.util

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.setCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

abstract class ScopedActivity : AppCompatActivity(), CoroutineScope {

    protected lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        setCrashlytics()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}