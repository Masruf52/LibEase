package com.example.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Clean data representation of an authenticated Firebase user.
 */
data class AuthUserInfo(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean = false
)

fun FirebaseUser.toAuthUserInfo(): AuthUserInfo = AuthUserInfo(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    isAnonymous = isAnonymous
)

/**
 * Authentication UI / domain state wrapper.
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: AuthUserInfo) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Contract for Library Management Authentication operations.
 */
interface AuthRepository {
    val currentAuthUser: Flow<AuthUserInfo?>

    fun getCurrentUser(): AuthUserInfo?
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUserInfo>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String? = null): Result<AuthUserInfo>
    suspend fun signInWithGoogle(idToken: String): Result<AuthUserInfo>
    suspend fun signInWithCredentialManager(context: Context, serverClientId: String): Result<AuthUserInfo>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}

/**
 * Concrete implementation utilizing Firebase Auth and Android Credential Manager.
 */
class FirebaseAuthRepository(
    private val authProvider: () -> FirebaseAuth? = {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
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
        // Emit initial value immediately
        trySend(instance.currentUser?.toAuthUserInfo())

        awaitClose {
            instance.removeAuthStateListener(listener)
        }
    }

    override fun getCurrentUser(): AuthUserInfo? {
        return auth?.currentUser?.toAuthUserInfo()
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUserInfo> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth is not initialized. Please ensure google-services.json is configured.")
        )
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user?.toAuthUserInfo()
                ?: return Result.failure(IllegalStateException("Sign in succeeded but user is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): Result<AuthUserInfo> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth is not initialized. Please ensure google-services.json is configured.")
        )
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Account creation succeeded but user is null"))

            if (!displayName.isNullOrBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }

            Result.success(firebaseUser.toAuthUserInfo())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUserInfo> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth is not initialized. Please ensure google-services.json is configured.")
        )
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user?.toAuthUserInfo()
                ?: return Result.failure(IllegalStateException("Google sign-in succeeded but user is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithCredentialManager(
        context: Context,
        serverClientId: String
    ): Result<AuthUserInfo> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                signInWithGoogle(googleIdTokenCredential.idToken)
            } else {
                Result.failure(IllegalArgumentException("Unsupported credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth is not initialized. Please ensure google-services.json is configured.")
        )
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth?.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
