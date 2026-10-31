package com.shadow.firetv.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val seasonsData: Map<String, List<Episode>> = emptyMap(),
    val seasonsList: List<String> = emptyList(),
    val selectedSeason: String = "",
    val currentEpisodes: List<Episode> = emptyList(),
    val continueEpisode: Episode? = null,
    val continueSeasonNumber: String = "?",
    val errorMessage: String? = null
)

class SeriesDetailViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

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
                .onFailure {
                    _uiState.update { it.copy(isWatchlisted = !targetState) }
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

    private suspend fun reindexAndReloadEpisodes(contentId: Int, fallbackMap: Map<String, List<Episode>>) {
        try {
            repository.reindexSeries(contentId, _uiState.value.source)
        } catch (_: Throwable) {
        }
        repository.getEpisodes(contentId, _uiState.value.source)
            .onSuccess { retryMap -> processLoadedEpisodes(retryMap) }
            .onFailure { processLoadedEpisodes(fallbackMap) }
    }

    private fun processLoadedEpisodes(seasonsMap: Map<String, List<Episode>>) {
        val seasonsList = seasonsMap.keys.sortedWith(Comparator { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull()
            val numB = b.filter { it.isDigit() }.toIntOrNull()
            if (numA != null && numB != null) numA.compareTo(numB)
            else a.compareTo(b)
        })
        val selectedSeason = seasonsList.firstOrNull() ?: ""
        val currentEpisodes = seasonsMap[selectedSeason] ?: emptyList()

        val (continueEp, continueSeason) = calculateContinueWatchingEpisode(seasonsList, seasonsMap)

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
        val contentId = _uiState.value.contentId
        if (contentId == -1) return
        viewModelScope.launch {
            repository.reindexSeries(contentId, _uiState.value.source)
            repository.getEpisodes(contentId, _uiState.value.source)
                .onSuccess { seasonsMap -> processLoadedEpisodes(seasonsMap) }
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

    class Factory(private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SeriesDetailViewModel(repository) as T
        }
    }
}
