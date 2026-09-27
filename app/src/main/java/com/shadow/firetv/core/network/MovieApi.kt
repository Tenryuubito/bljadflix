package com.shadow.firetv.core.network

import com.shadow.firetv.core.model.AuthStatusResponse
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.CreateUserRequest
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.LoginRequest
import com.shadow.firetv.core.model.LoginResponse
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.model.ProgressRequest
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.model.SyncStatusResponse
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.UserDto
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.model.WatchlistStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MovieApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): UserDto

    @GET("auth/status")
    suspend fun checkAuthStatus(): AuthStatusResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("movies")
    suspend fun getMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("source") source: String? = null
    ): List<Movie>

    @GET("genres")
    suspend fun getGenres(
        @Query("source") source: String? = null
    ): List<String>

    @GET("movies/genre/{genre_name}")
    suspend fun getMoviesByGenre(
        @Path("genre_name") genreName: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("source") source: String? = null
    ): List<Movie>

    @GET("search")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("genre") genre: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("source") source: String? = null
    ): List<Movie>

    @GET("resolve-stream")
    suspend fun resolveStream(
        @Query("episode_id") episodeId: Int,
        @Query("source") source: String? = null
    ): StreamResponse

    @GET("movies/{content_id}/episodes")
    suspend fun getEpisodes(
        @Path("content_id") contentId: Int,
        @Query("source") source: String? = null
    ): Map<String, List<Episode>>

    @POST("movies/{content_id}/reindex")
    suspend fun reindexSeries(
        @Path("content_id") contentId: Int,
        @Query("source") source: String? = null
    ): TriggerIndexResponse

    @POST("progress")
    suspend fun updateProgress(@Body request: ProgressRequest)

    @GET("continue-watching")
    suspend fun getContinueWatching(): List<ContinueWatchingItem>

    @GET("watchlist")
    suspend fun getWatchlist(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<Movie>

    @GET("watchlist/{content_id}")
    suspend fun getWatchlistStatus(@Path("content_id") contentId: Int): WatchlistStatus

    @POST("watchlist")
    suspend fun updateWatchlist(@Body request: WatchlistRequest)

    @POST("sync-user-content")
    suspend fun syncUserContent()

    @GET("sync-status")
    suspend fun getSyncStatus(): SyncStatusResponse

    @POST("admin/trigger-index")
    suspend fun triggerIndex(
        @Query("source") source: String? = null
    ): TriggerIndexResponse
}
