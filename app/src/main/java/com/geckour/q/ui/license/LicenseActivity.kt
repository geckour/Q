package com.geckour.q.ui.license

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LicenseActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, LicenseActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: レイアウト作成
    }
}