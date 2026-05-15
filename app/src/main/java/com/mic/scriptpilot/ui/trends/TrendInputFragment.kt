package com.mic.scriptpilot.ui.trends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentTrendInputBinding
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrendInputFragment : Fragment() {
    private var _binding: FragmentTrendInputBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrendInputViewModel by viewModels()

    private lateinit var categoryOptions: List<TrendCategoryOption>
    private lateinit var locations: Array<String>
    private lateinit var ranges: Array<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrendInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.toolbar.setupWithNavController(navController, rootAppBarConfiguration())

        categoryOptions = TrendCategoryCatalog.categories
        locations = resources.getStringArray(R.array.trend_locations)
        ranges = resources.getStringArray(R.array.trend_time_ranges)
        val categoryLabels = categoryOptions.map { it.label }

        binding.inputCategory.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categoryLabels),
        )
        binding.inputLocation.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, locations),
        )
        binding.inputTimeRange.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ranges),
        )

        binding.inputCategory.setOnItemClickListener { _, _, position, _ ->
            viewModel.setCategory(categoryOptions[position].id)
        }
        binding.inputLocation.setOnItemClickListener { _, _, position, _ ->
            viewModel.setRegion(locations[position])
        }
        binding.inputTimeRange.setOnItemClickListener { _, _, position, _ ->
            viewModel.setTimeRange(ranges[position])
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val categoryLabel = state.selectedCategoryLabel
                    if (binding.inputCategory.text?.toString() != categoryLabel) {
                        binding.inputCategory.setText(categoryLabel, false)
                    }
                    if (binding.inputLocation.text?.toString() != state.region) {
                        binding.inputLocation.setText(state.region, false)
                    }
                    if (binding.inputTimeRange.text?.toString() != state.timeRange) {
                        binding.inputTimeRange.setText(state.timeRange, false)
                    }
                    binding.chipSelectedCategory.text = getString(
                        R.string.trend_selected_category_chip_format,
                        categoryLabel,
                    )
                }
            }
        }

        binding.buttonFindTrends.setOnClickListener {
            val state = viewModel.uiState.value
            val action = TrendInputFragmentDirections.actionTrendInputToResults(
                state.selectedCategoryId,
                state.region,
                state.timeRange,
            )
            navController.navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
