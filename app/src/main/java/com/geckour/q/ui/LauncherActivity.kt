package com.geckour.q.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.setCrashlytics
import com.geckour.q.ui.main.MainActivity

class LauncherActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, LauncherActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCrashlytics()

        if (isTaskRoot) startActivity(MainActivity.createIntent(this))
        finish()
    }
}