package com.bgmi.engine

import android.util.Log
import java.io.File

object ThermalMonitor {

    private const val TAG = "ThermalMonitor"
    private const val THERMAL_BASE_PATH = "/sys/class/thermal"

    // Gaming-relevant sensor types (prioritized)
    // These represent what the user actually feels and what affects performance
    private val GAMING_SENSORS = listOf(
        "tz_game_shell",      // Vivo/iQOO game-specific shell temp
        "skin-msm-therm",     // Qualcomm skin temperature
        "skin_therm",         // Common skin sensor
        "tz_shell",           // Shell temperature (Vivo)
        "back_therm",         // Back panel sensor (Samsung, OnePlus)
        "quiet_therm",        // Quiet/ambient skin sensor (Pixel)
        "sdm-therm",          // Snapdragon mobile thermal
        "pa_therm0",          // Power amplifier thermal (Samsung)
        "xo_therm",           // Crystal oscillator thermal (Qualcomm)
        "mtktscpu",           // MediaTek CPU thermal
        "mtktsAP",            // MediaTek AP thermal
        "battery"             // Battery temperature (universal fallback)
    )

    // CPU/GPU sensors (for reference, not for mode switching)
    private val CHIP_SENSORS = listOf(
        "cpuss-0", "cpuss-1",     // Qualcomm CPU subsystem
        "gpuss-0",                 // Qualcomm GPU subsystem
        "cpu-0-0", "cpu-0-1",     // Samsung Exynos CPU
        "gpu",                     // Generic GPU
        "soc_thermal",             // Generic SoC thermal
        "mtktscpu"                 // MediaTek CPU
    )

    // Cache zone numbers once discovered
    private var gamingSensorZone: String? = null
    private var chipSensorZone: String? = null

    /**
     * Returns the gaming-relevant temperature (skin/shell).
     * This is what should drive mode switching — NOT raw chip temps.
     */
    fun getMaxTemperature(): Double {
        // Try cached zone first
        if (gamingSensorZone != null) {
            val temp = readSingleZone(gamingSensorZone!!)
            if (temp > 0) return temp
            gamingSensorZone = null // Cache invalid, re-discover
        }

        // Discover best gaming sensor
        return discoverAndReadGamingSensor()
    }

    /**
     * Returns raw chip temperature (CPU/GPU) — for display only, not mode switching.
     */
    fun getChipTemperature(): Double {
        if (chipSensorZone != null) {
            val temp = readSingleZone(chipSensorZone!!)
            if (temp > 0) return temp
            chipSensorZone = null
        }

        return discoverAndReadChipSensor()
    }

    fun isOverheating(): Boolean {
        val temp = getMaxTemperature()
        return temp >= 40.0 && temp > 0
    }

    private fun discoverAndReadGamingSensor(): Double {
        try {
            // Use Shizuku to read zone types and find our preferred sensors
            for (sensorType in GAMING_SENSORS) {
                val result = ShizukuManager.runCommand(
                    "for z in $THERMAL_BASE_PATH/thermal_zone*; do " +
                    "t=\$(cat \$z/type 2>/dev/null); " +
                    "if [ \"\$t\" = \"$sensorType\" ]; then " +
                    "cat \$z/temp 2>/dev/null; echo \" \$z\"; break; fi; done"
                )
                if (result.success && result.output.isNotBlank()) {
                    val parts = result.output.trim().split(" ")
                    if (parts.isNotEmpty()) {
                        val rawTemp = parts[0].toDoubleOrNull()
                        if (rawTemp != null && rawTemp > 0) {
                            val celsius = if (rawTemp > 1000) rawTemp / 1000.0 else rawTemp
                            if (celsius in 10.0..120.0) {
                                if (parts.size > 1) {
                                    gamingSensorZone = parts[1].replace("$THERMAL_BASE_PATH/", "")
                                    Log.d(TAG, "Using gaming sensor: $sensorType (${gamingSensorZone}) = ${celsius}°C")
                                }
                                return celsius
                            }
                        }
                    }
                }
            }

            // Fallback: read zone0 (usually generic)
            val fallback = readSingleZone("thermal_zone0")
            if (fallback > 0) return fallback

        } catch (e: Exception) {
            Log.e(TAG, "Gaming sensor discovery failed: ${e.message}")
        }

        return -1.0
    }

    private fun discoverAndReadChipSensor(): Double {
        try {
            for (sensorType in CHIP_SENSORS) {
                val result = ShizukuManager.runCommand(
                    "for z in $THERMAL_BASE_PATH/thermal_zone*; do " +
                    "t=\$(cat \$z/type 2>/dev/null); " +
                    "if [ \"\$t\" = \"$sensorType\" ]; then " +
                    "cat \$z/temp 2>/dev/null; echo \" \$z\"; break; fi; done"
                )
                if (result.success && result.output.isNotBlank()) {
                    val parts = result.output.trim().split(" ")
                    if (parts.isNotEmpty()) {
                        val rawTemp = parts[0].toDoubleOrNull()
                        if (rawTemp != null && rawTemp > 0) {
                            val celsius = if (rawTemp > 1000) rawTemp / 1000.0 else rawTemp
                            if (celsius in 10.0..120.0) {
                                if (parts.size > 1) {
                                    chipSensorZone = parts[1].replace("$THERMAL_BASE_PATH/", "")
                                }
                                return celsius
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chip sensor discovery failed: ${e.message}")
        }
        return -1.0
    }

    private fun readSingleZone(zone: String): Double {
        return try {
            val path = if (zone.startsWith("/")) zone else "$THERMAL_BASE_PATH/$zone"

            // Try direct read first
            val file = File("$path/temp")
            val rawStr = if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                // Use Shizuku
                val result = ShizukuManager.runCommand("cat $path/temp")
                if (result.success) result.output.trim() else return -1.0
            }

            val rawTemp = rawStr.toDoubleOrNull() ?: return -1.0
            val celsius = if (rawTemp > 1000) rawTemp / 1000.0 else rawTemp

            if (celsius in 10.0..120.0) celsius else -1.0
        } catch (e: Exception) {
            -1.0
        }
    }
}
