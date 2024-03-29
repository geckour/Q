package com.geckour.q.ui.instant

import android.os.Bundle
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.geckour.q.R
import com.geckour.q.databinding.ActivityInstantPlayerBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.util.getTimeString
import com.geckour.q.util.saveTempAudioFile
import org.koin.androidx.viewmodel.ext.android.viewModel

class InstantPlayerActivity : AppCompatActivity() {

    private val viewModel by viewModel<InstantPlayerViewModel>()
    private lateinit var binding: ActivityInstantPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_instant_player)

        binding.root.setOnClickListener { finish() }
        binding.container.setOnTouchListener { _, _ -> true }
        binding.actionLeft.apply {
            setOnClickListener {
                viewModel.onPlaybackButtonPressed(PlaybackButton.PREV)
            }
            setOnLongClickListener {
                viewModel.onPlaybackButtonPressed(PlaybackButton.REWIND)
                true
            }
        }
        binding.actionPlayPause.setOnClickListener {
            viewModel.onPlaybackButtonPressed(PlaybackButton.PLAY)
        }
        binding.actionRight.apply {
            setOnClickListener {
                viewModel.onPlaybackButtonPressed(PlaybackButton.NEXT)
            }
            setOnLongClickListener {
                viewModel.onPlaybackButtonPressed(PlaybackButton.FF)
                true
            }
        }
        listOf(
            binding.actionLeft,
            binding.actionRight
        ).forEach {
            it.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        viewModel.onPlaybackButtonPressed(PlaybackButton.UNDEFINED)
                    }
                }

                return@setOnTouchListener false
            }
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

            intent?.data?.let { uri ->
                val path = if (uri.scheme == "content") {
                    contentResolver.openInputStream(uri)
                        ?.use { it.saveTempAudioFile(this) }
                        ?.path
                } else uri.path
                player.submit(path ?: return@let)
                viewModel.onPlaybackButtonPressed(PlaybackButton.PLAY)
            }

            player.isPlayingListener = {
                binding.isPlaying = it
            }
            player.progressListener = { (current, duration) ->
                binding.seekBar.progress =
                    (binding.seekBar.max * (current.toFloat() / duration)).toInt()
                binding.timeLeft.text = current.getTimeString()
                binding.timeRight.text = duration.getTimeString()
            }
        }

        viewModel.bindPlayer()
    }
}