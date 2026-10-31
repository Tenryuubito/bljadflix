package com.shadow.firetv.core.data

import com.shadow.firetv.core.model.AppVersionResponse
import com.shadow.firetv.core.model.AuthStatusResponse
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.LoginRequest
import com.shadow.firetv.core.model.LoginResponse
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.model.ProgressRequest
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.model.SyncStatusResponse
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.model.WatchlistStatus
import com.shadow.firetv.core.network.MovieApi
import com.shadow.firetv.core.source.SourceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

class MovieRepositoryImpl(
    private val api: MovieApi,
    private val sourceManager: SourceManager = SourceManager.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MovieRepository {

    private fun sourceId(source: String? = null): String = source ?: sourceManager.getSelectedSource().id

    private suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(call())
        } catch (e: HttpException) {
            val errorMsg = try {
                val json = e.response()?.errorBody()?.string()
                if (!json.isNullOrEmpty()) {
                    val jsonObj = JSONObject(json)
                    jsonObj.optString("detail", e.message())
                } else {
                    e.message()
                }
            } catch (_: Exception) {
                e.message()
            }
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(
        username: String?,
        password: String?,
        code: String?,
        deviceId: String,
        deviceName: String
    ): Result<LoginResponse> =
        safeApiCall {
            api.login(
                LoginRequest(
                    username = username,
                    password = password,
                    code = code,
                    device_id = deviceId,
                    device_name = deviceName
                )
            )
        }

    override suspend fun checkAuthStatus(): Result<AuthStatusResponse> =
        safeApiCall { api.checkAuthStatus() }

    override suspend fun logout(): Result<Unit> =
        safeApiCall { api.logout() }

    override suspend fun getMovies(page: Int, limit: Int): Result<List<Movie>> =
        safeApiCall { api.getMovies(page, limit, sourceId()) }

    override suspend fun getGenres(): Result<List<String>> =
        safeApiCall { api.getGenres(sourceId()) }

    override suspend fun getMoviesByGenre(genreName: String, page: Int, limit: Int): Result<List<Movie>> =
        safeApiCall { api.getMoviesByGenre(genreName, page, limit, sourceId()) }

    override suspend fun searchMovies(query: String, genre: String?, page: Int, limit: Int): Result<List<Movie>> =
        safeApiCall { api.searchMovies(query, genre, page, limit, sourceId()) }

    override suspend fun resolveStream(episodeId: Int, source: String?): Result<StreamResponse> =
        safeApiCall { api.resolveStream(episodeId, sourceId(source)) }

    override suspend fun getEpisodes(contentId: Int, source: String?): Result<Map<String, List<Episode>>> =
        safeApiCall { api.getEpisodes(contentId, sourceId(source)) }

    override suspend fun reindexSeries(contentId: Int, source: String?): Result<TriggerIndexResponse> =
        safeApiCall { api.reindexSeries(contentId, sourceId(source)) }

    override suspend fun updateProgress(request: ProgressRequest): Result<Unit> =
        safeApiCall { api.updateProgress(request.copy(source = sourceId(request.source))) }

    override suspend fun getContinueWatching(): Result<List<ContinueWatchingItem>> =
        safeApiCall { api.getContinueWatching() }

    override suspend fun getWatchlist(page: Int, limit: Int): Result<List<Movie>> =
        safeApiCall { api.getWatchlist(page, limit) }

    override suspend fun getWatchlistStatus(contentId: Int, source: String?): Result<WatchlistStatus> =
        safeApiCall { api.getWatchlistStatus(contentId, sourceId(source)) }

    override suspend fun updateWatchlist(request: WatchlistRequest): Result<Unit> =
        safeApiCall { api.updateWatchlist(request.copy(source = sourceId(request.source))) }

    override suspend fun syncUserContent(): Result<Unit> =
        safeApiCall { api.syncUserContent(sourceId()) }

    override suspend fun getSyncStatus(): Result<SyncStatusResponse> =
        safeApiCall { api.getSyncStatus() }

    override suspend fun triggerIndex(source: String?): Result<TriggerIndexResponse> =
        safeApiCall { api.triggerIndex(source) }

    override suspend fun getAppVersion(): Result<AppVersionResponse> =
        safeApiCall { api.getAppVersion() }
}
