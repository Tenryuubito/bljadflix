package com.shadow.firetv

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.MainAppVersion
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.core.ui.components.DialogAction
import com.shadow.firetv.core.ui.components.TvDialog
import com.shadow.firetv.core.ui.theme.BljadflixTheme
import com.shadow.firetv.feature.auth.LoginActivity
import com.shadow.firetv.feature.main.MainActions
import com.shadow.firetv.feature.main.MainEvent
import com.shadow.firetv.feature.main.MainScreen
import com.shadow.firetv.feature.main.MainViewModel
import com.shadow.firetv.feature.main.SplashOverlay
import com.shadow.firetv.feature.settings.SettingsActivity
import com.shadow.firetv.feature.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory() }
    private val updateViewModel: UpdateViewModel by viewModels { UpdateViewModel.Factory() }

    private var isSplashAnimDone = false
    private var isIntroAudioDone = false
    private var isInitialDataLoaded = false
    private var mediaPlayer: MediaPlayer? = null
    private val isSplashVisible = mutableStateOf(true)
    private val optionsItem = mutableStateOf<ContinueWatchingItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ThemeManager.getInstance(this).isIntroEnabled()) {
            playIntro()
        } else {
            isIntroAudioDone = true
        }

        val actions = MainActions(
            onSidebarSelect = viewModel::selectSidebarIndex,
            onSearchChange = viewModel::onSearchQueryChanged,
            onOpenSettings = viewModel::openSettings,
            onMovieClick = ::openSeries,
            onContinueClick = ::openPlayer,
            onContinueOptions = { optionsItem.value = it },
            onLoadMore = { viewModel.loadContentBottom() },
            onRetry = viewModel::retry
        )

        setContent {
            BljadflixTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val update by updateViewModel.availableUpdate.collectAsStateWithLifecycle()
                val splashVisible by isSplashVisible
                val options by optionsItem

                LaunchedEffect(state.isInitializing) {
                    if (!state.isInitializing) {
                        isInitialDataLoaded = true
                        checkSplashAndHide()
                    }
                }

                Box {
                    MainScreen(state = state, isSplashVisible = splashVisible, actions = actions)
                    AnimatedVisibility(visible = splashVisible, exit = fadeOut(tween(400))) {
                        SplashOverlay(
                            progressPercent = state.initProgressPercent,
                            statusText = state.initStepDescription,
                            onAnimationDone = {
                                isSplashAnimDone = true
                                checkSplashAndHide()
                            }
                        )
                    }
                }

                options?.let { item -> ContinueWatchingOptionsDialog(item) }
                val pendingUpdate = update
                if (pendingUpdate != null && !splashVisible && options == null) {
                    UpdateDialog(pendingUpdate)
                }
            }
        }

        observeEvents()
    }

    @androidx.compose.runtime.Composable
    private fun ContinueWatchingOptionsDialog(item: ContinueWatchingItem) {
        val close = { optionsItem.value = null }
        TvDialog(
            title = item.title,
            onDismiss = close,
            actions = listOf(
                DialogAction(stringResource(R.string.continue_option_play, item.season_number, item.episode_number), isPrimary = true) {
                    close()
                    openPlayer(item)
                },
                DialogAction(stringResource(R.string.continue_option_series)) {
                    close()
                    openSeries(item)
                },
                DialogAction(stringResource(R.string.continue_option_remove)) {
                    close()
                    viewModel.hideContinueWatching(item)
                },
                DialogAction(stringResource(R.string.dialog_cancel), onClick = close)
            )
        )
    }

    @androidx.compose.runtime.Composable
    private fun UpdateDialog(update: MainAppVersion) {
        val changelog = update.changelog?.takeIf { it.isNotBlank() }
        val message = buildString {
            append(stringResource(R.string.update_dialog_message, update.versionName, BuildConfig.VERSION_NAME))
            if (changelog != null) {
                append("\n\n")
                append(changelog)
            }
        }
        TvDialog(
            title = stringResource(R.string.update_dialog_title),
            message = message,
            onDismiss = updateViewModel::dismissUpdate,
            actions = listOf(
                DialogAction(stringResource(R.string.update_dialog_install), isPrimary = true, onClick = ::startLauncherUpdate),
                DialogAction(stringResource(R.string.update_dialog_later), onClick = updateViewModel::dismissUpdate)
            )
        )
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        is MainEvent.NavigateToLogin -> {
                            val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
                                if (!event.message.isNullOrEmpty()) putExtra("KICKOUT_MESSAGE", event.message)
                            }
                            startActivity(intent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }
                        is MainEvent.NavigateToSettings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAuthStatus()
        viewModel.refreshState()
        viewModel.refreshContinueWatching()
        updateViewModel.checkForUpdate()
    }

    private fun openSeries(movie: Movie) {
        openSeries(movie.id, movie.title, movie.description, movie.genre, movie.cover_url, movie.source)
    }

    private fun openSeries(item: ContinueWatchingItem) {
        openSeries(item.id, item.title, item.description, item.genre, item.cover_url, item.source)
    }

    private fun openSeries(id: Int, title: String, description: String?, genre: String?, coverUrl: String?, source: String?) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("CONTENT_ID", id)
            putExtra("TITLE", title)
            putExtra("DESCRIPTION", description)
            putExtra("GENRE", genre)
            putExtra("COVER_URL", coverUrl)
            putExtra("SOURCE", source)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openPlayer(item: ContinueWatchingItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EPISODE_ID", item.episode_id)
            putExtra("CONTENT_ID", item.id)
            putExtra("SOURCE", item.source)
            putExtra("TITLE", item.title)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun startLauncherUpdate() {
        val launchIntent = packageManager.getLaunchIntentForPackage(LAUNCHER_PACKAGE)
        if (launchIntent == null) {
            updateViewModel.dismissUpdate()
            Toast.makeText(
                this,
                getString(R.string.update_launcher_missing, BuildConfig.BACKEND_URL.trimEnd('/')),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
        finishAffinity()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isSplashVisible.value && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) skipIntro()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun playIntro() {
        try {
            val player = MediaPlayer.create(this, R.raw.bljadflix_intro)
            if (player == null) {
                isIntroAudioDone = true
                return
            }
            mediaPlayer = player
            player.setOnCompletionListener { mp -> onIntroFinished(mp) }
            player.setOnErrorListener { mp, _, _ ->
                onIntroFinished(mp)
                true
            }
            player.start()
        } catch (_: Exception) {
            isIntroAudioDone = true
        }
    }

    private fun onIntroFinished(mp: MediaPlayer) {
        mp.release()
        if (mediaPlayer === mp) mediaPlayer = null
        isIntroAudioDone = true
        checkSplashAndHide()
    }

    private fun skipIntro() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isIntroAudioDone = true
        isSplashAnimDone = true
        checkSplashAndHide()
    }

    private fun checkSplashAndHide() {
        if (isSplashAnimDone && isIntroAudioDone && isInitialDataLoaded) isSplashVisible.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LAUNCHER_PACKAGE = "com.shadow.firetv.installer"
    }
}
