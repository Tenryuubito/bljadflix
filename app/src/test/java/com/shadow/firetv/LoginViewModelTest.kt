package com.shadow.firetv

import app.cash.turbine.test
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.LoginResponse
import com.shadow.firetv.feature.auth.LoginEvent
import com.shadow.firetv.feature.auth.LoginViewModel
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
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)
    private lateinit var authManager: AuthManager
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        authManager = AuthManager.createForTesting()
        authManager.clearSession()
        viewModel = LoginViewModel(repository, authManager)
    }

    @Test
    fun `changing username updates uiState`() = runTest {
        viewModel.onUsernameChanged("testuser")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("testuser", state.usernameInput)
        }
    }

    @Test
    fun `changing password updates uiState`() = runTest {
        viewModel.onPasswordChanged("secret123")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("secret123", state.passwordInput)
        }
    }

    @Test
    fun `submitLogin with empty username sets error message`() = runTest {
        viewModel.submitLogin(username = "", password = "password")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Bitte Benutzernamen eingeben", state.errorMessage)
        }
    }

    @Test
    fun `submitLogin with empty password sets error message`() = runTest {
        viewModel.submitLogin(username = "user", password = "")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Bitte Passwort eingeben", state.errorMessage)
        }
    }

    @Test
    fun `submitLogin with valid credentials logs in user and emits NavigateToMain`() = runTest {
        val loginResponse = LoginResponse(token = "tok_123", user_id = 10, username = "testuser", device_id = "device_xyz")
        coEvery { repository.login(username = "testuser", password = "password123", code = null, deviceId = any(), deviceName = any()) } returns Result.success(loginResponse)

        viewModel.events.test {
            viewModel.submitLogin(username = "testuser", password = "password123")

            val event = awaitItem()
            assertEquals(LoginEvent.NavigateToMain, event)
            assertTrue(authManager.isLoggedIn())
            assertEquals("tok_123", authManager.getToken())
        }
    }

    @Test
    fun `submitLogin with invalid credentials sets error message`() = runTest {
        coEvery { repository.login(username = "wrong", password = "bad", code = null, deviceId = any(), deviceName = any()) } returns Result.failure(RuntimeException("Ungültige Anmeldedaten"))

        viewModel.submitLogin(username = "wrong", password = "bad")

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoggedIn)
            assertEquals("Ungültige Anmeldedaten", state.errorMessage)
        }
    }
}
