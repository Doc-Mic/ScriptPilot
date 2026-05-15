package com.mic.scriptpilot.ui.projects

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mic.scriptpilot.databinding.FragmentProjectListBinding
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.ui.common.adapter.ProjectCardStyle
import com.mic.scriptpilot.ui.common.adapter.ProjectListAdapter
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProjectListFragment : Fragment() {
    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectListViewModel by viewModels()

    private val adapter = ProjectListAdapter(ProjectCardStyle.ROW) { project ->
        showProjectDetail(project)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())

        binding.recyclerProjects.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProjects.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.projects.collect { list ->
                    adapter.submitList(list)
                    binding.emptyState.isVisible = list.isEmpty()
                    binding.recyclerProjects.isVisible = list.isNotEmpty()
                }
            }
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
