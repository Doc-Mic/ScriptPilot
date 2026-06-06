package com.mic.scriptpilot.ui.script

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.DialogCustomDurationBinding
import com.mic.scriptpilot.databinding.FragmentScriptBinding
import com.mic.scriptpilot.domain.model.ScriptOutline
import com.mic.scriptpilot.ui.common.AiActionBarConfig
import com.mic.scriptpilot.ui.common.AiActionBarController
import com.mic.scriptpilot.ui.common.applyNavigationBarBottomMargin
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import com.mic.scriptpilot.ui.common.reserveBottomOverlaySpace
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScriptFragment : Fragment() {
    private var _binding: FragmentScriptBinding? = null
    private val binding get() = _binding!!

    private val args: ScriptFragmentArgs by navArgs()
    private val viewModel: ScriptViewModel by viewModels()

    private var lastOutline: ScriptOutline? = null
    private var customDurationMinutes: Int? = null
    private var lastNonCustomDurationChipId: Int = R.id.chipThreeMin
    private var scriptSaved = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScriptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.header.setupScreenHeader(
            R.string.title_script,
            showBack = true,
            showHome = true,
            onHome = { navController.navigateHomeClearingWorkflow() },
        ) {
            navController.navigateUp()
        }
        binding.aiActionBar.root.applyNavigationBarBottomMargin()
        binding.scrollContent.reserveBottomOverlaySpace(binding.aiActionBar.root)

        val tones = resources.getStringArray(R.array.script_tones)
        val preferences = viewModel.creatorPreferences.value
        binding.inputTone.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tones),
        )
        if (binding.inputTone.text.isNullOrEmpty()) {
            binding.inputTone.setText(preferences.defaultScriptTone.takeIf { it in tones } ?: tones.first(), false)
        }

        if (binding.inputIdea.text.isNullOrEmpty() && args.ideaText.isNotBlank()) {
            binding.inputIdea.setText(args.ideaText)
        }

        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (id == R.id.chipCustom) {
                showCustomDurationDialog()
            } else {
                lastNonCustomDurationChipId = id
                customDurationMinutes = null
                binding.chipCustom.text = getString(R.string.duration_custom)
            }
        }
        binding.chipCustom.setOnClickListener {
            if (binding.chipGroupDuration.checkedChipId == R.id.chipCustom && customDurationMinutes != null) {
                showCustomDurationDialog()
            }
        }

        binding.buttonGenerate.setOnClickListener {
            val idea = binding.inputIdea.text?.toString().orEmpty()
            val tone = binding.inputTone.text?.toString().orEmpty().ifBlank { tones.first() }
            val duration = durationLabel()
            if (duration == null) {
                Snackbar.make(binding.root, R.string.error_custom_duration_required, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            scriptSaved = false
            viewModel.generateScript(idea, duration, tone)
        }
        binding.buttonGenerateSeoPack.setOnClickListener {
            openSeoWithEditableScript()
        }
        binding.inputGeneratedScript.doAfterTextChanged {
            if (scriptSaved) {
                scriptSaved = false
                bindAiActions(tones)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonGenerate.isEnabled = !state.loading

                    val outline = state.outline
                    val previousOutline = lastOutline
                    lastOutline = outline
                    val hasOutline = outline != null
                    binding.cardOutput.isVisible = hasOutline
                    binding.aiActionBar.root.isVisible = hasOutline

                    if (outline != null) {
                        if (outline != previousOutline) {
                            binding.inputGeneratedScript.setText(outline.asFullScript())
                        }
                        bindAiActions(tones)
                    }

                    if (state.saveComplete) {
                        scriptSaved = true
                        outline?.let { bindAiActions(tones) }
                        Snackbar.make(binding.root, R.string.ai_action_saved_projects, Snackbar.LENGTH_SHORT).show()
                        viewModel.consumeSaveEvent()
                    }

                    state.errorMessage?.let { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        viewModel.consumeError()
                    }
                }
            }
        }
    }

    private fun bindAiActions(tones: Array<String>) {
        val saveAction = {
            saveEditableScript()
        }
        AiActionBarController.bind(
            binding.aiActionBar,
            AiActionBarConfig(
                saved = scriptSaved,
                onCopy = {
                    AiActionBarController.copyText(requireContext(), binding.root, editableScriptText())
                },
                onSave = saveAction,
                onFeedback = { AiActionBarController.showFeedbackDialog(requireContext(), binding.root) },
                onShare = {
                    AiActionBarController.shareText(requireContext(), binding.root, editableScriptText())
                },
                onRegenerate = {
                    val duration = durationLabel()
                    if (duration == null) {
                        Snackbar.make(binding.root, R.string.error_custom_duration_required, Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(binding.root, R.string.ai_action_generating_new, Snackbar.LENGTH_SHORT).show()
                        val idea = binding.inputIdea.text?.toString().orEmpty()
                        val tone = binding.inputTone.text?.toString().orEmpty().ifBlank { tones.first() }
                        scriptSaved = false
                        viewModel.generateScript(idea, duration, tone)
                    }
                },
            ),
        )
    }

    private fun saveEditableScript() {
        val scriptText = editableScriptText()
        if (scriptText.isBlank()) {
            Snackbar.make(binding.root, R.string.error_script_required_for_seo, Snackbar.LENGTH_LONG).show()
            return
        }
        val ideaLine = binding.inputIdea.text?.toString().orEmpty()
        viewModel.saveCurrent(ideaLine, scriptText)
    }

    private fun openSeoWithEditableScript() {
        val scriptText = editableScriptText()
        if (scriptText.isBlank()) {
            Snackbar.make(binding.root, R.string.error_script_required_for_seo, Snackbar.LENGTH_LONG).show()
            return
        }
        val ideaLine = binding.inputIdea.text?.toString().orEmpty()
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.set(PREFILL_SCRIPT_KEY, scriptText)
        navController.navigate(
            ScriptFragmentDirections.actionScriptToSeo(
                scriptDraft = scriptText,
                topicHint = ideaLine,
            ),
        )
    }

    private fun editableScriptText(): String =
        binding.inputGeneratedScript.text?.toString()?.trim().orEmpty()

    /** Label sent as [LongScriptRequest.durationLabel] (parsed by backend), or null if Custom is invalid. */
    private fun durationLabel(): String? {
        return when (binding.chipGroupDuration.checkedChipId) {
            R.id.chipThreeMin -> getString(R.string.duration_three_min)
            R.id.chipFiveMin -> getString(R.string.duration_five_min)
            R.id.chipTenMin -> getString(R.string.duration_ten_min)
            R.id.chipFifteenMin -> getString(R.string.duration_fifteen_min)
            R.id.chipCustom -> {
                val n = customDurationMinutes
                if (n == null || n <= 0 || n > MAX_CUSTOM_DURATION_MINUTES) null else "$n min"
            }
            else -> getString(R.string.duration_three_min)
        }
    }

    private fun showCustomDurationDialog() {
        val dialogBinding = DialogCustomDurationBinding.inflate(layoutInflater)
        customDurationMinutes?.let { dialogBinding.inputCustomMinutes.setText(it.toString()) }

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_custom_duration_title)
                .setView(dialogBinding.root)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (customDurationMinutes == null) {
                        binding.chipGroupDuration.check(lastNonCustomDurationChipId)
                    }
                }
                .setPositiveButton(R.string.action_apply, null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = dialogBinding.inputCustomMinutes.text?.toString()?.trim()?.toIntOrNull()
                if (minutes == null || minutes !in 1..MAX_CUSTOM_DURATION_MINUTES) {
                    dialogBinding.layoutCustomMinutes.error =
                        getString(R.string.error_custom_duration_range, MAX_CUSTOM_DURATION_MINUTES)
                    return@setOnClickListener
                }

                customDurationMinutes = minutes
                binding.chipCustom.text = getString(R.string.duration_custom_selected, minutes)
                binding.chipGroupDuration.check(R.id.chipCustom)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MAX_CUSTOM_DURATION_MINUTES = 30
        const val PREFILL_SCRIPT_KEY = "prefill_script"
    }
}
