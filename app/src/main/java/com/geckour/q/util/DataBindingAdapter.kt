package com.geckour.q.util

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.databinding.BindingAdapter

@BindingAdapter("app:srcBitmap")
fun loadImage(imageView: ImageView, bitmap: Bitmap?) {
    imageView.setImageBitmap(bitmap)
}

@BindingAdapter("app:srcUri")
fun loadImage(imageView: ImageView, uri: Uri?) {
    imageView.setImageURI(uri)
}

@BindingAdapter("app:backgroundAttr")
fun setBackgroundFromAttribute(view: View, @AttrRes attr: Int) {
    view.setBackgroundColor(view.context.theme.getColor(attr))
}

@BindingAdapter("app:tintAttr")
fun setImageTintFromAttribute(imageView: ImageView, @AttrRes attr: Int) {
    imageView.imageTintList = ColorStateList.valueOf(imageView.context.theme.getColor(attr))
}

@BindingAdapter("app:backgroundTintAttr")
fun setBackgroundTintFromAttribute(view: View, @AttrRes attr: Int) {
    view.backgroundTintList = ColorStateList.valueOf(view.context.theme.getColor(attr))
}

@BindingAdapter("app:thumbTintAttr")
fun setThumbTintFromAttribute(seekBar: AppCompatSeekBar, @AttrRes attr: Int) {
    seekBar.thumbTintList = ColorStateList.valueOf(seekBar.context.theme.getColor(attr))
}