package com.mic.scriptpilot.ui.trends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentTrendResultBinding
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrendResultFragment : Fragment() {
    private var _binding: FragmentTrendResultBinding? = null
    private val binding get() = _binding!!

    private val args: TrendResultFragmentArgs by navArgs()
    private val viewModel: TrendsViewModel by viewModels()

    private val adapter = TrendAdapter { trend ->
        findNavController().navigate(
            TrendResultFragmentDirections.actionTrendResultToIdea(
                topic = trend.title,
                trendCategory = TrendCategoryCatalog.labelFor(args.category),
                trendVirality = trend.virality,
                trendOpportunity = trend.opportunity,
            ),
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrendResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())
        val categoryLabel = TrendCategoryCatalog.labelFor(args.category)
        binding.toolbar.subtitle = listOf(categoryLabel, args.location, args.timeRange).joinToString(" · ")

        binding.recyclerTrends.layoutManager = LinearLayoutManager(requireContext())
        if (binding.recyclerTrends.layoutAnimation == null) {
            binding.recyclerTrends.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_trend_list_enter)
        }
        binding.recyclerTrends.itemAnimator =
            DefaultItemAnimator().apply {
                addDuration = 220
                changeDuration = 180
                moveDuration = 180
                removeDuration = 180
            }
        binding.recyclerTrends.adapter = adapter

        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        viewModel.load(args.category, args.location, args.timeRange)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val loading = state.loading
                    val error = state.errorMessage

                    binding.shimmerContainer.isVisible = loading
                    if (loading) {
                        binding.shimmerContainer.startShimmer()
                    } else {
                        binding.shimmerContainer.stopShimmer()
                    }

                    binding.errorState.isVisible = !loading && error != null
                    binding.textErrorMessage.text = error.orEmpty()

                    binding.emptyState.isVisible = !loading && error == null && state.trends.isEmpty()
                    binding.recyclerTrends.isVisible = !loading && error == null && state.trends.isNotEmpty()

                    adapter.submitList(state.trends) {
                        if (!loading && state.trends.isNotEmpty()) {
                            binding.recyclerTrends.scheduleLayoutAnimation()
                        }
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
