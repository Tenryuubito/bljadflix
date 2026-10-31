package com.shadow.firetv

import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.network.MovieApi
import com.shadow.firetv.core.source.SourceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieRepositoryTest {

    private val api: MovieApi = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val sourceManager = SourceManager.createForTesting()
    private lateinit var repository: MovieRepositoryImpl

    @Before
    fun setup() {
        repository = MovieRepositoryImpl(
            api = api,
            sourceManager = sourceManager,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `getMovies returns success when api call succeeds`() = runTest {
        val movies = listOf(Movie(1, "Test Movie", "Desc", "url", "pageUrl", "Action"))
        coEvery { api.getMovies(1, 20, "aniworld") } returns movies

        val result = repository.getMovies(1, 20)

        assertTrue(result.isSuccess)
        assertEquals(movies, result.getOrNull())
    }

    @Test
    fun `getMovies returns failure when api throws exception`() = runTest {
        coEvery { api.getMovies(1, 20, "aniworld") } throws RuntimeException("Network Error")

        val result = repository.getMovies(1, 20)

        assertTrue(result.isFailure)
        assertEquals("Network Error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `searchMovies queries api with parameters`() = runTest {
        val movies = listOf(Movie(2, "Search Movie", "Desc", "url", "pageUrl", "Sci-Fi"))
        coEvery { api.searchMovies("Hero", null, 1, 20, "aniworld") } returns movies

        val result = repository.searchMovies("Hero", null, 1, 20)

        assertTrue(result.isSuccess)
        assertEquals(movies, result.getOrNull())
    }

    @Test
    fun `resolveStream returns stream response`() = runTest {
        val stream = StreamResponse("http://stream.url", mapOf("User-Agent" to "FireTV"))
        coEvery { api.resolveStream(101, "aniworld") } returns stream

        val result = repository.resolveStream(101)

        assertTrue(result.isSuccess)
        assertEquals(stream, result.getOrNull())
    }

    @Test
    fun `updateWatchlist sends request to api`() = runTest {
        val req = WatchlistRequest(content_id = 5, is_watchlisted = true)
        val sent = req.copy(source = "aniworld")
        coEvery { api.updateWatchlist(sent) } returns Unit

        val result = repository.updateWatchlist(req)

        assertTrue(result.isSuccess)
        coVerify { api.updateWatchlist(sent) }
    }

    @Test
    fun `getContinueWatching returns items from api`() = runTest {
        val items = listOf(
            ContinueWatchingItem(
                id = 10, title = "Series", description = "Desc", cover_url = "cover",
                genre = "Drama", episode_id = 101, season_number = 1, episode_number = 2,
                episode_title = "Ep 2", progress_seconds = 120f, duration_seconds = 1200f
            )
        )
        coEvery { api.getContinueWatching() } returns items

        val result = repository.getContinueWatching()

        assertTrue(result.isSuccess)
        assertEquals(items, result.getOrNull())
    }

    @Test
    fun `triggerIndex sends request to api`() = runTest {
        val response = TriggerIndexResponse("success", "aniworld")
        coEvery { api.triggerIndex("aniworld") } returns response

        val result = repository.triggerIndex("aniworld")

        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
        coVerify { api.triggerIndex("aniworld") }
    }

    @Test
    fun `reindexSeries sends request to api`() = runTest {
        val response = TriggerIndexResponse("success", "aniworld")
        coEvery { api.reindexSeries(42, "aniworld") } returns response

        val result = repository.reindexSeries(42)

        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
        coVerify { api.reindexSeries(42, "aniworld") }
    }
}
