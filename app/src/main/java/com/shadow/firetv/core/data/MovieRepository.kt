package com.shadow.firetv.core.data

import com.shadow.firetv.core.model.AuthStatusResponse
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.LoginResponse
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.model.ProgressRequest
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.model.SyncStatusResponse
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.UserDto
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.model.WatchlistStatus

interface MovieRepository {
    suspend fun login(
        username: String? = null,
        password: String? = null,
        code: String? = null,
        deviceId: String,
        deviceName: String = "Fire TV Stick"
    ): Result<LoginResponse>
    suspend fun createUser(username: String, loginCode: String? = null): Result<UserDto>
    suspend fun checkAuthStatus(): Result<AuthStatusResponse>
    suspend fun logout(): Result<Unit>

    suspend fun getMovies(page: Int = 1, limit: Int = 20): Result<List<Movie>>
    suspend fun getGenres(): Result<List<String>>
    suspend fun getMoviesByGenre(genreName: String, page: Int = 1, limit: Int = 20): Result<List<Movie>>
    suspend fun searchMovies(query: String, genre: String? = null, page: Int = 1, limit: Int = 20): Result<List<Movie>>
    suspend fun resolveStream(episodeId: Int): Result<StreamResponse>
    suspend fun getEpisodes(contentId: Int): Result<Map<String, List<Episode>>>
    suspend fun reindexSeries(contentId: Int): Result<TriggerIndexResponse>
    suspend fun updateProgress(request: ProgressRequest): Result<Unit>
    suspend fun getContinueWatching(): Result<List<ContinueWatchingItem>>
    suspend fun getWatchlist(page: Int = 1, limit: Int = 20): Result<List<Movie>>
    suspend fun getWatchlistStatus(contentId: Int): Result<WatchlistStatus>
    suspend fun updateWatchlist(request: WatchlistRequest): Result<Unit>
    suspend fun syncUserContent(): Result<Unit>
    suspend fun getSyncStatus(): Result<SyncStatusResponse>
    suspend fun triggerIndex(source: String? = null): Result<TriggerIndexResponse>
}
