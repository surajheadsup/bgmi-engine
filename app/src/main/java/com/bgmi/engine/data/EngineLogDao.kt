package com.bgmi.engine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EngineLogDao {

    @Insert
    fun insert(log: EngineLogEntry)

    @Query("SELECT * FROM engine_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): List<EngineLogEntry>

    @Query("SELECT * FROM engine_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getLogsForSession(sessionId: Long): List<EngineLogEntry>

    @Query("SELECT * FROM engine_logs WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByType(type: String, limit: Int): List<EngineLogEntry>

    @Query("DELETE FROM engine_logs WHERE sessionId = :sessionId")
    fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM engine_logs")
    fun deleteAll()
}
