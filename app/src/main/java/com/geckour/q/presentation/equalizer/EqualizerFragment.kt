package com.geckour.q.presentation.equalizer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.geckour.q.R
import com.geckour.q.databinding.FragmentEqualizerBinding
import com.geckour.q.databinding.ItemEqualizerSeekBarBinding
import com.geckour.q.service.PlayerService
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.EqualizerSettings
import com.geckour.q.util.SettingCommand
import com.geckour.q.util.equalizerEnabled
import com.geckour.q.util.equalizerParams
import com.geckour.q.util.equalizerSettings
import com.geckour.q.util.getReadableString
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setEqualizerLevel
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt
import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBar

class EqualizerFragment : Fragment() {

    companion object {
        fun newInstance(): EqualizerFragment = EqualizerFragment()
    }

    private lateinit var binding: FragmentEqualizerBinding
    private val viewModel: EqualizerViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private var initialStoredState = false
    private var errorThrown = false

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEqualizerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        initialStoredState = sharedPreferences.equalizerEnabled
        if (initialStoredState) sendCommand(SettingCommand.SET_EQUALIZER)

        observeEvents()

        binding.viewModel = viewModel
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_equalizer

        inflateSeekBars()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.toggle_theme_toolbar, menu)

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_daynight -> {
                val sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                val toggleTo = sharedPreferences.isNightMode.not()
                sharedPreferences.isNightMode = toggleTo
                (requireActivity() as CrashlyticsBundledActivity).delegate.localNightMode =
                        toggleTo.toNightModeInt
            }
            else -> return false
        }
        return true
    }

    override fun onStop() {
        super.onStop()

        if (errorThrown) sharedPreferences.equalizerEnabled = initialStoredState
    }

    private fun observeEvents() {
        mainViewModel.player.observe(this) { player ->
            player ?: return@observe

            player.setOnEqualizerStateChangedListener {
                onEqualizerStateChanged(it)
            }
        }

        viewModel.enabled.observe(this) {
            it ?: return@observe

            val changeTo = it.not()
            sharedPreferences.equalizerEnabled = changeTo
            sendCommand(
                    if (changeTo) SettingCommand.SET_EQUALIZER
                    else SettingCommand.UNSET_EQUALIZER
            )
        }

        viewModel.flatten.observe(this) { flatten() }
    }

    private fun onEqualizerStateChanged(state: Boolean) {
        errorThrown = sharedPreferences.equalizerEnabled && state.not()
        if (errorThrown) {
            Toast.makeText(
                    requireContext(),
                    R.string.equalizer_message_error_turn_on,
                    Toast.LENGTH_LONG
            ).show()
        }

        if (viewModel.enabled.value != state) {
            viewModel.enabled.value = state
            binding.viewModel = viewModel
        }
    }

    private fun inflateSeekBars() {
        binding.seekBarContainer.removeAllViews()

        sharedPreferences.equalizerParams?.also { params ->
            val levels = sharedPreferences.equalizerSettings?.levels
            binding.textScaleBottom.text = getString(
                    R.string.equalizer_scale_label, (params.levelRange.first / 100f).getReadableString()
            )
            binding.textScaleLowerMiddle.text = getString(
                    R.string.equalizer_scale_label, (params.levelRange.first / 200f).getReadableString()
            )
            binding.textScaleUpperMiddle.text = getString(
                    R.string.equalizer_scale_label,
                    (params.levelRange.second / 200f).getReadableString()
            )
            binding.textScaleTop.text = getString(
                    R.string.equalizer_scale_label,
                    (params.levelRange.second / 100f).getReadableString()
            )
            params.bands.forEachIndexed { i, band ->
                ItemEqualizerSeekBarBinding.inflate(
                        layoutInflater, binding.seekBarContainer, false
                ).apply {
                    seekBar.max = params.levelRange.let { it.second - it.first }
                    seekBarLabel.text = getString(
                            R.string.equalizer_seek_bar_label,
                            (band.centerFreq / 1000f).getReadableString()
                    )
                    seekBar.progress = if (levels != null) seekBar.calcProgress(params, levels[i])
                    else seekBar.max / 2
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                                seekBar: SeekBar, progress: Int, fromUser: Boolean
                        ) {
                            val level = params.normalizeLevel(progress.toFloat() / seekBar.max)
                            sharedPreferences.setEqualizerLevel(i, level)
                            sendCommand(SettingCommand.REFLECT_EQUALIZER_SETTING)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            if (seekBar == null) return
                            val level =
                                    params.normalizeLevel(seekBar.progress.toFloat() / seekBar.max)
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
            binding.seekBarContainer.getChildAt(it).findViewById<VerticalSeekBar>(R.id.seek_bar)
                    ?.apply {
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
            this.levelRange.first + ((this.levelRange.second - this.levelRange.first) * ratio).toInt()

    private fun SeekBar.calcProgress(params: EqualizerParams, level: Int): Int =
            ((level - params.levelRange.first).toFloat() * this.max / params.levelRange.let { it.second - it.first }).toInt()
}