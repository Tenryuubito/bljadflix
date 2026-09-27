package com.shadow.firetv.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.UserSession
import com.shadow.firetv.core.network.RetrofitClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val usernameInput: String = "",
    val passwordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val username: String? = null
)

sealed interface LoginEvent {
    object NavigateToMain : LoginEvent
}

class LoginViewModel(
    private val repository: MovieRepository,
    private val authManager: AuthManager = AuthManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(usernameInput = username, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun submitLogin(
        username: String? = null,
        password: String? = null,
        context: Context? = null
    ) {
        val finalUsername = (username ?: _uiState.value.usernameInput).trim()
        val finalPassword = (password ?: _uiState.value.passwordInput).trim()

        if (finalUsername.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Bitte Benutzernamen eingeben") }
            return
        }
        if (finalPassword.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Bitte Passwort eingeben") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val deviceId = authManager.getDeviceId(context)

            repository.login(username = finalUsername, password = finalPassword, deviceId = deviceId, deviceName = "Fire TV Stick")
                .onSuccess { res ->
                    val session = UserSession(
                        token = res.token,
                        userId = res.user_id,
                        username = res.username,
                        deviceId = res.device_id
                    )
                    authManager.saveSession(session)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            username = res.username,
                            errorMessage = null
                        )
                    }
                    _events.emit(LoginEvent.NavigateToMain)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Anmeldung fehlgeschlagen. Ungültige Anmeldedaten."
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance),
        private val authManager: AuthManager = AuthManager.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository, authManager) as T
        }
    }
}
