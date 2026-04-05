package com.bgmi.engine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GameSessionDao {

    @Insert
    fun insert(session: GameSession): Long

    @Query("SELECT * FROM game_sessions ORDER BY startTime DESC")
    fun getAllSessions(): List<GameSession>

    @Query("SELECT * FROM game_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): List<GameSession>

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    fun getSessionById(id: Long): GameSession?

    @Query("SELECT COUNT(*) FROM game_sessions")
    fun getSessionCount(): Int

    @Query("SELECT SUM(durationMs) FROM game_sessions")
    fun getTotalPlayTimeMs(): Long?

    @Query("SELECT AVG(avgFps) FROM game_sessions WHERE avgFps > 0")
    fun getOverallAvgFps(): Double?

    @Query("SELECT AVG(avgTemp) FROM game_sessions WHERE avgTemp > 0")
    fun getOverallAvgTemp(): Double?

    @Query("SELECT AVG(performanceScore) FROM game_sessions")
    fun getOverallAvgScore(): Double?

    @Query("SELECT MAX(peakTemp) FROM game_sessions")
    fun getHighestPeakTemp(): Double?

    @Query("SELECT SUM(thermalHits) FROM game_sessions")
    fun getTotalThermalHits(): Int?

    @Query("SELECT * FROM game_sessions WHERE startTime >= :since ORDER BY performanceScore DESC LIMIT 1")
    fun getBestSessionSince(since: Long): GameSession?

    @Query("SELECT * FROM game_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    fun getSessionsSince(since: Long): List<GameSession>

    @Query("DELETE FROM game_sessions WHERE id = :id")
    fun deleteSession(id: Long)

    @Query("DELETE FROM game_sessions")
    fun deleteAll()
}
