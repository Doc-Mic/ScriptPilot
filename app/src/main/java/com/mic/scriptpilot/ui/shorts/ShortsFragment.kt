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
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentShortsBinding
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShortsFragment : Fragment() {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShortsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())

        binding.buttonGenerate.setOnClickListener {
            val topic = binding.inputTopic.text?.toString().orEmpty()
            viewModel.generate(topic)
        }

        binding.buttonSaveShort.setOnClickListener {
            val topic = binding.inputTopic.text?.toString().orEmpty()
            val script = binding.textOutput.text?.toString().orEmpty()
            if (script.isNotBlank()) {
                viewModel.save(topic, script)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonGenerate.isEnabled = !state.loading

                    val hasScript = !state.scriptText.isNullOrBlank()
                    binding.layoutShortsResult.isVisible = hasScript
                    if (hasScript) {
                        binding.textOutput.text = state.scriptText.orEmpty()
                    }

                    if (state.saveComplete) {
                        Snackbar.make(binding.root, R.string.message_project_saved, Snackbar.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
