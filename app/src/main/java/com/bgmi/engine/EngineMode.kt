package com.bgmi.engine

import android.content.Context

enum class EngineMode(
    val displayName: String,
    val color: Int
) {
    EXTREME("EXTREME", 0xFF4ECB71.toInt()),    // Green  — cool device, full power
    BALANCED("BALANCED", 0xFFFFD700.toInt()),   // Yellow — warming up, moderate throttle
    SAFE("SAFE", 0xFFFF6B6B.toInt());           // Red    — hot device, aggressive throttle

    companion object {
        // Mode switch cooldown — prevents rapid flapping
        private const val MODE_SWITCH_COOLDOWN_MS = 60_000L  // 60 seconds minimum between switches
        private var lastModeSwitchTime = 0L

        // Rolling temperature average to filter sensor noise
        private val tempHistory = mutableListOf<Double>()
        private const val TEMP_HISTORY_SIZE = 5  // Average over last 5 readings

        /**
         * Record a temperature reading and return smoothed value.
         * Filters out sensor noise spikes.
         */
        fun recordTemp(rawTemp: Double): Double {
            if (rawTemp <= 0) return -1.0

            tempHistory.add(rawTemp)
            if (tempHistory.size > TEMP_HISTORY_SIZE) {
                tempHistory.removeAt(0)
            }

            if (tempHistory.size < 2) return rawTemp

            // Remove outliers: drop highest and lowest, average the rest
            val sorted = tempHistory.sorted()
            val trimmed = if (sorted.size >= 4) {
                sorted.subList(1, sorted.size - 1)
            } else {
                sorted
            }
            return trimmed.average()
        }

        /**
         * Determines engine mode based on smoothed temperature.
         * Uses configurable thresholds from EnginePrefs + cooldown to prevent flapping.
         */
        fun fromTemperature(context: Context, rawTemp: Double, currentMode: EngineMode): EngineMode {
            if (rawTemp < 0) return currentMode

            val smoothedTemp = recordTemp(rawTemp)
            if (smoothedTemp < 0) return currentMode

            // Read thresholds from settings
            val warningTemp = EnginePrefs.getWarningTemp(context).toDouble()   // Default 58
            val emergencyTemp = EnginePrefs.getEmergencyTemp(context).toDouble() // Default 64

            // Hysteresis: need to drop further to recover
            val balancedRecover = warningTemp - 5.0   // e.g. 53°C to go back to EXTREME
            val extremeRecover = warningTemp - 8.0     // e.g. 50°C to go from SAFE → EXTREME

            val targetMode = when (currentMode) {
                EXTREME -> when {
                    smoothedTemp >= emergencyTemp -> SAFE
                    smoothedTemp >= warningTemp -> BALANCED
                    else -> EXTREME
                }
                BALANCED -> when {
                    smoothedTemp >= emergencyTemp -> SAFE
                    smoothedTemp <= balancedRecover -> EXTREME
                    else -> BALANCED
                }
                SAFE -> when {
                    smoothedTemp <= extremeRecover -> EXTREME
                    smoothedTemp <= balancedRecover -> BALANCED
                    else -> SAFE
                }
            }

            // Cooldown check — don't switch if too soon
            if (targetMode != currentMode) {
                val now = System.currentTimeMillis()
                if (now - lastModeSwitchTime < MODE_SWITCH_COOLDOWN_MS) {
                    return currentMode // Too soon, stay in current mode
                }
                lastModeSwitchTime = now
            }

            return targetMode
        }

        fun resetState() {
            tempHistory.clear()
            lastModeSwitchTime = 0L
        }
    }
}
