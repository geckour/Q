package com.geckour.q.ui

import android.app.SearchManager
import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.concurrent.futures.await
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.geckour.q.service.PlayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlayFromSearchActivity : AppCompatActivity() {

    companion object {
        private val AWAIT_SUBMISSION_TIMEOUT = 5.seconds
        private val AWAIT_SUBMISSION_INTERVAL = 50.milliseconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val query = intent?.getStringExtra(SearchManager.QUERY).orEmpty()
        val extras = intent?.extras ?: Bundle.EMPTY

        lifecycleScope.launch {
            runCatching {
                val mediaController = MediaController.Builder(
                    this@PlayFromSearchActivity,
                    SessionToken(
                        this@PlayFromSearchActivity,
                        ComponentName(this@PlayFromSearchActivity, PlayerService::class.java)
                    )
                ).buildAsync().await()

                mediaController.setMediaItem(
                    MediaItem.Builder()
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(query)
                                .setExtras(extras)
                                .build()
                        )
                        .build()
                )
                mediaController.prepare()
                mediaController.play()

                withTimeoutOrNull(AWAIT_SUBMISSION_TIMEOUT) {
                    while (mediaController.mediaItemCount < 1) {
                        delay(AWAIT_SUBMISSION_INTERVAL)
                    }
                }

                mediaController.release()
            }.onFailure { Timber.e(it) }

            finish()
        }
    }
}
