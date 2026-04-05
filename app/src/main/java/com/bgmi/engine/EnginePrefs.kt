package com.bgmi.engine

import android.content.Context
import android.content.SharedPreferences

object EnginePrefs {

    private const val PREFS_NAME = "bgmi_engine_prefs"

    // Thermal thresholds
    private const val KEY_TEMP_WARNING = "temp_warning"
    private const val KEY_TEMP_EMERGENCY = "temp_emergency"

    // Feature toggles
    private const val KEY_DROP_BRIGHTNESS = "drop_brightness"
    private const val KEY_KILL_ALL_BG = "kill_all_bg"
    private const val KEY_AUTO_DND = "auto_dnd"
    private const val KEY_VOICE_ENABLED = "voice_enabled"

    // New features
    private const val KEY_DNS_OPT_ENABLED = "dns_opt_enabled"
    private const val KEY_DNS_PROVIDER = "dns_provider" // cloudflare, google, adguard
    private const val KEY_TOUCH_BOOST_ENABLED = "touch_boost_enabled"
    private const val KEY_RES_SCALE_ENABLED = "res_scale_enabled"
    private const val KEY_RES_SCALE_PERCENT = "res_scale_percent" // 50-100
    private const val KEY_GPU_BOOST_ENABLED = "gpu_boost_enabled"
    private const val KEY_PRE_GAME_CLEAN = "pre_game_clean"
    private const val KEY_BATTERY_ESTIMATOR = "battery_estimator"

    // Active preset
    private const val KEY_PRESET = "active_preset"

    // Defaults (Balanced)
    const val DEFAULT_TEMP_WARNING = 42
    const val DEFAULT_TEMP_EMERGENCY = 46

    // --- Presets ---
    // Based on Snapdragon 8 Gen 2 thermal analysis:
    // - Skin temp 38-40°C is normal gaming
    // - 42°C+ means device is warming up
    // - 46°C+ needs throttling to prevent discomfort
    // - 50°C+ is emergency territory

    data class Preset(
        val name: String,
        val warningTemp: Int,
        val emergencyTemp: Int,
        val dnsOpt: Boolean,
        val touchBoost: Boolean,
        val gpuBoost: Boolean,
        val preGameClean: Boolean,
        val autoDnd: Boolean,
        val dropBrightness: Boolean,
        val killAllBg: Boolean,
        val batteryEstimator: Boolean,
        val voiceEnabled: Boolean,
        val description: String
    )

    val PRESET_COMPETITIVE = Preset(
        name = "Competitive",
        warningTemp = 44,         // Push harder before throttling
        emergencyTemp = 48,       // Only throttle at extreme heat
        dnsOpt = true,            // Low-latency DNS
        touchBoost = true,        // Max touch response
        gpuBoost = true,          // Max GPU
        preGameClean = true,      // Clean RAM before game
        autoDnd = true,           // Block all notifications
        dropBrightness = false,   // Keep brightness for visibility
        killAllBg = true,         // Kill everything for max RAM
        batteryEstimator = false, // Don't waste cycles on estimates
        voiceEnabled = true,      // Hear thermal alerts
        description = "Max FPS, low latency, aggressive kill\nWarning: 44°C | Emergency: 48°C\nBest for ranked matches"
    )

    val PRESET_BALANCED = Preset(
        name = "Balanced",
        warningTemp = 42,         // Standard threshold
        emergencyTemp = 46,       // Safe throttle point
        dnsOpt = true,
        touchBoost = true,
        gpuBoost = false,         // Let system manage GPU
        preGameClean = true,
        autoDnd = false,          // Allow notifications
        dropBrightness = true,    // Save battery on emergency
        killAllBg = true,
        batteryEstimator = true,
        voiceEnabled = true,
        description = "Good performance with thermal safety\nWarning: 42°C | Emergency: 46°C\nBest for casual/long sessions"
    )

    val PRESET_BATTERY = Preset(
        name = "Battery Saver",
        warningTemp = 40,         // Throttle early to save battery
        emergencyTemp = 44,       // Aggressive cooling
        dnsOpt = true,
        touchBoost = false,       // Save power
        gpuBoost = false,         // System-managed GPU
        preGameClean = true,
        autoDnd = false,
        dropBrightness = true,    // Drop brightness early
        killAllBg = true,
        batteryEstimator = true,  // Show time remaining
        voiceEnabled = false,     // Save TTS battery
        description = "Extended gaming, lower heat\nWarning: 40°C | Emergency: 44°C\nBest for long sessions on low battery"
    )

    fun getActivePreset(context: Context): String = prefs(context).getString(KEY_PRESET, "balanced") ?: "balanced"
    fun setActivePreset(context: Context, preset: String) = prefs(context).edit().putString(KEY_PRESET, preset).apply()

    fun applyPreset(context: Context, preset: Preset) {
        val editor = prefs(context).edit()
        editor.putInt(KEY_TEMP_WARNING, preset.warningTemp)
        editor.putInt(KEY_TEMP_EMERGENCY, preset.emergencyTemp)
        editor.putBoolean(KEY_DNS_OPT_ENABLED, preset.dnsOpt)
        editor.putBoolean(KEY_TOUCH_BOOST_ENABLED, preset.touchBoost)
        editor.putBoolean(KEY_GPU_BOOST_ENABLED, preset.gpuBoost)
        editor.putBoolean(KEY_PRE_GAME_CLEAN, preset.preGameClean)
        editor.putBoolean(KEY_AUTO_DND, preset.autoDnd)
        editor.putBoolean(KEY_DROP_BRIGHTNESS, preset.dropBrightness)
        editor.putBoolean(KEY_KILL_ALL_BG, preset.killAllBg)
        editor.putBoolean(KEY_BATTERY_ESTIMATOR, preset.batteryEstimator)
        editor.putBoolean(KEY_VOICE_ENABLED, preset.voiceEnabled)
        editor.putString(KEY_PRESET, preset.name.lowercase().replace(" ", "_"))
        editor.apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Thermal ---
    fun getWarningTemp(context: Context): Int = prefs(context).getInt(KEY_TEMP_WARNING, DEFAULT_TEMP_WARNING)
    fun setWarningTemp(context: Context, temp: Int) = prefs(context).edit().putInt(KEY_TEMP_WARNING, temp).apply()
    fun getEmergencyTemp(context: Context): Int = prefs(context).getInt(KEY_TEMP_EMERGENCY, DEFAULT_TEMP_EMERGENCY)
    fun setEmergencyTemp(context: Context, temp: Int) = prefs(context).edit().putInt(KEY_TEMP_EMERGENCY, temp).apply()

    // --- Feature Toggles ---
    fun isDropBrightness(context: Context): Boolean = prefs(context).getBoolean(KEY_DROP_BRIGHTNESS, true)
    fun setDropBrightness(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_DROP_BRIGHTNESS, v).apply()

    fun isKillAllBg(context: Context): Boolean = prefs(context).getBoolean(KEY_KILL_ALL_BG, true)
    fun setKillAllBg(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_KILL_ALL_BG, v).apply()

    fun isAutoDnd(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DND, false)
    fun setAutoDnd(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_DND, v).apply()

    fun isVoiceEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_VOICE_ENABLED, true)
    fun setVoiceEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_VOICE_ENABLED, v).apply()

    // --- DNS Optimizer ---
    fun isDnsOptEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DNS_OPT_ENABLED, false)
    fun setDnsOptEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_DNS_OPT_ENABLED, v).apply()

    fun getDnsProvider(context: Context): String = prefs(context).getString(KEY_DNS_PROVIDER, "cloudflare") ?: "cloudflare"
    fun setDnsProvider(context: Context, v: String) = prefs(context).edit().putString(KEY_DNS_PROVIDER, v).apply()

    // --- Touch Boost ---
    fun isTouchBoostEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TOUCH_BOOST_ENABLED, false)
    fun setTouchBoostEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_TOUCH_BOOST_ENABLED, v).apply()

    // --- Resolution Scaler ---
    fun isResScaleEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_RES_SCALE_ENABLED, false)
    fun setResScaleEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_RES_SCALE_ENABLED, v).apply()

    fun getResScale(context: Context): Int = prefs(context).getInt(KEY_RES_SCALE_PERCENT, 80)
    fun setResScale(context: Context, v: Int) = prefs(context).edit().putInt(KEY_RES_SCALE_PERCENT, v).apply()

    // --- GPU Governor ---
    fun isGpuBoostEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GPU_BOOST_ENABLED, false)
    fun setGpuBoostEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_GPU_BOOST_ENABLED, v).apply()

    // --- Pre-Game Clean ---
    fun isPreGameCleanEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_PRE_GAME_CLEAN, true)
    fun setPreGameCleanEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_PRE_GAME_CLEAN, v).apply()

    // --- Battery Estimator ---
    fun isBatteryEstimatorEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BATTERY_ESTIMATOR, true)
    fun setBatteryEstimatorEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_BATTERY_ESTIMATOR, v).apply()
}
