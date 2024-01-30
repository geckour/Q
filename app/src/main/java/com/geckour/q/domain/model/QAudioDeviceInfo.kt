package com.geckour.q.domain.model

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.mediarouter.media.MediaRouter
import com.geckour.q.R

data class QAudioDeviceInfo(
    val routeId: String,
    val address: String?,
    val id: Int,
    val name: String,
    val selected: Boolean
) {

    companion object {

        fun from(
            mediaRouteInfo: MediaRouter.RouteInfo,
            audioDeviceInfo: AudioDeviceInfo,
            activeAudioDeviceInfo: AudioDeviceInfo?,
        ): QAudioDeviceInfo {
            val audioDeviceAddress =
                if (Build.VERSION.SDK_INT > 27) audioDeviceInfo.address else null
            return QAudioDeviceInfo(
                routeId = mediaRouteInfo.id,
                address = audioDeviceAddress,
                id = audioDeviceInfo.id,
                name = audioDeviceInfo.productName.toString(),
                selected = activeAudioDeviceInfo?.let { it.id == audioDeviceInfo.id }
                    ?: (mediaRouteInfo.isSelected &&
                            mediaRouteInfo.name == audioDeviceInfo.productName.toString())
            )
        }

        fun getDefaultQAudioDeviceInfo(context: Context, mediaRouteInfo: MediaRouter.RouteInfo) = QAudioDeviceInfo(
            routeId = mediaRouteInfo.id,
            address = null,
            id = 0,
            name = context.getString(R.string.default_device_name),
            selected = true
        )
    }
}
