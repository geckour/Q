package com.geckour.q.ui.equalizer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.databinding.ActivityEqualizerBinding
import com.geckour.q.databinding.ItemEqualizerSeekBarBinding
import com.geckour.q.service.PlayerService
import com.geckour.q.util.*
import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBar

class EqualizerActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent =
                Intent(context, EqualizerActivity::class.java)
    }

    private lateinit var binding: ActivityEqualizerBinding
    private val viewModel: EqualizerViewModel by lazy {
        ViewModelProviders.of(this)[EqualizerViewModel::class.java]
    }
    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.enabled.value = sharedPreferences.equalizerEnabled
        binding = DataBindingUtil.setContentView(this, R.layout.activity_equalizer)
        binding.viewModel = viewModel

        inflateSeekBars()

        observeEvents()
    }

    private fun observeEvents() {
        viewModel.enabled.observe(this) {
            if (it == null) return@observe
            sharedPreferences.equalizerEnabled = it
            sendCommand(
                    if (it) SettingCommand.SET_EQUALIZER
                    else SettingCommand.UNSET_EQUALIZER)
        }

        viewModel.flatten.observe(this) { flatten() }

        viewModel.requireRebindSelf.observe(this) {
            binding.viewModel = binding.viewModel
        }
    }

    private fun inflateSeekBars() {
        sharedPreferences.equalizerParams?.also { params ->
            val levels = sharedPreferences.equalizerSettings?.levels
            params.bands.forEachIndexed { i, band ->
                ItemEqualizerSeekBarBinding.inflate(layoutInflater,
                        binding.seekBarContainer, false).apply {
                    seekBarLabel.text =
                            getString(R.string.equalizer_seek_bar_label,
                                    band.centerFreq.toFloat().getReadableString())
                    if (levels != null) {
                        seekBar.progress = seekBar.calcProgress(params, levels[i])
                    }
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar,
                                                       progress: Int, fromUser: Boolean) {
                            val level = params.normalizeLevel(progress.toFloat() / seekBar.max)
                            sharedPreferences.setEqualizerLevel(i, level)
                            sendCommand(SettingCommand.REFLECT_EQUALIZER_SETTING)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            if (seekBar == null) return
                            val level = params.normalizeLevel(seekBar.progress.toFloat() / seekBar.max)
                            seekBar.progress = seekBar.calcProgress(params, level)
                            sharedPreferences.setEqualizerLevel(i, level)
                            sendCommand(SettingCommand.REFLECT_EQUALIZER_SETTING)
                        }
                    })
                    binding.seekBarContainer.addView(this.root)
                }
            }
        } ?: binding.seekBarContainer.removeAllViews()
    }

    private fun flatten() {
        (0 until binding.seekBarContainer.childCount).forEach {
            binding.seekBarContainer.getChildAt(it)
                    .findViewById<VerticalSeekBar>(R.id.seek_bar)?.apply {
                        progress = max / 2
                    }
        }
        sharedPreferences.equalizerSettings =
                EqualizerSettings(sharedPreferences.equalizerSettings?.levels?.map { 0 } ?: return)
    }

    private fun sendCommand(command: SettingCommand) {
        startService(getCommandIntent(command))
    }

    private fun getCommandIntent(command: SettingCommand): Intent =
            PlayerService.createIntent(this).apply {
                action = command.name
                putExtra(PlayerService.ARGS_KEY_SETTING_COMMAND, command.ordinal)
            }

    private fun EqualizerParams.normalizeLevel(ratio: Float): Int =
            this.levelRange.first +
                    ((this.levelRange.second - this.levelRange.first) * ratio).toInt()

    private fun SeekBar.calcProgress(params: EqualizerParams, level: Int): Int =
            ((level - params.levelRange.first).toFloat() * this.max / params.levelRange.let { it.second - it.first }).toInt()
}