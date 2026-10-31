package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.TriggerIndexResponse
import com.shadow.firetv.core.model.UserSession
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.feature.settings.SettingsEvent
import com.shadow.firetv.feature.settings.SettingsViewModel
import io.mockk.coEvery
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authManager: AuthManager
    private lateinit var sourceManager: SourceManager
    private lateinit var themeManager: ThemeManager
    private val repository: MovieRepository = mockk(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        authManager = AuthManager.createForTesting()
        sourceManager = SourceManager.createForTesting()
        themeManager = ThemeManager.createForTesting()

        authManager.saveSession(
            UserSession(
                token = "test_token",
                userId = 123,
                username = "TestUser",
                deviceId = "device_1"
            )
        )

        viewModel = SettingsViewModel(authManager, sourceManager, themeManager, repository)
    }

    @Test
    fun `initial state loads profile and current settings`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("TestUser", state.username)
            assertEquals(123, state.userId)
            assertEquals("device_1", state.deviceId)
            assertEquals(StreamingSource.ANIWORLD, state.selectedSource)
            assertEquals(AppTheme.SOVIET_RED, state.selectedTheme)
        }
    }

    @Test
    fun `selecting source updates state and sourceManager`() = runTest {
        viewModel.onSourceSelected(StreamingSource.STO)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(StreamingSource.STO, state.selectedSource)
            assertEquals(StreamingSource.STO, sourceManager.getSelectedSource())
        }
    }

    @Test
    fun `selecting theme updates state and themeManager`() = runTest {
        viewModel.onThemeSelected(AppTheme.EMERALD_GREEN)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppTheme.EMERALD_GREEN, state.selectedTheme)
            assertEquals(AppTheme.EMERALD_GREEN, themeManager.getSelectedTheme())
        }
    }

    @Test
    fun `syncUserContent triggers sync and updates state and emits toast`() = runTest {
        coEvery { repository.syncUserContent() } returns Result.success(Unit)

        viewModel.events.test {
            viewModel.syncUserContent()

            val toastEvent = awaitItem()
            assertEquals(SettingsEvent.ShowToast("Benutzerdaten erfolgreich synchronisiert"), toastEvent)
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isSyncing)
            assertEquals("Synchronisation erfolgreich", state.syncStatusMessage)
        }
    }

    @Test
    fun `triggerIndexer triggers indexer and updates state and emits toast`() = runTest {
        coEvery { repository.triggerIndex(source = "aniworld") } returns Result.success(
            TriggerIndexResponse(status = "success", source = "aniworld")
        )

        viewModel.events.test {
            viewModel.triggerIndexer()

            val toastEvent = awaitItem()
            assertEquals(SettingsEvent.ShowToast("Indexer gestartet (aniworld)"), toastEvent)
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isIndexing)
            assertEquals("Indexer erfolgreich gestartet", state.indexingStatusMessage)
        }
    }

    @Test
    fun `clearCache emits toast event`() = runTest {
        viewModel.events.test {
            viewModel.clearCache()

            val toastEvent = awaitItem()
            assertEquals(SettingsEvent.ShowToast("Cache & temporäre Daten wurden geleert"), toastEvent)
        }
    }

    @Test
    fun `logout clears session and emits NavigateToLogin event`() = runTest {
        viewModel.events.test {
            viewModel.logout()

            val toastEvent = awaitItem()
            val navEvent = awaitItem()

            assertFalse(authManager.isLoggedIn())
            assertEquals(SettingsEvent.NavigateToLogin, navEvent)
        }
    }

    @Test
    fun `toggleIntro flips the intro setting`() = runTest {
        assertTrue(viewModel.uiState.value.isIntroEnabled)

        viewModel.toggleIntro()

        assertFalse(viewModel.uiState.value.isIntroEnabled)
    }
}
