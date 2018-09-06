package com.geckour.q.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import com.geckour.q.domain.model.Song
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import timber.log.Timber

class PlayerService : Service() {

    enum class InsertActionType {
        NEXT,
        LAST,
        OVERRIDE,
        SHUFFLE_NEXT,
        SHUFFLE_LAST,
        SHUFFLE_OVERRIDE
    }

    enum class OrientedClassType {
        ARTIST,
        ALBUM,
        SONG,
        GENRE,
        PLAYLIST
    }

    data class QueueMetadata(
            val actionType: InsertActionType,
            val classType: OrientedClassType
    )

    data class InsertQueue(
            val metadata: QueueMetadata,
            val queue: List<Song>
    )

    inner class PlayerBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)
    }

    private val binder = PlayerBinder()
    private val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(null))
    private lateinit var player: SimpleExoPlayer
    val queue: ArrayList<Song> = ArrayList()
    private var currentPosition: Int = 0
    private val currentSong: Song?
        get() = if (currentPosition in queue.indices) queue[currentPosition] else null

    private var onQueueChanged: ((List<Song>) -> Unit)? = null

    private val handler = Handler()

    private val mediaSourceFactory: ExtractorMediaSource.Factory by lazy {
        ExtractorMediaSource.Factory(DefaultDataSourceFactory(applicationContext,
                Util.getUserAgent(applicationContext, packageName)))
    }

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {

        }

        override fun onSeekProcessed() {

        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?,
                                     trackSelections: TrackSelectionArray?) {

        }

        override fun onPlayerError(error: ExoPlaybackException?) {

        }

        override fun onLoadingChanged(isLoading: Boolean) {

        }

        override fun onPositionDiscontinuity(reason: Int) {

        }

        override fun onRepeatModeChanged(repeatMode: Int) {

        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {

        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {

        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> Unit
                Player.STATE_IDLE -> Unit
                Player.STATE_BUFFERING -> Unit
                Player.STATE_ENDED -> next()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector).apply {
            addListener(eventListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        player.release()
    }

    fun setOnQueueChangedListener(listener: (List<Song>) -> Unit) {
        this.onQueueChanged = listener
    }

    fun submitQueue(queue: InsertQueue) {
        when (queue.metadata.actionType) {
            InsertActionType.NEXT -> {
                this.queue.addAll(currentPosition + 1, queue.queue)
            }
            InsertActionType.LAST -> {
                this.queue.addAll(this.queue.size, queue.queue)
            }
            InsertActionType.OVERRIDE -> {
                this.queue.clear()
                this.queue.addAll(queue.queue)
            }
            InsertActionType.SHUFFLE_NEXT -> {
                this.queue.addAll(currentPosition + 1,
                        queue.queue.shuffleByClassType(queue.metadata.classType))
            }
            InsertActionType.SHUFFLE_LAST -> {
                this.queue.addAll(this.queue.size,
                        queue.queue.shuffleByClassType(queue.metadata.classType))
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                this.queue.clear()
                this.queue.addAll(queue.queue.shuffleByClassType(queue.metadata.classType))
            }
        }

        onQueueChanged?.invoke(this.queue)
    }

    fun play(position: Int = currentPosition) {
        Timber.d("qgeck play invoked")
        if (position > queue.lastIndex) return
        this.currentPosition = position

        val uri = try {
            Uri.parse(currentSong?.sourcePath ?: return)
        } catch (t: Throwable) {
            Timber.e(t)
            return
        }

        stop()

        player.playWhenReady = true
        val mediaSource = mediaSourceFactory.createMediaSource(uri)
        player.prepare(mediaSource)
    }

    fun resume() {
        Timber.d("qgeck resume invoked")
        player.playWhenReady = true
    }

    fun pause() {
        Timber.d("qgeck pause invoked")
        player.playWhenReady = false
    }

    fun togglePlayPause() {
        if (player.playWhenReady) pause()
        else {
            if (player.playbackState == Player.STATE_READY) resume()
            else play()
        }
    }

    fun stop() {
        pause()
        player.seekTo(0)
    }

    fun clear() {
        stop()
        this.queue.clear()
    }

    fun next() {
        play(currentPosition + 1)
    }

    fun prev() {
        play(currentPosition - 1)
    }

    fun fastForward() {

    }

    fun rewind() {

    }

    fun seekToHead() {
        player.seekTo(0)
    }

    fun headOrPrev() {
        val current = currentSong ?: return
        if (currentPosition > 0 && player.contentPosition < current.duration / 100) prev()
        else seekToHead()
    }

    fun seek(ratio: Float) {
        if (ratio !in 0..1) return
        val current = currentSong ?: return
        player.seekTo((current.duration * ratio).toLong())
    }

    fun shuffle() {
        if (this.queue.isEmpty()) return
        val toHold = this.queue.subList(0, currentPosition + 1)
        val toShuffle = this.queue.subList(currentPosition + 1, this.queue.size)
        this.queue.clear()
        this.queue.addAll(toHold + toShuffle.shuffled())
    }

    private fun List<Song>.shuffleByClassType(classType: OrientedClassType): List<Song> =
            when (classType) {
                OrientedClassType.ARTIST -> {
                    val artists = this.map { it.artist }.distinct().shuffled()
                    artists.map { artist ->
                        this.filter { it.artist == artist }
                    }.flatten()
                }
                OrientedClassType.ALBUM -> {
                    val albumIds = this.map { it.albumId }.distinct().shuffled()
                    albumIds.map { id ->
                        this.filter { it.albumId == id }
                    }.flatten()
                }
                OrientedClassType.SONG -> {
                    this.shuffled()
                }
                OrientedClassType.GENRE -> {
                    val genreIds = this.map { it.genreId }.distinct().shuffled()
                    genreIds.map { id ->
                        this.filter { it.genreId == id }
                    }.flatten()
                }
                OrientedClassType.PLAYLIST -> {
                    val playlistIds = this.map { it.playlistId }.distinct().shuffled()
                    playlistIds.map { id ->
                        this.filter { it.playlistId == id }
                    }.flatten()
                }
            }
}