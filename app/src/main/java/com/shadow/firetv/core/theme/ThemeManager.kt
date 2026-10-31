package com.shadow.firetv.core.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView
import com.shadow.firetv.R

enum class AppTheme(
    val id: String,
    val displayName: String,
    val backgroundResId: Int,
    val primaryColorHex: String
) {
    SOVIET_RED("soviet_red", "Sowjet Rot", R.drawable.bg_radial_red, "#E50914"),
    CLASSIC_DARK("classic_dark", "Klassisch Dunkel", R.drawable.bg_radial_dark, "#333333"),
    CYBERPUNK_BLUE("cyberpunk_blue", "Cyberpunk Blau", R.drawable.bg_radial_blue, "#0088FF"),
    EMERALD_GREEN("emerald_green", "Smaragd Grün", R.drawable.bg_radial_green, "#00CC66"),
    GOLD_ROYAL("gold_royal", "Königliches Gold", R.drawable.bg_radial_gold, "#FFD700");

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

    fun applyTheme(activity: Activity) {
        val theme = getSelectedTheme()
        activity.window?.decorView?.setBackgroundResource(theme.backgroundResId)
    }

    fun getPrimaryColorInt(): Int {
        return try {
            Color.parseColor(getSelectedTheme().primaryColorHex)
        } catch (_: Exception) {
            Color.parseColor("#E50914")
        }
    }

    fun getOnPrimaryColorInt(): Int {
        return if (getSelectedTheme() == AppTheme.CLASSIC_DARK) Color.WHITE else Color.BLACK
    }

    fun applySidebarTheme(view: View, textView: TextView) {
        val primaryColor = getPrimaryColorInt()
        val defaultBgColor = Color.parseColor("#1A1A1A")

        val stateListDrawable = StateListDrawable()

        val focusedDrawable = ColorDrawable(primaryColor)
        val selectedDrawable = ColorDrawable(primaryColor)
        val defaultDrawable = ColorDrawable(defaultBgColor)

        stateListDrawable.addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
        stateListDrawable.addState(intArrayOf(android.R.attr.state_selected), selectedDrawable)
        stateListDrawable.addState(intArrayOf(android.R.attr.state_pressed), focusedDrawable)
        stateListDrawable.addState(intArrayOf(), defaultDrawable)

        view.background = stateListDrawable

        val textColorOnAccent = getOnPrimaryColorInt()

        val textColorDefault = Color.parseColor("#AAAAAA")

        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_selected),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                textColorOnAccent,
                textColorOnAccent,
                textColorOnAccent,
                textColorDefault
            )
        )

        textView.setTextColor(colorStateList)
    }
}
