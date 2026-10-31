package com.shadow.firetv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.ProgressRequest
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.network.RetrofitClient
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.stream.HosterLinkResolver
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

data class PlayerUiState(
    val contentId: Int = -1,
    val source: String? = null,
    val seriesTitle: String = "",
    val episodeId: Int = -1,
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val streamToken: Int = 0,
    val hosterSkip: Int = 0,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTime: Float = 0f,
    val duration: Float = 0f,
    val maxProgress: Float = 0f,
    val isCompletedMarked: Boolean = false,
    val isTransitioning: Boolean = false,
    val episodesList: List<Episode> = emptyList(),
    val countdownSeconds: Int? = null,
    val isCountdownCancelled: Boolean = false,
    val errorMessage: String? = null
) {
    val currentEpisode: Episode?
        get() = episodesList.firstOrNull { it.id == episodeId }

    val nextEpisode: Episode?
        get() = episodesList.indexOfFirst { it.id == episodeId }
            .takeIf { it != -1 }
            ?.let { episodesList.getOrNull(it + 1) }

    val previousEpisode: Episode?
        get() = episodesList.indexOfFirst { it.id == episodeId }
            .takeIf { it > 0 }
            ?.let { episodesList[it - 1] }

    val showNextEpisodeCard: Boolean
        get() = nextEpisode != null && !isCountdownCancelled && (countdownSeconds != null || isCompletedMarked)
}

sealed interface PlayerEvent {
    data class ShowToast(val message: String) : PlayerEvent
    data class SeekTo(val seconds: Float, val isResume: Boolean = false) : PlayerEvent
    object FinishActivity : PlayerEvent
}

class PlayerViewModel(
    private val repository: MovieRepository,
    private val hosterLinkResolver: HosterLinkResolver = HosterLinkResolver(),
    private val sourceManager: SourceManager = SourceManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>()
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var pollCount = 0
    private var resumeHandled = false
    private var resumeWaitPolls = 0
    private var countdownJob: Job? = null
    private var streamJob: Job? = null

    fun initPlayer(contentId: Int, episodeId: Int, source: String? = null, seriesTitle: String = "") {
        _uiState.update { it.copy(contentId = contentId, episodeId = episodeId, source = source, seriesTitle = seriesTitle) }
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
            repository.getEpisodes(contentId, _uiState.value.source).onSuccess { seasonsMap ->
                val sortedSeasons = seasonsMap.keys.sortedWith(Comparator { a, b ->
                    val numA = a.filter { it.isDigit() }.toIntOrNull()
                    val numB = b.filter { it.isDigit() }.toIntOrNull()
                    if (numA != null && numB != null) numA.compareTo(numB)
                    else a.compareTo(b)
                })
                val list = mutableListOf<Episode>()
                for (s in sortedSeasons) {
                    val seasonNumber = s.filter { it.isDigit() }.toIntOrNull() ?: 0
                    list.addAll((seasonsMap[s] ?: emptyList()).map { ep ->
                        if (ep.season_number > 0) ep else ep.copy(season_number = seasonNumber)
                    })
                }
                _uiState.update { it.copy(episodesList = list) }
            }
        }
    }

    fun loadStream(episodeId: Int, hosterSkip: Int = 0) {
        countdownJob?.cancel()
        streamJob?.cancel()
        pollCount = 0
        resumeHandled = false
        resumeWaitPolls = 0
        _uiState.update {
            it.copy(
                episodeId = episodeId,
                hosterSkip = hosterSkip,
                isLoading = true,
                isPlaying = false,
                currentTime = 0f,
                duration = 0f,
                maxProgress = 0f,
                isCompletedMarked = false,
                isTransitioning = false,
                countdownSeconds = null,
                isCountdownCancelled = false,
                errorMessage = null
            )
        }
        streamJob = viewModelScope.launch {
            val result = if (hosterSkip > 0) resolveOnDevice(episodeId, hosterSkip) else resolveStream(episodeId)
            result
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            streamUrl = res.stream_url,
                            streamHeaders = res.headers,
                            streamToken = it.streamToken + 1,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "Stream konnte nicht geladen werden"
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                }
        }
    }

    fun retry() {
        val state = _uiState.value
        if (state.episodeId == -1) return
        loadStream(state.episodeId, state.hosterSkip)
    }

    fun tryOtherHoster() {
        val state = _uiState.value
        if (state.episodeId == -1) return
        loadStream(state.episodeId, state.hosterSkip + 1)
    }

    private suspend fun resolveStream(episodeId: Int): Result<StreamResponse> {
        val sourceId = _uiState.value.source?.takeIf { it.isNotBlank() } ?: sourceManager.getSelectedSource().id
        val onDeviceFirst = sourceId.equals(StreamingSource.ANIWORLD.id, ignoreCase = true)

        val first = if (onDeviceFirst) resolveOnDevice(episodeId) else repository.resolveStream(episodeId, _uiState.value.source)
        if (first.isSuccess) return first

        val second = if (onDeviceFirst) repository.resolveStream(episodeId, _uiState.value.source) else resolveOnDevice(episodeId)
        return if (second.isSuccess) second else first
    }

    private suspend fun resolveOnDevice(episodeId: Int, skip: Int = 0): Result<StreamResponse> {
        val episodeUrl = findEpisodeUrl(episodeId)
            ?: return Result.failure(IllegalStateException("Episode nicht gefunden"))
        return hosterLinkResolver.resolve(episodeUrl, skip)
    }

    private suspend fun findEpisodeUrl(episodeId: Int): String? {
        _uiState.value.episodesList.firstOrNull { it.id == episodeId }?.episode_url?.let { return it }
        val contentId = _uiState.value.contentId
        if (contentId == -1) return null
        return repository.getEpisodes(contentId, _uiState.value.source).getOrNull()
            ?.values?.flatten()
            ?.firstOrNull { it.id == episodeId }
            ?.episode_url
    }

    fun updateProgressState(currentTime: Float, duration: Float, isPaused: Boolean, isEnded: Boolean) {
        val state = _uiState.value
        if (state.isTransitioning || state.isLoading) return

        val newMaxProgress = if (currentTime > state.maxProgress) currentTime else state.maxProgress

        _uiState.update {
            it.copy(
                currentTime = currentTime,
                duration = duration,
                maxProgress = newMaxProgress,
                isPlaying = !isPaused
            )
        }

        if (duration <= 0) return

        if (!resumeHandled) handleResume(currentTime, duration)

        val isCompleted = isEnded || (currentTime >= duration * COMPLETION_RATIO)
        if (isCompleted && !state.isCompletedMarked) {
            _uiState.update { it.copy(isCompletedMarked = true) }
            sendProgress(newMaxProgress, duration, isCompleted = true)
        }

        val remaining = duration - currentTime
        val current = _uiState.value
        if (current.nextEpisode != null) {
            val inEndZone = isEnded || remaining <= COUNTDOWN_SECONDS
            if (inEndZone && current.countdownSeconds == null && !current.isCountdownCancelled) {
                startCountdown()
            } else if (!inEndZone && current.countdownSeconds != null && remaining > COUNTDOWN_SECONDS + 2) {
                countdownJob?.cancel()
                _uiState.update { it.copy(countdownSeconds = null) }
            }
        } else if (isEnded || remaining <= 2f) {
            playNextEpisode()
            return
        }

        if (!isEnded && resumeHandled) {
            pollCount++
            if (pollCount >= 20 && state.contentId != -1) {
                pollCount = 0
                sendProgress(newMaxProgress, duration, _uiState.value.isCompletedMarked)
            }
        }
    }

    private fun handleResume(currentTime: Float, duration: Float) {
        val state = _uiState.value
        if (state.episodesList.isEmpty() && resumeWaitPolls++ < RESUME_WAIT_POLLS) return
        resumeHandled = true

        val episode = state.currentEpisode ?: return
        val saved = episode.progress_seconds
        if (episode.is_completed || saved < MIN_RESUME_SECONDS || saved >= duration * COMPLETION_RATIO) return
        if (currentTime >= saved - 5f) return

        val target = (saved - RESUME_REWIND_SECONDS).coerceAtLeast(0f)
        viewModelScope.launch { _events.emit(PlayerEvent.SeekTo(target, isResume = true)) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS.toInt() downTo 1) {
                _uiState.update { it.copy(countdownSeconds = seconds) }
                delay(1000)
            }
            playNextEpisode()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(countdownSeconds = null, isCountdownCancelled = true) }
    }

    fun playNextEpisode() {
        val state = _uiState.value
        if (state.isTransitioning) return
        countdownJob?.cancel()
        _uiState.update { it.copy(isTransitioning = true) }

        val dur = if (state.duration > 0) state.duration else state.maxProgress / COMPLETION_RATIO
        sendProgress(state.maxProgress, dur, isCompleted = true)

        val nextEp = state.nextEpisode
        if (nextEp != null) {
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

    fun skipToNextEpisode() {
        val target = _uiState.value.nextEpisode
        if (target == null) {
            viewModelScope.launch { _events.emit(PlayerEvent.ShowToast("Das ist die letzte Folge")) }
            return
        }
        switchToEpisode(target)
    }

    fun skipToPreviousEpisode() {
        val target = _uiState.value.previousEpisode
        if (target == null) {
            viewModelScope.launch { _events.emit(PlayerEvent.ShowToast("Das ist die erste Folge")) }
            return
        }
        switchToEpisode(target)
    }

    private fun switchToEpisode(target: Episode) {
        val state = _uiState.value
        if (state.isTransitioning) return
        if (resumeHandled && state.maxProgress > 0 && state.duration > 0) {
            sendProgress(state.maxProgress, state.duration, state.isCompletedMarked)
        }
        loadStream(target.id)
    }

    fun sendProgress(currentTime: Float, duration: Float, isCompleted: Boolean) {
        val state = _uiState.value
        if (state.episodeId == -1 || state.contentId == -1) return

        _uiState.update { s ->
            s.copy(episodesList = s.episodesList.map { ep ->
                if (ep.id == state.episodeId) {
                    ep.copy(progress_seconds = currentTime, duration_seconds = duration, is_completed = isCompleted)
                } else {
                    ep
                }
            })
        }

        viewModelScope.launch {
            val req = ProgressRequest(
                episode_id = state.episodeId,
                content_id = state.contentId,
                progress_seconds = currentTime,
                duration_seconds = duration,
                is_completed = isCompleted,
                source = state.source
            )
            repository.updateProgress(req)
        }
    }

    fun onPlayerDestroy() {
        val state = _uiState.value
        if (resumeHandled && state.contentId != -1 && !state.isTransitioning && state.maxProgress > 0 && state.duration > 0) {
            sendProgress(state.maxProgress, state.duration, state.isCompletedMarked)
        }
    }

    companion object {
        const val COUNTDOWN_SECONDS = 10f
        private const val COMPLETION_RATIO = 0.9f
        private const val MIN_RESUME_SECONDS = 10f
        private const val RESUME_REWIND_SECONDS = 3f
        private const val RESUME_WAIT_POLLS = 10
    }

    class Factory(private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance)) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(repository) as T
        }
    }
}
