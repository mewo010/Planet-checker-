package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "screening_snapshots")
data class ScreeningSnapshotEntity(
    @PrimaryKey val screeningKey: String,
    val presentationId: String?,
    val title: String,
    val room: String,
    val screeningDate: String, // ISO YYYY-MM-DD
    val startTime: String,
    val endTime: String,
    val startMin: Int,
    val endMin: Int,
    val estimatedOccupiedSeats: Int?,
    val availableSeats: Int?,
    val totalClassifiedSeats: Int?,
    val roomCapacity: Int?,
    val availabilityRatio: Double?,
    val availabilitySource: String,
    val attendanceKind: String,
    val snapshotTimestampEpochMs: Long,
    val hasCompleted: Boolean,
    val isFinal: Boolean
)

@Entity(
    tableName = "occupancy_timeline",
    indices = [Index(value = ["screeningDate", "bucketMinute"], unique = true)]
)
data class OccupancyTimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val screeningDate: String,
    val bucketMinute: Int,
    val labelTime: String,
    val totalCurrentViewers: Int,
    val activeScreeningsCount: Int,
    val knownActiveCount: Int,
    val timestampEpochMs: Long
)

@Entity(
    tableName = "monitoring_gaps",
    indices = [Index(value = ["screeningDate", "startEpochMs"], unique = true)]
)
data class MonitoringGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val screeningDate: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val reason: String
)

@Entity(tableName = "monitor_heartbeat")
data class MonitorHeartbeatEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncEpochMs: Long,
    val lastCinemaDate: String,
    val wasOffline: Boolean = false
)
