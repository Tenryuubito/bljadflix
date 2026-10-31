package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.feature.player.PlayerViewModel
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
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        viewModel = PlayerViewModel(repository)
    }

    @Test
    fun `initPlayer resolves stream and loads episodes`() = runTest {
        val streamResponse = StreamResponse("http://stream.m3u8", mapOf("Header" to "Val"))
        val episodesMap = mapOf(
            "1" to listOf(
                Episode(10, 1, "Ep 1", "url1"),
                Episode(11, 2, "Ep 2", "url2")
            )
        )
        coEvery { repository.getEpisodes(1) } returns Result.success(episodesMap)
        coEvery { repository.resolveStream(10) } returns Result.success(streamResponse)

        viewModel.initPlayer(contentId = 1, episodeId = 10)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("http://stream.m3u8", state.streamUrl)
            assertEquals(2, state.episodesList.size)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `updateProgressState tracks max progress and triggers completion`() = runTest {
        val streamResponse = StreamResponse("http://stream.m3u8", emptyMap())
        coEvery { repository.getEpisodes(1) } returns Result.success(emptyMap())
        coEvery { repository.resolveStream(10) } returns Result.success(streamResponse)

        viewModel.initPlayer(contentId = 1, episodeId = 10)

        viewModel.updateProgressState(currentTime = 950f, duration = 1000f, isPaused = false, isEnded = false)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(950f, state.currentTime)
            assertEquals(1000f, state.duration)
            assertEquals(950f, state.maxProgress)
            assertTrue(state.isPlaying)
            assertTrue(state.isCompletedMarked)
        }

        coVerify { repository.updateProgress(any()) }
    }
}
