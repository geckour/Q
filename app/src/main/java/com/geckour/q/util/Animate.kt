package com.geckour.q.util

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.view.View
import com.geckour.q.R

fun View.shake() {
    (AnimatorInflater.loadAnimator(context, R.animator.animator_shake) as AnimatorSet).apply {
        setTarget(this@shake)
        start()
    }
}