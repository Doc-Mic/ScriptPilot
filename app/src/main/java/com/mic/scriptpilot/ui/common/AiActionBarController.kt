package com.mic.scriptpilot.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.IncludeAiActionBarBinding

data class AiActionBarConfig(
    val saved: Boolean = false,
    val onCopy: (() -> Unit)? = null,
    val onSave: (() -> Unit)? = null,
    val onFeedback: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onRegenerate: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onSaveProject: (() -> Unit)? = null,
    val onSendSeo: (() -> Unit)? = null,
    val onGenerateCta: (() -> Unit)? = null,
    val onGenerateCaptions: (() -> Unit)? = null,
)

object AiActionBarController {
    fun bind(binding: IncludeAiActionBarBinding, config: AiActionBarConfig) {
        styleDefaultAction(binding.buttonCopy)
        styleDefaultAction(binding.buttonFeedback)
        styleDefaultAction(binding.buttonShare)
        styleDefaultAction(binding.buttonRegenerate)
        styleSaveButton(binding.buttonSave, config.saved)
        bindAction(binding.buttonCopy, config.onCopy, true)
        bindAction(binding.buttonSave, config.onSave, true)
        bindAction(binding.buttonFeedback, config.onFeedback, true)
        bindAction(binding.buttonShare, config.onShare, true)
        bindAction(binding.buttonRegenerate, config.onRegenerate, true)
        bindAction(binding.buttonEdit, config.onEdit, config.onEdit != null)
        bindAction(binding.buttonSaveProject, config.onSaveProject, config.onSaveProject != null)
        bindAction(binding.buttonSendSeo, config.onSendSeo, config.onSendSeo != null)
        bindAction(binding.buttonGenerateCta, config.onGenerateCta, config.onGenerateCta != null)
        bindAction(binding.buttonGenerateCaptions, config.onGenerateCaptions, config.onGenerateCaptions != null)
    }

    fun copyText(context: Context, anchor: View, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("scriptpilot_ai_result", text))
        Snackbar.make(anchor, R.string.message_copied, Snackbar.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, anchor: View, text: String) {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(intent, context.getString(R.string.ai_share_chooser))
        runCatching {
            context.startActivity(chooser)
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                Snackbar.make(anchor, R.string.profile_no_app_available, Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(anchor, R.string.profile_no_app_available, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    fun showFeedbackDialog(context: Context, anchor: View, onSelected: (String) -> Unit = {}) {
        val options = context.resources.getStringArray(R.array.ai_feedback_options)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.ai_feedback_title)
            .setItems(options) { _, which ->
                val selected = options.getOrNull(which).orEmpty()
                // TODO: Send feedback category to analytics once analytics is connected.
                onSelected(selected)
                Snackbar.make(anchor, R.string.ai_action_feedback_saved, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun bindAction(button: MaterialButton, action: (() -> Unit)?, shouldShow: Boolean) {
        button.isVisible = shouldShow
        button.setOnClickListener(null)
        if (action != null) {
            button.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                view.playCardPressAnimation()
                action()
            }
        }
    }

    private fun styleSaveButton(button: MaterialButton, saved: Boolean) {
        button.text = button.context.getString(if (saved) R.string.ai_action_saved else R.string.ai_action_save)
        button.setIconResource(R.drawable.ic_save)
        button.isSelected = saved
        val foregroundColor =
            if (saved) {
                ContextCompat.getColor(button.context, R.color.sp_primary)
            } else {
                MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        button.iconTint = ColorStateList.valueOf(foregroundColor)
        button.setTextColor(foregroundColor)
        button.backgroundTintList =
            if (saved) {
                ColorStateList.valueOf(ContextCompat.getColor(button.context, R.color.ai_action_selected))
            } else {
                ColorStateList.valueOf(Color.TRANSPARENT)
            }
    }

    private fun styleDefaultAction(button: MaterialButton) {
        val color = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant)
        button.isSelected = false
        button.iconTint = ColorStateList.valueOf(color)
        button.setTextColor(color)
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }
}
