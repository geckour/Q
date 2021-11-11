package com.geckour.q.ui.license

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.geckour.q.R
import com.geckour.q.databinding.ActivityLicenseBinding
import com.geckour.q.domain.model.LicenseItem
import com.geckour.q.util.isNightMode
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt
import com.geckour.q.util.toggleDayNight
import org.koin.android.ext.android.inject

class LicenseActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, LicenseActivity::class.java)
    }

    private lateinit var binding: ActivityLicenseBinding
    private val sharedPreferences by inject<SharedPreferences>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        delegate.localNightMode = sharedPreferences.isNightMode.toNightModeInt

        binding = DataBindingUtil.setContentView(this, R.layout.activity_license)
        binding.recyclerView.adapter = LicenseListAdapter(
            listOf(
                LicenseItem(
                    getString(R.string.license_name_coroutines),
                    getString(R.string.license_text_coroutines)
                ), LicenseItem(
                    getString(R.string.license_name_androidx),
                    getString(R.string.license_text_androidx)
                ), LicenseItem(
                    getString(R.string.license_name_binding),
                    getString(R.string.license_text_binding)
                ), LicenseItem(
                    getString(R.string.license_name_timber), getString(R.string.license_text_timber)
                ), LicenseItem(
                    getString(R.string.license_name_stetho), getString(R.string.license_text_stetho)
                ), LicenseItem(
                    getString(R.string.license_name_gson), getString(R.string.license_text_gson)
                ), LicenseItem(
                    getString(R.string.license_name_aac), getString(R.string.license_text_aac)
                ), LicenseItem(
                    getString(R.string.license_name_permission),
                    getString(R.string.license_text_permission)
                ), LicenseItem(
                    getString(R.string.license_name_glide), getString(R.string.license_text_glide)
                ), LicenseItem(
                    getString(R.string.license_name_exo), getString(R.string.license_text_exo)
                ), LicenseItem(
                    getString(R.string.license_name_seek_bar),
                    getString(R.string.license_text_seek_bar)
                )
            )
        )
        binding.toolbar.setOnClickListener { binding.recyclerView.smoothScrollToPosition(0) }

        setSupportActionBar(binding.toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toggle_theme_toolbar, menu)
        menu.setIconTint()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_daynight -> toggleDayNight(sharedPreferences)
            else -> return false
        }
        return true
    }
}