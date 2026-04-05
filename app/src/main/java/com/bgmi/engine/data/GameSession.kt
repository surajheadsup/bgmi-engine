package com.bgmi.engine.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val avgFps: Int,
    val avgTemp: Double,
    val avgCpu: Int,
    val avgRam: Int,
    val peakTemp: Double,
    val minBattery: Int,
    val startBattery: Int,
    val endBattery: Int,
    val thermalHits: Int,
    val modeChanges: Int,
    val stabilizerRuns: Int,
    val performanceScore: Int,
    val dominantMode: String // EXTREME, BALANCED, SAFE
)
