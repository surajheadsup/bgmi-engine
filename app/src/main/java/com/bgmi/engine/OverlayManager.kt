package com.bgmi.engine

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isShowing = false
    private var isExpanded = false
    var voiceEnabled = true
        private set

    // Countdown overlay
    private var countdownView: View? = null
    private var countdownParams: WindowManager.LayoutParams? = null
    private var isCountdownShowing = false
    var onCancelShutdown: (() -> Unit)? = null

    // Collapsed bar views
    private var tvBarMode: TextView? = null
    private var tvBarFps: TextView? = null
    private var tvBarTemp: TextView? = null
    private var tvBarBattery: TextView? = null
    private var tvBarPing: TextView? = null
    private var tvBarBrightness: TextView? = null
    private var tvExpandArrow: TextView? = null
    private var dotIndicator: View? = null

    // Expanded panel views
    private var expandedPanel: LinearLayout? = null
    private var tvExpCpu: TextView? = null
    private var tvExpRam: TextView? = null
    private var tvExpSession: TextView? = null
    private var tvExpStatus: TextView? = null
    private var btnToggleVoice: TextView? = null
    private var btnToggleOverride: TextView? = null
    private var btnKillApps: TextView? = null

    fun show() {
        if (isShowing) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                Log.e(TAG, "WindowManager is null")
                return
            }

            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.overlay_dashboard, null)

            initViews()
            setupListeners()

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0 // Stick to very top
            }

            windowManager?.addView(overlayView, layoutParams)
            isShowing = true
            isExpanded = false
            expandedPanel?.visibility = View.GONE
            tvExpandArrow?.text = "▼"

            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            isShowing = false
        }
    }

    fun hide() {
        if (!isShowing) return

        try {
            windowManager?.removeView(overlayView)
            overlayView = null
            isShowing = false
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }

    fun isVisible(): Boolean = isShowing

    fun updateMode(mode: EngineMode) {
        if (!isShowing) return
        handler.post {
            updateModeDisplay()
        }
    }

    fun updateWithStats(stats: StatsCollector.SystemStats, mode: EngineMode, sessionMinutes: Long, estMinutesRemaining: Int = -1) {
        if (!isShowing) return

        handler.post {
            try {
                // --- Collapsed bar ---
                updateModeDisplay()

                // FPS
                if (stats.estimatedFps > 0) {
                    tvBarFps?.text = "\uD83C\uDFAE ${stats.estimatedFps}"
                    tvBarFps?.setTextColor(fpsColor(stats.estimatedFps))
                } else {
                    tvBarFps?.text = "\uD83C\uDFAE --"
                    tvBarFps?.setTextColor(0xFF888888.toInt())
                }

                // Temp — show gaming sensor (skin/shell temp, not raw chip temp)
                val displayTemp = if (stats.thermalCelsius > 0) stats.thermalCelsius else stats.batteryTempCelsius.toDouble()

                if (displayTemp > 0) {
                    tvBarTemp?.text = String.format("\uD83C\uDF21 %.0f°", displayTemp)
                    tvBarTemp?.setTextColor(tempColor(displayTemp))
                } else {
                    tvBarTemp?.text = "\uD83C\uDF21 --°"
                }

                // Battery + estimated time remaining
                if (stats.batteryPercent >= 0) {
                    val charge = if (stats.isCharging) "+" else ""
                    val est = if (estMinutesRemaining > 0) " ~${estMinutesRemaining}m" else ""
                    tvBarBattery?.text = "\uD83D\uDD0B ${stats.batteryPercent}%${charge}${est}"
                    tvBarBattery?.setTextColor(batteryColor(stats.batteryPercent))
                }

                // Ping
                if (stats.networkLatencyMs > 0) {
                    tvBarPing?.text = "\uD83D\uDCF6 ${stats.networkLatencyMs}ms"
                    tvBarPing?.setTextColor(pingColor(stats.networkLatencyMs))
                } else {
                    tvBarPing?.text = "\uD83D\uDCF6 --"
                }

                // Brightness
                if (stats.brightnessPercent >= 0) {
                    tvBarBrightness?.text = "☀ ${stats.brightnessPercent}%"
                } else {
                    tvBarBrightness?.text = "☀ --"
                }

                // Status dot color
                val dotColor = when {
                    displayTemp >= 42 -> 0xFFFF6B6B.toInt()
                    displayTemp >= 38 || stats.cpuUsagePercent >= 85 -> 0xFFFFD700.toInt()
                    else -> 0xFF4ECB71.toInt()
                }
                dotIndicator?.setBackgroundColor(dotColor)

                // --- Expanded panel ---
                if (stats.cpuUsagePercent >= 0) {
                    tvExpCpu?.text = "CPU ${stats.cpuUsagePercent}%"
                    tvExpCpu?.setTextColor(cpuColor(stats.cpuUsagePercent))
                }

                if (stats.ramUsagePercent >= 0) {
                    tvExpRam?.text = "RAM ${stats.ramUsagePercent}%"
                    tvExpRam?.setTextColor(ramColor(stats.ramUsagePercent))
                }

                val estText = if (estMinutesRemaining > 0) " (~${estMinutesRemaining}m left)" else ""
                tvExpSession?.text = "${sessionMinutes}m$estText"
                tvExpSession?.setTextColor(0xFFAAAAAA.toInt())

                val status = determineStatus(stats)
                tvExpStatus?.text = status.first
                tvExpStatus?.setTextColor(status.second)

                // Update toggle button states
                updateToggleStates()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay stats", e)
            }
        }
    }

    fun showWarning(message: String) {
        if (!isShowing) return
        handler.post {
            tvExpStatus?.text = message
            tvExpStatus?.setTextColor(0xFFFF6B6B.toInt())
        }
    }

    // --- Private ---

    private fun initViews() {
        overlayView?.let { v ->
            // Collapsed bar
            tvBarMode = v.findViewById(R.id.tvBarMode)
            tvBarFps = v.findViewById(R.id.tvBarFps)
            tvBarTemp = v.findViewById(R.id.tvBarTemp)
            tvBarBattery = v.findViewById(R.id.tvBarBattery)
            tvBarPing = v.findViewById(R.id.tvBarPing)
            tvBarBrightness = v.findViewById(R.id.tvBarBrightness)
            tvExpandArrow = v.findViewById(R.id.tvExpandArrow)
            dotIndicator = v.findViewById(R.id.dotIndicator)

            // Expanded panel
            expandedPanel = v.findViewById(R.id.expandedPanel)
            tvExpCpu = v.findViewById(R.id.tvExpCpu)
            tvExpRam = v.findViewById(R.id.tvExpRam)
            tvExpSession = v.findViewById(R.id.tvExpSession)
            tvExpStatus = v.findViewById(R.id.tvExpStatus)
            btnToggleVoice = v.findViewById(R.id.btnToggleVoice)
            btnToggleOverride = v.findViewById(R.id.btnToggleOverride)
            btnKillApps = v.findViewById(R.id.btnKillApps)
        }
    }

    private fun setupListeners() {
        val collapsedBar = overlayView?.findViewById<View>(R.id.collapsedBar) ?: return

        // Tap collapsed bar to expand/collapse, drag to move
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        collapsedBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams?.x = initialX - dx.toInt()
                        layoutParams?.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(overlayView, layoutParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Drag update failed", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }

        // Toggle buttons
        btnToggleVoice?.setOnClickListener {
            voiceEnabled = !voiceEnabled
            updateToggleStates()
        }

        btnToggleOverride?.setOnClickListener {
            val newState = !EngineService.manualOverride
            EngineService.setManualOverride(newState)
            updateToggleStates()
        }

        btnKillApps?.setOnClickListener {
            // Run kill on background thread to avoid blocking UI
            Thread {
                EngineService.killBackgroundAppsNow()
            }.start()
            btnKillApps?.text = "Killed!"
            handler.postDelayed({ btnKillApps?.text = "Kill BG" }, 1500)
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        if (isExpanded) {
            expandedPanel?.visibility = View.VISIBLE
            tvExpandArrow?.text = "▲"
        } else {
            expandedPanel?.visibility = View.GONE
            tvExpandArrow?.text = "▼"
        }
        // Refresh layout
        try {
            windowManager?.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Layout update failed", e)
        }
    }

    private fun updateModeDisplay() {
        if (EngineService.manualOverride) {
            tvBarMode?.text = "⚡OVR"
            tvBarMode?.setTextColor(com.bgmi.engine.ui.Colors.ACCENT.toInt())
        } else {
            val mode = EngineService.currentMode
            tvBarMode?.text = when (mode) {
                EngineMode.EXTREME -> "⚡EXT"
                EngineMode.BALANCED -> "⚡BAL"
                EngineMode.SAFE -> "⚡SAFE"
            }
            tvBarMode?.setTextColor(mode.color)
        }
    }

    private fun updateToggleStates() {
        // Voice button
        if (voiceEnabled) {
            btnToggleVoice?.text = "Voice ON"
            btnToggleVoice?.setTextColor(0xFF4ECB71.toInt())
        } else {
            btnToggleVoice?.text = "Voice OFF"
            btnToggleVoice?.setTextColor(0xFFFF6B6B.toInt())
        }

        // Override button
        if (EngineService.manualOverride) {
            btnToggleOverride?.text = "120Hz Lock"
            btnToggleOverride?.setTextColor(com.bgmi.engine.ui.Colors.ACCENT.toInt())
        } else {
            btnToggleOverride?.text = "Auto"
            btnToggleOverride?.setTextColor(0xFF4ECB71.toInt())
        }
    }

    private fun determineStatus(stats: StatsCollector.SystemStats): Pair<String, Int> {
        val temp = maxOf(
            if (stats.thermalCelsius > 0) stats.thermalCelsius else 0.0,
            stats.batteryTempCelsius.toDouble()
        )
        return when {
            temp >= 42 -> "Hot" to 0xFFFF6B6B.toInt()
            stats.cpuUsagePercent >= 85 || stats.ramUsagePercent >= 90 -> "Warn" to 0xFFFFD700.toInt()
            temp >= 38 -> "Warm" to 0xFFFFD700.toInt()
            stats.batteryPercent in 1..15 -> "LowBat" to 0xFFFFD700.toInt()
            else -> "OK" to 0xFF4ECB71.toInt()
        }
    }

    // --- Countdown Overlay ---

    fun showCountdown(secondsLeft: Int, tempCelsius: Double) {
        handler.post {
            if (!isCountdownShowing) {
                showCountdownOverlay()
            }
            countdownView?.findViewById<TextView>(R.id.tvCountdownNumber)?.text = "$secondsLeft"
            countdownView?.findViewById<TextView>(R.id.tvCountdownTemp)?.text =
                "Temperature: ${String.format("%.1f", tempCelsius)}°C"
        }
    }

    fun hideCountdown() {
        handler.post {
            if (!isCountdownShowing) return@post
            try {
                windowManager?.removeView(countdownView)
                countdownView = null
                isCountdownShowing = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide countdown", e)
            }
        }
    }

    fun flashWarning() {
        handler.post {
            // Flash the bar red briefly
            val card = overlayView?.findViewById<View>(R.id.dashboardCard) ?: return@post
            card.setBackgroundColor(0x66FF6B6B.toInt())
            handler.postDelayed({
                card.setBackgroundResource(R.drawable.overlay_bg)
            }, 500)
        }
    }

    private fun showCountdownOverlay() {
        try {
            val wm = windowManager ?: return
            val inflater = LayoutInflater.from(context)
            countdownView = inflater.inflate(R.layout.overlay_countdown, null)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            countdownParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            countdownView?.findViewById<TextView>(R.id.btnCancelShutdown)?.setOnClickListener {
                onCancelShutdown?.invoke()
            }

            wm.addView(countdownView, countdownParams)
            isCountdownShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show countdown overlay", e)
        }
    }

    // --- Color helpers ---
    private fun fpsColor(fps: Int) = when {
        fps >= 50 -> 0xFF4ECB71.toInt()
        fps >= 30 -> 0xFFFFD700.toInt()
        else -> 0xFFFF6B6B.toInt()
    }
    private fun tempColor(t: Double) = when {
        t >= 42 -> 0xFFFF6B6B.toInt()
        t >= 38 -> 0xFFFFD700.toInt()
        else -> 0xFF4ECB71.toInt()
    }
    private fun batteryColor(pct: Int) = when {
        pct <= 15 -> 0xFFFF6B6B.toInt()
        pct <= 30 -> 0xFFFFD700.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
    private fun pingColor(ms: Int) = when {
        ms >= 150 -> 0xFFFF6B6B.toInt()
        ms >= 80 -> 0xFFFFD700.toInt()
        else -> 0xFF4ECB71.toInt()
    }
    private fun cpuColor(pct: Int) = when {
        pct >= 85 -> 0xFFFF6B6B.toInt()
        pct >= 60 -> 0xFFFFD700.toInt()
        else -> 0xFF4ECB71.toInt()
    }
    private fun ramColor(pct: Int) = when {
        pct >= 85 -> 0xFFFF6B6B.toInt()
        pct >= 70 -> 0xFFFFD700.toInt()
        else -> 0xFF4ECB71.toInt()
    }
}
