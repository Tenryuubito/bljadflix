package com.shadow.firetv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.stream.AdBlocker
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.feature.player.PlayerEvent
import com.shadow.firetv.feature.player.PlayerUiState
import com.shadow.firetv.feature.player.PlayerViewModel
import com.shadow.firetv.feature.player.SeekAccelerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    private enum class PanelMode { ERROR, OPTIONS }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private lateinit var controlsContainer: FrameLayout
    private lateinit var playPauseIcon: ImageView
    private lateinit var seekBar: ProgressBar
    private lateinit var textCurrentTime: TextView
    private lateinit var textDuration: TextView
    private lateinit var tvSeekIndicator: TextView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerEpisode: TextView

    private lateinit var cardNextEpisode: LinearLayout
    private lateinit var tvNextLabel: TextView
    private lateinit var tvNextTitle: TextView
    private lateinit var tvNextHint: TextView
    private lateinit var pbNextCountdown: ProgressBar

    private lateinit var panelOverlay: FrameLayout
    private lateinit var tvPanelTitle: TextView
    private lateinit var tvPanelMessage: TextView
    private lateinit var btnPanelNext: Button
    private lateinit var btnPanelPrevious: Button
    private lateinit var btnPanelRetry: Button
    private lateinit var btnPanelOtherHoster: Button
    private lateinit var btnPanelClose: Button
    private var panelMode: PanelMode? = null

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

    private val seekAccelerator = SeekAccelerator()
    private val seekApplyHandler = Handler(Looper.getMainLooper())
    private val seekApplyRunnable = Runnable {
        seekAccelerator.finish()?.let { seekTo(it) }
    }

    private var pollJob: Job? = null
    private var isPageLoading = false
    private var lastStreamToken = 0
    private var wasLoading = false
    @Volatile
    private var videoStarted = false

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
        tvPlayerTitle = findViewById(R.id.tv_player_title)
        tvPlayerEpisode = findViewById(R.id.tv_player_episode)

        cardNextEpisode = findViewById(R.id.card_next_episode)
        tvNextLabel = findViewById(R.id.tv_next_label)
        tvNextTitle = findViewById(R.id.tv_next_title)
        tvNextHint = findViewById(R.id.tv_next_hint)
        pbNextCountdown = findViewById(R.id.pb_next_countdown)
        pbNextCountdown.max = PlayerViewModel.COUNTDOWN_SECONDS.toInt()

        panelOverlay = findViewById(R.id.panel_overlay)
        tvPanelTitle = findViewById(R.id.tv_panel_title)
        tvPanelMessage = findViewById(R.id.tv_panel_message)
        btnPanelNext = findViewById(R.id.btn_panel_next)
        btnPanelPrevious = findViewById(R.id.btn_panel_previous)
        btnPanelRetry = findViewById(R.id.btn_panel_retry)
        btnPanelOtherHoster = findViewById(R.id.btn_panel_other_hoster)
        btnPanelClose = findViewById(R.id.btn_panel_close)

        setupWebView()
        setupPanel()

        val episodeId = intent.getIntExtra("EPISODE_ID", -1)
        val contentId = intent.getIntExtra("CONTENT_ID", -1)

        viewModel.initPlayer(contentId, episodeId, intent.getStringExtra("SOURCE"), intent.getStringExtra("TITLE").orEmpty())
        observeViewModel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                return false
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (!AdBlocker.isAdHost(request?.url?.host)) return null
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                val currentHost = view?.url?.let { Uri.parse(it).host }
                return AdBlocker.shouldBlockNavigation(currentHost, request.url.host, request.isRedirect, videoStarted)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == BLANK_PAGE) return
                isPageLoading = false
                updateSpinner(viewModel.uiState.value)
                startPolling()
                view?.evaluateJavascript(
                    """
                    if (!window.__bljadflixInjected) {
                    window.__bljadflixInjected = true;
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
                            Array.prototype.forEach.call(document.body.children, function(el) {
                                if (el !== video && !el.contains(video) && el.tagName !== 'SCRIPT') {
                                    el.style.setProperty('display', 'none', 'important');
                                }
                            });
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
                            video.style.zIndex = '2147483647';
                            video.style.background = 'black';
                        }
                    }, 500);
                    }
                """.trimIndent(), null
                )
            }
        }
    }

    private fun setupPanel() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.03f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
        listOf(btnPanelNext, btnPanelPrevious, btnPanelRetry, btnPanelOtherHoster, btnPanelClose).forEach {
            it.onFocusChangeListener = focusListener
        }

        btnPanelNext.setOnClickListener {
            hidePanel()
            viewModel.skipToNextEpisode()
        }
        btnPanelPrevious.setOnClickListener {
            hidePanel()
            viewModel.skipToPreviousEpisode()
        }
        btnPanelRetry.setOnClickListener {
            hidePanel()
            viewModel.retry()
        }
        btnPanelOtherHoster.setOnClickListener {
            hidePanel()
            viewModel.tryOtherHoster()
        }
        btnPanelClose.setOnClickListener {
            if (panelMode == PanelMode.ERROR) finish() else hidePanel()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlayerEvent.ShowToast -> Toast.makeText(this@PlayerActivity, event.message, Toast.LENGTH_SHORT).show()
                            is PlayerEvent.SeekTo -> {
                                seekTo(event.seconds)
                                if (event.isResume) {
                                    showSeekIndicator(getString(R.string.player_resumed, formatTime(event.seconds.toInt())))
                                }
                            }
                            is PlayerEvent.FinishActivity -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: PlayerUiState) {
        if (state.isLoading && !wasLoading) {
            videoStarted = false
            pollJob?.cancel()
            webView.loadUrl(BLANK_PAGE)
        }
        wasLoading = state.isLoading

        val streamUrl = state.streamUrl
        if (streamUrl != null && state.streamToken != lastStreamToken) {
            lastStreamToken = state.streamToken
            isPageLoading = true
            loadStream(streamUrl, state.streamHeaders)
            webView.requestFocus()
        }
        updateSpinner(state)

        tvPlayerTitle.text = state.seriesTitle
        tvPlayerTitle.visibility = if (state.seriesTitle.isBlank()) View.GONE else View.VISIBLE
        tvPlayerEpisode.text = state.currentEpisode?.let { episodeLabel(it) }.orEmpty()

        playPauseIcon.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        if (state.duration > 0) {
            seekBar.max = state.duration.toInt()
            seekBar.progress = state.currentTime.toInt()
            textCurrentTime.text = formatTime(state.currentTime.toInt())
            textDuration.text = formatTime(state.duration.toInt())
        } else {
            seekBar.progress = 0
            textCurrentTime.text = formatTime(0)
            textDuration.text = formatTime(0)
        }

        renderNextEpisodeCard(state)

        val error = state.errorMessage
        if (error != null && !state.isLoading) {
            if (panelMode != PanelMode.ERROR) showPanel(PanelMode.ERROR)
            tvPanelMessage.text = error
        } else if (panelMode == PanelMode.ERROR) {
            hidePanel()
        }
    }

    private fun updateSpinner(state: PlayerUiState) {
        progressBar.visibility = if (state.isLoading || (isPageLoading && state.errorMessage == null)) View.VISIBLE else View.GONE
    }

    private fun renderNextEpisodeCard(state: PlayerUiState) {
        val next = state.nextEpisode
        if (!state.showNextEpisodeCard || next == null || panelMode != null) {
            cardNextEpisode.visibility = View.GONE
            return
        }
        cardNextEpisode.visibility = View.VISIBLE
        tvNextTitle.text = episodeLabel(next)
        val countdown = state.countdownSeconds
        if (countdown != null) {
            tvNextLabel.text = getString(R.string.player_next_in, countdown)
            tvNextHint.setText(R.string.player_next_hint_countdown)
            pbNextCountdown.visibility = View.VISIBLE
            pbNextCountdown.progress = countdown
        } else {
            tvNextLabel.setText(R.string.player_next_episode)
            tvNextHint.setText(R.string.player_next_hint)
            pbNextCountdown.visibility = View.GONE
        }
    }

    private fun episodeLabel(episode: Episode): String {
        val base = getString(R.string.player_episode_label, episode.season_number, episode.episode_number)
        return if (episode.title.isNullOrBlank()) base else "$base – ${episode.title}"
    }

    private fun showPanel(mode: PanelMode) {
        panelMode = mode
        val state = viewModel.uiState.value
        hideHandler.removeCallbacks(hideRunnable)
        controlsContainer.visibility = View.GONE
        cardNextEpisode.visibility = View.GONE

        if (mode == PanelMode.ERROR) {
            tvPanelTitle.setText(R.string.player_error_title)
            tvPanelMessage.text = state.errorMessage.orEmpty()
            btnPanelNext.visibility = View.GONE
            btnPanelPrevious.visibility = View.GONE
            btnPanelRetry.setText(R.string.player_retry)
            btnPanelClose.setText(R.string.player_back_to_overview)
        } else {
            tvPanelTitle.setText(R.string.player_options_title)
            tvPanelMessage.text = state.currentEpisode?.let { episodeLabel(it) }.orEmpty()
            btnPanelNext.visibility = if (state.nextEpisode != null) View.VISIBLE else View.GONE
            btnPanelPrevious.visibility = if (state.previousEpisode != null) View.VISIBLE else View.GONE
            btnPanelRetry.setText(R.string.player_reload)
            btnPanelClose.setText(R.string.player_continue_watching)
        }
        panelOverlay.visibility = View.VISIBLE
        val firstButton = listOf(btnPanelNext, btnPanelRetry).first { it.visibility == View.VISIBLE }
        firstButton.requestFocus()
    }

    private fun hidePanel() {
        panelMode = null
        panelOverlay.visibility = View.GONE
        webView.requestFocus()
        renderNextEpisodeCard(viewModel.uiState.value)
    }

    private fun loadStream(url: String, headers: Map<String, String>) {
        val path = Uri.parse(url).path.orEmpty().lowercase(Locale.ROOT)
        if (!path.endsWith(".m3u8") && !path.endsWith(".mp4")) {
            webView.loadUrl(url, headers)
            return
        }
        val src = TextUtils.htmlEncode(url)
        val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width\"></head>" +
            "<body style=\"margin:0;background:#000\"><video src=\"$src\" autoplay playsinline " +
            "style=\"width:100vw;height:100vh;background:#000\"></video></body></html>"
        webView.loadDataWithBaseURL(headers["Referer"] ?: url, html, "text/html", "utf-8", null)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(500)
                webView.evaluateJavascript(
                    """
                    (function() {
                        var ended = window.__videoEnded || '0';
                        var v = document.querySelector('video');
                        if (v) return v.currentTime + ',' + v.duration + ',' + (v.paused ? '1' : '0') + ',' + ended;
                        try {
                            if (typeof jwplayer === 'function') {
                                var p = jwplayer();
                                if (p.getDuration && p.getDuration() > 0) {
                                    return p.getPosition() + ',' + p.getDuration() + ',' + (p.getState() === 'playing' ? '0' : '1') + ',' + ended;
                                }
                            }
                        } catch(e) {}
                        return '';
                    })();
                """.trimIndent()
                ) { result ->
                    val cleanResult = result?.replace("\"", "") ?: ""
                    if (cleanResult.isNotEmpty() && cleanResult != "null") {
                        val parts = cleanResult.split(",")
                        if (parts.size >= 4) {
                            val currentTime = parts[0].toFloatOrNull() ?: 0f
                            val duration = parts[1].toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: 0f
                            val paused = parts[2] == "1"
                            val ended = parts[3] == "1"
                            if (duration > 0f) videoStarted = true

                            viewModel.updateProgressState(currentTime, duration, paused, ended)
                        }
                    }
                }
            }
        }
    }

    private fun seekTo(seconds: Float) {
        val target = String.format(Locale.US, "%.2f", seconds)
        webView.evaluateJavascript(
            """
            (function(t) {
                try {
                    if (typeof jwplayer === 'function') {
                        var p = jwplayer();
                        if (p.seek) p.seek(t);
                    }
                } catch(e) {}
                var v = document.querySelector('video');
                if (v) v.currentTime = t;
            })($target);
        """.trimIndent(), null
        )
    }

    private fun togglePlayPause() {
        webView.evaluateJavascript(
            """
            window.__userHasPaused = true;
            var v = document.querySelector('video');
            if (v) { if (v.paused) v.play(); else v.pause(); }
            try {
                if (!v && typeof jwplayer === 'function') {
                    var p = jwplayer();
                    if (p.getState() === 'playing') p.pause(); else p.play();
                }
            } catch(e) {}
        """.trimIndent(), null
        )
    }

    private fun handleSeekKey(direction: Int, baseSeconds: Int, event: KeyEvent) {
        val state = viewModel.uiState.value
        if (state.duration <= 0f) return
        val preview = seekAccelerator.step(
            direction = direction,
            baseSeconds = baseSeconds,
            isRepeat = event.repeatCount > 0,
            currentPosition = state.currentTime,
            duration = state.duration,
            nowMs = SystemClock.uptimeMillis()
        ) ?: return
        val offset = if (preview.offsetSeconds >= 0) {
            getString(R.string.seek_offset_forward, preview.offsetSeconds)
        } else {
            getString(R.string.seek_offset_back, abs(preview.offsetSeconds))
        }
        showSeekIndicator(getString(R.string.seek_indicator, offset, formatTime(preview.targetSeconds.toInt())))
        seekApplyHandler.removeCallbacks(seekApplyRunnable)
        seekApplyHandler.postDelayed(seekApplyRunnable, SEEK_APPLY_DELAY_MS)
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
        seekIndicatorHandler.postDelayed(seekIndicatorRunnable, 1200)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (panelMode != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK || (keyCode == KeyEvent.KEYCODE_MENU && panelMode == PanelMode.OPTIONS)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (panelMode == PanelMode.ERROR) finish() else hidePanel()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        if (keyCode !in HANDLED_KEYS) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val state = viewModel.uiState.value
        if (keyCode != KeyEvent.KEYCODE_BACK) showControls()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount > 0) return true
                if (state.countdownSeconds != null) viewModel.skipToNextEpisode() else togglePlayPause()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.repeatCount == 0) togglePlayPause()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> handleSeekKey(-1, DPAD_SEEK_SECONDS, event)
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleSeekKey(1, DPAD_SEEK_SECONDS, event)
            KeyEvent.KEYCODE_MEDIA_REWIND -> handleSeekKey(-1, MEDIA_SEEK_SECONDS, event)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> handleSeekKey(1, MEDIA_SEEK_SECONDS, event)
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.repeatCount == 0 && state.showNextEpisodeCard) viewModel.skipToNextEpisode()
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0 && !state.isLoading) showPanel(PanelMode.OPTIONS)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (event.repeatCount == 0) viewModel.skipToNextEpisode()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (event.repeatCount == 0) viewModel.skipToPreviousEpisode()
            KeyEvent.KEYCODE_BACK -> {
                if (state.countdownSeconds != null) viewModel.cancelCountdown() else finish()
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onPlayerDestroy()
        pollJob?.cancel()
        hideHandler.removeCallbacksAndMessages(null)
        seekIndicatorHandler.removeCallbacksAndMessages(null)
        seekApplyHandler.removeCallbacksAndMessages(null)
        webView.destroy()
    }

    companion object {
        private const val BLANK_PAGE = "about:blank"
        private const val DPAD_SEEK_SECONDS = 10
        private const val MEDIA_SEEK_SECONDS = 30
        private const val SEEK_APPLY_DELAY_MS = 500L

        private val HANDLED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_BACK
        )
    }
}
