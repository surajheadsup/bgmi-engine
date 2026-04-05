package com.bgmi.engine.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stat_snapshots")
data class StatSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val elapsedMs: Long,
    val fps: Int,
    val tempCelsius: Double,
    val cpuPercent: Int,
    val ramPercent: Int,
    val batteryPercent: Int,
    val pingMs: Int,
    val mode: String
)
