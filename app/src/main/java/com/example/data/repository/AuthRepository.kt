package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Clean data representation of an authenticated Google / Firebase user.
 */
data class AuthUserInfo(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

fun FirebaseUser.toAuthUserInfo(): AuthUserInfo = AuthUserInfo(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString()
)

sealed interface GoogleSignInOutcome {
    data class Success(val user: AuthUserInfo) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

/**
 * Contract for Library Management Authentication operations using Google Sign-In
 * via Jetpack Credential Manager exclusively.
 */
interface AuthRepository {
    val currentAuthUser: Flow<AuthUserInfo?>

    fun getCurrentUser(): AuthUserInfo?
    suspend fun attemptAutoSignIn(context: Context): GoogleSignInOutcome
    suspend fun signInWithGoogleInteractive(context: Context): GoogleSignInOutcome
    suspend fun signOut(context: Context? = null): Result<Unit>
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Concrete implementation utilizing Firebase Auth and Jetpack Credential Manager
 * with GetSignInWithGoogleOption (interactive) and GetGoogleIdOption (silent startup).
 */
class FirebaseAuthRepository(
    private val authProvider: () -> FirebaseAuth? = {
        try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
    }
) : AuthRepository {

    private val auth: FirebaseAuth?
        get() = authProvider()

    override val currentAuthUser: Flow<AuthUserInfo?> = callbackFlow {
        val instance = auth
        if (instance == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.toAuthUserInfo())
        }
        instance.addAuthStateListener(listener)
        trySend(instance.currentUser?.toAuthUserInfo())

        awaitClose {
            instance.removeAuthStateListener(listener)
        }
    }

    override fun getCurrentUser(): AuthUserInfo? {
        return auth?.currentUser?.toAuthUserInfo()
    }

    override suspend fun attemptAutoSignIn(context: Context): GoogleSignInOutcome {
        val firebaseAuth = auth ?: return GoogleSignInOutcome.Cancelled
        firebaseAuth.currentUser?.let { existing ->
            return GoogleSignInOutcome.Success(existing.toAuthUserInfo())
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id unavailable for silent sign-in", e)
            return GoogleSignInOutcome.Cancelled
        }

        // Single credential option per request for API 36 Credential Manager responsiveness
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            exchangeGoogleCredential(firebaseAuth, result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In unavailable: ${e.message}")
            GoogleSignInOutcome.Cancelled
        }
    }

    override suspend fun signInWithGoogleInteractive(context: Context): GoogleSignInOutcome {
        val firebaseAuth = auth ?: return GoogleSignInOutcome.Error(
            "Firebase Authentication is not initialized."
        )

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "Missing default_web_client_id resource", e)
            return GoogleSignInOutcome.Error(
                "Google Sign-In configuration missing: default_web_client_id not found."
            )
        }

        val activityContext = context.findActivity() ?: context

        // Interactive Sign-In: strictly one GetSignInWithGoogleOption per request
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(activityContext, request)
            exchangeGoogleCredential(firebaseAuth, result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Interactive Google Sign-In failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }

    private suspend fun exchangeGoogleCredential(
        firebaseAuth: FirebaseAuth,
        credential: androidx.credentials.Credential
    ): GoogleSignInOutcome {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = firebaseAuth.signInWithCredential(authCredential).await()
            val user = authResult.user?.toAuthUserInfo()
                ?: return GoogleSignInOutcome.Error("Google Sign-In succeeded, but user profile was empty.")
            return GoogleSignInOutcome.Success(user)
        }
        return GoogleSignInOutcome.Error("Unexpected credential type received.")
    }

    override suspend fun signOut(context: Context?): Result<Unit> {
        return try {
            auth?.signOut()
            if (context != null) {
                try {
                    val credentialManager = CredentialManager.create(context.applicationContext)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear CredentialManager state", e)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "Auth"
    }
}
