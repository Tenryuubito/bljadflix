package com.shadow.firetv.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val contentId: Int = -1,
    val source: String? = null,
    val title: String = "",
    val description: String = "",
    val genre: String? = null,
    val coverUrl: String? = null,
    val isWatchlisted: Boolean = false,
    val isLoading: Boolean = false,
    val isReindexing: Boolean = false,
    val seasonsData: Map<String, List<Episode>> = emptyMap(),
    val seasonsList: List<String> = emptyList(),
    val selectedSeason: String = "",
    val currentEpisodes: List<Episode> = emptyList(),
    val continueEpisode: Episode? = null,
    val continueSeasonNumber: String = "?",
    val errorMessage: String? = null
)

sealed interface SeriesDetailEvent {
    data class ShowToast(val message: String) : SeriesDetailEvent
}

class SeriesDetailViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SeriesDetailEvent>()
    val events: SharedFlow<SeriesDetailEvent> = _events.asSharedFlow()

    fun initData(contentId: Int, title: String, description: String, genre: String?, coverUrl: String?, source: String? = null) {
        _uiState.update {
            it.copy(
                contentId = contentId,
                source = source,
                title = title,
                description = description,
                genre = genre,
                coverUrl = coverUrl
            )
        }
        if (contentId != -1) {
            loadWatchlistStatus(contentId)
            loadEpisodes(contentId)
        }
    }

    fun loadWatchlistStatus(contentId: Int) {
        viewModelScope.launch {
            repository.getWatchlistStatus(contentId, _uiState.value.source)
                .onSuccess { status ->
                    _uiState.update { it.copy(isWatchlisted = status.is_watchlisted) }
                }
        }
    }

    fun toggleWatchlist() {
        val currentState = _uiState.value
        val contentId = currentState.contentId
        if (contentId == -1) return

        val targetState = !currentState.isWatchlisted
        _uiState.update { it.copy(isWatchlisted = targetState) }

        viewModelScope.launch {
            repository.updateWatchlist(WatchlistRequest(contentId, targetState, currentState.source))
                .onSuccess {
                    _events.emit(SeriesDetailEvent.ShowToast(if (targetState) "Zur Watchlist hinzugefügt" else "Von der Watchlist entfernt"))
                }
                .onFailure {
                    _uiState.update { it.copy(isWatchlisted = !targetState) }
                    _events.emit(SeriesDetailEvent.ShowToast("Watchlist konnte nicht gespeichert werden"))
                }
        }
    }

    fun loadEpisodes(contentId: Int, isRetry: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getEpisodes(contentId, _uiState.value.source)
                .onSuccess { seasonsMap ->
                    if (seasonsMap.isEmpty() && !isRetry) {
                        reindexAndReloadEpisodes(contentId, seasonsMap)
                    } else {
                        processLoadedEpisodes(seasonsMap)
                    }
                }
                .onFailure { error ->
                    handleEpisodeError(error)
                }
        }
    }

    fun refreshEpisodes() {
        val contentId = _uiState.value.contentId
        if (contentId == -1 || _uiState.value.isLoading) return
        viewModelScope.launch {
            repository.getEpisodes(contentId, _uiState.value.source)
                .onSuccess { seasonsMap ->
                    if (seasonsMap.isNotEmpty()) processLoadedEpisodes(seasonsMap, keepSelectedSeason = true)
                }
        }
    }

    private suspend fun reindexAndReloadEpisodes(contentId: Int, fallbackMap: Map<String, List<Episode>>) {
        try {
            repository.reindexSeries(contentId, _uiState.value.source)
        } catch (_: Throwable) {
        }
        processLoadedEpisodes(awaitEpisodeChange(contentId, 0) ?: fallbackMap)
    }

    private suspend fun awaitEpisodeChange(contentId: Int, previousCount: Int): Map<String, List<Episode>>? {
        var latest: Map<String, List<Episode>>? = null
        repeat(REINDEX_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(REINDEX_POLL_INTERVAL_MS)
            repository.getEpisodes(contentId, _uiState.value.source).onSuccess { map ->
                latest = map
                if (episodeCount(map) != previousCount) return map
            }
        }
        return latest
    }

    private fun episodeCount(seasonsMap: Map<String, List<Episode>>): Int = seasonsMap.values.sumOf { it.size }

    private fun processLoadedEpisodes(seasonsMap: Map<String, List<Episode>>, keepSelectedSeason: Boolean = false) {
        val seasonsList = seasonsMap.keys.sortedWith(Comparator { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull()
            val numB = b.filter { it.isDigit() }.toIntOrNull()
            if (numA != null && numB != null) numA.compareTo(numB)
            else a.compareTo(b)
        })
        val (continueEp, continueSeason) = calculateContinueWatchingEpisode(seasonsList, seasonsMap)

        val previousSeason = _uiState.value.selectedSeason
        val selectedSeason = when {
            keepSelectedSeason && previousSeason in seasonsList -> previousSeason
            continueSeason in seasonsList -> continueSeason
            else -> seasonsList.firstOrNull() ?: ""
        }
        val currentEpisodes = seasonsMap[selectedSeason] ?: emptyList()

        _uiState.update {
            it.copy(
                isLoading = false,
                seasonsData = seasonsMap,
                seasonsList = seasonsList,
                selectedSeason = selectedSeason,
                currentEpisodes = currentEpisodes,
                continueEpisode = continueEp,
                continueSeasonNumber = continueSeason,
                errorMessage = if (seasonsMap.isEmpty()) "Keine Episoden vorhanden" else null
            )
        }
    }

    private fun handleEpisodeError(error: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = error.message ?: "Fehler beim Laden der Episoden"
            )
        }
    }

    fun selectSeason(season: String) {
        val seasonsMap = _uiState.value.seasonsData
        val episodes = seasonsMap[season] ?: emptyList()
        _uiState.update {
            it.copy(
                selectedSeason = season,
                currentEpisodes = episodes
            )
        }
    }

    fun reindexSeries() {
        val state = _uiState.value
        val contentId = state.contentId
        if (contentId == -1 || state.isReindexing || state.isLoading) return
        val previousCount = episodeCount(state.seasonsData)
        _uiState.update { it.copy(isReindexing = true) }
        viewModelScope.launch {
            val started = repository.reindexSeries(contentId, state.source)
            if (started.isFailure) {
                _uiState.update { it.copy(isReindexing = false) }
                _events.emit(SeriesDetailEvent.ShowToast(started.exceptionOrNull()?.message ?: "Aktualisierung fehlgeschlagen"))
                return@launch
            }
            val updated = awaitEpisodeChange(contentId, previousCount)
            if (updated != null && updated.isNotEmpty()) processLoadedEpisodes(updated, keepSelectedSeason = true)
            _uiState.update { it.copy(isReindexing = false) }
            val added = (updated?.let { episodeCount(it) } ?: previousCount) - previousCount
            val message = when {
                added == 1 -> "1 neue Folge gefunden"
                added > 1 -> "$added neue Folgen gefunden"
                else -> "Keine neuen Folgen gefunden"
            }
            _events.emit(SeriesDetailEvent.ShowToast(message))
        }
    }

    private fun calculateContinueWatchingEpisode(
        seasonsList: List<String>,
        seasonsMap: Map<String, List<Episode>>
    ): Pair<Episode?, String> {
        val allEpisodes = seasonsList.flatMap { seasonsMap[it] ?: emptyList() }
        if (allEpisodes.isEmpty()) return Pair(null, "?")

        val mostRecentlyWatched = allEpisodes.filter { it.last_watched_at > 0 }.maxByOrNull { it.last_watched_at }

        val targetEp: Episode = if (mostRecentlyWatched != null) {
            if (mostRecentlyWatched.is_completed) {
                val currentIndex = allEpisodes.indexOf(mostRecentlyWatched)
                if (currentIndex != -1 && currentIndex + 1 < allEpisodes.size) {
                    allEpisodes[currentIndex + 1]
                } else {
                    allEpisodes.first()
                }
            } else {
                mostRecentlyWatched
            }
        } else {
            allEpisodes.firstOrNull { !it.is_completed } ?: allEpisodes.first()
        }

        val season = seasonsList.find { seasonsMap[it]?.contains(targetEp) == true } ?: "?"

        return Pair(targetEp, season)
    }

    companion object {
        private const val REINDEX_POLL_ATTEMPTS = 6
        private const val REINDEX_POLL_INTERVAL_MS = 2500L
    }

    class Factory(private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SeriesDetailViewModel(repository) as T
        }
    }
}
