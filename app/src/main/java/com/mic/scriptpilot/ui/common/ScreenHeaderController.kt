package com.mic.scriptpilot.ui.common

import android.util.TypedValue
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.IncludeScreenHeaderBinding

fun IncludeScreenHeaderBinding.setupScreenHeader(
    title: CharSequence,
    subtitle: CharSequence? = null,
    showBack: Boolean = false,
    showHome: Boolean = false,
    onHome: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    textHeaderTitle.text = title
    textHeaderTitle.setTextAppearance(
        if (showBack) {
            R.style.TextAppearance_ScriptPilot_ScreenHeaderBack
        } else {
            R.style.TextAppearance_ScriptPilot_ScreenHeaderLarge
        },
    )
    textHeaderTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (showBack) 24f else 28f)

    textHeaderSubtitle.text = subtitle ?: ""
    textHeaderSubtitle.isVisible = !subtitle.isNullOrBlank()

    buttonBack.isVisible = showBack
    buttonBack.setOnClickListener(if (showBack) View.OnClickListener { onBack?.invoke() } else null)

    buttonHome.isVisible = showHome
    buttonHome.setOnClickListener(if (showHome) View.OnClickListener { onHome?.invoke() } else null)

    applyHeaderInsets()
}

fun IncludeScreenHeaderBinding.setupScreenHeader(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int? = null,
    showBack: Boolean = false,
    showHome: Boolean = false,
    onHome: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val subtitle = subtitleRes?.let { root.context.getString(it) }
    setupScreenHeader(root.context.getString(titleRes), subtitle, showBack, showHome, onHome, onBack)
}

private fun IncludeScreenHeaderBinding.applyHeaderInsets() {
    val initialTop = headerRoot.paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(headerRoot) { view, insets ->
        val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = initialTop + statusTop)
        insets
    }
    if (headerRoot.isAttachedToWindow) {
        ViewCompat.requestApplyInsets(headerRoot)
    } else {
        headerRoot.addOnAttachStateChangeListener(
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
