package com.bgmi.engine.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GameSession::class, StatSnapshot::class, EngineLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameSessionDao(): GameSessionDao
    abstract fun statSnapshotDao(): StatSnapshotDao
    abstract fun engineLogDao(): EngineLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bgmi_engine_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
