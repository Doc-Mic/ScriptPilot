package com.mic.scriptpilot.ui.seo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentSeoBinding
import com.mic.scriptpilot.domain.model.SeoResultLine
import com.mic.scriptpilot.ui.common.AiActionBarConfig
import com.mic.scriptpilot.ui.common.AiActionBarController
import com.mic.scriptpilot.ui.common.applyNavigationBarBottomMargin
import com.mic.scriptpilot.ui.common.adapter.SeoResultAdapter
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import com.mic.scriptpilot.ui.common.reserveBottomOverlaySpace
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SeoFragment : Fragment() {
    private var _binding: FragmentSeoBinding? = null
    private val binding get() = _binding!!

    private val args: SeoFragmentArgs by navArgs()
    private val viewModel: SeoViewModel by viewModels()

    private var latestState = SeoUiState()
    private var seoSaved = false
    private var hasScrolledForGeneration = false
    private var suppressDescriptionEdit = false

    private val adapter = SeoResultAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.header.setupScreenHeader(
            R.string.title_seo,
            showBack = true,
            showHome = true,
            onHome = { navController.navigateHomeClearingWorkflow() },
        ) {
            navController.navigateUp()
        }
        prefillFromArgs()
        setupResultsList()
        setupTabs()
        setupSafeActionBar()

        binding.buttonGenerate.setOnClickListener {
            seoSaved = false
            generateSeoPack()
        }
        binding.inputSeoDescription.doAfterTextChanged { editable ->
            if (!suppressDescriptionEdit) {
                seoSaved = false
                viewModel.updateDescription(editable?.toString().orEmpty())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestState = state
                    render(state)
                }
            }
        }
    }

    private fun prefillFromArgs() {
        val navController = findNavController()
        val savedPrefill =
            navController.previousBackStackEntry?.savedStateHandle?.remove<String>(PREFILL_SCRIPT_KEY)
                ?: navController.currentBackStackEntry?.savedStateHandle?.remove<String>(PREFILL_SCRIPT_KEY)
        if (binding.inputScript.text.isNullOrEmpty() && !savedPrefill.isNullOrBlank()) {
            binding.inputScript.setText(savedPrefill)
        } else if (binding.inputScript.text.isNullOrEmpty() && args.scriptDraft.isNotBlank()) {
            binding.inputScript.setText(args.scriptDraft)
        }
        if (binding.inputTopic.text.isNullOrEmpty() && args.topicHint.isNotBlank()) {
            binding.inputTopic.setText(args.topicHint)
        }
    }

    private fun setupResultsList() {
        if (binding.recyclerResults.layoutAnimation == null) {
            binding.recyclerResults.layoutAnimation =
                android.view.animation.AnimationUtils.loadLayoutAnimation(
                    requireContext(),
                    R.anim.layout_slide_in_bottom,
                )
        }
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.setHasFixedSize(false)
        binding.recyclerResults.isNestedScrollingEnabled = false
        binding.recyclerResults.itemAnimator = null
    }

    private fun setupTabs() {
        binding.tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position ?: 0) {
                        0 -> viewModel.selectTab(SeoResultTab.TITLES)
                        1 -> viewModel.selectTab(SeoResultTab.DESCRIPTIONS)
                        else -> viewModel.selectTab(SeoResultTab.TAGS)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                override fun onTabReselected(tab: TabLayout.Tab?) = Unit
            },
        )
    }

    private fun setupSafeActionBar() {
        binding.aiActionBar.root.applyNavigationBarBottomMargin()
        binding.scrollContent.reserveBottomOverlaySpace(binding.aiActionBar.root)
    }

    private fun generateSeoPack() {
        val script = binding.inputScript.text?.toString()?.trim().orEmpty()
        val topic = binding.inputTopic.text?.toString()?.trim().orEmpty()
        viewModel.generate(script, topic, selectedContentTypes())
    }

    private fun selectedContentTypes(): List<String> =
        binding.chipGroupContentTypes.checkedChipIds.mapNotNull { id ->
            binding.chipGroupContentTypes.findViewById<Chip>(id)?.text?.toString()
        }.ifEmpty {
            listOf(getString(R.string.seo_chip_youtube))
        }

    private fun render(state: SeoUiState) {
        if (binding.tabs.selectedTabPosition != state.tab.ordinal) {
            binding.tabs.getTabAt(state.tab.ordinal)?.select()
        }

        updateTabCounts(state)
        binding.shimmerLoading.isVisible = state.isLoading
        val showRecycler = state.shouldShowRecycler()
        val showDescriptionEditor =
            !state.isLoading &&
                state.hasGenerated &&
                state.tab == SeoResultTab.DESCRIPTIONS &&
                state.descriptions.isNotEmpty()
        binding.layoutDescriptionEditor.isVisible = showDescriptionEditor
        binding.recyclerResults.isVisible = showRecycler && state.tab != SeoResultTab.DESCRIPTIONS
        if (showDescriptionEditor) {
            val descriptionText = state.descriptions.firstOrNull()?.text.orEmpty()
            if (binding.inputSeoDescription.text?.toString() != descriptionText && !binding.inputSeoDescription.hasFocus()) {
                suppressDescriptionEdit = true
                binding.inputSeoDescription.setText(descriptionText)
                suppressDescriptionEdit = false
            }
        }

        adapter.submitList(state.itemsForTab()) {
            if (showRecycler && state.tab != SeoResultTab.DESCRIPTIONS) {
                binding.recyclerResults.scheduleLayoutAnimation()
            }
        }

        val showEmpty = !state.isLoading && !showRecycler && !showDescriptionEditor
        binding.emptyState.isVisible = showEmpty
        if (showEmpty) {
            binding.textEmpty.setText(
                if (!state.hasGenerated) {
                    R.string.seo_empty_prompt
                } else {
                    R.string.seo_results_empty_tab
                },
            )
        }

        if (state.isLoading) {
            hasScrolledForGeneration = false
            binding.shimmerLoading.startShimmer()
        } else {
            binding.shimmerLoading.stopShimmer()
        }
        binding.buttonGenerate.isEnabled = !state.isLoading
        binding.aiActionBar.root.isVisible = state.hasGenerated && !state.isLoading
        if (state.hasGenerated && !state.isLoading) {
            bindAiActions()
            scrollToResultsOnce()
        }

        if (state.saveComplete) {
            seoSaved = true
            bindAiActions()
            Snackbar.make(binding.root, R.string.ai_action_saved_projects, Snackbar.LENGTH_SHORT).show()
            viewModel.consumeSaveEvent()
        }

        state.errorMessage?.let { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    private fun updateTabCounts(state: SeoUiState) {
        setTabText(0, R.string.seo_tab_titles, state.titles.size)
        setTabText(1, R.string.seo_tab_descriptions, state.descriptions.size)
        setTabText(2, R.string.seo_tab_tags, state.tags.size)
    }

    private fun setTabText(index: Int, labelRes: Int, count: Int) {
        val label = getString(labelRes)
        binding.tabs.getTabAt(index)?.text =
            if (count > 0) {
                getString(R.string.seo_tab_count, label, count)
            } else {
                label
            }
    }

    private fun bindAiActions() {
        AiActionBarController.bind(
            binding.aiActionBar,
            AiActionBarConfig(
                saved = seoSaved,
                onCopy = {
                    AiActionBarController.copyText(requireContext(), binding.root, currentSectionText())
                },
                onSave = {
                    viewModel.saveCurrentPackage(
                        sourceText = binding.inputScript.text?.toString().orEmpty(),
                        contextHint = binding.inputTopic.text?.toString().orEmpty(),
                    )
                },
                onFeedback = {
                    AiActionBarController.showFeedbackDialog(requireContext(), binding.root)
                },
                onShare = {
                    AiActionBarController.shareText(requireContext(), binding.root, currentSectionText())
                },
                onRegenerate = {
                    Snackbar.make(binding.root, R.string.ai_action_generating_new, Snackbar.LENGTH_SHORT).show()
                    seoSaved = false
                    generateSeoPack()
                },
            ),
        )
    }

    private fun currentSectionText(): String =
        when (latestState.tab) {
            SeoResultTab.TAGS -> tagsCsv()
            SeoResultTab.DESCRIPTIONS -> editableDescriptionText()
            SeoResultTab.TITLES -> latestState.itemsForTab().toShareText()
        }.ifBlank {
            latestState.allText()
        }

    private fun editableDescriptionText(): String =
        binding.inputSeoDescription.text?.toString()?.trim().orEmpty()

    private fun tagsCsv(): String =
        latestState.tags.joinToString(", ") { it.text }

    private fun List<SeoResultLine>.toShareText(): String =
        joinToString("\n") { it.text }

    private fun SeoUiState.allText(): String =
        listOf(titles, descriptions, tags)
            .flatten()
            .joinToString("\n") { it.text }

    private fun scrollToResultsOnce() {
        if (hasScrolledForGeneration) return
        hasScrolledForGeneration = true
        binding.scrollContent.post {
            binding.scrollContent.smoothScrollTo(0, binding.layoutResults.top)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val PREFILL_SCRIPT_KEY = "prefill_script"
    }
}
