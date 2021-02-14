package com.geckour.q.ui.instant

import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.geckour.q.R
import com.geckour.q.databinding.ActivityInstantPlayerBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.util.getTimeString

class InstantPlayerActivity : AppCompatActivity() {

    private val viewModel: InstantPlayerViewModel by viewModels()
    private lateinit var binding: ActivityInstantPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_instant_player)

        binding.root.setOnClickListener { finish() }
        binding.actionLeft.setOnClickListener {
            viewModel.onPlaybackButtonPressed(PlaybackButton.PREV)
        }
        binding.actionPlayPause.setOnClickListener {
            viewModel.onPlaybackButtonPressed(PlaybackButton.PLAY)
        }
        binding.actionRight.setOnClickListener {
            viewModel.onPlaybackButtonPressed(PlaybackButton.NEXT)
        }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                Unit

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                viewModel.onSeekBarProgressChanged(seekBar.progress.toFloat() / seekBar.max)
            }
        })

        viewModel.player.observe(this) { player ->
            player ?: return@observe

            intent?.data?.path?.let {
                player.submit(it)
                viewModel.onPlaybackButtonPressed(PlaybackButton.PLAY)
            }

            player.isPlaying.observe(this) {
                binding.isPlaying = it
            }
            player.progress.observe(this) { (current, duration) ->
                binding.seekBar.progress =
                    (binding.seekBar.max * (current.toFloat() / duration)).toInt()
                binding.timeLeft.text = current.getTimeString()
                binding.timeRight.text = duration.getTimeString()
            }
        }

        viewModel.bindPlayer()
    }
}