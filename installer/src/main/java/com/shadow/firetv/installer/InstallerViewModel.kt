package com.shadow.firetv.installer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.installer.data.AppVersionInfo
import com.shadow.firetv.installer.data.AuthRequest
import com.shadow.firetv.installer.network.InstallerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

data class InstallerUiState(
    val statusMessage: String = "Initialisiere Installer...",
    val isChecking: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val installedVersionCode: Int? = null,
    val serverVersionInfo: AppVersionInfo? = null,
    val isAuthenticated: Boolean = false,
    val showLogin: Boolean = false,
    val errorMessage: String? = null,
    val downloadedApkFile: File? = null,
    val readyToLaunch: Boolean = false,
    val canInstallOrUpdate: Boolean = false
)

class InstallerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private fun getApi(baseUrl: String): InstallerApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InstallerApi::class.java)
    }

    fun checkStatusAndVersions(context: Context, baseUrl: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChecking = true,
                    statusMessage = "Verbinde mit Server ($baseUrl)...",
                    errorMessage = null
                )
            }

            try {
                val api = getApi(baseUrl)
                val response = api.getVersionInfo()

                if (!response.isSuccessful || response.body() == null) {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            statusMessage = "Fehler beim Laden der Versionsinformationen vom Server",
                            errorMessage = "HTTP ${response.code()}"
                        )
                    }
                    return@launch
                }

                val versionInfo = response.body()!!
                val targetPackageName = versionInfo.mainApp.packageName

                val installedCode = getInstalledVersionCode(context, targetPackageName)
                val isInstalled = installedCode != null
                val serverCode = versionInfo.mainApp.versionCode
                val isUpToDate = isInstalled && (installedCode!! >= serverCode)

                val status = when {
                    !isInstalled -> "Bljadflix Client ist NICHT installiert (v${versionInfo.mainApp.versionName} verfügbar)"
                    !isUpToDate -> "Update verfügbar: v${versionInfo.mainApp.versionName} (Installiert: v$installedCode)"
                    else -> "Bljadflix Client v${versionInfo.mainApp.versionName} ist auf dem neuesten Stand"
                }

                _uiState.update {
                    it.copy(
                        isChecking = false,
                        statusMessage = status,
                        installedVersionCode = installedCode,
                        serverVersionInfo = versionInfo,
                        readyToLaunch = isUpToDate,
                        canInstallOrUpdate = !isUpToDate
                    )
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

    private fun getInstalledVersionCode(context: Context, packageName: String): Int? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val pkgInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                pkgInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun loginAndProceed(usernameInput: String, passwordInput: String, baseUrl: String, context: Context) {
        if (usernameInput.isBlank() || passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Bitte Benutzernamen und Passwort eingeben") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, errorMessage = null, statusMessage = "Melde an...") }
            try {
                val api = getApi(baseUrl)
                val resp = api.login(AuthRequest(usernameInput, passwordInput))
                if (resp.isSuccessful && resp.body() != null) {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            showLogin = false,
                            statusMessage = "Anmeldung erfolgreich!"
                        )
                    }
                    checkStatusAndVersions(context, baseUrl)
                } else {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            errorMessage = "Anmeldung fehlgeschlagen: HTTP ${resp.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        errorMessage = "Fehler bei Anmeldung: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun downloadMainAppApk(context: Context, baseUrl: String) {
        val serverInfo = _uiState.value.serverVersionInfo ?: return
        val downloadRelativeUrl = serverInfo.mainApp.downloadUrl

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    statusMessage = "Lade Bljadflix Haupt-App herunter...",
                    errorMessage = null
                )
            }

            try {
                val fullUrl = if (downloadRelativeUrl.startsWith("http")) {
                    downloadRelativeUrl
                } else {
                    val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                    val cleanRel = if (downloadRelativeUrl.startsWith("/")) downloadRelativeUrl else "/$downloadRelativeUrl"
                    "$cleanBase$cleanRel"
                }

                val api = getApi(baseUrl)
                val response = api.downloadApk(fullUrl)

                if (!response.isSuccessful || response.body() == null) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            errorMessage = "Download fehlgeschlagen (HTTP ${response.code()})"
                        )
                    }
                    return@launch
                }

                val body = response.body()!!
                val apkFile = File(context.externalCacheDir ?: context.cacheDir, "bljadflix-main-update.apk")

                withContext(Dispatchers.IO) {
                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile).use { outputStream ->
                            val buffer = ByteArray(8 * 1024)
                            val contentLength = body.contentLength()
                            var bytesRead: Int
                            var totalRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                if (contentLength > 0) {
                                    val progress = ((totalRead * 100) / contentLength).toInt()
                                    _uiState.update { state -> state.copy(downloadProgress = progress) }
                                }
                            }
                            outputStream.flush()
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100,
                        downloadedApkFile = apkFile,
                        statusMessage = "Download abgeschlossen! Bereit zur Installation."
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = "Download-Fehler: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
