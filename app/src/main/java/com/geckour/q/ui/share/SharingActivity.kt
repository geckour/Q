package com.geckour.q.ui.share

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Song
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.bundleArtwork
import com.geckour.q.util.formatPattern
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import timber.log.Timber
import kotlin.coroutines.experimental.CoroutineContext

class SharingActivity : AppCompatActivity() {

    enum class IntentRequestCode(val code: Int) {
        SHARE(666)
    }

    companion object {
        private const val ARGS_KEY_SONG = "args_key_song"
        private const val ARGS_KEY_REQUIRE_UNLOCK = "args_key_require_unlock"

        fun getIntent(context: Context, song: Song, requireUnlock: Boolean = false): Intent =
                Intent(context, SharingActivity::class.java).apply {
                    putExtra(ARGS_KEY_SONG, song)
                    putExtra(ARGS_KEY_REQUIRE_UNLOCK, requireUnlock)
                }
    }

    val parentJob = Job()
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        startShare(intent.getSong() ?: return, intent.requireUnlock())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onNewIntent(intent)
        finish()
    }

    private fun startShare(song: Song, requireUnlock: Boolean) {
        val keyguardManager =
                try {
                    getSystemService(KeyguardManager::class.java)
                } catch (t: Throwable) {
                    Timber.e(t)
                    null
                }

        if (requireUnlock.not() || keyguardManager?.isDeviceLocked != true) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

            bgScope.launch {
                val albumName: String? = DB.getInstance(this@SharingActivity)
                        .albumDao().get(song.albumId)?.title
                val sharingText: String =
                        song.getSharingText(this@SharingActivity, albumName)
                uiScope.launch {
                    ShareCompat.IntentBuilder.from(this@SharingActivity)
                            .setChooserTitle(R.string.share_chooser_title)
                            .setText(sharingText)
                            .also {
                                if (sharedPreferences.bundleArtwork && song.thumbUriString != null) {
                                    it.setStream(Uri.parse(song.thumbUriString)).setType("image/*")
                                } else it.setType("text/plain")
                            }
                            .createChooserIntent()
                            .apply {
                                PendingIntent.getActivity(
                                        this@SharingActivity,
                                        IntentRequestCode.SHARE.ordinal,
                                        this@apply,
                                        PendingIntent.FLAG_CANCEL_CURRENT
                                ).send()
                            }
                }
            }
        }
    }

    private fun Intent?.requireUnlock(): Boolean {
        val default = true
        if (this == null) return default

        return if (this.hasExtra(ARGS_KEY_REQUIRE_UNLOCK))
            this.getBooleanExtra(ARGS_KEY_REQUIRE_UNLOCK, default)
        else default
    }

    override fun onDestroy() {
        super.onDestroy()

        parentJob.cancel()
    }

    private fun Intent.getSong(): Song? = getParcelableExtra(ARGS_KEY_SONG)
}

fun Song.getSharingText(context: Context, albumName: String?): String =
        context.formatPattern.getSharingText(this, albumName)

fun String.getSharingText(song: Song, albumName: String?): String =
        this.splitIncludeDelimiter("''", "'", "TI", "AR", "AL", "\\\\n")
                .let { splitList ->
                    val escapes = splitList.mapIndexed { i, s -> Pair(i, s) }
                            .filter { it.second == "'" }
                            .apply { if (lastIndex < 0) return@let splitList }

                    return@let ArrayList<String>().apply {
                        for (i in 0 until escapes.lastIndex step 2) {
                            this.addAll(
                                    splitList.subList(
                                            if (i == 0) 0 else escapes[i - 1].first + 1,
                                            escapes[i].first))

                            this.add(
                                    splitList.subList(
                                            escapes[i].first,
                                            escapes[i + 1].first + 1
                                    ).joinToString(""))
                        }

                        this.addAll(
                                splitList.subList(
                                        if (escapes[escapes.lastIndex].first + 1 < splitList.lastIndex)
                                            escapes[escapes.lastIndex].first + 1
                                        else splitList.lastIndex,
                                        splitList.size
                                ))
                    }
                }.joinToString("") {
                    return@joinToString Regex("^'(.+)'$").let { regex ->
                        if (it.matches(regex)) it.replace(regex, "$1")
                        else when (it) {
                            "'" -> ""
                            "''" -> "'"
                            "TI" -> song.name ?: UNKNOWN
                            "AR" -> song.artist
                            "AL" -> albumName ?: UNKNOWN
                            "\\n" -> "\n"
                            else -> it
                        }
                    }
                }

fun String.splitIncludeDelimiter(vararg delimiters: String) =
        delimiters.joinToString("|")
                .let { pattern ->
                    this.split(Regex("(?<=$pattern)|(?=$pattern)"))
                }