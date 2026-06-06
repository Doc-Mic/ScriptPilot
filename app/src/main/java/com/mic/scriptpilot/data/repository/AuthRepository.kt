package com.mic.scriptpilot.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
)

class AuthException(message: String) : Exception(message)

interface AuthRepository {
    val authState: Flow<AuthUser?>
    fun currentUser(): AuthUser?
    fun isSignedIn(): Boolean
    suspend fun login(email: String, password: String): AuthUser
    suspend fun signup(name: String, email: String, password: String): AuthUser
    suspend fun signInWithGoogle(idToken: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    suspend fun updateDisplayName(name: String): AuthUser
    suspend fun signOut()
}

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) : AuthRepository {
    override val authState: Flow<AuthUser?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                trySend(auth.currentUser.toAuthUser())
            }
            firebaseAuth.addAuthStateListener(listener)
            awaitClose { firebaseAuth.removeAuthStateListener(listener) }
        }

    override fun currentUser(): AuthUser? = firebaseAuth.currentUser.toAuthUser()

    override fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    override suspend fun login(email: String, password: String): AuthUser =
        runFirebaseAuth {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user.requireAuthUser()
        }

    override suspend fun signup(name: String, email: String, password: String): AuthUser =
        runFirebaseAuth {
            val user = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
            val firebaseUser = user ?: throw AuthException("We could not create your account. Please try again.")
            val profileUpdates =
                UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
            firebaseUser.updateProfile(profileUpdates).await()
            firebaseUser.reload().await()
            firebaseAuth.currentUser.requireAuthUser()
        }

    override suspend fun signInWithGoogle(idToken: String): AuthUser =
        runFirebaseAuth {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().user.requireAuthUser()
        }

    override suspend fun sendPasswordReset(email: String) {
        runFirebaseAuth {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
        }
    }

    override suspend fun updateDisplayName(name: String): AuthUser =
        runFirebaseAuth {
            val user = firebaseAuth.currentUser
                ?: throw AuthException("Sign in again to update your profile.")
            val profileUpdates =
                UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
            user.updateProfile(profileUpdates).await()
            user.reload().await()
            firebaseAuth.currentUser.requireAuthUser()
        }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}

private suspend fun <T> runFirebaseAuth(block: suspend () -> T): T =
    try {
        block()
    } catch (e: FirebaseAuthException) {
        throw AuthException(e.toSafeMessage())
    } catch (e: AuthException) {
        throw e
    } catch (_: Exception) {
        throw AuthException("Authentication failed. Please try again.")
    }

private fun FirebaseAuthException.toSafeMessage(): String =
    when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_USER_NOT_FOUND",
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        -> "Email or password is incorrect."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists with this email."
        "ERROR_WEAK_PASSWORD" -> "Use a stronger password."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Check your internet connection and try again."
        else -> "Authentication failed. Please try again."
    }

private fun FirebaseUser?.requireAuthUser(): AuthUser =
    this.toAuthUser() ?: throw AuthException("Authentication failed. Please try again.")

private fun FirebaseUser?.toAuthUser(): AuthUser? =
    this?.let { user ->
        AuthUser(
            uid = user.uid,
            displayName = user.displayName?.takeIf { it.isNotBlank() },
            email = user.email?.takeIf { it.isNotBlank() },
        )
    }
