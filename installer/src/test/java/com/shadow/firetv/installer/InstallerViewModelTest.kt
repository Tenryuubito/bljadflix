package com.shadow.firetv.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerViewModelTest {

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
    }
}
