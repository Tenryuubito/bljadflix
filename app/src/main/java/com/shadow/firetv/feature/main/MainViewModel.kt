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
    val canRetry: Boolean = false,
    val lastLoadedPage: Int = 1,
    val isInitializing: Boolean = true,
    val initCurrentStep: Int = 0,
    val initTotalSteps: Int = 1,
    val initStepDescription: String = "Starte Anwendung...",
    val initProgressPercent: Int = 0
)

private data class GenreCache(
    val movies: List<Movie>,
    val lastLoadedPage: Int,
    val isEndOfList: Boolean
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
    private val maxSyncPolls = 150
    private var lastNonSearchSidebarIndex = 0
    private val genreCache = mutableMapOf<String, GenreCache>()

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

            updateInitProgress(1, 5, "Prüfe Authentifizierung...", 20)
            if (!checkAuthStatusInternal()) {
                _uiState.update { it.copy(isInitializing = false) }
                return@launch
            }

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

            val sidebarItems = BASE_SIDEBAR_ITEMS + genresList
            _uiState.update { it.copy(sidebarItems = sidebarItems, selectedSource = currentSource) }

            updateInitProgress(3, 5, "Synchronisiere Benutzerinhalte vom Server...", 60)
            repository.syncUserContent()

            updateInitProgress(4, 5, "Lade Startinhalte...", 85)
            fetchInitialContent()

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
        genreCache.clear()
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
            genreCache.clear()
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
                    _uiState.update { it.copy(sidebarItems = BASE_SIDEBAR_ITEMS + sanitizeGenres(genres)) }
                }
                .onFailure {
                    _uiState.update { it.copy(sidebarItems = BASE_SIDEBAR_ITEMS) }
                }
        }
    }

    fun selectSidebarIndex(index: Int) {
        val items = _uiState.value.sidebarItems
        if (index < 0 || index >= items.size) return

        val selectedItem = items[index]

        lastNonSearchSidebarIndex = index
        _uiState.update { it.copy(selectedSidebarIndex = index) }

        when (selectedItem) {
            "📺 Weiterschauen" -> selectMode(MainMode.CONTINUE_WATCHING)
            "⭐ Meine Watchlist" -> selectMode(MainMode.WATCHLIST)
            "🎬 Alle Serien" -> selectMode(MainMode.GENRE, genre = "Alle")
            else -> selectMode(MainMode.GENRE, genre = selectedItem)
        }
    }

    fun openSettings() {
        viewModelScope.launch {
            _events.emit(MainEvent.NavigateToSettings)
        }
    }

    fun selectMode(mode: MainMode, genre: String = "") {
        val cached = if (mode == MainMode.GENRE) genreCache[genre] else null
        if (cached != null) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    mode = mode,
                    currentGenre = genre,
                    movies = cached.movies,
                    continueWatchingItems = emptyList(),
                    lastLoadedPage = cached.lastLoadedPage,
                    isEndOfList = cached.isEndOfList,
                    isLoading = false,
                    errorMessage = null,
                    canRetry = false
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                mode = mode,
                currentGenre = genre,
                movies = emptyList(),
                continueWatchingItems = emptyList(),
                lastLoadedPage = 1,
                isEndOfList = false,
                errorMessage = null,
                canRetry = false
            )
        }
        loadContentBottom(isReset = true, delayMillis = SELECTION_DEBOUNCE_MS)
    }

    fun retry() {
        if (_uiState.value.sidebarItems.size <= BASE_SIDEBAR_ITEMS.size) loadSidebar()
        loadContentBottom(isReset = true)
    }

    fun hideContinueWatching(item: ContinueWatchingItem) {
        val previous = _uiState.value.continueWatchingItems
        val remaining = previous.filterNot { it.id == item.id && it.source == item.source }
        _uiState.update {
            it.copy(
                continueWatchingItems = remaining,
                errorMessage = if (remaining.isEmpty() && it.mode == MainMode.CONTINUE_WATCHING) "Keine Inhalte vorhanden" else it.errorMessage
            )
        }
        viewModelScope.launch {
            repository.hideContinueWatching(item.id, item.source)
                .onSuccess {
                    _events.emit(MainEvent.ShowToast("„${item.title}“ aus Weiterschauen entfernt"))
                }
                .onFailure {
                    if (_uiState.value.mode == MainMode.CONTINUE_WATCHING) {
                        _uiState.update { it.copy(continueWatchingItems = previous, errorMessage = null) }
                    }
                    _events.emit(MainEvent.ShowToast("Eintrag konnte nicht entfernt werden"))
                }
        }
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

    fun loadContentBottom(isReset: Boolean = false, delayMillis: Long = 0) {
        val state = _uiState.value
        if (!isReset && (state.isLoading || state.isEndOfList)) return
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, canRetry = false) }
            if (delayMillis > 0) delay(delayMillis)
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

    private fun handleMovieResult(res: List<Movie>, pageToLoad: Int, isWatchlist: Boolean = false) {
        val isEndOfList = isWatchlist || res.size < 20
        _uiState.update { currentState ->
            if (res.isNotEmpty()) {
                if (pageToLoad == 1) {
                    currentState.copy(
                        movies = res,
                        lastLoadedPage = 1,
                        isEndOfList = isEndOfList,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    val knownIds = currentState.movies.mapTo(HashSet()) { it.id }
                    currentState.copy(
                        movies = currentState.movies + res.filter { it.id !in knownIds },
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
        val updated = _uiState.value
        if (updated.mode == MainMode.GENRE && updated.movies.isNotEmpty()) {
            genreCache[updated.currentGenre] = GenreCache(updated.movies, updated.lastLoadedPage, updated.isEndOfList)
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
                errorMessage = if (pageToLoad == 1) error.message ?: "Fehler beim Laden" else null,
                canRetry = pageToLoad == 1
            )
        }
        if (pageToLoad > 1) {
            viewModelScope.launch {
                _events.emit(MainEvent.ShowToast("Weitere Inhalte konnten nicht geladen werden"))
            }
        }
    }

    private fun syncAndPollStatus() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var wasSyncing = false
            repeat(maxSyncPolls) {
                val result = repository.getSyncStatus()
                if (result.isFailure) return@launch
                result.onSuccess { status ->
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

    companion object {
        private const val SELECTION_DEBOUNCE_MS = 200L
        private val BASE_SIDEBAR_ITEMS = listOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien")
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
