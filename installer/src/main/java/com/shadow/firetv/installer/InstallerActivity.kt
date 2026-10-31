package com.shadow.firetv.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.installer.databinding.ActivityInstallerBinding
import kotlinx.coroutines.launch
import java.io.File

class InstallerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallerBinding
    private val viewModel: InstallerViewModel by viewModels()

    private val backendBaseUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    private var awaitingInstallResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        awaitingInstallResult = savedInstanceState?.getBoolean(KEY_AWAITING_INSTALL) ?: false

        setupListeners()
        observeViewModel()

        if (savedInstanceState == null) {
            checkVersions(AutoMode.FULL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingInstallResult) {
            awaitingInstallResult = false
            checkVersions(AutoMode.LAUNCH_ONLY)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_AWAITING_INSTALL, awaitingInstallResult)
    }

    private fun checkVersions(autoMode: AutoMode) {
        viewModel.checkStatusAndVersions(this, backendBaseUrl, BuildConfig.VERSION_CODE, autoMode)
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener { checkVersions(AutoMode.NONE) }
        binding.btnPrimary.setOnClickListener { viewModel.performPendingAction(this, backendBaseUrl) }
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
                            is InstallerEvent.InstallApk -> triggerApkInstallation(event.file)
                            InstallerEvent.LaunchMainApp -> launchMainApp()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: InstallerUiState) {
        binding.tvStatus.text = state.errorMessage?.let { "${state.statusMessage}\n$it" } ?: state.statusMessage

        val info = state.serverVersionInfo
        val installed = state.installedVersionCode?.let { "Build $it" } ?: "nicht installiert"
        binding.tvVersionInfo.text = getString(
            R.string.version_info,
            installed,
            info?.mainApp?.versionName ?: "-",
            BuildConfig.VERSION_NAME,
            info?.launcher?.versionName ?: "-"
        )

        val busy = state.isChecking || state.isDownloading
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        binding.progressBar.isIndeterminate = state.isChecking
        if (state.isDownloading) {
            binding.progressBar.progress = state.downloadProgress
        }

        val wasEnabled = binding.btnPrimary.isEnabled
        binding.btnPrimary.isEnabled = !busy && state.pendingAction != null
        binding.btnPrimary.text = when {
            state.pendingAction == null -> getString(R.string.btn_not_ready)
            state.downloadedFor == state.pendingAction && state.downloadedApkFile != null -> getString(R.string.btn_install_now)
            state.pendingAction == InstallerAction.UPDATE_LAUNCHER -> getString(R.string.btn_update_launcher)
            state.pendingAction == InstallerAction.INSTALL_MAIN_APP -> getString(R.string.btn_install_update)
            else -> getString(R.string.btn_launch)
        }
        if (!wasEnabled && binding.btnPrimary.isEnabled) {
            binding.btnPrimary.requestFocus()
        } else if (!binding.btnPrimary.isEnabled && !binding.btnRetry.hasFocus()) {
            binding.btnRetry.requestFocus()
        }
    }

    private fun launchMainApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(MAIN_APP_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(this, "Bljadflix Haupt-App ist nicht auf dem Gerät installiert!", Toast.LENGTH_LONG).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        finish()
    }

    private fun triggerApkInstallation(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(this, "APK-Datei nicht gefunden!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val apkUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            awaitingInstallResult = true
            startActivity(installIntent)
        } catch (e: Exception) {
            awaitingInstallResult = false
            Toast.makeText(this, "Fehler beim Starten der Installation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val MAIN_APP_PACKAGE = "com.shadow.firetv"
        private const val KEY_AWAITING_INSTALL = "awaiting_install_result"
    }
}
