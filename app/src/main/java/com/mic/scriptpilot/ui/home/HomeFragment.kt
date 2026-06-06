package com.mic.scriptpilot.ui.home

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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentHomeBinding
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.ui.common.adapter.ProjectCardStyle
import com.mic.scriptpilot.ui.common.adapter.ProjectListAdapter
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private val recentAdapter = ProjectListAdapter(ProjectCardStyle.COMPACT) { project ->
        showProjectDetail(project)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.title_home_dashboard)

        binding.textGreeting.setText(greetingStringRes())

        binding.recyclerRecent.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerRecent.adapter = recentAdapter
        binding.recyclerRecent.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            changeDuration = 180
        }

        fun navigateWithPress(target: View, action: () -> Unit) {
            target.setOnClickListener { v ->
                v.playCardPressAnimation()
                action()
            }
        }

        val nav = findNavController()
        navigateWithPress(binding.cardTrends) { nav.navigate(HomeFragmentDirections.actionHomeToTrendInput()) }
        navigateWithPress(binding.cardIdea) { nav.navigate(HomeFragmentDirections.actionHomeToIdea()) }
        navigateWithPress(binding.cardScript) { nav.navigate(HomeFragmentDirections.actionHomeToScript()) }
        navigateWithPress(binding.cardShorts) { nav.navigate(HomeFragmentDirections.actionHomeToShorts()) }
        navigateWithPress(binding.cardSeo) { nav.navigate(HomeFragmentDirections.actionHomeToSeo()) }
        navigateWithPress(binding.buttonViewAllProjects) { nav.navigate(HomeFragmentDirections.actionHomeToProjects()) }
        navigateWithPress(binding.buttonFirstProject) { nav.navigate(HomeFragmentDirections.actionHomeToScript()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentProjects.collect { projects ->
                    recentAdapter.submitList(projects)
                    val empty = projects.isEmpty()
                    binding.emptyRecent.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.recyclerRecent.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun greetingStringRes(): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            in 17..20 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
    }

    private fun showProjectDetail(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(project.title)
            .setMessage(project.script)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
