package com.core.common.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {

    @Test
    fun `fromId 正确映射三档模式且未知值回退到跟随系统`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromId("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromId("dark"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId("unknown"))
    }

    @Test
    fun `AppThemeController 切换模式即时更新`() {
        val prev = AppThemeController.mode.value
        try {
            AppThemeController.setMode(AppThemeMode.DARK)
            assertEquals(AppThemeMode.DARK, AppThemeController.mode.value)
            AppThemeController.setMode(AppThemeMode.LIGHT)
            assertEquals(AppThemeMode.LIGHT, AppThemeController.mode.value)
        } finally {
            AppThemeController.setMode(prev)
        }
    }
}
