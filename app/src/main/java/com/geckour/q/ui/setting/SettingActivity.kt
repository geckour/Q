package com.geckour.q.ui.setting

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.databinding.ActivitySettingBinding
import com.geckour.q.databinding.DialogSpinnerBinding
import com.geckour.q.ui.license.LicenseActivity
import com.geckour.q.util.Screen
import com.geckour.q.util.getPreferScreen
import com.geckour.q.util.setPreferScreen

class SettingActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SettingActivity::class.java)
    }

    private val viewModel: SettingViewModel by lazy {
        ViewModelProviders.of(this)[SettingViewModel::class.java]
    }
    private lateinit var binding: ActivitySettingBinding
    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_setting)
        binding.itemChooseScreen.apply {
            viewModel = getChooseLaunchScreenViewModel()
        }
        binding.itemLicense.viewModel = getLicenseViewModel()

        observeEvents()
    }

    private fun observeEvents() {
        viewModel.scrollToTop.observe(this, Observer {
            binding.scrollView.smoothScrollTo(0, 0)
        })
    }

    private fun getChooseLaunchScreenViewModel(): SettingItemViewModel =
            SettingItemViewModel(
                    getString(R.string.setting_item_title_screen_on_launch),
                    getString(R.string.setting_item_desc_screen_on_launch),
                    getString(sharedPreferences.getPreferScreen().displayNameResId),
                    false) {
                val binding = DialogSpinnerBinding.inflate(
                        LayoutInflater.from(this@SettingActivity), null, false).apply {
                    val arrayAdapter =
                            object : ArrayAdapter<String>(
                                    this@SettingActivity,
                                    android.R.layout.simple_spinner_item,
                                    Screen.values().map { getString(it.displayNameResId) }) {
                                override fun getDropDownView(position: Int,
                                                             convertView: View?,
                                                             parent: ViewGroup): View =
                                        super.getDropDownView(position, convertView, parent).apply {
                                            if (position == spinner.selectedItemPosition) {
                                                (this as TextView).setTextColor(
                                                        getColor(R.color.colorPrimaryDark))
                                            }
                                        }
                            }.apply {
                                setDropDownViewResource(
                                        android.R.layout.simple_spinner_dropdown_item)
                            }
                    spinner.apply {
                        adapter = arrayAdapter
                        setSelection(sharedPreferences.getPreferScreen().ordinal)
                    }
                }

                AlertDialog.Builder(this@SettingActivity).generate(
                        binding.root,
                        getString(R.string.dialog_title_choose_screen_on_launch),
                        getString(R.string.dialog_desc_choose_screen_on_launch)) { dialog, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            val screenIndex = binding.spinner.selectedItemPosition
                            val screen = Screen.values()[screenIndex]
                            sharedPreferences.setPreferScreen(screen)
                            summary = getString(screen.displayNameResId)
                            this@SettingActivity.binding.itemChooseScreen.viewModel = this
                        }
                    }
                    dialog.dismiss()
                }.show()
            }

    private fun getLicenseViewModel(): SettingItemViewModel =
            SettingItemViewModel(getString(R.string.setting_item_title_license),
                    getString(R.string.setting_item_desc_license),
                    null,
                    false) {
                startActivity(LicenseActivity.createIntent(this@SettingActivity))
            }

    private fun AlertDialog.Builder.generate(
            view: View,
            title: String,
            message: String? = null,
            callback: (dialog: DialogInterface, which: Int) -> Unit = { _, _ -> }): AlertDialog {
        setTitle(title)
        if (message != null) setMessage(message)
        setView(view)
        setPositiveButton(R.string.dialog_ok) { dialog, which -> callback(dialog, which) }
        setNegativeButton(R.string.dialog_ng) { dialog, _ -> dialog.dismiss() }

        return create()
    }
}