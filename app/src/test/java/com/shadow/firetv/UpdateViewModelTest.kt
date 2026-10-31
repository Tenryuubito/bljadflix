package com.shadow.firetv

import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.model.AppVersionResponse
import com.shadow.firetv.core.model.MainAppVersion
import com.shadow.firetv.feature.update.UpdateViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk(relaxed = true)

    private fun serverVersion(code: Int) {
        coEvery { repository.getAppVersion() } returns Result.success(
            AppVersionResponse(MainAppVersion(versionCode = code, versionName = "1.0.$code", changelog = "Neu"))
        )
    }

    @Test
    fun `newer server version is offered as update`() {
        serverVersion(12)
        val viewModel = UpdateViewModel(repository, installedVersionCode = 10)

        viewModel.checkForUpdate()

        assertEquals(12, viewModel.availableUpdate.value?.versionCode)
    }

    @Test
    fun `same or older server version is not offered`() {
        serverVersion(10)
        val viewModel = UpdateViewModel(repository, installedVersionCode = 10)

        viewModel.checkForUpdate()

        assertNull(viewModel.availableUpdate.value)
    }

    @Test
    fun `dismissed version is not offered again but a newer one is`() {
        serverVersion(12)
        val viewModel = UpdateViewModel(repository, installedVersionCode = 10)
        viewModel.checkForUpdate()
        viewModel.dismissUpdate()

        viewModel.checkForUpdate()
        assertNull(viewModel.availableUpdate.value)

        serverVersion(13)
        viewModel.checkForUpdate()
        assertEquals(13, viewModel.availableUpdate.value?.versionCode)
    }

    @Test
    fun `network error offers no update`() {
        coEvery { repository.getAppVersion() } returns Result.failure(Exception("offline"))
        val viewModel = UpdateViewModel(repository, installedVersionCode = 10)

        viewModel.checkForUpdate()

        assertNull(viewModel.availableUpdate.value)
    }
}
