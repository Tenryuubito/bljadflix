package com.shadow.firetv.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.network.RetrofitClient
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainMode {
    CONTINUE_WATCHING, WATCHLIST, GENRE, SEARCH
}

data class MainUiState(
    val selectedSource: StreamingSource = StreamingSource.ANIWORLD,
    val mode: MainMode = MainMode.CONTINUE_WATCHING,
    val currentGenre: String = "",
    val currentQuery: String = "",
    val sidebarItems: List<String> = emptyList(),
    val selectedSidebarIndex: Int = 0,
    val movies: List<Movie> = emptyList(),
    val continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
    val isEndOfList: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val firstLoadedPage: Int = 1,
    val lastLoadedPage: Int = 1,
    val isInitializing: Boolean = true,
    val initCurrentStep: Int = 0,
    val initTotalSteps: Int = 1,
    val initStepDescription: String = "Starte Anwendung...",
    val initProgressPercent: Int = 0
)

sealed interface MainEvent {
    data class ShowToast(val message: String) : MainEvent
    data class NavigateToLogin(val message: String? = null) : MainEvent
    object NavigateToSettings : MainEvent
}

class MainViewModel(
    private val repository: MovieRepository,
    private val authManager: AuthManager = AuthManager.getInstance(),
    private val sourceManager: SourceManager = SourceManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(selectedSource = sourceManager.getSelectedSource()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var debounceJob: Job? = null
    private var syncJob: Job? = null
    private var initJob: Job? = null
    private val maxPages = 5
    private var lastNonSearchSidebarIndex = 0

    init {
        startAppInitialization()
    }

    fun startAppInitialization() {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInitializing = true,
                    initCurrentStep = 0,
                    initTotalSteps = 5,
                    initStepDescription = "Starte Anwendung...",
                    initProgressPercent = 0
                )
            }

            // 1. Authentifizierung prüfen
            updateInitProgress(1, 5, "Prüfe Authentifizierung...", 20)
            if (!checkAuthStatusInternal()) {
                _uiState.update { it.copy(isInitializing = false) }
                return@launch
            }

            // 2. Kategorien vom Backend-Server laden
            val currentSource = sourceManager.getSelectedSource()
            updateInitProgress(2, 5, "Lade Kategorien von Backend (${currentSource.displayName})...", 40)

            var genresList = emptyList<String>()
            repository.getGenres()
                .onSuccess { genres ->
                    genresList = sanitizeGenres(genres)
                }
                .onFailure {
                    genresList = emptyList()
                }

            val baseComponents = listOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien", "⚙️ Einstellungen")
            val sidebarItems = baseComponents + genresList
            _uiState.update { it.copy(sidebarItems = sidebarItems, selectedSource = currentSource) }

            // 3. Benutzerdaten synchronisieren
            updateInitProgress(3, 5, "Synchronisiere Benutzerinhalte vom Server...", 60)
            repository.syncUserContent()

            // 4. Startinhalte laden
            updateInitProgress(4, 5, "Lade Startinhalte...", 85)
            fetchInitialContent()

            // 5. Fertigstellen
            updateInitProgress(5, 5, "Initialisierung abgeschlossen", 100)
            delay(100)

            _uiState.update { it.copy(isInitializing = false) }

            syncAndPollStatus()
        }
    }

    private suspend fun fetchInitialContent() {
        val currentState = _uiState.value
        when (currentState.mode) {
            MainMode.CONTINUE_WATCHING -> {
                repository.getContinueWatching()
                    .onSuccess { res ->
                        _uiState.update {
                            it.copy(
                                continueWatchingItems = res,
                                isLoading = false,
                                errorMessage = if (res.isEmpty()) "Keine Inhalte vorhanden" else null
                            )
                        }
                    }
                    .onFailure { error -> handleMovieError(error, 1) }
            }
            MainMode.WATCHLIST -> {
                repository.getWatchlist(1)
                    .onSuccess { res -> handleMovieResult(res, 1, isWatchlist = true) }
                    .onFailure { error -> handleMovieError(error, 1) }
            }
            MainMode.GENRE -> {
                repository.getMoviesByGenre(currentState.currentGenre, 1)
                    .onSuccess { res -> handleMovieResult(res, 1) }
                    .onFailure { error -> handleMovieError(error, 1) }
            }
            MainMode.SEARCH -> {
                repository.searchMovies(currentState.currentQuery, null, 1)
                    .onSuccess { res -> handleMovieResult(res, 1) }
                    .onFailure { error -> handleMovieError(error, 1) }
            }
        }
    }

    private suspend fun checkAuthStatusInternal(): Boolean {
        if (!authManager.isLoggedIn()) {
            _events.emit(MainEvent.NavigateToLogin())
            return false
        }
        var isValid = true
        repository.checkAuthStatus().onSuccess { status ->
            if (!status.is_valid) {
                authManager.clearSession()
                _events.emit(MainEvent.NavigateToLogin(status.message ?: "Sitzung abgelaufen oder auf einem anderen Gerät angemeldet"))
                isValid = false
            }
        }.onFailure {
            if (!authManager.isLoggedIn()) {
                _events.emit(MainEvent.NavigateToLogin("Sitzung ungültig"))
                isValid = false
            }
        }
        return isValid
    }

    private fun updateInitProgress(step: Int, total: Int, desc: String, percent: Int) {
        _uiState.update {
            it.copy(
                initCurrentStep = step,
                initTotalSteps = total,
                initStepDescription = desc,
                initProgressPercent = percent.coerceIn(0, 100)
            )
        }
    }

    fun checkAuthStatus() {
        if (!authManager.isLoggedIn()) {
            viewModelScope.launch {
                _events.emit(MainEvent.NavigateToLogin())
            }
            return
        }

        viewModelScope.launch {
            repository.checkAuthStatus().onSuccess { status ->
                if (!status.is_valid) {
                    authManager.clearSession()
                    _events.emit(MainEvent.NavigateToLogin(status.message ?: "Sitzung abgelaufen oder auf einem anderen Gerät angemeldet"))
                }
            }.onFailure {
                if (!authManager.isLoggedIn()) {
                    _events.emit(MainEvent.NavigateToLogin("Sitzung ungültig"))
                }
            }
        }
    }

    fun onSourceChanged(source: StreamingSource) {
        if (_uiState.value.selectedSource == source) return
        sourceManager.setSelectedSource(source)
        _uiState.update { it.copy(selectedSource = source) }
        loadSidebar()
        loadContentBottom(isReset = true)
        viewModelScope.launch {
            _events.emit(MainEvent.ShowToast("Quelle gewechselt: ${source.displayName}"))
        }
    }

    fun refreshState() {
        val currentSource = sourceManager.getSelectedSource()
        if (_uiState.value.selectedSource != currentSource) {
            _uiState.update { it.copy(selectedSource = currentSource) }
            loadSidebar()
            loadContentBottom(isReset = true)
        }
    }

    private fun sanitizeGenres(genres: List<String>): List<String> {
        return genres.map { it.trim() }.filter { g ->
            g.isNotBlank() && g.length in 2..30 && !g.contains("\"") && !g.contains("[") && !g.contains("•")
        }
    }

    fun loadSidebar() {
        val currentSource = sourceManager.getSelectedSource()
        _uiState.update { it.copy(selectedSource = currentSource) }
        viewModelScope.launch {
            repository.getGenres()
                .onSuccess { genres ->
                    val items = mutableListOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien", "⚙️ Einstellungen").apply {
                        addAll(sanitizeGenres(genres))
                    }
                    _uiState.update { it.copy(sidebarItems = items) }
                }
                .onFailure {
                    val defaultItems = listOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien", "⚙️ Einstellungen")
                    _uiState.update { it.copy(sidebarItems = defaultItems) }
                }
        }
    }

    fun selectSidebarIndex(index: Int) {
        val items = _uiState.value.sidebarItems
        if (index < 0 || index >= items.size) return

        val selectedItem = items[index]

        if (selectedItem == "⚙️ Einstellungen") {
            viewModelScope.launch {
                _events.emit(MainEvent.NavigateToSettings)
            }
            return
        }

        lastNonSearchSidebarIndex = index
        _uiState.update { it.copy(selectedSidebarIndex = index) }

        when (selectedItem) {
            "📺 Weiterschauen" -> selectMode(MainMode.CONTINUE_WATCHING)
            "⭐ Meine Watchlist" -> selectMode(MainMode.WATCHLIST)
            "🎬 Alle Serien" -> selectMode(MainMode.GENRE, genre = "Alle")
            else -> selectMode(MainMode.GENRE, genre = selectedItem)
        }
    }

    fun selectMode(mode: MainMode, genre: String = "") {
        _uiState.update {
            it.copy(
                mode = mode,
                currentGenre = genre,
                movies = emptyList(),
                continueWatchingItems = emptyList(),
                firstLoadedPage = 1,
                lastLoadedPage = 1,
                isEndOfList = false,
                errorMessage = null
            )
        }
        loadContentBottom(isReset = true)
    }

    fun onSearchQueryChanged(query: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(350)
            if (query.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        mode = MainMode.SEARCH,
                        currentQuery = query,
                        selectedSidebarIndex = -1,
                        movies = emptyList(),
                        firstLoadedPage = 1,
                        lastLoadedPage = 1,
                        isEndOfList = false,
                        errorMessage = null
                    )
                }
                loadContentBottom(isReset = true)
            } else if (_uiState.value.mode == MainMode.SEARCH) {
                val restoreIndex = if (lastNonSearchSidebarIndex >= 0 && lastNonSearchSidebarIndex < _uiState.value.sidebarItems.size) {
                    lastNonSearchSidebarIndex
                } else {
                    0
                }
                selectSidebarIndex(restoreIndex)
            }
        }
    }

    fun loadContentBottom(isReset: Boolean = false) {
        if (_uiState.value.isLoading) return
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val pageToLoad = if (isReset) 1 else _uiState.value.lastLoadedPage + 1
            val currentState = _uiState.value

            when (currentState.mode) {
                MainMode.CONTINUE_WATCHING -> {
                    repository.getContinueWatching()
                        .onSuccess { res ->
                            _uiState.update {
                                it.copy(
                                    continueWatchingItems = res,
                                    isLoading = false,
                                    errorMessage = if (res.isEmpty()) "Keine Inhalte vorhanden" else null
                                )
                            }
                        }
                        .onFailure { error ->
                            handleMovieError(error, pageToLoad)
                        }
                }
                MainMode.WATCHLIST -> {
                    repository.getWatchlist(pageToLoad)
                        .onSuccess { res ->
                            handleMovieResult(res, pageToLoad, isWatchlist = true)
                        }
                        .onFailure { handleMovieError(it, pageToLoad) }
                }
                MainMode.GENRE -> {
                    repository.getMoviesByGenre(currentState.currentGenre, pageToLoad)
                        .onSuccess { res ->
                            handleMovieResult(res, pageToLoad)
                        }
                        .onFailure { handleMovieError(it, pageToLoad) }
                }
                MainMode.SEARCH -> {
                    repository.searchMovies(currentState.currentQuery, null, pageToLoad)
                        .onSuccess { res ->
                            handleMovieResult(res, pageToLoad)
                        }
                        .onFailure { handleMovieError(it, pageToLoad) }
                }
            }
        }
    }

    fun loadContentTop() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.firstLoadedPage <= 1) return
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val pageToLoad = currentState.firstLoadedPage - 1

            val result = when (currentState.mode) {
                MainMode.GENRE -> repository.getMoviesByGenre(currentState.currentGenre, pageToLoad)
                MainMode.SEARCH -> repository.searchMovies(currentState.currentQuery, null, pageToLoad)
                else -> Result.success(emptyList())
            }

            result.onSuccess { res ->
                if (res.isNotEmpty()) {
                    val currentList = currentState.movies
                    val newMovies = res.filter { new -> currentList.none { old -> old.id == new.id } }
                    val updatedMovies = newMovies + currentList

                    var newLastPage = currentState.lastLoadedPage
                    var finalMovies = updatedMovies
                    if (newLastPage - pageToLoad + 1 > maxPages) {
                        finalMovies = updatedMovies.dropLast(20)
                        newLastPage--
                    }

                    _uiState.update {
                        it.copy(
                            movies = finalMovies,
                            firstLoadedPage = pageToLoad,
                            lastLoadedPage = newLastPage,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun handleMovieResult(res: List<Movie>, pageToLoad: Int, isWatchlist: Boolean = false) {
        val isEndOfList = isWatchlist || res.size < 20
        _uiState.update { currentState ->
            if (res.isNotEmpty()) {
                if (pageToLoad == 1) {
                    currentState.copy(
                        movies = res,
                        firstLoadedPage = 1,
                        lastLoadedPage = 1,
                        isEndOfList = isEndOfList,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    val newMovies = res.filter { new -> currentState.movies.none { old -> old.id == new.id } }
                    val combined = currentState.movies + newMovies
                    var newFirstPage = currentState.firstLoadedPage
                    var finalMovies = combined

                    if (pageToLoad - newFirstPage + 1 > maxPages) {
                        finalMovies = combined.drop(20)
                        newFirstPage++
                    }

                    currentState.copy(
                        movies = finalMovies,
                        firstLoadedPage = newFirstPage,
                        lastLoadedPage = pageToLoad,
                        isEndOfList = isEndOfList,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } else if (pageToLoad == 1) {
                currentState.copy(
                    movies = emptyList(),
                    isLoading = false,
                    errorMessage = "Keine Inhalte gefunden"
                )
            } else {
                currentState.copy(
                    isEndOfList = true,
                    isLoading = false
                )
            }
        }
    }

    private fun handleMovieError(error: Throwable, pageToLoad: Int) {
        if (!authManager.isLoggedIn()) {
            viewModelScope.launch {
                _events.emit(MainEvent.NavigateToLogin("Sitzung ungültig"))
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (pageToLoad == 1) error.message ?: "Fehler beim Laden" else null
            )
        }
    }

    private fun syncAndPollStatus() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            repository.syncUserContent()
            var wasSyncing = false
            while (true) {
                repository.getSyncStatus().onSuccess { status ->
                    if (status.is_syncing) {
                        wasSyncing = true
                    } else if (wasSyncing) {
                        if (_uiState.value.mode == MainMode.CONTINUE_WATCHING || _uiState.value.mode == MainMode.WATCHLIST) {
                            loadContentBottom(isReset = true)
                            _events.emit(MainEvent.ShowToast("Watchlist aktualisiert"))
                        }
                        return@launch
                    } else {
                        return@launch
                    }
                }
                delay(2000)
            }
        }
    }

    class Factory(
        private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance),
        private val authManager: AuthManager = AuthManager.getInstance(),
        private val sourceManager: SourceManager = SourceManager.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, authManager, sourceManager) as T
        }
    }
}
