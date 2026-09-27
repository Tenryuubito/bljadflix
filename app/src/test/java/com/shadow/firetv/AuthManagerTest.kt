package com.shadow.firetv

import android.content.SharedPreferences
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.model.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthManagerTest {

    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        authManager = AuthManager.createForTesting()
        authManager.clearSession()
    }

    @Test
    fun `saveSession persists user session and sets isLoggedIn to true`() {
        val session = UserSession(token = "test_token", userId = 42, username = "TestUser", deviceId = "device_123")

        authManager.saveSession(session)

        assertTrue(authManager.isLoggedIn())
        assertEquals("test_token", authManager.getToken())
        assertEquals(session, authManager.getSession())
    }

    @Test
    fun `clearSession removes session and sets isLoggedIn to false`() {
        val session = UserSession(token = "test_token", userId = 42, username = "TestUser", deviceId = "device_123")
        authManager.saveSession(session)

        authManager.clearSession()

        assertFalse(authManager.isLoggedIn())
        assertNull(authManager.getSession())
        assertNull(authManager.getToken())
    }

    @Test
    fun `getDeviceId returns non-empty device id`() {
        val deviceId = authManager.getDeviceId()

        assertNotNull(deviceId)
        assertTrue(deviceId.isNotEmpty())
    }

    @Test
    fun `saveSession persists session in sharedPreferences and restores on new instance`() {
        val fakePrefs = FakeSharedPreferences()
        val authManager1 = AuthManager.createForTesting(fakePrefs)
        val session = UserSession(token = "persisted_token", userId = 99, username = "SavedUser", deviceId = "device_99")

        authManager1.saveSession(session)

        val authManager2 = AuthManager.createForTesting(fakePrefs)
        assertTrue(authManager2.isLoggedIn())
        assertEquals("persisted_token", authManager2.getToken())
        assertEquals(session, authManager2.getSession())
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { map[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { map[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
