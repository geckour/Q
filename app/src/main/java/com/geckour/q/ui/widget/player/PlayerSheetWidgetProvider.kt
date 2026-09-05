@file:Suppress("RestrictedApiAndroidX")

package com.geckour.q.ui.widget.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.concurrent.futures.await
import com.geckour.q.service.PlayerService
import com.geckour.q.util.getIsInNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Publishes [com.geckour.q.ui.main.PlayerSheet] to the home screen.
 *
 * The sheet is composed with the RemoteCompose creation API into a serialised document, which is
 * handed to the launcher as [RemoteViews.DrawInstructions]. That is what lets a Compose layout –
 * rather than the handful of widgets `RemoteViews` normally supports – be rendered by another
 * process.
 */
class PlayerSheetWidgetProvider : AppWidgetProvider() {

    companion object {

        private const val DEFAULT_WIDTH_DP = 250
        private const val DEFAULT_HEIGHT_DP = 180

        private const val COMMAND_SETTLE_MILLIS = 250L

        fun requestUpdate(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PlayerSheetWidgetProvider::class.java)
            )
            if (appWidgetIds.isEmpty()) return

            // ACTION_APPWIDGET_UPDATE is a protected broadcast, so the app asks itself to
            // refresh through its own action instead.
            context.sendBroadcast(
                Intent(context, PlayerSheetWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
            )
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        // onReceive hands out a receiver restricted context, which cannot bind to the player
        // service, so everything downstream works off the application context.
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        coroutineScope.launch {
            try {
                renderAll(applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        coroutineScope.launch {
            try {
                render(applicationContext, appWidgetManager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = intent
                    .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?.takeIf { it.isNotEmpty() }
                    ?: appWidgetManager.getAppWidgetIds(
                        ComponentName(context, PlayerSheetWidgetProvider::class.java)
                    )
                onUpdate(context, appWidgetManager, appWidgetIds)
            }

            ACTION_CONTROL -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

                val widgetAction = intent.getStringExtra(EXTRA_WIDGET_ACTION)
                    ?.let { name ->
                        PlayerWidgetAction.entries.firstOrNull { it.name == name }
                    }
                val applicationContext = context.applicationContext
                val pendingResult = goAsync()
                coroutineScope.launch {
                    try {
                        dispatchCommand(applicationContext, widgetAction)
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        renderAll(
                            context = applicationContext,
                            appWidgetManager = appWidgetManager,
                            appWidgetIds = appWidgetManager.getAppWidgetIds(
                                ComponentName(
                                    applicationContext,
                                    PlayerSheetWidgetProvider::class.java
                                )
                            ),
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun renderAll(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = appWidgetIds.forEach { appWidgetId ->
        runCatching { render(context, appWidgetManager, appWidgetId) }
            .onFailure { Timber.e(it, "Failed to render the player sheet widget $appWidgetId") }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val displayMetrics = context.resources.displayMetrics
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            ?.takeIf { it > 0 }
            ?: DEFAULT_WIDTH_DP
        val heightDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: DEFAULT_HEIGHT_DP
        val isInNightMode: Boolean = context.getIsInNightMode().firstOrNull() == true

        val layout = PlayerWidgetLayout.of(heightDp)
        val colors = PlayerWidgetColors.of(isInNightMode)
        val activeIcons = PlayerWidgetIcons.of(context, colors.button)
        val inactiveIcons = PlayerWidgetIcons.of(context, colors.inactive)
        val state = loadPlayerWidgetState(
            context = context,
            artworkSizePx = (layout.artworkSizeDp * displayMetrics.density).roundToInt() * 2,
        )

        val capturedDocument = runCatching {
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = RemoteCreationDisplayInfo(
                    width = (widthDp * displayMetrics.density).roundToInt(),
                    height = (heightDp * displayMetrics.density).roundToInt(),
                    densityDpi = displayMetrics.densityDpi,
                    fontScale = context.resources.configuration.fontScale,
                ),
                // Restricts the document to what the platform widget renderer supports.
                profile = RcPlatformProfiles.WIDGETS_V7,
            ) {
                RemotePlayerSheet(
                    state = state,
                    colors = colors,
                    activeIcons = activeIcons,
                    inactiveIcons = inactiveIcons,
                    layout = layout,
                )
            }
        }.getOrElse {
            Timber.e(it, "Failed to capture the player sheet document")
            return
        }

        val remoteViews = RemoteViews(
            RemoteViews.DrawInstructions.Builder(listOf(capturedDocument.bytes)).build()
        )
        PlayerWidgetAction.entries.forEach { action ->
            remoteViews.setOnClickPendingIntent(
                action.actionId,
                action.pendingIntent(context, appWidgetId),
            )
        }

        withContext(Dispatchers.Main) {
            runCatching { appWidgetManager.updateAppWidget(appWidgetId, remoteViews) }
                .onFailure { Timber.e(it, "Failed to publish the player sheet widget") }
        }
    }

    private suspend fun dispatchCommand(
        context: Context,
        widgetAction: PlayerWidgetAction?,
    ) = withContext(Dispatchers.Main) {
        if (widgetAction == null) return@withContext

        val controller = runCatching {
            MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlayerService::class.java))
            ).buildAsync().await()
        }.getOrElse {
            Timber.e(it, "Failed to connect to the player session")
            return@withContext
        }

        try {
            when (widgetAction) {
                PlayerWidgetAction.RotateRepeatMode -> controller.sendCustomCommand(
                    SessionCommand(PlayerService.ACTION_COMMAND_ROTATE_REPEAT_MODE, Bundle.EMPTY),
                    Bundle.EMPTY,
                )

                PlayerWidgetAction.Shuffle -> controller.sendCustomCommand(
                    SessionCommand(PlayerService.ACTION_COMMAND_SHUFFLE_QUEUE, Bundle.EMPTY),
                    bundleOf(PlayerService.ACTION_EXTRA_SHUFFLE_ACTION_TYPE to null),
                )

                PlayerWidgetAction.Prev -> controller.seekToPrevious()
                PlayerWidgetAction.Next -> controller.seekToNext()
                PlayerWidgetAction.TogglePlayPause ->
                    if (controller.playWhenReady) controller.pause() else controller.play()

                PlayerWidgetAction.OpenApp -> Unit
            }
            delay(COMMAND_SETTLE_MILLIS.milliseconds)
        } finally {
            controller.release()
        }
    }
}

/** The [PendingIntent] the launcher fires when [this] is tapped. */
private fun PlayerWidgetAction.pendingIntent(
    context: Context,
    appWidgetId: Int,
): PendingIntent = when (this) {
    PlayerWidgetAction.OpenApp -> PendingIntent.getActivity(
        context,
        0,
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    else -> context.controlPendingIntent(
        uri = "$CONTROL_URI_SCHEME://$appWidgetId/action/$name".toUri(),
    ) { putExtra(EXTRA_WIDGET_ACTION, name) }
}

/**
 * [Intent.filterEquals] ignores extras, so each target gets its own [uri] to keep the system from
 * folding every control into a single [PendingIntent].
 */
private fun Context.controlPendingIntent(
    uri: Uri,
    extras: Intent.() -> Unit,
): PendingIntent = PendingIntent.getBroadcast(
    this,
    0,
    Intent(this, PlayerSheetWidgetProvider::class.java)
        .setAction(ACTION_CONTROL)
        .setData(uri)
        .apply(extras),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/** Asks the provider to re-render, in place of the protected `ACTION_APPWIDGET_UPDATE`. */
private const val ACTION_REFRESH = "com.geckour.q.widget.action.REFRESH"

/** Carries a tapped [PlayerWidgetAction] back to the provider. */
private const val ACTION_CONTROL = "com.geckour.q.widget.action.CONTROL"
private const val CONTROL_URI_SCHEME = "q-widget"
private const val EXTRA_WIDGET_ACTION = "com.geckour.q.widget.extra.WIDGET_ACTION"
