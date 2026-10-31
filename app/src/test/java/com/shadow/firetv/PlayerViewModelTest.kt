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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        coEvery { hosterLinkResolver.resolve(any(), any()) } returns Result.failure(Exception("Kein Hoster"))
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
    fun `failed stream resolution keeps player open with error message`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(emptyMap())
        coEvery { repository.resolveStream(10) } returns Result.failure(Exception("Failed to fetch episode page"))

        viewModel.events.test {
            viewModel.initPlayer(contentId = 1, episodeId = 10)
            expectNoEvents()
        }

        val state = viewModel.uiState.value
        assertEquals("Failed to fetch episode page", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `retry clears error and loads stream again`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(emptyMap())
        coEvery { repository.resolveStream(10) } returnsMany listOf(
            Result.failure(Exception("Timeout")),
            Result.success(StreamResponse("http://stream.m3u8", emptyMap()))
        )

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.retry()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals("http://stream.m3u8", state.streamUrl)
    }

    @Test
    fun `other hoster resolves next ranked hoster on device`() = runTest {
        val episodeUrl = "http://186.2.175.5/serie/show/staffel-1/episode-1"
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", episode_url = episodeUrl)))
        )
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("https://voe.sx/e/first", emptyMap()))
        coEvery { hosterLinkResolver.resolve(episodeUrl, 1) } returns Result.success(StreamResponse("https://vidmoly.to/e/second", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.tryOtherHoster()

        val state = viewModel.uiState.value
        assertEquals("https://vidmoly.to/e/second", state.streamUrl)
        assertEquals(1, state.hosterSkip)
    }

    @Test
    fun `playback resumes at saved position`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", progress_seconds = 600f, duration_seconds = 1400f)))
        )
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.events.test {
            viewModel.initPlayer(contentId = 1, episodeId = 10)
            viewModel.updateProgressState(currentTime = 0.5f, duration = 1400f, isPaused = false, isEnded = false)
            assertEquals(PlayerEvent.SeekTo(597f, isResume = true), awaitItem())
        }
    }

    @Test
    fun `completed episode starts from the beginning`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", is_completed = true, progress_seconds = 1350f, duration_seconds = 1400f)))
        )
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.events.test {
            viewModel.initPlayer(contentId = 1, episodeId = 10)
            viewModel.updateProgressState(currentTime = 0.5f, duration = 1400f, isPaused = false, isEnded = false)
            expectNoEvents()
        }
    }

    @Test
    fun `countdown near the end starts the next episode`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", ""), Episode(11, 2, "Ep 2", "")))
        )
        coEvery { repository.resolveStream(any()) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.updateProgressState(currentTime = 1395f, duration = 1400f, isPaused = false, isEnded = false)

        assertEquals(10, viewModel.uiState.value.countdownSeconds)
        assertTrue(viewModel.uiState.value.showNextEpisodeCard)

        advanceTimeBy(10_500)

        assertEquals(11, viewModel.uiState.value.episodeId)
        assertNull(viewModel.uiState.value.countdownSeconds)
        coVerify { repository.resolveStream(11, any()) }
    }

    @Test
    fun `cancelling the countdown stays on the current episode`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", ""), Episode(11, 2, "Ep 2", "")))
        )
        coEvery { repository.resolveStream(any()) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.updateProgressState(currentTime = 1395f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.cancelCountdown()
        advanceTimeBy(15_000)

        val state = viewModel.uiState.value
        assertEquals(10, state.episodeId)
        assertTrue(state.isCountdownCancelled)
        assertFalse(state.showNextEpisodeCard)
    }

    @Test
    fun `skipping episodes switches to neighbours with season info`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf(
                "1" to listOf(Episode(10, 1, "Ep 1", "")),
                "2" to listOf(Episode(20, 1, "S2 Ep 1", ""))
            )
        )
        coEvery { repository.resolveStream(any()) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.skipToNextEpisode()

        assertEquals(20, viewModel.uiState.value.episodeId)
        assertEquals(2, viewModel.uiState.value.currentEpisode?.season_number)

        viewModel.skipToPreviousEpisode()

        assertEquals(10, viewModel.uiState.value.episodeId)
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
    fun `saveProgress stores the current position after seeking back`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(mapOf("1" to listOf(Episode(10, 1, "Ep 1", ""))))
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.updateProgressState(currentTime = 300f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.updateProgressState(currentTime = 120f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.saveProgress()

        coVerify {
            repository.updateProgress(match { it.episode_id == 10 && it.progress_seconds == 120f && !it.is_completed })
        }
    }

    @Test
    fun `saveProgress ignores very short views`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(mapOf("1" to listOf(Episode(10, 1, "Ep 1", ""))))
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.updateProgressState(currentTime = 4f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.saveProgress()

        coVerify(exactly = 0) { repository.updateProgress(any()) }
    }

    @Test
    fun `saveProgress waits until the resume seek has landed`() = runTest {
        coEvery { repository.getEpisodes(1) } returns Result.success(
            mapOf("1" to listOf(Episode(10, 1, "Ep 1", "", progress_seconds = 600f, duration_seconds = 1400f)))
        )
        coEvery { repository.resolveStream(10) } returns Result.success(StreamResponse("http://stream.m3u8", emptyMap()))

        viewModel.initPlayer(contentId = 1, episodeId = 10)
        viewModel.updateProgressState(currentTime = 12f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.saveProgress()
        coVerify(exactly = 0) { repository.updateProgress(any()) }

        viewModel.updateProgressState(currentTime = 598f, duration = 1400f, isPaused = false, isEnded = false)
        viewModel.saveProgress()
        coVerify { repository.updateProgress(match { it.progress_seconds == 598f }) }
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
