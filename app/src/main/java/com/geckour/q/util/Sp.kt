package com.geckour.q.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp

val Int.nonScaleSp
    @Composable get() = (this / LocalDensity.current.fontScale).sp

val Int.nonUpScaleSp
    @Composable get() = LocalDensity.current.fontScale.let {
        if (it > 1) this / it else this.toFloat()
    }.sp

val Int.nonDownScaleSp
    @Composable get() = LocalDensity.current.fontScale.let {
        if (it < 1) this / it else this.toFloat()
    }.sp

val Float.nonScaleSp
    @Composable get() = (this / LocalDensity.current.fontScale).sp

val Float.nonUpScaleSp
    @Composable get() = LocalDensity.current.fontScale.let {
        if (it > 1) this / it else this
    }.sp

val Float.nonDownScaleSp
    @Composable get() = LocalDensity.current.fontScale.let {
        if (it < 1) this / it else this
    }.sp