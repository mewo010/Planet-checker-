package com.example.data.repository

import com.example.data.local.AttendanceDao
import com.example.data.local.MonitorHeartbeatEntity
import com.example.data.local.MonitoringGapEntity
import com.example.data.local.OccupancyTimelineEntity
import com.example.data.local.ScreeningSnapshotEntity
import com.example.data.model.AttendanceKind
import com.example.data.model.AvailabilitySource
import com.example.data.model.DailyScreeningBreakdownItem
import com.example.data.model.DailyStatisticsSummary
import com.example.data.model.DailyTimelinePoint
import com.example.data.model.MonitoringGapInfo
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.deduplicateScreenings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Coordinates durable attendance snapshots, monitoring gap detection, and daily statistics
 * backed by Room persistence.
 */
class AttendanceHistoryRepository(
    private val dao: AttendanceDao,
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() }
) {
    private val jerusalemZone = ZoneId.of("Asia/Jerusalem")
    private val hhmmFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun observeAvailableDates(): Flow<List<String>> = dao.observeAvailableDates()

    fun observeDailyStats(
        dateIso: String,
        todayIso: String,
        liveScreeningsProvider: () -> List<Screening>,
        nowProvider: () -> ZonedDateTime
    ): Flow<DailyStatisticsSummary> {
        return combine(
            dao.observeSnapshotsForDate(dateIso),
            dao.observeTimelineForDate(dateIso),
            dao.observeGapsForDate(dateIso)
        ) { snapshots, timeline, gaps ->
            buildDailyStatsSummary(
                dateIso = dateIso,
                isToday = dateIso == todayIso,
                snapshots = snapshots,
                timeline = timeline,
                gaps = gaps,
                liveScreenings = if (dateIso == todayIso) liveScreeningsProvider() else emptyList(),
                now = nowProvider()
            )
        }
    }

    suspend fun getDailyStatsOnce(
        dateIso: String,
        todayIso: String,
        liveScreenings: List<Screening> = emptyList(),
        now: ZonedDateTime = ZonedDateTime.now(jerusalemZone)
    ): DailyStatisticsSummary {
        val snapshots = dao.getSnapshotsForDate(dateIso)
        val timeline = dao.getTimelineForDate(dateIso)
        val gaps = dao.getGapsForDate(dateIso)
        return buildDailyStatsSummary(
            dateIso = dateIso,
            isToday = dateIso == todayIso,
            snapshots = snapshots,
            timeline = timeline,
            gaps = gaps,
            liveScreenings = if (dateIso == todayIso) liveScreenings else emptyList(),
            now = now
        )
    }

    /**
     * Records live screening snapshots to Room while strictly obeying accuracy rules:
     * - Never stores sample fallback data as real attendance.
     * - Never overwrites an already finalized (isFinal = true) snapshot.
     * - Never queries ended screenings or fabricates attendance for screenings that ended before monitoring began.
     * - Records monitoring gaps if app/network stopped for > [GAP_THRESHOLD_MS].
     */
    suspend fun recordLiveObservation(
        screenings: List<Screening>,
        cinemaDate: LocalDate,
        now: ZonedDateTime,
        cinemaNowMin: Int
    ) {
        val nowMs = timeProviderMs()
        val dateIso = cinemaDate.toString()

        // Track monitoring continuity and detect gaps
        val lastHeartbeat = dao.getHeartbeat()
        if (lastHeartbeat != null &&
            lastHeartbeat.lastCinemaDate == dateIso &&
            (nowMs - lastHeartbeat.lastSyncEpochMs) > GAP_THRESHOLD_MS
        ) {
            dao.insertGap(
                MonitoringGapEntity(
                    screeningDate = dateIso,
                    startEpochMs = lastHeartbeat.lastSyncEpochMs,
                    endEpochMs = nowMs,
                    reason = if (lastHeartbeat.wasOffline) "NETWORK_UNAVAILABLE" else "APP_INACTIVE_OR_STOPPED"
                )
            )
        }
        dao.upsertHeartbeat(
            MonitorHeartbeatEntity(
                lastSyncEpochMs = nowMs,
                lastCinemaDate = dateIso,
                wasOffline = false
            )
        )

        val uniqueScreenings = deduplicateScreenings(screenings)
        val existingList = dao.getSnapshotsForDate(dateIso).associateBy { it.screeningKey }
        val toUpsert = mutableListOf<ScreeningSnapshotEntity>()

        for (screening in uniqueScreenings) {
            if (screening.availabilitySource == AvailabilitySource.SAMPLE_FALLBACK) continue

            val key = buildScreeningKey(dateIso, screening)
            val existing = existingList[key]

            val statusNow = screening.statusAt(now)
            val isEnded = statusNow == ScreeningStatus.ENDED || statusNow == ScreeningStatus.ENDED_RECENTLY

            // Preserve already finalized snapshots untouched (only updating completion flag once ended)
            if (existing != null && existing.isFinal) {
                if (isEnded && !existing.hasCompleted) {
                    toUpsert.add(existing.copy(hasCompleted = true))
                }
                continue
            }

            if (isEnded) {
                if (existing != null && existing.estimatedOccupiedSeats != null) {
                    // Preserve the last pre-end observed estimate as the final snapshot
                    toUpsert.add(
                        existing.copy(
                            hasCompleted = true,
                            isFinal = true
                        )
                    )
                } else {
                    // Screening already ended before any valid occupancy observation was captured:
                    // Do NOT invent or assume post-end seat availability equals historical attendance.
                    toUpsert.add(
                        ScreeningSnapshotEntity(
                            screeningKey = key,
                            presentationId = screening.presentationId,
                            title = screening.title,
                            room = screening.room,
                            screeningDate = dateIso,
                            startTime = screening.startStr,
                            endTime = screening.endStr,
                            startMin = screening.startMin,
                            endMin = screening.endMin,
                            estimatedOccupiedSeats = null,
                            availableSeats = null,
                            totalClassifiedSeats = null,
                            roomCapacity = screening.roomCapacity(),
                            availabilityRatio = null,
                            availabilitySource = AvailabilitySource.NONE.name,
                            attendanceKind = AttendanceKind.UNKNOWN.name,
                            snapshotTimestampEpochMs = nowMs,
                            hasCompleted = true,
                            isFinal = true
                        )
                    )
                }
                continue
            }

            // Active or upcoming screening
            val occupied = screening.estimatedOccupiedSeats()
            val capacity = screening.roomCapacity()
            val usableOrFallbackCap = screening.usableHallCapacity() ?: capacity
            val summary = screening.seatSummary
            val available = when {
                summary != null && summary.totalValidSeats > 0 -> summary.availableSeats
                occupied != null && usableOrFallbackCap != null -> (usableOrFallbackCap - occupied).coerceAtLeast(0)
                else -> null
            }
            val totalClassified = summary?.totalValidSeats?.takeIf { it > 0 } ?: if (occupied != null) capacity else null

            val isNearEnd = screening.status == ScreeningStatus.LIVE &&
                (screening.endMin - cinemaNowMin) in 0..FINAL_CAPTURE_WINDOW_MIN

            val attendanceKind = when {
                occupied == null -> AttendanceKind.UNKNOWN
                else -> AttendanceKind.ESTIMATED
            }

            val effectiveIsFinal = isNearEnd && occupied != null

            // Avoid overwriting a richer known estimate with an unknown one
            if (occupied == null && existing != null && existing.estimatedOccupiedSeats != null) {
                continue
            }

            toUpsert.add(
                ScreeningSnapshotEntity(
                    screeningKey = key,
                    presentationId = screening.presentationId,
                    title = screening.title,
                    room = screening.room,
                    screeningDate = dateIso,
                    startTime = screening.startStr,
                    endTime = screening.endStr,
                    startMin = screening.startMin,
                    endMin = screening.endMin,
                    estimatedOccupiedSeats = occupied,
                    availableSeats = available,
                    totalClassifiedSeats = totalClassified,
                    roomCapacity = capacity,
                    availabilityRatio = screening.availabilityRatio?.toDouble(),
                    availabilitySource = screening.availabilitySource.name,
                    attendanceKind = attendanceKind.name,
                    snapshotTimestampEpochMs = nowMs,
                    hasCompleted = false,
                    isFinal = effectiveIsFinal
                )
            )
        }

        if (toUpsert.isNotEmpty()) {
            dao.upsertSnapshots(toUpsert.distinctBy { it.screeningKey })
        }

        // Save timeline bucket point (15-min bucket so repeated refreshes don't create spam rows)
        val bucketMinute = (cinemaNowMin / TIMELINE_BUCKET_MIN) * TIMELINE_BUCKET_MIN
        val wrapped = ((bucketMinute % 1440) + 1440) % 1440
        val labelTime = String.format("%02d:%02d", wrapped / 60, wrapped % 60)

        val occupancy = calculateCinemaOccupancy(uniqueScreenings, now)

        dao.upsertTimelinePoint(
            OccupancyTimelineEntity(
                screeningDate = dateIso,
                bucketMinute = bucketMinute,
                labelTime = labelTime,
                totalCurrentViewers = occupancy.occupiedSeats,
                activeScreeningsCount = occupancy.activeScreenings,
                knownActiveCount = occupancy.countedScreenings,
                timestampEpochMs = nowMs
            )
        )
    }

    /**
     * Records an offline sync attempt so monitoring gaps accurately note network outages.
     */
    suspend fun recordOfflineAttempt(cinemaDate: LocalDate) {
        val nowMs = timeProviderMs()
        val dateIso = cinemaDate.toString()
        val lastHeartbeat = dao.getHeartbeat()
        if (lastHeartbeat == null) {
            dao.upsertHeartbeat(
                MonitorHeartbeatEntity(
                    lastSyncEpochMs = nowMs,
                    lastCinemaDate = dateIso,
                    wasOffline = true
                )
            )
            return
        }
        if (lastHeartbeat.lastCinemaDate == dateIso && (nowMs - lastHeartbeat.lastSyncEpochMs) > GAP_THRESHOLD_MS) {
            dao.insertGap(
                MonitoringGapEntity(
                    screeningDate = dateIso,
                    startEpochMs = lastHeartbeat.lastSyncEpochMs,
                    endEpochMs = nowMs,
                    reason = "NETWORK_UNAVAILABLE"
                )
            )
        }
        dao.upsertHeartbeat(
            lastHeartbeat.copy(
                lastCinemaDate = dateIso,
                wasOffline = true
            )
        )
    }

    fun buildDailyStatsSummary(
        dateIso: String,
        isToday: Boolean,
        snapshots: List<ScreeningSnapshotEntity>,
        timeline: List<OccupancyTimelineEntity>,
        gaps: List<MonitoringGapEntity>,
        liveScreenings: List<Screening>,
        now: ZonedDateTime
    ): DailyStatisticsSummary {
        // Deduplicate by screeningKey and by canonical slot (room+start+title), preferring richer/newer entries
        val uniqueSnapshots = snapshots
            .sortedWith(
                compareByDescending<ScreeningSnapshotEntity> { it.isFinal }
                    .thenByDescending { it.estimatedOccupiedSeats != null }
                    .thenByDescending { it.snapshotTimestampEpochMs }
            )
            .distinctBy { it.screeningKey }
            .distinctBy { "${it.screeningDate}|${com.example.data.model.canonicalSlotKey(it.room, it.startTime, it.title)}" }

        val todayOccupancy = if (isToday) {
            calculateCinemaOccupancy(liveScreenings, now)
        } else null
        val currentlyRunningCount = todayOccupancy?.activeScreenings ?: 0
        val currentViewers = todayOccupancy?.occupiedSeats ?: 0

        val completedSnapshots = uniqueSnapshots.filter { it.hasCompleted }
        val upcomingCount = uniqueSnapshots.count { !it.hasCompleted } - currentlyRunningCount

        var completedAttendanceSum = 0
        var completedKnown = 0
        var completedUnknown = 0

        var totalAttendanceSum = 0
        var totalKnown = 0
        var totalUnknown = 0

        var totalAvailableSum = 0
        var anyKnownAvailable = false
        var totalOccupiedSum = 0
        var anyKnownOccupied = false

        var confirmedCount = 0
        var estimatedCount = 0
        var unknownCount = 0

        val breakdowns = uniqueSnapshots.map { snap ->
            val kind = runCatching { AttendanceKind.valueOf(snap.attendanceKind) }
                .getOrDefault(AttendanceKind.UNKNOWN)
            val source = runCatching { AvailabilitySource.valueOf(snap.availabilitySource) }
                .getOrDefault(AvailabilitySource.NONE)

            when (kind) {
                AttendanceKind.CONFIRMED -> confirmedCount++
                AttendanceKind.ESTIMATED -> estimatedCount++
                AttendanceKind.UNKNOWN -> unknownCount++
            }

            val occ = snap.estimatedOccupiedSeats
            if (occ != null) {
                totalAttendanceSum += occ
                totalKnown++
                totalOccupiedSum += occ
                anyKnownOccupied = true
                if (snap.hasCompleted) {
                    completedAttendanceSum += occ
                    completedKnown++
                }
            } else {
                totalUnknown++
                if (snap.hasCompleted) {
                    completedUnknown++
                }
            }

            val avail = snap.availableSeats
            if (avail != null) {
                totalAvailableSum += avail
                anyKnownAvailable = true
            }

            DailyScreeningBreakdownItem(
                screeningKey = snap.screeningKey,
                presentationId = snap.presentationId,
                title = snap.title,
                room = snap.room,
                startTime = snap.startTime,
                endTime = snap.endTime,
                startMin = snap.startMin,
                endMin = snap.endMin,
                estimatedOccupiedSeats = occ,
                availableSeats = avail,
                roomCapacity = snap.roomCapacity,
                attendanceKind = kind,
                availabilitySource = source,
                hasCompleted = snap.hasCompleted,
                isFinal = snap.isFinal,
                snapshotTimestampEpochMs = snap.snapshotTimestampEpochMs
            )
        }

        val timelinePoints = timeline.map {
            DailyTimelinePoint(
                bucketMinute = it.bucketMinute,
                labelTime = it.labelTime,
                totalCurrentViewers = it.totalCurrentViewers,
                activeScreeningsCount = it.activeScreeningsCount,
                knownActiveCount = it.knownActiveCount
            )
        }

        val gapInfos = gaps.map { gap ->
            val startStr = Instant.ofEpochMilli(gap.startEpochMs).atZone(jerusalemZone).format(hhmmFormatter)
            val endStr = Instant.ofEpochMilli(gap.endEpochMs).atZone(jerusalemZone).format(hhmmFormatter)
            MonitoringGapInfo(
                startEpochMs = gap.startEpochMs,
                endEpochMs = gap.endEpochMs,
                reason = gap.reason,
                startLabel = startStr,
                endLabel = endStr
            )
        }

        return DailyStatisticsSummary(
            date = dateIso,
            isToday = isToday,
            totalScreeningsToday = uniqueSnapshots.size,
            currentlyRunningScreenings = currentlyRunningCount,
            completedScreenings = completedSnapshots.size,
            upcomingScreenings = upcomingCount.coerceAtLeast(0),
            estimatedCurrentViewers = currentViewers,
            estimatedCompletedAttendance = if (completedKnown > 0) completedAttendanceSum else null,
            completedWithKnownAttendance = completedKnown,
            completedWithUnknownAttendance = completedUnknown,
            estimatedTotalAttendanceToday = if (totalKnown > 0) totalAttendanceSum else null,
            totalWithKnownAttendance = totalKnown,
            totalWithUnknownAttendance = totalUnknown,
            totalKnownAvailableSeats = if (anyKnownAvailable) totalAvailableSum else null,
            totalKnownOccupiedSeats = if (anyKnownOccupied) totalOccupiedSum else null,
            confirmedCount = confirmedCount,
            estimatedCount = estimatedCount,
            unknownCount = unknownCount,
            timelinePoints = timelinePoints,
            monitoringGaps = gapInfos,
            screeningBreakdowns = breakdowns
        )
    }

    companion object {
        const val FINAL_CAPTURE_WINDOW_MIN = 20
        const val TIMELINE_BUCKET_MIN = 15
        const val GAP_THRESHOLD_MS = 35 * 60 * 1000L

        fun buildScreeningKey(dateIso: String, screening: Screening): String {
            val pid = screening.presentationId?.takeIf { it.isNotBlank() }
            return if (pid != null) {
                "$dateIso|pid:$pid"
            } else {
                "$dateIso|${screening.room}|${screening.startStr}|${screening.title}"
            }
        }
    }
}
