package com.geckour.q.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.geckour.q.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun List<String?>.getThumb(context: Context): Bitmap? {
    if (this.isEmpty()) return null
    val unit = 100
    val width = ((this.size * 0.9 - 0.1) * unit).toInt()
    val bitmap = createBitmap(width, unit)
    val canvas = Canvas(bitmap)
    withContext(Dispatchers.IO) {
        this@getThumb.reversed()
            .fold(emptyList<String?>()) { acc, uriString ->
                if (acc.isNotEmpty() && acc.last() == uriString) acc else acc + uriString
            }
            .forEachIndexed { i, uriString ->
                val b = catchAsNull {
                    SingletonImageLoader.get(context)
                        .execute(
                            ImageRequest.Builder(context)
                                .data(uriString ?: R.drawable.ic_empty)
                                .size(unit)
                                .scale(Scale.FIT)
                                .allowHardware(false)
                                .build()
                        )
                        .image
                        ?.toBitmap()
                } ?: return@forEachIndexed
                canvas.drawBitmap(
                    b,
                    bitmap.width - (i + 1) * unit * 0.9f,
                    (unit - b.height) / 2f,
                    Paint()
                )
            }
    }
    return bitmap
}
