package com.bgmi.engine

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class GameDetector(private val context: Context) {

    companion object {
        private const val TAG = "GameDetector"
        const val BGMI_PACKAGE = "com.pubg.imobile"
    }

    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    fun isBgmiInForeground(): Boolean {
        return try {
            val foregroundPackage = getForegroundPackage()
            foregroundPackage == BGMI_PACKAGE
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting foreground app", e)
            false
        }
    }

    fun hasUsageAccess(): Boolean {
        return try {
            val manager = usageStatsManager ?: return false
            val now = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000,
                now
            )
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Usage access check failed", e)
            false
        }
    }

    private fun getForegroundPackage(): String? {
        val manager = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000,
            now
        )
        if (stats.isNullOrEmpty()) return null

        return stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}
