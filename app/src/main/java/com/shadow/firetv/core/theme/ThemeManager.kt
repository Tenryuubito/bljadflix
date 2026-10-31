package com.shadow.firetv.core.theme

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme(
    val id: String,
    val displayName: String,
    val primaryColorHex: String
) {
    SOVIET_RED("soviet_red", "Sowjet Rot", "#E50914"),
    CLASSIC_DARK("classic_dark", "Klassisch Dunkel", "#333333"),
    CYBERPUNK_BLUE("cyberpunk_blue", "Cyberpunk Blau", "#0088FF"),
    EMERALD_GREEN("emerald_green", "Smaragd Grün", "#00CC66"),
    GOLD_ROYAL("gold_royal", "Königliches Gold", "#FFD700");

    companion object {
        fun fromId(id: String?): AppTheme {
            if (id.isNullOrEmpty()) return SOVIET_RED
            return values().find { it.id.equals(id, ignoreCase = true) } ?: SOVIET_RED
        }
    }
}

class ThemeManager private constructor(private var sharedPreferences: SharedPreferences?) {

    private var memoryTheme: AppTheme? = null

    companion object {
        private const val PREF_NAME = "firetv_theme_prefs"
        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_INTRO_ENABLED = "intro_enabled"

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context? = null): ThemeManager {
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
                instance ?: ThemeManager(
                    context?.applicationContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        fun createForTesting(sharedPreferences: SharedPreferences? = null): ThemeManager {
            return ThemeManager(sharedPreferences)
        }
    }

    fun getSelectedTheme(): AppTheme {
        if (memoryTheme != null) return memoryTheme!!

        val storedId = sharedPreferences?.getString(KEY_THEME_ID, null)
        val theme = AppTheme.fromId(storedId)
        memoryTheme = theme
        return theme
    }

    fun setSelectedTheme(theme: AppTheme) {
        memoryTheme = theme
        sharedPreferences?.edit()?.putString(KEY_THEME_ID, theme.id)?.apply()
    }

    fun isIntroEnabled(): Boolean = sharedPreferences?.getBoolean(KEY_INTRO_ENABLED, true) ?: true

    fun setIntroEnabled(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean(KEY_INTRO_ENABLED, enabled)?.apply()
    }
}
