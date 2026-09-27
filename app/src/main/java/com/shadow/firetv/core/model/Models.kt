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
    val is_completed: Boolean
)

data class Episode(
    val id: Int,
    val episode_number: Int,
    val title: String,
    val url: String,
    val is_completed: Boolean = false,
    val progress_seconds: Float = 0f,
    val last_watched_at: Double = 0.0
)

data class WatchlistRequest(
    val content_id: Int,
    val is_watchlisted: Boolean
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
    val duration_seconds: Float
)

// Auth Models
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

data class CreateUserRequest(
    val username: String,
    val login_code: String? = null
)

data class UserDto(
    val id: Int,
    val username: String,
    val login_code: String,
    val created_at: Double,
    val is_active: Boolean,
    val active_device_id: String? = null
)

data class TriggerIndexResponse(
    val status: String,
    val source: String? = null
)
