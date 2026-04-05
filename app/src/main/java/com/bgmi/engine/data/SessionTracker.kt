package com.bgmi.engine.data

import android.content.Context
import android.util.Log

class SessionTracker(context: Context) {

    companion object {
        private const val TAG = "SessionTracker"
    }

    private val db = AppDatabase.getInstance(context)
    private val sessionDao = db.gameSessionDao()
    private val snapshotDao = db.statSnapshotDao()
    private val logDao = db.engineLogDao()

    private var currentSessionId: Long = -1
    private var sessionStartTime: Long = 0
    private var startBattery: Int = -1

    // Accumulators for averaging
    private var fpsSum = 0L
    private var tempSum = 0.0
    private var cpuSum = 0L
    private var ramSum = 0L
    private var snapshotCount = 0
    private var peakTemp = 0.0
    private var minBattery = 100
    private var modeChanges = 0
    private val modeCounts = mutableMapOf<String, Int>()

    // Snapshot buffer to batch inserts
    private val snapshotBuffer = mutableListOf<StatSnapshot>()

    fun startSession(batteryPercent: Int) {
        sessionStartTime = System.currentTimeMillis()
        startBattery = batteryPercent
        currentSessionId = -1
        fpsSum = 0L
        tempSum = 0.0
        cpuSum = 0L
        ramSum = 0L
        snapshotCount = 0
        peakTemp = 0.0
        minBattery = batteryPercent
        modeChanges = 0
        modeCounts.clear()
        snapshotBuffer.clear()

        Log.d(TAG, "Session tracking started")
    }

    fun recordSnapshot(
        fps: Int,
        temp: Double,
        cpu: Int,
        ram: Int,
        battery: Int,
        ping: Int,
        mode: String
    ) {
        val now = System.currentTimeMillis()
        val elapsed = now - sessionStartTime

        snapshotCount++
        if (fps > 0) fpsSum += fps
        if (temp > 0) tempSum += temp
        if (cpu >= 0) cpuSum += cpu
        if (ram >= 0) ramSum += ram
        if (temp > peakTemp) peakTemp = temp
        if (battery in 1 until minBattery) minBattery = battery

        modeCounts[mode] = (modeCounts[mode] ?: 0) + 1

        snapshotBuffer.add(
            StatSnapshot(
                sessionId = 0, // Will be updated on flush
                timestamp = now,
                elapsedMs = elapsed,
                fps = fps,
                tempCelsius = temp,
                cpuPercent = cpu,
                ramPercent = ram,
                batteryPercent = battery,
                pingMs = ping,
                mode = mode
            )
        )

        // Flush buffer every 30 snapshots
        if (snapshotBuffer.size >= 30) {
            flushSnapshots()
        }
    }

    fun recordModeChange() {
        modeChanges++
    }

    fun logEvent(type: String, message: String) {
        try {
            val entry = EngineLogEntry(
                sessionId = currentSessionId.coerceAtLeast(0),
                timestamp = System.currentTimeMillis(),
                type = type,
                message = message
            )
            logDao.insert(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event", e)
        }
    }

    fun endSession(thermalHits: Int, stabilizerRuns: Int, performanceScore: Int, endBattery: Int) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - sessionStartTime

        if (duration < 10_000) {
            Log.d(TAG, "Session too short (<10s), discarding")
            snapshotBuffer.clear()
            return
        }

        val fpsCount = if (fpsSum > 0) snapshotCount else 1
        val tempCount = if (tempSum > 0) snapshotCount else 1

        val session = GameSession(
            startTime = sessionStartTime,
            endTime = endTime,
            durationMs = duration,
            avgFps = if (fpsSum > 0) (fpsSum / fpsCount).toInt() else 0,
            avgTemp = if (tempSum > 0) tempSum / tempCount else 0.0,
            avgCpu = if (cpuSum > 0) (cpuSum / snapshotCount.coerceAtLeast(1)).toInt() else 0,
            avgRam = if (ramSum > 0) (ramSum / snapshotCount.coerceAtLeast(1)).toInt() else 0,
            peakTemp = peakTemp,
            minBattery = minBattery,
            startBattery = startBattery,
            endBattery = endBattery,
            thermalHits = thermalHits,
            modeChanges = modeChanges,
            stabilizerRuns = stabilizerRuns,
            performanceScore = performanceScore,
            dominantMode = modeCounts.maxByOrNull { it.value }?.key ?: "EXTREME"
        )

        try {
            currentSessionId = sessionDao.insert(session)
            Log.d(TAG, "Session saved with id=$currentSessionId, duration=${duration / 1000}s")

            // Flush remaining snapshots with correct session ID
            flushSnapshots()

            // Log session end
            logEvent("INFO", "Session ended: ${duration / 60000}min, score=$performanceScore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    private fun flushSnapshots() {
        if (snapshotBuffer.isEmpty()) return

        try {
            val sessionId = currentSessionId.coerceAtLeast(0)
            val toInsert = snapshotBuffer.map { it.copy(sessionId = sessionId) }
            snapshotDao.insertAll(toInsert)
            snapshotBuffer.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush snapshots", e)
        }
    }

    fun getCurrentSessionId(): Long = currentSessionId
}
