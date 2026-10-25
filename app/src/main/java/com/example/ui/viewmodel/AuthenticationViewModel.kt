package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserRole
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthUserInfo
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.GoogleSignInOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val selectedRole: UserRole = UserRole.STUDENT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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

    fun onRoleChange(role: UserRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role, errorMessage = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun attemptAutoSignIn(
        context: Context,
        onAuthSuccess: (AuthUserInfo) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val current = authRepository.getCurrentUser()
        if (current != null) {
            onAuthSuccess(current)
            return
        }
        viewModelScope.launch {
            when (val outcome = authRepository.attemptAutoSignIn(context)) {
                is GoogleSignInOutcome.Success -> onAuthSuccess(outcome.user)
                GoogleSignInOutcome.Cancelled,
                is GoogleSignInOutcome.Error -> onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        onSuccess: (AuthUserInfo) -> Unit = {}
    ) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val outcome = authRepository.signInWithGoogleInteractive(context)) {
                is GoogleSignInOutcome.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                    onSuccess(outcome.user)
                }
                GoogleSignInOutcome.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is GoogleSignInOutcome.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = outcome.message
                    )
                }
            }
        }
    }

    fun signOut(context: Context? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.signOut(context)
            _uiState.value = AuthUiState()
            onComplete()
        }
    }
}
