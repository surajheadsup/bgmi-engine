package com.bgmi.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.app.ActivityManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress

object StatsCollector {

    private const val TAG = "StatsCollector"

    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L
    private var lastFpsTimestamp = 0L
    private var lastFrameCount = 0L
    private var lastPingTime = 0L
    private var lastPingResult = -1
    private const val PING_INTERVAL_MS = 60_000L

    data class SystemStats(
        val cpuUsagePercent: Int,
        val ramUsagePercent: Int,
        val ramUsedMb: Long,
        val ramTotalMb: Long,
        val batteryPercent: Int,
        val batteryTempCelsius: Float,
        val isCharging: Boolean,
        val estimatedFps: Int,
        val networkLatencyMs: Int,
        val refreshRate: Int,
        val thermalCelsius: Double,
        val brightnessPercent: Int
    )

    fun collectStats(context: Context): SystemStats {
        return SystemStats(
            cpuUsagePercent = getCpuUsage(),
            ramUsagePercent = getRamUsagePercent(context),
            ramUsedMb = getRamUsedMb(context),
            ramTotalMb = getRamTotalMb(context),
            batteryPercent = getBatteryPercent(context),
            batteryTempCelsius = getBatteryTemperature(context),
            isCharging = isCharging(context),
            estimatedFps = getEstimatedFps(),
            networkLatencyMs = getNetworkLatency(),
            refreshRate = getCurrentRefreshRate(),
            thermalCelsius = ThermalMonitor.getMaxTemperature(),
            brightnessPercent = getBrightnessPercent()
        )
    }

    // --- CPU Usage ---

    private fun getCpuUsage(): Int {
        // Method 1: Direct /proc/stat read
        val directResult = getCpuFromProcStat()
        if (directResult >= 0) return directResult

        // Method 2: Via Shizuku (if direct read is blocked)
        return getCpuViaShizuku()
    }

    private fun getCpuFromProcStat(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()

            if (line == null || !line.startsWith("cpu")) return -1

            parseCpuLine(line)
        } catch (e: Exception) {
            -1
        }
    }

    private fun getCpuViaShizuku(): Int {
        return try {
            val result = ShizukuManager.runCommand("cat /proc/stat | head -1")
            if (!result.success) return -1
            parseCpuLine(result.output.trim())
        } catch (e: Exception) {
            Log.e(TAG, "CPU via Shizuku failed", e)
            -1
        }
    }

    private fun parseCpuLine(line: String): Int {
        if (!line.startsWith("cpu")) return -1
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 5) return -1

        val user = parts[1].toLongOrNull() ?: 0L
        val nice = parts[2].toLongOrNull() ?: 0L
        val system = parts[3].toLongOrNull() ?: 0L
        val idle = parts[4].toLongOrNull() ?: 0L
        val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
        val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
        val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L

        val totalCpu = user + nice + system + idle + iowait + irq + softirq
        val totalIdle = idle + iowait

        if (prevCpuTotal == 0L) {
            prevCpuTotal = totalCpu
            prevCpuIdle = totalIdle
            return 0
        }

        val diffTotal = totalCpu - prevCpuTotal
        val diffIdle = totalIdle - prevCpuIdle

        prevCpuTotal = totalCpu
        prevCpuIdle = totalIdle

        if (diffTotal == 0L) return 0

        val usage = ((diffTotal - diffIdle).toDouble() / diffTotal * 100).toInt()
        return usage.coerceIn(0, 100)
    }

    // --- RAM Usage ---

    private fun getRamUsagePercent(context: Context): Int {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return -1
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedMem = memInfo.totalMem - memInfo.availMem
            ((usedMem.toDouble() / memInfo.totalMem) * 100).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            Log.e(TAG, "RAM usage read failed", e)
            -1
        }
    }

    private fun getRamUsedMb(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return -1
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        } catch (e: Exception) {
            -1
        }
    }

    private fun getRamTotalMb(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return -1
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            -1
        }
    }

    // --- Battery ---

    private fun getBatteryPercent(context: Context): Int {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return -1
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level == -1 || scale == -1 || scale == 0) return -1
            (level * 100 / scale)
        } catch (e: Exception) {
            Log.e(TAG, "Battery percent read failed", e)
            -1
        }
    }

    private fun getBatteryTemperature(context: Context): Float {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return -1f
            val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (temp == -1) -1f else temp / 10f
        } catch (e: Exception) {
            Log.e(TAG, "Battery temp read failed", e)
            -1f
        }
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return false
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    // --- FPS Estimation ---
    // Uses SurfaceFlinger timestats clear-and-read approach.
    // Clear stats → wait for next poll (2s) → read totalFrames / displayOnTime = real FPS.
    // This captures ALL composited frames including BGMI's Unreal Engine output.

    private var timestatsCleared = false

    private fun getEstimatedFps(): Int {
        return try {
            if (!timestatsCleared) {
                // First call: clear stats, next call will read
                ShizukuManager.runCommandSilent("dumpsys SurfaceFlinger --timestats -clear")
                timestatsCleared = true
                return -1
            }

            // Read stats accumulated since last clear
            val result = ShizukuManager.runCommand(
                "dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | head -8"
            )
            if (!result.success || result.output.isBlank()) {
                timestatsCleared = false
                return -1
            }

            // Parse totalFrames and displayOnTime
            val framesMatch = Regex("totalFrames\\s*=\\s*(\\d+)").find(result.output)
            val timeMatch = Regex("displayOnTime\\s*=\\s*(\\d+)").find(result.output)

            val totalFrames = framesMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0
            val displayOnTimeMs = timeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0

            // Clear for next cycle
            ShizukuManager.runCommandSilent("dumpsys SurfaceFlinger --timestats -clear")

            if (totalFrames <= 0 || displayOnTimeMs <= 0) return -1

            // FPS = totalFrames / (displayOnTime in seconds)
            val fps = (totalFrames * 1000.0 / displayOnTimeMs).toInt()
            fps.coerceIn(1, 144)
        } catch (e: Exception) {
            Log.d(TAG, "FPS estimation failed: ${e.message}")
            timestatsCleared = false
            -1
        }
    }

    // --- Network Latency (throttled to every 60s) ---

    private fun getNetworkLatency(): Int {
        val now = System.currentTimeMillis()
        if (now - lastPingTime < PING_INTERVAL_MS && lastPingResult >= 0) {
            return lastPingResult
        }
        return try {
            val startTime = now
            val reachable = InetAddress.getByName("8.8.8.8").isReachable(2000)
            lastPingTime = now
            if (reachable) {
                lastPingResult = (System.currentTimeMillis() - startTime).toInt()
                lastPingResult
            } else {
                lastPingResult = -1
                -1
            }
        } catch (e: Exception) {
            Log.d(TAG, "Network latency check failed: ${e.message}")
            lastPingTime = now
            lastPingResult = -1
            -1
        }
    }

    // --- Refresh Rate ---

    private fun getCurrentRefreshRate(): Int {
        return try {
            // Try Vivo/iQOO key first
            val vivoResult = ShizukuManager.runCommand("settings get global vivo_screen_refresh_rate_mode")
            if (vivoResult.success && vivoResult.output.trim() != "null" && vivoResult.output.trim().isNotEmpty()) {
                val v = vivoResult.output.trim().toIntOrNull()
                if (v != null && v > 0) return v
            }
            // Standard AOSP key
            val result = ShizukuManager.runCommand("settings get system peak_refresh_rate")
            if (result.success && result.output.trim() != "null") {
                val v = result.output.trim().toFloatOrNull()?.toInt()
                if (v != null && v > 0) return v
            }
            // Samsung key
            val samsungResult = ShizukuManager.runCommand("settings get global refresh_rate_mode")
            if (samsungResult.success && samsungResult.output.trim() != "null") {
                val mode = samsungResult.output.trim().toIntOrNull()
                if (mode != null) return when (mode) { 2 -> 120; 1 -> 90; else -> 60 }
            }
            // OnePlus key
            val opResult = ShizukuManager.runCommand("settings get global oneplus_screen_refresh_rate")
            if (opResult.success && opResult.output.trim() != "null") {
                val v = opResult.output.trim().toIntOrNull()
                if (v != null && v > 0) return v
            }
            -1
        } catch (e: Exception) {
            -1
        }
    }

    // --- Brightness ---

    private fun getBrightnessPercent(): Int {
        return try {
            val result = ShizukuManager.runCommand("settings get system screen_brightness")
            if (result.success && result.output.isNotBlank()) {
                val raw = result.output.trim().toIntOrNull() ?: return -1
                // Android brightness is 0-255, convert to 0-100%
                (raw * 100 / 255).coerceIn(0, 100)
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
