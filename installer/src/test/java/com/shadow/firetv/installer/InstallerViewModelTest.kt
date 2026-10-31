package com.shadow.firetv.installer

import com.shadow.firetv.installer.data.AppVersionInfo
import com.shadow.firetv.installer.data.ComponentVersion
import com.shadow.firetv.installer.data.MainAppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerViewModelTest {

    private fun versionInfo(launcherCode: Int, mainCode: Int) = AppVersionInfo(
        launcher = ComponentVersion(launcherCode, "1.0.$launcherCode", "com.shadow.firetv.installer"),
        mainApp = MainAppVersion(
            packageName = "com.shadow.firetv",
            versionCode = mainCode,
            versionName = "1.0.$mainCode",
            downloadUrl = "/app/download/main",
            minLauncherVersionCode = 1,
            changelog = null
        )
    )

    @Test
    fun `initial uiState has expected default values`() {
        val viewModel = InstallerViewModel()
        val state = viewModel.uiState.value

        assertEquals("Initialisiere Installer...", state.statusMessage)
        assertFalse(state.isDownloading)
        assertFalse(state.readyToLaunch)
        assertFalse(state.canInstallOrUpdate)
        assertNull(state.downloadedApkFile)
        assertNull(state.errorMessage)
        assertNull(state.pendingAction)
    }

    @Test
    fun `newer launcher on server is updated first`() {
        val action = InstallerViewModel.decideAction(versionInfo(5, 5), launcherVersionCode = 4, installedMainVersionCode = 3)
        assertEquals(InstallerAction.UPDATE_LAUNCHER, action)
    }

    @Test
    fun `missing main app is installed`() {
        val action = InstallerViewModel.decideAction(versionInfo(5, 5), launcherVersionCode = 5, installedMainVersionCode = null)
        assertEquals(InstallerAction.INSTALL_MAIN_APP, action)
    }

    @Test
    fun `outdated main app is updated`() {
        val action = InstallerViewModel.decideAction(versionInfo(5, 5), launcherVersionCode = 5, installedMainVersionCode = 4)
        assertEquals(InstallerAction.INSTALL_MAIN_APP, action)
    }

    @Test
    fun `up to date main app is launched`() {
        val action = InstallerViewModel.decideAction(versionInfo(5, 5), launcherVersionCode = 6, installedMainVersionCode = 5)
        assertEquals(InstallerAction.LAUNCH_MAIN_APP, action)
    }

    @Test
    fun `relative download url is joined with base url`() {
        assertEquals(
            "http://10.0.0.5:8000/app/download/launcher",
            InstallerViewModel.resolveUrl("http://10.0.0.5:8000/", "/app/download/launcher")
        )
        assertEquals(
            "https://cdn.example/app.apk",
            InstallerViewModel.resolveUrl("http://10.0.0.5:8000", "https://cdn.example/app.apk")
        )
    }
}
