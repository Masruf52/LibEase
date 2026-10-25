package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserRole
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.data.repository.AuthUserInfo
import com.example.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val confirmPassword: String = "",
    val selectedRole: UserRole = UserRole.STUDENT,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetPasswordSuccessMessage: String? = null,
    val forgotPasswordDialogOpen: Boolean = false
)

class AuthenticationViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentAuthUser: StateFlow<AuthUserInfo?> = authRepository.currentAuthUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authRepository.getCurrentUser()
        )

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword, errorMessage = null)
    }

    fun onRoleChange(role: UserRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(isConfirmPasswordVisible = !_uiState.value.isConfirmPasswordVisible)
    }

    fun setRegisterMode(isRegister: Boolean) {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = isRegister,
            errorMessage = null,
            resetPasswordSuccessMessage = null
        )
    }

    fun openForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(forgotPasswordDialogOpen = true, errorMessage = null)
    }

    fun closeForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(forgotPasswordDialogOpen = false)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, resetPasswordSuccessMessage = null)
    }

    fun login(onSuccess: (AuthUserInfo) -> Unit = {}) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email address")
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signInWithEmail(email, password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { user ->
                onSuccess(user)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.localizedMessage ?: "Authentication failed. Check credentials."
                )
            }
        }
    }

    fun register(onSuccess: (AuthUserInfo) -> Unit = {}) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword
        val name = _uiState.value.name.trim()

        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your full name")
            return
        }
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your university/institutional email")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUpWithEmail(email, password, name)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { user ->
                onSuccess(user)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.localizedMessage ?: "Registration failed. Please try again."
                )
            }
        }
    }

    fun sendPasswordReset(resetEmail: String? = null) {
        val targetEmail = (resetEmail ?: _uiState.value.email).trim()
        if (targetEmail.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please provide an email to send password reset")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.sendPasswordResetEmail(targetEmail)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    resetPasswordSuccessMessage = "Password reset link sent to $targetEmail",
                    forgotPasswordDialogOpen = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.localizedMessage ?: "Failed to send reset email."
                )
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: (AuthUserInfo) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signInWithGoogle(idToken)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { user ->
                onSuccess(user)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.localizedMessage ?: "Google sign-in failed."
                )
            }
        }
    }

    fun signInWithCredentialManager(
        context: Context,
        serverClientId: String,
        onSuccess: (AuthUserInfo) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signInWithCredentialManager(context, serverClientId)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { user ->
                onSuccess(user)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.localizedMessage ?: "Credential Manager sign-in failed."
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState()
        }
    }

    /**
     * Helper to pre-populate credentials for fast testing / demo mode.
     */
    fun fillDemoCredentials(email: String, role: UserRole) {
        _uiState.value = _uiState.value.copy(
            email = email,
            password = "password123",
            name = if (role == UserRole.ADMIN) "Dr. Eleanor Vance" else "Alex Rivers",
            selectedRole = role,
            errorMessage = null
        )
    }
}
