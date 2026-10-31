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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.core.stream.AdBlocker
import com.shadow.firetv.core.ui.theme.BljadflixTheme
import com.shadow.firetv.feature.player.PlayerEvent
import com.shadow.firetv.feature.player.PlayerOverlay
import com.shadow.firetv.feature.player.PlayerPanel
import com.shadow.firetv.feature.player.PlayerPanelActions
import com.shadow.firetv.feature.player.PlayerUiState
import com.shadow.firetv.feature.player.PlayerViewModel
import com.shadow.firetv.feature.player.SeekAccelerator
import com.shadow.firetv.feature.player.formatPlaybackTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val controlsVisible = mutableStateOf(false)
    private val seekText = mutableStateOf<String?>(null)
    private val panelState = mutableStateOf<PlayerPanel?>(null)
    private val isPageLoading = mutableStateOf(false)
    private val panelMode: PlayerPanel? get() = panelState.value

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { controlsVisible.value = false }

    private val seekIndicatorHandler = Handler(Looper.getMainLooper())
    private val seekIndicatorRunnable = Runnable { seekText.value = null }

    private val seekAccelerator = SeekAccelerator()
    private val seekApplyHandler = Handler(Looper.getMainLooper())
    private val seekApplyRunnable = Runnable {
        seekAccelerator.finish()?.let { seekTo(it) }
    }

    private var pollJob: Job? = null
    private var lastStreamToken = 0
    private var wasLoading = false
    @Volatile
    private var videoStarted = false

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val panelActions = PlayerPanelActions(
            onNext = {
                hidePanel()
                viewModel.skipToNextEpisode()
            },
            onPrevious = {
                hidePanel()
                viewModel.skipToPreviousEpisode()
            },
            onRetry = {
                hidePanel()
                viewModel.retry()
            },
            onOtherHoster = {
                hidePanel()
                viewModel.tryOtherHoster()
            },
            onClose = { if (panelMode == PlayerPanel.ERROR) finish() else hidePanel() }
        )

        setContent {
            BljadflixTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    PlayerOverlay(
                        state = state,
                        controlsVisible = controlsVisible.value,
                        seekText = seekText.value,
                        panel = panelState.value,
                        showSpinner = state.isLoading || (isPageLoading.value && state.errorMessage == null),
                        actions = panelActions
                    )
                }
            }
        }

        setupWebView()

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

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                callback?.onCustomViewHidden()
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
                isPageLoading.value = false
                startPolling()
                view?.evaluateJavascript(INJECTED_JS, null)
            }
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
                                    showSeekIndicator(getString(R.string.player_resumed, formatPlaybackTime(event.seconds.toInt())))
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
            isPageLoading.value = true
            loadStream(streamUrl, state.streamHeaders)
            webView.requestFocus()
        }

        if (state.errorMessage != null && !state.isLoading) {
            if (panelMode != PlayerPanel.ERROR) showPanel(PlayerPanel.ERROR)
        } else if (panelMode == PlayerPanel.ERROR) {
            hidePanel()
        }
    }

    private fun showPanel(mode: PlayerPanel) {
        hideHandler.removeCallbacks(hideRunnable)
        controlsVisible.value = false
        panelState.value = mode
    }

    private fun hidePanel() {
        panelState.value = null
        webView.requestFocus()
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
                        var v = $ACTIVE_VIDEO_JS;
                        var videoState = v ? v.currentTime + ',' + v.duration + ',' + (v.paused ? '1' : '0') + ',' + ended : '';
                        if (v && v.duration > 0) return videoState;
                        try {
                            if (typeof jwplayer === 'function') {
                                var p = jwplayer();
                                if (p.getDuration && p.getDuration() > 0) {
                                    return p.getPosition() + ',' + p.getDuration() + ',' + (p.getState() === 'playing' ? '0' : '1') + ',' + ended;
                                }
                            }
                        } catch(e) {}
                        return videoState;
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
                var v = $ACTIVE_VIDEO_JS;
                if (v) v.currentTime = t;
            })($target);
        """.trimIndent(), null
        )
    }

    private fun pauseVideo() {
        webView.evaluateJavascript(
            """
            window.__userHasPaused = true;
            var v = $ACTIVE_VIDEO_JS;
            if (v) v.pause();
            try {
                if (!v && typeof jwplayer === 'function') jwplayer().pause(true);
            } catch(e) {}
        """.trimIndent(), null
        )
    }

    private fun togglePlayPause() {
        webView.evaluateJavascript(
            """
            window.__userHasPaused = true;
            var v = $ACTIVE_VIDEO_JS;
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
        showSeekIndicator(getString(R.string.seek_indicator, offset, formatPlaybackTime(preview.targetSeconds.toInt())))
        seekApplyHandler.removeCallbacks(seekApplyRunnable)
        seekApplyHandler.postDelayed(seekApplyRunnable, SEEK_APPLY_DELAY_MS)
    }

    private fun showControls() {
        controlsVisible.value = true
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3500)
    }

    private fun showSeekIndicator(text: String) {
        seekText.value = text
        seekIndicatorHandler.removeCallbacks(seekIndicatorRunnable)
        seekIndicatorHandler.postDelayed(seekIndicatorRunnable, 1200)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (panelMode != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK || (keyCode == KeyEvent.KEYCODE_MENU && panelMode == PlayerPanel.OPTIONS)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (panelMode == PlayerPanel.ERROR) finish() else hidePanel()
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
                if (event.repeatCount == 0 && !state.isLoading) showPanel(PlayerPanel.OPTIONS)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (event.repeatCount == 0) viewModel.skipToNextEpisode()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (event.repeatCount == 0) viewModel.skipToPreviousEpisode()
            KeyEvent.KEYCODE_BACK -> {
                if (state.countdownSeconds != null) viewModel.cancelCountdown() else finish()
            }
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveProgress()
    }

    override fun onStart() {
        super.onStart()
        webView.onResume()
    }

    override fun onStop() {
        pauseVideo()
        webView.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
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

        private const val ACTIVE_VIDEO_JS =
            "(window.__bljadflixVideo ? window.__bljadflixVideo() : document.querySelector('video'))"

        private val INJECTED_JS = """
            if (!window.__bljadflixInjected) {
            window.__bljadflixInjected = true;
            window.open = function() { return null; };

            var noFullscreen = function() { return Promise.resolve(); };
            ['requestFullscreen', 'webkitRequestFullscreen', 'webkitRequestFullScreen'].forEach(function(name) {
                try { Element.prototype[name] = noFullscreen; } catch(e) {}
            });
            ['webkitEnterFullscreen', 'webkitEnterFullScreen'].forEach(function(name) {
                try { HTMLVideoElement.prototype[name] = function() {}; } catch(e) {}
            });

            window.__bljadflixVideo = function() {
                var videos = document.querySelectorAll('video');
                var best = null;
                var bestScore = -1;
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    var score = (v.videoWidth > 0 ? 8 : 0) + (v.readyState >= 2 ? 4 : 0) + (v.paused ? 0 : 2) + (v === window.__bljadflixActive ? 1 : 0);
                    if (score > bestScore) { best = v; bestScore = score; }
                }
                return best;
            };

            var VIDEO_PROPS = ['display', 'visibility', 'opacity', 'width', 'height', 'position', 'top', 'left', 'z-index', 'background', 'object-fit', 'transform'];
            var ANCESTOR_PROPS = ['transform', 'filter', 'perspective', 'contain', 'will-change', 'opacity', 'visibility'];

            var releaseVideo = function(v) {
                VIDEO_PROPS.forEach(function(p) { v.style.removeProperty(p); });
                for (var a = v.parentElement; a && a !== document.body && a !== document.documentElement; a = a.parentElement) {
                    ANCESTOR_PROPS.forEach(function(p) { a.style.removeProperty(p); });
                }
            };

            var showVideo = function(video) {
                Array.prototype.forEach.call(document.body.children, function(el) {
                    var keep = el === video || el.contains(video);
                    if (keep && el.hasAttribute('data-bljadflix-hidden')) {
                        el.removeAttribute('data-bljadflix-hidden');
                        el.style.removeProperty('display');
                    } else if (!keep && el.tagName !== 'SCRIPT' && !el.hasAttribute('data-bljadflix-hidden')) {
                        el.setAttribute('data-bljadflix-hidden', '1');
                        el.style.setProperty('display', 'none', 'important');
                    }
                });
                for (var a = video.parentElement; a && a !== document.body && a !== document.documentElement; a = a.parentElement) {
                    a.style.setProperty('transform', 'none', 'important');
                    a.style.setProperty('filter', 'none', 'important');
                    a.style.setProperty('perspective', 'none', 'important');
                    a.style.setProperty('contain', 'none', 'important');
                    a.style.setProperty('will-change', 'auto', 'important');
                    a.style.setProperty('opacity', '1', 'important');
                    a.style.setProperty('visibility', 'visible', 'important');
                }
                video.style.setProperty('display', 'block', 'important');
                video.style.setProperty('visibility', 'visible', 'important');
                video.style.setProperty('opacity', '1', 'important');
                video.style.setProperty('width', '100vw', 'important');
                video.style.setProperty('height', '100vh', 'important');
                video.style.setProperty('position', 'fixed', 'important');
                video.style.setProperty('top', '0', 'important');
                video.style.setProperty('left', '0', 'important');
                video.style.setProperty('z-index', '2147483647', 'important');
                video.style.setProperty('background', 'black', 'important');
                video.style.setProperty('object-fit', 'contain', 'important');
                video.style.setProperty('transform', 'none', 'important');
            };

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

                var video = window.__bljadflixVideo();
                if (!video) return;
                if (!video.__bljadflixEnded) {
                    video.addEventListener('ended', function() { window.__videoEnded = '1'; });
                    video.__bljadflixEnded = true;
                }
                if (!window.__userHasPaused && video.paused) {
                    var played = video.play();
                    if (played && played.catch) played.catch(function(){});
                }
                if (video.readyState < 1 && video.videoWidth === 0) return;
                var previous = window.__bljadflixActive;
                if (previous && previous !== video) releaseVideo(previous);
                window.__bljadflixActive = video;
                showVideo(video);
            }, 500);
            }
        """.trimIndent()

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
