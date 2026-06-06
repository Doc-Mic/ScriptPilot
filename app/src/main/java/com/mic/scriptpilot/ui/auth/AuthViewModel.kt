package com.mic.scriptpilot.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.AuthException
import com.mic.scriptpilot.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val authSuccess: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isSignedIn(): Boolean = authRepository.isSignedIn()

    fun login(email: String, password: String) {
        val validationError = validateEmailPassword(email, password)
        if (validationError != null) {
            showMessage(validationError)
            return
        }
        runAuth { authRepository.login(email, password) }
    }

    fun signup(name: String, email: String, password: String, confirmPassword: String) {
        val validationError = validateSignup(name, email, password, confirmPassword)
        if (validationError != null) {
            showMessage(validationError)
            return
        }
        runAuth { authRepository.signup(name, email, password) }
    }

    fun sendPasswordReset(email: String) {
        val validationError = validateEmail(email)
        if (validationError != null) {
            showMessage(validationError)
            return
        }
        viewModelScope.launch {
            _uiState.update { AuthUiState(loading = true) }
            runCatching {
                authRepository.sendPasswordReset(email)
            }.onSuccess {
                _uiState.update {
                    AuthUiState(message = "Password reset email sent. Check your inbox.")
                }
            }.onFailure { e ->
                _uiState.update {
                    AuthUiState(message = e.toAuthMessage())
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            showMessage("Google sign-in did not return a valid token.")
            return
        }
        runAuth { authRepository.signInWithGoogle(idToken) }
    }

    fun onGoogleSignInCancelled() {
        showMessage("Google sign-in cancelled.")
    }

    fun onGoogleSignInFailed(message: String? = null) {
        showMessage(message ?: "Google sign-in failed. Please try again.")
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeAuthSuccess() {
        _uiState.update { it.copy(authSuccess = false) }
    }

    private fun runAuth(block: suspend () -> Any) {
        viewModelScope.launch {
            _uiState.update { AuthUiState(loading = true) }
            runCatching {
                block()
            }.onSuccess {
                _uiState.update { AuthUiState(authSuccess = true) }
            }.onFailure { e ->
                _uiState.update { AuthUiState(message = e.toAuthMessage()) }
            }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(loading = false, message = message) }
    }
}

private fun validateSignup(
    name: String,
    email: String,
    password: String,
    confirmPassword: String,
): String? =
    when {
        name.isBlank() -> "Enter your name."
        validateEmailPassword(email, password) != null -> validateEmailPassword(email, password)
        confirmPassword.isBlank() -> "Confirm your password."
        password != confirmPassword -> "Passwords do not match."
        else -> null
    }

private fun validateEmailPassword(email: String, password: String): String? =
    validateEmail(email) ?: when {
        password.isBlank() -> "Enter your password."
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least 6 characters."
        else -> null
    }

private fun validateEmail(email: String): String? =
    when {
        email.isBlank() -> "Enter your email address."
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
        else -> null
    }

private fun Throwable.toAuthMessage(): String =
    when (this) {
        is AuthException -> message ?: "Authentication failed. Please try again."
        else -> "Authentication failed. Please try again."
    }

private const val MIN_PASSWORD_LENGTH = 6
