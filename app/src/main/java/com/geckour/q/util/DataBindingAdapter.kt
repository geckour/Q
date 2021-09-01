package com.geckour.q.util

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.databinding.BindingAdapter

@BindingAdapter("srcBitmap")
fun ImageView.loadImage(bitmap: Bitmap?) {
    setImageBitmap(bitmap)
}

@BindingAdapter("srcUri")
fun ImageView.loadImage(uri: Uri?) {
    setImageURI(uri)
}

@BindingAdapter("foregroundAttr")
fun View.setForegroundFromAttribute(@AttrRes attr: Int) {
    foreground = ColorDrawable(context.theme.getColor(attr))
}

@BindingAdapter("backgroundAttr")
fun View.setBackgroundFromAttribute(@AttrRes attr: Int) {
    setBackgroundColor(context.theme.getColor(attr))
}

@BindingAdapter("textColorAttr")
fun TextView.setTextColorFromAttribute(@AttrRes attr: Int) {
    setTextColor(context.theme.getColor(attr))
}

@BindingAdapter("tintAttr")
fun ImageView.setImageTintFromAttribute(@AttrRes attr: Int) {
    imageTintList = ColorStateList.valueOf(context.theme.getColor(attr))
}

@BindingAdapter("backgroundTintAttr")
fun View.setBackgroundTintFromAttribute(@AttrRes attr: Int) {
    backgroundTintList = ColorStateList.valueOf(context.theme.getColor(attr))
}

@BindingAdapter("thumbTintAttr")
fun AppCompatSeekBar.setThumbTintFromAttribute(@AttrRes attr: Int) {
    thumbTintList = ColorStateList.valueOf(context.theme.getColor(attr))
}