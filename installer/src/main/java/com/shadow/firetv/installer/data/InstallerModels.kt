package com.shadow.firetv.installer.data

data class AppVersionInfo(
    val launcher: ComponentVersion,
    val mainApp: MainAppVersion
)

data class ComponentVersion(
    val versionCode: Int,
    val versionName: String,
    val packageName: String,
    val downloadUrl: String? = null
)

data class MainAppVersion(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val minLauncherVersionCode: Int,
    val changelog: String?
)
