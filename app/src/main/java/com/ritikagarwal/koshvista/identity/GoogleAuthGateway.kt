package com.ritikagarwal.koshvista.identity

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class SignedInOwner(val subjectId: String, val displayName: String?, val email: String?)

/** Google Credential Manager supplies the stable account identity; email is display-only. */
class GoogleAuthGateway {
    suspend fun signIn(activity: Activity, webClientId: String): SignedInOwner {
        require(webClientId.isNotBlank()) { "Google sign-in is not configured" }
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google sign-in did not return an identity"
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val subject = google.uniqueId
        require(subject.isNotBlank()) { "Google account identifier is missing" }
        return SignedInOwner(subject, google.displayName, google.email)
    }
}
