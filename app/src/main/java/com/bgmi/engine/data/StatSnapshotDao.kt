package com.bgmi.engine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StatSnapshotDao {

    @Insert
    fun insert(snapshot: StatSnapshot)

    @Insert
    fun insertAll(snapshots: List<StatSnapshot>)

    @Query("SELECT * FROM stat_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSnapshotsForSession(sessionId: Long): List<StatSnapshot>

    @Query("DELETE FROM stat_snapshots WHERE sessionId = :sessionId")
    fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM stat_snapshots")
    fun deleteAll()
}
