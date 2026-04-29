package com.geckour.q.ui.license

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.R
import com.geckour.q.domain.model.LicenseItem
import kotlinx.collections.immutable.persistentListOf

class LicenseActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, LicenseActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = persistentListOf(
            LicenseItem(
                getString(R.string.license_name_koin),
                getString(R.string.license_text_koin)
            ),
            LicenseItem(
                getString(R.string.license_name_coroutines),
                getString(R.string.license_text_coroutines)
            ),
            LicenseItem(
                getString(R.string.license_name_androidx),
                getString(R.string.license_text_androidx)
            ),
            LicenseItem(
                getString(R.string.license_name_binding),
                getString(R.string.license_text_binding)
            ),
            LicenseItem(
                getString(R.string.license_name_timber), getString(R.string.license_text_timber)
            ),
            LicenseItem(
                getString(R.string.license_name_json), getString(R.string.license_text_json)
            ),
            LicenseItem(
                getString(R.string.license_name_aac), getString(R.string.license_text_aac)
            ),
            LicenseItem(
                getString(R.string.license_name_permission),
                getString(R.string.license_text_permission)
            ),
            LicenseItem(
                getString(R.string.license_name_coil), getString(R.string.license_text_coil)
            ),
            LicenseItem(
                getString(R.string.license_name_exo), getString(R.string.license_text_exo)
            ),
            LicenseItem(
                getString(R.string.license_name_seek_bar),
                getString(R.string.license_text_seek_bar)
            ),
            LicenseItem(
                getString(R.string.license_name_dropbox),
                getString(R.string.license_text_dropbox)
            ),
            LicenseItem(
                getString(R.string.license_name_codec),
                getString(R.string.license_text_codec)
            ),
        )

        // TODO: Show items on the screen
    }
}