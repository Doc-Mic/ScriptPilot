package com.mic.scriptpilot.ui.common

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

fun View.playCardPressAnimation() {
    val interpolator = AccelerateDecelerateInterpolator()
    animate()
        .scaleX(0.97f)
        .scaleY(0.97f)
        .setDuration(90L)
        .setInterpolator(interpolator)
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .setInterpolator(interpolator)
                .start()
        }
        .start()
}
