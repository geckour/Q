package com.geckour.q.domain.model

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.mediarouter.media.MediaRouter
import com.geckour.q.R
import kotlinx.serialization.Serializable

@Serializable
data class QAudioDeviceInfo(
    val routeId: String,
    val address: String?,
    val audioDeviceId: Int,
    val audioDeviceName: String,
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
                audioDeviceId = audioDeviceInfo.id,
                audioDeviceName = audioDeviceInfo.productName.toString(),
                selected = activeAudioDeviceInfo?.let { it.id == audioDeviceInfo.id }
                    ?: (mediaRouteInfo.isSelected &&
                            mediaRouteInfo.name == audioDeviceInfo.productName.toString())
            )
        }

        fun get(
            context: Context,
            mediaRouteInfoList: List<MediaRouter.RouteInfo>,
            audioDeviceInfoList: List<AudioDeviceInfo>,
            activeAudioDeviceInfo: AudioDeviceInfo?,
        ) = mediaRouteInfoList.flatMap { mediaRouteInfo ->
            audioDeviceInfoList.map { audioDeviceInfo ->
                from(
                    mediaRouteInfo = mediaRouteInfo,
                    audioDeviceInfo = audioDeviceInfo,
                    activeAudioDeviceInfo = activeAudioDeviceInfo
                )
            }
        }.let { qAudioDeviceInfoList ->
            val selectedMediaRouteInfo =
                mediaRouteInfoList.firstOrNull { it.isSelected } ?: return@let qAudioDeviceInfoList
            if (qAudioDeviceInfoList.none { it.selected }) {
                qAudioDeviceInfoList +
                        getDefaultQAudioDeviceInfo(context, selectedMediaRouteInfo)
            } else qAudioDeviceInfoList
        }

        fun getDefaultQAudioDeviceInfo(
            context: Context,
            mediaRouteInfo: MediaRouter.RouteInfo
        ) = QAudioDeviceInfo(
            routeId = mediaRouteInfo.id,
            address = null,
            audioDeviceId = 0,
            audioDeviceName = context.getString(R.string.default_device_name),
            selected = true
        )
    }
}
