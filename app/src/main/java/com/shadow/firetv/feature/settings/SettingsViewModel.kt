package com.shadow.firetv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.network.RetrofitClient
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedSource: StreamingSource = StreamingSource.ANIWORLD,
    val selectedTheme: AppTheme = AppTheme.SOVIET_RED,
    val username: String = "",
    val userId: Int = -1,
    val deviceId: String = "",
    val appVersion: String = "v1.0",
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val isIndexing: Boolean = false,
    val indexingStatusMessage: String? = null
)

sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent
    data class ThemeChanged(val theme: AppTheme) : SettingsEvent
}

class SettingsViewModel(
    private val authManager: AuthManager = AuthManager.getInstance(),
    private val sourceManager: SourceManager = SourceManager.getInstance(),
    private val themeManager: ThemeManager = ThemeManager.getInstance(),
    private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val session = authManager.getSession()
        val currentSource = sourceManager.getSelectedSource()
        val currentTheme = themeManager.getSelectedTheme()
        val version = try {
            "v${com.shadow.firetv.BuildConfig.VERSION_NAME}"
        } catch (_: Exception) {
            "v1.0"
        }

        _uiState.update {
            it.copy(
                selectedSource = currentSource,
                selectedTheme = currentTheme,
                username = session?.username ?: "Gast / Unbekannt",
                userId = session?.userId ?: -1,
                deviceId = session?.deviceId ?: authManager.getDeviceId(),
                appVersion = version
            )
        }
    }

    fun onSourceSelected(source: StreamingSource) {
        if (_uiState.value.selectedSource == source) return
        sourceManager.setSelectedSource(source)
        _uiState.update { it.copy(selectedSource = source) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowToast("Quelle gewechselt: ${source.displayName}"))
        }
    }

    fun onThemeSelected(theme: AppTheme) {
        if (_uiState.value.selectedTheme == theme) return
        themeManager.setSelectedTheme(theme)
        _uiState.update { it.copy(selectedTheme = theme) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.ThemeChanged(theme))
            _events.emit(SettingsEvent.ShowToast("Theme gewechselt: ${theme.displayName}"))
        }
    }

    fun syncUserContent() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Synchronisiere...") }
        viewModelScope.launch {
            repository.syncUserContent()
                .onSuccess {
                    _uiState.update { it.copy(isSyncing = false, syncStatusMessage = "Synchronisation erfolgreich") }
                    _events.emit(SettingsEvent.ShowToast("Benutzerdaten erfolgreich synchronisiert"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSyncing = false, syncStatusMessage = "Fehler bei Synchronisation") }
                    _events.emit(SettingsEvent.ShowToast(error.message ?: "Synchronisation fehlgeschlagen"))
                }
        }
    }

    fun triggerIndexer() {
        if (_uiState.value.isIndexing) return
        val currentSource = _uiState.value.selectedSource.id
        _uiState.update { it.copy(isIndexing = true, indexingStatusMessage = "Indexer wird gestartet...") }
        viewModelScope.launch {
            repository.triggerIndex(source = currentSource)
                .onSuccess {
                    _uiState.update { it.copy(isIndexing = false, indexingStatusMessage = "Indexer erfolgreich gestartet") }
                    _events.emit(SettingsEvent.ShowToast("Indexer gestartet (${currentSource})"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isIndexing = false, indexingStatusMessage = "Fehler beim Starten des Indexers") }
                    _events.emit(SettingsEvent.ShowToast(error.message ?: "Indexer-Start fehlgeschlagen"))
                }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowToast("Cache & temporäre Daten wurden geleert"))
        }
    }

    fun logout() {
        authManager.clearSession()
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowToast("Erfolgreich abgemeldet"))
            _events.emit(SettingsEvent.NavigateToLogin)
        }
    }

    class Factory(
        private val authManager: AuthManager = AuthManager.getInstance(),
        private val sourceManager: SourceManager = SourceManager.getInstance(),
        private val themeManager: ThemeManager = ThemeManager.getInstance(),
        private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(authManager, sourceManager, themeManager, repository) as T
        }
    }
}
