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
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.databinding.ActivitySettingBinding
import com.geckour.q.databinding.DialogEditTextBinding
import com.geckour.q.databinding.DialogSpinnerBinding
import com.geckour.q.ui.license.LicenseActivity
import com.geckour.q.util.*

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
        binding.itemChooseScreen.viewModel = chooseLaunchScreenViewModel
        binding.itemArtworkOnLockScreen.viewModel = artworkOnLockScreenViewModel
        binding.itemDucking.viewModel = duckingViewModel
        binding.itemLicense.viewModel = licenseViewModel
        binding.itemFormatPattern.viewModel = formatPatternViewModel
        binding.itemBundleArtwork.viewModel = bundleArtworkViewModel

        observeEvents()
    }

    private fun observeEvents() {
        viewModel.scrollToTop.observe(this) { binding.scrollView.smoothScrollTo(0, 0) }
    }

    private val chooseLaunchScreenViewModel: SettingItemViewModel by lazy {
        SettingItemViewModel(
                getString(R.string.setting_item_title_screen_on_launch),
                getString(R.string.setting_item_desc_screen_on_launch),
                getString(sharedPreferences.preferScreen.displayNameResId),
                false, onClick = {
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
                    setSelection(sharedPreferences.preferScreen.ordinal)
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
                        sharedPreferences.preferScreen = screen
                        summary = getString(screen.displayNameResId)
                        this@SettingActivity.binding.itemChooseScreen.viewModel = this
                    }
                }
                dialog.dismiss()
            }.show()
        })
    }

    private val artworkOnLockScreenViewModel: SettingItemViewModel by lazy {
        val state = sharedPreferences.showArtworkOnLockScreen
        SettingItemViewModel(getString(R.string.setting_item_title_artwork_on_lock_screen),
                getString(R.string.setting_item_desc_artwork_on_lock_screen),
                state.switchSummary, true, onSwitchClick = {
            sharedPreferences.showArtworkOnLockScreen = it
            summary = it.switchSummary
            binding.itemArtworkOnLockScreen.viewModel = this
            if (binding.itemArtworkOnLockScreen.state.isChecked != it)
                binding.itemArtworkOnLockScreen.state.isChecked = it
        }).apply { switchState = state }
    }

    private val duckingViewModel: SettingItemViewModel by lazy {
        val state = sharedPreferences.ducking
        SettingItemViewModel(getString(R.string.setting_item_title_ducking),
                getString(R.string.setting_item_desc_ducking),
                state.switchSummary, true, onSwitchClick = {
            sharedPreferences.ducking = it
            summary = it.switchSummary
            binding.itemDucking.viewModel = this
            if (binding.itemDucking.state.isChecked != it)
                binding.itemDucking.state.isChecked = it
        }).apply { switchState = state }
    }

    private val formatPatternViewModel: SettingItemViewModel by lazy {
        SettingItemViewModel(getString(R.string.setting_item_title_format_pattern),
                getString(R.string.setting_item_desc_format_pattern),
                this.formatPattern, false, onClick = {
            onCLickSpecifyPatternFormat()
        })
    }

    private val bundleArtworkViewModel: SettingItemViewModel by lazy {
        val state = sharedPreferences.bundleArtwork
        SettingItemViewModel(getString(R.string.setting_item_title_bundle_artwork),
                getString(R.string.setting_item_desc_bundle_artwork),
                state.switchSummary, true, onSwitchClick = {
            sharedPreferences.bundleArtwork = it
            summary = it.switchSummary
            binding.itemBundleArtwork.viewModel = this
            if (binding.itemBundleArtwork.state.isChecked != it)
                binding.itemBundleArtwork.state.isChecked = it
        }).apply { switchState = state }
    }

    private val licenseViewModel: SettingItemViewModel by lazy {
        SettingItemViewModel(getString(R.string.setting_item_title_license),
                getString(R.string.setting_item_desc_license),
                null,
                false, onClick = {
            startActivity(LicenseActivity.createIntent(this@SettingActivity))
        })
    }

    private fun onCLickSpecifyPatternFormat() {
        val patternFormatDialogBinding = DialogEditTextBinding.inflate(
                LayoutInflater.from(this),
                null,
                false
        ).apply {
            hint = getString(R.string.setting_dialog_hint_pattern_format)
            editText.setText(this@SettingActivity.formatPattern)
            editText.setSelection(editText.text.length)
        }

        AlertDialog.Builder(this).generate(
                patternFormatDialogBinding.root,
                getString(R.string.setting_dialog_title_pattern_format),
                getString(R.string.setting_dialog_message_pattern_format)
        ) { dialog, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val pattern = patternFormatDialogBinding.editText.text.toString()
                    this.formatPattern = pattern
                    binding.itemFormatPattern.viewModel =
                            formatPatternViewModel.apply { summary = pattern }
                }
            }
            dialog.dismiss()
        }.show()
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

    private val Boolean.switchSummary: String
        get() = getString(if (this) R.string.setting_switch_on else R.string.setting_switch_off)
}