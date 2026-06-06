package com.mic.scriptpilot.ui.profile

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
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentEditProfileBinding
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.profile_edit_profile, showBack = true) {
            findNavController().navigateUp()
        }

        binding.buttonSaveProfile.setOnClickListener {
            viewModel.updateDisplayName(binding.inputDisplayName.text?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val displayName = state.displayName.ifBlank { getString(R.string.profile_creator_name) }
                        if (binding.inputDisplayName.text.isNullOrBlank()) {
                            binding.inputDisplayName.setText(displayName)
                        }
                        binding.textAvatarInitials.text = initialsFor(displayName, state.email)
                        binding.inputEmail.setText(state.email)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        if (event is ProfileEvent.Message) {
                            Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_SHORT).show()
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
