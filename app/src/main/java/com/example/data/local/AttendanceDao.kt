package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM screening_snapshots WHERE screeningDate = :date ORDER BY startMin ASC, room ASC")
    fun observeSnapshotsForDate(date: String): Flow<List<ScreeningSnapshotEntity>>

    @Query("SELECT * FROM screening_snapshots WHERE screeningDate = :date ORDER BY startMin ASC, room ASC")
    suspend fun getSnapshotsForDate(date: String): List<ScreeningSnapshotEntity>

    @Query("SELECT * FROM screening_snapshots WHERE screeningKey = :key LIMIT 1")
    suspend fun getSnapshotByKey(key: String): ScreeningSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<ScreeningSnapshotEntity>)

    @Query("SELECT DISTINCT screeningDate FROM screening_snapshots ORDER BY screeningDate DESC")
    fun observeAvailableDates(): Flow<List<String>>

    @Query("SELECT * FROM occupancy_timeline WHERE screeningDate = :date ORDER BY bucketMinute ASC")
    fun observeTimelineForDate(date: String): Flow<List<OccupancyTimelineEntity>>

    @Query("SELECT * FROM occupancy_timeline WHERE screeningDate = :date ORDER BY bucketMinute ASC")
    suspend fun getTimelineForDate(date: String): List<OccupancyTimelineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelinePoint(point: OccupancyTimelineEntity)

    @Query("SELECT * FROM monitoring_gaps WHERE screeningDate = :date ORDER BY startEpochMs ASC")
    fun observeGapsForDate(date: String): Flow<List<MonitoringGapEntity>>

    @Query("SELECT * FROM monitoring_gaps WHERE screeningDate = :date ORDER BY startEpochMs ASC")
    suspend fun getGapsForDate(date: String): List<MonitoringGapEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGap(gap: MonitoringGapEntity)

    @Query("SELECT * FROM monitor_heartbeat WHERE id = 1 LIMIT 1")
    suspend fun getHeartbeat(): MonitorHeartbeatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHeartbeat(heartbeat: MonitorHeartbeatEntity)
}
