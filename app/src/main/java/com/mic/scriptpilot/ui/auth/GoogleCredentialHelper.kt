package com.mic.scriptpilot.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class GoogleSignInCancelledException : Exception()

suspend fun requestGoogleIdToken(context: Context, webClientId: String): String {
    val credentialManager = CredentialManager.create(context)
    val googleOption =
        GetSignInWithGoogleOption.Builder(webClientId)
            .build()
    val request =
        GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
    val result =
        try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInCancelledException()
        } catch (e: NoCredentialException) {
            throw IllegalStateException("No Google account is available on this device.", e)
        }
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google sign-in response could not be read.", e)
        }
    }
    throw IllegalStateException("Google sign-in returned an unsupported credential.")
}
