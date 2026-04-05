package com.bgmi.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceAlertManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAlertManager"
        private const val COOLDOWN_MS = 60_000L // Don't repeat same alert within 1 minute
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val lastAlertTimes = mutableMapOf<AlertType, Long>()

    enum class AlertType {
        HIGH_LOAD,
        THERMAL_WARNING,
        BATTERY_LOW,
        MODE_CHANGE,
        SHIZUKU_ERROR
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS init failed", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isReady) {
                tts?.setSpeechRate(1.1f)
                tts?.setPitch(0.9f)
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.w(TAG, "TTS language not supported")
            }
        } else {
            Log.e(TAG, "TTS init failed with status: $status")
        }
    }

    fun alertHighLoad(cpuPercent: Int) {
        speak(AlertType.HIGH_LOAD, "Warning. High CPU load at $cpuPercent percent. Performance may be affected.")
    }

    fun alertThermalWarning(tempCelsius: Double) {
        val rounded = String.format("%.1f", tempCelsius)
        speak(AlertType.THERMAL_WARNING, "Thermal warning. Device temperature at $rounded degrees. Switching to cooling mode.")
    }

    fun alertBatteryLow(percent: Int) {
        speak(AlertType.BATTERY_LOW, "Battery low. $percent percent remaining. Consider plugging in.")
    }

    fun alertModeChange(mode: String) {
        speak(AlertType.MODE_CHANGE, "Engine mode changed to $mode.")
    }

    fun alertShizukuError() {
        speak(AlertType.SHIZUKU_ERROR, "Warning. Shizuku connection lost. Optimizations paused.")
    }

    private fun speak(type: AlertType, message: String) {
        if (!isReady || tts == null) return

        val now = System.currentTimeMillis()
        val lastTime = lastAlertTimes[type] ?: 0L

        if (now - lastTime < COOLDOWN_MS) return

        try {
            lastAlertTimes[type] = now
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null, type.name)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            Log.e(TAG, "TTS shutdown failed", e)
        }
    }
}
