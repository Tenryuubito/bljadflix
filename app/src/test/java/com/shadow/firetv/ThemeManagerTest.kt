package com.shadow.firetv

import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemeManagerTest {

    private lateinit var themeManager: ThemeManager

    @Before
    fun setup() {
        themeManager = ThemeManager.createForTesting()
    }

    @Test
    fun `default theme is SOVIET_RED`() {
        assertEquals(AppTheme.SOVIET_RED, themeManager.getSelectedTheme())
    }

    @Test
    fun `setSelectedTheme updates selected theme`() {
        themeManager.setSelectedTheme(AppTheme.CYBERPUNK_BLUE)
        assertEquals(AppTheme.CYBERPUNK_BLUE, themeManager.getSelectedTheme())
    }

    @Test
    fun `AppTheme fromId parses correctly`() {
        assertEquals(AppTheme.CLASSIC_DARK, AppTheme.fromId("classic_dark"))
        assertEquals(AppTheme.EMERALD_GREEN, AppTheme.fromId("emerald_green"))
        assertEquals(AppTheme.GOLD_ROYAL, AppTheme.fromId("gold_royal"))
        assertEquals(AppTheme.SOVIET_RED, AppTheme.fromId("unknown_id"))
    }
}
