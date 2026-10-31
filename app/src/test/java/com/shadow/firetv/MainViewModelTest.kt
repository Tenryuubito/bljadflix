package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.AuthStatusResponse
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.model.SyncStatusResponse
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.feature.main.MainEvent
import com.shadow.firetv.feature.main.MainMode
import com.shadow.firetv.feature.main.MainViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        every { authManager.isLoggedIn() } returns true
        coEvery { repository.checkAuthStatus() } returns Result.success(AuthStatusResponse(is_valid = true))
        coEvery { repository.getGenres() } returns Result.success(listOf("Action", "Comedy"))
        coEvery { repository.getSyncStatus() } returns Result.success(SyncStatusResponse(is_syncing = false))
        coEvery { repository.getContinueWatching() } returns Result.success(emptyList())

        viewModel = MainViewModel(repository, authManager = authManager)
    }

    @Test
    fun `initialization progress updates steps and completes`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isInitializing)
        assertEquals(100, state.initProgressPercent)
        val expectedSidebar = listOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien", "Action", "Comedy")
        assertEquals(expectedSidebar, state.sidebarItems)
        assertEquals(MainMode.CONTINUE_WATCHING, state.mode)
    }

    @Test
    fun `selecting genre sidebar item updates mode and loads genre movies`() = runTest {
        val movies = listOf(Movie(1, "Action Movie", "Desc", "url", "pageUrl", "Action"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } returns Result.success(movies)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MainMode.GENRE, state.mode)
        assertEquals("Action", state.currentGenre)
        assertEquals(movies, state.movies)
        assertFalse(state.isLoading)
    }

    @Test
    fun `switching genres quickly shows content of the last selected genre`() = runTest {
        val actionMovies = listOf(Movie(1, "Action Movie", "Desc", "url", "pageUrl", "Action"))
        val comedyMovies = listOf(Movie(2, "Comedy Movie", "Desc", "url", "pageUrl", "Comedy"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } coAnswers {
            delay(1000)
            Result.success(actionMovies)
        }
        coEvery { repository.getMoviesByGenre("Comedy", 1, 20) } returns Result.success(comedyMovies)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)
        testScheduler.advanceTimeBy(300)
        viewModel.selectSidebarIndex(4)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Comedy", state.currentGenre)
        assertEquals(comedyMovies, state.movies)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loading more content stops at end of list`() = runTest {
        val movies = listOf(Movie(1, "Action Movie", "Desc", "url", "pageUrl", "Action"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } returns Result.success(movies)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)
        advanceUntilIdle()
        viewModel.loadContentBottom()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEndOfList)
        coVerify(exactly = 0) { repository.getMoviesByGenre("Action", 2, 20) }
    }

    @Test
    fun `opening settings emits NavigateToSettings event`() = runTest {
        advanceUntilIdle()
        viewModel.events.test {
            viewModel.openSettings()
            assertEquals(MainEvent.NavigateToSettings, awaitItem())
        }
    }

    @Test
    fun `selecting continue watching loads continue watching items`() = runTest {
        val continueItems = listOf(
            ContinueWatchingItem(
                id = 1, title = "Show", description = "Desc", cover_url = "cover", genre = "Action",
                episode_id = 10, season_number = 1, episode_number = 1, episode_title = "Pilot",
                progress_seconds = 100f, duration_seconds = 1000f
            )
        )
        coEvery { repository.getContinueWatching() } returns Result.success(continueItems)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MainMode.CONTINUE_WATCHING, state.mode)
        assertEquals(continueItems, state.continueWatchingItems)
        assertFalse(state.isLoading)
    }

    @Test
    fun `refreshing continue watching shows the newly watched episode`() = runTest {
        val before = ContinueWatchingItem(
            id = 1, title = "Show", description = "Desc", cover_url = "cover", genre = "Action",
            episode_id = 10, season_number = 1, episode_number = 1, episode_title = "Pilot",
            progress_seconds = 100f, duration_seconds = 1000f
        )
        val after = before.copy(episode_id = 11, episode_number = 2, episode_title = "Zwei", progress_seconds = 0f)
        coEvery { repository.getContinueWatching() } returns Result.success(listOf(before))

        advanceUntilIdle()
        viewModel.selectSidebarIndex(0)
        advanceUntilIdle()
        assertEquals(listOf(before), viewModel.uiState.value.continueWatchingItems)

        coEvery { repository.getContinueWatching() } returns Result.success(listOf(after))
        viewModel.refreshContinueWatching()
        advanceUntilIdle()

        assertEquals(listOf(after), viewModel.uiState.value.continueWatchingItems)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `watchlist loads further pages and keeps equal ids from other sources`() = runTest {
        val firstPage = (1..20).map { Movie(it, "Serie $it", "", "", "", source = "sto") }
        val secondPage = listOf(Movie(1, "Andere Serie", "", "", "", source = "aniworld"))
        coEvery { repository.getWatchlist(1, any()) } returns Result.success(firstPage)
        coEvery { repository.getWatchlist(2, any()) } returns Result.success(secondPage)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(1)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEndOfList)

        viewModel.loadContentBottom()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(21, state.movies.size)
        assertEquals("aniworld", state.movies.last().source)
        assertTrue(state.isEndOfList)
    }

    @Test
    fun `search query updates search mode and returns matching movies`() = runTest {
        val searchResults = listOf(Movie(5, "Spider-Man", "Desc", "url", "pageUrl", "Action"))
        coEvery { repository.searchMovies("Spider", null, 1, 20) } returns Result.success(searchResults)

        advanceUntilIdle()
        viewModel.onSearchQueryChanged("Spider")
        testScheduler.advanceTimeBy(600)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MainMode.SEARCH, state.mode)
        assertEquals("Spider", state.currentQuery)
        assertEquals(searchResults, state.movies)
        assertFalse(state.isLoading)
    }

    @Test
    fun `changing source updates state and reloads sidebar and content`() = runTest {
        val sourceManager = SourceManager.createForTesting()
        viewModel = MainViewModel(repository, authManager = authManager, sourceManager = sourceManager)

        val newGenres = listOf("Anime", "Fantasy")
        coEvery { repository.getGenres() } returns Result.success(newGenres)

        advanceUntilIdle()
        viewModel.onSourceChanged(StreamingSource.FILMO)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamingSource.FILMO, state.selectedSource)
        assertEquals(StreamingSource.FILMO, sourceManager.getSelectedSource())
    }

    @Test
    fun `hiding continue watching item removes it and calls repository`() = runTest {
        val first = ContinueWatchingItem(
            id = 1, title = "Show", description = null, cover_url = null, genre = null,
            episode_id = 10, season_number = 1, episode_number = 2, episode_title = null,
            progress_seconds = 100f, duration_seconds = 1000f, source = "sto"
        )
        val second = first.copy(id = 2, title = "Other")
        coEvery { repository.getContinueWatching() } returns Result.success(listOf(first, second))
        coEvery { repository.hideContinueWatching(1, "sto") } returns Result.success(Unit)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(0)
        advanceUntilIdle()
        viewModel.events.test {
            viewModel.hideContinueWatching(first)
            assertEquals(MainEvent.ShowToast("„Show“ aus Weiterschauen entfernt"), awaitItem())
        }

        assertEquals(listOf(second), viewModel.uiState.value.continueWatchingItems)
        coVerify { repository.hideContinueWatching(1, "sto") }
    }

    @Test
    fun `failed hide restores continue watching item`() = runTest {
        val item = ContinueWatchingItem(
            id = 1, title = "Show", description = null, cover_url = null, genre = null,
            episode_id = 10, season_number = 1, episode_number = 2, episode_title = null,
            progress_seconds = 100f, duration_seconds = 1000f, source = "sto"
        )
        coEvery { repository.getContinueWatching() } returns Result.success(listOf(item))
        coEvery { repository.hideContinueWatching(1, "sto") } returns Result.failure(RuntimeException("offline"))

        advanceUntilIdle()
        viewModel.selectSidebarIndex(0)
        advanceUntilIdle()
        viewModel.hideContinueWatching(item)
        advanceUntilIdle()

        assertEquals(listOf(item), viewModel.uiState.value.continueWatchingItems)
    }

    @Test
    fun `loading error offers retry and retry reloads content`() = runTest {
        val movies = listOf(Movie(1, "Movie 1", "Desc", "url", "pageUrl", "Action"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } returnsMany listOf(
            Result.failure(RuntimeException("Server nicht erreichbar")),
            Result.success(movies)
        )

        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals("Server nicht erreichbar", failed.errorMessage)
        assertTrue(failed.canRetry)

        viewModel.retry()
        advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertEquals(movies, recovered.movies)
        assertFalse(recovered.canRetry)
    }

    @Test
    fun `returning to a loaded genre uses cached content`() = runTest {
        val actionMovies = listOf(Movie(1, "Movie 1", "Desc", "url", "pageUrl", "Action"))
        val comedyMovies = listOf(Movie(2, "Movie 2", "Desc", "url", "pageUrl", "Comedy"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } returns Result.success(actionMovies)
        coEvery { repository.getMoviesByGenre("Comedy", 1, 20) } returns Result.success(comedyMovies)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)
        advanceUntilIdle()
        viewModel.selectSidebarIndex(4)
        advanceUntilIdle()
        viewModel.selectSidebarIndex(3)

        assertEquals(actionMovies, viewModel.uiState.value.movies)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { repository.getMoviesByGenre("Action", 1, 20) }
    }
}
