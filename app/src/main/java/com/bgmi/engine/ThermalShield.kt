package com.bgmi.engine

import android.content.Context
import android.util.Log

/**
 * 2-stage thermal protection: Warning + Emergency Cool.
 * Critical shutdown removed.
 */
class ThermalShield(
    private val context: Context,
    private val onWarning: (Double) -> Unit,
    private val onEmergencyCool: (Double) -> Unit,
    private val onRecovery: (String) -> Unit
) {

    companion object {
        private const val TAG = "ThermalShield"
    }

    enum class Stage { NORMAL, WARNING, EMERGENCY }

    var currentStage = Stage.NORMAL
        private set

    private var emergencyApplied = false
    private var savedBrightness = -1

    fun evaluate(tempCelsius: Double) {
        if (tempCelsius < 0) return

        val warningThreshold = EnginePrefs.getWarningTemp(context).toDouble()
        val emergencyThreshold = EnginePrefs.getEmergencyTemp(context).toDouble()

        val newStage = when {
            tempCelsius >= emergencyThreshold -> Stage.EMERGENCY
            tempCelsius >= warningThreshold -> Stage.WARNING
            else -> Stage.NORMAL
        }

        if (newStage.ordinal < currentStage.ordinal) {
            handleRecovery(currentStage, newStage, tempCelsius)
        }

        if (newStage.ordinal > currentStage.ordinal) {
            handleEscalation(newStage, tempCelsius)
        }

        currentStage = newStage
    }

    private fun handleEscalation(newStage: Stage, temp: Double) {
        Log.w(TAG, "Thermal escalation: ${currentStage.name} → ${newStage.name} at ${temp}°C")
        when (newStage) {
            Stage.WARNING -> onWarning(temp)
            Stage.EMERGENCY -> {
                Thread { applyEmergencyCooling(temp) }.start()
                onEmergencyCool(temp)
            }
            Stage.NORMAL -> {}
        }
    }

    private fun handleRecovery(fromStage: Stage, toStage: Stage, temp: Double) {
        Log.d(TAG, "Thermal recovery: ${fromStage.name} → ${toStage.name} at ${temp}°C")
        if (emergencyApplied) {
            Thread { restoreFromEmergency() }.start()
        }
        onRecovery(fromStage.name)
    }

    private fun applyEmergencyCooling(temp: Double) {
        if (emergencyApplied) return
        emergencyApplied = true

        if (!ShizukuManager.hasPermission()) return

        ShizukuManager.runCommandSilent("settings put global low_power 0")
        ShizukuManager.runCommandSilent("settings put global vivo_screen_refresh_rate_mode 60")
        ShizukuManager.runCommandSilent("settings put system peak_refresh_rate 60.0")
        ShizukuManager.runCommandSilent("settings put system min_refresh_rate 60.0")

        if (EnginePrefs.isDropBrightness(context)) {
            val result = ShizukuManager.runCommand("settings get system screen_brightness")
            if (result.success) savedBrightness = result.output.trim().toIntOrNull() ?: -1
            ShizukuManager.runCommandSilent("settings put system screen_brightness 102")
            ShizukuManager.runCommandSilent("settings put system screen_brightness_mode 0")
        }

        if (EnginePrefs.isKillAllBg(context)) {
            GameOptimizer.killHeavyApps()
        }

        Log.w(TAG, "Emergency cooling applied at ${temp}°C")
    }

    private fun restoreFromEmergency() {
        emergencyApplied = false
        if (!ShizukuManager.hasPermission()) return
        if (savedBrightness > 0) {
            ShizukuManager.runCommandSilent("settings put system screen_brightness $savedBrightness")
            ShizukuManager.runCommandSilent("settings put system screen_brightness_mode 1")
            savedBrightness = -1
        }
        Log.d(TAG, "Emergency settings restored")
    }

    fun reset() {
        if (emergencyApplied) {
            Thread { restoreFromEmergency() }.start()
        }
        currentStage = Stage.NORMAL
    }
}
