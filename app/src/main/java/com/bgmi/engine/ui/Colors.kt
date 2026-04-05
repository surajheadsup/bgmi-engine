package com.bgmi.engine.ui

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.bgmi.engine.R

/**
 * Centralized color helper — resolves theme-aware colors at runtime.
 * Use this instead of hardcoding hex values in Kotlin code.
 */
object Colors {
    // Brand (constant, not theme-dependent)
    const val ACCENT = 0xFF2979FF.toInt()
    const val SUCCESS = 0xFF4ECB71.toInt()
    const val WARNING = 0xFFFFD700.toInt()
    const val DANGER = 0xFFFF6B6B.toInt()
    const val INFO = 0xFF3498DB.toInt()
    const val ORANGE = 0xFFFF8C00.toInt()
    const val PURPLE = 0xFF9B59B6.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    // Theme-aware colors — resolve from theme attributes
    fun surface(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorSurface)
    fun surfaceHigh(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    fun surfaceLow(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorSurfaceContainerLow)
    fun onSurface(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorOnSurface)
    fun onSurfaceVariant(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
    fun outline(ctx: Context) = resolveAttr(ctx, com.google.android.material.R.attr.colorOutline)

    // Selection highlight
    fun selected(ctx: Context): Int {
        return if (ThemeManager.getTheme(ctx) == ThemeManager.LIGHT) 0x22E94560 else 0x22E94560
    }

    fun itemBg(ctx: Context) = surfaceHigh(ctx)

    private fun resolveAttr(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
