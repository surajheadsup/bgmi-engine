package com.bgmi.engine.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "engine_logs")
data class EngineLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val type: String,    // THERMAL, MODE, KILL, SHIZUKU, ERROR, INFO
    val message: String
)
