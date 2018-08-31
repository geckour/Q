package com.geckour.q.util

import android.databinding.BindingAdapter
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView

@BindingAdapter("app:srcBitmap")
fun loadImage(imageView: ImageView, bitmap: Bitmap?) {
    imageView.setImageBitmap(bitmap)
}

@BindingAdapter("app:srcUri")
fun loadImage(imageView: ImageView, uri: Uri?) {
    imageView.setImageURI(uri)
}