package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.WatchlistRequest
import com.shadow.firetv.core.model.WatchlistStatus
import com.shadow.firetv.feature.details.SeriesDetailEvent
import com.shadow.firetv.feature.details.SeriesDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
            assertEquals(102, state.continueEpisode?.id)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `refreshEpisodes keeps selected season and updates progress`() = runTest {
        val initialMap = mapOf(
            "1" to listOf(Episode(101, 1, "Pilot", "http://ep1")),
            "2" to listOf(Episode(201, 1, "S2 Pilot", "http://ep3"))
        )
        val refreshedMap = mapOf(
            "1" to listOf(Episode(101, 1, "Pilot", "http://ep1")),
            "2" to listOf(Episode(201, 1, "S2 Pilot", "http://ep3", progress_seconds = 300f, duration_seconds = 1200f, last_watched_at = 500.0))
        )
        coEvery { repository.getWatchlistStatus(1) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(1) } returnsMany listOf(Result.success(initialMap), Result.success(refreshedMap))

        viewModel.initData(1, "Test Series", "Desc", "Genre", "coverUrl")
        viewModel.selectSeason("2")
        viewModel.refreshEpisodes()

        val state = viewModel.uiState.value
        assertEquals("2", state.selectedSeason)
        assertEquals(300f, state.currentEpisodes.first().progress_seconds)
        assertEquals(201, state.continueEpisode?.id)
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

    @Test
    fun `toggleWatchlist confirms success with a toast`() = runTest {
        coEvery { repository.getWatchlistStatus(6) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(6) } returns Result.success(mapOf("1" to listOf(Episode(1, 1, "Ep", "u"))))
        coEvery { repository.updateWatchlist(WatchlistRequest(6, true)) } returns Result.success(Unit)

        viewModel.initData(6, "Title", "Desc", null, null)
        viewModel.events.test {
            viewModel.toggleWatchlist()
            assertEquals(SeriesDetailEvent.ShowToast("Zur Watchlist hinzugefügt"), awaitItem())
        }
    }

    @Test
    fun `toggleWatchlist reports failure with a toast`() = runTest {
        coEvery { repository.getWatchlistStatus(6) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(6) } returns Result.success(mapOf("1" to listOf(Episode(1, 1, "Ep", "u"))))
        coEvery { repository.updateWatchlist(WatchlistRequest(6, true)) } returns Result.failure(RuntimeException("offline"))

        viewModel.initData(6, "Title", "Desc", null, null)
        viewModel.events.test {
            viewModel.toggleWatchlist()
            assertEquals(SeriesDetailEvent.ShowToast("Watchlist konnte nicht gespeichert werden"), awaitItem())
        }
    }

    @Test
    fun `initData preselects the season of the continue episode`() = runTest {
        val episodesMap = mapOf(
            "1" to listOf(Episode(101, 1, "S1", "u", is_completed = true, last_watched_at = 100.0)),
            "2" to listOf(Episode(201, 1, "S2", "u", progress_seconds = 300f, duration_seconds = 1200f, last_watched_at = 200.0))
        )
        coEvery { repository.getWatchlistStatus(7) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(7) } returns Result.success(episodesMap)

        viewModel.initData(7, "Title", "Desc", null, null)

        val state = viewModel.uiState.value
        assertEquals("2", state.selectedSeason)
        assertEquals(201, state.currentEpisodes.first().id)
    }

    @Test
    fun `reindexSeries polls until new episodes appear`() = runTest {
        val before = mapOf("1" to listOf(Episode(1, 1, "Ep 1", "u")))
        val after = mapOf("1" to listOf(Episode(1, 1, "Ep 1", "u"), Episode(2, 2, "Ep 2", "u"), Episode(3, 3, "Ep 3", "u")))
        coEvery { repository.getWatchlistStatus(8) } returns Result.success(WatchlistStatus(is_watchlisted = false))
        coEvery { repository.getEpisodes(8) } returnsMany listOf(
            Result.success(before),
            Result.success(before),
            Result.success(after)
        )
        coEvery { repository.reindexSeries(8) } returns Result.success(TriggerIndexResponse("success"))

        viewModel.initData(8, "Title", "Desc", null, null)
        viewModel.events.test {
            viewModel.reindexSeries()
            assertTrue(viewModel.uiState.value.isReindexing)
            advanceUntilIdle()
            assertEquals(SeriesDetailEvent.ShowToast("2 neue Folgen gefunden"), awaitItem())
        }

        val state = viewModel.uiState.value
        assertFalse(state.isReindexing)
        assertEquals(3, state.currentEpisodes.size)
    }
}
