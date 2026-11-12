package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScreeningSnapshotEntity::class,
        OccupancyTimelineEntity::class,
        MonitoringGapEntity::class,
        MonitorHeartbeatEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlanetDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: PlanetDatabase? = null

        fun getInstance(context: Context): PlanetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanetDatabase::class.java,
                    "planet_checker_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
