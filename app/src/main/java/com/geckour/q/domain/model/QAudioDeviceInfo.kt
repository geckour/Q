package com.geckour.q.domain.model

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.mediarouter.media.MediaRouter

data class QAudioDeviceInfo(
    val routeId: String,
    val address: String?,
    val id: Int?,
    val name: String,
    val selected: Boolean
) {

    companion object {

        fun from(
            mediaRouteInfo: MediaRouter.RouteInfo,
            audioDeviceInfo: AudioDeviceInfo?
        ): QAudioDeviceInfo {
            val audioDeviceAddress =
                if (Build.VERSION.SDK_INT > 27) audioDeviceInfo?.address else null
            return QAudioDeviceInfo(
                routeId = mediaRouteInfo.id,
                address = audioDeviceAddress,
                id = audioDeviceInfo?.id,
                name = mediaRouteInfo.name,
                selected = mediaRouteInfo.isSelected && mediaRouteInfo.name == audioDeviceInfo?.productName?.toString()
            )
        }
    }
}
