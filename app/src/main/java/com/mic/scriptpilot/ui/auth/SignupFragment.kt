package com.mic.scriptpilot.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.AuthActivity
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentSignupBinding
import com.mic.scriptpilot.ui.common.setupScreenHeader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignupFragment : Fragment() {
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (viewModel.isSignedIn()) {
            openMain()
            return
        }

        binding.header.setupScreenHeader(R.string.auth_signup_title, R.string.auth_signup_subtitle)

        binding.buttonSignup.setOnClickListener {
            viewModel.signup(
                binding.inputName.text?.toString().orEmpty(),
                binding.inputEmail.text?.toString().orEmpty(),
                binding.inputPassword.text?.toString().orEmpty(),
                binding.inputConfirmPassword.text?.toString().orEmpty(),
            )
        }
        binding.buttonGoogle.setOnClickListener {
            signInWithGoogle()
        }
        binding.buttonLogin.setOnClickListener {
            findNavController().navigate(R.id.action_signup_to_login)
        }

        collectState()
    }

    private fun signInWithGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                requestGoogleIdToken(requireActivity(), getString(R.string.default_web_client_id))
            }.onSuccess { token ->
                viewModel.signInWithGoogle(token)
            }.onFailure { e ->
                if (e is GoogleSignInCancelledException) {
                    viewModel.onGoogleSignInCancelled()
                } else {
                    viewModel.onGoogleSignInFailed(e.message)
                }
            }
        }
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.buttonSignup.isEnabled = !state.loading
                    binding.buttonGoogle.isEnabled = !state.loading
                    binding.buttonLogin.isEnabled = !state.loading

                    if (state.authSuccess) {
                        viewModel.consumeAuthSuccess()
                        openMain()
                    }
                    state.message?.let { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun openMain() {
        (requireActivity() as AuthActivity).openMain()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
