package com.shadow.firetv.installer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.installer.data.AppVersionInfo
import com.shadow.firetv.installer.network.InstallerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

enum class InstallerAction {
    UPDATE_LAUNCHER,
    INSTALL_MAIN_APP,
    LAUNCH_MAIN_APP
}

enum class AutoMode {
    FULL,
    LAUNCH_ONLY,
    NONE
}

sealed interface InstallerEvent {
    data class InstallApk(val file: File) : InstallerEvent
    object LaunchMainApp : InstallerEvent
}

data class InstallerUiState(
    val statusMessage: String = "Initialisiere Installer...",
    val isChecking: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val installedVersionCode: Int? = null,
    val serverVersionInfo: AppVersionInfo? = null,
    val errorMessage: String? = null,
    val downloadedApkFile: File? = null,
    val downloadedFor: InstallerAction? = null,
    val downloadedVersionCode: Int? = null,
    val pendingAction: InstallerAction? = null,
    val readyToLaunch: Boolean = false,
    val canInstallOrUpdate: Boolean = false
)

class InstallerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InstallerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<InstallerEvent> = _events.asSharedFlow()

    private fun getApi(baseUrl: String): InstallerApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InstallerApi::class.java)
    }

    fun checkStatusAndVersions(
        context: Context,
        baseUrl: String,
        launcherVersionCode: Int,
        autoMode: AutoMode = AutoMode.NONE
    ) {
        if (_uiState.value.isDownloading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChecking = true,
                    statusMessage = "Verbinde mit Server ($baseUrl)...",
                    errorMessage = null
                )
            }

            try {
                val response = getApi(baseUrl).getVersionInfo()
                val versionInfo = response.body()

                if (!response.isSuccessful || versionInfo == null) {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            statusMessage = "Fehler beim Laden der Versionsinformationen vom Server",
                            errorMessage = "HTTP ${response.code()}"
                        )
                    }
                    return@launch
                }

                val installedCode = getInstalledVersionCode(context, versionInfo.mainApp.packageName)
                val action = decideAction(versionInfo, launcherVersionCode, installedCode)

                _uiState.update {
                    it.copy(
                        isChecking = false,
                        statusMessage = statusFor(action, versionInfo, installedCode),
                        installedVersionCode = installedCode,
                        serverVersionInfo = versionInfo,
                        pendingAction = action,
                        readyToLaunch = action == InstallerAction.LAUNCH_MAIN_APP,
                        canInstallOrUpdate = action != InstallerAction.LAUNCH_MAIN_APP
                    )
                }

                val runAutomatically = when (autoMode) {
                    AutoMode.FULL -> true
                    AutoMode.LAUNCH_ONLY -> action == InstallerAction.LAUNCH_MAIN_APP
                    AutoMode.NONE -> false
                }
                if (runAutomatically) {
                    performPendingAction(context, baseUrl)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        statusMessage = "Verbindungsfehler zum Backend Server",
                        errorMessage = e.localizedMessage ?: "Unbekannter Netzwerkfehler"
                    )
                }
            }
        }
    }

    fun performPendingAction(context: Context, baseUrl: String) {
        val state = _uiState.value
        if (state.isChecking || state.isDownloading) return
        val action = state.pendingAction ?: return
        val info = state.serverVersionInfo ?: return
        state.downloadedApkFile?.takeIf {
            state.downloadedFor == action && state.downloadedVersionCode == targetVersionCode(action, info) && it.exists()
        }?.let {
            _events.tryEmit(InstallerEvent.InstallApk(it))
            return
        }
        when (action) {
            InstallerAction.UPDATE_LAUNCHER -> downloadApk(
                context,
                baseUrl,
                action,
                info.launcher.downloadUrl ?: DEFAULT_LAUNCHER_DOWNLOAD_URL,
                LAUNCHER_APK_NAME,
                "Lade neuen Bljadflix Launcher v${info.launcher.versionName} herunter..."
            )
            InstallerAction.INSTALL_MAIN_APP -> downloadApk(
                context,
                baseUrl,
                action,
                info.mainApp.downloadUrl,
                MAIN_APP_APK_NAME,
                "Lade Bljadflix v${info.mainApp.versionName} herunter..."
            )
            InstallerAction.LAUNCH_MAIN_APP -> _events.tryEmit(InstallerEvent.LaunchMainApp)
        }
    }

    fun onInstallFailed(message: String) {
        _uiState.update { it.copy(statusMessage = message, errorMessage = null) }
    }

    private fun getInstalledVersionCode(context: Context, packageName: String): Int? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val pkgInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(packageName, 0)
                pkgInfo.longVersionCode.toInt()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun downloadApk(
        context: Context,
        baseUrl: String,
        action: InstallerAction,
        downloadUrl: String,
        fileName: String,
        message: String
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    statusMessage = message,
                    errorMessage = null
                )
            }

            try {
                val response = getApi(baseUrl).downloadApk(resolveUrl(baseUrl, downloadUrl))
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            statusMessage = "Download fehlgeschlagen",
                            errorMessage = "HTTP ${response.code()}"
                        )
                    }
                    return@launch
                }

                val apkFile = File(context.externalCacheDir ?: context.cacheDir, fileName)

                withContext(Dispatchers.IO) {
                    apkFile.delete()
                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile).use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            val contentLength = body.contentLength()
                            var totalRead = 0L
                            var lastProgress = -1
                            while (true) {
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) break
                                outputStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = ((totalRead * 100) / contentLength).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        _uiState.update { state -> state.copy(downloadProgress = progress) }
                                    }
                                }
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100,
                        downloadedApkFile = apkFile,
                        downloadedFor = action,
                        downloadedVersionCode = it.serverVersionInfo?.let { info -> targetVersionCode(action, info) },
                        statusMessage = "Download abgeschlossen. Bitte Installation bestätigen."
                    )
                }
                _events.tryEmit(InstallerEvent.InstallApk(apkFile))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        statusMessage = "Download fehlgeschlagen",
                        errorMessage = e.localizedMessage ?: "Unbekannter Netzwerkfehler"
                    )
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_LAUNCHER_DOWNLOAD_URL = "/app/download/launcher"
        private const val LAUNCHER_APK_NAME = "bljadflix-launcher-update.apk"
        private const val MAIN_APP_APK_NAME = "bljadflix-main-update.apk"

        fun decideAction(
            info: AppVersionInfo,
            launcherVersionCode: Int,
            installedMainVersionCode: Int?
        ): InstallerAction = when {
            info.launcher.versionCode > launcherVersionCode -> InstallerAction.UPDATE_LAUNCHER
            installedMainVersionCode == null || installedMainVersionCode < info.mainApp.versionCode ->
                InstallerAction.INSTALL_MAIN_APP
            else -> InstallerAction.LAUNCH_MAIN_APP
        }

        fun targetVersionCode(action: InstallerAction, info: AppVersionInfo): Int? = when (action) {
            InstallerAction.UPDATE_LAUNCHER -> info.launcher.versionCode
            InstallerAction.INSTALL_MAIN_APP -> info.mainApp.versionCode
            InstallerAction.LAUNCH_MAIN_APP -> null
        }

        fun resolveUrl(baseUrl: String, downloadUrl: String): String {
            if (downloadUrl.startsWith("http")) return downloadUrl
            return baseUrl.trimEnd('/') + "/" + downloadUrl.trimStart('/')
        }

        private fun statusFor(action: InstallerAction, info: AppVersionInfo, installedCode: Int?): String =
            when (action) {
                InstallerAction.UPDATE_LAUNCHER ->
                    "Neuer Launcher verfügbar: v${info.launcher.versionName}"
                InstallerAction.INSTALL_MAIN_APP ->
                    if (installedCode == null) {
                        "Bljadflix ist nicht installiert (v${info.mainApp.versionName} verfügbar)"
                    } else {
                        "Update verfügbar: v${info.mainApp.versionName} (installiert: Build $installedCode)"
                    }
                InstallerAction.LAUNCH_MAIN_APP ->
                    "Bljadflix v${info.mainApp.versionName} ist auf dem neuesten Stand"
            }
    }
}
