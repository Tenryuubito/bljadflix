package com.shadow.firetv.feature.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebStorage
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.shadow.firetv.R
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.feature.auth.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var tvProfileUsername: TextView
    private lateinit var tvProfileUserId: TextView
    private lateinit var tvProfileDeviceId: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var btnSyncUserData: Button
    private lateinit var progressSync: ProgressBar
    private lateinit var tvSyncStatus: TextView
    private lateinit var btnTriggerIndexer: Button
    private lateinit var progressIndexer: ProgressBar
    private lateinit var tvIndexerStatus: TextView
    private lateinit var btnClearCache: Button
    private lateinit var btnToggleIntro: Button
    private lateinit var btnLogout: Button

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory() }
    private var isInitialSourceSet = false
    private var isInitialThemeSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_settings)

        spinnerSource = findViewById(R.id.spinner_source_settings)
        spinnerTheme = findViewById(R.id.spinner_theme_settings)
        tvProfileUsername = findViewById(R.id.tv_profile_username)
        tvProfileUserId = findViewById(R.id.tv_profile_user_id)
        tvProfileDeviceId = findViewById(R.id.tv_profile_device_id)
        tvAppVersion = findViewById(R.id.tv_app_version)
        btnSyncUserData = findViewById(R.id.btn_sync_user_data)
        progressSync = findViewById(R.id.progress_sync)
        tvSyncStatus = findViewById(R.id.tv_sync_status)
        btnTriggerIndexer = findViewById(R.id.btn_trigger_indexer)
        progressIndexer = findViewById(R.id.progress_indexer)
        tvIndexerStatus = findViewById(R.id.tv_indexer_status)
        btnClearCache = findViewById(R.id.btn_clear_cache)
        btnToggleIntro = findViewById(R.id.btn_toggle_intro)
        btnLogout = findViewById(R.id.btn_logout)

        setupSpinners()
        setupButtons()
        applyThemeToHeaders()
        observeViewModel()
    }

    private fun applyThemeToHeaders() {
        val themeManager = ThemeManager.getInstance(this)
        val primaryColor = themeManager.getPrimaryColorInt()
        val headerColor = if (themeManager.getSelectedTheme() == AppTheme.CLASSIC_DARK) {
            Color.parseColor("#AAAAAA")
        } else {
            primaryColor
        }

        findViewById<TextView>(R.id.tv_header_streaming)?.setTextColor(headerColor)
        findViewById<TextView>(R.id.tv_header_profile)?.setTextColor(headerColor)
        findViewById<TextView>(R.id.tv_header_appinfo)?.setTextColor(headerColor)
    }

    private fun setupSpinners() {
        val sources = StreamingSource.values()
        val sourceNames = sources.map { it.displayName }
        val sourceAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, sourceNames)
        sourceAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinnerSource.adapter = sourceAdapter

        spinnerSource.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isInitialSourceSet) {
                    isInitialSourceSet = true
                    return
                }
                if (position >= 0 && position < sources.size) {
                    viewModel.onSourceSelected(sources[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val themes = AppTheme.values()
        val themeNames = themes.map { it.displayName }
        val themeAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, themeNames)
        themeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinnerTheme.adapter = themeAdapter

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isInitialThemeSet) {
                    isInitialThemeSet = true
                    return
                }
                if (position >= 0 && position < themes.size) {
                    viewModel.onThemeSelected(themes[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val interpolator = FastOutSlowInInterpolator()
            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).setInterpolator(interpolator).setDuration(120).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setInterpolator(interpolator).setDuration(120).start()
            }
        }

        btnSyncUserData.onFocusChangeListener = focusListener
        btnTriggerIndexer.onFocusChangeListener = focusListener
        btnClearCache.onFocusChangeListener = focusListener
        btnToggleIntro.onFocusChangeListener = focusListener
        btnLogout.onFocusChangeListener = focusListener

        btnSyncUserData.setOnClickListener {
            viewModel.syncUserContent()
        }

        btnTriggerIndexer.setOnClickListener {
            viewModel.triggerIndexer()
        }

        btnToggleIntro.setOnClickListener {
            viewModel.toggleIntro()
        }

        btnClearCache.setOnClickListener {
            clearAppCache()
        }

        btnLogout.setOnClickListener {
            viewModel.logout()
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SettingsEvent.ShowToast -> Toast.makeText(this@SettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                            is SettingsEvent.ThemeChanged -> {
                                ThemeManager.getInstance(this@SettingsActivity).applyTheme(this@SettingsActivity)
                                applyThemeToHeaders()
                            }
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
    }

    private fun renderState(state: SettingsUiState) {
        val sources = StreamingSource.values()
        val sourceIndex = sources.indexOf(state.selectedSource)
        if (sourceIndex != -1 && spinnerSource.selectedItemPosition != sourceIndex) {
            spinnerSource.setSelection(sourceIndex, false)
        }

        val themes = AppTheme.values()
        val themeIndex = themes.indexOf(state.selectedTheme)
        if (themeIndex != -1 && spinnerTheme.selectedItemPosition != themeIndex) {
            spinnerTheme.setSelection(themeIndex, false)
        }

        btnToggleIntro.text = getString(if (state.isIntroEnabled) R.string.settings_intro_on else R.string.settings_intro_off)

        tvProfileUsername.text = state.username
        tvProfileUserId.text = if (state.userId != -1) "#${state.userId}" else "-"
        tvProfileDeviceId.text = state.deviceId
        tvAppVersion.text = state.appVersion

        btnSyncUserData.isEnabled = !state.isSyncing
        progressSync.visibility = if (state.isSyncing) View.VISIBLE else View.GONE
        if (state.syncStatusMessage != null) {
            tvSyncStatus.visibility = View.VISIBLE
            tvSyncStatus.text = state.syncStatusMessage
        } else {
            tvSyncStatus.visibility = View.GONE
        }

        btnTriggerIndexer.isEnabled = !state.isIndexing
        progressIndexer.visibility = if (state.isIndexing) View.VISIBLE else View.GONE
        if (state.indexingStatusMessage != null) {
            tvIndexerStatus.visibility = View.VISIBLE
            tvIndexerStatus.text = state.indexingStatusMessage
        } else {
            tvIndexerStatus.visibility = View.GONE
        }
    }
}
