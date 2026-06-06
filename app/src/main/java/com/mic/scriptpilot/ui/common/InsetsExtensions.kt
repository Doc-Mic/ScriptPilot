package com.mic.scriptpilot.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView

fun View.applyNavigationBarBottomMargin(extraBottomDp: Int = 16) {
    val initialBottomMargin = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    val extraBottom = dp(extraBottomDp)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialBottomMargin + extraBottom + navBottom
        }
        insets
    }
    requestInsetsWhenAttached()
}

fun NestedScrollView.reserveBottomOverlaySpace(overlay: View, extraBottomDp: Int = 24) {
    val initialBottomPadding = paddingBottom
    val extraBottom = dp(extraBottomDp)
    var navBottom = 0

    fun updatePaddingForOverlay() {
        val overlayHeight = if (overlay.isShown) overlay.height else 0
        updatePadding(bottom = initialBottomPadding + navBottom + overlayHeight + extraBottom)
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        updatePaddingForOverlay()
        insets
    }
    overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePaddingForOverlay() }
    overlay.doOnLayout { updatePaddingForOverlay() }
    requestInsetsWhenAttached()
}

private fun View.requestInsetsWhenAttached() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
    }
}

private fun View.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
