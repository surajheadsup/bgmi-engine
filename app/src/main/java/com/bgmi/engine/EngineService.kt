package com.bgmi.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bgmi.engine.data.SessionTracker

class EngineService : Service() {

    companion object {
        private const val TAG = "EngineService"
        private const val CHANNEL_ID = "bgmi_engine_pro_channel"
        private const val NOTIFICATION_ID = 1
        private const val DETECTION_INTERVAL_IDLE = 10_000L
        private const val DETECTION_INTERVAL_ACTIVE = 5_000L
        private const val HEAVY_TASK_INTERVAL = 120_000L
        private const val STATS_UPDATE_INTERVAL = 2_000L
        private const val BATTERY_LOW_THRESHOLD = 15

        private val KILLABLE_PACKAGES = listOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.twitter.android",
            "com.snapchat.android",
            "com.amazon.mShop.android.shopping",
            "com.spotify.music",
            "com.netflix.mediaclient",
            "com.zhiliaoapp.musically"
        )

        @Volatile var isRunning = false
            private set
        @Volatile var sessionTimeMs = 0L
            private set
        @Volatile var thermalHits = 0
            private set
        @Volatile var stabilizerRuns = 0
            private set
        @Volatile var performanceScore = 100
            private set
        @Volatile var currentMode: EngineMode = EngineMode.EXTREME
            private set
        @Volatile var latestStats: StatsCollector.SystemStats? = null
            private set

        // Manual override — when enabled, auto mode switching is disabled
        // and device stays locked at 120Hz + performance mode always on
        @Volatile var manualOverride = false
            private set

        fun setManualOverride(enabled: Boolean) {
            manualOverride = enabled
            Log.d(TAG, "Manual override ${if (enabled) "ENABLED" else "DISABLED"}")
        }

        fun killBackgroundAppsNow() {
            if (!ShizukuManager.hasPermission()) return
            GameOptimizer.killHeavyApps()
            Log.d(TAG, "Manual kill BG apps triggered from overlay")
        }
    }

    private lateinit var gameDetector: GameDetector
    private var overlayManager: OverlayManager? = null
    private var voiceAlertManager: VoiceAlertManager? = null
    private var sessionTracker: SessionTracker? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private var gameWasRunning = false
    private var sessionStartTime = 0L
    private var lastHeavyTaskTime = 0L
    private var previousMode: EngineMode? = null
    private var batteryLowAlerted = false
    private var originalRefreshRate = 120
    private var gameLastSeenTime = 0L  // For session debounce
    private var sessionActive = false  // True once a session has started
    private val SESSION_DEBOUNCE_MS = 60_000L  // 60 seconds — BGMI loading/matchmaking can take a while

    private val detectionRunnable = object : Runnable {
        override fun run() {
            performDetectionCycle()
            val interval = if (gameWasRunning) DETECTION_INTERVAL_ACTIVE else DETECTION_INTERVAL_IDLE
            mainHandler.postDelayed(this, interval)
        }
    }

    private val statsRunnable = object : Runnable {
        override fun run() {
            if (gameWasRunning) {
                collectAndUpdateStats()
            }
            bgHandler?.postDelayed(this, STATS_UPDATE_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        gameDetector = GameDetector(this)
        ShizukuManager.init()
        createNotificationChannel()

        // Background thread for stats collection (network latency, etc.)
        bgThread = HandlerThread("EngineStats").apply { start() }
        bgHandler = Handler(bgThread!!.looper)

        // Initialize overlay
        if (Settings.canDrawOverlays(this)) {
            overlayManager = OverlayManager(this)
        } else {
            Log.w(TAG, "Overlay permission not granted — dashboard will not show")
        }

        // Initialize voice alerts
        voiceAlertManager = VoiceAlertManager(this)

        // Initialize session tracker
        sessionTracker = SessionTracker(this)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_text_idle)))
        isRunning = true
        resetSessionStats()

        // Show overlay immediately when engine starts
        mainHandler.post { overlayManager?.show() }

        mainHandler.post(detectionRunnable)
        bgHandler?.post(statsRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        mainHandler.removeCallbacks(detectionRunnable)
        bgHandler?.removeCallbacks(statsRunnable)

        if (gameWasRunning) {
            restoreSettings()
            gameWasRunning = false
        }

        overlayManager?.hide()
        overlayManager = null
        voiceAlertManager?.shutdown()
        voiceAlertManager = null
        ShizukuManager.destroy()

        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null

        isRunning = false
        super.onDestroy()
    }

    // --- Detection Cycle ---

    private fun performDetectionCycle() {
        try {
            val bgmiRunning = gameDetector.isBgmiInForeground()
            val now = System.currentTimeMillis()

            if (bgmiRunning) {
                gameLastSeenTime = now

                if (!sessionActive) {
                    // First detection or new session after long absence
                    onGameStarted()
                    sessionActive = true
                    gameWasRunning = true
                } else if (!gameWasRunning) {
                    // Was briefly gone, now back — just resume
                    Log.d(TAG, "BGMI back — resuming session")
                    gameWasRunning = true
                } else {
                    // Normal running tick
                    onGameRunning()
                }
            } else {
                // BGMI not in foreground
                if (sessionActive) {
                    val goneFor = now - gameLastSeenTime
                    if (gameLastSeenTime > 0 && goneFor < SESSION_DEBOUNCE_MS) {
                        // Within 60s debounce — keep session alive, just mark not running
                        gameWasRunning = false
                    } else {
                        // Gone for > 60s — end session
                        Log.d(TAG, "BGMI gone for ${goneFor/1000}s — ending session")
                        onGameStopped()
                        sessionActive = false
                        gameWasRunning = false
                        gameLastSeenTime = 0
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detection cycle error", e)
        }
    }

    private fun onGameStarted() {
        Log.d(TAG, "BGMI started — applying optimizations")
        sessionStartTime = System.currentTimeMillis()
        lastHeavyTaskTime = sessionStartTime
        previousMode = null
        batteryLowAlerted = false
        currentMode = EngineMode.EXTREME
        performanceScore = 100
        EngineMode.resetState() // Clear temp history + cooldown

        updateNotification(getString(R.string.notification_text_active))

        // Start session tracking
        val battery = latestStats?.batteryPercent ?: -1
        bgHandler?.post {
            sessionTracker?.startSession(battery)
            sessionTracker?.logEvent("INFO", "BGMI detected — session started")
        }

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted, skipping optimizations")
            speakIfEnabled { voiceAlertManager?.alertShizukuError() }
            overlayManager?.showWarning("No Shizuku")
            return
        }

        // Save original refresh rate before changing
        val curRate = ShizukuManager.runCommand("settings get system peak_refresh_rate")
        if (curRate.success) {
            originalRefreshRate = curRate.output.trim().toFloatOrNull()?.toInt() ?: 120
        }

        // Apply initial extreme settings (120Hz + performance mode)
        applyExtremeSettings()

        // Apply all game optimizations (DNS, touch, resolution, GPU, DND, pre-clean)
        bgHandler?.post {
            GameOptimizer.applyOnGameStart(this)
            sessionTracker?.logEvent("INFO", "Game optimizations applied")
        }

        // Init battery estimator
        GameOptimizer.initBatteryTracking(battery)

        // Kill heavy background apps
        killHeavyBackgroundApps()

        if (manualOverride) {
            updateNotification("Manual Override — 120Hz Locked")
            speakIfEnabled { voiceAlertManager?.alertModeChange("Manual Override") }
        } else {
            speakIfEnabled { voiceAlertManager?.alertModeChange("Extreme") }
        }
    }

    private fun onGameRunning() {
        val now = System.currentTimeMillis()
        sessionTimeMs = now - sessionStartTime

        if (manualOverride) {
            // Manual override active — skip auto mode switching
            // Ensure 120Hz + performance mode stays locked
            if (currentMode != EngineMode.EXTREME) {
                currentMode = EngineMode.EXTREME
                performanceScore = 100
                applyExtremeSettings()
                overlayManager?.updateMode(EngineMode.EXTREME)
                updateNotification("Manual Override — 120Hz Locked")
            }
        } else {
            // Auto mode — switch based on smoothed temperature (with hysteresis + cooldown)
            val temp = ThermalMonitor.getMaxTemperature()
            val newMode = EngineMode.fromTemperature(this, temp, currentMode)
            if (newMode != currentMode) {
                onModeChanged(newMode)
            }
        }

        // Heavy tasks every 2 minutes
        if (now - lastHeavyTaskTime >= HEAVY_TASK_INTERVAL) {
            lastHeavyTaskTime = now
            performHeavyTasks()
        }
    }

    private fun onGameStopped() {
        Log.d(TAG, "BGMI stopped — restoring settings")
        sessionTimeMs = System.currentTimeMillis() - sessionStartTime

        restoreSettings()
        updateNotification(getString(R.string.notification_text_idle))

        // Restore all game optimizations
        bgHandler?.post { GameOptimizer.restoreOnGameExit(this) }

        // Save session to database
        val endBattery = latestStats?.batteryPercent ?: -1
        bgHandler?.post {
            sessionTracker?.endSession(thermalHits, stabilizerRuns, performanceScore, endBattery)
        }
        // Overlay stays visible — only hides when engine stops
    }

    private fun setRefreshRate(hz: Int) {
        // Vivo/iQOO specific
        ShizukuManager.runCommandSilent("settings put global vivo_screen_refresh_rate_mode $hz")
        // Standard AOSP keys (Pixel, Samsung, OnePlus, Xiaomi)
        ShizukuManager.runCommandSilent("settings put system peak_refresh_rate ${hz}.0")
        ShizukuManager.runCommandSilent("settings put system min_refresh_rate ${hz}.0")
        // Samsung specific
        ShizukuManager.runCommandSilent("settings put global refresh_rate_mode ${if (hz >= 120) 2 else if (hz >= 90) 1 else 0}")
        // OnePlus/OPPO specific
        ShizukuManager.runCommandSilent("settings put global oneplus_screen_refresh_rate ${hz}")
    }

    private fun applyExtremeSettings() {
        if (!ShizukuManager.hasPermission()) return
        ShizukuManager.runCommandSilent("settings put global low_power 0")
        setRefreshRate(120)
    }

    // --- Mode Engine ---

    private fun onModeChanged(newMode: EngineMode) {
        val oldMode = currentMode
        currentMode = newMode
        val temp = ThermalMonitor.getMaxTemperature()
        Log.d(TAG, "Mode changed: ${oldMode.displayName} -> ${newMode.displayName} (temp: ${temp}°C)")

        if (!ShizukuManager.hasPermission()) return

        when (newMode) {
            EngineMode.EXTREME -> {
                ShizukuManager.runCommandSilent("settings put global low_power 0")
                setRefreshRate(120)
                updateNotification(getString(R.string.notification_text_active))
            }
            EngineMode.BALANCED -> {
                ShizukuManager.runCommandSilent("settings put global low_power 0")
                setRefreshRate(90)
                updateNotification("Balanced mode — device warming")
            }
            EngineMode.SAFE -> {
                ShizukuManager.runCommandSilent("settings put global low_power 0")
                setRefreshRate(60)
                updateNotification(getString(R.string.notification_text_thermal))
                speakIfEnabled { voiceAlertManager?.alertThermalWarning(temp) }
            }
        }

        speakIfEnabled { voiceAlertManager?.alertModeChange(newMode.displayName) }
        overlayManager?.updateMode(newMode)

        // Track mode change
        bgHandler?.post {
            sessionTracker?.recordModeChange()
            sessionTracker?.logEvent("MODE", "${oldMode.displayName} → ${newMode.displayName} (${String.format("%.1f", temp)}°C)")
        }

        // Performance score reflects thermal state
        performanceScore = when (newMode) {
            EngineMode.EXTREME -> 100
            EngineMode.BALANCED -> 70
            EngineMode.SAFE -> 40
        }
    }

    // --- Heavy Tasks ---

    private fun performHeavyTasks() {
        if (!ShizukuManager.hasPermission()) {
            speakIfEnabled { voiceAlertManager?.alertShizukuError() }
            return
        }

        stabilizerRuns++

        killHeavyBackgroundApps()

        // Thermal logging
        val temp = ThermalMonitor.getMaxTemperature()
        if (temp >= 40.0 && temp > 0) {
            thermalHits++
            Log.w(TAG, "Thermal hit #$thermalHits at ${temp}C | Current mode: ${currentMode.displayName} | Override: $manualOverride")
            bgHandler?.post {
                sessionTracker?.logEvent("THERMAL", "Hit #$thermalHits at ${String.format("%.1f", temp)}°C")
            }
        }

        // In manual override, re-apply 120Hz every cycle to prevent system from resetting it
        if (manualOverride) {
            applyExtremeSettings()
            updateNotification("Manual Override — 120Hz Locked")
        } else {
            when (currentMode) {
                EngineMode.SAFE -> updateNotification(getString(R.string.notification_text_thermal))
                else -> updateNotification(getString(R.string.notification_text_active))
            }
        }
    }

    private fun killHeavyBackgroundApps() {
        GameOptimizer.killHeavyApps()
    }

    // --- Stats Collection ---

    private fun collectAndUpdateStats() {
        try {
            val stats = StatsCollector.collectStats(this)
            latestStats = stats

            val sessionMinutes = sessionTimeMs / 60_000

            // Battery remaining estimate
            val estMinutes = if (EnginePrefs.isBatteryEstimatorEnabled(this)) {
                GameOptimizer.getEstimatedMinutesRemaining(stats.batteryPercent)
            } else -1

            // Update overlay on main thread
            mainHandler.post {
                overlayManager?.updateWithStats(stats, currentMode, sessionMinutes, estMinutes)
            }

            // Update widget periodically
            EngineWidget.updateAllWidgets(this)

            // Use gaming sensor temp (skin/shell — not raw chip)
            val temp = if (stats.thermalCelsius > 0) stats.thermalCelsius else stats.batteryTempCelsius.toDouble()

            // Record snapshot for session tracking
            sessionTracker?.recordSnapshot(
                fps = stats.estimatedFps,
                temp = temp,
                cpu = stats.cpuUsagePercent,
                ram = stats.ramUsagePercent,
                battery = stats.batteryPercent,
                ping = stats.networkLatencyMs,
                mode = currentMode.displayName
            )

            // Battery estimator
            if (EnginePrefs.isBatteryEstimatorEnabled(this) && stats.batteryPercent > 0) {
                GameOptimizer.recordBattery(stats.batteryPercent)
            }

            // Voice alerts for critical conditions
            if (stats.cpuUsagePercent >= 90) {
                speakIfEnabled { voiceAlertManager?.alertHighLoad(stats.cpuUsagePercent) }
                performanceScore = (performanceScore - 5).coerceAtLeast(10)
            }

            if (stats.batteryPercent in 1..BATTERY_LOW_THRESHOLD && !batteryLowAlerted) {
                batteryLowAlerted = true
                speakIfEnabled { voiceAlertManager?.alertBatteryLow(stats.batteryPercent) }
            }

            // Adjust performance score based on stats
            if (stats.thermalCelsius >= 42 || stats.batteryTempCelsius >= 42) {
                performanceScore = (performanceScore - 5).coerceAtLeast(10)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Stats collection error", e)
        }
    }

    private fun isVoiceEnabled(): Boolean {
        return overlayManager?.voiceEnabled ?: true
    }

    private fun speakIfEnabled(action: () -> Unit) {
        if (isVoiceEnabled()) action()
    }

    // --- Restore ---

    private fun restoreSettings() {
        if (!ShizukuManager.hasPermission()) return
        ShizukuManager.runCommandSilent("settings put global low_power 0")
        setRefreshRate(originalRefreshRate)
    }

    private fun resetSessionStats() {
        sessionTimeMs = 0L
        thermalHits = 0
        stabilizerRuns = 0
        performanceScore = 100
        currentMode = EngineMode.EXTREME
        sessionStartTime = 0L
        gameWasRunning = false
        sessionActive = false
        gameLastSeenTime = 0
        previousMode = null
        batteryLowAlerted = false
        latestStats = null
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
