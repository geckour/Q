package com.geckour.q.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.text.SubtitleParser
import com.geckour.q.util.SPOTIFY_TRAILING_SILENCE_MILLIS
import com.geckour.q.util.isSpotifySourcePath

@OptIn(UnstableApi::class)
class SpotifyAwareMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val durationMs = mediaItem.mediaMetadata.durationMs
        if (mediaItem.mediaId.isSpotifySourcePath.not() || durationMs == null || durationMs <= 0) {
            return delegate.createMediaSource(mediaItem)
        }

        return SilenceMediaSource.Factory()
            .setDurationUs(Util.msToUs(durationMs + SPOTIFY_TRAILING_SILENCE_MILLIS))
            .createMediaSource()
            .apply { updateMediaItem(mediaItem) }
    }

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory = apply {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): MediaSource.Factory = apply {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun setCmcdConfigurationFactory(
        cmcdConfigurationFactory: CmcdConfiguration.Factory
    ): MediaSource.Factory = apply {
        delegate.setCmcdConfigurationFactory(cmcdConfigurationFactory)
    }

    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory
    ): MediaSource.Factory = apply {
        delegate.setSubtitleParserFactory(subtitleParserFactory)
    }

    @C.ContentType
    override fun getSupportedTypes(): IntArray = delegate.supportedTypes
}
