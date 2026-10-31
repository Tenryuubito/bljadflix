package com.shadow.firetv.core.model

data class Movie(
    val id: Int,
    val title: String,
    val description: String,
    val cover_url: String,
    val source_page_url: String,
    val genre: String? = null,
    val source: String? = null
)

data class StreamResponse(
    val stream_url: String,
    val headers: Map<String, String>
)

data class SyncStatusResponse(
    val is_syncing: Boolean
)

data class ProgressRequest(
    val episode_id: Int,
    val content_id: Int,
    val progress_seconds: Float,
    val duration_seconds: Float,
    val is_completed: Boolean,
    val source: String? = null
)

data class Episode(
    val id: Int,
    val episode_number: Int,
    val title: String,
    val url: String,
    val is_completed: Boolean = false,
    val progress_seconds: Float = 0f,
    val duration_seconds: Float = 0f,
    val last_watched_at: Double = 0.0,
    val episode_url: String? = null
)

data class WatchlistRequest(
    val content_id: Int,
    val is_watchlisted: Boolean,
    val source: String? = null
)

data class WatchlistStatus(
    val is_watchlisted: Boolean
)

data class ContinueWatchingItem(
    val id: Int,
    val title: String,
    val description: String?,
    val cover_url: String?,
    val genre: String?,
    val episode_id: Int,
    val season_number: Int,
    val episode_number: Int,
    val episode_title: String?,
    val progress_seconds: Float,
    val duration_seconds: Float,
    val source: String? = null
)

data class LoginRequest(
    val username: String? = null,
    val password: String? = null,
    val code: String? = null,
    val device_id: String,
    val device_name: String = "Fire TV Stick"
)

data class LoginResponse(
    val token: String,
    val user_id: Int,
    val username: String,
    val device_id: String
)

data class UserSession(
    val token: String,
    val userId: Int,
    val username: String,
    val deviceId: String
)

data class AuthStatusResponse(
    val is_valid: Boolean,
    val active_device_id: String? = null,
    val message: String? = null
)

data class TriggerIndexResponse(
    val status: String,
    val source: String? = null
)

data class AppVersionResponse(
    val mainApp: MainAppVersion
)

data class MainAppVersion(
    val versionCode: Int,
    val versionName: String,
    val changelog: String? = null
)
