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
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentProfileBinding
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.title_profile, R.string.profile_creator_subtitle)
        setupActions()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun setupActions() {
        binding.buttonEditProfile.setOnClickListener { view ->
            view.playCardPressAnimation()
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }
        binding.cardPremiumPlans.setOnClickListener { view ->
            view.playCardPressAnimation()
            findNavController().navigate(R.id.action_profile_to_premiumPlans)
        }
        binding.cardSettings.setOnClickListener { view ->
            view.playCardPressAnimation()
            findNavController().navigate(R.id.action_profile_to_settings)
        }
        binding.cardSupportLegal.setOnClickListener { view ->
            view.playCardPressAnimation()
            findNavController().navigate(R.id.action_profile_to_supportLegal)
        }
        binding.cardAbout.setOnClickListener { view ->
            view.playCardPressAnimation()
            findNavController().navigate(R.id.action_profile_to_about)
        }
    }

    private fun render(state: ProfileUiState) {
        val displayName = state.displayName.ifBlank { getString(R.string.profile_creator_name) }
        binding.textAvatarInitials.text = initialsFor(displayName, state.email)
        binding.textCreatorName.text = displayName
        binding.textCreatorSubtitle.text = getString(R.string.profile_hub_subtitle)
        binding.textCreatorEmail.text = state.email.ifBlank { getString(R.string.profile_email_placeholder) }

        binding.textScriptsCreated.text = numberFormat.format(state.usageStats.scriptsCreated)
        binding.textIdeasGenerated.text = numberFormat.format(state.usageStats.ideasGenerated)
        binding.textProjectsSaved.text = numberFormat.format(state.usageStats.projectsSaved)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

fun initialsFor(displayName: String, email: String): String {
    val nameParts =
        displayName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    val fromName =
        when {
            nameParts.size >= 2 -> "${nameParts.first().first()}${nameParts.last().first()}"
            nameParts.size == 1 -> nameParts.first().first().toString()
            else -> ""
        }
    val raw =
        fromName.ifBlank { email.trim().firstOrNull()?.toString().orEmpty() }
            .ifBlank { "SP" }
    return raw.take(2).uppercase()
}
