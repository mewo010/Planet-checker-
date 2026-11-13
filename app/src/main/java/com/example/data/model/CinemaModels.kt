package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.data.diagnostics.LiveOccupancyValidationSnapshot
import com.example.data.diagnostics.ScreeningSourceSnapshot
import com.example.data.diagnostics.SourceTelemetryBundle
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatFetchResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private val ISRAEL_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

enum class RoomType(val label: String, val badgeColor: Color, val containerColor: Color) {
    IMAX("IMAX", Color(0xFF38BDF8), Color(0xFF082F49)),
    VIP("VIP", Color(0xFFFACC15), Color(0xFF422006)),
    FOUR_DX("4DX", Color(0xFFA78BFA), Color(0xFF2E1065)),
    SCREEN_X("ScreenX", Color(0xFF34D399), Color(0xFF064E3B)),
    TWO_D("2D", Color(0xFF94A3B8), Color(0xFF1E293B));

    companion object {
        fun fromRoom(room: String): RoomType {
            val num = extractRoomNumber(room) ?: return TWO_D
            return when {
                num == 5 -> IMAX
                num == 19 -> SCREEN_X
                num == 20 -> FOUR_DX
                num in 21..24 -> VIP
                else -> TWO_D
            }
        }

        fun extractRoomNumber(room: String): Int? {
            val regex = Regex("\\d+")
            val match = regex.find(room)
            return match?.value?.toIntOrNull()
        }

        fun formatRoomLabel(room: String): String {
            return if (room.contains("אולם")) room else "אולם $room"
        }
    }
}

enum class ScreeningStatus {
    LIVE,
    UPCOMING,
    ENDED_RECENTLY,
    ENDED
}

enum class CrowdLevel(val label: String, val color: Color, val progressColor: Color) {
    QUIET("שקט", Color(0xFF34D399), Color(0xFF10B981)),
    MEDIUM("בינוני", Color(0xFF38BDF8), Color(0xFF0284C7)),
    BUSY("עמוס", Color(0xFFFBBF24), Color(0xFFF59E0B)),
    VERY_BUSY("עמוס מאוד", Color(0xFFFB7185), Color(0xFFE11D48)),
    UNKNOWN("אין נתונים", Color(0xFF94A3B8), Color(0xFF475569))
}

internal fun normalizeTimeString(rawTime: String): String {
    val parts = rawTime.trim().split(":")
    if (parts.size < 2) return rawTime.trim()
    val h = parts[0].trim().toIntOrNull() ?: return rawTime.trim()
    val m = parts[1].trim().toIntOrNull() ?: return rawTime.trim()
    return String.format("%02d:%02d", h, m)
}

fun canonicalSlotKey(room: String, start: String, title: String): String {
    val roomNum = RoomType.extractRoomNumber(room)
    val roomNorm = roomNum?.let { "R$it" } ?: room.trim().replace(Regex("\\s+"), " ").lowercase()
    val timeNorm = normalizeTimeString(start)
    val titleNorm = title.trim().replace(Regex("\\s+"), " ").lowercase()
    return "$roomNorm|$timeNorm|$titleNorm"
}

enum class OccupancyCountKind(val hebrewLabel: String) {
    VERIFIED_SEAT_MAP("מפת מושבים מאומתת"),
    RATIO_ESTIMATE("אומדן יחס תפוסה"),
    UNKNOWN("לא ידוע")
}

enum class UsableCapacityStatus(val hebrewLabel: String) {
    KNOWN("קיבולת שמישה ידועה"),
    UNKNOWN_BROKEN_SEATS("מושבים תקולים לא ידועים"),
    UNKNOWN_HALL("אולם לא מוכר")
}

data class HallCapacityInfo(
    val hallNumber: Int?,
    val originalCapacity: Int?,
    val brokenSeats: Int?,
    val usableCapacity: Int?,
    val isBrokenSeatsKnown: Boolean,
    val status: UsableCapacityStatus
)

/**
 * Computes usable capacity = originalCapacity - brokenSeats, clamped >= 0.
 * - Returns null when either originalCapacity or brokenSeats is null (never silently treats null as 0).
 * - Preserves originalCapacity when brokenSeats == 0 (confirmed no broken seats).
 */
fun calculateUsableCapacity(originalCapacity: Int?, brokenSeats: Int?): Int? {
    if (originalCapacity == null || brokenSeats == null) return null
    val sanitizedBroken = brokenSeats.coerceAtLeast(0)
    return (originalCapacity - sanitizedBroken).coerceAtLeast(0)
}

/**
 * Computes usable/bookable capacity from a verified live seat map without double-subtracting
 * broken seats that are already omitted from the map or classified as BLOCKED/UNAVAILABLE/UNKNOWN.
 */
fun resolveLiveSeatMapUsableCapacity(
    originalCapacity: Int?,
    manualBrokenSeats: Int?,
    summary: SeatAvailabilitySummary
): Int {
    val sellableInMap = (summary.totalValidSeats - summary.blockedSeats - summary.unavailableSeats)
        .coerceAtLeast(0)
    val omittedFromOriginal = originalCapacity?.let {
        (it - summary.totalRawSeatObjects).coerceAtLeast(0)
    } ?: 0
    val alreadyExcludedByMap = summary.blockedSeats +
        summary.unavailableSeats +
        summary.unknownSeats +
        omittedFromOriginal
    val remainingManualBroken = if (manualBrokenSeats != null) {
        (manualBrokenSeats.coerceAtLeast(0) - alreadyExcludedByMap).coerceAtLeast(0)
    } else {
        0
    }
    return (sellableInMap - remainingManualBroken).coerceAtLeast(0)
}

data class QuickBookSourceStatus(
    val stateLabel: String = "טרם נבדק",
    val httpCode: Int? = null,
    val totalOfficialEvents: Int = 0,
    val recoveredMissingScreenings: Int = 0,
    val recoveredPresentationIds: Int = 0,
    val isReachable: Boolean = false,
    val errorDetail: String? = null
)

@JsonClass(generateAdapter = true)
data class RawScreening(
    @param:Json(name = "title") val title: String = "",
    @param:Json(name = "room") val room: String = "",
    @param:Json(name = "start") val start: String = "",
    @param:Json(name = "end") val end: String = "",
    @param:Json(name = "availabilityRatio") val availabilityRatio: Float? = null,
    @param:Json(name = "presentationId") val presentationId: String? = null,
    @param:Json(name = "presentation_id") val presentationIdSnake: String? = null,
    @param:Json(name = "presentationCode") val presentationCode: String? = null,
    @param:Json(name = "performanceId") val performanceId: String? = null,
    @param:Json(name = "sessionId") val sessionId: String? = null,
    @param:Json(name = "id") val id: String? = null,
    @param:Json(name = "bookingUrl") val bookingUrl: String? = null,
    @param:Json(name = "url") val url: String? = null,
    @param:Json(name = "venueTypeId") val venueTypeId: Int? = null,
    @param:Json(name = "date") val date: String? = null,
    @param:Json(name = "soldOut") val soldOut: Boolean? = null
) {
    /**
     * Resolves presentationId ONLY from real schedule feed fields or booking URL parameters.
     * Rejects placeholder values like "null", "undefined", or "0".
     */
    fun resolvedPresentationId(): String? {
        val direct = listOf(presentationId, presentationIdSnake, performanceId, sessionId, id)
            .asSequence()
            .mapNotNull { it?.trim() }
            .firstOrNull { isValidPresentationId(it) }
        if (direct != null) return direct

        val link = bookingUrl ?: url ?: return null
        val match = Regex("(?:presentationId|presentation_id|performanceId|sessionId|pid)=([A-Za-z0-9_-]+)")
            .find(link)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { isValidPresentationId(it) }
    }

    private fun isValidPresentationId(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase()
        if (lower == "null" || lower == "undefined" || lower == "none" || lower == "0") return false
        return true
    }
}

@JsonClass(generateAdapter = true)
data class FirstScheduleResponse(
    @param:Json(name = "date") val date: String? = null,
    @param:Json(name = "screenings") val screenings: List<RawScreening> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HourlyScheduleResponse(
    @param:Json(name = "screenings") val screenings: List<RawScreening> = emptyList()
)

data class Screening(
    val id: String,
    val title: String,
    val room: String,
    val roomNumber: Int?,
    val roomType: RoomType,
    val startStr: String,
    val endStr: String,
    val startMin: Int,
    val endMin: Int,
    val entryMin: Int,
    val capacity: Int?,
    val availabilityRatio: Float?,
    val occupancyPercentage: Int,
    val estimatedPeople: Int?,
    val status: ScreeningStatus,
    val crowdLevel: CrowdLevel,
    val posterUrl: String? = null,
    val presentationId: String? = null,
    val presentationCode: String? = null,
    val venueTypeId: Int = 1,
    val date: LocalDate? = null,
    val seatSummary: SeatAvailabilitySummary? = null,
    val availabilitySource: AvailabilitySource = AvailabilitySource.NONE,
    val seatFetchState: SeatFetchState = SeatFetchState.NONE,
    val seatApiHttpCode: Int? = null,
    val seatFetchError: String? = null,
    val lastSeatUpdateEpochMs: Long? = null,
    val seatApiFromCache: Boolean = false,
    val sourceSnapshot: ScreeningSourceSnapshot? = null
) {
    val progress: Float
        get() = if (status == ScreeningStatus.LIVE) {
            val total = (endMin - startMin).coerceAtLeast(1)
            if (total > 0) 0.5f else 0f
        } else 0f

    fun canonicalSlotKey(): String = canonicalSlotKey(room, startStr, title)

    fun canonicalDedupeKey(): String {
        val pid = presentationId?.trim()?.takeIf { it.isNotEmpty() }
        val datePart = date?.toString() ?: "any"
        return if (pid != null) {
            "$datePart|pid:$pid"
        } else {
            "$datePart|${canonicalSlotKey()}"
        }
    }

    /**
     * Original official hall capacity before subtracting broken seats.
     */
    fun originalHallCapacity(): Int? =
        roomNumber?.let { CinemaRepository.HALL_CAPACITIES[it] } ?: capacity

    /**
     * Manually reported broken seats count for this screening's hall (null = unknown).
     */
    fun brokenSeatsCount(): Int? =
        roomNumber?.let { CinemaRepository.BROKEN_SEATS_BY_HALL[it] }

    /**
     * True only when both original capacity and broken-seat count (including explicit 0) are known.
     */
    fun isBrokenSeatsKnown(): Boolean =
        originalHallCapacity() != null && brokenSeatsCount() != null

    fun hallCapacityInfo(): HallCapacityInfo =
        CinemaRepository.getHallCapacityInfo(roomNumber, fallbackOriginalCapacity = originalHallCapacity())

    /**
     * Returns verified seat map capacity ONLY when statuses across the layout are verified;
     * otherwise falls back to official hall capacity so partial/unverified seat lists never
     * shrink auditorium size.
     */
    fun roomCapacity(): Int? {
        val verifiedSeatCount = if (seatSummary != null &&
            seatSummary.availabilityRatio != null &&
            !seatSummary.hasUnverifiedStatuses &&
            seatSummary.totalValidSeats > 0
        ) {
            seatSummary.totalValidSeats
        } else {
            null
        }
        return verifiedSeatCount ?: capacity ?: roomNumber?.let { CinemaRepository.HALL_CAPACITIES[it] }
    }

    /**
     * Computes usable hall capacity:
     * - For verified live seat maps: excludes BLOCKED/UNAVAILABLE/UNKNOWN/omitted seats once without
     *   double-subtracting manually reported broken seats already reflected in the map.
     * - For unverified or schedule-ratio screenings: returns `originalHallCapacity - brokenSeats`
     *   (never negative), or `null` when brokenSeats is null/unknown.
     */
    fun usableHallCapacity(): Int? {
        val summary = seatSummary
        if (summary != null &&
            summary.availabilityRatio != null &&
            !summary.hasUnverifiedStatuses &&
            summary.totalValidSeats > 0
        ) {
            return resolveLiveSeatMapUsableCapacity(
                originalCapacity = originalHallCapacity(),
                manualBrokenSeats = brokenSeatsCount(),
                summary = summary
            )
        }
        return calculateUsableCapacity(originalHallCapacity(), brokenSeatsCount())
    }

    fun brokenSeatsExcludedByLiveMap(): Boolean {
        val summary = seatSummary ?: return false
        if (summary.availabilityRatio == null || summary.hasUnverifiedStatuses || summary.totalValidSeats <= 0) {
            return false
        }
        val manualBroken = brokenSeatsCount()
            ?: return (summary.blockedSeats + summary.unavailableSeats + summary.unknownSeats) > 0
        val omittedFromOriginal = originalHallCapacity()?.let {
            (it - summary.totalRawSeatObjects).coerceAtLeast(0)
        } ?: 0
        val alreadyExcluded = summary.blockedSeats + summary.unavailableSeats + summary.unknownSeats + omittedFromOriginal
        return manualBroken > 0 && alreadyExcluded >= manualBroken
    }

    /**
     * Capacity used for ratio-based occupancy estimation: prefers usable capacity when known,
     * falling back to original capacity only when broken-seat count is unknown (never mutating usableHallCapacity to non-null).
     */
    fun effectiveRatioCapacity(): Int? = usableHallCapacity() ?: roomCapacity()

    fun occupancyCountKind(): OccupancyCountKind {
        val summary = seatSummary
        if (summary != null && summary.totalValidSeats > 0 && summary.availabilityRatio != null) {
            return OccupancyCountKind.VERIFIED_SEAT_MAP
        }
        val ratio = availabilityRatio?.toDouble()?.takeIf { it in 0.0..1.0 }
        val cap = effectiveRatioCapacity()
        if (ratio != null && cap != null) {
            return OccupancyCountKind.RATIO_ESTIMATE
        }
        return OccupancyCountKind.UNKNOWN
    }

    /**
     * Estimates occupied seats cleanly without fabricating numbers for unknown states.
     * Prefers actual parsed occupied + reserved seat count from seats-statusV2 when verified,
     * and uses usable capacity for ratio-based occupancy estimates when known.
     */
    fun estimatedOccupiedSeats(): Int? {
        val summary = seatSummary
        if (summary != null && summary.totalValidSeats > 0 && summary.availabilityRatio != null) {
            return summary.occupiedAndReservedSeats.coerceIn(0, summary.totalValidSeats)
        }
        val ratio = availabilityRatio?.toDouble()?.takeIf { it in 0.0..1.0 } ?: return null
        val cap = effectiveRatioCapacity() ?: return null
        return ((cap * (1.0 - ratio)).roundToInt()).coerceIn(0, cap)
    }

    /**
     * Evaluates whether this screening is in progress at [now] in Asia/Jerusalem cinema-day time.
     * Handles exact start/end bounds [startMin, endMin), post-midnight wrap before 07:00,
     * late shows extending past 07:00, and DST offsets cleanly.
     */
    fun isShowingAt(now: ZonedDateTime): Boolean {
        val (effectiveCinemaDate, cinemaMin) = resolveIsraelCinemaDayAndMin(now)
        if (date == null) {
            return cinemaMin in startMin until endMin
        }
        if (date == effectiveCinemaDate) {
            return cinemaMin in startMin until endMin
        }
        // Handle late screening from previous cinema day extending past 07:00
        if (date == effectiveCinemaDate.minusDays(1) && endMin > 1860) {
            return (cinemaMin + 1440) in startMin until endMin
        }
        return false
    }

    /**
     * Computes consistent ScreeningStatus for [now] in Israel timezone, matching [isShowingAt].
     */
    fun statusAt(now: ZonedDateTime): ScreeningStatus {
        if (isShowingAt(now)) return ScreeningStatus.LIVE
        val (effectiveCinemaDate, cinemaMin) = resolveIsraelCinemaDayAndMin(now)

        if (date != null) {
            if (date.isBefore(effectiveCinemaDate)) {
                val shiftedNow = cinemaMin + 1440
                return if (date == effectiveCinemaDate.minusDays(1) &&
                    shiftedNow < endMin + CinemaRepository.ENDED_KEEP
                ) {
                    ScreeningStatus.ENDED_RECENTLY
                } else {
                    ScreeningStatus.ENDED
                }
            }
            if (date.isAfter(effectiveCinemaDate)) {
                return ScreeningStatus.UPCOMING
            }
        }

        return when {
            cinemaMin < startMin -> ScreeningStatus.UPCOMING
            cinemaMin < endMin + CinemaRepository.ENDED_KEEP -> ScreeningStatus.ENDED_RECENTLY
            else -> ScreeningStatus.ENDED
        }
    }

    companion object {
        fun resolveIsraelCinemaDayAndMin(now: ZonedDateTime): Pair<LocalDate, Int> {
            val jerusalemNow = now.withZoneSameInstant(ISRAEL_ZONE)
            val rawMin = jerusalemNow.hour * 60 + jerusalemNow.minute
            val cinemaMin = if (rawMin < 420) rawMin + 1440 else rawMin
            val effectiveDate = if (rawMin < 420) {
                jerusalemNow.toLocalDate().minusDays(1)
            } else {
                jerusalemNow.toLocalDate()
            }
            return effectiveDate to cinemaMin
        }
    }
}

data class ScreeningOccupancyDiagnostic(
    val screeningId: String,
    val room: String,
    val roomNumber: Int?,
    val title: String,
    val presentationId: String?,
    val presentationCode: String? = null,
    val startTime: String,
    val endTime: String,
    val availabilitySource: AvailabilitySource = AvailabilitySource.NONE,
    val seatFetchState: SeatFetchState = SeatFetchState.NONE,
    val seatApiHttpCode: Int? = null,
    val totalRawSeatObjects: Int? = null,
    val totalValidSeats: Int?,
    val availableSeats: Int?,
    val occupiedSeats: Int?,
    val reservedSeats: Int?,
    val blockedSeats: Int?,
    val unknownSeats: Int?,
    val roomCapacity: Int?,
    val originalHallCapacity: Int? = roomCapacity,
    val brokenSeatsCount: Int? = null,
    val usableHallCapacity: Int? = null,
    val brokenSeatsKnown: Boolean = false,
    val brokenSeatsExcludedByLiveMap: Boolean = false,
    val countedOccupiedSeats: Int?,
    val verifiedOccupiedSeats: Int = 0,
    val estimatedOccupiedSeats: Int = 0,
    val occupancyKind: OccupancyCountKind = OccupancyCountKind.UNKNOWN,
    val observedPropertyKeys: Set<String> = emptySet(),
    val rawStatusHistogram: Map<String, Int> = emptyMap(),
    val includedInOccupancyTotal: Boolean,
    val exclusionReason: String?
)

data class OccupancyResult(
    val occupiedSeats: Int = 0,
    val verifiedOccupiedSeats: Int = 0,
    val estimatedOccupiedSeats: Int = 0,
    val countedScreenings: Int = 0,
    val verifiedScreeningsCount: Int = 0,
    val estimatedScreeningsCount: Int = 0,
    val missingScreenings: Int = 0,
    val missingPresentationIdScreenings: Int = 0,
    val unverifiedSeatStatusScreenings: Int = 0,
    val unknownBrokenSeatsScreenings: Int = 0,
    val failedScreenings: Int = 0,
    val activeScreenings: Int = 0,
    val completenessPercentage: Int = 100,
    val unknownOccupancy: Boolean = false,
    val isComplete: Boolean = true,
    val lastUpdatedEpochMs: Long? = null,
    val screeningDiagnostics: List<ScreeningOccupancyDiagnostic> = emptyList()
)

/**
 * Deduplicates screenings so the same screening slot or presentationId is never counted twice.
 * Also merges records belonging to the same physical slot even if only one carries presentationId.
 */
fun deduplicateScreenings(screenings: List<Screening>): List<Screening> {
    if (screenings.size <= 1) return screenings
    val bySlot = linkedMapOf<String, Screening>()
    val pidToSlot = mutableMapOf<String, String>()

    for (item in screenings) {
        val pid = item.presentationId?.trim()?.takeIf { it.isNotEmpty() }
        val datePart = item.date?.toString() ?: "any"
        val slotKey = "$datePart|${item.canonicalSlotKey()}"
        val matchedSlot = pid?.let { pidToSlot[it] } ?: slotKey

        val existing = bySlot[matchedSlot]
        if (existing == null) {
            bySlot[matchedSlot] = item
            if (pid != null) pidToSlot[pid] = matchedSlot
        } else {
            val merged = mergeDuplicateScreeningPair(existing, item)
            bySlot[matchedSlot] = merged
            merged.presentationId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                pidToSlot[it] = matchedSlot
            }
        }
    }
    return bySlot.values.toList()
}

private fun mergeDuplicateScreeningPair(a: Screening, b: Screening): Screening {
    val aRank = sourcePriority(a.availabilitySource) + if (a.estimatedOccupiedSeats() != null) 10 else 0
    val bRank = sourcePriority(b.availabilitySource) + if (b.estimatedOccupiedSeats() != null) 10 else 0
    val primary = if (bRank > aRank) b else a
    val secondary = if (primary === a) b else a

    val resolvedPid = primary.presentationId ?: secondary.presentationId
    val resolvedCode = primary.presentationCode ?: secondary.presentationCode
    val resolvedSummary = primary.seatSummary ?: secondary.seatSummary
    val resolvedRatio = primary.availabilityRatio ?: secondary.availabilityRatio
    val hasVerifiedSummary = resolvedSummary != null &&
        resolvedSummary.availabilityRatio != null &&
        !resolvedSummary.hasUnverifiedStatuses &&
        resolvedSummary.totalValidSeats > 0
    val resolvedCap = if (hasVerifiedSummary) {
        resolvedSummary?.totalValidSeats
    } else {
        primary.roomCapacity() ?: secondary.roomCapacity()
    }
    val ratioCap = if (hasVerifiedSummary) {
        resolvedCap
    } else {
        primary.usableHallCapacity() ?: secondary.usableHallCapacity() ?: resolvedCap
    }
    val resolvedEstPeople = when {
        resolvedSummary != null && resolvedSummary.totalValidSeats > 0 && resolvedSummary.availabilityRatio != null ->
            resolvedSummary.occupiedAndReservedSeats
        resolvedRatio != null && ratioCap != null ->
            (ratioCap * (1.0 - resolvedRatio.toDouble())).roundToInt().coerceIn(0, ratioCap)
        else -> null
    }

    val mergedSnapshot = mergeSourceSnapshots(primary.sourceSnapshot, secondary.sourceSnapshot)

    return primary.copy(
        presentationId = resolvedPid,
        presentationCode = resolvedCode,
        seatSummary = resolvedSummary,
        capacity = resolvedCap,
        availabilityRatio = resolvedRatio,
        estimatedPeople = resolvedEstPeople,
        occupancyPercentage = if (resolvedRatio != null) {
            ((1.0 - resolvedRatio.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else 0,
        crowdLevel = calculateCrowdLevelForRatio(resolvedRatio),
        posterUrl = primary.posterUrl ?: secondary.posterUrl,
        seatFetchState = if (primary.seatFetchState != SeatFetchState.NONE) primary.seatFetchState else secondary.seatFetchState,
        seatApiHttpCode = primary.seatApiHttpCode ?: secondary.seatApiHttpCode,
        seatFetchError = primary.seatFetchError ?: secondary.seatFetchError,
        lastSeatUpdateEpochMs = primary.lastSeatUpdateEpochMs ?: secondary.lastSeatUpdateEpochMs,
        seatApiFromCache = if (primary.seatSummary != null) primary.seatApiFromCache else secondary.seatApiFromCache,
        sourceSnapshot = mergedSnapshot
    )
}

private fun mergeSourceSnapshots(
    primary: ScreeningSourceSnapshot?,
    secondary: ScreeningSourceSnapshot?
): ScreeningSourceSnapshot? {
    if (primary == null) return secondary
    if (secondary == null) return primary
    return primary.copy(
        cinemaDate = primary.cinemaDate ?: secondary.cinemaDate,
        inFirstUrl = primary.inFirstUrl || secondary.inFirstUrl,
        firstUrlRatio = primary.firstUrlRatio ?: secondary.firstUrlRatio,
        firstUrlPresentationId = primary.firstUrlPresentationId ?: secondary.firstUrlPresentationId,
        firstUrlEnd = primary.firstUrlEnd ?: secondary.firstUrlEnd,
        inHourlyUrl = primary.inHourlyUrl || secondary.inHourlyUrl,
        hourlyUrlRatio = primary.hourlyUrlRatio ?: secondary.hourlyUrlRatio,
        hourlyUrlPresentationId = primary.hourlyUrlPresentationId ?: secondary.hourlyUrlPresentationId,
        hourlyUrlEnd = primary.hourlyUrlEnd ?: secondary.hourlyUrlEnd,
        inQuickBook = primary.inQuickBook || secondary.inQuickBook,
        quickBookPresentationId = primary.quickBookPresentationId ?: secondary.quickBookPresentationId,
        quickBookPresentationCode = primary.quickBookPresentationCode ?: secondary.quickBookPresentationCode,
        quickBookSoldOut = primary.quickBookSoldOut ?: secondary.quickBookSoldOut,
        quickBookEnd = primary.quickBookEnd ?: secondary.quickBookEnd,
        seatApiAttempted = primary.seatApiAttempted || secondary.seatApiAttempted,
        seatApiFromCache = if (primary.seatApiAttempted) primary.seatApiFromCache else secondary.seatApiFromCache,
        seatApiHttpCode = primary.seatApiHttpCode ?: secondary.seatApiHttpCode,
        seatApiFetchedAtEpochMs = primary.seatApiFetchedAtEpochMs ?: secondary.seatApiFetchedAtEpochMs,
        seatApiTtlMs = primary.seatApiTtlMs ?: secondary.seatApiTtlMs,
        seatApiError = primary.seatApiError ?: secondary.seatApiError
    )
}

private fun sourcePriority(source: AvailabilitySource): Int = when (source) {
    AvailabilitySource.LIVE_SEAT_MAP -> 6
    AvailabilitySource.HOURLY_FEED -> 5
    AvailabilitySource.OFFICIAL_QUICKBOOK -> 4
    AvailabilitySource.SCHEDULE_FEED -> 3
    AvailabilitySource.SAMPLE_FALLBACK -> 2
    AvailabilitySource.NONE -> 1
}

/**
 * Computes full live occupancy metrics across all currently running screenings at [now].
 * - Deduplicates screenings so no show is counted twice.
 * - Excludes ended and upcoming shows.
 * - Separates verified live seat-map counts from schedule ratio estimates.
 * - Explicitly tracks missing, unverified, missing-PID, and failed screenings instead of hiding `null` as `0`.
 */
fun calculateCinemaOccupancy(
    screenings: List<Screening>,
    now: ZonedDateTime = ZonedDateTime.now(ISRAEL_ZONE),
    lastUpdatedEpochMs: Long? = null
): OccupancyResult {
    val unique = deduplicateScreenings(screenings)
    var totalOccupied = 0
    var verifiedTotal = 0
    var estimatedTotal = 0
    var activeCount = 0
    var countedCount = 0
    var verifiedScreeningsCount = 0
    var estimatedScreeningsCount = 0
    var missingCount = 0
    var missingPidCount = 0
    var unverifiedCount = 0
    var unknownBrokenCount = 0
    var failedCount = 0
    val diagnostics = mutableListOf<ScreeningOccupancyDiagnostic>()

    for (screening in unique) {
        if (!screening.isShowingAt(now)) continue
        activeCount++

        val summary = screening.seatSummary
        val cap = screening.roomCapacity()
        val origCap = screening.originalHallCapacity()
        val brokenCount = screening.brokenSeatsCount()
        val usableCap = screening.usableHallCapacity()
        val brokenKnown = screening.isBrokenSeatsKnown()
        val excludedByLiveMap = screening.brokenSeatsExcludedByLiveMap()
        if (!brokenKnown) {
            unknownBrokenCount++
        }
        val occupied = screening.estimatedOccupiedSeats()
        val kind = screening.occupancyCountKind()
        if (screening.presentationId.isNullOrBlank()) {
            missingPidCount++
        }
        if ((summary?.unknownSeats ?: 0) > 0 || screening.seatFetchState == SeatFetchState.UNVERIFIED_STATUSES) {
            unverifiedCount++
        }

        if (occupied != null) {
            totalOccupied += occupied
            countedCount++
            val verifiedPart = if (kind == OccupancyCountKind.VERIFIED_SEAT_MAP) occupied else 0
            val estimatedPart = if (kind == OccupancyCountKind.RATIO_ESTIMATE) occupied else 0
            if (kind == OccupancyCountKind.VERIFIED_SEAT_MAP) {
                verifiedTotal += occupied
                verifiedScreeningsCount++
            } else {
                estimatedTotal += occupied
                estimatedScreeningsCount++
            }

            diagnostics.add(
                ScreeningOccupancyDiagnostic(
                    screeningId = screening.id,
                    room = screening.room,
                    roomNumber = screening.roomNumber,
                    title = screening.title,
                    presentationId = screening.presentationId,
                    presentationCode = screening.presentationCode,
                    startTime = screening.startStr,
                    endTime = screening.endStr,
                    availabilitySource = screening.availabilitySource,
                    seatFetchState = screening.seatFetchState,
                    seatApiHttpCode = screening.seatApiHttpCode ?: summary?.httpStatusCode,
                    totalRawSeatObjects = summary?.totalRawSeatObjects,
                    totalValidSeats = summary?.totalValidSeats,
                    availableSeats = summary?.availableSeats,
                    occupiedSeats = summary?.occupiedSeats,
                    reservedSeats = summary?.reservedSeats,
                    blockedSeats = summary?.blockedSeats,
                    unknownSeats = summary?.unknownSeats,
                    roomCapacity = cap,
                    originalHallCapacity = origCap,
                    brokenSeatsCount = brokenCount,
                    usableHallCapacity = usableCap,
                    brokenSeatsKnown = brokenKnown,
                    brokenSeatsExcludedByLiveMap = excludedByLiveMap,
                    countedOccupiedSeats = occupied,
                    verifiedOccupiedSeats = verifiedPart,
                    estimatedOccupiedSeats = estimatedPart,
                    occupancyKind = kind,
                    observedPropertyKeys = summary?.observedPropertyKeys.orEmpty(),
                    rawStatusHistogram = summary?.rawStatusHistogram.orEmpty(),
                    includedInOccupancyTotal = true,
                    exclusionReason = null
                )
            )
        } else {
            val isFailure = screening.seatFetchState == SeatFetchState.FAILED ||
                screening.seatFetchState == SeatFetchState.AUTH_REQUIRED
            if (isFailure) {
                failedCount++
            } else {
                missingCount++
            }

            val reason = when {
                screening.seatFetchState == SeatFetchState.FAILED ->
                    "API_FETCH_FAILED (${screening.seatFetchError ?: "network/server error"})"
                screening.seatFetchState == SeatFetchState.AUTH_REQUIRED ->
                    "API_AUTH_REQUIRED (${screening.seatFetchError ?: "401/403"})"
                screening.seatFetchState == SeatFetchState.UNVERIFIED_STATUSES ||
                    summary?.hasUnverifiedStatuses == true ->
                    "UNVERIFIED_SEAT_STATUSES (unknown=${summary?.unknownSeats ?: 0})"
                cap == null ->
                    "UNKNOWN_ROOM_CAPACITY (room=${screening.room})"
                screening.presentationId.isNullOrBlank() && screening.availabilityRatio == null ->
                    "MISSING_PRESENTATION_ID_AND_RATIO"
                else ->
                    "MISSING_SEAT_DATA (${screening.seatFetchState.name})"
            }

            diagnostics.add(
                ScreeningOccupancyDiagnostic(
                    screeningId = screening.id,
                    room = screening.room,
                    roomNumber = screening.roomNumber,
                    title = screening.title,
                    presentationId = screening.presentationId,
                    presentationCode = screening.presentationCode,
                    startTime = screening.startStr,
                    endTime = screening.endStr,
                    availabilitySource = screening.availabilitySource,
                    seatFetchState = screening.seatFetchState,
                    seatApiHttpCode = screening.seatApiHttpCode ?: summary?.httpStatusCode,
                    totalRawSeatObjects = summary?.totalRawSeatObjects,
                    totalValidSeats = summary?.totalValidSeats,
                    availableSeats = summary?.availableSeats,
                    occupiedSeats = summary?.occupiedSeats,
                    reservedSeats = summary?.reservedSeats,
                    blockedSeats = summary?.blockedSeats,
                    unknownSeats = summary?.unknownSeats,
                    roomCapacity = cap,
                    originalHallCapacity = origCap,
                    brokenSeatsCount = brokenCount,
                    usableHallCapacity = usableCap,
                    brokenSeatsKnown = brokenKnown,
                    brokenSeatsExcludedByLiveMap = excludedByLiveMap,
                    countedOccupiedSeats = null,
                    verifiedOccupiedSeats = 0,
                    estimatedOccupiedSeats = 0,
                    occupancyKind = OccupancyCountKind.UNKNOWN,
                    observedPropertyKeys = summary?.observedPropertyKeys.orEmpty(),
                    rawStatusHistogram = summary?.rawStatusHistogram.orEmpty(),
                    includedInOccupancyTotal = false,
                    exclusionReason = reason
                )
            )
        }
    }

    val unknownOccupancy = activeCount > 0 && countedCount == 0
    val isComplete = missingCount == 0 && failedCount == 0
    val completeness = if (activeCount == 0) 100 else ((countedCount * 100.0) / activeCount).roundToInt().coerceIn(0, 100)

    return OccupancyResult(
        occupiedSeats = totalOccupied,
        verifiedOccupiedSeats = verifiedTotal,
        estimatedOccupiedSeats = estimatedTotal,
        countedScreenings = countedCount,
        verifiedScreeningsCount = verifiedScreeningsCount,
        estimatedScreeningsCount = estimatedScreeningsCount,
        missingScreenings = missingCount,
        missingPresentationIdScreenings = missingPidCount,
        unverifiedSeatStatusScreenings = unverifiedCount,
        unknownBrokenSeatsScreenings = unknownBrokenCount,
        failedScreenings = failedCount,
        activeScreenings = activeCount,
        completenessPercentage = completeness,
        unknownOccupancy = unknownOccupancy,
        isComplete = isComplete,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        screeningDiagnostics = diagnostics
    )
}

/**
 * Computes estimated current viewers across all currently running screenings at [now].
 * Delegates to [calculateCinemaOccupancy] so deduplication and bounds match everywhere.
 */
fun currentlyOccupiedSeats(
    screenings: List<Screening>,
    now: ZonedDateTime = ZonedDateTime.now(ISRAEL_ZONE)
): Int = calculateCinemaOccupancy(screenings, now).occupiedSeats

/**
 * Merges live seat availability summaries (from seats-statusV2), fetch status results,
 * and/or hourly ratios into [screenings].
 * - Prefers verified live seat counts over schedule-feed estimates.
 * - Never overwrites a valid availabilityRatio with an upstream null override.
 * - NEVER shrinks room capacity using an unverified or partial seat-map subset.
 * - Tracks failures cleanly via [SeatFetchState] and HTTP codes.
 */
fun mergeScreeningAvailability(
    screenings: List<Screening>,
    seatSummariesByKey: Map<String, SeatAvailabilitySummary> = emptyMap(),
    scheduleRatiosByKey: Map<String, Float?> = emptyMap(),
    seatFetchResultsByKey: Map<String, SeatFetchResult> = emptyMap()
): List<Screening> {
    return screenings.map { screening ->
        val pid = screening.presentationId
        val slotKey = screening.canonicalSlotKey()
        val legacyKey = "${screening.room}|${screening.startStr}|${screening.title}"

        val summary = pid?.let { seatSummariesByKey[it] }
            ?: seatSummariesByKey[screening.id]
            ?: seatSummariesByKey[slotKey]

        val fetchResult = pid?.let { seatFetchResultsByKey[it] }
            ?: seatFetchResultsByKey[screening.id]
            ?: seatFetchResultsByKey[slotKey]

        val hourlyRatio = (scheduleRatiosByKey[slotKey] ?: scheduleRatiosByKey[legacyKey])
            ?.takeIf { it in 0f..1f }

        when {
            summary != null -> {
                val verifiedRatio = summary.availabilityRatio?.toFloat()?.coerceIn(0f, 1f)
                val successResult = fetchResult as? SeatFetchResult.Success
                val httpCode = summary.httpStatusCode ?: successResult?.httpCode
                val fromCache = successResult?.fromCache ?: screening.seatApiFromCache
                val ttlMs = successResult?.ttlMs
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    seatApiAttempted = true,
                    seatApiFromCache = fromCache,
                    seatApiHttpCode = httpCode,
                    seatApiFetchedAtEpochMs = summary.fetchedAtEpochMs,
                    seatApiTtlMs = ttlMs,
                    seatApiError = if (verifiedRatio == null && summary.hasUnverifiedStatuses) "UNVERIFIED_STATUSES" else null
                )
                val canTrustSeatCountAsCapacity =
                    verifiedRatio != null && !summary.hasUnverifiedStatuses && summary.totalValidSeats > 0
                val effectiveCapacity = if (canTrustSeatCountAsCapacity) {
                    summary.totalValidSeats
                } else {
                    screening.roomCapacity()
                }

                if (verifiedRatio != null) {
                    val occupied = summary.occupiedAndReservedSeats
                        .coerceIn(0, summary.totalValidSeats.coerceAtLeast(0))
                    val pct = ((1.0 - verifiedRatio) * 100.0).roundToInt().coerceIn(0, 100)
                    screening.copy(
                        capacity = effectiveCapacity,
                        availabilityRatio = verifiedRatio,
                        occupancyPercentage = pct,
                        estimatedPeople = occupied,
                        crowdLevel = calculateCrowdLevelForRatio(verifiedRatio),
                        seatSummary = summary,
                        availabilitySource = AvailabilitySource.LIVE_SEAT_MAP,
                        seatFetchState = SeatFetchState.SUCCESS,
                        seatApiHttpCode = httpCode,
                        seatFetchError = null,
                        lastSeatUpdateEpochMs = summary.fetchedAtEpochMs,
                        seatApiFromCache = fromCache,
                        sourceSnapshot = updatedTrace
                    )
                } else {
                    // Seat statuses are unverified/UNKNOWN in live response: attach raw summary honestly
                    // WITHOUT shrinking hall capacity or overwriting known schedule estimate with null.
                    val fallbackRatio = screening.availabilityRatio?.takeIf { it in 0f..1f } ?: hourlyRatio
                    val ratioCap = screening.usableHallCapacity() ?: effectiveCapacity
                    val recalcEst = if (fallbackRatio != null && ratioCap != null) {
                        (ratioCap * (1.0 - fallbackRatio.toDouble())).roundToInt()
                            .coerceIn(0, ratioCap)
                    } else null
                    val pct = if (fallbackRatio != null) {
                        ((1.0 - fallbackRatio.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                    } else 0
                    val source = when {
                        screening.availabilityRatio != null -> screening.availabilitySource
                        hourlyRatio != null -> AvailabilitySource.HOURLY_FEED
                        else -> AvailabilitySource.NONE
                    }
                    screening.copy(
                        capacity = effectiveCapacity,
                        availabilityRatio = fallbackRatio,
                        occupancyPercentage = pct,
                        estimatedPeople = recalcEst,
                        crowdLevel = calculateCrowdLevelForRatio(fallbackRatio),
                        seatSummary = summary,
                        lastSeatUpdateEpochMs = summary.fetchedAtEpochMs,
                        availabilitySource = source,
                        seatFetchState = if (fallbackRatio == null) SeatFetchState.UNVERIFIED_STATUSES else SeatFetchState.SUCCESS,
                        seatApiHttpCode = httpCode,
                        seatFetchError = if (summary.hasUnverifiedStatuses) "סטטוס מושבים גולמי טרם אומת" else null,
                        seatApiFromCache = fromCache,
                        sourceSnapshot = updatedTrace
                    )
                }
            }

            fetchResult is SeatFetchResult.AuthRequired -> {
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    seatApiAttempted = true,
                    seatApiFromCache = false,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatApiError = fetchResult.message
                )
                screening.copy(
                    seatFetchState = SeatFetchState.AUTH_REQUIRED,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatFetchError = fetchResult.message,
                    seatApiFromCache = false,
                    sourceSnapshot = updatedTrace
                )
            }

            fetchResult is SeatFetchResult.NetworkError -> {
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    seatApiAttempted = true,
                    seatApiFromCache = false,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatApiError = fetchResult.message
                )
                screening.copy(
                    seatFetchState = SeatFetchState.FAILED,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatFetchError = fetchResult.message,
                    seatApiFromCache = false,
                    sourceSnapshot = updatedTrace
                )
            }

            fetchResult is SeatFetchResult.EmptyOrInvalid -> {
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    seatApiAttempted = true,
                    seatApiFromCache = false,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatApiError = fetchResult.message
                )
                screening.copy(
                    seatFetchState = SeatFetchState.MISSING_DATA,
                    seatApiHttpCode = fetchResult.httpCode,
                    seatFetchError = fetchResult.message,
                    seatApiFromCache = false,
                    sourceSnapshot = updatedTrace
                )
            }

            fetchResult is SeatFetchResult.MissingPresentationId -> {
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    seatApiAttempted = false,
                    seatApiError = "MISSING_PRESENTATION_ID"
                )
                screening.copy(
                    seatFetchState = SeatFetchState.MISSING_PRESENTATION_ID,
                    seatFetchError = "חסר מזהה הקרנה (presentationId)",
                    sourceSnapshot = updatedTrace
                )
            }

            hourlyRatio != null -> {
                val ratioCap = screening.effectiveRatioCapacity() ?: screening.roomCapacity()
                val occPct = ((1f - hourlyRatio) * 100f).roundToInt().coerceIn(0, 100)
                val estPeople = if (ratioCap != null) (ratioCap * (1.0 - hourlyRatio.toDouble())).roundToInt().coerceIn(0, ratioCap) else null
                val updatedTrace = (screening.sourceSnapshot ?: ScreeningSourceSnapshot(cinemaDate = screening.date?.toString())).copy(
                    inHourlyUrl = true,
                    hourlyUrlRatio = screening.sourceSnapshot?.hourlyUrlRatio ?: hourlyRatio
                )
                screening.copy(
                    availabilityRatio = hourlyRatio,
                    occupancyPercentage = occPct,
                    estimatedPeople = estPeople,
                    crowdLevel = calculateCrowdLevelForRatio(hourlyRatio),
                    availabilitySource = AvailabilitySource.HOURLY_FEED,
                    seatFetchState = SeatFetchState.SUCCESS,
                    seatFetchError = null,
                    sourceSnapshot = updatedTrace
                )
            }

            else -> screening
        }
    }
}

fun calculateCrowdLevelForRatio(availabilityRatio: Float?): CrowdLevel {
    if (availabilityRatio == null) return CrowdLevel.UNKNOWN
    val occupancy = ((1f - availabilityRatio) * 100f).toInt()
    return when {
        occupancy > 85 -> CrowdLevel.VERY_BUSY
        occupancy > 60 -> CrowdLevel.BUSY
        occupancy > 30 -> CrowdLevel.MEDIUM
        else -> CrowdLevel.QUIET
    }
}

data class CinemaEvent(
    val timeMin: Int,
    val kind: EventKind, // IN or OUT
    val screening: Screening,
    val isUrgent: Boolean,
    val minutesUntil: Int
)

enum class EventKind {
    IN,
    OUT
}

data class KpiStats(
    val peopleInside: Int = 0,
    val activeHallsCount: Int = 0,
    val activeKnownHallsCount: Int = 0,
    val occupancyResult: OccupancyResult = OccupancyResult(),
    val out30MinPeople: Int = 0,
    val out30MinHalls: Int = 0,
    val in30MinPeople: Int = 0,
    val in30MinHalls: Int = 0,
    val nextEvent: CinemaEvent? = null
)

data class MovieSummary(
    val title: String,
    val genre: String,
    val screeningCount: Int,
    val posterUrl: String? = null,
    val primaryColor: Color = Color(0xFFE11D48)
)

data class DailyScreeningBreakdownItem(
    val screeningKey: String,
    val presentationId: String?,
    val title: String,
    val room: String,
    val startTime: String,
    val endTime: String,
    val startMin: Int,
    val endMin: Int,
    val estimatedOccupiedSeats: Int?,
    val availableSeats: Int?,
    val roomCapacity: Int?,
    val attendanceKind: AttendanceKind,
    val availabilitySource: AvailabilitySource,
    val hasCompleted: Boolean,
    val isFinal: Boolean,
    val snapshotTimestampEpochMs: Long
)

data class DailyTimelinePoint(
    val bucketMinute: Int,
    val labelTime: String,
    val totalCurrentViewers: Int,
    val activeScreeningsCount: Int,
    val knownActiveCount: Int
)

data class MonitoringGapInfo(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val reason: String,
    val startLabel: String,
    val endLabel: String
)

data class DailyStatisticsSummary(
    val date: String,
    val isToday: Boolean = true,
    val totalScreeningsToday: Int = 0,
    val currentlyRunningScreenings: Int = 0,
    val completedScreenings: Int = 0,
    val upcomingScreenings: Int = 0,
    val estimatedCurrentViewers: Int = 0,
    val estimatedCompletedAttendance: Int? = null,
    val completedWithKnownAttendance: Int = 0,
    val completedWithUnknownAttendance: Int = 0,
    val estimatedTotalAttendanceToday: Int? = null,
    val totalWithKnownAttendance: Int = 0,
    val totalWithUnknownAttendance: Int = 0,
    val totalKnownAvailableSeats: Int? = null,
    val totalKnownOccupiedSeats: Int? = null,
    val confirmedCount: Int = 0,
    val estimatedCount: Int = 0,
    val unknownCount: Int = 0,
    val timelinePoints: List<DailyTimelinePoint> = emptyList(),
    val monitoringGaps: List<MonitoringGapInfo> = emptyList(),
    val screeningBreakdowns: List<DailyScreeningBreakdownItem> = emptyList()
)

data class CinemaUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLiveDataSource: Boolean = false,
    val dataSourceLabel: String = "טוען…",
    val currentDate: String = "",
    val currentCinemaIsoDate: String = "",
    val currentIsraelTime: String = "--:--:--",
    val currentCinemaMinutes: Int = 0,
    val lastSuccessfulUpdateEpochMs: Long? = null,
    val lastSuccessfulUpdateLabel: String = "טרם עודכן",
    val dataFreshnessLabel: String = "ממתין לעדכון",
    val seatMapUpdateInProgress: Boolean = false,
    val hasUnverifiedSeatStatuses: Boolean = false,
    val quickBookStatus: QuickBookSourceStatus = QuickBookSourceStatus(),
    val allScreenings: List<Screening> = emptyList(),
    val filteredScreenings: List<Screening> = emptyList(),
    val timelineEvents: List<CinemaEvent> = emptyList(),
    val movieSummaries: List<MovieSummary> = emptyList(),
    val kpiStats: KpiStats = KpiStats(),
    val filterType: String = "ALL", // ALL, LIVE, IMAX, VIP, 4DX, ScreenX, 2D
    val searchQuery: String = "",
    val selectedMovieTitle: String? = null,
    val sortBy: SortOption = SortOption.START_TIME,
    val showEnded: Boolean = false,
    val selectedScreening: Screening? = null,
    val activeTab: CinemaTab = CinemaTab.SCHEDULE,
    val errorMessage: String? = null,
    val countsByType: Map<String, Int> = emptyMap(),
    val selectedHistoryDate: String = "",
    val availableHistoryDates: List<String> = emptyList(),
    val dailyStats: DailyStatisticsSummary? = null,
    val sourceTelemetry: SourceTelemetryBundle = SourceTelemetryBundle(),
    val latestValidationSnapshot: LiveOccupancyValidationSnapshot? = null,
    val latestValidationJsonPath: String? = null
)

enum class SortOption(val title: String) {
    START_TIME("שעת התחלה"),
    END_TIME("שעת סיום"),
    ROOM("אולם"),
    CROWD("הכי עמוס")
}

enum class CinemaTab(val title: String) {
    SCHEDULE("לוח הקרנות"),
    TIMELINE("ציר עומסים"),
    HALLS_MAP("מפת אולמות"),
    DAILY_STATS("סטטיסטיקה")
}
