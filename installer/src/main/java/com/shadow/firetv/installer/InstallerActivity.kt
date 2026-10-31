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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()

        viewModel.checkStatusAndVersions(this, backendBaseUrl)
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            viewModel.checkStatusAndVersions(this, backendBaseUrl)
        }

        binding.btnPrimary.setOnClickListener {
            val state = viewModel.uiState.value
            when {
                state.readyToLaunch -> {
                    launchMainApp()
                }
                state.downloadedApkFile != null -> {
                    triggerApkInstallation(state.downloadedApkFile)
                }
                state.canInstallOrUpdate -> {
                    viewModel.downloadMainAppApk(this, backendBaseUrl)
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvStatus.text = state.statusMessage

                    val serverCode = state.serverVersionInfo?.mainApp?.versionName ?: "-"
                    val installedCode = if (state.installedVersionCode != null) "v${state.installedVersionCode}" else "Nicht installiert"
                    binding.tvVersionInfo.text = "Installiert: $installedCode | Verfügbar: v$serverCode"

                    if (state.isChecking || state.isDownloading) {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.isIndeterminate = state.isChecking
                        if (state.isDownloading) {
                            binding.progressBar.progress = state.downloadProgress
                        }
                    } else {
                        binding.progressBar.visibility = View.GONE
                    }

                    binding.btnPrimary.isEnabled = !state.isChecking && !state.isDownloading

                    val primaryText = when {
                        state.readyToLaunch -> getString(R.string.btn_launch)
                        state.downloadedApkFile != null -> "Jetzt Installieren"
                        state.canInstallOrUpdate -> getString(R.string.btn_install_update)
                        else -> "Nicht Bereit"
                    }
                    binding.btnPrimary.text = primaryText

                    state.errorMessage?.let { msg ->
                        Toast.makeText(this@InstallerActivity, msg, Toast.LENGTH_LONG).show()
                    }

                    if (state.downloadedApkFile != null && !state.isDownloading) {
                        triggerApkInstallation(state.downloadedApkFile)
                    }
                }
            }
        }
    }

    private fun launchMainApp() {
        val targetPackage = "com.shadow.firetv"
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Bljadflix Haupt-App ist nicht auf dem Gerät installiert!", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerApkInstallation(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(this, "APK-Datei nicht gefunden!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "$packageName.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(this, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Starten der Installation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
