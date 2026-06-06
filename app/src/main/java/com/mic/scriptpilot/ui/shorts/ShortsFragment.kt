package com.mic.scriptpilot.ui.shorts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentShortsBinding
import com.mic.scriptpilot.ui.common.AiActionBarConfig
import com.mic.scriptpilot.ui.common.AiActionBarController
import com.mic.scriptpilot.ui.common.applyNavigationBarBottomMargin
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import com.mic.scriptpilot.ui.common.reserveBottomOverlaySpace
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShortsFragment : Fragment() {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortsViewModel by viewModels()
    private var shortSaved = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShortsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.header.setupScreenHeader(
            R.string.title_shorts,
            showBack = true,
            showHome = true,
            onHome = { navController.navigateHomeClearingWorkflow() },
        ) {
            navController.navigateUp()
        }
        binding.aiActionBar.root.applyNavigationBarBottomMargin()
        binding.scrollContent.reserveBottomOverlaySpace(binding.aiActionBar.root)

        binding.buttonGenerate.setOnClickListener {
            val topic = binding.inputTopic.text?.toString().orEmpty()
            shortSaved = false
            viewModel.generate(topic)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonGenerate.isEnabled = !state.loading

                    val hasScript = !state.scriptText.isNullOrBlank()
                    binding.layoutShortsResult.isVisible = hasScript
                    binding.aiActionBar.root.isVisible = hasScript
                    if (hasScript) {
                        binding.textOutput.text = state.scriptText.orEmpty()
                        bindAiActions(state.scriptText.orEmpty())
                    }

                    if (state.saveComplete) {
                        shortSaved = true
                        state.scriptText?.let { bindAiActions(it) }
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

    private fun bindAiActions(script: String) {
        AiActionBarController.bind(
            binding.aiActionBar,
            AiActionBarConfig(
                saved = shortSaved,
                onCopy = { AiActionBarController.copyText(requireContext(), binding.root, script) },
                onSave = {
                    val topic = binding.inputTopic.text?.toString().orEmpty()
                    viewModel.save(topic, script)
                },
                onFeedback = { AiActionBarController.showFeedbackDialog(requireContext(), binding.root) },
                onShare = { AiActionBarController.shareText(requireContext(), binding.root, script) },
                onRegenerate = {
                    Snackbar.make(binding.root, R.string.ai_action_generating_new, Snackbar.LENGTH_SHORT).show()
                    shortSaved = false
                    viewModel.generate(binding.inputTopic.text?.toString().orEmpty())
                },
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
