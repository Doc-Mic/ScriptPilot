package com.mic.scriptpilot.ui.idea

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentIdeaBinding
import com.mic.scriptpilot.ui.common.AiActionBarConfig
import com.mic.scriptpilot.ui.common.AiActionBarController
import com.mic.scriptpilot.ui.common.applyNavigationBarBottomMargin
import com.mic.scriptpilot.ui.common.adapter.IdeaListAdapter
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import com.mic.scriptpilot.ui.common.reserveBottomOverlaySpace
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IdeaFragment : Fragment() {
    private var _binding: FragmentIdeaBinding? = null
    private val binding get() = _binding!!

    private val args: IdeaFragmentArgs by navArgs()
    private val viewModel: IdeaViewModel by viewModels()

    private val adapter = IdeaListAdapter { idea ->
        val action = IdeaFragmentDirections.actionIdeaToScript(idea.title)
        findNavController().navigate(action)
    }
    private var ideasSaved = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIdeaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.header.setupScreenHeader(
            R.string.title_ideas,
            showBack = true,
            showHome = true,
            onHome = { navController.navigateHomeClearingWorkflow() },
        ) {
            navController.navigateUp()
        }
        binding.aiActionBar.root.applyNavigationBarBottomMargin()
        binding.scrollContent.reserveBottomOverlaySpace(binding.aiActionBar.root)

        val styles = resources.getStringArray(R.array.idea_styles)
        val preferences = viewModel.creatorPreferences.value
        binding.inputStyle.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, styles),
        )
        if (binding.inputStyle.text.isNullOrEmpty()) {
            binding.inputStyle.setText(preferences.defaultContentStyle.takeIf { it in styles } ?: styles.first(), false)
        }

        if (binding.inputTopic.text.isNullOrEmpty() && args.topic.isNotBlank()) {
            binding.inputTopic.setText(args.topic)
        }

        val hasTrendSignals = args.trendVirality >= 0 && args.trendOpportunity >= 0
        binding.cardTrendInsight.isVisible = hasTrendSignals
        if (hasTrendSignals) {
            binding.layoutTopic.helperText = getString(R.string.idea_trend_insight_hint)
            binding.textTrendInsight.text =
                getString(
                    R.string.idea_trend_insight_banner,
                    args.trendCategory.ifBlank { getString(R.string.trend_category_fallback) },
                    args.trendVirality,
                    args.trendOpportunity,
                )
        } else {
            binding.layoutTopic.helperText = null
        }

        binding.recyclerIdeas.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerIdeas.adapter = adapter

        binding.buttonGenerate.setOnClickListener {
            val topic = binding.inputTopic.text?.toString().orEmpty()
            val style = binding.inputStyle.text?.toString().orEmpty().ifBlank { styles.first() }
            ideasSaved = false
            viewModel.generateIdeas(topic, style)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonGenerate.isEnabled = !state.loading
                    adapter.submitList(state.ideas)

                    val hasIdeas = state.ideas.isNotEmpty()
                    binding.recyclerIdeas.isVisible = hasIdeas
                    binding.emptyIdeas.isVisible = !state.loading && !hasIdeas
                    binding.aiActionBar.root.isVisible = hasIdeas
                    if (hasIdeas) {
                        bindAiActions(styles)
                    }

                    if (state.saveComplete) {
                        ideasSaved = true
                        bindAiActions(styles)
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

    private fun bindAiActions(styles: Array<String>) {
        AiActionBarController.bind(
            binding.aiActionBar,
            AiActionBarConfig(
                saved = ideasSaved,
                onCopy = {
                    AiActionBarController.copyText(requireContext(), binding.root, generatedIdeasText())
                },
                onSave = {
                    viewModel.saveCurrentIdeas(binding.inputTopic.text?.toString().orEmpty())
                },
                onFeedback = {
                    AiActionBarController.showFeedbackDialog(requireContext(), binding.root)
                },
                onShare = {
                    AiActionBarController.shareText(requireContext(), binding.root, generatedIdeasText())
                },
                onRegenerate = {
                    Snackbar.make(binding.root, R.string.ai_action_generating_new, Snackbar.LENGTH_SHORT).show()
                    val topic = binding.inputTopic.text?.toString().orEmpty()
                    val style = binding.inputStyle.text?.toString().orEmpty().ifBlank { styles.first() }
                    ideasSaved = false
                    viewModel.generateIdeas(topic, style)
                },
            ),
        )
    }

    private fun generatedIdeasText(): String =
        adapter.currentList.joinToString("\n\n") { item ->
            buildString {
                append(item.title)
                if (item.angle.isNotBlank()) {
                    append("\n")
                    append(item.angle)
                }
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
