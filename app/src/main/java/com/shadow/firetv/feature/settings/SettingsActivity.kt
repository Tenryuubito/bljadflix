package com.shadow.firetv.feature.settings

import android.content.Intent
import android.os.Bundle
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.shadow.firetv.core.ui.theme.BljadflixTheme
import com.shadow.firetv.feature.auth.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = SettingsActions(
            onSourceSelected = viewModel::onSourceSelected,
            onThemeSelected = viewModel::onThemeSelected,
            onToggleIntro = viewModel::toggleIntro,
            onTriggerIndexer = viewModel::triggerIndexer,
            onSync = viewModel::syncUserContent,
            onClearCache = ::clearAppCache,
            onLogout = viewModel::logout
        )

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BljadflixTheme(theme = state.selectedTheme) {
                SettingsScreen(state = state, actions = actions)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SettingsEvent.ShowToast -> Toast.makeText(this@SettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                        is SettingsEvent.NavigateToLogin -> {
                            val intent = Intent(this@SettingsActivity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun clearAppCache() {
        lifecycleScope.launch {
            Glide.get(this@SettingsActivity).clearMemory()
            WebStorage.getInstance().deleteAllData()
            withContext(Dispatchers.IO) {
                Glide.get(this@SettingsActivity).clearDiskCache()
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            viewModel.clearCache()
        }
    }
}
