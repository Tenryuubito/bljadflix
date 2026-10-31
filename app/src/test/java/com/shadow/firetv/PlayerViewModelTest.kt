package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.model.StreamResponse
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.stream.HosterLinkResolver
import com.shadow.firetv.feature.player.PlayerEvent
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
    private val hosterLinkResolver: HosterLinkResolver = mockk()
    private val sourceManager = SourceManager.createForTesting()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        sourceManager.setSelectedSource(StreamingSource.STO)
        coEvery { hosterLinkResolver.resolve(any()) } returns Result.failure(Exception("Kein Hoster"))
        viewModel = PlayerViewModel(repository, hosterLinkResolver, sourceManager)
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
    fun `failed stream resolution shows error message before closing player`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(emptyMap())
        coEvery { repository.resolveStream(10) } returns Result.failure(Exception("Failed to fetch episode page"))

        viewModel.events.test {
            viewModel.initPlayer(contentId = 1, episodeId = 10)
            assertEquals(PlayerEvent.ShowToast("Failed to fetch episode page"), awaitItem())
            assertEquals(PlayerEvent.FinishActivity, awaitItem())
        }
    }

    @Test
    fun `aniworld stream is resolved on device without asking the backend`() = runTest {
        val episodeUrl = "https://aniworld.to/anime/stream/show/staffel-1/episode-1"
        coEvery { repository.getEpisodes(1, "aniworld") } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", episode_url = episodeUrl)))
        )
        coEvery { hosterLinkResolver.resolve(episodeUrl) } returns Result.success(StreamResponse("https://voe.sx/e/abc", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10, source = "aniworld")

        assertEquals("https://voe.sx/e/abc", viewModel.uiState.value.streamUrl)
        coVerify(exactly = 0) { repository.resolveStream(any(), any()) }
    }

    @Test
    fun `backend failure falls back to on-device resolution`() = runTest {
        val episodeUrl = "http://186.2.175.5/serie/show/staffel-1/episode-1"
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", episode_url = episodeUrl)))
        )
        coEvery { repository.resolveStream(10) } returns Result.failure(Exception("Failed to fetch episode page"))
        coEvery { hosterLinkResolver.resolve(episodeUrl) } returns Result.success(StreamResponse("https://voe.sx/e/xyz", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)

        assertEquals("https://voe.sx/e/xyz", viewModel.uiState.value.streamUrl)
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
