package com.bgmi.engine

import android.content.Context
import android.util.Log

/**
 * Handles all Shizuku-based game optimizations.
 * Each feature can be toggled independently via EnginePrefs.
 */
object GameOptimizer {

    private const val TAG = "GameOptimizer"

    // Saved states for restore
    private var savedDns1: String? = null
    private var savedDns2: String? = null
    private var savedTouchRate: String? = null
    private var savedGpuGovernor: String? = null

    // Battery estimator
    private var batteryAtStart = -1
    private var sessionStartMs = 0L
    private val batteryHistory = mutableListOf<Pair<Long, Int>>() // elapsed_ms → percent

    /**
     * Apply all enabled optimizations when game starts.
     */
    fun applyOnGameStart(context: Context) {
        val start = System.currentTimeMillis()
        sessionStartMs = start
        synchronized(batteryHistory) { batteryHistory.clear() }

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "No Shizuku permission — skipping optimizations")
            return
        }

        // Pre-Game: Clear cache + trim memory
        if (EnginePrefs.isPreGameCleanEnabled(context)) {
            applyPreGameClean()
        }

        // Network DNS
        if (EnginePrefs.isDnsOptEnabled(context)) {
            applyDnsOptimizer(context)
        }

        // Touch Boost
        if (EnginePrefs.isTouchBoostEnabled(context)) {
            applyTouchBoost()
        }

        // GPU Governor
        if (EnginePrefs.isGpuBoostEnabled(context)) {
            applyGpuGovernor()
        }

        // Auto DND
        if (EnginePrefs.isAutoDnd(context)) {
            applyDnd(true)
        }

        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "All optimizations applied in ${elapsed}ms")
    }

    /**
     * Restore everything when game exits.
     */
    fun restoreOnGameExit(context: Context) {
        if (!ShizukuManager.hasPermission()) return

        // Always restore everything — don't check toggles
        // (user may have toggled off mid-game, but values are still changed)
        restoreDns()
        restoreTouchRate()
        restoreGpuGovernor()
        applyDnd(false)

        Log.d(TAG, "All optimizations restored")
    }

    /**
     * Kill apps and remove from recents.
     */
    // Never kill these
    private val PROTECTED = setOf(
        "com.bgmi.engine", "com.pubg.imobile", "moe.shizuku.privileged.api",
        "com.android.systemui", "com.android.launcher3"
    )

    fun killApp(packageName: String) {
        if (PROTECTED.contains(packageName)) return
        ShizukuManager.runCommandSilent("am force-stop $packageName")
        ShizukuManager.runCommandSilent("cmd alarm remove-all $packageName")
        ShizukuManager.runCommandSilent("cmd jobscheduler cancel $packageName")
        ShizukuManager.runCommandSilent("cmd notification cancel_all $packageName")
        removeFromRecents(packageName)
    }

    /**
     * Remove app from recents list.
     * Parses dumpsys to find taskId, then removes it.
     */
    fun removeFromRecents(packageName: String) {
        try {
            val result = ShizukuManager.runCommand(
                "dumpsys activity recents | grep 'Recent #' | grep '$packageName'"
            )
            if (!result.success || result.output.isBlank()) return

            // Format: "* Recent #0: Task{abc123 #489 type=standard A=10325:com.example ...}"
            // Extract #NNN (the task/stack ID)
            val taskIds = Regex("#(\\d+)\\s+type=").findAll(result.output)
                .map { it.groupValues[1] }
                .toList()

            for (id in taskIds) {
                ShizukuManager.runCommandSilent("am stack remove $id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeFromRecents failed: ${e.message}")
        }
    }

    /**
     * Kill list of heavy apps + remove from recents.
     */
    fun killHeavyApps() {
        val apps = listOf(
            "com.google.android.youtube", "com.instagram.android",
            "com.facebook.katana", "com.facebook.orca",
            "com.twitter.android", "com.snapchat.android",
            "com.whatsapp", "com.spotify.music",
            "com.netflix.mediaclient", "com.zhiliaoapp.musically",
            "com.amazon.mShop.android.shopping", "com.reddit.frontpage",
            "com.discord", "com.linkedin.android"
        )
        for (pkg in apps) {
            killApp(pkg)
        }
    }

    // --- Pre-Game Clean ---

    private fun applyPreGameClean() {
        Log.d(TAG, "Pre-game clean: trimming memory + clearing caches")
        // Trim memory
        ShizukuManager.runCommandSilent("am send-trim-memory com.pubg.imobile RUNNING_LOW")
        // Drop filesystem caches (frees RAM)
        ShizukuManager.runCommandSilent("sync && echo 3 > /proc/sys/vm/drop_caches")
        // Compact RAM
        ShizukuManager.runCommandSilent("am memory-factor set NORMAL")
    }

    // --- DNS Optimizer ---

    private fun applyDnsOptimizer(context: Context) {
        // Save current private DNS settings
        val curMode = ShizukuManager.runCommand("settings get global private_dns_mode")
        val curHost = ShizukuManager.runCommand("settings get global private_dns_specifier")
        savedDns1 = if (curMode.success) curMode.output.trim() else null
        savedDns2 = if (curHost.success) curHost.output.trim() else null

        val dnsProvider = EnginePrefs.getDnsProvider(context)
        val hostname = when (dnsProvider) {
            "cloudflare" -> "one.one.one.one"
            "google" -> "dns.google"
            "adguard" -> "dns.adguard.com"
            else -> "one.one.one.one"
        }

        // Set Private DNS (Android 9+, works via settings)
        ShizukuManager.runCommandSilent("settings put global private_dns_mode hostname_mode")
        ShizukuManager.runCommandSilent("settings put global private_dns_specifier $hostname")
        Log.d(TAG, "DNS set to $dnsProvider ($hostname)")
    }

    private fun restoreDns() {
        if (savedDns1 != null && savedDns1 != "null") {
            ShizukuManager.runCommandSilent("settings put global private_dns_mode $savedDns1")
        } else {
            ShizukuManager.runCommandSilent("settings put global private_dns_mode opportunistic")
        }
        if (savedDns2 != null && savedDns2 != "null") {
            ShizukuManager.runCommandSilent("settings put global private_dns_specifier $savedDns2")
        }
        Log.d(TAG, "DNS restored")
    }

    // --- Touch Boost ---

    private fun applyTouchBoost() {
        // Save current pointer speed
        val curSpeed = ShizukuManager.runCommand("settings get system pointer_speed")
        savedTouchRate = if (curSpeed.success) curSpeed.output.trim() else "0"

        // Try device-specific touch rate boost paths
        val touchPaths = listOf(
            // Samsung
            "echo 1 > /sys/devices/virtual/sec/tsp/cmd && echo boost_level,2 > /sys/devices/virtual/sec/tsp/cmd",
            // Qualcomm (Vivo/iQOO/OnePlus)
            "echo 1 > /proc/touchpanel/game_switch_enable",
            // Xiaomi/Redmi
            "echo 1 > /sys/touchpanel/double_tap",
            // Generic
            "echo 1 > /sys/class/touch/touch_dev/palm_sensor"
        )
        for (cmd in touchPaths) {
            val r = ShizukuManager.runCommand(cmd)
            if (r.success) {
                Log.d(TAG, "Touch boost applied via: ${cmd.take(40)}...")
                break
            }
        }

        // Pointer speed max (works on all devices)
        ShizukuManager.runCommandSilent("settings put system pointer_speed 7")
        Log.d(TAG, "Touch boost applied")
    }

    private fun restoreTouchRate() {
        // Restore pointer speed
        val speed = savedTouchRate ?: "0"
        ShizukuManager.runCommandSilent("settings put system pointer_speed $speed")

        // Try to disable device-specific touch boost
        ShizukuManager.runCommandSilent("echo 0 > /proc/touchpanel/game_switch_enable")
        Log.d(TAG, "Touch settings restored (pointer_speed=$speed)")
    }

    // --- GPU Boost ---
    // Note: GPU sysfs is restricted on most non-root devices.
    // We use settings-based approach: disable battery saver + adaptive battery

    private fun applyGpuGovernor() {
        // Disable battery saver (ensures max GPU/CPU)
        ShizukuManager.runCommandSilent("settings put global low_power 0")
        ShizukuManager.runCommandSilent("settings put global low_power_sticky 0")
        // Disable adaptive battery (prevents system from throttling)
        ShizukuManager.runCommandSilent("settings put global adaptive_battery_management_enabled 0")

        // Try GPU governor paths (works on some devices)
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/gpu/governor",
            "/sys/kernel/gpu/gpu_governor"
        )
        for (path in paths) {
            val cur = ShizukuManager.runCommand("cat $path 2>/dev/null")
            if (cur.success && cur.output.isNotBlank() && cur.output.trim() != "unknown") {
                savedGpuGovernor = cur.output.trim()
                ShizukuManager.runCommandSilent("echo performance > $path")
                Log.d(TAG, "GPU governor: $savedGpuGovernor → performance via $path")
                break
            }
        }

        Log.d(TAG, "GPU boost applied (battery saver OFF, adaptive OFF)")
    }

    private fun restoreGpuGovernor() {
        // Restore adaptive battery
        ShizukuManager.runCommandSilent("settings put global adaptive_battery_management_enabled 1")

        // Restore GPU governor if we changed it
        if (savedGpuGovernor != null) {
            val paths = listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                "/sys/class/devfreq/gpu/governor",
                "/sys/kernel/gpu/gpu_governor"
            )
            for (path in paths) {
                ShizukuManager.runCommandSilent("echo $savedGpuGovernor > $path")
            }
        }
        Log.d(TAG, "GPU boost restored")
    }

    // --- Auto DND ---
    // Uses zen_mode setting (works on all Android 8+)

    private fun applyDnd(enable: Boolean) {
        if (enable) {
            // zen_mode: 0=off, 1=important only, 2=total silence, 3=alarms only
            ShizukuManager.runCommandSilent("settings put global zen_mode 2")
            Log.d(TAG, "DND enabled (zen_mode=2)")
        } else {
            ShizukuManager.runCommandSilent("settings put global zen_mode 0")
            Log.d(TAG, "DND disabled (zen_mode=0)")
        }
    }

    // --- Battery Drain Estimator ---

    fun initBatteryTracking(batteryPercent: Int) {
        batteryAtStart = batteryPercent
        sessionStartMs = System.currentTimeMillis()
        synchronized(batteryHistory) {
            batteryHistory.clear()
            batteryHistory.add(0L to batteryPercent)
        }
    }

    fun recordBattery(batteryPercent: Int) {
        val elapsed = System.currentTimeMillis() - sessionStartMs
        synchronized(batteryHistory) {
            batteryHistory.add(elapsed to batteryPercent)
            // Keep last 60 readings max
            if (batteryHistory.size > 60) {
                batteryHistory.removeAt(0)
            }
        }
    }

    fun getEstimatedMinutesRemaining(currentBattery: Int): Int {
        synchronized(batteryHistory) {
            if (batteryHistory.size < 3) return -1
            if (batteryAtStart <= 0 || currentBattery <= 0) return -1

            // Calculate drain rate from last N readings
            val oldest = batteryHistory.first()
            val newest = batteryHistory.last()

            val drainPercent = oldest.second - newest.second
            val elapsedMs = newest.first - oldest.first

            if (drainPercent <= 0 || elapsedMs <= 0) return -1

            // Rate: percent per ms
            val drainRatePerMs = drainPercent.toDouble() / elapsedMs
            if (drainRatePerMs <= 0) return -1

            // Time to reach 5%
            val remainingPercent = currentBattery - 5
            if (remainingPercent <= 0) return 0

            val remainingMs = remainingPercent / drainRatePerMs
            return (remainingMs / 60000).toInt()
        }
    }

    fun getDrainPerHour(currentBattery: Int): Double {
        synchronized(batteryHistory) {
            if (batteryHistory.size < 2) return -1.0

            val oldest = batteryHistory.first()
            val newest = batteryHistory.last()
            val drainPercent = oldest.second - newest.second
            val elapsedMs = newest.first - oldest.first

            if (elapsedMs <= 0 || drainPercent <= 0) return -1.0

            return drainPercent.toDouble() / elapsedMs * 3600000.0
        }
    }
}
