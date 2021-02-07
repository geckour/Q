package com.geckour.q.util

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes

fun Resources.Theme.getColor(@AttrRes attrResId: Int): Int =
    TypedValue().apply { this@getColor.resolveAttribute(attrResId, this, true) }.data