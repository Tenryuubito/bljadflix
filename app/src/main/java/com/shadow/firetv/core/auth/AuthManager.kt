package com.shadow.firetv.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.shadow.firetv.core.model.UserSession
import java.util.UUID

class AuthManager private constructor(private var sharedPreferences: SharedPreferences?) {

    private var memorySession: UserSession? = null
    private var memoryDeviceId: String? = null

    companion object {
        private const val PREF_NAME = "firetv_auth_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DEVICE_ID = "device_id"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context? = null): AuthManager {
            val current = instance
            if (current != null) {
                if (context != null && current.sharedPreferences == null) {
                    synchronized(this) {
                        if (current.sharedPreferences == null) {
                            current.sharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        }
                    }
                }
                return current
            }
            return synchronized(this) {
                instance ?: AuthManager(
                    context?.applicationContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        fun createForTesting(sharedPreferences: SharedPreferences? = null): AuthManager {
            return AuthManager(sharedPreferences)
        }
    }

    fun getDeviceId(context: Context? = null): String {
        if (memoryDeviceId != null) return memoryDeviceId!!

        val storedDeviceId = sharedPreferences?.getString(KEY_DEVICE_ID, null)
        if (!storedDeviceId.isNullOrEmpty()) {
            memoryDeviceId = storedDeviceId
            return storedDeviceId
        }

        val deviceId = if (context != null) {
            try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: UUID.randomUUID().toString()
            } catch (e: Exception) {
                UUID.randomUUID().toString()
            }
        } else {
            UUID.randomUUID().toString()
        }

        sharedPreferences?.edit()?.putString(KEY_DEVICE_ID, deviceId)?.apply()
        memoryDeviceId = deviceId
        return deviceId
    }

    fun saveSession(session: UserSession) {
        memorySession = session
        memoryDeviceId = session.deviceId
        sharedPreferences?.edit()?.apply {
            putString(KEY_TOKEN, session.token)
            putInt(KEY_USER_ID, session.userId)
            putString(KEY_USERNAME, session.username)
            if (session.deviceId.isNotEmpty()) {
                putString(KEY_DEVICE_ID, session.deviceId)
            }
            apply()
        }
    }

    fun getSession(): UserSession? {
        if (memorySession != null) return memorySession

        val prefs = sharedPreferences ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val storedDeviceId = prefs.getString(KEY_DEVICE_ID, "") ?: ""

        if (token.isEmpty() || userId == -1) return null

        val deviceId = if (storedDeviceId.isNotEmpty()) storedDeviceId else getDeviceId()

        val session = UserSession(token = token, userId = userId, username = username, deviceId = deviceId)
        memorySession = session
        return session
    }

    fun getToken(): String? {
        return getSession()?.token
    }

    fun isLoggedIn(): Boolean {
        return getSession() != null
    }

    fun clearSession() {
        memorySession = null
        sharedPreferences?.edit()?.apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            apply()
        }
    }
}
