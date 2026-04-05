package com.bgmi.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import com.bgmi.engine.ui.BaseActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceInfoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        loadDeviceInfo()
        loadBatteryInfo()
        loadThermalInfo()
    }

    private fun loadDeviceInfo() {
        // Device
        setText(R.id.tvDeviceModel, "Model: ${Build.MODEL}")
        setText(R.id.tvDeviceBrand, "Brand: ${Build.BRAND} (${Build.MANUFACTURER})")
        setText(R.id.tvDeviceBoard, "Board: ${Build.BOARD}")
        setText(R.id.tvDeviceHardware, "Hardware: ${Build.HARDWARE}")

        // OS
        setText(R.id.tvOsVersion, "Android ${Build.VERSION.RELEASE}")
        setText(R.id.tvApiLevel, "API Level: ${Build.VERSION.SDK_INT}")
        setText(R.id.tvSecurityPatch, "Security Patch: ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "N/A"}")
        setText(R.id.tvBuildNumber, "Build: ${Build.DISPLAY}")

        // Memory
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem / (1024 * 1024 * 1024.0)
        val availRam = memInfo.availMem / (1024 * 1024 * 1024.0)
        setText(R.id.tvRamTotal, "Total RAM: ${String.format("%.1f", totalRam)} GB")
        setText(R.id.tvRamAvailable, "Available: ${String.format("%.1f", availRam)} GB")

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes / (1024 * 1024 * 1024.0)
        val freeStorage = stat.availableBytes / (1024 * 1024 * 1024.0)
        setText(R.id.tvStorageTotal, "Storage: ${String.format("%.0f", totalStorage)} GB")
        setText(R.id.tvStorageAvailable, "Free: ${String.format("%.1f", freeStorage)} GB")

        // Display
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        setText(R.id.tvResolution, "Resolution: ${dm.widthPixels} \u00D7 ${dm.heightPixels}")
        setText(R.id.tvDensity, "Density: ${dm.densityDpi} dpi (${dm.density}x)")

        // Refresh Rate
        @Suppress("DEPRECATION")
        val refreshRate = windowManager.defaultDisplay.refreshRate
        setText(R.id.tvRefreshRate, "Refresh Rate: ${String.format("%.0f", refreshRate)} Hz")

        // CPU info via Shizuku
        lifecycleScope.launch(Dispatchers.IO) {
            val cpuResult = ShizukuManager.runCommand("cat /proc/cpuinfo | grep 'Hardware' | head -1")
            val cpuName = if (cpuResult.success && cpuResult.output.isNotBlank()) {
                cpuResult.output.trim().substringAfter(":").trim()
            } else {
                Build.HARDWARE
            }

            val coresResult = ShizukuManager.runCommand("nproc")
            val cores = if (coresResult.success) coresResult.output.trim() else Runtime.getRuntime().availableProcessors().toString()

            val freqResult = ShizukuManager.runCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null")
            val maxFreq = if (freqResult.success) {
                val khz = freqResult.output.trim().toLongOrNull()
                if (khz != null) "${String.format("%.2f", khz / 1_000_000.0)} GHz" else "N/A"
            } else "N/A"

            val gpuResult = ShizukuManager.runCommand("dumpsys SurfaceFlinger | grep 'GLES' | head -1")
            val gpuName = if (gpuResult.success && gpuResult.output.isNotBlank()) {
                gpuResult.output.trim().substringAfter("GLES:").trim().take(60)
            } else "N/A"

            withContext(Dispatchers.Main) {
                setText(R.id.tvCpuInfo, "CPU: $cpuName")
                setText(R.id.tvCpuCores, "Cores: $cores")
                setText(R.id.tvCpuFreq, "Max Freq: $maxFreq")
                setText(R.id.tvGpuInfo, "GPU: $gpuName")
            }
        }
    }

    private fun loadBatteryInfo() {
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val health = bi.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val tech = bi.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            setText(R.id.tvBatteryLevel, "Level: ${pct}%")
            setText(R.id.tvBatteryHealth, "Health: $healthStr")
            setText(R.id.tvBatteryTemp, "Temperature: ${if (temp > 0) "${temp / 10.0}\u00B0C" else "N/A"}")
            setText(R.id.tvBatteryVoltage, "Voltage: ${if (voltage > 0) "${voltage / 1000.0}V" else "N/A"}")
            setText(R.id.tvBatteryTech, "Technology: $tech")
        }
    }

    private fun loadThermalInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val skinTemp = ThermalMonitor.getMaxTemperature()
            val chipTemp = ThermalMonitor.getChipTemperature()

            // Battery temp from intent
            val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val battTemp = if (bi != null) {
                val t = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (t > 0) t / 10.0 else -1.0
            } else -1.0

            withContext(Dispatchers.Main) {
                setText(R.id.tvThermalSkin, "Skin/Shell: ${if (skinTemp > 0) "${String.format("%.1f", skinTemp)}\u00B0C" else "N/A"}")
                setText(R.id.tvThermalChip, "CPU/GPU: ${if (chipTemp > 0) "${String.format("%.1f", chipTemp)}\u00B0C" else "N/A"}")
                setText(R.id.tvThermalBattery, "Battery: ${if (battTemp > 0) "${String.format("%.1f", battTemp)}\u00B0C" else "N/A"}")
            }
        }
    }

    private fun setText(id: Int, text: String) {
        findViewById<TextView>(id)?.text = text
    }
}
