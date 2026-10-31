package com.shadow.firetv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.feature.player.PlayerEvent
import com.shadow.firetv.feature.player.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class PlayerActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private lateinit var controlsContainer: FrameLayout
    private lateinit var playPauseIcon: ImageView
    private lateinit var seekBar: ProgressBar
    private lateinit var textCurrentTime: TextView
    private lateinit var textDuration: TextView
    private lateinit var tvSeekIndicator: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        controlsContainer.animate()
            .alpha(0.0f)
            .setDuration(300)
            .withEndAction { controlsContainer.visibility = View.GONE }
            .start()
    }

    private val seekIndicatorHandler = Handler(Looper.getMainLooper())
    private val seekIndicatorRunnable = Runnable {
        tvSeekIndicator.animate()
            .alpha(0.0f)
            .setDuration(200)
            .withEndAction { tvSeekIndicator.visibility = View.GONE }
            .start()
    }

    private var pollJob: Job? = null

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_player)

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        controlsContainer = findViewById(R.id.controls_container)
        playPauseIcon = findViewById(R.id.play_pause_icon)
        seekBar = findViewById(R.id.seek_bar)
        textCurrentTime = findViewById(R.id.text_current_time)
        textDuration = findViewById(R.id.text_duration)
        tvSeekIndicator = findViewById(R.id.tv_seek_indicator)

        setupWebView()

        val episodeId = intent.getIntExtra("EPISODE_ID", -1)
        val contentId = intent.getIntExtra("CONTENT_ID", -1)

        viewModel.initPlayer(contentId, episodeId, intent.getStringExtra("SOURCE"))
        observeViewModel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                startPolling()
                view?.evaluateJavascript(
                    """
                    setInterval(function() {
                        window.open = function() { return null; };
                        
                        try {
                            if (typeof jwplayer === 'function') {
                                var p = jwplayer();
                                if (!window.__jwEndedAttached) {
                                    p.on('complete', function() { window.__videoEnded = '1'; });
                                    window.__jwEndedAttached = true;
                                }
                                if (!window.__userHasPaused && p.getState && p.getState() !== 'playing') {
                                    p.play();
                                }
                            }
                        } catch(e) {}
                        
                        var video = document.querySelector('video');
                        if (video) {
                            if (!window.__vidEndedAttached) {
                                video.addEventListener('ended', function() { window.__videoEnded = '1'; });
                                window.__vidEndedAttached = true;
                            }
                            if (!window.__userHasPaused && video.paused) {
                                video.play().catch(function(){});
                            }
                            
                            video.style.width = '100vw';
                            video.style.height = '100vh';
                            video.style.position = 'fixed';
                            video.style.top = '0';
                            video.style.left = '0';
                            video.style.zIndex = '999999';
                            video.style.background = 'black';
                        }
                    }, 500);
                """.trimIndent(), null
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var lastLoadedUrl: String? = null
                    viewModel.uiState.collect { state ->
                        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        if (state.streamUrl != null && state.streamUrl != lastLoadedUrl) {
                            lastLoadedUrl = state.streamUrl
                            webView.evaluateJavascript("window.__videoEnded = '0'; window.__userHasPaused = false;", null)
                            webView.loadUrl(state.streamUrl, state.streamHeaders)
                            webView.requestFocus()
                        }

                        playPauseIcon.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                        if (state.duration > 0) {
                            seekBar.max = state.duration.toInt()
                            seekBar.progress = state.currentTime.toInt()
                            textCurrentTime.text = formatTime(state.currentTime.toInt())
                            textDuration.text = formatTime(state.duration.toInt())
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlayerEvent.ShowToast -> Toast.makeText(this@PlayerActivity, event.message, Toast.LENGTH_SHORT).show()
                            is PlayerEvent.FinishActivity -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                webView.evaluateJavascript(
                    """
                    (function() {
                        var v = document.querySelector('video');
                        var ended = window.__videoEnded || '0';
                        if (v) return v.currentTime + ',' + v.duration + ',' + (v.paused ? '1' : '0') + ',' + ended;
                        return '';
                    })();
                """.trimIndent()
                ) { result ->
                    val cleanResult = result?.replace("\"", "") ?: ""
                    if (cleanResult.isNotEmpty() && cleanResult != "null") {
                        val parts = cleanResult.split(",")
                        if (parts.size >= 4) {
                            val currentTime = parts[0].toFloatOrNull() ?: 0f
                            val duration = parts[1].toFloatOrNull() ?: 0f
                            val paused = parts[2] == "1"
                            val ended = parts[3] == "1"

                            viewModel.updateProgressState(currentTime, duration, paused, ended)
                        }
                    }
                }
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun showControls() {
        controlsContainer.animate().cancel()
        if (controlsContainer.visibility != View.VISIBLE) {
            controlsContainer.alpha = 0f
            controlsContainer.visibility = View.VISIBLE
            controlsContainer.animate().alpha(1.0f).setDuration(200).start()
        }
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3500)
    }

    private fun showSeekIndicator(text: String) {
        tvSeekIndicator.text = text
        tvSeekIndicator.animate().cancel()
        tvSeekIndicator.alpha = 1.0f
        tvSeekIndicator.visibility = View.VISIBLE
        seekIndicatorHandler.removeCallbacks(seekIndicatorRunnable)
        seekIndicatorHandler.postDelayed(seekIndicatorRunnable, 1000)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            showControls()
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    webView.evaluateJavascript(
                        """
                        window.__userHasPaused = true;
                        var v = document.querySelector('video');
                        if (v) { if (v.paused) v.play(); else v.pause(); }
                        try {
                            if (typeof jwplayer === 'function') {
                                var p = jwplayer();
                                if (p.getState() === 'playing') p.pause(); else p.play();
                            }
                        } catch(e) {}
                    """.trimIndent(), null
                    )
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    showSeekIndicator(getString(R.string.seek_rewind_10))
                    webView.evaluateJavascript(
                        """
                        var v = document.querySelector('video');
                        if (v) v.currentTime -= 10;
                    """.trimIndent(), null
                    )
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    showSeekIndicator(getString(R.string.seek_forward_10))
                    webView.evaluateJavascript(
                        """
                        var v = document.querySelector('video');
                        if (v) v.currentTime += 10;
                    """.trimIndent(), null
                    )
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onPlayerDestroy()
        pollJob?.cancel()
        hideHandler.removeCallbacksAndMessages(null)
        seekIndicatorHandler.removeCallbacksAndMessages(null)
        webView.destroy()
    }
}
