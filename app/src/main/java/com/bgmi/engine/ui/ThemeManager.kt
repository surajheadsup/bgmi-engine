package com.bgmi.engine.ui

import android.content.Context
import android.content.SharedPreferences

object ThemeManager {

    private const val PREFS = "bgmi_theme"
    private const val KEY_THEME = "current_theme"

    const val DARK = "dark"
    const val LIGHT = "light"
    const val AMOLED = "amoled"

    fun getTheme(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, DARK) ?: DARK
    }

    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme).apply()
    }

    // Colors for current theme
    data class Colors(
        val background: Int,
        val cardBg: Int,
        val cardBorder: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textMuted: Int,
        val accent: Int,
        val statusBar: Int
    )

    fun getColors(context: Context): Colors {
        return when (getTheme(context)) {
            LIGHT -> Colors(
                background = 0xFFF0F0F5.toInt(),
                cardBg = 0xFFFFFFFF.toInt(),
                cardBorder = 0xFFE0E0E0.toInt(),
                textPrimary = 0xFF1A1A2E.toInt(),
                textSecondary = 0xFF555555.toInt(),
                textMuted = 0xFF999999.toInt(),
                accent = 0xFFE94560.toInt(),
                statusBar = 0xFFF0F0F5.toInt()
            )
            AMOLED -> Colors(
                background = 0xFF000000.toInt(),
                cardBg = 0xFF0A0A0A.toInt(),
                cardBorder = 0xFF1A1A1A.toInt(),
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0xFFAAAAAA.toInt(),
                textMuted = 0xFF555555.toInt(),
                accent = 0xFFE94560.toInt(),
                statusBar = 0xFF000000.toInt()
            )
            else -> Colors( // DARK (default)
                background = 0xFF0F0F1A.toInt(),
                cardBg = 0xFF161625.toInt(),
                cardBorder = 0xFF1E1E35.toInt(),
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0xFFAAAAAA.toInt(),
                textMuted = 0xFF555555.toInt(),
                accent = 0xFFE94560.toInt(),
                statusBar = 0xFF0D0D1A.toInt()
            )
        }
    }
}
