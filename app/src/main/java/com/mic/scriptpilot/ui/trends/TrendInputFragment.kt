package com.mic.scriptpilot.ui.trends

import android.os.Bundle
import android.text.InputType
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentTrendInputBinding
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrendInputFragment : Fragment() {
    private var _binding: FragmentTrendInputBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrendInputViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrendInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        binding.header.setupScreenHeader(
            R.string.title_trends,
            showBack = true,
            showHome = true,
            onHome = { navController.navigateHomeClearingWorkflow() },
        ) {
            navController.navigateUp()
        }

        setupDropdowns()

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
                }
            }
        }

        binding.buttonFindTrends.setOnClickListener {
            dismissDropdowns()
            clearDropdownFocus()
            val state = viewModel.uiState.value
            val action = TrendInputFragmentDirections.actionTrendInputToResults(
                state.selectedCategoryId,
                state.region,
                state.timeRange,
            )
            navController.navigate(action)
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            setupDropdowns()
        }
    }

    override fun onPause() {
        dismissDropdowns()
        super.onPause()
    }

    private fun setupDropdowns() {
        val categoryOptions = TrendCategoryCatalog.categories
        val categoryLabels = categoryOptions.map { it.label }
        val locations = resources.getStringArray(R.array.trend_locations).toList()
        val ranges = resources.getStringArray(R.array.trend_time_ranges).toList()
        val state = viewModel.uiState.value

        binding.inputCategory.attachDropdown(
            values = categoryLabels,
            selectedValue = state.selectedCategoryLabel,
        ) { selected ->
            val categoryId = categoryOptions.firstOrNull { it.label == selected }?.id ?: return@attachDropdown
            viewModel.setCategory(categoryId)
        }
        binding.inputLocation.attachDropdown(
            values = locations,
            selectedValue = state.region,
        ) { selected ->
            viewModel.setRegion(selected)
        }
        binding.inputTimeRange.attachDropdown(
            values = ranges,
            selectedValue = state.timeRange,
        ) { selected ->
            viewModel.setTimeRange(selected)
        }
    }

    private fun MaterialAutoCompleteTextView.attachDropdown(
        values: List<String>,
        selectedValue: String,
        onSelected: (String) -> Unit,
    ) {
        setAdapter(null)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, values)

        threshold = 0
        inputType = InputType.TYPE_NULL
        keyListener = null
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = true
        isCursorVisible = false
        setAdapter(adapter)
        setText(selectedValue, false)
        setOnClickListener { showDropDown() }
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showDropDown()
            }
        }
        setOnItemClickListener { parent, _, position, _ ->
            onSelected(parent.getItemAtPosition(position).toString())
        }
    }

    private fun dismissDropdowns() {
        if (_binding == null) return
        binding.inputCategory.dismissDropDown()
        binding.inputLocation.dismissDropDown()
        binding.inputTimeRange.dismissDropDown()
    }

    private fun clearDropdownFocus() {
        if (_binding == null) return
        binding.inputCategory.clearFocus()
        binding.inputLocation.clearFocus()
        binding.inputTimeRange.clearFocus()
        binding.root.clearFocus()
    }

    private fun clearDropdownListenersAndAdapters() {
        if (_binding == null) return
        listOf(binding.inputCategory, binding.inputLocation, binding.inputTimeRange).forEach { input ->
            input.dismissDropDown()
            input.setOnClickListener(null)
            input.onFocusChangeListener = null
            input.setOnItemClickListener(null)
            input.setAdapter(null)
            input.keyListener = null
        }
    }

    override fun onDestroyView() {
        dismissDropdowns()
        clearDropdownListenersAndAdapters()
        super.onDestroyView()
        _binding = null
    }
}
