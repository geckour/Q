package com.geckour.q.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.presentation.main.MainActivity

class LauncherActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, LauncherActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isTaskRoot) startActivity(MainActivity.createIntent(this))
        finish()
    }
}