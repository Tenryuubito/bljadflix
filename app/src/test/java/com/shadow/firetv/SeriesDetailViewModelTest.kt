package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.model.WatchlistStatus
import com.shadow.firetv.feature.details.SeriesDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)
    private lateinit var viewModel: SeriesDetailViewModel

    @Before
    fun setup() {
        viewModel = SeriesDetailViewModel(repository)
    }

    @Test
    fun `initData loads watchlist status and episodes`() = runTest {
        val episodesMap = mapOf(
            "1" to listOf(
                Episode(101, 1, "Pilot", "http://ep1", is_completed = true, last_watched_at = 1000.0),
                Episode(102, 2, "Ep 2", "http://ep2", is_completed = false, last_watched_at = 0.0)
            )
        )
        coEvery { repository.getWatchlistStatus(1) } returns Result.success(WatchlistStatus(is_watchlisted = true))
        coEvery { repository.getEpisodes(1) } returns Result.success(episodesMap)

        viewModel.initData(1, "Test Series", "Desc", "Genre", "coverUrl")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Test Series", state.title)
            assertTrue(state.isWatchlisted)
            assertEquals(listOf("1"), state.seasonsList)
            assertEquals("1", state.selectedSeason)
            assertEquals(2, state.currentEpisodes.size)
            // Continue episode should be Ep 2 (since Pilot was completed)
            assertEquals(102, state.continueEpisode?.id)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `toggleWatchlist toggles state and calls repository`() = runTest {
        coEvery { repository.getWatchlistStatus(5) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(5) } returns Result.success(emptyMap())
        coEvery { repository.updateWatchlist(WatchlistRequest(5, true)) } returns Result.success(Unit)

        viewModel.initData(5, "Title", "Desc", null, null)
        viewModel.toggleWatchlist()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isWatchlisted)
            coVerify { repository.updateWatchlist(WatchlistRequest(5, true)) }
        }
    }

    @Test
    fun `toggleWatchlist reverts state if repository returns error`() = runTest {
        coEvery { repository.getWatchlistStatus(5) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(5) } returns Result.success(emptyMap())
        coEvery { repository.updateWatchlist(WatchlistRequest(5, true)) } returns Result.failure(RuntimeException("Error"))

        viewModel.initData(5, "Title", "Desc", null, null)
        viewModel.toggleWatchlist()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isWatchlisted)
        }
    }

    @Test
    fun `initData handles formatted season keys correctly`() = runTest {
        val episodesMap = mapOf(
            "Staffel 1" to listOf(
                Episode(201, 1, "Ep 1", "http://ep1")
            ),
            "Staffel 2" to listOf(
                Episode(202, 1, "Ep 2", "http://ep2")
            )
        )
        coEvery { repository.getWatchlistStatus(2) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(2) } returns Result.success(episodesMap)

        viewModel.initData(2, "Formatted Series", "Desc", "Genre", "coverUrl")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("Staffel 1", "Staffel 2"), state.seasonsList)
            assertEquals("Staffel 1", state.selectedSeason)
            assertEquals(1, state.currentEpisodes.size)
            assertEquals(201, state.currentEpisodes[0].id)
        }
    }

    @Test
    fun `initData reindexes series when episodes map is empty`() = runTest {
        val reindexedMap = mapOf(
            "1" to listOf(Episode(301, 1, "Reindexed Ep", "http://ep301"))
        )
        coEvery { repository.getWatchlistStatus(3) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(3) } returnsMany listOf(Result.success(emptyMap()), Result.success(reindexedMap))
        coEvery { repository.reindexSeries(3) } returns Result.success(TriggerIndexResponse("success"))

        viewModel.initData(3, "New Series", "Desc", null, null)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("1"), state.seasonsList)
            assertEquals(1, state.currentEpisodes.size)
            assertEquals(301, state.currentEpisodes[0].id)
            coVerify { repository.reindexSeries(3) }
        }
    }
}
