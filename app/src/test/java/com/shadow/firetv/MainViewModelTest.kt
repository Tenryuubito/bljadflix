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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val expectedSidebar = listOf("📺 Weiterschauen", "⭐ Meine Watchlist", "🎬 Alle Serien", "⚙️ Einstellungen", "Action", "Comedy")
        assertEquals(expectedSidebar, state.sidebarItems)
        assertEquals(MainMode.CONTINUE_WATCHING, state.mode)
    }

    @Test
    fun `selecting genre sidebar item updates mode and loads genre movies`() = runTest {
        val movies = listOf(Movie(1, "Action Movie", "Desc", "url", "pageUrl", "Action"))
        coEvery { repository.getMoviesByGenre("Action", 1, 20) } returns Result.success(movies)

        advanceUntilIdle()
        viewModel.selectSidebarIndex(4) // Index 4 is "Action"
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MainMode.GENRE, state.mode)
        assertEquals("Action", state.currentGenre)
        assertEquals(movies, state.movies)
        assertFalse(state.isLoading)
    }

    @Test
    fun `selecting settings sidebar item emits NavigateToSettings event`() = runTest {
        advanceUntilIdle()
        viewModel.events.test {
            viewModel.selectSidebarIndex(3) // Index 3 is "⚙️ Einstellungen"
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
        viewModel.selectSidebarIndex(0) // Index 0 is "📺 Weiterschauen"
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MainMode.CONTINUE_WATCHING, state.mode)
        assertEquals(continueItems, state.continueWatchingItems)
        assertFalse(state.isLoading)
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
}
