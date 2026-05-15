package com.mic.scriptpilot.ui.script

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentScriptBinding
import com.mic.scriptpilot.domain.model.ScriptOutline
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScriptFragment : Fragment() {
    private var _binding: FragmentScriptBinding? = null
    private val binding get() = _binding!!

    private val args: ScriptFragmentArgs by navArgs()
    private val viewModel: ScriptViewModel by viewModels()

    private var lastOutline: ScriptOutline? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScriptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())

        val tones = resources.getStringArray(R.array.script_tones)
        binding.inputTone.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tones),
        )
        if (binding.inputTone.text.isNullOrEmpty()) {
            binding.inputTone.setText(tones.first(), false)
        }

        if (binding.inputIdea.text.isNullOrEmpty() && args.ideaText.isNotBlank()) {
            binding.inputIdea.setText(args.ideaText)
        }

        binding.layoutCustomDuration.isVisible =
            binding.chipGroupDuration.checkedChipId == R.id.chipCustom
        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            binding.layoutCustomDuration.isVisible = id == R.id.chipCustom
        }

        binding.buttonGenerate.setOnClickListener {
            val idea = binding.inputIdea.text?.toString().orEmpty()
            val tone = binding.inputTone.text?.toString().orEmpty().ifBlank { tones.first() }
            val duration = durationLabel()
            if (duration == null) {
                Snackbar.make(binding.root, R.string.error_custom_duration_required, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            viewModel.generateScript(idea, duration, tone)
        }

        binding.buttonSave.setOnClickListener {
            val outline = lastOutline ?: return@setOnClickListener
            val ideaLine = binding.inputIdea.text?.toString().orEmpty()
            viewModel.saveCurrent(ideaLine, outline)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonGenerate.isEnabled = !state.loading

                    lastOutline = state.outline
                    val outline = state.outline
                    val hasOutline = outline != null
                    binding.cardOutput.isVisible = hasOutline
                    binding.actionsRow.isVisible = hasOutline

                    if (outline != null) {
                        binding.textHook.text = outline.hook
                        binding.textIntro.text = outline.intro
                        binding.textBody.text = outline.body
                        binding.textOutro.text = outline.outro
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

    /** Label sent as [LongScriptRequest.durationLabel] (parsed by backend), or null if Custom is invalid. */
    private fun durationLabel(): String? {
        return when (binding.chipGroupDuration.checkedChipId) {
            R.id.chipThreeMin -> getString(R.string.duration_three_min)
            R.id.chipFiveMin -> getString(R.string.duration_five_min)
            R.id.chipTenMin -> getString(R.string.duration_ten_min)
            R.id.chipFifteenMin -> getString(R.string.duration_fifteen_min)
            R.id.chipCustom -> {
                val n = binding.inputCustomMinutes.text?.toString()?.trim()?.toIntOrNull()
                if (n == null || n <= 0 || n > 180) null else "$n min"
            }
            else -> getString(R.string.duration_three_min)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
