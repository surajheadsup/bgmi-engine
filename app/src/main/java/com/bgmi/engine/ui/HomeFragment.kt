package com.bgmi.engine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgmi.engine.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class HomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var liveStatusText = ""

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded) refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPermissions(view)
        setupEngine(view)
        setupDeviceStatus(view)
        setupQuickKill(view)
        loadPerformanceCard(view)
    }


    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
        view?.let { loadPerformanceCard(it) }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupPermissions(view: View) {
        view.findViewById<TextView>(R.id.btnGrantShizuku).setOnClickListener {
            if (!ShizukuManager.isShizukuAvailable()) {
                showAlert("Shizuku Not Running",
                    "Install Shizuku from Play Store and start via Wireless Debugging or ADB.")
                return@setOnClickListener
            }
            ShizukuManager.requestPermission { granted ->
                if (isAdded) requireActivity().runOnUiThread { refreshStatus() }
            }
        }
        view.findViewById<TextView>(R.id.btnGrantUsage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        view.findViewById<TextView>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
        }
    }

    private fun setupEngine(view: View) {
        view.findViewById<TextView>(R.id.btnStartStop).setOnClickListener {
            val ctx = requireContext()
            if (EngineService.isRunning) {
                ctx.stopService(Intent(ctx, EngineService::class.java))
            } else {
                val intent = Intent(ctx, EngineService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            }
        }
        view.findViewById<SwitchMaterial>(R.id.switchOverride).setOnCheckedChangeListener { _, checked ->
            EngineService.setManualOverride(checked)
        }
    }

    private fun setupDeviceStatus(view: View) {
        view.findViewById<View>(R.id.btnRefreshStatus).setOnClickListener { refreshLiveDeviceStatus() }
        view.findViewById<View>(R.id.btnApplyNow).setOnClickListener {
            if (!ShizukuManager.hasPermission()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                GameOptimizer.applyOnGameStart(requireContext())
                if (isAdded) withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Applied!", Toast.LENGTH_SHORT).show()
                    refreshLiveDeviceStatus()
                }
            }
        }
        view.findViewById<View>(R.id.btnRestoreAll).setOnClickListener {
            if (!ShizukuManager.hasPermission()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                GameOptimizer.restoreOnGameExit(requireContext())
                if (isAdded) withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Restored!", Toast.LENGTH_SHORT).show()
                    refreshLiveDeviceStatus()
                }
            }
        }
        view.findViewById<View>(R.id.btnCopyStatus).setOnClickListener {
            if (liveStatusText.isBlank()) return@setOnClickListener
            val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("Status", liveStatusText))
            Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickKill(view: View) {
        view.findViewById<View>(R.id.btnHomeKill).setOnClickListener {
            if (!ShizukuManager.hasPermission()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val whitelist = requireContext().getSharedPreferences("bgmi_engine_prefs", Context.MODE_PRIVATE)
                    .getStringSet("kill_whitelist", emptySet()) ?: emptySet()
                val protd = setOf("com.bgmi.engine", "com.pubg.imobile", "moe.shizuku.privileged.api", "com.android.systemui", "com.android.launcher3")
                val sysPat = listOf("vendor.", "android.hardware.", "android.hidl.", "com.android.", "com.qualcomm.", "com.qti.",
                    "org.codeaurora.", "com.google.android.", "com.vivo.", "com.bbk.", "com.iqoo.",
                    "android.process.", "android.system.", ".dataservices", ".pasr", ".qms", ".qtidataservices",
                    "sensors.", "media.", "qccsyshal", "_zygote")
                val r = ShizukuManager.runCommand("ps -A -o NAME")
                if (!r.success) return@launch
                val toKill = r.output.lines().map { it.trim().split(":").first() }
                    .filter { it.contains(".") && !it.startsWith("[") }
                    .filter { pkg -> !protd.contains(pkg) && !whitelist.contains(pkg) }
                    .filter { pkg -> !sysPat.any { pkg.startsWith(it) || pkg.contains(it) } }.distinct()
                if (toKill.isEmpty()) return@launch
                if (isAdded) withContext(Dispatchers.Main) {
                    KillTerminalActivity.packagesToKill = toKill
                    startActivity(Intent(requireContext(), KillTerminalActivity::class.java))
                }
            }
        }
    }

    private fun refreshStatus() {
        val view = view ?: return
        val ctx = context ?: return

        // Battery + Temp
        val bi = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scl = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val sts = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val tmp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val pct = if (lvl >= 0 && scl > 0) lvl * 100 / scl else -1
            val chg = sts == BatteryManager.BATTERY_STATUS_CHARGING
            if (pct >= 0) view.findViewById<TextView>(R.id.tvHomeBattery).text = "🔋 ${pct}%${if (chg) "+" else ""}"
            if (tmp > 0) view.findViewById<TextView>(R.id.tvHomeTemp).text = "🌡 ${tmp / 10}°C"
        }

        // RAM
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (am != null) {
            val mem = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mem)
            val used = (mem.totalMem - mem.availMem) / (1024 * 1024 * 1024.0)
            val total = mem.totalMem / (1024 * 1024 * 1024.0)
            val pct = ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt()
            view.findViewById<TextView>(R.id.tvHomeRam).text = "RAM ${String.format("%.1f", used)}/${String.format("%.0f", total)}G"
        }

        // CPU
        val stats = EngineService.latestStats
        view.findViewById<TextView>(R.id.tvHomeCpu).text = if (stats != null && stats.cpuUsagePercent >= 0) "CPU ${stats.cpuUsagePercent}%" else "CPU --"

        // Process counts — use same filter as kill logic for accurate user count
        if (ShizukuManager.hasPermission()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val r = ShizukuManager.runCommand("ps -A -o NAME")
                if (r.success && isAdded) {
                    val sysPat = listOf("vendor.", "android.hardware.", "android.hidl.", "com.android.", "com.qualcomm.", "com.qti.",
                        "org.codeaurora.", "com.google.android.", "com.vivo.", "com.bbk.", "com.iqoo.",
                        "android.process.", "android.system.", ".dataservices", ".pasr", ".qms", ".qtidataservices",
                        "sensors.", "media.", "qccsyshal", "_zygote")
                    val protd = setOf("com.bgmi.engine", "com.pubg.imobile", "moe.shizuku.privileged.api", "com.android.systemui", "com.android.launcher3")
                    val allProcs = r.output.lines().map { it.trim().split(":").first() }
                        .filter { it.contains(".") && !it.startsWith("[") }.distinct()
                    val sys = allProcs.filter { pkg -> sysPat.any { pkg.startsWith(it) || pkg.contains(it) } || protd.contains(pkg) }
                    val user = allProcs - sys.toSet()
                    withContext(Dispatchers.Main) { view.findViewById<TextView>(R.id.tvHomeProcs)?.text = "U:${user.size} S:${sys.size}" }
                }
            }
        }

        // Permissions
        val shizukuOk = ShizukuManager.isShizukuAvailable()
        val permOk = ShizukuManager.hasPermission()
        val usageOk = GameDetector(ctx).hasUsageAccess()
        val overlayOk = Settings.canDrawOverlays(ctx)

        fun setDot(id: Int, ok: Boolean) { view.findViewById<View>(id).setBackgroundColor(if (ok) Colors.SUCCESS else Colors.DANGER) }
        setDot(R.id.dotShizuku, shizukuOk && permOk)
        setDot(R.id.dotUsage, usageOk)
        setDot(R.id.dotOverlay, overlayOk)

        view.findViewById<TextView>(R.id.tvShizukuStatus).text = if (!shizukuOk) "Shizuku — Not running" else if (!permOk) "Shizuku — No permission" else "Shizuku — Ready"
        view.findViewById<TextView>(R.id.btnGrantShizuku).visibility = if (shizukuOk && permOk) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tvShizukuGuide).visibility = if (!shizukuOk) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvUsageStatus).text = if (usageOk) "Usage Access — Granted" else "Usage Access — Required"
        view.findViewById<TextView>(R.id.btnGrantUsage).visibility = if (usageOk) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tvOverlayStatus).text = if (overlayOk) "Overlay — Granted" else "Overlay — Required"
        view.findViewById<TextView>(R.id.btnGrantOverlay).visibility = if (overlayOk) View.GONE else View.VISIBLE

        // Engine
        if (EngineService.isRunning) {
            view.findViewById<TextView>(R.id.tvEngineStatus).text = "Engine Running"
            view.findViewById<TextView>(R.id.tvEngineStatus).setTextColor(0xFF4ECB71.toInt())
            view.findViewById<TextView>(R.id.btnStartStop).text = "STOP"
            val mode = EngineService.currentMode
            view.findViewById<TextView>(R.id.tvEngineMode).apply {
                visibility = View.VISIBLE
                text = if (EngineService.manualOverride) "MANUAL OVERRIDE" else mode.displayName
                setTextColor(if (EngineService.manualOverride) com.bgmi.engine.ui.Colors.ACCENT.toInt() else mode.color)
            }
        } else {
            view.findViewById<TextView>(R.id.tvEngineStatus).text = "Engine Stopped"
            view.findViewById<TextView>(R.id.tvEngineStatus).setTextColor(0xFFAAAAAA.toInt())
            view.findViewById<TextView>(R.id.btnStartStop).text = "START"
            view.findViewById<TextView>(R.id.tvEngineMode).visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.btnStartStop).isEnabled = usageOk && shizukuOk && permOk

        // Override sync
        val sw = view.findViewById<SwitchMaterial>(R.id.switchOverride)
        if (sw.isChecked != EngineService.manualOverride) sw.isChecked = EngineService.manualOverride
        view.findViewById<TextView>(R.id.tvOverrideStatus).text = if (EngineService.manualOverride) "120Hz locked" else "Auto (heat-based)"
        view.findViewById<TextView>(R.id.tvOverrideStatus).setTextColor(if (EngineService.manualOverride) com.bgmi.engine.ui.Colors.ACCENT.toInt() else 0xFF4ECB71.toInt())
    }

    private fun refreshLiveDeviceStatus() {
        val view = view ?: return
        if (!ShizukuManager.hasPermission()) return
        view.findViewById<TextView>(R.id.tvDeviceStatusUpdated).text = "Reading..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val sb = StringBuilder("═══ BGMI Engine Pro — Status ═══\n")
            sb.appendLine("Engine: ${if (EngineService.isRunning) "RUNNING" else "STOPPED"}")
            sb.appendLine()

            data class S(val id: Int, val cmd: String, val label: String, val fmt: (String) -> Pair<String, Int>)
            val g = Colors.SUCCESS; val y = Colors.WARNING; val r = Colors.DANGER; val w = Colors.onSurfaceVariant(requireContext())

            val items = listOf(
                S(R.id.tvLiveShizuku, "whoami", "Shizuku") { if (it == "shell") "shell ✓" to g else it to y },
                S(R.id.tvLiveRefreshRate, "settings get global vivo_screen_refresh_rate_mode", "Refresh") { v -> val hz = v.toIntOrNull(); if (hz != null) "${hz}Hz" to (if (hz >= 120) g else r) else "null" to r },
                S(R.id.tvLivePerfMode, "settings get global low_power", "BatSaver") { if (it.trim() == "0") "OFF ✓" to g else "ON" to r },
                S(R.id.tvLiveBrightness, "settings get system screen_brightness", "Bright") { v -> val raw = v.toIntOrNull(); if (raw != null) "${raw*100/255}%" to w else v to w },
                S(R.id.tvLiveBrightnessMode, "settings get system screen_brightness_mode", "Mode") { if (it == "1") "Auto" to g else "Manual" to y },
                S(R.id.tvLiveDns, "settings get global private_dns_specifier", "DNS") { v -> when { v.contains("one.one") -> "Cloudflare ✓" to g; v.contains("dns.google") -> "Google ✓" to g; v == "null" -> "Default" to w; else -> v to y } },
                S(R.id.tvLiveGpu, "settings get global adaptive_battery_management_enabled", "Adaptive") { if (it.trim() == "0") "OFF ✓" to g else "ON" to y },
                S(R.id.tvLivePointerSpeed, "settings get system pointer_speed", "Touch") { v -> val s = v.toIntOrNull(); if (s != null && s >= 5) "$v ✓" to g else "$v" to w },
                S(R.id.tvLiveDnd, "settings get global zen_mode", "DND") { if (it.trim() == "0") "OFF" to w else "ON" to y },
                S(R.id.tvLiveTemp, "cat /sys/class/thermal/thermal_zone104/temp 2>/dev/null || echo -1", "Temp") { v -> val raw = v.trim().toDoubleOrNull(); if (raw != null && raw > 0) { val c = if (raw > 1000) raw/1000 else raw; "${String.format("%.1f", c)}°C" to (if (c >= 42) r else g) } else "--" to w }
            )
            val icons = mapOf(R.id.tvLiveShizuku to "⚙", R.id.tvLiveRefreshRate to "🖥", R.id.tvLivePerfMode to "⚡",
                R.id.tvLiveBrightness to "☀", R.id.tvLiveBrightnessMode to "☀", R.id.tvLiveDns to "📶",
                R.id.tvLiveGpu to "🎮", R.id.tvLivePointerSpeed to "👆", R.id.tvLiveDnd to "🔇", R.id.tvLiveTemp to "🌡")

            for (s in items) {
                val result = ShizukuManager.runCommand(s.cmd)
                val raw = if (result.success) result.output.trim() else "ERR"
                val (display, color) = if (result.success) s.fmt(raw) else "FAIL" to r
                sb.appendLine("[${if (result.success) "+" else "!"}] ${s.label}: $display")
                if (isAdded) withContext(Dispatchers.Main) {
                    view?.findViewById<TextView>(s.id)?.text = "${icons[s.id] ?: ""} ${s.label}: $display"
                    view?.findViewById<TextView>(s.id)?.setTextColor(color)
                }
            }
            sb.appendLine("\nTimestamp: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            liveStatusText = sb.toString()
            if (isAdded) withContext(Dispatchers.Main) {
                view?.findViewById<TextView>(R.id.tvDeviceStatusUpdated)?.text = "Updated ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
                view?.findViewById<TextView>(R.id.tvDeviceStatusUpdated)?.setTextColor(0xFF4ECB71.toInt())
                view?.findViewById<TextView>(R.id.tvLiveUser)?.text = "🔑 Shell: uid=2000(shell)"
            }
        }
    }

    private fun loadPerformanceCard(view: View) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.bgmi.engine.data.AppDatabase.getInstance(requireContext())
                val dao = db.gameSessionDao()
                val totalSessions = dao.getSessionCount()
                val totalPlayMs = dao.getTotalPlayTimeMs() ?: 0L
                val avgScore = dao.getOverallAvgScore() ?: 0.0
                val avgTemp = dao.getOverallAvgTemp() ?: 0.0
                val totalThermal = dao.getTotalThermalHits() ?: 0

                val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
                val lastSession = dao.getBestSessionSince(weekAgo)

                val totalMin = totalPlayMs / 60000
                val hours = totalMin / 60
                val mins = totalMin % 60
                val playTimeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                val lastStr = if (lastSession != null) "Score ${lastSession.performanceScore}" else "None"

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    view.findViewById<TextView>(R.id.tvPerfSessions)?.text = "$totalSessions"
                    view.findViewById<TextView>(R.id.tvPerfPlayTime)?.text = playTimeStr
                    view.findViewById<TextView>(R.id.tvPerfScore)?.text = if (avgScore > 0) "${avgScore.toInt()}" else "--"
                    view.findViewById<TextView>(R.id.tvPerfAvgTemp)?.text = if (avgTemp > 0) "${String.format("%.1f", avgTemp)}°" else "--"
                    view.findViewById<TextView>(R.id.tvPerfThermal)?.text = "$totalThermal"
                    view.findViewById<TextView>(R.id.tvPerfLastSession)?.text = lastStr
                }
            } catch (e: Exception) {
                // Silent — card just shows defaults
            }
        }
    }

    private fun showAlert(title: String, message: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }.show()
    }
}
