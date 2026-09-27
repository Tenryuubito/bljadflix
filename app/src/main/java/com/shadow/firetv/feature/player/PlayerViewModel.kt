package com.shadow.firetv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.ProgressRequest
import com.shadow.firetv.core.network.RetrofitClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val contentId: Int = -1,
    val episodeId: Int = -1,
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTime: Float = 0f,
    val duration: Float = 0f,
    val maxProgress: Float = 0f,
    val isCompletedMarked: Boolean = false,
    val isTransitioning: Boolean = false,
    val episodesList: List<Episode> = emptyList(),
    val errorMessage: String? = null
)

sealed interface PlayerEvent {
    data class ShowToast(val message: String) : PlayerEvent
    object FinishActivity : PlayerEvent
}

class PlayerViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>()
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var pollCount = 0

    fun initPlayer(contentId: Int, episodeId: Int) {
        _uiState.update { it.copy(contentId = contentId, episodeId = episodeId) }
        if (contentId != -1) {
            loadEpisodesList(contentId)
        }
        if (episodeId != -1) {
            loadStream(episodeId)
        } else {
            viewModelScope.launch { _events.emit(PlayerEvent.FinishActivity) }
        }
    }

    fun loadEpisodesList(contentId: Int) {
        viewModelScope.launch {
            repository.getEpisodes(contentId).onSuccess { seasonsMap ->
                val sortedSeasons = seasonsMap.keys.sortedWith(Comparator { a, b ->
                    val numA = a.filter { it.isDigit() }.toIntOrNull()
                    val numB = b.filter { it.isDigit() }.toIntOrNull()
                    if (numA != null && numB != null) numA.compareTo(numB)
                    else a.compareTo(b)
                })
                val list = mutableListOf<Episode>()
                for (s in sortedSeasons) {
                    list.addAll(seasonsMap[s] ?: emptyList())
                }
                _uiState.update { it.copy(episodesList = list) }
            }
        }
    }

    fun loadStream(episodeId: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    episodeId = episodeId,
                    isLoading = true,
                    maxProgress = 0f,
                    isCompletedMarked = false,
                    isTransitioning = false,
                    errorMessage = null
                )
            }
            pollCount = 0

            repository.resolveStream(episodeId)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            streamUrl = res.stream_url,
                            streamHeaders = res.headers,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Stream konnte nicht geladen werden"
                        )
                    }
                    _events.emit(PlayerEvent.FinishActivity)
                }
        }
    }

    fun updateProgressState(currentTime: Float, duration: Float, isPaused: Boolean, isEnded: Boolean) {
        val state = _uiState.value
        if (state.isTransitioning) return

        val newMaxProgress = if (currentTime > state.maxProgress) currentTime else state.maxProgress
        val newIsPlaying = !isPaused

        _uiState.update {
            it.copy(
                currentTime = currentTime,
                duration = duration,
                maxProgress = newMaxProgress,
                isPlaying = newIsPlaying
            )
        }

        if (duration > 0) {
            val isCompleted = isEnded || (currentTime >= duration * 0.90f)

            if (isCompleted && !state.isCompletedMarked) {
                _uiState.update { it.copy(isCompletedMarked = true) }
                sendProgress(newMaxProgress, duration, isCompleted = true)
            }

            if (isEnded || (duration > 0 && currentTime >= duration - 2f)) {
                if (!state.isTransitioning) {
                    playNextEpisode()
                }
            } else {
                pollCount++
                if (pollCount >= 20 && state.contentId != -1) {
                    pollCount = 0
                    sendProgress(newMaxProgress, duration, state.isCompletedMarked)
                }
            }
        }
    }

    fun playNextEpisode() {
        val state = _uiState.value
        if (state.isTransitioning) return
        _uiState.update { it.copy(isTransitioning = true) }

        // Save progress for current episode
        val dur = if (state.duration > 0) state.duration else state.maxProgress / 0.9f
        sendProgress(state.maxProgress, dur, isCompleted = true)

        val currentIndex = state.episodesList.indexOfFirst { it.id == state.episodeId }
        if (currentIndex != -1 && currentIndex + 1 < state.episodesList.size) {
            val nextEp = state.episodesList[currentIndex + 1]
            viewModelScope.launch {
                _events.emit(PlayerEvent.ShowToast("Starte nächste Folge..."))
            }
            loadStream(nextEp.id)
        } else {
            viewModelScope.launch {
                _events.emit(PlayerEvent.FinishActivity)
            }
        }
    }

    fun sendProgress(currentTime: Float, duration: Float, isCompleted: Boolean) {
        val state = _uiState.value
        if (state.episodeId == -1 || state.contentId == -1) return

        viewModelScope.launch {
            val req = ProgressRequest(
                episode_id = state.episodeId,
                content_id = state.contentId,
                progress_seconds = currentTime,
                duration_seconds = duration,
                is_completed = isCompleted
            )
            repository.updateProgress(req)
        }
    }

    fun onPlayerDestroy() {
        val state = _uiState.value
        if (state.contentId != -1 && !state.isTransitioning && state.maxProgress > 0 && state.duration > 0) {
            sendProgress(state.maxProgress, state.duration, state.isCompletedMarked)
        }
    }

    class Factory(private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(repository) as T
        }
    }
}
