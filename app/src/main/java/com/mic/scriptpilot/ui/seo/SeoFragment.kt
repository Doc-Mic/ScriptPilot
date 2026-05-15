package com.mic.scriptpilot.ui.seo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentSeoBinding
import com.mic.scriptpilot.ui.common.adapter.SeoResultAdapter
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SeoFragment : Fragment() {
    private var _binding: FragmentSeoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SeoViewModel by viewModels()

    private val adapter = SeoResultAdapter { text ->
        copyToClipboard(text)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())

        if (binding.recyclerResults.layoutAnimation == null) {
            binding.recyclerResults.layoutAnimation =
                android.view.animation.AnimationUtils.loadLayoutAnimation(
                    requireContext(),
                    R.anim.layout_slide_in_bottom,
                )
        }
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            changeDuration = 180
            moveDuration = 180
            removeDuration = 180
        }

        binding.tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position ?: 0) {
                        0 -> viewModel.selectTab(SeoResultTab.TITLES)
                        1 -> viewModel.selectTab(SeoResultTab.DESCRIPTIONS)
                        else -> viewModel.selectTab(SeoResultTab.TAGS)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        fun inputs(): Triple<String, String, String> {
            val script = binding.inputScript.text?.toString()?.trim().orEmpty()
            val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
            val topic = binding.inputTopic.text?.toString()?.trim().orEmpty()
            return Triple(script, title, topic)
        }

        binding.buttonGenerate.setOnClickListener {
            val (script, title, topic) = inputs()
            viewModel.generate(script, title, topic)
        }
        binding.buttonRegenerate.setOnClickListener {
            val (script, title, topic) = inputs()
            viewModel.generate(script, title, topic)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (binding.tabs.selectedTabPosition != state.tab.ordinal) {
                        binding.tabs.getTabAt(state.tab.ordinal)?.select()
                    }

                    binding.shimmerLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    val showRecycler = state.shouldShowRecycler()
                    binding.recyclerResults.visibility = if (showRecycler) View.VISIBLE else View.GONE

                    adapter.submitList(state.itemsForTab()) {
                        if (showRecycler) {
                            binding.recyclerResults.scheduleLayoutAnimation()
                        }
                    }

                    val showEmpty = !state.isLoading && !showRecycler
                    binding.textEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
                    if (showEmpty) {
                        val msg =
                            if (!state.hasGenerated) {
                                R.string.seo_empty_prompt
                            } else {
                                R.string.seo_results_empty_tab
                            }
                        binding.textEmpty.setText(msg)
                    }

                    if (state.isLoading) {
                        binding.shimmerLoading.startShimmer()
                    } else {
                        binding.shimmerLoading.stopShimmer()
                    }
                    binding.buttonGenerate.isEnabled = !state.isLoading
                    binding.buttonRegenerate.visibility = if (state.hasGenerated) View.VISIBLE else View.GONE
                    binding.buttonRegenerate.isEnabled = state.hasGenerated && !state.isLoading

                    state.errorMessage?.let { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        viewModel.consumeError()
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("seo_line", text))
        Snackbar.make(binding.root, R.string.message_copied, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
