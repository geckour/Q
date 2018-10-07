package com.geckour.q.ui.equalizer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.databinding.FragmentEqualizerBinding
import com.geckour.q.databinding.ItemEqualizerSeekBarBinding
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.*
import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBar

class EqualizerFragment : Fragment() {

    companion object {
        const val ACTION_EQUALIZER_STATE = "action_equalizer_state"
        const val EXTRA_KEY_EQUALIZER_ENABLED = "extra_key_equalizer_enabled"

        fun newInstance(): EqualizerFragment = EqualizerFragment()
    }

    private lateinit var binding: FragmentEqualizerBinding
    private val viewModel: EqualizerViewModel by lazy {
        ViewModelProviders.of(requireActivity())[EqualizerViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private var changeEnabledTo: Boolean? = null

    private var initialSettingState: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentEqualizerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            initialSettingState = sharedPreferences.equalizerEnabled
            if (initialSettingState) sendCommand(SettingCommand.SET_EQUALIZER)
        }

        observeEvents()

        binding.viewModel = viewModel
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.resumedFragmentId.value = R.id.nav_equalizer

        inflateSeekBars()
    }

    override fun onStop() {
        super.onStop()
        if (initialSettingState.not())
            sharedPreferences.equalizerEnabled = viewModel.enabled
    }

    private fun observeEvents() {
        viewModel.toggleEnabled.observe(this) {
            val changeTo = viewModel.enabled.not()
            sharedPreferences.equalizerEnabled = changeTo
            changeEnabledTo = changeTo
            sendCommand(
                    if (changeTo) SettingCommand.SET_EQUALIZER
                    else SettingCommand.UNSET_EQUALIZER)
        }

        viewModel.flatten.observe(this) { flatten() }

        viewModel.equalizerState.observe(this) {
            if (it == null) return@observe

            if (changeEnabledTo == true && it.not())
                Toast.makeText(requireContext(),
                        R.string.equalizer_message_error_turn_on, Toast.LENGTH_LONG).show()

            if (viewModel.enabled != it) {
                viewModel.enabled = it
                binding.viewModel = viewModel
            }
            changeEnabledTo = null
        }
    }

    private fun inflateSeekBars() {
        binding.seekBarContainer.removeAllViews()

        sharedPreferences.equalizerParams?.also { params ->
            val levels = sharedPreferences.equalizerSettings?.levels
            params.bands.forEachIndexed { i, band ->
                ItemEqualizerSeekBarBinding.inflate(layoutInflater,
                        binding.seekBarContainer, false).apply {
                    seekBarLabel.text =
                            getString(R.string.equalizer_seek_bar_label,
                                    (band.centerFreq / 1000f).getReadableString())
                    seekBar.progress =
                            if (levels != null)
                                seekBar.calcProgress(params, levels[i])
                            else seekBar.max / 2
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
        }
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
        activity?.apply { startService(getCommandIntent(this, command)) }
    }

    private fun getCommandIntent(context: Context, command: SettingCommand): Intent =
            PlayerService.createIntent(context).apply {
                action = command.name
                putExtra(PlayerService.ARGS_KEY_SETTING_COMMAND, command.ordinal)
            }

    private fun EqualizerParams.normalizeLevel(ratio: Float): Int =
            this.levelRange.first +
                    ((this.levelRange.second - this.levelRange.first) * ratio).toInt()

    private fun SeekBar.calcProgress(params: EqualizerParams, level: Int): Int =
            ((level - params.levelRange.first).toFloat() * this.max / params.levelRange.let { it.second - it.first }).toInt()
}