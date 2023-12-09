package com.geckour.q.domain.model

import android.graphics.Rect
import androidx.window.layout.FoldingFeature

sealed interface LayoutType {

    data object Single: LayoutType

    data class Twin(
        val hingePosition: Rect,
        val orientation: FoldingFeature.Orientation
    ): LayoutType
}